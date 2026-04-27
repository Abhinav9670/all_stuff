const { isEmpty } = require('lodash');
const logger = require('../../config/logger');
const { createOrder, createReturnOrder } = require('../services/order.service');
const { purchase, purchaseReturn, capturePayment } = require('../services/payfort');
const {
  createPurchaseReq,
  getClient,
  createCapturePaymentReq,
  getClientForCapturePayment
} = require('../services/payfort/create');
const { getIpAddress, getCurrencyAndPrecision } = require('../utils');
const urlUtils = require('url');
const { stringifyError } = require('../utils/log');

exports.cardPayment = async (req, res) => {
  try {
    const { body, headers, hostname } = req;
    global.loggerInfo(
      `card.controller Card Payment Begin  headers : ${JSON.stringify(headers)} body : ${JSON.stringify(body)}`
    );

    const ipAddress = getIpAddress(req);
    const customerIp = headers['cf-connecting-ip'];
    console.log(`Find correct Ip : cardPay : customer ip: ${customerIp}`);

    const { quoteId, storeId, returnPayment } = body || {};
    const { currency, useDeviceFingerPrint } = getCurrencyAndPrecision(storeId);
    body.paymentMode = 'card';

    const orderDetails = await handleOrderCreation(body, headers, returnPayment);
    if (!orderDetails.incrementId) {
      return handleOrderFailure(res, quoteId, orderDetails);
    }

    const apmTransaction = global.apm.startTransaction('PayfortCardTransaction', 'request');
    global.loggerInfo('card.controller - cardPayment - orderDetails: ', JSON.stringify(orderDetails));

    const purchaseData = createPurchaseReq({ body, currency, ipAddress, useDeviceFingerPrint, orderDetails });
    const client = getClient({ ...body, hostname });

    global.loggerInfo(
      `card.controller purchaseData : ${JSON.stringify(purchaseData)} || QUOTE ID:, ${quoteId}, ${JSON.stringify(
        orderDetails
      )} || client:, ${JSON.stringify(client)}`
    );

    const { error, success } = await purchase(client, purchaseData, apmTransaction);
    res.setHeader('Content-Type', 'application/json');
    res.status(200);

    if (!isEmpty(error)) {
      return handlePurchaseError(res, error, orderDetails, headers, body);
    }

    sendSuccessResponse(res, orderDetails, success);
    apmTransaction.end();
  } catch (e) {
    global.loggerInfo(`Error in Card Payment Error : ${stringifyError(e)}`);
    global.loggerError(e, 'Error Card Payment');
    res.status(500).send({ error: { paymentError: e.message } });
  }
};

async function handleOrderCreation(body, headers, returnPayment) {
  const startTime = new Date();
  let orderDetails = {};

  if (returnPayment) {
    orderDetails = await createReturnOrder(body, headers);
  } else {
    orderDetails = await createOrder(body, headers);
  }

  const endTime = new Date();
  global.loggerInfo(`Order Create API TIME: ${orderDetails?.incrementId}: ${endTime - startTime}ms`);

  return orderDetails;
}

function handleOrderFailure(res, quoteId, orderDetails) {
  global.loggerInfo(
    `Unable To Create Order: Quote ID : ${JSON.stringify({ quoteId })} , Order Details : ${JSON.stringify({
      orderDetails
    })}`
  );
  global.loggerError('Unable To Create Order:', JSON.stringify({ quoteId, orderDetails }));

  const error = orderDetails?.error;
  res.status(500).send({
    statusCode: orderDetails.statusCode,
    error,
    orderNumber: orderDetails.incrementId,
    orderId: orderDetails.orderId
  });
}

function handlePurchaseError(res, error, orderDetails, headers, body) {
  let errorDetails = '';
  try {
    errorDetails = JSON.stringify({
      statusCode: orderDetails.successResponse.statusCode,
      error: Object.assign({}, error, {
        order: orderDetails.successResponse
      }),
      orderNumber: orderDetails.incrementId,
      orderId: orderDetails.orderId
    });
    console.log('Printing inside try block of cardPayment error');
  } catch (e) {
    errorDetails = JSON.stringify({
      statusCode: orderDetails?.successResponse?.statusCode,
      error: e?.message,
      orderNumber: orderDetails?.incrementId,
      orderId: orderDetails?.orderId
    });
    console.log('Printing inside catch block of cardPayment error');
  }

  logger.info(`Error in card purchase. Error : ${errorDetails}`);
  logger.info(`Card Payment Begin  headers : ${JSON.stringify(headers)} body : ${JSON.stringify(body)}`);
  res.send(errorDetails);
}

function sendSuccessResponse(res, orderDetails, success) {
  const successObj = {
    statusCode: orderDetails.successResponse.statusCode,
    success: {
      order: orderDetails.successResponse,
      payfort: success?.payfort,
      redirectUrl: success?.redirectUrl,
      message: success?.message
    }
  };
  res.status(200).send(JSON.stringify(successObj));
}


exports.cardReturn = async (req, res) => {
  const payfortQuery = req.method === 'GET' ? req.query : req.body;

  const client = getClient({
    storeId: payfortQuery.merchant_extra4,
    reqHost: req.headers && req.headers.origin
  });

  const { error, success } = await purchaseReturn({ payfortQuery, client });
  const { storeUrl } = getCurrencyAndPrecision(payfortQuery.merchant_extra4);

  const {
    language,
    merchant_extra1 = '',
    response_code = '',
    response_message = ''
  } = success || error || {};

  let [, , , addressId, orderNumber, orderId] = merchant_extra1.split('_');

  const { payfort } = global?.payfortConfig;
  const { paymentService = {} } = payfort || {};
  const { websiteHost: host } = paymentService;

  logger.info('payfort ', payfort);
  logger.info('websiteHost ', host);
  logger.info('websiteHost ', host);

  const urlFormat = {
    protocol: 'https',
    host,
    query: error
  };
  logger.info('urlFormat done ');

  if (!isEmpty(error)) {
    const returnUrl = buildErrorRedirectUrl(urlFormat, storeUrl, language, orderNumber, orderId, response_code, response_message);
    logger.info('returnUrl:', returnUrl);
    return res.redirect(returnUrl.toString());
  }

  if (success) {
    logger.info(`Return:, ${success}`);
    logger.info('in side susccess:');

    const paymentStatus = getPaymentStatus(payfortQuery, payfort, success.status);

    logger.info('paymentStatus:', paymentStatus);

    const returnUrl = buildSuccessRedirectUrl(
      urlFormat,
      storeUrl,
      language,
      paymentStatus,
      orderId,
      orderNumber,
      addressId
    );

    logger.info('final redirect url:' + returnUrl);
    return res.redirect(returnUrl.toString());
  }
};

function buildErrorRedirectUrl(urlFormat, storeUrl, language, orderNumber, orderId, response_code, response_message) {
  const retPathname = `/${storeUrl}/${language === 'en' ? 'en' : 'ar'}/checkout/payment/replica`;

  return urlUtils.format(
    Object.assign({}, urlFormat, {
      pathname: retPathname,
      query: {
        orderNumber,
        orderId,
        response_code,
        response_message,
        paymentStatus: 'failed'
      }
    })
  );
}

function getPaymentStatus(payfortQuery, payfort, status) {
  const isAuth = payfortQuery.command === 'AUTHORIZATION';
  const successStatuses = isAuth
    ? payfort['PAYFORT_AUTHORIZATION_SUCCESS_STATUS']
    : payfort['PAYFORT_PURCHASE_SUCCESS_STATUS'];

  global.loggerInfo(
    'card.controller - payfort.PAYFORT_AUTHORIZATION_SUCCESS_STATUS: ',
    payfort['PAYFORT_AUTHORIZATION_SUCCESS_STATUS']
  );
  global.loggerInfo(
    'card.controller - payfort.PAYFORT_PURCHASE_SUCCESS_STATUS: ',
    payfort['PAYFORT_PURCHASE_SUCCESS_STATUS']
  );
  global.loggerInfo('card.controller - payfortQuery.command: ', payfortQuery.command);
  global.loggerInfo('card.controller: ', JSON.stringify(successStatuses));

  if (successStatuses.includes(status)) {
    return 'success';
  } else if (status === '15' || status === '19') {
    return 'pending';
  }
  return 'failed';
}

function buildSuccessRedirectUrl(urlFormat, storeUrl, language, paymentStatus, orderId, orderNumber, addressId) {
  const basePath = `/${storeUrl}/${language === 'en' ? 'en' : 'ar'}/checkout/payment`;

  let pathname = '';
  let query = { orderId, orderNumber };

  if (paymentStatus === 'pending') {
    pathname = `${basePath}/confirmation`;
    query = { ...query, paymentStatus, addressId };
  } else if (paymentStatus !== 'success') {
    logger.info('paymentStatus not success');
    pathname = `${basePath}/retry`;
    query = { ...query, paymentStatus, addressId };
  } else {
    logger.info('else  not success');
    pathname = `${basePath}/confirmation`;
  }

  return urlUtils.format(
    Object.assign({}, urlFormat, {
      pathname,
      query
    })
  );
}


exports.capturePayment = async (req, res) => {
  try {
    const { body, headers, hostname } = req;
    logger.info(`Capture Payment Begin  headers : ${JSON.stringify(headers)} body : ${JSON.stringify(body)}`);

    const captureData = createCapturePaymentReq({ body });
    const client = getClientForCapturePayment({ ...body, hostname });

    logger.info(`captureData : ${JSON.stringify(captureData)}`);
    logger.info(`Consul Config for Capture : ${JSON.stringify(client)}. Request Body : ${JSON.stringify(body)}`);

    const { error, success } = await capturePayment(client, captureData);
    res.setHeader('Content-Type', 'application/json');
    res.status(200);
    if (!isEmpty(error)) {
      global.loggerInfo(
        `Error in Capture Payment method. Error : ${stringifyError(error)}, captureData : ${JSON.stringify(
          captureData
        )}`
      );
      res.send(
        JSON.stringify({
          statusCode: success.statusCode,
          error: Object.assign({}, error, {
            response_message: success.message
          })
        })
      );
    } else {
      const successObj = {
        statusCode: success.statusCode,
        success: {
          message: success.message
        }
      };
      logger.info('message ', success.message);
      logger.info('capture payment ', success.message);
      res.status(200).send(JSON.stringify(successObj));
    }
  } catch (e) {
    global.loggerInfo(`Error in Capture Payment method. Error : ${stringifyError(e)}`);
    res.status(500).send({ error: { capturePaymentError: e.message } });
  }
};
