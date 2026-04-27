const request = require('request');
const _ = require('lodash');
const APIError = require('../errors/api-error');
const crypto = require('crypto');
const { compareVersions } = require('../middlewares/jwt/validateToken');

exports.createOrder = async (body, headers) => {
  const { storeId = 3, appVersion = '', paymentMode } = body;
  const apiDomain = global?.payfortConfig?.payfort?.paymentService?.apiDomain;
  const xSourceHeader = extractXSourceHeader(paymentMode, headers, body);
  const xHeaderToken = getXHeaderToken(headers['x-header-token']);

  const store = getStoreById(storeId);
  if (!store) throw new APIError({ message: 'Error In Processing Payments! Store ID not found!' });

  const orderCreateRequest = buildOrderRequest(body, headers, store);
  const headerData = buildRequestHeaders(headers, xSourceHeader, appVersion, xHeaderToken);
  const isMobileApp = headers['x-source'] !== 'msite';
  // Default → use V2
  let useV3 = false;
  if (isMobileApp && typeof compareVersions === 'function') {
    try {
      const pm = global?.paymentMethods ?? {};
      const splitOrderCheck = Array.isArray(pm?.splitOrderCheck) ? pm.splitOrderCheck : [];
      // Find config for this store
      const storeSplitConfig = splitOrderCheck.find(c => Number(c.storeId) === Number(storeId));
      // client >= configured version
      useV3 = compareVersions(headers['x-client-version'], storeSplitConfig.appVersion.trim()) >= 0;
    } catch (err) {
      useV3 = false; // fallback safely
    }
  }
  if (useV3) {
    return await createOrderV3ApiCall(orderCreateRequest, headerData, apiDomain);
  }

  return await createOrderApiCall(orderCreateRequest, headerData, apiDomain);
};

function extractXSourceHeader(paymentMode, headers, body) {
  if (paymentMode === 'card') return headers['x-source'] || body?.xSourceHeader;
  if (paymentMode === 'apple') return body?.xSourceHeader || 'ios';
  return '';
}

function getStoreById(storeId) {
  const stores = global.config?.environments[0]?.stores;
  return _.find(stores, s => s.storeId == storeId);
}

function buildOrderRequest(body, headers, store) {
  const {
    customerId,
    quoteId,
    ipAddress,
    storeId = 3,
    merchantReference,
    appVersion = '',
    paymentData,
    orderIncrementId,
    retryPaymentReplica
  } = body;

  const isApplePay = shouldUseApplePay(headers, paymentData);

  const source = getSourceValue(body?.source);
  const payfortAuthorized = store?.isPayfortAuthorized || false;

  const request = {
    customerId,
    quoteId,
    ipAddress,
    storeId,
    source,
    merchantReference,
    appVersion,
    payfortAuthorized,
    isApplePay: isApplePay ? 1 : 0
  };

  if (orderIncrementId) request.orderIncrementId = orderIncrementId;
  if (retryPaymentReplica) request.retryPaymentReplica = retryPaymentReplica;

  return request;
}

function shouldUseApplePay(headers, paymentData) {
  const hasApplePayKey = global.paymentMethods?.sendApplePayKey ?? true;
  const hasEphemeralKey = paymentData?.header?.ephemeralPublicKey;
  const isIOS = headers['x-source']?.toLowerCase() === 'ios';

  return hasApplePayKey && hasEphemeralKey && isIOS;
}

function getSourceValue(source) {
  const parsed = parseInt(source);
  return parsed > -1 ? parsed : 2;
}

function buildRequestHeaders(headers, xSourceHeader, appVersion, xHeaderToken) {
  const headerData = {
    Token: headers.token,
    'Content-Type': 'application/json',
    'X-Source': xSourceHeader,
    'X-Client-Version': headers['x-client-version'] || appVersion,
    'X-Header-Cloud-Method': 'true',
    'x-header-token': headers['x-header-token'] || xHeaderToken,
    'x-original-forwarded-for': headers['x-forwarded-for'] || headers['cf-connecting-ip']
  };

  const deviceId = headers['device-id'] || headers['Device-Id'];
  if (deviceId) headerData['device-id'] = deviceId;

  return headerData;
}

function createOrderApiCall(orderCreateRequest, headers, apiDomain) {
  return new Promise(resolve => {
    request.post(
      {
        url: `${apiDomain}/rest/order/auth/v2/create`,
        json: orderCreateRequest,
        headers
      },
      (error, response = {}) => {
        resolve(handleOrderCreateResponse(error, response));
      }
    );
  });
}

function createOrderV3ApiCall(orderCreateRequest, headers, apiDomain) {
  return new Promise(resolve => {
    request.post(
      {
        url: `${apiDomain}/rest/order/auth/v3/create`,
        json: orderCreateRequest,
        headers
      },
      (error, response = {}) => {
        resolve(handleOrderCreateResponse(error, response));
      }
    );
  });
}

function handleOrderCreateResponse(error, response) {
  if (error) {
    return { error: formatError(500, 'Order creation failed', error) };
  }

  const body = response?.body;

  if (body?.statusCode === '200') {
    const { grandTotal = 0, orderId, incrementId, firstOrderByCustomer, payfortAuthorized } = body.response || {};
    return {
      amount: Number(grandTotal),
      orderId,
      incrementId,
      firstOrderByCustomer,
      payfortAuthorized,
      successResponse: body
    };
  }

  return {
    statusCode: body?.statusCode,
    error: formatError(body?.statusCode, 'Order creation failed', body)
  };
}

function formatError(statusCode, message, data) {
  return {
    statusCode,
    status: 'FAILURE',
    message,
    order: data
  };
}

function getXHeaderToken(token) {
  let finalToken = token;
  try {
    const tokenArray = token.split('_');
    if (tokenArray.length > 1 && !isNaN(tokenArray[tokenArray.length - 1])) {
      finalToken = tokenArray.slice(0, -1).join('_');
    }
  } catch (e) {
    console.log(JSON.stringify({ e, token, finalToken }));
  }
  return finalToken;
}

exports.createReturnOrder = async (body, headers) => {
  const { orderIncrementId, amount, orderId, rmaCount } = body;
  const incrementId = await getIncrementId(orderIncrementId, rmaCount);
  const baseResponse = buildBaseReturnResponse(orderId, incrementId, amount);
  const rmaAmount = await getRmsReturnAmount(body, headers);

  if (!rmaAmount?.error && rmaAmount?.amount) {
    baseResponse.response.grandTotal = rmaAmount.amount;
  } else {
    return {
      ...baseResponse,
      error: rmaAmount?.error,
      statusCode: '500',
      status: false,
      incrementId: null,
      successResponse: {
        error: rmaAmount?.error || null,
        quoteResponse: null,
        codOrder: false,
        ...baseResponse.response
      }
    };
  }

  return {
    ...baseResponse,
    successResponse: {
      error: null,
      quoteResponse: null,
      codOrder: false,
      ...baseResponse.response
    }
  };
};

function buildBaseReturnResponse(orderId, incrementId, amount) {
  return {
    orderId,
    incrementId,
    amount,
    response: {
      statusCode: '200',
      status: true,
      statusMsg: 'Return Order payment deducted successfully!',
      incrementId,
      orderId,
      grandTotal: amount,
      quoteId: null,
      tabbyPaymentId: null,
      firstOrderByCustomer: false,
      payfortAuthorized: '0'
    }
  };
}

const getIncrementId = async (orderIncrementId, rmaCount) => {
  const randomNumner = await generateTwoDigitRandomNumber();
  return orderIncrementId + 'R' + rmaCount + randomNumner;
};

const getRmsReturnAmount = async (body, headers) => {
  const { customerId, storeId, orderId, items, appVersion = '' } = body;

  const orderRmaRequest = { customerId, orderId, storeId, items };
  const apiDomain = global?.payfortConfig?.payfort?.paymentService?.apiDomain;
  const xSourceHeader = headers['x-source'] || headers['X-Source'] || body?.xSourceHeader;
  const xHeaderToken = getXHeaderToken(headers['x-header-token']);

  const headerData = buildRequestHeaders(headers, xSourceHeader, appVersion, xHeaderToken);

  return new Promise(resolve => {
    request.post(
      {
        url: `${apiDomain}/rest/order/auth/v2/rma/returnFeeToPay`,
        json: orderRmaRequest,
        headers: headerData
      },
      (error, response = {}) => {
        return resolve(handleRmsReturnAmountResponse(error, response));
      }
    );
  });
};

const handleRmsReturnAmountResponse = (error, response) => {
  if (error) {
    return { error: formatError(500, 'Return amount fetch failed', error) };
  }

  const responseBody = response?.body;

  if (typeof responseBody === 'number' && responseBody > 0) {
    return { amount: responseBody };
  }

  if (responseBody?.statusCode !== '200') {
    return { error: formatError(500, 'Return amount fetch failed', responseBody) };
  }

  return {}; // Handles edge case where statusCode is 200 but no actionable content
};

const generateTwoDigitRandomNumber = async () => {
  return crypto.randomInt(0, 100);
};
