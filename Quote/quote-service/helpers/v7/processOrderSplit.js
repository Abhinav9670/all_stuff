const { fetchSLADetails } = require('../v6/appendCityDetails.js');
const { 
  splitProductsByFulfillmentMode, 
  generateShippingAmountArray 
} = require('../orderSplitHelper.js');
const { 
  getBulkWarehouseDetails,
  extractWarehouseIdsFromSLA,
  mapSellerDetailsToProducts
} = require('../../javaApis/product.js');
const { upsertQuote } = require('../upsertQuote');
const { collection } = require('../../config/couchbase.js');
const { logError } = require('../utils');
const cache = require('memory-cache');


const extractWarehouseIdsFromBestInventory = (productSLADetails, xHeaderToken) => {
  try {
    if (!productSLADetails || !Array.isArray(productSLADetails)) {
      return [];
    }

    const warehouseIds = [];

    productSLADetails.forEach(product => {
      if (product.warehouse_details && Array.isArray(product.warehouse_details)) {
        product.warehouse_details.forEach(warehouse => {
          if (warehouse.warehouseId && !warehouseIds.includes(warehouse.warehouseId)) {
            warehouseIds.push(warehouse.warehouseId);
          }
        });
      }
    });

    return warehouseIds;
  } catch (error) {
    logError(error, 'Error in extractWarehouseIdsFromSLA: ', xHeaderToken);
  }
};



const getBestInventory = (inventoryArr = [], productArr = [], threshold = 1) => {
  const productQtyMap = new Map(
    productArr.map(({ sku, quantity }) => [sku, Number(quantity)])
  );

  // Group inventory by SKU
  const inventoryMap = inventoryArr.reduce((map, inv) => {
    (map.get(inv.sku) ?? map.set(inv.sku, []).get(inv.sku)).push(inv);
    return map;
  }, new Map());

  // Classify warehouse based on fulfillment_mode
  const getType = inv => {
    const wh = inv.warehouse_details?.[0] ?? {};
    const mode = (wh.fulfillment_mode || "").toLowerCase();
    if (mode === "local" || mode === "express") return "local";
    return "global";
  };

  return [...inventoryMap.entries()].flatMap(([sku, inventories]) => {
    const userQty = productQtyMap.get(sku);
    if (!userQty) return [];

    // Eligible inventories
    const eligible = inventories.filter(inv => Number(inv.value) >= userQty);

    if (!eligible.length) return [];

    // 1️⃣ Pick local/express if available
    const localOrExpress = eligible.filter(
      inv => getType(inv) === "local" && (Number(inv.value) - userQty) >= threshold
    );
    if (localOrExpress.length) {
      // pick max qty among local/express
      return [
        localOrExpress.reduce(
          (max, curr) => (Number(curr.value) > Number(max.value) ? curr : max)
        )
      ];
    }

    // 2️⃣ Otherwise pick first global that satisfies qty
    const global = eligible.find(inv => getType(inv) === "global");
    return global ? [global] : [];
  });
};



const assignAddressMapperDataToProducts = (productsArr, addressMapperResponse) => {
  if (!addressMapperResponse?.data?.response?.length) {
    return productsArr;
  }

  const addressLookup = new Map();
  addressMapperResponse.data.response.forEach(addr => {
    if (addr?.warehouse_id && addr?.data) {
      addressLookup.set(addr.warehouse_id, addr.data);
    }
  });

  return productsArr.map(product => {
    const addressData = addressLookup.get(product.warehouseId);
    if (addressData) {
      return {
        ...product,
        estimatedDate: addressData.estimated_date,
        minEstimatedDate: addressData.estimated_date_min,
        maxEstimatedDate: addressData.estimated_date_max,
        minSla: addressData.min_sla,
        maxSla: addressData.max_sla
      };
    }
    return product;
  });
};


const calculateHighestSLADate = (orderProducts, shipmentType) => {
  if (!orderProducts?.length) return 'TBD';

  const parseDate = (dateStr) => dateStr && (dateStr instanceof Date ? dateStr : new Date(dateStr));
  
  const formatSLADate = (date) => {
    const day = String(date.getDate()).padStart(2, '0');
    const month = date.toLocaleString('en-US', { month: 'short' });
    const year = date.getFullYear();
    return `${day} ${month} ${year}`;
  };

  const isGlobal = shipmentType === "global";
  let result = { min: null, max: null, highest: null };


  orderProducts.forEach(product => {
    const minDate = parseDate(product.minEstimatedDate);
    const maxDate = parseDate(product.maxEstimatedDate);
    const estimatedDate = parseDate(product.estimatedDate);

    if (isGlobal) {
      if (maxDate && (!result.max || maxDate > result.max)) {
        result.max = maxDate;
      }
      if(minDate && (!result.min || minDate > result.min)) {
        result.min = minDate;
      }
    } else {
      const compareDate = estimatedDate || maxDate;
      if (compareDate && (!result.highest || compareDate > result.highest)) {
        result.highest = compareDate;
      }
    }
  });

  if (isGlobal && result.min && result.max) {
    return `${formatSLADate(result.min)} - ${formatSLADate(result.max)}`;
  }
  
  return result.highest ? formatSLADate(result.highest) : 'TBD';
};

const processOrder = (order, productLookupMap, shipmentType) => {
  const orderProducts = order.productSkus
    ?.map(sku => productLookupMap.get(sku))
    .filter(Boolean) || [];

  return {
    shipmentType: shipmentType.toLowerCase(),
    SLA: calculateHighestSLADate(orderProducts, shipmentType),
    products: orderProducts,
    addressDetails: order.addressDetails
  };
};

const transformOrdersAndExtractWarehouseIds = (orderSplitResult, productsArr) => {
  const ordersObject = {};
  const warehouseIds = new Set();
  
  Object.entries(orderSplitResult.orders || {}).forEach(([shipmentMode, orderList]) => {
    ordersObject[shipmentMode] = orderList.map(order => {
      if (order.warehouseIds) {
        order.warehouseIds.forEach(id => warehouseIds.add(id));
      }
      
      return processOrder(order, productsArr, shipmentMode);
    });
  });

  return {
    ordersObject,
    warehouseIds: Array.from(warehouseIds)
  };
};

exports.processOrderSplitLogic = async ({
  productsArr,
  quote,
  responseObject,
  orderSplitFlag,
  isClubShipment,
  storeId,
  xHeaderToken,
  cityId,
  countryId,
  childSkus,
  getStoreConfig,
  getProductSLADetails,
  calculateOrderSplit,
  logInfo,
  formatPrice,
  grandTotal,
  isVersion7 = false,
  orderCreation = false
}) => {
  let orderSplitShippingAmount = 0;
  let orderSplitResult = {};
  let bestinventoryOp = false


  if(orderSplitFlag){
    const shippingConfig = {
      consolidatedOrdersTotalThreshold: getStoreConfig(storeId, "consolidatedOrdersTotalThreshold") || 0,
      isCODEnabled: getStoreConfig(storeId, "isCODEnabled") || false
    };
    let [productSLADetails, addressMapperResponse] = await Promise.all([
      getProductSLADetails({ cityId, countryId, skus: childSkus, storeId }),
      fetchSLADetails({
        warehouseIds: productsArr.map(product => product.warehouseId).filter(Boolean),
        cityId,
        countryId
      })
    ]);

    let warehouseIdsFromSLA = await extractWarehouseIdsFromSLA(productSLADetails,xHeaderToken);
    logInfo("allWarehouseIds", warehouseIdsFromSLA);

    const whInventoryRes = productSLADetails

   if(productSLADetails && Array.isArray(productSLADetails) && productSLADetails.length > 0 && productsArr.length > 0){
    const warehouseThresholdQty = getStoreConfig(storeId, "warehouseThresholdQty") || 1;
    const bestInventory = await getBestInventory(productSLADetails, productsArr,warehouseThresholdQty);
    productSLADetails = bestInventory;
    logInfo("productSLADetails", productSLADetails);
    bestinventoryOp = true;
   }

    // Optimized: Backward compatibility - Only run when products are missing warehouseIds
    const productsMissingWarehouseIds = productsArr.filter(product => 
      !product.warehouseId || product.warehouseId.trim() === ''
    );
    
    if (productsMissingWarehouseIds.length > 0) {
      const defaultWarehouseId = getStoreConfig(storeId, "defaultWarehouseId") || "";
      const defaultFulfillmentMode = getStoreConfig(storeId, "defaultFulfillmentMode") || "";
      
      // logInfo('Backward compatibility triggered', {
      //   productsMissingWarehouseIds: productsMissingWarehouseIds.length,
      //   totalProducts: productsArr.length,
      //   defaultWarehouseId,
      //   defaultFulfillmentMode
      // }, xHeaderToken);
      
      if (defaultWarehouseId) {
        let slaDetailsUpdated = 0;
        let productsUpdated = 0;
        
        // Update products that don't have warehouseId
        productsMissingWarehouseIds.forEach(product => {
          product.warehouseId = defaultWarehouseId;
          product.fulfillmentMode = defaultFulfillmentMode;
          productsUpdated++;
        });

        // Update productSLADetails if they exist and have missing warehouseIds
        if (productSLADetails?.length > 0) {
          productSLADetails.forEach(slaDetail => {
            if (slaDetail.warehouse_details?.length > 0) {
              let detailUpdated = false;
              slaDetail.warehouse_details.forEach(warehouseDetail => {
                // Check for missing, null, or empty warehouseId
                if (!warehouseDetail.warehouseId || warehouseDetail.warehouseId.trim() === '') {
                  warehouseDetail.warehouseId = defaultWarehouseId;
                  warehouseDetail.fulfillment_mode = defaultFulfillmentMode;
                  detailUpdated = true;
                }
              });
              if (detailUpdated) slaDetailsUpdated++;
            }
          });
        }

        // Re-fetch addressMapperResponse with updated warehouseIds
        [addressMapperResponse] = await Promise.all([
          fetchSLADetails({
            warehouseIds: productsArr.map(product => product.warehouseId).filter(Boolean),
            cityId,
            countryId
          })
        ]);
        
        // Update productsArr with new address mapper data
        productsArr = assignAddressMapperDataToProducts(productsArr, addressMapperResponse);
        
        // Log summary of backward compatibility processing
        // logInfo('Backward compatibility completed', {
        //   defaultWarehouseId,
        //   defaultFulfillmentMode,
        //   slaDetailsUpdated,
        //   productsUpdated,
        //   totalSLADetails: productSLADetails?.length || 0,
        //   totalProducts: productsArr.length
        // }, xHeaderToken);
      } else {
        logInfo('No default warehouseId configured', 'Cannot apply backward compatibility', xHeaderToken);
      }
    } else {
      logInfo('Backward compatibility skipped', 'All products have warehouseIds', xHeaderToken);
    }
    
    const itemsMissingDetails = quote?.quoteItem?.filter(item => 
      !item.fulfillmentMode || !item.warehouseId
    ) || [];

const enhanceProductWithSLADetails = (product, slaDetails) => {
  const warehouseDetail = slaDetails?.warehouse_details?.[0];
  if (!warehouseDetail) return product;

  return {
    ...product,
    // SLA details
    estimatedDate: warehouseDetail.estimated_date,
    minEstimatedDate: warehouseDetail.min_estimated_date,
    maxEstimatedDate: warehouseDetail.max_estimated_date,
    minSla: warehouseDetail.min_sla,
    maxSla: warehouseDetail.max_sla,
    fulfillmentMode: warehouseDetail.fulfillment_mode || product.fulfillmentMode,
    warehouseId: warehouseDetail.warehouseId,
    // Seller details
    ...(['sellerId', 'sellerName', 'sellerCode', 'warehouseName', 'city', 'country',
         'countryCode', 'isStyliRegisteredSeller', 'deliveryType', 'midMileLocationId',
         'midMileLocationName', 'lastmileWarehouseId', 'lastmileLocationName',
         'fulfillmentType', 'shipmentType'].reduce((acc, key) => {
      if (warehouseDetail[key] !== undefined) acc[key] = warehouseDetail[key];
      return acc;
    }, {}))
  };
};

    //To update the all warehouseInfo into main response Object
    if (orderCreation) {
      const wareHouseDetails = await getBulkWarehouseDetails({ warehouseIds: warehouseIdsFromSLA, xHeaderToken });
      logInfo("wareHouseDetails", wareHouseDetails);

      if (whInventoryRes && Array.isArray(whInventoryRes) && whInventoryRes.length > 0) {
        //merge the whInventoryRes with the wareHouseDetails based on the warehouseId and update the whInventoryRes with the mergedWarehouseDetails
        const mergedWarehouseDetails = whInventoryRes.map(whInventory => {
          const wareHouseDetail = wareHouseDetails.find(wareHouse => wareHouse.warehouseId === whInventory.warehouse_details[0].warehouseId);
          logInfo("wareHouseDetail", wareHouseDetail, whInventory.warehouse_details[0]);

          const resObj = {
            sellerId: wareHouseDetail?.sellerId,
            sellerName: wareHouseDetail?.sellerName,
            warehouseId: wareHouseDetail?.warehouseId,
            fulfillmentMode: wareHouseDetail?.fulfillmentMode,
            fulfillmentType: wareHouseDetail?.fulfillmentType,
            deliveryType: wareHouseDetail?.deliveryType,
            city: wareHouseDetail?.city,
            country: wareHouseDetail?.country,
            firstMileLocationId: wareHouseDetail?.warehouseId || null,
            firstMileLocationName: wareHouseDetail?.warehouseName || null,
            midMileLocationId: wareHouseDetail?.midMileLocationId || null,
            midMileLocationName: wareHouseDetail?.midMileLocationName || null,
            lastmileWarehouseId: wareHouseDetail?.lastmileWarehouseId || null,
            lastmileLocationName: wareHouseDetail?.lastmileLocationName || null,
            lastmileWarehouseName: wareHouseDetail?.lastmileWarehouseName || null,
            estimatedDate: whInventory.warehouse_details[0].estimated_date,
            minEstimatedDate: whInventory.warehouse_details[0].min_estimated_date,
            maxEstimatedDate: whInventory.warehouse_details[0].max_estimated_date,
            minSla: whInventory.warehouse_details[0].min_sla,
            maxSla: whInventory.warehouse_details[0].max_sla,
            warehouseName: wareHouseDetail?.warehouseName
          }
          return resObj;
        });
        logInfo("mergedWarehouseDetails", mergedWarehouseDetails);
        responseObject.warehouseDetails = mergedWarehouseDetails;
      }

    }

// Optimized: Process SLA details and update products in parallel
if (itemsMissingDetails.length > 0 && productSLADetails?.length > 0 || bestinventoryOp) {
  const warehouseIds = extractWarehouseIdsFromBestInventory(productSLADetails,xHeaderToken);
  // logInfo("Extracted warehouse IDs", `Found ${warehouseIds.length}`, xHeaderToken);

  let enhancedProductSLADetails = productSLADetails;


  [addressMapperResponse] = await Promise.all([
    fetchSLADetails({
      warehouseIds: productsArr.map(product => product.warehouseId).filter(Boolean),
      cityId,
      countryId
    })
  ]);

  // Update productsArr with new address mapper data
  productsArr = assignAddressMapperDataToProducts(productsArr, addressMapperResponse);
  
  // Get warehouse seller details if needed
  if (warehouseIds.length > 0) {
    const warehouseSellerDetails = await getBulkWarehouseDetails({ warehouseIds, xHeaderToken });
    
    enhancedProductSLADetails = mapSellerDetailsToProducts(
      productSLADetails,
      warehouseSellerDetails,
      xHeaderToken
    );
  }

  if (quote.quoteItem?.length > 0) {
    try {
      // Create lookup map for efficient product matching
      const slaLookup = new Map(enhancedProductSLADetails.map(sla => [sla.sku, sla]));
      
      // Update quote items and products array efficiently
      quote.quoteItem = quote.quoteItem.map(product => 
        enhanceProductWithSLADetails(product, slaLookup.get(product.sku))
      );
      
      productsArr = productsArr.map(product =>
        enhanceProductWithSLADetails(product, slaLookup.get(product.sku))
      );

      // Parallel operations: Save quote and continue processing
      const quoteCollection = await collection();
      await upsertQuote({ storeId, quote, collection: quoteCollection, xHeaderToken });

      // logInfo(
      //   "Updated quote with seller details in Couchbase",
      //   `Quote ${quote.id} updated with ${productsArr.length} products`,
      //   xHeaderToken
      // );
    } catch (error) {
      logError(error, "Error updating quote with seller details in Couchbase", xHeaderToken);
    }
  }
} else {
  logInfo(
    "Skipping bulk warehouse API",
    itemsMissingDetails.length === 0 ? "All items have fulfillment details" : "No product SLA details found",
    xHeaderToken
  );
}

    // Assign address mapper data to products (already fetched in parallel)
    productsArr = assignAddressMapperDataToProducts(productsArr, addressMapperResponse);

    // Get shipping configuration from consul and build config structure
    const shippingConfigFromConsul = getStoreConfig(storeId,"shippingConfig") || {};

    // Build config structure for new centralized functions
    const shipmentConfig = {
      orderSplitFlag: true,
      shipmentMode: Object.keys(shippingConfigFromConsul).filter(key =>
        typeof shippingConfigFromConsul[key] === 'object' && shippingConfigFromConsul[key] !== null
      ),
      shippingConfig: shippingConfigFromConsul
    };

    // Flatten mode-level config so calculateOrderSplit gets local/global thresholds and amounts (same as local shipping)
    const fullShippingConfig = {
      consolidatedOrdersTotalThreshold: getStoreConfig(storeId, "consolidatedOrdersTotalThreshold") || 0,
      isCODEnabled: getStoreConfig(storeId, "isCODEnabled") || false
    };
    (shipmentConfig.shipmentMode || []).forEach(mode => {
      const modeConfig = shippingConfigFromConsul[mode] || {};
      fullShippingConfig[`${mode}ShippingThreshold`] = modeConfig[`${mode}ShippingThreshold`] ?? modeConfig[`${mode}ShipmentThreshold`] ?? 0;
      fullShippingConfig[`${mode}ShippingAmount`] = modeConfig[`${mode}ShippingAmount`] ?? modeConfig[`${mode}ShipmentAmount`] ?? 0;
    });

    // Calculate order split using the dynamic helper (full config so global + local shipping are included)
    orderSplitResult = calculateOrderSplit({
      products: productsArr,
      shippingConfig: fullShippingConfig,
      shipmentModes: shipmentConfig.shipmentMode,
      xHeaderToken,
      isClubShipment
    });

    // Create product lookup map for efficient order processing
    const productLookupMap = new Map(productsArr.map(product => [product.sku, product]));
    
    // Combined transformation and warehouse ID extraction
    const { ordersObject, warehouseIds: allWarehouseIds } = transformOrdersAndExtractWarehouseIds(orderSplitResult, productLookupMap);
    
    // Store shipping amount for grand total calculation
    orderSplitShippingAmount = orderSplitResult.totalShippingAmount;

    // Pre-calculate split products to avoid redundant calls
    const splitProducts = splitProductsByFulfillmentMode(productsArr, shipmentConfig);
    
    // Parallel operations: Process address details and generate shipping info
    const [updatedShipping] = await Promise.all([
      Promise.resolve(generateShippingAmountArray(shipmentConfig, splitProducts,quote,storeId))
    ]);

    // Log address processing results

    // Calculate total remaining shipping amount efficiently
    const totalRemainingShippingAmount = updatedShipping.reduce((sum, item) => sum + (item.remainshippingAmount || 0), 0);

    // console.log("totalRemainingShippingAmount", totalRemainingShippingAmount);

    // Update response object totals if remaining shipping amount exists
    if (totalRemainingShippingAmount > 0) {
      const prevShippingAmount = parseFloat(responseObject.shippingAmount) || 0;
      const adjustmentAmount = totalRemainingShippingAmount - prevShippingAmount;
      
      if (adjustmentAmount !== 0) {
        ['grandTotal', 'baseGrandTotal', 'estimatedTotal'].forEach(key => {
          const currentValue = parseFloat(responseObject[key]) || 0;
          responseObject[key] = String(formatPrice(currentValue + adjustmentAmount));
        });
      }
    }


    // Update grandTotal, baseGrandTotal, estimatedTotal if totalRemainingShippingAmount is 0
    if(totalRemainingShippingAmount === 0 ){
      const prevShippingAmount = parseFloat(responseObject.shippingAmount) || 0;
      // console.log("OederSplitHelper :: prevShippingAmount", prevShippingAmount);
      if (prevShippingAmount !== 0) {
        // console.log("OederSplitHelper :: prevShippingAmount is not 0");
        ['grandTotal', 'baseGrandTotal', 'estimatedTotal'].forEach(key => {
          const currentValue = parseFloat(responseObject[key]) || 0;
          // Clamp to 0 to avoid negative totals (e.g. when store credit + free shipping)
          const newValue = Math.max(0, currentValue - prevShippingAmount);
          responseObject[key] = String(formatPrice(newValue));
        });
      }
    }


    // Optimized: Build response object efficiently
    const summary = orderSplitResult?.summary || {};
    const totalShippingAmount = orderSplitResult.totalShippingAmount || 0;
    const totalNumberOfShipments = orderSplitResult.totalNumberOfShipments || 0;
    
    // Extract dynamic counts from summary
    const dynamicCounts = Object.entries(summary)
      .filter(([key]) => key.includes('count') && !['totalOrders'].includes(key))
      .reduce((acc, [key, value]) => ({ ...acc, [key]: value }), {});

    const baseConfig = cache.get('baseConfig') || {};
    const enableAdvancedCodCharges = baseConfig?.enableAdvancedCodCharges ?? false;
    const paymentMethod = quote?.quotePayment?.method || '';
    const isCodPayment = paymentMethod === 'cashondelivery';
    
    // console.log('[COD CHARGES] processOrderSplitLogic - enableAdvancedCodCharges:', enableAdvancedCodCharges, 'isVersion7:', isVersion7, 'isCodPayment:', isCodPayment, 'shippingAmount items:', (updatedShipping || []).length);
    
    let shippingAmountWithCod = updatedShipping || [];
    
    if (enableAdvancedCodCharges && isVersion7) {
      // console.log('[COD CHARGES] processOrderSplitLogic - Feature flag enabled and version7, appending COD charges to shippingAmount array');
      shippingAmountWithCod = (updatedShipping || []).map(shippingItem => {
        const result = { ...shippingItem };
        
        if (isCodPayment) {
          const shipmentMode = shippingItem.shipmentMode?.toLowerCase();
          let codCharges = 0;
          
          if (shipmentMode === 'express') {
            codCharges = Number(quote.expressCodCharges || 0);
            // console.log('[COD CHARGES] processOrderSplitLogic - Express shipment mode, codCharges:', codCharges);
          } else if (shipmentMode === 'global') {
            codCharges = Number(quote.globalCodCharges || 0);
            // console.log('[COD CHARGES] processOrderSplitLogic - Global shipment mode, codCharges:', codCharges);
          } else {
            codCharges = Number(quote.expressCodCharges || quote.codCharges || 0);
            // console.log('[COD CHARGES] processOrderSplitLogic - Default shipment mode, codCharges:', codCharges);
          }
          
          result.codCharges = String(formatPrice(codCharges));
          // console.log('[COD CHARGES] processOrderSplitLogic - Added codCharges to shippingItem:', {
          //   shipmentMode: shippingItem.shipmentMode,
          //   codCharges: result.codCharges
          // });
        } else {
          result.codCharges = "0";
          // console.log('[COD CHARGES] processOrderSplitLogic - Non-COD payment, setting codCharges to 0');
        }
        
        return result;
      });
    } else {
      // console.log('[COD CHARGES] processOrderSplitLogic - Feature flag disabled or not version7, keeping traditional shippingAmount (no codCharges field)');
    }

    // Update response object properties
    Object.assign(responseObject, {
      shippingAmount: shippingAmountWithCod,
      isCODEnabled: shippingConfig.isCODEnabled,
      consolidatedOrdersTotalThreshold: 
        shippingConfig?.consolidatedOrdersTotalThreshold > 0 && 
        summary?.totalSubtotal > shippingConfig.consolidatedOrdersTotalThreshold 
          ? 0 : shippingConfig.consolidatedOrdersTotalThreshold,
      totalNumberOfShipments,
      orderSplitInfo: {
        isClubShipment: orderSplitResult?.orderSplitInfo?.isClubShipment,
        orders: ordersObject,
        totalNumberOfShipments,
        totalShippingAmount: String(formatPrice(totalShippingAmount)),
        summary: {
          totalOrders: summary?.totalOrders,
          totalSubtotal: String(formatPrice(summary?.totalSubtotal || 0)),
          totalGrandTotal: String(formatPrice(summary?.totalGrandTotal || 0)),
          ...dynamicCounts
        }
      }
    });

    // Update shipping charges if needed
    if (totalShippingAmount > 0) {
      quote.shippingCharges = totalShippingAmount;
    }

    // logInfo("Order split calculation completed", {
    //   totalOrders: totalNumberOfShipments,
    //   totalShippingAmount,
    //   isClubShipment
    // }, xHeaderToken);
   } else {
    // Optimized: Default order split info for non-split orders
    responseObject.orderSplitInfo = {
      isOrderSplit: false,
      totalNumberOfShipments: 1,
      totalShippingAmount: String(formatPrice(quote.shippingCharges || 0)),
      orders: {},
      summary: {
        totalOrders: 1,
        totalSubtotal: String(formatPrice(quote.subtotal || 0)),
        totalGrandTotal: String(formatPrice(grandTotal || 0))
      }
    };
   }

  // Do not add orderSplitShippingAmount here; all split shipping (local, global, club) is applied via totalRemainingShippingAmount adjustment above. Do not rely on isClubShipment.

  // Re-apply store credit against full total (including global shipping) so same mechanism as local
  if (orderSplitFlag && quote?.amstorecreditUse && Number(quote.amstorecreditAmount || 0) > 0) {
    const currentGrandTotal = parseFloat(responseObject.grandTotal) || 0;
    const storeCreditBalance = parseFloat(responseObject.storeCreditBalance || 0) || 0;
    const appliedSoFar = parseFloat(quote.amstorecreditAmount || 0) || 0;
    const fullTotalBeforeStoreCredit = currentGrandTotal + appliedSoFar;
    const storeCreditToApply = Math.min(fullTotalBeforeStoreCredit, storeCreditBalance);
    const newGrandTotal = Math.max(0, fullTotalBeforeStoreCredit - storeCreditToApply);
    responseObject.grandTotal = String(formatPrice(newGrandTotal));
    responseObject.baseGrandTotal = String(formatPrice(newGrandTotal));
    responseObject.storeCreditApplied = storeCreditToApply > 0 ? String(formatPrice(storeCreditToApply)) : null;
  }

  // Add orders array to main response object for route level access
  // console.log("orderSplitResult", orderSplitResult);

  responseObject.products = productsArr;

  return responseObject;
}