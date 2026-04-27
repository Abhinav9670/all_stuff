
const { getQuoteTotals: v6GetQuoteTotals } = require('../v6/quoteController.js');
const { getQuote: v6GetQuote, getQuoteOptimized: v6GetQuoteOptimized } = require('../v6/quoteController.js');
const {
  getStoreConfig,
  getAppConfigKey
} = require("../../helpers/utils");
const { hasGlobalProducts } = require('../../helpers/v6/codCharges');
const cache = require('memory-cache');


let dateCache = { todayStr: null, tomorrowStr: null, lastCalculated: null };

/**
 * @param {Object} data - The original data object
 * @param {Object} shippingConfig - Shipping configuration with mappings
 * @returns {Object} - Updated data with replaced modes
 */
function replaceModes(data, shippingConfig,storeId,isClubShipment) {
  const arabicStores = getAppConfigKey('arabicStores') || [3, 11, 13];

  const lang = (arabicStores.indexOf(Number(storeId)) > -1) ? 'ar' : 'en';

  const todayArabicText = getAppConfigKey('todayArabicText');
  const tomorrowArabicText = getAppConfigKey('tomorrowArabicText');
  const isOrderSplit=getStoreConfig(storeId, "orderSplitFlag") || false;

  if(!data?.orderSplitInfo){
    data.orderSplitInfo = {
      isOrderSplit: isOrderSplit,
      isClubShipment: isClubShipment,
      totalNumberOfShipments: 0,
      totalShippingAmount: 0,
      orders: {},
      summary: {
        totalOrders: 0,
        totalSubtotal: 0,
        totalGrandTotal: 0
      }
    };
  }
  // Ultra-fast validation - minimal checks

  if(data.shippingAmount && !Array.isArray(data.shippingAmount)){
    data.shippingAmount = [];
  }
  if (!data?.products?.length || !shippingConfig) return data;
  
  try {
    const mapping = new Map();
    let hasMapping = false;
    
    for (const key in shippingConfig) {
      const config = shippingConfig[key];
      if (config?.shipmentMapping) {
        mapping.set(key, config.shipmentMapping);
        hasMapping = true;
      }
    }
    
    // Early exit if no mappings
    if (!hasMapping) return data;
    
    // Smart cloning - only clone if we have mappings that will be used
    let updated = data;
    let needsCloning = false;
    
    // Quick check if any transformations are needed
    const hasShippingAmountChanges = data.shippingAmount?.some(s => mapping.has(s?.shipmentMode));
    const hasProductChanges = data.products.some(p => mapping.has(p?.fulfillmentMode));
    const hasOrderChanges = data.orderSplitInfo?.orders && 
      Object.keys(data.orderSplitInfo.orders).some(mode => mapping.has(mode));
    
    needsCloning = hasShippingAmountChanges || hasProductChanges || hasOrderChanges;
    
    if (!needsCloning) return data;
    
    // Minimal cloning - only clone what we need
    updated = {
      ...data,
      shippingAmount: hasShippingAmountChanges ? [...data.shippingAmount] : data.shippingAmount,
      products: hasProductChanges ? [...data.products] : data.products,
      orderSplitInfo: hasOrderChanges ? {
        ...data.orderSplitInfo,
        orders: { ...data.orderSplitInfo.orders }
      } : data.orderSplitInfo
    };
    
    // Cached date calculation - only calculate once per minute
    const now = Date.now();
    if (!dateCache.lastCalculated || (now - dateCache.lastCalculated) > 60000) {
      const today = new Date();
      const tomorrow = new Date(today);
      tomorrow.setDate(today.getDate() + 1);
      
      const formatDate = (date) => {
        const day = String(date.getDate()).padStart(2, "0");
        const month = date.toLocaleString("en-US", { month: "short" });
        return `${day} ${month} ${date.getFullYear()}`;
      };
      
      dateCache.todayStr = formatDate(today);
      dateCache.tomorrowStr = formatDate(tomorrow);
      dateCache.lastCalculated = now;
    }
    
    if (hasShippingAmountChanges) {
      for (let i = 0; i < updated.shippingAmount.length; i++) {
        const item = updated.shippingAmount[i];
        if (item?.shipmentMode) {
          const newMode = mapping.get(item.shipmentMode);
          if (newMode) {
            updated.shippingAmount[i] = { ...item, shipmentMode: newMode };
          }
        }
      }
    }
    
    if (hasProductChanges) {
      for (let i = 0; i < updated.products.length; i++) {
        const product = updated.products[i];
        if (product?.fulfillmentMode) {
          const newMode = mapping.get(product.fulfillmentMode);
          if (newMode) {
            updated.products[i] = { ...product, fulfillmentMode: newMode };
          }
        }
      }
    }
    
    if (hasOrderChanges) {
      const orders = updated.orderSplitInfo.orders;
      const keysToProcess = Object.keys(orders).filter(key => mapping.has(key));
      
      for (const fromMode of keysToProcess) {
        const toMode = mapping.get(fromMode);
        const orderList = orders[fromMode];
        
        if (Array.isArray(orderList) && toMode !== fromMode) {
          // Special handling for local → express with SLA transformation
          if (fromMode === "local") {
            orders[toMode] = orderList.map(shipment => {
              const updated = { ...shipment, shipmentType: toMode };
              
              if (updated.SLA) {
                if (updated.SLA.includes(dateCache.todayStr)) {
                  updated.SLA = lang === 'ar' ? todayArabicText : "Today";
                } else if (updated.SLA.includes(dateCache.tomorrowStr)) {
                  updated.SLA =  lang === 'ar' ? tomorrowArabicText :"Tomorrow";
                }
              }
              
              return updated;
            });
          } else {
            // Just copy and update shipment type
            orders[toMode] = orderList.map(s => ({ ...s, shipmentType: toMode }));
          }
          
          delete orders[fromMode];
        }
      }
    }
    
    return updated;
    
  } catch (error) { // Minimal error handling - just return original data
    // console.error('[replaceModes] Error:', error.message);
    return data;
  }
}




exports.getQuoteTotals = async ({ req, res, pool }) => {
  try {
    req.body["isVersion7"] = true;
    const { storeId, orderCreation = false, isClubShipment = false } = req.body;
    
    const v6GetQuoteTotalsResponse = await v6GetQuoteTotals({ req, res, pool });
    
    if (!v6GetQuoteTotalsResponse) {
      // console.log("Quote totals response is null");
      return false;
    }
    
    
    const removeCodForGlobalShipment = getStoreConfig(storeId, 'removecodforglobalshipment') || false;
    if (removeCodForGlobalShipment) {
      let productsToCheck = [];
      
      if (v6GetQuoteTotalsResponse?.products && Array.isArray(v6GetQuoteTotalsResponse.products)) {
        productsToCheck = v6GetQuoteTotalsResponse.products;
      } else if (v6GetQuoteTotalsResponse?.orderSplitInfo?.orders) {
        const orders = v6GetQuoteTotalsResponse.orderSplitInfo.orders;
        for (const orderList of Object.values(orders)) {
          if (Array.isArray(orderList)) {
            for (const shipment of orderList) {
              if (Array.isArray(shipment?.products)) {
                productsToCheck = productsToCheck.concat(shipment.products);
              }
            }
          }
        }
      }
      
      if (hasGlobalProducts(productsToCheck)) {
        if (v6GetQuoteTotalsResponse?.availablePaymentMethods && Array.isArray(v6GetQuoteTotalsResponse.availablePaymentMethods)) {
          v6GetQuoteTotalsResponse.availablePaymentMethods = v6GetQuoteTotalsResponse.availablePaymentMethods.filter(
            method => method !== 'cashondelivery' && method !== 'cashOnDelivery' && method !== 'CASH_ON_DELIVERY'
          );
          // console.log('[V7 TOTAL API] Removed cashondelivery from availablePaymentMethods due to global shipment products');
        }
      }
    }
    
    if (orderCreation) {
      const shippingConfig = getStoreConfig(storeId, "shippingConfig");
      if (shippingConfig) {
        const transformedResponse = replaceModes(v6GetQuoteTotalsResponse, shippingConfig,storeId,isClubShipment);
        if (removeCodForGlobalShipment && transformedResponse) {
          let productsToCheckAfterTransform = [];
          if (transformedResponse?.products && Array.isArray(transformedResponse.products)) {
            productsToCheckAfterTransform = transformedResponse.products;
          } else if (transformedResponse?.orderSplitInfo?.orders) {
            const orders = transformedResponse.orderSplitInfo.orders;
            for (const orderList of Object.values(orders)) {
              if (Array.isArray(orderList)) {
                for (const shipment of orderList) {
                  if (Array.isArray(shipment?.products)) {
                    productsToCheckAfterTransform = productsToCheckAfterTransform.concat(shipment.products);
                  }
                }
              }
            }
          }
          
          if (hasGlobalProducts(productsToCheckAfterTransform)) {
            if (transformedResponse?.availablePaymentMethods && Array.isArray(transformedResponse.availablePaymentMethods)) {
              transformedResponse.availablePaymentMethods = transformedResponse.availablePaymentMethods.filter(
                method => method !== 'cashondelivery' && method !== 'cashOnDelivery' && method !== 'CASH_ON_DELIVERY'
              );
              // console.log('[V7 TOTAL API] Removed cashondelivery from availablePaymentMethods after transformation due to global shipment products');
            }
          }
        }
        
        // Remove products if they exist (optimization for totals endpoint)
        if (transformedResponse?.products) {
          delete transformedResponse.products;
        }
        
        return res.status(200).json({
          status: true,
          statusCode: "200",
          statusMsg: "quote found!",
          response: transformedResponse,
        });
      }
    }
    
    // Fast path - remove products if they exist
    if (v6GetQuoteTotalsResponse?.products) {
      delete v6GetQuoteTotalsResponse.products;
    }
    
    return res.status(200).json({
      status: true,
      statusCode: "200",
      statusMsg: "quote found!",
      response: v6GetQuoteTotalsResponse,
    });
    
  } catch (error) {
    // console.error("Error in v7 getQuoteTotals:", error);
    return res.status(500).json({
      status: false,
      statusCode: "500",
      statusMsg: "Internal server error in v7 getQuoteTotals: " + error.message,
    });
  }
};

exports.getQuote = async function ({ req, res, pool, resetNotifs = true }) {
  try {    
    req.body["isVersion7"] = true;
    const { storeId, orderCreation = false, isClubShipment = false } = req.body;
    
    const baseConfig = cache.get("baseConfig") || {};
    const enablev6getoptimization = baseConfig?.enablev6getoptimization || false;
    const v6GetQuoteResponse = enablev6getoptimization === true || req?.body?.optimized === true
      ? await v6GetQuoteOptimized({ req, res, pool, resetNotifs })
      : await v6GetQuote({ req, res, pool, resetNotifs });
    
    if (!v6GetQuoteResponse?.response) {
      // console.log("Quote response is null");
      return false;
    }
    
    if (!orderCreation && !isClubShipment) {
      const shippingConfig = getStoreConfig(storeId, "shippingConfig");
      if (shippingConfig) {
        v6GetQuoteResponse.response = replaceModes(v6GetQuoteResponse.response, shippingConfig,storeId,isClubShipment);
      }
    }
    return res.status(200).json(v6GetQuoteResponse);
    
  } catch (error) {
    // console.error("Error in v7 getQuote:", error);
    return res.status(500).json({
      status: false,
      statusCode: "500",
      statusMsg: "Internal server error in v7 getQuote: " + error.message,
    });
  }
};