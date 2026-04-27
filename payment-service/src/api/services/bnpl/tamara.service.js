const axios = require('axios');
const _ = require('lodash');
const logger = require('../../../config/logger');
const APIError = require('../../errors/api-error');
const utils = require('./utils');
const TAMARA_INSTALLMENTS_3 = 'tamara_installments_3';
const TAMARA_INSTALLMENTS_6 = 'tamara_installments_6';
const redisClient = require('../../../config/redis');
const moment = require('moment');
const COLOR_CODE = '#545454';

/**
 * Returns session from the cache otherwise hit API to create a new one
 * @param {*} quote
 * @returns
 */
exports.initTamaraPayment = async (quote, responseObject, store, paymentConfig) => {
  try {
    const apmTransaction = globalThis.apm.startTransaction('tamaraTransaction', 'request');
    const paymentMethods = globalThis.paymentMethods;
    if (quote.paymentMethod === TAMARA_INSTALLMENTS_3 || quote.paymentMethod === TAMARA_INSTALLMENTS_6) {
      return await handleTamaraInstallment(quote, responseObject, store, paymentConfig, apmTransaction, paymentMethods);
    } else {
      return await handleTamaraNonInstallment(quote, responseObject, store, paymentConfig, apmTransaction, paymentMethods);
    }
  } catch (error) {
    logger.error(`### Error in Tamara Payments: ${quote.id}`, error);
    throw new APIError({ message: 'Error in Tamara Payments.' });
  }
};

const handleTamaraInstallment = async (quote, responseObject, store, paymentConfig, apmTransaction, paymentMethods) => {
  const key = `${quote.paymentMethod}_${quote.id}_${responseObject.grandTotal}`;
  
  const apmSpanRedisFetch = apmTransaction.startSpan('redisFetch');
  const cacheSession = await redisClient.get(key);
  apmSpanRedisFetch.end();

  if (cacheSession && !quote.retryPayment) {
    const cacheValue = JSON.parse(cacheSession);
    if (!_.includes(quote.paymentMethod, 'tamara')) {
      delete cacheValue['payment_id'];
    }
    apmTransaction.end();
    return cacheValue;
  } else {
    const session = await generateSession(
      paymentMethods,
      responseObject,
      paymentConfig,
      quote,
      store,
      apmTransaction
    );
    return await putSeesionInCache(key, session, quote, apmTransaction);
  }
};

const handleTamaraNonInstallment = async (quote, responseObject, store, paymentConfig, apmTransaction, paymentMethods) => {
  if (paymentMethods.tamaraEligibilityCheck) {
    const resTamaraEligibility = await checkTamaraEligibility(
      responseObject,
      paymentConfig,
      quote,
      store,
      apmTransaction
    );
    apmTransaction.end();
    return resTamaraEligibility;
  } else {
    return getPayments(quote, responseObject, store, paymentConfig, apmTransaction);
  }
};

const generateSession = async (paymentMethods, responseObject, paymentConfig, quote, store, apmTransaction) => {
  let session = {};
  const shouldDoEligibilityOnly = quote?._shouldDoEligibilityOnly === true;

  logger.info(
    `### bnplEligibilityOnly ### Tamara generateSession for Quote: ${quote?.id} ### shouldDoEligibilityOnly: ${shouldDoEligibilityOnly}`
  );

  // If shouldDoEligibilityOnly is true or tamaraEligibilityCheck is enabled, use eligibility check
  if (shouldDoEligibilityOnly || paymentMethods.tamaraEligibilityCheck) {
    const paymentsConfig = await checkTamaraEligibility(responseObject, paymentConfig, quote, store, apmTransaction);
    if (paymentsConfig.tamara_installments_3 || paymentsConfig.tamara_installments_6) {
      // Only create full session if shouldDoEligibilityOnly is false
      if (!shouldDoEligibilityOnly) {
        session = await createSession(quote, responseObject, store, paymentConfig, apmTransaction);
        mergeInstallments(session, paymentsConfig);
      } else {
        // For eligibility-only, just return the eligibility config (no web_url, no payment_id)
        session = paymentsConfig;
      }
    }
  } else {
    session = await createSession(quote, responseObject, store, paymentConfig, apmTransaction);
  }

  return session;
};

const mergeInstallments = (session, paymentsConfig) => {
  if (session.tamara_installments_3 && paymentsConfig?.tamara_installments_3) {
    session.tamara_installments_3.installments = paymentsConfig.tamara_installments_3.installments;
  }
  if (session.tamara_installments_6 && paymentsConfig?.tamara_installments_6) {
    session.tamara_installments_6.installments = paymentsConfig.tamara_installments_6.installments;
  }
};


const putSeesionInCache = async (key, session, quote, apmTransaction) => {
  const apmSpanRedisSet = apmTransaction.startSpan('redisSet');
  if (session) {
    await redisClient.set(key, JSON.stringify(session));
    utils.setTTLForRedisKey(key);
    if (!_.includes(quote.paymentMethod, 'tamara')) delete session['payment_id'];
    apmSpanRedisSet.end();
    apmTransaction.end();
    return session;
  } else {
    apmSpanRedisSet.end();
    apmTransaction.end();
    throw new APIError({ message: 'Error in Tamara Payments.' });
  }
};

const resetPaymentMethods = (responseObject, option) => {
  const payments = responseObject?.availablePaymentMethods?.filter(m => !_.includes(m, option));
  responseObject.availablePaymentMethods = payments;
};

const checkTamaraEligibility = async (responseObject, paymentConfig, quote, store, apmTransaction) => {
  const apmSpan = apmTransaction.startSpan('tamaraCheckEligibility');
  try {
    const payload = buildEligibilityPayload(responseObject, quote, store);
    const config = buildEligibilityRequestConfig(paymentConfig);
    const response = await axios.post(`${paymentConfig.tamaraApiUrl}/credit-pre-check`, JSON.stringify(payload), config);

    apmSpan.end();

    if (response?.status === 200) {
      return await processAPIResponse({
        responseObject,
        response,
        amount: Number(payload.total_amount.amount),
        country: store.websiteCode,
        currency: store.storeCurrency,
        paymentConfig,
        apmTransaction,
        quote
      });
    }

    console.log(`Tamara eligibility check success : ${responseObject.quoteId}, Status : ${response.status}`);
  } catch (e) {
    console.log(e, 'Error in checking tamara eligibility');
    logger.error(`### Error in Tamara Eligibility check Payment: ${quote.id}`, e);
    apmSpan.end();
  }
  return {};
};

const calculateNetAmount = (responseObject) => {
  const grandTotal = Number(responseObject.grandTotal || 0);
  const codCharges = Number(responseObject.codCharges || 0);
  const estimatedTotal = Number(responseObject.estimatedTotal || 0);

  let amount = grandTotal - codCharges;
  if (amount === 0) amount = estimatedTotal;

  return amount;
};

const getCurrencyAmountObject = (amount, currency) => ({
  amount: String(amount || ''),
  currency: currency
});

const getUpperCaseCountryCode = (websiteCode) => (websiteCode || '').toUpperCase();

const buildEligibilityPayload = (responseObject, quote, store) => {
  const addr = quote?.quoteAddress || {};
  const amount = calculateNetAmount(responseObject);
  const currency = store.storeCurrency;
  const countryCode = getUpperCaseCountryCode(store.websiteCode);

  return {
    total_amount: getCurrencyAmountObject(amount, currency),
    country_code: countryCode,
    items: [
      {
        total_amount: getCurrencyAmountObject(amount, currency)
      }
    ],
    consumer: {
      phone_number: addr.mobileNumber || ''
    },
    shipping_address: {
      country_code: countryCode
    }
  };
};


const buildEligibilityRequestConfig = (paymentConfig) => {
  const paymentMethods = globalThis.paymentMethods || {};
  const enablePaymentOptimizations = paymentMethods.enablePaymentOptimizations !== false; // Default: true (enabled)
  const httpTimeout = enablePaymentOptimizations 
    ? (paymentConfig.httpTimeout || paymentMethods.credentials?.httpTimeoutInMs || 5000) 
    : undefined;
  const config = {
    headers: {
      Authorization: `Bearer ${paymentConfig.tamaraToken}`,
      'Content-Type': 'application/json'
    }
  };
  if (httpTimeout) {
    config.timeout = httpTimeout;
  }
  return config;
};
const getPayments = async (quote, responseObject, store, paymentConfig, apmTransaction) => {
  const apmSpanTamaraGetPayments = apmTransaction.startSpan('tamaraGetPayments');
  const currency = store.storeCurrency;
  const country = store.websiteCode;
  const amount = Number(responseObject.grandTotal);
  const url = `${
    paymentConfig.tamaraApiUrl
  }/payment-types?country=${country?.toUpperCase()}&currency=${currency}&order_value=${amount}`;
  const paymentMethods = globalThis.paymentMethods || {};
  const enablePaymentOptimizations = paymentMethods.enablePaymentOptimizations !== false; // Default: true (enabled)
  const httpTimeout = enablePaymentOptimizations 
    ? (paymentConfig.httpTimeout || paymentMethods.credentials?.httpTimeoutInMs) 
    : undefined;
  const config = {
    headers: {
      Authorization: `Bearer ${paymentConfig.tamaraToken}`,
      'Content-Type': 'application/json'
    }
  };
  if (httpTimeout) {
    config.timeout = httpTimeout;
  }
  return axios
    .get(url, config)
    .then(res => {
      logger.info(
        `### Tamara Get Payment Options for Quote : ${quote.id} ### Request : ${JSON.stringify(
          config
        )}, ### Response : ${JSON.stringify(res.data)}`
      );
      apmSpanTamaraGetPayments.end();
      apmTransaction.end();
      return extractResponse(res.data, responseObject, quote);
    })
    .catch(error => {
      logger.error(
        error,
        `### Error in Get Payment Options for Quote: ${quote.id} 
        ### Request : ${JSON.stringify(config)} ### Response : ${JSON.stringify(error?.response?.data)}`
      );
      apmSpanTamaraGetPayments.end();
      apmTransaction.end();
      return {};
    });
};

const createSession = async (quote, responseObject, store, paymentConfig, apmTransaction) => {
  const payload = await buildSessionPayload(quote, responseObject, store, paymentConfig);
  const url = paymentConfig.tamaraApiUrl;
  const paymentMethods = globalThis.paymentMethods || {};
  const enablePaymentOptimizations = paymentMethods.enablePaymentOptimizations !== false; // Default: true (enabled)
  const httpTimeout = enablePaymentOptimizations 
    ? (paymentConfig.httpTimeout || paymentMethods.credentials?.httpTimeoutInMs) 
    : undefined;
  const config = {
    headers: {
      Authorization: `Bearer ${paymentConfig.tamaraToken}`,
      'Content-Type': 'application/json'
    }
  };
  if (httpTimeout) {
    config.timeout = httpTimeout;
  }
  const apmSpanTamaraCreateSession = apmTransaction.startSpan('tamaraCreateSession');
  return axios
    .post(url, payload, config)
    .then(res => {
      logger.info(
        `### Tamara Create Session for Quote : ${quote.id}  ### Request : ${JSON.stringify(
          config
        )} ### Response : ${JSON.stringify(res.data)}`
      );
      apmSpanTamaraCreateSession.end();
      return extractSessionResponse(res.data, quote, responseObject);
    })
    .catch(error => {
      const res = error?.response?.data;
      const msg = res ? res : error.message;
      logger.error(
        `### Error in Creating Tamara Session for Quote: ${quote.id} ### Request : ${JSON.stringify(
          config
        )} ### Response : ${JSON.stringify(msg)}`
      );
      apmSpanTamaraCreateSession.end();
      return {};
    });
};

const extractSessionResponse = (response, quote, responseObject) => {
  const installments = quote?.paymentMethod === 'tamara_installments_3' ? 4 : 6;
  const totalAmount = responseObject.grandTotal === '0' ? responseObject.estimatedTotal : responseObject.grandTotal;
  const pay_per_installment = _.toString(calculateInstallmentAmounts(totalAmount, installments));

  const tabbyPaymentOps = {};
  if (!_.isEmpty(response)) {
    tabbyPaymentOps[`${quote.paymentMethod}`] = {
      web_url: response.checkout_url,
      amount_to_pay: _.toString(totalAmount),
      installments_count: installments,
      pay_per_installment: pay_per_installment,
      color_code: COLOR_CODE
    };
  }
  tabbyPaymentOps['payment_id'] = response.order_id;
  return tabbyPaymentOps;
};

const calculateInstallmentAmounts = (totalAmount, installments) => {
  try {
    const amount = Number(totalAmount) / installments;
    return _.round(amount, 2);
  } catch (error) {
    return 0;
  }
};

const buildSessionPayload = async (quote, responseObject, store, paymentConfig) => {
  const sessionObj = {
    order_reference_id: _.toString(quote?.id),
    order_number: _.toString(quote?.id),
    description: 'Dresses/Accessories',
    total_amount: {
      amount: responseObject.grandTotal === '0' ? responseObject.estimatedTotal : responseObject.grandTotal,
      currency: store.storeCurrency
    },
    instalments: quote?.paymentMethod === 'tamara_installments_3' ? 4 : 6,
    country_code: _.toUpper(store.websiteCode),
    payment_type: 'PAY_BY_INSTALMENTS',
    locale: store?.storeLanguage,
    items: orderItems(quote),
    consumer: buildConsumerInfo(quote),
    shipping_address: buildAddress(quote),
    billing_address: buildAddress(quote),
    tax_amount: defaultAmount(store.storeCurrency),
    shipping_amount: defaultAmount(store.storeCurrency),
    merchant_url: buildMerchantUrls(responseObject, store, paymentConfig),
    platform: 'Styli OMS',
    risk_assessment: { has_cod_failed: false }
  };

  addDiscountIfPresent(quote, store, sessionObj);

  return sessionObj;
};

const buildConsumerInfo = (quote) => {
  const addr = quote.quoteAddress;
  const lastName = addr?.lastname?.trim() || '.';
  const firstName = addr?.firstname?.trim() || lastName;

  return {
    first_name: firstName,
    last_name: lastName,
    phone_number: addr?.mobileNumber,
    email: quote.customerEmail
  };
};

const getTrimmedName = (name, fallback) => {
  return (name?.trim() || fallback);
};

const buildAddress = (quote) => {
  const addr = quote?.quoteAddress || {};

  const lastName = getTrimmedName(addr.lastname, '.');
  const firstName = getTrimmedName(addr.firstname, lastName);

  return {
    first_name: firstName,
    last_name: lastName,
    line1: addr.buildingNumber || '',
    line2: '',
    region: addr.region || '',
    postal_code: addr.area || '',
    city: addr.city || '',
    country_code: addr.countryId || '',
    phone_number: addr.mobileNumber || ''
  };
};


const defaultAmount = (currency) => ({
  amount: '00.00',
  currency
});

const buildMerchantUrls = (responseObject, store, paymentConfig) => ({
  success: utils.buildUrl('success', responseObject, store, paymentConfig),
  cancel: utils.buildUrl('cancel', responseObject, store, paymentConfig),
  failure: utils.buildUrl('failure', responseObject, store, paymentConfig),
  notification: paymentConfig.tamaraNotificationUrl
});

const addDiscountIfPresent = (quote, store, sessionObj) => {
  try {
    if (quote.discountData?.length) {
      const totalDiscount = quote.discountData.reduce((acc, item) => acc + Number(item.value), 0);
      sessionObj.discount = {
        name: 'STYLICOUPON',
        amount: {
          amount: String(totalDiscount) || '0',
          currency: store.storeCurrency
        }
      };
    }
  } catch (e) {
    console.log('Tamara discount object exception ', e);
  }
};


const extractResponse = async (response, responseObject, quote) => {
  const tabbyPaymentOps = {};
  let totalAmount = getAdjustedAmount(responseObject, quote);

  response[0]?.supported_instalments?.forEach(res => {
    const key = getInstalmentKey(res?.instalments);
    if (key) {
      tabbyPaymentOps[key] = buildPaymentRes(totalAmount, res?.instalments);
    }
  });

  if (_.includes(quote.paymentMethod, 'tamara')) {
    tabbyPaymentOps['payment_id'] = response?.order_id;
  }

  return tabbyPaymentOps;
};

const shouldSubtractCODCharges = (quote, amount, responseObject) => {
  const isCashOnDelivery = quote?.paymentMethod === 'cashondelivery';
  const hasCODCharges = Number(quote?.codCharges || 0) > 0;
  const isEstimatedAmount = amount == responseObject.estimatedTotal;

  return isCashOnDelivery && hasCODCharges && isEstimatedAmount;
};

const getAdjustedAmount = (responseObject, quote) => {
  let amount = responseObject.grandTotal === '0'
    ? responseObject.estimatedTotal
    : responseObject.grandTotal;

  if (shouldSubtractCODCharges(quote, amount, responseObject)) {
    amount = amount - Number(quote.codCharges || 0);
    globalThis.loggerInfo(`initTamaraPayment: Subtracting COD charges from amount : New amount is : ${amount}`);
  }

  return amount;
};


const getInstalmentKey = (instalment) => {
  if ([3, 4].includes(instalment)) return 'tamara_installments_3';
  if (instalment === 6) return 'tamara_installments_6';
  return null;
};


const buildPaymentRes = (totalAmount, installments) => {
  const pay_per_installment = _.toString(calculateInstallmentAmounts(totalAmount, installments));
  return {
    amount_to_pay: _.toString(totalAmount),
    installments_count: installments,
    pay_per_installment: _.toString(pay_per_installment)
  };
};

const orderItems = quote => {
  return quote.quoteItem?.map(item => {
    return {
      name: item.name,
      type: 'simple',
      description: '',
      quantity: item.qty,
      total_amount: {
        amount: item.priceInclTax,
        currency: quote.storeCurrencyCode
      },
      reference_id: quote.id,
      image_url: item.imgUrl,
      sku: item.sku
    };
  });
};

const tamaraPaymentPlans = async (installment, country, currency, amount, tamara_installments, paymentConfig) => {
  const url = `${paymentConfig.tamaraApiUrl}/payment-plan`;
  const paymentMethods = globalThis.paymentMethods || {};
  const enablePaymentOptimizations = paymentMethods.enablePaymentOptimizations !== false; // Default: true (enabled)
  const httpTimeout = enablePaymentOptimizations 
    ? (paymentConfig.httpTimeout || paymentMethods.credentials?.httpTimeoutInMs || 5000) 
    : undefined;
  const paymentPlanConfig = {
    headers: {
      Authorization: `Bearer ${paymentConfig.tamaraToken}`,
      'Content-Type': 'application/json'
    },
    params: {
      country: country?.toUpperCase(),
      currency: currency,
      payment_type: 'PAY_BY_INSTALMENTS',
      public_key: paymentConfig.tamarapublickey,
      order_value: String(amount || ''),
      instalments: installment
    }
  };
  if (httpTimeout) {
    paymentPlanConfig.timeout = httpTimeout;
  }

  const paymentPlanResponse = await axios.get(url, paymentPlanConfig);
  if (paymentPlanResponse?.status == 200) {
    const installments = [];
    const paymentPlanData = paymentPlanResponse.data;
    paymentPlanData.repayments.forEach(repayment => {
      let repaymentData = {};
      repaymentData.amount = repayment.amount.amount.toString();
      const fmtDate = moment(repayment.due_date).format('DD MMM YY');
      repaymentData.due_date = fmtDate;
      installments.push(repaymentData);
    });
    if (installments.length > 0) {
      tamara_installments.pay_per_installment = installments[0].amount;
    }

    tamara_installments.installments = installments;
  }
};

const processAPIResponse = async ({
  responseObject,
  response,
  amount,
  country,
  currency,
  paymentConfig,
  apmTransaction,
  quote = null
}) => {
  console.log(
    `Tamara eligibility check success : ${responseObject.quoteId} response :: ${JSON.stringify(response.data)}`
  );
  const shouldDoEligibilityOnly = quote?._shouldDoEligibilityOnly === true;
  
  logger.info(
    `### bnplEligibilityOnly ### Quote: ${responseObject.quoteId} ### shouldDoEligibilityOnly: ${shouldDoEligibilityOnly}`
  );
  
  const paymentsConfig = {};
  const res = response.data;
  if (res?.[0]?.supported_instalments?.length) {
    const installments = res[0].supported_instalments;

    const tamara_installments_3 = installments.find(e => {
      return e.instalments === 3 || e.instalments === 4;
    });
    if (!tamara_installments_3) {
      resetPaymentMethods(responseObject, TAMARA_INSTALLMENTS_3);
    } else {
      let installmentObj = {};
      installmentObj.installments_count = 3;
      installmentObj.amount_to_pay = amount.toString();
      
      // Skip payment plans API call if shouldDoEligibilityOnly is true
      if (shouldDoEligibilityOnly) {
        // Calculate pay_per_installment locally without API call
        installmentObj.pay_per_installment = _.toString(_.round(Number(amount) / 4, 2));
        logger.info(
          `### bnplEligibilityOnly ### Quote: ${responseObject.quoteId} ### Skipping tamaraPaymentPlans for 3 installments`
        );
      } else {
        const apmSpanTamaraPaymentPlans_3 = apmTransaction.startSpan('tamaraPaymentPlans_3');
        await tamaraPaymentPlans(4, country, currency, amount, installmentObj, paymentConfig);
        apmSpanTamaraPaymentPlans_3.end();
      }
      paymentsConfig.tamara_installments_3 = installmentObj;
    }
    const tamara_installments_6 = installments.find(e => {
      return e.instalments === 6;
    });
    if (!tamara_installments_6) {
      resetPaymentMethods(responseObject, TAMARA_INSTALLMENTS_6);
    } else {
      let installmentObj = {};
      installmentObj.installments_count = 6;
      installmentObj.amount_to_pay = amount.toString();
      
      // Skip payment plans API call if shouldDoEligibilityOnly is true
      if (shouldDoEligibilityOnly) {
        // Calculate pay_per_installment locally without API call
        installmentObj.pay_per_installment = _.toString(_.round(Number(amount) / 6, 2));
        logger.info(
          `### bnplEligibilityOnly ### Quote: ${responseObject.quoteId} ### Skipping tamaraPaymentPlans for 6 installments`
        );
      } else {
        const apmSpanTamaraPaymentPlans_6 = apmTransaction.startSpan('tamaraPaymentPlans_6');
        await tamaraPaymentPlans(6, country, currency, amount, installmentObj, paymentConfig);
        apmSpanTamaraPaymentPlans_6.end();
      }
      paymentsConfig.tamara_installments_6 = installmentObj;
    }
  } else {
    resetPaymentMethods(responseObject, TAMARA_INSTALLMENTS_3);
    resetPaymentMethods(responseObject, TAMARA_INSTALLMENTS_6);
  }
  return paymentsConfig;
};
