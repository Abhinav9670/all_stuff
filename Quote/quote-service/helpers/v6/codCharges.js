const { getStoreConfig, formatPrice, getBaseConfig, logInfo } = require('../utils');
const cache = require('memory-cache');
const _ = require('lodash');

exports.hasGlobalProducts = (quoteItems = []) => {
  try {
    // Validate input
    if (!Array.isArray(quoteItems) || quoteItems.length === 0) {
      return false;
    }
    
    const hasGlobal = quoteItems.filter(item => {
      try {
        // Safely handle fulfillmentMode
        const fulfillmentMode = item?.fulfillmentMode?.toLowerCase?.();
        
        // Check if fulfillmentMode is global and isGift is false or 0
        return fulfillmentMode && 
               fulfillmentMode === 'global' && 
               (!item?.isGift || item.isGift === false || item.isGift === 0);
      } catch (itemError) {
        // Log individual item processing errors but continue with other items
        // console.error('[COD CHARGES] Error processing item in hasGlobalProducts:');
        return false;
      }
    });
    
    // console.log('[COD CHARGES] hasGlobalProducts:', hasGlobal.length, 'items:', quoteItems.length);
    return hasGlobal.length > 0;
    
  } catch (error) {
    // Handle any unexpected errors
    // console.error('[COD CHARGES] Error in hasGlobalProducts:');
    return false; // Return false as safe default
  }
};

exports.hasExpressShipments = (quoteItems = [], quote = {}) => {
  try {
    // Validate input
    if (!Array.isArray(quoteItems) || quoteItems.length === 0) {
      return false;
    }
    
    const hasExpressDeliveryType = quoteItems.filter(item => {
      try {
        // Safely handle fulfillmentMode
        const fulfillmentMode = item?.fulfillmentMode?.toLowerCase?.();
        
        // Check if fulfillmentMode is express or local and isGift is false or 0
        return fulfillmentMode && 
               (fulfillmentMode === 'express' || fulfillmentMode === 'local') && 
               (!item?.isGift || item.isGift === false || item.isGift === 0);
      } catch (itemError) {
        // Log individual item processing errors but continue with other items
        // console.error('[COD CHARGES] Error processing item in hasExpressShipments:', itemError, 'item:', item);
        return false;
      }
    });

    // console.log('[COD CHARGES] hasExpressDeliveryType:', hasExpressDeliveryType.length, 'items:', quoteItems.length);
    return hasExpressDeliveryType.length > 0;
    
  } catch (error) {
    // Handle any unexpected errors
    // console.error('[COD CHARGES] Error in hasExpressShipments:', error, 'quoteItems:', quoteItems, 'quote:', quote);
    return false; // Return false as safe default
  }
};

exports.getCodConfig = (storeId) => {
  const websiteCode = getStoreConfig(storeId, 'websiteCode');
  const codConfigBase = getBaseConfig('codConfig') || {};
  
  // console.log('[COD CHARGES] getCodConfig - storeId:', storeId, 'websiteCode:', websiteCode, 'codConfigBase keys:', Object.keys(codConfigBase));
  
  const countryConfig = codConfigBase[websiteCode] || codConfigBase['default'] || {};
  
  // console.log('[COD CHARGES] getCodConfig - countryConfig found:', !!countryConfig, 'countryConfig:', JSON.stringify(countryConfig));
  
  const legacyCodCharges = Number(getStoreConfig(storeId, 'codCharges') || 0);
  
  const countryCodCharges = countryConfig.codCharges || {};
  
  const hasCountryCodCharges = countryConfig.codCharges && typeof countryConfig.codCharges === 'object';
  
  const codConfig = {
    enableCod: countryConfig.enableCod !== false,
    enableCodForGlobal: countryConfig.enableCodForGlobal !== false,
    codCharges: {
      express: hasCountryCodCharges 
        ? Number(countryCodCharges.express ?? 0)
        : Number(legacyCodCharges || 0),
      global: hasCountryCodCharges 
        ? Number(countryCodCharges.global ?? 0)
        : Number(legacyCodCharges || 0)
    }
  };
  
  // console.log('[COD CHARGES] getCodConfig - Final config:', JSON.stringify(codConfig));
  
  return codConfig;
};

exports.calculateCodCharges = ({ quoteItems = [], quote = {}, storeId, paymentMethod = '' }) => {
  // console.log('[COD CHARGES] calculateCodCharges - paymentMethod:', paymentMethod, 'storeId:', storeId, 'quoteItems count:', quoteItems.length);
  
  if (paymentMethod !== 'cashondelivery') {
    // console.log('[COD CHARGES] calculateCodCharges - Payment method is not COD, returning zero charges');
    return {
      totalCodCharges: 0,
      expressCodCharges: 0,
      globalCodCharges: 0,
      codEnabled: false,
      codBreakdown: []
    };
  }
  
  const codConfig = exports.getCodConfig(storeId);
  
  const hasGlobal = exports.hasGlobalProducts(quoteItems);
  const hasExpress = exports.hasExpressShipments(quoteItems, quote);
  
  // console.log('[COD CHARGES] calculateCodCharges - Cart analysis:', {
  //   hasGlobal,
  //   hasExpress,
  //   enableCod: codConfig.enableCod,
  //   enableCodForGlobal: codConfig.enableCodForGlobal
  // });
  
  // If both enableCod and enableCodForGlobal are false, no COD charges
  if (!codConfig.enableCod && !codConfig.enableCodForGlobal) {
    // console.log('[COD CHARGES] calculateCodCharges - COD is disabled for both express and global');
    return {
      totalCodCharges: 0,
      expressCodCharges: 0,
      globalCodCharges: 0,
      codEnabled: false,
      codBreakdown: [],
      reason: 'COD_DISABLED_FOR_BOTH'
    };
  }
  
  let expressCodCharges = 0;
  let globalCodCharges = 0;
  const codBreakdown = [];
  
  // Apply express COD charges only if enableCod is true
  if (codConfig.enableCod && hasExpress) {
    expressCodCharges = codConfig.codCharges.express;
    codBreakdown.push({
      type: 'express',
      amount: formatPrice(expressCodCharges),
      label: 'Express COD Charges'
    });
    // console.log('[COD CHARGES] calculateCodCharges - Express COD charges applied:', expressCodCharges);
  } else if (codConfig.enableCod && !hasExpress && !hasGlobal) {
    expressCodCharges = codConfig.codCharges.express;
    codBreakdown.push({
      type: 'express',
      amount: formatPrice(expressCodCharges),
      label: 'COD Charges'
    });
    // console.log('[COD CHARGES] calculateCodCharges - Def`ault cart (neither express nor global), using express charges:', expressCodCharges);
  }
  
  if (codConfig.enableCodForGlobal && hasGlobal) {
    globalCodCharges = codConfig.codCharges.global;
    codBreakdown.push({
      type: 'global',
      amount: formatPrice(globalCodCharges),
      label: 'Global COD Charges'
    });
    // console.log('[COD CHARGES] calculateCodCharges - Global COD charges applied:', globalCodCharges);
  } else if (hasGlobal && !codConfig.enableCodForGlobal) {
    // console.log('[COD CHARGES] calculateCodCharges - Global products detected but enableCodForGlobal is false, skipping global COD charges');
  }
  
  const totalCodCharges = expressCodCharges + globalCodCharges;
  
  const result = {
    totalCodCharges: formatPrice(totalCodCharges),
    expressCodCharges: formatPrice(expressCodCharges),
    globalCodCharges: formatPrice(globalCodCharges),
    codEnabled: true,
    codBreakdown,
    hasExpress,
    hasGlobal
  };
  
  // console.log('[COD CHARGES] calculateCodCharges - Final result:', JSON.stringify(result));
  return result;
};
