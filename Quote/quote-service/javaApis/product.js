const axios = require('axios');
const { logError, logInfo } = require('../helpers/utils');
const _ = require('lodash');
const { AUTH_INTERNAL_HEADER_BEARER_TOKEN } = process.env;
const internalAuthToken = AUTH_INTERNAL_HEADER_BEARER_TOKEN?.split(',')?.[0];
const { getAppConfigKey } = require('../helpers/utils');

exports.inventoryCheck = async ({
  inventoryRequestObject,
  xHeaderToken,
  orderCreation
}) => {
  if ((inventoryRequestObject?.skus?.length || 0) < 1 || orderCreation)
    return [];

  try {
    logInfo(
      'process.env.INVENTORY_CHECK',
      `${process.env.INVENTORY_CHECK}`,
      xHeaderToken
    );
    const response = await axios.post(
      process.env.INVENTORY_CHECK,
      inventoryRequestObject,
      {
        headers: {
          'Content-Type': 'application/json',
          'authorization-token': internalAuthToken,
          'source': 'quote-service'
        }
      }
    );
    // const responseProductArray = response?.data?.response?.productStatus;
    const responseProductArray = response?.data?.response;
    return responseProductArray;
  } catch (e) {
    logError(e, 'Error inventory check : ', xHeaderToken);
    // console.log('Error inventory check : ', e.message);
  }
};

const getInvStockRows = async ({ pool, productIds }) => {
  const productFilter = productIds.length === 0 ? '' : productIds.join("','");
  let query =
    "SELECT * FROM `cataloginventory_stock_item` WHERE product_id in ('" +
    productFilter +
    "') AND stock_id = 1";
  const [rows, fields] = await pool.query(query);
  return rows;
};

const getInvResRows = async ({ pool, skus }) => {
  const skuFilter = skus.length === 0 ? '' : skus.join("','");
  let query =
    "SELECT * FROM `inventory_reservation` WHERE sku in ('" +
    skuFilter +
    "') AND stock_id = 1";
  const [rows, fields] = await pool.query(query);
  // const [rows, fields] = await pool.query("SELECT * FROM `inventory_reservation` WHERE sku in (?) AND stock_id = ?", [skus.join(','), 1])
  return rows;
};

exports.inventoryCheckFromDB = async ({
  productsMap,
  orderCreation,
  pool,
  xHeaderToken
}) => {
  if (orderCreation || _.isEmpty(productsMap)) return [];
  // const productIds = [...productsMap.keys()];
  // const productIds = Array.from(productsMap.keys());
  const productIds = new Array();
  const skus = new Array();
  for (let key in productsMap) {
    productIds.push(key);
    skus.push(productsMap[key]);
  }

  let response = [];
  try {
    const promisesArray = [
      getInvStockRows({ pool, productIds }),
      getInvResRows({ pool, skus })
    ];
    const resPromiseAll = await Promise.all(promisesArray).then(values => {
      return values;
    });
    const invStockRows = resPromiseAll[0];
    // console.log("rows in get inv", invStockRows);

    const invResRows = resPromiseAll[1];
    // console.log("rows in get reservations", invResRows);

    for (const invStockItem of invStockRows) {
      let value = 0;
      if (invStockItem.is_in_stock == 1) {
        value = invResRows
          .filter(el => el.sku === productsMap[invStockItem.product_id])
          .reduce((sum, n) => sum + Number(n.quantity), 0);
        value = value + Number(invStockItem.qty);
      }
      response.push({
        procuctId: invStockItem.product_id,
        sku: productsMap[invStockItem.product_id],
        value
      });
    }

    // console.log("debug+++++++++", response);
    return response;
  } catch (e) {
    logError(e, 'Error inventory check : ', xHeaderToken);
    // console.log('Error inventory check : ', e.message);
  }
};

exports.getSellerWarehouseDetails = async ({
  warehouseId
}) => {
  if (!warehouseId)
    return [];

  // Validate warehouseId to ensure it's safe for URL construction
  if (typeof warehouseId !== 'string' || !/^[a-zA-Z0-9_-]+$/.test(warehouseId)) {
    logError(new Error('Invalid warehouseId format'), 'Invalid warehouseId provided', warehouseId);
    return [];
  }

  try {
    logInfo(
      'process.env.INVENTORY_API',
      `${process.env.INVENTORY_API}`
    );
    const inventoryApi = getAppConfigKey('inventoryApi') || 'https://inventoryapi.stylishop.store/api';
    logInfo(`INVENTORY_API::${inventoryApi}`);
    const sellerUrl = '/v1/seller-details/warehouse';

    const fullUrl = `${inventoryApi}${sellerUrl}/${warehouseId}`;

    const response = await axios.get(
      fullUrl,
      {
        headers: {
          'Content-Type': 'application/json',
          'authorization-token': internalAuthToken,
          'source': 'quote-service'
        }
      }
    );
    const responseProductArray = response?.data?.response;
    return responseProductArray;
  } catch (e) {

    // console.log('Error inventory check : ', e.message);
  }
};

exports.fetchFulfillmentMode = async ({
  skus,
  storeId,
  cityId
}) => {
  if (!skus || !storeId || !cityId)
    return [];

  try {
    logInfo(
      'process.env.INVENTORY_API',
      `${process.env.INVENTORY_API}`
    );
    const inventoryApi = getAppConfigKey('inventoryApi') || 'https://inventoryapi.stylishop.store/api';
    const fullUrl = `${inventoryApi}/inventory/storefront/v2/atp`;

    const response = await axios.post(
      fullUrl,
      {
        city_id : cityId,skus, storeId : storeId
      },
      {
        headers: {
          'Content-Type': 'application/json',
          'authorization-token': internalAuthToken,
          'source': 'quote-service'
        }
      }
    );
    const responseProductArray = response?.data?.response;
    const fulfillmentMode = this.getFulfillmentModeForSku(responseProductArray, skus[0]); 
    return fulfillmentMode;
  } catch (e) {

    // console.log('Error inventory check : ', e.message);
  }
};


exports.getFulfillmentModeForSku = (responseArray, targetSku) => {
  if (!responseArray || !Array.isArray(responseArray) || !targetSku) {
    return null;
  }

  // Find the product with the matching SKU
  const product = responseArray.find(item => item.sku === targetSku);

  if (!product || !product.warehouse_details || !Array.isArray(product.warehouse_details)) {
    return null;
  }

  // Get the fulfillment_mode from the first warehouse detail (or you can modify logic if needed)
  const warehouseDetail = product.warehouse_details[0];

  return warehouseDetail?.fulfillment_mode || null;
};


exports.getProductSLADetails = async ({
  cityId,
  countryId,
  skus,
  storeId
}) => {
  if (!cityId)
    return [];

  try {
    logInfo(
      'process.env.INVENTORY_API',
      `${process.env.INVENTORY_API}`
    );
    const inventoryApi = getAppConfigKey('inventoryApi') || 'https://inventoryapi.stylishop.store/api';
    logInfo(`INVENTORY_API::${inventoryApi}`);
    const response = await axios.post(
      `${inventoryApi}/inventory/storefront/v2/atp`,
      {
        city_id : cityId,country_id : countryId,skus, storeId : storeId, isQuote: true
      },
      {
        headers: {
          'Content-Type': 'application/json',
          'authorization-token': internalAuthToken,
          'source': 'quote-service'
        }
      }
    );
    const responseProductArray = response?.data?.response;
    return responseProductArray;
  } catch (e) {
    // console.log('Error inventory check : ', e.message);
  }
};

/**
 * Get bulk warehouse seller details for multiple warehouseIds
 * @param {Array} warehouseIds - Array of warehouse IDs
 * @param {string} xHeaderToken - Request header token for logging
 * @returns {Array} Array of seller details mapped to warehouse IDs
 */
exports.getBulkWarehouseDetails = async ({
  warehouseIds,
  xHeaderToken
}) => {
  if (!warehouseIds || !Array.isArray(warehouseIds) || warehouseIds.length === 0) {
    // logInfo('No warehouse IDs provided for bulk warehouse details', '', xHeaderToken);
    return [];
  }

  try {
    // logInfo(
    //   'Calling bulk warehouse API',
    //   `warehouseIds: ${warehouseIds.join(', ')}`,
    //   xHeaderToken
    // );
    
    const inventoryApi = getAppConfigKey('inventoryApi') || 'https://inventoryapi.stylishop.store/api';
    const bulkWarehousePath = '/api/v1/seller-details/bulk-warehouse';
    
    // Construct URL safely
    const baseUrl = new URL(inventoryApi);
    const fullUrl = `${baseUrl.origin}${bulkWarehousePath}`;

    const response = await axios.post(
      fullUrl,
      {
        warehouseIds: warehouseIds
      },
      {
        headers: {
          'Content-Type': 'application/json',
          'authorization-token': internalAuthToken,
          'source': 'quote-service'
        }
      }
    );
    
    const responseData = response?.data?.response;
    // logInfo('Bulk warehouse API response received', `Found ${responseData?.length || 0} warehouse details`, xHeaderToken);
    
    return responseData || [];
  } catch (e) {
    logError(e, 'Error in getBulkWarehouseDetails: ', xHeaderToken);
    // console.log('Error getBulkWarehouseDetails: ', e.message);
    return [];
  }
};

/**
 * Extract unique warehouse IDs from product SLA details
 * @param {Array} productSLADetails - Array of product SLA details
 * @returns {Array} Array of unique warehouse IDs
 */
exports.extractWarehouseIdsFromSLA = (productSLADetails) => {
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
};

/**
 * Map seller details to products based on warehouse IDs
 * @param {Array} productSLADetails - Array of product SLA details
 * @param {Array} warehouseSellerDetails - Array of seller details from bulk API
 * @param {string} xHeaderToken - Request header token for logging
 * @returns {Array} Product SLA details enhanced with seller information
 */
exports.mapSellerDetailsToProducts = (productSLADetails, warehouseSellerDetails, xHeaderToken) => {
  if (!productSLADetails || !Array.isArray(productSLADetails) || 
      !warehouseSellerDetails || !Array.isArray(warehouseSellerDetails)) {
    return productSLADetails || [];
  }

  // Create a map of warehouseId to seller details for quick lookup
  const sellerDetailsMap = {};
  warehouseSellerDetails.forEach(seller => {
    if (seller.warehouseId) {
      sellerDetailsMap[seller.warehouseId] = seller;
    }
  });

  // Enhanced products with seller details
  const enhancedProducts = productSLADetails.map(product => {
    if (product.warehouse_details && Array.isArray(product.warehouse_details)) {
      const enhancedWarehouseDetails = product.warehouse_details.map(warehouse => {
        const sellerInfo = sellerDetailsMap[warehouse.warehouseId];
        
        if (sellerInfo) {
          return {
            ...warehouse,
            // Add seller details
            sellerId: sellerInfo.sellerId,
            sellerName: sellerInfo.sellerName,
            sellerCode: sellerInfo.sellerCode,
            warehouseName: sellerInfo.warehouseName,
            city: sellerInfo.city,
            country: sellerInfo.country,
            countryCode: sellerInfo.countryCode,
            isStyliRegisteredSeller: sellerInfo.isStyliRegisteredSeller,
            deliveryType: sellerInfo.deliveryType,
            midMileLocationId: sellerInfo.midMileLocationId,
            midMileLocationName: sellerInfo.midMileLocationName,
            lastmileWarehouseId: sellerInfo.lastmileWarehouseId,
            lastmileLocationName: sellerInfo.lastmileLocationName,
            fulfillmentType: sellerInfo.fulfillmentType,
            shipmentType: sellerInfo.shipmentType
          };
        }
        
        return warehouse;
      });

      return {
        ...product,
        warehouse_details: enhancedWarehouseDetails
      };
    }
    
    return product;
  });

  // logInfo('Mapped seller details to products', `Enhanced ${enhancedProducts.length} products`, xHeaderToken);
  return enhancedProducts;
};