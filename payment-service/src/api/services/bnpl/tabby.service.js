const axios = require('axios');
const _ = require('lodash');
const mysql = require('../../../config/mysql');
const moment = require('moment');
const utils = require('./utils');
const logger = require('../../../config/logger');
const COLOR_CODE = '#545454';
const redisClient = require('../../../config/redis');
const ValidationError = require('../../errors/validation-error');
const { stringifyError } = require('../../utils/log');
const { AUTH_INTERNAL_HEADER_BEARER_TOKEN } = process.env;

/**
 * Returns session from the cache otherwise hit API to create a new one
 * @param {*} quote
 * @returns
 */
exports.initTabbyPayment = async (quote, responseObject, store, paymentConfig) => {
  const shouldDoEligibilityOnly = quote?._shouldDoEligibilityOnly === true;
  const key = `tabby_${quote.id}_${responseObject.grandTotal}`;
  const apmTransaction = globalThis.apm.startTransaction('tabbyTransaction', 'request');

  try {
    // Only use cached session if NOT in eligibility-only mode (cached sessions are full sessions)
    if (!shouldDoEligibilityOnly) {
      const cachedSession = await fetchCachedTabbySession(key, apmTransaction);
      if (cachedSession && !quote.retryPayment) {
        return sanitizeSessionIfNeeded(cachedSession, quote.paymentMethod, apmTransaction);
      }
    }

    handleCODAdjustment(quote, responseObject);

    if (Number(responseObject.grandTotal) <= 0) {
      throw new ValidationError({ message: "Tabby session error. Amount shouldn't be zero." });
    }

    const session = await createTabbySession(quote, responseObject, store, paymentConfig, apmTransaction);
    
    // Only cache full sessions, not eligibility-only responses
    if (!shouldDoEligibilityOnly) {
      return await cacheTabbySession(session, key, quote.paymentMethod, apmTransaction);
    } else {
      // For eligibility-only, don't cache (or use different key)
      apmTransaction.end();
      return session;
    }
  } catch (error) {
    logger.error(`initTabbyPayment : Error in processing Tabby payment. ${quote.id}`, error);
    throw new ValidationError({ message: 'Error in Tabby Payments.' });
  }
};

const fetchCachedTabbySession = async (key, apmTransaction) => {
  const apmSpanRedisFetch = apmTransaction.startSpan('redisFetch');
  const cacheSession = await redisClient.get(key);
  apmSpanRedisFetch.end();
  return cacheSession ? JSON.parse(cacheSession) : null;
};

const sanitizeSessionIfNeeded = (session, paymentMethod, apmTransaction) => {
  if (!_.includes(paymentMethod, 'tabby')) {
    delete session['payment_id'];
  }
  apmTransaction.end();
  return session;
};

const handleCODAdjustment = (quote, responseObject) => {
  if (quote.paymentMethod === 'cashondelivery' && quote?.codCharges > 0) {
    const codCharges = quote?.codCharges || 0;
    globalThis.loggerInfo(`initTabbyPayment: paymentMethod is cashondelivery, subtracting COD charges: ${codCharges}`);
    responseObject.grandTotal = parseFloat((responseObject.grandTotal - codCharges).toFixed(2));
    globalThis.loggerInfo(
      `initTabbyPayment: Updated grandTotal after subtracting COD charges: ${responseObject.grandTotal}`
    );
  }
};

const createTabbySession = async (quote, responseObject, store, paymentConfig, apmTransaction) => {
  return await createSession(quote, responseObject, store, paymentConfig, apmTransaction);
};

const cacheTabbySession = async (session, key, paymentMethod, apmTransaction) => {
  if (!session) {
    apmTransaction.end();
    throw new ValidationError({ message: 'Error in Tabby Payments.' });
  }

  const apmSpanRedisSet = apmTransaction.startSpan('redisSet');
  await redisClient.set(key, JSON.stringify(session));
  utils.setTTLForRedisKey(key);
  apmSpanRedisSet.end();

  if (!_.includes(paymentMethod, 'tabby')) {
    delete session['payment_id'];
  }

  apmTransaction.end();
  return session;
};

/**
 * Crate Tabby Session to Initiate Tabby Payment Transaction
 */
const createSession = async (quote, responseObject, store, paymentConfig, apmTransaction) => {
  const sessionObj = await buildSessionPayload(quote, responseObject, store, paymentConfig, apmTransaction);
  const url = paymentConfig.apiUrl;
  const paymentMethods = globalThis.paymentMethods || {};
  const enablePaymentOptimizations = paymentMethods.enablePaymentOptimizations !== false; // Default: true (enabled)
  const httpTimeout = enablePaymentOptimizations
    ? paymentConfig.httpTimeout || paymentMethods.credentials?.httpTimeoutInMs || 5000
    : undefined;
  const config = {
    headers: {
      Authorization: `Bearer ${paymentConfig.apiPublicKey}`,
      'Content-Type': 'application/json'
    }
  };
  if (httpTimeout) {
    config.timeout = httpTimeout;
  }
  logger.info(`### LOGGGER CHECK FOR QUOTE : ${quote.id} PAYLOAD : ${JSON.stringify(sessionObj)}`);
  const apmSpanTabbySession = apmTransaction.startSpan('tabbySession');
  return axios
    .post(url, sessionObj, config)
    .then(res => {
      logger.info(
        `### Tabby Create Session for Quote : ${quote.id} ###Request : ${JSON.stringify(
          config
        )} ### Response : ${JSON.stringify(res.data)}`
      );
      apmSpanTabbySession.end();
      // Pass the amount for both eligibility-only and full mode (needed for amount_to_pay calculation)
      const amount = sessionObj.payment?.amount || responseObject?.grandTotal;
      return extractResponse(res.data, amount, quote);
    })
    .catch(error => {
      apmSpanTabbySession.end();
      logger.error(
        `### Error in Creating Tabby Session for Quote: ${quote.id} ###Request : ${JSON.stringify(
          config
        )}  ### Response : ${JSON.stringify(error?.response?.data)}`
      );
      
      // Per Tabby's recommendation: Default to showing Tabby as available on errors/timeouts
      // This prevents blocking checkout due to temporary API issues
      const shouldDoEligibilityOnly = quote?._shouldDoEligibilityOnly === true;
      if (shouldDoEligibilityOnly) {
        logger.info(
          `### bnplEligibilityOnly ### Quote: ${quote.id} ### Error/timeout occurred, defaulting to Tabby available`
        );
        return {
          tabby_installments: { available: true },
          tabby_paylater: { available: true }
        };
      }
      
      // For full mode, return empty (let caller handle)
      return null;
    });
};
/**
 * Capture required information from Tabby Response and store Tabby Payment ID in quote.
 * @param {*} response
 * @param {*} quote
 * @returns
 */
const extractResponse = async (response, amount = null, quote = null) => {
  const shouldDoEligibilityOnly = quote?._shouldDoEligibilityOnly === true;
  
  logger.info(
    `### bnplEligibilityOnly ### Tabby extractResponse ### shouldDoEligibilityOnly: ${shouldDoEligibilityOnly}, response status: ${response?.status}`
  );
  
  const tabbyPaymentOps = {};

  // Eligibility-only mode: Check status field (per Tabby's recommendation)
  if (shouldDoEligibilityOnly) {
    const status = response.status;
    logger.info(
      `### bnplEligibilityOnly ### Tabby eligibility check status: ${status}`
    );
    
    if (status === 'created') {
      // Customer is eligible - return minimal info
      const products = response.configuration?.available_products;
      if (!_.isEmpty(products)) {
        const { installments, pay_later: payLater } = products;
        
        if (installments?.[0]) {
          const installmentsCount = installments[0].installments_count || 3;
          
          // Priority: Use amount from Tabby's response (includes fees), fallback to passed amount
          const totalAmount = installments[0].amount_to_pay || 
            response.payment?.amount || 
            amount || 
            0;
          
          // Priority: Use actual installment amount from Tabby's response if available
          // This ensures we get the correct amount (including fees) that Tabby calculates
          let payPerInstallment = null;
          if (installments[0].pay_per_installment) {
            payPerInstallment = installments[0].pay_per_installment;
            logger.info(
              `### bnplEligibilityOnly ### Tabby eligibility for Quote: ${quote?.id} ### Using pay_per_installment from Tabby response: ${payPerInstallment}`
            );
          } else if (installments[0].installments?.[0]?.amount) {
            payPerInstallment = installments[0].installments[0].amount;
            logger.info(
              `### bnplEligibilityOnly ### Tabby eligibility for Quote: ${quote?.id} ### Using installment amount from Tabby response: ${payPerInstallment}`
            );
          } else if (installments[0].installments?.[0]?.principal) {
            payPerInstallment = installments[0].installments[0].principal;
            logger.info(
              `### bnplEligibilityOnly ### Tabby eligibility for Quote: ${quote?.id} ### Using installment principal from Tabby response: ${payPerInstallment}`
            );
          } else if (totalAmount > 0) {
            // Fallback: Calculate from total amount (should rarely be needed)
            payPerInstallment = _.toString(_.round(Number(totalAmount) / installmentsCount, 2));
            logger.info(
              `### bnplEligibilityOnly ### Tabby eligibility for Quote: ${quote?.id} ### Calculated pay_per_installment: ${payPerInstallment} (totalAmount: ${totalAmount}, installmentsCount: ${installmentsCount})`
            );
          }
          
          tabbyPaymentOps['tabby_installments'] = {
            available: true,
            installments_count: installmentsCount
          };
          
          if (payPerInstallment) {
            tabbyPaymentOps['tabby_installments'].pay_per_installment = payPerInstallment;
            tabbyPaymentOps['tabby_installments'].amount_to_pay = _.toString(totalAmount);
          }
        }
        
        if (payLater?.[0]) {
          tabbyPaymentOps['tabby_paylater'] = {
            available: true
          };
        }
      }
      // Note: No payment_id in eligibility-only mode (not needed)
    } else if (status === 'rejected') {
      // Customer is not eligible
      const rejectionReason = response.configuration?.available_products?.installments?.[0]?.rejection_reason;
      logger.info(
        `### bnplEligibilityOnly ### Tabby eligibility rejected. Reason: ${rejectionReason}`
      );
      // Return empty object - Tabby not available
      return {};
    }
    
    return tabbyPaymentOps;
  } else {
    // Full mode: Extract complete installment details
    logger.info(
      `### bnplEligibilityOnly ### Tabby extractResponse ### Running FULL mode - extracting complete installment details`
    );
    const products = response.configuration?.available_products;

    if (!_.isEmpty(products)) {
      const { installments, pay_later: payLater } = products;

      if (installments?.[0]) {
        // Calculate amount_to_pay from installments if not in product, or use passed amount
        const totalAmount = installments[0].amount_to_pay || 
          amount ||
          (installments[0].installments?.reduce((sum, inst) => sum + Number(inst.amount || inst.principal || 0), 0)) ||
          response.payment?.amount ||
          0;
        tabbyPaymentOps['tabby_installments'] = buildInstallmentInfo(installments[0], totalAmount);
      }

      if (payLater?.[0]) {
        // Calculate amount_to_pay from installments if not in product, or use passed amount
        const totalAmount = payLater[0].amount_to_pay || 
          amount ||
          (payLater[0].installments?.reduce((sum, inst) => sum + Number(inst.amount || inst.principal || 0), 0)) ||
          response.payment?.amount ||
          0;
        tabbyPaymentOps['tabby_paylater'] = buildInstallmentInfo(payLater[0], totalAmount);
      }
    }

    tabbyPaymentOps['payment_id'] = response.payment?.id;
    return tabbyPaymentOps;
  }
};

// Helper: build common installment object
const buildInstallmentInfo = (product, totalAmount = null) => {
  // Use provided totalAmount, or product.amount_to_pay, or calculate from installments
  const amountToPay = totalAmount || 
    product.amount_to_pay || 
    (product.installments?.reduce((sum, inst) => sum + Number(inst.amount || inst.principal || 0), 0)) ||
    '';
  
  // Calculate pay_per_installment if not present in product
  // Priority: 1) product.pay_per_installment, 2) first installment amount, 3) calculated from totalAmount
  let payPerInstallment = product.pay_per_installment;
  if (!payPerInstallment || payPerInstallment === '') {
    if (product.installments?.[0]?.amount) {
      payPerInstallment = product.installments[0].amount;
    } else if (product.installments?.[0]?.principal) {
      payPerInstallment = product.installments[0].principal;
    } else if (amountToPay && product.installments_count) {
      // Calculate: total amount / number of installments
      payPerInstallment = _.toString(_.round(Number(amountToPay) / product.installments_count, 2));
    } else {
      payPerInstallment = '';
    }
  }
  
  return {
    web_url: product.web_url,
    pay_per_installment: payPerInstallment,
    installments_count: product.installments_count,
    amount_to_pay: amountToPay ? _.toString(amountToPay) : '',
    color_code: COLOR_CODE,
    installments: findInstallmentDates(product)
  };
};

/**
 * Get customer data (registeredSince and totalOrders) based on user type and optimization settings
 * @param {boolean} isGuestUser - Whether the user is a guest
 * @param {string} customerId - Customer ID
 * @param {boolean} enablePaymentOptimizations - Whether optimizations are enabled
 * @param {string} quoteId - Quote ID for logging
 * @returns {Promise<{registeredSince: string, totalOrders: number}>}
 */
const getCustomerData = async (isGuestUser, customerId, enablePaymentOptimizations, quoteId) => {
  if (isGuestUser) {
    logger.info(
      `### enablePaymentOptimizations ### Tabby buildSessionPayload for Quote: ${quoteId} ### Skipping customer-related operations (getRegisteredSince, findOrdersByCustomer) for guest user`
    );
    return {
      registeredSince: moment().utc().format('YYYY-MM-DDTHH:mm:ss.SSSZ'),
      totalOrders: 0
    };
  }

  if (enablePaymentOptimizations) {
    logger.info(
      `### enablePaymentOptimizations ### Tabby buildSessionPayload for Quote: ${quoteId} ### Using parallel execution for customer data (optimizations enabled)`
    );
    const [registeredSince, totalOrders] = await Promise.all([
      getRegisteredSince(customerId),
      findOrdersByCustomer(customerId)
    ]);
    return { registeredSince, totalOrders };
  }

  logger.info(
    `### enablePaymentOptimizations ### Tabby buildSessionPayload for Quote: ${quoteId} ### Using sequential execution for customer data (optimizations disabled)`
  );
  const registeredSince = await getRegisteredSince(customerId);
  const totalOrders = await findOrdersByCustomer(customerId);
  return { registeredSince, totalOrders };
};

const buildSessionPayload = async (quote, responseObject, store, paymentConfig, apmTransaction) => {
  const shouldDoEligibilityOnly = quote?._shouldDoEligibilityOnly === true;
  
  logger.info(
    `### bnplEligibilityOnly ### Tabby buildSessionPayload for Quote: ${quote?.id} ### shouldDoEligibilityOnly: ${shouldDoEligibilityOnly}`
  );

  // Minimal payload for eligibility check (per Tabby's recommendation)
  if (shouldDoEligibilityOnly) {
    const sessionObj = {
      merchant_code: getMerchantCode(responseObject, paymentConfig),
      payment: {
        amount: responseObject?.grandTotal?.toFixed(2),
        currency: quote.storeCurrencyCode,
        buyer: {
          email: quote.customerEmail,
          phone: quote.quoteAddress?.mobileNumber
        }
      }
    };
    logger.info(
      `### bnplEligibilityOnly ### Quote: ${quote?.id} ### Using minimal payload for eligibility check`
    );
    return sessionObj;
  } else {
    // Full payload for checkout session creation
    const sessionObj = {
      lang: utils.getStoreLanguageFromStore(store),
      merchant_code: getMerchantCode(responseObject, paymentConfig),
      merchant_urls: buildMerchantUrls(responseObject, store, paymentConfig)
    };

    const payment = buildPaymentObject(quote, responseObject);

    const paymentMethods = globalThis.paymentMethods || {};
    const enablePaymentOptimizations = paymentMethods.enablePaymentOptimizations !== false; // Default: true (enabled)

    // Check if customerId is present (not blank/empty) for guest user handling
    const customerId = quote?.customerId;
    const isGuestUser = !customerId || customerId === '' || (typeof customerId === 'string' && customerId.trim() === '');

    logger.info(
      `### enablePaymentOptimizations ### Tabby buildSessionPayload for Quote: ${
        quote?.id
      } ### enablePaymentOptimizations: ${enablePaymentOptimizations}, isGuestUser: ${isGuestUser}, customerId: ${
        customerId || 'empty'
      }`
    );

    const apmSpanGetRegisteredSince = apmTransaction.startSpan('getRegisteredSince');
    const apmSpanFindOrdersByCustomer = apmTransaction.startSpan('findOrdersByCustomer');

    const { registeredSince, totalOrders } = await getCustomerData(
      isGuestUser,
      customerId,
      enablePaymentOptimizations,
      quote?.id
    );

    apmSpanGetRegisteredSince.end();
    apmSpanFindOrdersByCustomer.end();

    payment.buyer_history = {
      registered_since: registeredSince,
      loyalty_level: checkLoyalty(totalOrders, paymentConfig),
      wishlist_count: 0
    };

    payment.order = {
      reference_id: _.toString(quote?.id),
      items: orderItems(quote)
    };

    payment.order_history = [];
    payment.shipping_address = {};
    sessionObj.payment = payment;
    return sessionObj;
  }
};

const getMerchantCode = (responseObject, paymentConfig) => {
  const xSource = _.lowerCase(responseObject.xSource);
  const clientVersionNumber = responseObject.xClientVersion?.split('.').join('') || 10000;
  if (xSource !== 'msite' && xSource !== 'oldmsite' && Number(clientVersionNumber) <= 337) {
    return paymentConfig.oldMerchantCode;
  }
  return paymentConfig.merchantCode;
};

const buildPaymentObject = (quote, responseObject) => {
  const quoteAddrs = quote.quoteAddress;
  logger.info(`### CHEKING TYPE FOR AMOUNT  ######### ${JSON.stringify(typeof responseObject?.grandTotal)}`);
  logger.info(
    `### CHEKING RESPONSE OBJECT TO SEE THE AMOUNT DECIMAL VALUES  ######### ${JSON.stringify(responseObject)}`
  );
  return {
    amount: responseObject?.grandTotal?.toFixed(2),
    currency: quote.storeCurrencyCode,
    description: '',
    buyer: {
      phone: quoteAddrs?.mobileNumber,
      email: quote.customerEmail,
      name: `${quoteAddrs?.firstname} ${quoteAddrs?.lastname}`
    }
  };
};

const buildMerchantUrls = (responseObject, store, paymentConfig) => ({
  success: utils.buildUrl('success', responseObject, store, paymentConfig),
  cancel: utils.buildUrl('cancel', responseObject, store, paymentConfig),
  failure: utils.buildUrl('failure', responseObject, store, paymentConfig)
});

const orderItems = quote => {
  return quote.quoteItem?.map(item => {
    return {
      title: item.name,
      quantity: item.qty,
      unit_price: item.priceInclTax,
      reference_id: item.sku,
      image_url: item?.imgUrl,
      gender: item.gender,
      brand: item.brandName
    };
  });
};

/**
 * Extract loyaltyLevel from config
 * @param {*} totalOrder
 * @param {*} paymentConfig
 * @returns
 */
const checkLoyalty = (totalOrder, paymentConfig) => {
  const loyaltyLevel = _.find(
    paymentConfig.loyaltyLevels,
    lev => _.toNumber(lev.minOrder) <= _.toNumber(totalOrder) && _.toNumber(lev.maxOrder) >= _.toNumber(totalOrder)
  );
  return loyaltyLevel ? loyaltyLevel.level : 0;
};

const getRegisteredSince = async customerId => {
  let registeredOn = moment().utc().format('YYYY-MM-DDTHH:mm:ss.SSSZ');
  try {
    if (customerId && customerId !== '' && (typeof customerId !== 'string' || customerId.trim() !== '')) {
      const payfortConfig = globalThis?.payfortConfig || {};
      const paymentMethods = globalThis.paymentMethods || {};
      const enablePaymentOptimizations = paymentMethods.enablePaymentOptimizations !== false; // Default: true (enabled)
      const CUSTOMER_SERVICE_BASE_URL = payfortConfig['order_details']?.CUSTOMER_SERVICE_BASE_URL;
      const url = `${CUSTOMER_SERVICE_BASE_URL}/rest/customer/oms/find/${customerId}`;
      const httpTimeout = enablePaymentOptimizations ? paymentMethods.credentials?.httpTimeoutInMs || 3000 : undefined;
      logger.info(
        `### enablePaymentOptimizations ### Tabby getRegisteredSince for customerId: ${customerId} ### enablePaymentOptimizations: ${enablePaymentOptimizations}, httpTimeout: ${
          httpTimeout || 'not set'
        }`
      );
      const config = {
        headers: {
          'authorization-token': AUTH_INTERNAL_HEADER_BEARER_TOKEN?.split(',')?.[0],
          'x-source': 'payment-service'
        }
      };
      if (httpTimeout) {
        config.timeout = httpTimeout;
      }
      const response = await axios.get(url, config);
      if (response?.status === 200 && response?.data) {
        const createdAt = response?.data?.customer?.createdAt;
        const result = createdAt ? moment(createdAt).format('YYYY-MM-DDTHH:mm:ss.SSSZ') : registeredOn;
        logger.info(
          `### enablePaymentOptimizations ### Tabby getRegisteredSince for customerId: ${customerId} ### Success, registeredSince: ${result}`
        );
        return result;
      }
      logger.info(
        `### enablePaymentOptimizations ### Tabby getRegisteredSince for customerId: ${customerId} ### Response from customer API. Status : ${
          response?.status
        }, Data: ${JSON.stringify(response?.data)}`
      );
    } else {
      logger.info(
        `### enablePaymentOptimizations ### Tabby getRegisteredSince ### Skipping - customerId is empty/blank`
      );
    }
  } catch (error) {
    const message = stringifyError(error);
    logger.error(`Error In getting Customer Registered Since: ${customerId}`, `Message : ${message}`);
  }
  return registeredOn;
};

const findOrdersByCustomer = async customerId => {
  if (!customerId || customerId === '' || (typeof customerId === 'string' && customerId.trim() === '')) {
    logger.info(
      `### enablePaymentOptimizations ### Tabby findOrdersByCustomer ### Skipping - customerId is empty/blank, returning 0`
    );
    return 0;
  }
  const findOrdersQuery = `select count(1) as total_orders from sales_order where customer_id=${customerId} AND  state IN ('processing', 'delivered', 'shipped')`;
  try {
    logger.info(
      `### enablePaymentOptimizations ### Tabby findOrdersByCustomer for customerId: ${customerId} ### Executing query: ${findOrdersQuery}`
    );
    const response = await mysql.query(findOrdersQuery);
    const data = JSON.parse(JSON.stringify(response));
    const totalOrders = data[0][0].total_orders;
    logger.info(
      `### enablePaymentOptimizations ### Tabby findOrdersByCustomer for customerId: ${customerId} ### Success, totalOrders: ${totalOrders}`
    );
    return totalOrders;
  } catch (error) {
    logger.error(error, `Error In getting Total Orders for Customer : ${customerId}`, `Query : ${findOrdersQuery}`);
  }
  return 0;
};

const findInstallmentDates = data => {
  return data?.installments?.map(val => {
    const fmtDate = moment(val.due_date).format('DD MMM YY');
    val['due_date'] = fmtDate;
    return val;
  });
};
