const _ = require('lodash');
const APIError = require('../errors/api-error');
const { initTabbyPayment } = require('./bnpl/tabby.service');
const { initTamaraPayment } = require('./bnpl/tamara.service');
const logger = require('../../config/logger');
const { restrictFraudCustomerPayments } = require('./fraud.service');
const redisClient = require('../../config/redis');
const { CASH_ON_DELIVERY } = require('../utils');

/**
 * Returns available payment options for the quote
 * @param {*} req
 * @param {*} apmTransaction
 */
exports.paymentOptions = async (req, apmTransaction) => {
  const overallStartTime = new Date();
  const { headers, body } = req;
  const quote = body;
  const responseObject = buildResponseObject(headers, quote);

  const customerId = quote?.customerId;
  const isGuestUser = !customerId || customerId === '' || (typeof customerId === 'string' && customerId.trim() === '');
  const enablePaymentOptimizations = globalThis.paymentMethods?.enablePaymentOptimizations !== false;
  logger.info(
    `### enablePaymentOptimizations ### paymentOptions for Quote: ${quote?.id} ### Flag Status: ${
      enablePaymentOptimizations ? 'ON' : 'OFF'
    }`
  );
  logger.info(
    `### enablePaymentOptimizations ### paymentOptions for Quote: ${
      quote?.id
    } ### isGuestUser: ${isGuestUser}, customerId: ${
      customerId || 'empty'
    }, enablePaymentOptimizations: ${enablePaymentOptimizations}`
  );

  const storeConfig = global.config;
  const paymentMethodsConfig = global.paymentMethods;
  const stores = storeConfig?.environments[0]?.stores;
  const store = _.find(stores, s => s.storeId == quote.storeId);
  if (!store)
    throw new APIError({
      message: 'Error In Processing Payments! Store ID not found!'
    });
  const paymentConfig = paymentMethodsConfig.credentials[`${store.websiteCode}`];
  const methods = paymentConfig?.methods;
  logger.info(
    `### enablePaymentOptimizations ### paymentOptions for Quote: ${
      quote?.id
    } ### Available methods before fraud check: ${JSON.stringify(methods)}`
  );

  const apmSpanFraudCheck = apmTransaction ? apmTransaction.startSpan('fraudCheckSpan') : null;
  const fraudCheckStartTime = new Date();
  const availableMethods = await restrictFraudCustomerPayments(quote, methods);
  const fraudCheckEndTime = new Date();
  if (apmSpanFraudCheck) apmSpanFraudCheck.end();
  logger.info(
    `### checkLatency ### Quote: ${quote?.id} ### Fraud check time: ${fraudCheckEndTime - fraudCheckStartTime}ms`
  );
  logger.info(
    `### enablePaymentOptimizations ### paymentOptions for Quote: ${
      quote?.id
    } ### Available methods after fraud check: ${JSON.stringify(availableMethods)}`
  );
  const tabby = availableMethods?.find(m => _.includes(m, 'tabby'));
  const tamara = availableMethods?.find(m => _.includes(m, 'tamara'));
  logger.info(
    `### enablePaymentOptimizations ### paymentOptions for Quote: ${
      quote?.id
    } ### Tabby enabled: ${!!tabby}, Tamara enabled: ${!!tamara}`
  );

  // Determine if should do eligibility-only based on bnplEligibilityOnly flag and paymentMethod
  const paymentMethods = globalThis.paymentMethods || {};
  let bnplEligibilityOnly = paymentMethods?.bnplEligibilityOnly === true;
  const paymentMethod = quote?.paymentMethod;

  // Check app version header to conditionally disable new implementation
  // Get minimum version from Consul configuration (default: '5.3.7000')
  const minVersion = paymentMethods?.bnplEligibilityOnlyMinVersion || '5.3.7000';
  const xClientVersion = headers?.['x-client-version'] || headers?.['X-Client-Version'] || '';
  
  // If x-client-version < minVersion → Disable new implementation (behave like flag false)
  // If x-client-version >= minVersion → Use current logic
  const isVersionCompatible = compareVersion(xClientVersion, minVersion) >= 0;
  
  if (!isVersionCompatible) {
    bnplEligibilityOnly = false;
    logger.info(
      `### bnplEligibilityOnly ### paymentOptions for Quote: ${quote?.id} ### Header check: x-client-version="${xClientVersion}", minVersion="${minVersion}" → Disabling new implementation (behaving like flag false)`
    );
  } else {
    logger.info(
      `### bnplEligibilityOnly ### paymentOptions for Quote: ${quote?.id} ### Header check: x-client-version="${xClientVersion}", minVersion="${minVersion}" → Using current logic`
    );
  }

  // Check if paymentMethod is BNPL (tabby_installments, tamara_installments_3, tamara_installments_6)
  const BNPL_PAYMENT_METHODS = ['tabby_installments', 'tamara_installments_3', 'tamara_installments_6'];

  const isBNPLPaymentMethod = BNPL_PAYMENT_METHODS.includes(paymentMethod);

  // Logic:
  // - If bnplEligibilityOnly: false → Always full flow (shouldDoEligibilityOnly = false)
  // - If bnplEligibilityOnly: true AND paymentMethod is NOT BNPL → Eligibility only (shouldDoEligibilityOnly = true)
  // - If bnplEligibilityOnly: true AND paymentMethod IS BNPL → Full flow (shouldDoEligibilityOnly = false)
  const shouldDoEligibilityOnly = bnplEligibilityOnly && !isBNPLPaymentMethod;

  logger.info(
    `### bnplEligibilityOnly ### paymentOptions for Quote: ${
      quote?.id
    } ### bnplEligibilityOnly: ${bnplEligibilityOnly}, paymentMethod: ${
      paymentMethod || 'none'
    }, isBNPLPaymentMethod: ${isBNPLPaymentMethod}, shouldDoEligibilityOnly: ${shouldDoEligibilityOnly}`
  );

  // Add shouldDoEligibilityOnly to quote for use in Tabby/Tamara services
  quote._shouldDoEligibilityOnly = shouldDoEligibilityOnly;

  const promise = [];
  if (tabby && tabby?.length > 0) {
    promise.push(
      (async () => {
        const apmSpanTabby = apmTransaction ? apmTransaction.startSpan('tabbyInitializationSpan') : null;
        const tabbyStartTime = new Date();
        try {
          const result = await initTabbyPayment(quote, responseObject, store, paymentConfig);
          const tabbyEndTime = new Date();
          if (apmSpanTabby) apmSpanTabby.end();
          logger.info(
            `### checkLatency ### Quote: ${quote?.id} ### Tabby initialization time: ${tabbyEndTime - tabbyStartTime}ms`
          );
          return result;
        } catch (error) {
          const tabbyEndTime = new Date();
          if (apmSpanTabby) apmSpanTabby.end();
          logger.info(
            `### checkLatency ### Quote: ${quote?.id} ### Tabby initialization time (failed): ${
              tabbyEndTime - tabbyStartTime
            }ms`
          );
          throw error;
        }
      })()
    );
  }
  if (tamara && tamara?.length > 0) {
    promise.push(
      (async () => {
        const apmSpanTamara = apmTransaction ? apmTransaction.startSpan('tamaraInitializationSpan') : null;
        const tamaraStartTime = new Date();
        try {
          const result = await initTamaraPayment(quote, responseObject, store, paymentConfig);
          const tamaraEndTime = new Date();
          if (apmSpanTamara) apmSpanTamara.end();
          logger.info(
            `### checkLatency ### Quote: ${quote?.id} ### Tamara initialization time: ${
              tamaraEndTime - tamaraStartTime
            }ms`
          );
          return result;
        } catch (error) {
          const tamaraEndTime = new Date();
          if (apmSpanTamara) apmSpanTamara.end();
          logger.info(
            `### checkLatency ### Quote: ${quote?.id} ### Tamara initialization time (failed): ${
              tamaraEndTime - tamaraStartTime
            }ms`
          );
          throw error;
        }
      })()
    );
  }
  const paymentsConfig = {};
  await Promise.allSettled(promise)
    .then(results => {
      results.forEach(res => {
        if (res?.status === 'fulfilled') Object.assign(paymentsConfig, res?.value || {});
      });
    })
    .catch(err => {
      logger.error(`Error in process BNPL Payments. Message : ${err.message}`, err);
    });
  const apmSpanValidate = apmTransaction ? apmTransaction.startSpan('validateMethodsSpan') : null;
  const validateStartTime = new Date();
  let availablePaymentMethods = validateMethods(availableMethods, paymentsConfig);
  const validateEndTime = new Date();
  if (apmSpanValidate) apmSpanValidate.end();
  logger.info(
    `### checkLatency ### Quote: ${quote?.id} ### Validate methods time: ${validateEndTime - validateStartTime}ms`
  );

  const apmSpanCOD = apmTransaction ? apmTransaction.startSpan('codRestrictionSpan') : null;
  const codStartTime = new Date();
  availablePaymentMethods = checkCODRestriction({
    availableMethods: availablePaymentMethods,
    quote: quote,
    websiteCode: store.websiteCode
  });
  const codEndTime = new Date();
  if (apmSpanCOD) apmSpanCOD.end();
  logger.info(
    `### checkLatency ### Quote: ${quote?.id} ### COD restriction check time: ${codEndTime - codStartTime}ms`
  );

  const overallEndTime = new Date();
  logger.info(
    `### checkLatency ### Quote: ${quote?.id} ### Total paymentOptions time: ${overallEndTime - overallStartTime}ms`
  );
  logger.info(
    `### enablePaymentOptimizations ### paymentOptions for Quote: ${
      quote?.id
    } ### Final available payment methods: ${JSON.stringify(availablePaymentMethods)}`
  );
  return {
    availablePaymentMethods: availablePaymentMethods,
    paymentsConfig: paymentsConfig
  };
};

const validateMethods = (availableMethods, paymentsConfig) => {
  let availablePaymentMethods = availableMethods;
  if (paymentsConfig) {
    const methods = Object.keys(paymentsConfig);
    const bnplMethods = availableMethods.filter(m => _.includes(m, 'tamara') || _.includes(m, 'tabby'));
    for (const method of bnplMethods) {
      const isPresent = methods?.find(m => _.includes(m, method));
      if (!isPresent) availablePaymentMethods = resetPaymentMethods(availablePaymentMethods, method);
    }
  }
  return availablePaymentMethods;
};

const checkCODRestriction = ({ availableMethods, quote, websiteCode }) => {
  const CODRestriction = global.paymentMethods?.CODRestriction?.[`${websiteCode}`];
  if (CODRestriction?.codRestrictionEnabled) {
    const grandTotal = Number(quote.subtotal);
    if (Number(CODRestriction?.codRestrictionMin) > 0) {
      if (grandTotal < Number(CODRestriction?.codRestrictionMin)) {
        return resetPaymentMethods(availableMethods, CASH_ON_DELIVERY);
      }
    }
    if (Number(CODRestriction?.codRestrictionMax) > 0) {
      if (grandTotal > Number(CODRestriction?.codRestrictionMax)) {
        return resetPaymentMethods(availableMethods, CASH_ON_DELIVERY);
      }
    }
  }
  return availableMethods;
};

const resetPaymentMethods = (availableMethods, option) => {
  return availableMethods?.filter(m => !_.includes(m, option));
};

const buildResponseObject = (headers, quote) => {
  const xClientVersion = headers['x-client-version'] || headers['X-Client-Version'] || '';
  const xSource = headers['x-source'] || headers['X-Source'] || '';
  const responseObject = {};
  responseObject['xSource'] = xSource;
  responseObject['xClientVersion'] = xClientVersion;
  responseObject['grandTotal'] = quote.subtotal;
  responseObject['estimatedTotal'] = quote.subtotal;
  responseObject['quoteId'] = quote.id;
  responseObject['customerId'] = quote.customerId;
  responseObject['storeId'] = quote.storeId;
  return responseObject;
};

/**
 * Clear the BNPL session from redis cache
 * @param {*} quote
 */
exports.clearSession = async quote => {
  let key;
  try {
    if (quote.paymentMethod === 'tamara_installments_3' || quote.paymentMethod === 'tamara_installments_6') {
      key = `${quote.paymentMethod}_${quote.id}_${quote.bnplAmount}`;
    } else if (_.includes(quote.paymentMethod, 'tabby')) {
      key = `tabby_${quote.id}_${quote.bnplAmount}`;
    }
    if (key) {
      await redisClient.del(key);
      logger.info(`BNPL session is cleared for quote : ${quote.id}, Payload : ${JSON.stringify(quote)}, Key : ${key}`);
    } else {
      logger.info(`BNPL session NOT FOUND for quote : ${quote.id}, Payload : ${JSON.stringify(quote)} Key : ${key}`);
    }
  } catch (error) {
    logger.error(
      `Error in clear session for quote ${quote.id}, ##Request ${JSON.stringify(quote)}, Key : ${key}, Error : ${error}`
    );
  }
};

/**
 * Compare two version strings (e.g., "5.3.7000" vs "5.3.6000")
 * @param {string} version1 - First version string
 * @param {string} version2 - Second version string
 * @returns {number} - Returns 1 if version1 > version2, -1 if version1 < version2, 0 if equal
 */
const compareVersion = (version1, version2) => {
  if (!version1 || !version2) return 0;
  
  const v1Parts = version1.split('.').map(Number);
  const v2Parts = version2.split('.').map(Number);
  
  const maxLength = Math.max(v1Parts.length, v2Parts.length);
  
  for (let i = 0; i < maxLength; i++) {
    const v1Part = v1Parts[i] || 0;
    const v2Part = v2Parts[i] || 0;
    
    if (v1Part > v2Part) return 1;
    if (v1Part < v2Part) return -1;
  }
  
  return 0;
};
