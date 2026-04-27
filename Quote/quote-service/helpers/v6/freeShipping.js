const { pool } = require("../mysqlPool");
const { logError, getStoreConfig, getShippingKey } = require("../utils");
// const { logger } = require('../utils');
const cache = require("memory-cache");
const _ = require("lodash");
const moment = require("moment");
const { getOrderedCount, getCustomerOrderList } = require("../../javaApis/orderedCount");
const { upsertQuote } = require("../upsertQuote");
const { getCustomerInfo } = require("../customer");
const client = require("../../helpers/redisConn");

const falseResponse = {
  numOfDays: "0",
  isActive: false,
};

/**
 *
 * @param {*} momentObject
 * @returns String rounded days difference
 *
 */
const getDifferenceFromToday = (momentObject) => {
  const diffInHours = momentObject.diff(moment(), "hours");
  const diffInDays = diffInHours / 24;
  return Math.round(diffInDays).toString();
};

const getHourDifferenceFromToday = (momentObject) => {
  const diffInMins = momentObject.diff(moment(), "minutes");
  const diffInHours = diffInMins / 60;
  return Math.round(diffInHours).toString();
};

/**
 * if shipping fee is zero, do nothing.
 * else if the new user and having free shipping time range then shipping fee should not be included(0).
 */
exports.evaluateFreeShipping = async ({
  quote,
  responseObject,
  storeId,
  xHeaderToken,
  collection,
  upsertFlag = true,
  optimized = false,
  customerInfo = null  
}) => {
  try {
    // logger.info(`evaluateFreeShipping: Processing quote ${quote.id}`);
    const baseConfig = cache.get("baseConfig");
    const freeShipping = baseConfig.freeShipping;
    const store = getStoreConfig(quote.storeId);
    const countryConfig = store ? freeShipping[store.websiteCode] : null;

    // Case for guest below API-1846
    if (!quote?.customerId) {
      let guestEnable = true;
      if (!freeShipping || !store) {
        guestEnable = false;
      }
      if (!countryConfig || !countryConfig.enable) {
        guestEnable = false;
      }
      if (guestEnable) {
        responseObject["firstFreeShipping"] = {
          numOfDays: "0",
          isActive: true,
        };
      } else responseObject["firstFreeShipping"] = falseResponse;

      return 0;
    }

    if (quote?.firstFreeShipping && countryConfig?.enable) {
      // logger.info(`evaluateFreeShipping: Quote ${quote.id} has existing firstFreeShipping data - expireOn: ${quote.firstFreeShipping?.expireOn}, expireAt: ${quote.firstFreeShipping?.expireAt}`);
      const expireOn = quote.firstFreeShipping?.expireOn;
      let isExpired = true;
      if (quote.firstFreeShipping?.expireAt) {
        isExpired = moment(quote.firstFreeShipping?.expireAt).isBefore(
          moment()
        );
      }
      // if expire date is blank then default value will be true
      // if expire date is before current date then isExpired = true
      // if expre date is fure date than current date isExpired = false
      // logger.info(`evaluateFreeShipping: Quote ${quote.id} - isExpired: ${isExpired}`);
      if (!isExpired) {
        // logger.info(`evaluateFreeShipping: Quote ${quote.id} - Free shipping still valid, applying zero charges`);
        quote.shippingCharges = 0;
        responseObject["firstFreeShipping"] = {
          numOfDays: getDifferenceFromToday(moment(expireOn)),
          isActive: true,
          expireOn: expireOn,
          expireAt: quote.firstFreeShipping?.expireAt,
        };
      } else {
        responseObject["firstFreeShipping"] = falseResponse;
        await checkWebhookFreeShippingData({
          quote,
          responseObject,
          storeId,
          xHeaderToken,
          collection,
          store,
          upsertFlag,
          customerInfo,
        });
      }
    } else {
      // logger.info(`evaluateFreeShipping: Quote ${quote.id} - No existing firstFreeShipping data, checking eligibility`);
      const resFirstFreeShipping = await checkFreeShippingEligibility({
        quote,
        responseObject,
        storeId,
        xHeaderToken,
        collection,
        freeShipping,
        store,
        upsertFlag,
        optimized,
        customerInfo
      });
      if (!resFirstFreeShipping?.status) {
        responseObject["firstFreeShipping"] = falseResponse;
        await checkWebhookFreeShippingData({
          quote,
          responseObject,
          storeId,
          xHeaderToken,
          collection,
          store,
          upsertFlag,
          customerInfo,
        });
      } else {
        return resFirstFreeShipping?.quote || quote;
      }
    }
  } catch (e) {
    logError(e, "error checking free shipping", e.message);
    responseObject["firstFreeShipping"] = falseResponse;
  }
  return 0;
};

/**
 * 1. Check if the free Shipping is enabled in Consul.
 * 2. If enabled, check if the customer is loggedin withing the provided time rage.
 * @param {*} customer
 * @param {*} quote
 * @returns
 */
const checkFreeShippingEligibility = async ({
  quote,
  responseObject,
  storeId,
  xHeaderToken,
  collection,
  freeShipping,
  store,
  upsertFlag,
  optimized = false,
  customerInfo = null  // Pass customerInfo from parent to avoid duplicate OMS API calls
}) => {
  // logger.info(`checkFreeShippingEligibility: Quote ${quote.id} - Checking eligibility, freeShipping config: ${JSON.stringify(freeShipping)}`);
  const res = { status: false, quote: quote };
  try {
    if (!freeShipping || !store) {
      // logger.info(`checkFreeShippingEligibility: Quote ${quote.id} - Free shipping key or store not found`);
      responseObject["firstFreeShipping"] = falseResponse;
      return res;
    }
    const countryConfig = freeShipping[store.websiteCode];
    if (!countryConfig || !countryConfig.enable) {
      // logger.info(`checkFreeShippingEligibility: Quote ${quote.id} - Consul flag is disabled`);
      responseObject["firstFreeShipping"] = falseResponse;
      return res;
    }
    const expDays = countryConfig.expireInDays;

    let customer;
    // Use passed customerInfo if available, otherwise fetch it
    if (customerInfo) {
      customer = customerInfo;
    } else if(!optimized || !quote.customerCreatedAt){
      customer = await getCustomerInfo(quote.customerId);
    } else {
      customer = {
        customerId: quote.customerId,
        email: quote.customerEmail,
        createdAt: moment(quote.customerCreatedAt)
      };
    }
    if (!customer) {
      responseObject["firstFreeShipping"] = falseResponse;
      return res;
    }
    const { customerId, email } = customer;
    const count = await getOrderedCount({
      customerId,
      xHeaderToken: email,
      storeId: quote.storeId,
      token: "",
      pool,
      quote,
      optimized
    });
    if (count != 0) {
      responseObject["firstFreeShipping"] = falseResponse;
      return res;
    }
    const createdAt = moment(customer.createdAt);
    const expireAt = moment(createdAt).add(expDays, "days");
    const isExpired = moment(expireAt).isBefore(moment());
    if (!isExpired) {
      quote.shippingCharges = 0;
      quote.firstFreeShipping = {
        isFreeShipping: true,
        expireOn: expireAt?.toISOString(),
        customerRegisteredOn: createdAt?.toISOString(),
        expireAt: expireAt,
      };
      if (upsertFlag && !optimized)
        await upsertQuote({
          storeId,
          quote,
          collection,
          xHeaderToken
        });
      // logger.info(`checkFreeShippingEligibility: Quote ${quote.id} - First Free Shipping applied, expires on ${quote?.firstFreeShipping?.expireAt}`);
      responseObject["firstFreeShipping"] = {
        numOfDays: getDifferenceFromToday(expireAt),
        isActive: true,
        expireOn: expireAt?.toISOString(),
        customerRegisteredOn: createdAt?.toISOString(),
        expireAt: expireAt,
      };
      res.quote = quote;
      res.status = true;
    } else responseObject["firstFreeShipping"] = falseResponse;
    return res;
  } catch (error) {
    logError(error, `Error In validating shipping fee eligibilit.`);
    responseObject["firstFreeShipping"] = falseResponse;
  }
  return res;
};

const checkWebhookFreeShippingData = async ({
  quote,
  responseObject,
  storeId,
  xHeaderToken,
  collection,
  store,
  upsertFlag,
  customerInfo = null  
}) => {
  try {
    if (
      quote?.webhookFreeShippingData &&
      !_.isEmpty(quote.webhookFreeShippingData)
    ) {
      // logger.info(`checkWebhookFreeShippingData: Quote ${quote.id} - Webhook data exists, expireOn: ${quote.webhookFreeShippingData?.expireOn}, expireAt: ${quote.webhookFreeShippingData?.expireAt}`);
      const expireOn = quote.webhookFreeShippingData?.expireOn;
      let isExpired = true;
      if (quote.webhookFreeShippingData?.expireAt) {
        isExpired = moment(quote.webhookFreeShippingData?.expireAt).isBefore(
          moment()
        );
      }
      // if expire date is blank then default value will be true
      // if expire date is before current date then isExpired = true
      // if expre date is fure date than current date isExpired = false
      // logger.info(`checkWebhookFreeShippingData: Quote ${quote.id} - Webhook isExpired: ${isExpired}`);
      if (!isExpired) {
        // logger.info(`checkWebhookFreeShippingData: Quote ${quote.id} - Webhook free shipping valid`);
        if (quote?.subtotal < quote?.freeShippingConfig?.minOrderValue) {
          responseObject["webhookFreeShippingData"] = falseResponse;
          quote.webhookFreeShippingData = {};
          return false;
        } else {
          quote.shippingCharges = 0;
          const createdAt = moment(quote?.freeShippingConfig?.requestedAt);
          responseObject["webhookFreeShippingData"] = {
            numOfHours: getHourDifferenceFromToday(moment(expireOn)),
            isActive: true,
            expireOn: expireOn,
            webhookCallOn: createdAt?.toISOString(),
            expireAt: quote.webhookFreeShippingData?.expireAt,
          };
        }
      } else {
        responseObject["webhookFreeShippingData"] = falseResponse;
        quote.freeShippingConfig = {};
        quote.webhookFreeShippingData = {};
      }
    } else {
      await checkWebhookFreeShippingEligibility({
        quote,
        responseObject,
        storeId,
        xHeaderToken,
        collection,
        store,
        upsertFlag,
        customerInfo,
      });
    }
  } catch (error) {
    logError(error, `Error In validating shipping fee eligibilit.`);
    responseObject["webhookFreeShippingData"] = falseResponse;
  }
};

const checkWebhookFreeShippingEligibility = async ({
  quote,
  responseObject,
  storeId,
  xHeaderToken,
  collection,
  store,
  upsertFlag,
  customerInfo = null  
}) => {
  try {
    const key = getShippingKey(quote?.customerId, storeId);
    const freeShippingConfig = await client.get(key);
    if (freeShippingConfig) {
      quote.freeShippingConfig = JSON.parse(freeShippingConfig);
    } else {
      responseObject["webhookFreeShippingData"] = falseResponse;
      return quote;
    }
    if (
      !quote?.freeShippingConfig ||
      _.isEmpty(quote?.freeShippingConfig) ||
      !store
    ) {
      responseObject["webhookFreeShippingData"] = falseResponse;
      return quote;
    }
    const expHour = quote?.freeShippingConfig?.expireInHour;

    // Use passed customerInfo if available, otherwise fetch it
    const customer = customerInfo || await getCustomerInfo(quote.customerId);
    if (!customer) {
      responseObject["webhookFreeShippingData"] = falseResponse;
      return quote;
    }
    if (quote?.subtotal < quote?.freeShippingConfig?.minOrderValue) {
      responseObject["webhookFreeShippingData"] = falseResponse;
      return quote;
    }

    const createdAt = moment(quote?.freeShippingConfig?.requestedAt);
    const expireAt = moment(createdAt).add(expHour, "hours");
    const isExpired = moment(expireAt).isBefore(moment());

    if (!isExpired) {
      quote.shippingCharges = 0;
      quote.webhookFreeShippingData = {
        isFreeShipping: true,
        expireOn: expireAt?.toISOString(),
        webhookCallOn: createdAt?.toISOString(),
        expireAt: expireAt,
      };
      if (upsertFlag)
        await upsertQuote({
          storeId,
          quote,
          collection,
          xHeaderToken
        });
      responseObject["webhookFreeShippingData"] = {
        numOfHours: getHourDifferenceFromToday(expireAt),
        isActive: true,
        expireOn: expireAt?.toISOString(),
        webhookCallOn: createdAt?.toISOString(),
        expireAt: expireAt,
      };
    } else responseObject["webhookFreeShippingData"] = falseResponse;

    return quote;
  } catch (error) {
    logError(
      error,
      `Error In validating shipping fee eligibility for webhook.`
    );
    responseObject["webhookFreeShippingData"] = falseResponse;
  }
  return quote;
};

const checkPassedWithStatusAndScheduler = (config) => {
  const { statusType = '', startDateAndTime = '', endDateAndTime = '' } = config || {};
  let statusPassed = false;
  //Check for Status Type and Dates
  if(statusType == '0'){
    statusPassed = true;
  } else if(statusType == '1' && startDateAndTime && endDateAndTime){
    const currentDate = newLocalDate(config?.storeIds || []);
    const sDate = parseDate(startDateAndTime);
    const eDate = parseDate(endDateAndTime);
    if (sDate && isValidDate(sDate) && eDate && isValidDate(eDate) && currentDate) {
      statusPassed = (sDate <= currentDate && currentDate < eDate);
    }
  }
  return statusPassed;
}

/**
 * Parse and validate free shipping configuration from cache
 * @param {*} freeShippingConfigRaw - Raw config from cache
 * @returns {Object|null} Parsed config or null
 */
const parseFreeShippingConfig = (freeShippingConfigRaw) => {
  if (freeShippingConfigRaw) {
    if (typeof freeShippingConfigRaw === 'string') {
      try {
        return JSON.parse(freeShippingConfigRaw);
      } catch (parseError) {
        // logger.warn(`Invalid JSON in freeShippingConfigConsul cache: ${parseError.message}`);
      }
    } else if (typeof freeShippingConfigRaw === 'object') {
      return freeShippingConfigRaw;
    }
  }
  return null;
};

/**
 * Filter orders by valid statuses
 * @param {Array} customerOrderList - List of customer orders
 * @returns {Array} Filtered orders
 */
const filterOrdersByStatus = (customerOrderList) => {
  const validStatuses = ["processing", "shipped", "delivered", "pending", "packed", "refunded"];
  return customerOrderList?.filter(order => validStatuses.includes(order.status)) || [];
};

/**
 * Initialize aggregated orders object with timeframes
 * @param {Array} timeframesOptions - Available timeframes
 * @returns {Object} Initialized aggregated orders
 */
const initializeAggregatedOrders = (timeframesOptions) => {
  const aggregatedOrders = {};
  if (timeframesOptions?.length > 0) {
    timeframesOptions.forEach(timeframe => {
      aggregatedOrders[timeframe.value] = 0;
    });
  } else {
    aggregatedOrders['Lifetime'] = 0;
  }
  return aggregatedOrders;
};

/**
 * Count orders for each timeframe
 * @param {Array} filteredOrderList - Filtered orders
 * @param {Array} timeframesOptions - Available timeframes
 * @param {Object} aggregatedOrders - Orders count by timeframe
 */
const countOrdersByTimeframe = (filteredOrderList, timeframesOptions, aggregatedOrders, storeId) => {
  if (filteredOrderList.length === 0 || !timeframesOptions?.length) return;
  
  // Use the same timezone logic as newLocalDate function
  const now = moment(newLocalDate([storeId])).startOf("day");
  filteredOrderList.forEach(order => {
    if (!order.createdAt) return;
    
    const orderDate = moment.utc(order.createdAt).local(true);
    timeframesOptions.forEach(timeframe => {
      if (timeframe.value.includes('-')) {
        const [startRange, endRange] = timeframe.value.split('-');
        const startMonths = parseInt(startRange.replace('m', ''));
        const endMonths = parseInt(endRange.replace('m', ''));
        
        const startDate = now.clone().subtract(endMonths, 'months').startOf("day");
        const endDate = startMonths === 0
            ? now.clone().endOf("day")
            : now.clone().subtract(startMonths - 1, 'months').subtract(1, 'day').endOf("day");
        if (orderDate.isBetween(startDate, endDate, null, '[]')) {
          aggregatedOrders[timeframe.value]++;
        }
      }
    });
  });
};

/**
 * Check if user segments are valid
 * @param {Array} userSegments - User segments to check
 * @param {Object} aggregatedOrders - Orders count by timeframe
 * @returns {boolean} True if all segments are valid
 */
const validateUserSegments = (userSegments, aggregatedOrders) => {
  if (!userSegments.length) return true;
  
  const data = userSegments.every(segment => 
    aggregatedOrders && aggregatedOrders[segment] !== undefined && aggregatedOrders[segment] > 0
  );
  return data;
};

/**
 * Process configuration and find matching thresholds
 * @param {Object} freeShippingConfig - Parsed config
 * @param {number} storeId - Store ID
 * @param {string} deliveryModel - Delivery model
 * @param {Object} aggregatedOrders - Orders count by timeframe
 * @returns {Array} Matching thresholds
 */
const findMatchingThresholds = (freeShippingConfig, storeId, deliveryModel, aggregatedOrders) => {
  const enableDeliveryModelForThresholdCalc = getStoreConfig(storeId, 'enableDeliveryModelForThresholdCalc') || false;
  const matchingThreshold = [];
  
  if (!freeShippingConfig?.configs || !Array.isArray(freeShippingConfig.configs)) {
    return matchingThreshold;
  }
  
  freeShippingConfig.configs.forEach(config => {
    const { storeIds = [], freeShippingEnabled = false } = config || {};
    const statusPassed = checkPassedWithStatusAndScheduler(config);

    // Check if status/scheduler is passed and storeId is matching
    if (!freeShippingEnabled || !statusPassed || !storeIds.includes(storeId.toString())) return;
    
    // Check if delivery model is matching
    if (enableDeliveryModelForThresholdCalc && deliveryModel && config?.deliveryModel) {
      if (config.deliveryModel.toLowerCase() !== deliveryModel.toLowerCase()) return;
    }

    // Check if user segments are passed, commenting as for now to avoid user segment validation
    // if (!validateUserSegments(config?.userSegments || [], aggregatedOrders)) return;

    // Push to array if all conditions are passed and threshold is not empty
    if (config?.threshold !== '') {
      matchingThreshold.push(Number(config.threshold));
    }
  });
  
  return matchingThreshold;
};

/**
 * Find fallback thresholds from default config
 * @param {Object} freeShippingConfig - Parsed config
 * @param {number} storeId - Store ID
 * @param {string} deliveryModel - Delivery model
 * @returns {Array} Fallback thresholds
 */
const findFallbackThresholds = (freeShippingConfig, storeId, deliveryModel) => {
  const enableDeliveryModelForThresholdCalc = getStoreConfig(storeId, 'enableDeliveryModelForThresholdCalc') || false;
  const defaultDeliveryModelForThreshold = getStoreConfig(storeId, 'defaultDeliveryModelForThreshold') || 'express';
  
  const thresholdKey = (defaultDeliveryModelForThreshold === 'global' || 
                       (enableDeliveryModelForThresholdCalc && deliveryModel === 'global')) 
                       ? 'thresholdGlobal' : 'threshold';
  
  const fallbackThresholds = [];
  if (freeShippingConfig?.defaultConfigs?.length > 0) {
    freeShippingConfig.defaultConfigs.forEach(defconfig => {
      if (defconfig?.storeIds.includes(storeId.toString()) && defconfig?.[thresholdKey] !== '') {
        fallbackThresholds.push(Number(defconfig[thresholdKey]));
      }
    });
  }
  
  return fallbackThresholds;
};

/**
 * Check if subtotal matches any threshold
 * @param {Array} matchingThreshold - Available thresholds
 * @param {number} subtotal - Current subtotal
 * @returns {Object} Result with allowFreeshipping and matchedSingleThreshold
 */
const checkSubtotalMatch = (matchingThreshold, subtotal) => {
  let allowFreeshipping = false;
  let matchedSingleThreshold = 0;
  let matchFound = false;
  
  if (matchingThreshold?.length > 0) {
    for (const val of matchingThreshold) {
      if (subtotal >= val) {
        allowFreeshipping = true;
        matchedSingleThreshold = val;
        matchFound = true;
        break;
      }
    }
  }

  if(!matchFound){
    matchedSingleThreshold = (Array.isArray(matchingThreshold) && matchingThreshold.length > 0) ? Math.max(...matchingThreshold) : 0;
  }
  
  return { allowFreeshipping, matchedSingleThreshold, matchFound };
};

/**
 * 
 * @param {*} quote 
 * @param {*} storeId
 * @param {*} xHeaderToken
 * @param {*} subtotal
 * @param {*} deliveryModel (Optional) - 'express' or 'global'
 * @returns 
 */
const freeShippingThresholdUpgraded = async ({
  quote,
  storeId,
  xHeaderToken,
  subtotal,
  deliveryModel = ''
}) => {
  try {
    // Get and parse free shipping config
    const freeShippingConfigRaw = cache.get("freeShippingConfigConsul");
    const freeShippingConfig = { "configs": [
      {
         "storeIds": [
            "1",
            "3"
         ],
         "userSegments": [
            "0m-3m",
            "4m-6m",
            "7m-9m",
            "10m-12m",
            "13m-15m",
            "16m-18m",
            "19m-21m",
            "22m-24m",
            "25m-240m",
            "Lifetime"
         ],
         "deliveryModel": "Express",
         "threshold": 10,
         "statusType": "1",
         "freeShippingEnabled": true,
         "startDateAndTime": "03/25/2026, 00:00",
         "endDateAndTime": "03/28/2026, 23:30"
      }
   ],
   "defaultConfigs": [
      {
         "storeIds": [
            "1",
            "3"
         ],
         "threshold": 70,
         "thresholdGlobal": 120
      },
      {
         "storeIds": [
            "7",
            "11"
         ],
         "threshold": 160,
         "thresholdGlobal": 160
      },
      {
         "storeIds": [
            "12",
            "13"
         ],
         "threshold": 20,
         "thresholdGlobal": 20
      },
      {
         "storeIds": [
            "15",
            "17"
         ],
         "threshold": 120,
         "thresholdGlobal": 120
      },
      {
         "storeIds": [
            "19",
            "21"
         ],
         "threshold": 20,
         "thresholdGlobal": 20
      },
      {
         "storeIds": [
            "23",
            "25"
         ],
         "threshold": 20,
         "thresholdGlobal": 20
      }
   ]}
    
    if (!freeShippingConfig) {
      return { allowFreeshipping: false, matchedSingleThreshold: 0 };
    }
    
    // Get customer orders and aggregate by timeframe
    const customerOrderList = await getCustomerOrderList({
      quote,
      customerEmail: quote?.customerEmail || '',
      xHeaderToken
    });
    
    const filteredOrderList = filterOrdersByStatus(customerOrderList);
    const timeframesOptions = [
      {
         "value": "0m-3m",
         "text": "Last 3 months"
      },
      {
         "value": "4m-6m",
         "text": "4-6 months"
      },
      {
         "value": "7m-9m",
         "text": "7-9 months"
      },
      {
         "value": "10m-12m",
         "text": "10-12 months"
      },
      {
         "value": "13m-15m",
         "text": "13-15 months"
      },
      {
         "value": "16m-18m",
         "text": "17-18 months"
      },
      {
         "value": "19m-21m",
         "text": "19-21 months"
      },
      {
         "value": "22m-24m",
         "text": "22-24 months"
      },
      {
         "value": "25m-240m",
         "text": "Greater than 24 months"
      },
      {
         "value": "Lifetime",
         "text": "Lifetime"
      }
   ];
    const aggregatedOrders = initializeAggregatedOrders(timeframesOptions);
    if(filteredOrderList.length == 0){
      aggregatedOrders['Lifetime'] = 1;
    }
    countOrdersByTimeframe(filteredOrderList, timeframesOptions, aggregatedOrders, storeId);
    
    // Find matching thresholds
    let matchingThreshold = findMatchingThresholds(freeShippingConfig, storeId, deliveryModel, aggregatedOrders);
    if(matchingThreshold.length > 0){
      matchingThreshold.sort((a, b) => b - a); //Descending order
      // Check if subtotal matches any threshold
      return checkSubtotalMatch(matchingThreshold, subtotal);
    }

    // If no matching threshold, try fallback config
    matchingThreshold = findFallbackThresholds(freeShippingConfig, storeId, deliveryModel);
    // Check if subtotal matches any threshold
    return checkSubtotalMatch(matchingThreshold, subtotal);
  } catch (error) {
    logError(error, `Error in freeShippingThresholdUpgraded`);
    return {allowFreeshipping: false, matchedSingleThreshold: 0};
  }
};

function parseDate(dateString) {
  const cleanedDateString = dateString.replace(/(\d+)(st|nd|rd|th)/, '$1');
  const parsedDate = moment(cleanedDateString, 'MM/DD/YYYY, HH:mm').toDate();
  if (!parsedDate || isNaN(parsedDate)) {
    throw new Error('Invalid date format');
  }
  return parsedDate;
}

const isValidDate = d => {
  return d instanceof Date && !isNaN(d);
};

const newLocalDate = storeIds => {
  let offset = 3 * 60; // For SA KW BH
  if (storeIds && Array.isArray(storeIds) && (storeIds.includes(7) || storeIds.includes(11) || storeIds.includes(23) || storeIds.includes(25))) {
    offset = 4 * 60; // FOR AE
  }
  
  // Get current UTC time
  const utcDate = new Date();
  const utcTime = utcDate.getTime() + (utcDate.getTimezoneOffset() * 60000);
  
  // Apply the specific timezone offset
  return new Date(utcTime + (offset * 60000));
};

exports.freeShippingThresholdUpgraded = freeShippingThresholdUpgraded;