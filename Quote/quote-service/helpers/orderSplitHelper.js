const { formatPrice, logInfo } = require("./utils");

const {
  getAppConfigKey,
  getStoreConfig
} = require("./utils");

/**
 * Calculate subtotal for a group of products - Optimized
 * @param {Array} products - Array of products
 * @returns {number} Subtotal amount
 */
const calculateSubtotal = (products) => {
  if (!products?.length) return 0;
  
  return products.reduce((total, product) => {
    if (product?.isGift === true || product?.isGift === 1) {
      return total;
    }
    // Get base price: use specialPrice only when it's a valid number (ignore "undefined" string / invalid)
    const rawSpecial = product?.prices?.specialPrice;
    const specialValid = rawSpecial != null && String(rawSpecial) !== "undefined";
    const specialNum = specialValid ? parseFloat(rawSpecial) : NaN;
    const price = Number.isFinite(specialNum)
      ? specialNum
      : (parseFloat(product?.prices?.price || product?.price || 0) || 0);
    const qty = parseInt(product.quantity || 1);
    let productTotal = price * qty;
    
    // Apply coupon discounts if they exist
    const coupons = product.appliedCouponValue;
    if (coupons?.length > 0) {
      const totalDiscount = coupons.reduce((sum, coupon) => 
        sum + parseFloat(coupon.discount || 0), 0
      );
      productTotal = Math.max(0, productTotal - totalDiscount);
    }
    
    return total + productTotal;
  }, 0);
};

/**
 * Calculate shipping amount for an order based on shipping configuration - Optimized
 * @param {Array} products - Array of products in the order
 * @param {Object} shippingConfig - Shipping configuration
 * @param {string} orderType - The shipment mode (e.g., local, global, express, etc.)
 * @returns {number} Shipping amount
 */
const calculateShippingForOrder = (products, shippingConfig, orderType) => {
  const subtotal = calculateSubtotal(products);
  const minimumShippingAmount = shippingConfig.minimumShippingAmount || 12;
  
  // Dynamic configuration lookup (support both Shipping* and Shipment* keys for global/local consistency)
  const threshold = shippingConfig[`${orderType}ShippingThreshold`] ?? shippingConfig[`${orderType}ShipmentThreshold`] ?? 0;
  const amount = shippingConfig[`${orderType}ShippingAmount`] ?? shippingConfig[`${orderType}ShipmentAmount`] ?? 0;

  // Calculate shipping: free if above threshold, otherwise minimum of amount or minimum
  const shippingAmount = subtotal >= threshold ? 0 : Math.max(amount, minimumShippingAmount);

  return formatPrice(shippingAmount);
};

/**
 * Create an order object with common properties - Optimized
 * @param {Array} products - Array of products
 * @param {Object} shippingConfig - Shipping configuration
 * @param {string} orderType - Type of order (express/premium/standard etc.)
 * @returns {Object} Order object
 */
const createOrder = (products, shippingConfig, orderType) => {
  const subtotal = calculateSubtotal(products);
  const shippingAmount = calculateShippingForOrder(products, shippingConfig, orderType);
  
  // Extract data in single pass
  const productSkus = [];
  const warehouseIds = [];
  
  products.forEach(product => {
    productSkus.push(product.sku);
    if (product.warehouseId) {
      warehouseIds.push(product.warehouseId);
    }
  });
  
  return {
    orderType,
    subtotal,
    shippingAmount,
    grandTotal: subtotal + shippingAmount,
    productsCount: products.length,
    country: orderType,
    productSkus,
    warehouseIds
  };
};

/**
 * Process products and create a single order - Optimized
 * @param {Array} products - Array of products
 * @param {Object} shippingConfig - Shipping configuration
 * @param {string} orderType - Type of order (express/premium/standard etc.)
 * @returns {Array} Array with single order
 */
const processProductsByDeliveryType = (products, shippingConfig, orderType) => {
  return products?.length > 0 ? [createOrder(products, shippingConfig, orderType)] : [];
};

/**
 * Create empty result structure for when no products exist
 * @param {Array} shipmentModes - Array of shipment modes
 * @returns {Object} Empty result structure
 */
const createEmptyResult = (shipmentModes) => {
  const result = {
    orders: {},
    totalShippingAmount: 0,
    totalNumberOfShipments: 0,
    orderSplitInfo: { isOrderSplit: false },
    summary: { totalOrders: 0, totalSubtotal: 0, totalGrandTotal: 0, isClubShipment: false }
  };
  
  // Initialize empty arrays for each mode
  shipmentModes.forEach(mode => {
    result.orders[mode] = [];
    result.orderSplitInfo[`${mode}Orders`] = [];
    result.orderSplitInfo[`${mode}ShippingAmount`] = 0;
  });
  
  return result;
};

/**
 * Process orders in a single optimized loop
 * @param {Object} splitProducts - Products split by shipment mode
 * @param {Object} shippingConfig - Shipping configuration
 * @param {Array} shipmentModes - Array of shipment modes
 * @param {boolean} isClubShipment - Whether this is a club shipment
 * @returns {Object} Processed result with orders and totals
 */
const processOrdersInSingleLoop = (splitProducts, shippingConfig, shipmentModes, isClubShipment) => {
  const result = {
    orders: {},
    totalShippingAmount: 0,
    totalNumberOfShipments: 0,
    orderSplitInfo: { isOrderSplit: false, isClubShipment },
    summary: { isClubShipment }
  };

  let totalSubtotal = 0;
  let totalGrandTotal = 0;
  const modeSummary = {};

  // Process all shipment modes in single loop
  shipmentModes.forEach(mode => {
    const modeProducts = splitProducts[mode] || [];
    const orders = modeProducts.length > 0 ? 
      processProductsByDeliveryType(modeProducts, shippingConfig, mode) : [];
    
    // Cache calculations to avoid recalculation
    const subtotal = orders.length > 0 ? orders[0].subtotal : 0;
    const grandTotal = orders.length > 0 ? orders[0].grandTotal : 0;
    const shippingAmount = orders.length > 0 ? orders[0].shippingAmount : 0;

    // Store results
    result.orders[mode] = orders;
    result.orderSplitInfo[`${mode}Orders`] = [...orders];
    
    // Update totals
    totalSubtotal += subtotal;
    totalGrandTotal += grandTotal;
    
    // Dynamic shipping threshold check (support both Shipping* and Shipment* keys for global/local consistency)
    const threshold = shippingConfig[`${mode}ShippingThreshold`] ?? shippingConfig[`${mode}ShipmentThreshold`] ?? 0;
    const amount = shippingConfig[`${mode}ShippingAmount`] ?? shippingConfig[`${mode}ShipmentAmount`] ?? 0;

    result.orderSplitInfo[`${mode}ShippingAmount`] =
      subtotal > threshold ? 0 : amount;
    
    // Store mode summary
    modeSummary[mode] = {
      count: orders.length,
      subtotal,
      grandTotal,
      shippingAmount
    };
  });

  // Calculate final shipping amount
  const consolidatedThreshold = shippingConfig.consolidatedOrdersTotalThreshold || 0;
  const totalShippingAmount = totalSubtotal > consolidatedThreshold ? 0 :
    shipmentModes.reduce((sum, mode) => sum + modeSummary[mode].shippingAmount, 0);

  // Finalize result
  result.totalShippingAmount = totalShippingAmount;
  result.totalNumberOfShipments = shipmentModes.reduce((sum, mode) => sum + result.orders[mode].length, 0);
  result.orderSplitInfo.isOrderSplit = result.totalNumberOfShipments > 1;
  
  result.summary = {
    totalOrders: result.totalNumberOfShipments,
    totalSubtotal,
    totalGrandTotal,
    isClubShipment,
    ...modeSummary
  };

  return result;
};

const calculateOrderSplitDynamic = ({
  products = [],
  shippingConfig = {},
  shipmentModes = [],
  xHeaderToken = "",
  isClubShipment = false
}) => {
  try {
    // logInfo("Starting dynamic order split calculation", { 
    //   productsCount: products.length,
    //   shipmentModes: shipmentModes 
    // }, xHeaderToken);

    if (!products?.length) {
      return createEmptyResult(shipmentModes);
    }

    // Split products once and process in single loop
    const splitProducts = splitProductsByFulfillmentModeOptimized(products, shipmentModes, shippingConfig);
    const result = processOrdersInSingleLoop(splitProducts, shippingConfig, shipmentModes, isClubShipment);

    // logInfo("Dynamic order split calculation completed", {
    //   totalOrders: result.totalNumberOfShipments,
    //   totalShippingAmount: result.totalShippingAmount,
    //   isOrderSplit: result.orderSplitInfo.isOrderSplit,
    //   isClubShipment: result.orderSplitInfo.isClubShipment
    // }, xHeaderToken);

    return result;

  } catch (error) {
    logInfo("Error in calculateOrderSplitDynamic", { error: error.message }, xHeaderToken);
    throw error;
  }
};

/**
 * Split products by fulfillmentMode based on shipmentMode configuration - Optimized
 * @param {Array} products - Array of products with fulfillmentMode property
 * @param {Array} shipmentModes - Array of shipment modes
 * @param {Object} shippingConfig - Shipping configuration
 * @returns {Object} Object containing split products by shipment mode
 */
const splitProductsByFulfillmentModeOptimized = (products, shipmentModes, shippingConfig) => {
  // Pre-build mapping lookup for faster matching
  const modeMapping = new Map();
  const defaultMode = shipmentModes[0] || 'local';
  
  // Create efficient lookup map
  shipmentModes.forEach(mode => {
    modeMapping.set(mode, mode);
    const modeConfig = shippingConfig[mode];
    if (modeConfig?.shipmentMapping) {
      modeMapping.set(modeConfig.shipmentMapping, mode);
    }
  });

  // Initialize result with empty arrays
  const splitProducts = Object.fromEntries(shipmentModes.map(mode => [mode, []]));
  
  // Single pass through products
  products.forEach(product => {
    const fulfillmentMode = product.fulfillmentMode?.toLowerCase();
    const targetMode = modeMapping.get(fulfillmentMode) || defaultMode;
    splitProducts[targetMode].push(product);
  });
  
  return splitProducts;
};

/**
 * Legacy function - kept for backward compatibility
 */
const splitProductsByFulfillmentMode = (products, config) => {
  const { shipmentMode = [], shippingConfig = {} } = config;
  return splitProductsByFulfillmentModeOptimized(products, shipmentMode, shippingConfig);
};

/**
 * Generate shipping amount array based on shipment configuration - Optimized
 * @param {Object} config - Configuration object with shipmentMode array and shippingConfig
 * @param {Object} splitProducts - Products split by shipment mode (optional, for calculating remaining thresholds)
 * @returns {Array} Array of shipping amount objects with required structure
 */
const generateShippingAmountArray = (config, splitProducts = null,quote = null,storeId = null) => {
  const { shipmentMode = [], shippingConfig = {} } = config;
  
  return shipmentMode
    .map(mode => {
      const modeConfig = shippingConfig[mode];
      if (!modeConfig) return null;
      
      // Use same keys as processOrdersInSingleLoop (ShippingThreshold / ShippingAmount)
      const threshold = modeConfig[`${mode}ShippingThreshold`] ?? modeConfig[`${mode}ShipmentThreshold`] ?? 0;
      const amount = modeConfig[`${mode}ShippingAmount`] ?? modeConfig[`${mode}ShipmentAmount`] ?? 0;
      let remainThreshold = 0;
      let remainShippingAmount = 0;
      
      // Calculate remaining threshold efficiently
      if (splitProducts?.[mode]?.length > 0) {
        const subtotal = calculateSubtotal(splitProducts[mode]);
        if (subtotal && subtotal > 0 && subtotal < threshold) {
          remainThreshold = threshold - subtotal;
          remainShippingAmount = amount;
        }
      }

      const shukranFreeShippingTier = getAppConfigKey('shukranFreeShippingTier') || ["platinum"];
      const isShukranEnable = getStoreConfig(storeId, 'isShukranEnable') || false;
      // Use quote.tier or quote.tierName so retryPayment flow (where quote.tier is not set) still gets free shipping from stored tier
      const tier = (quote?.tier || quote?.tierName || '').toString().toLowerCase();
      if(remainShippingAmount > 0 && tier && shukranFreeShippingTier.includes(tier) && isShukranEnable){
        remainShippingAmount = 0;
        remainThreshold = 0;
        // console.log("OrderSplitHelper :: remainShippingAmount and remainThreshold set to 0 due to shukran free shipping tier");
     }
      // console.log("OederSplitHelper :: modeConfig.shipmentMapping || mode", modeConfig.shipmentMapping || mode);
      // console.log("OederSplitHelper ::amount", amount);
      // console.log("OederSplitHelper ::threshold", threshold);
      // console.log("OederSplitHelper ::remainThreshold", remainThreshold);
      // console.log("OederSplitHelper :: remainShippingAmount", remainShippingAmount);
      
      return {
        shipmentMode: modeConfig.shipmentMapping || mode,
        shipmentAmount: amount,
        shipmentThreshold: threshold,
        remainThreshold: formatPrice(remainThreshold), // Fixed typo: remianThreshold -> remainThreshold
        remainshippingAmount: remainShippingAmount
      };
    })
    .filter(Boolean); // Remove null entries
};

/**
 * Process products and generate complete shipping information - Optimized
 * @param {Array} products - Array of products with fulfillmentMode property
 * @param {Object} config - Configuration object with orderSplitFlag, shipmentMode array and shippingConfig
 * @returns {Object} Object containing split products and shipping amount array
 */
const processProductsAndGenerateShipping = (products, config,quote = null,storeId = null) => {
  if (!config.orderSplitFlag || !products?.length) {
    return {
      splitProducts: { default: products || [] },
      shippingAmount: []
    };
  }
  
  // Single operation: split products and generate shipping info
  const splitProducts = splitProductsByFulfillmentMode(products, config);
  const shippingAmount = generateShippingAmountArray(config, splitProducts,quote,storeId);
  
  return { splitProducts, shippingAmount };
};

module.exports = {
  calculateOrderSplit: calculateOrderSplitDynamic, // Use dynamic version as the main function
  calculateOrderSplitDynamic,
  calculateSubtotal,
  calculateShippingForOrder,
  splitProductsByFulfillmentMode,
  splitProductsByFulfillmentModeOptimized,
  generateShippingAmountArray,
  processProductsAndGenerateShipping,
  createEmptyResult,
  processOrdersInSingleLoop
}; 