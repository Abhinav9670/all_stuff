const fs = require('fs/promises');
const { isEmpty } = require('lodash');
const request = require('request');
const logger = require('../../config/logger');
const { createOrder } = require('../services/order.service');
const { purchase } = require('../services/payfort');
const { createPurchaseReq, getClient } = require('../services/payfort/create');
const { getIpAddress, getCurrencyAndPrecision } = require('../utils');
const { stringifyError } = require('../utils/log');

const applePromise = options => {
  return new Promise((resolve, reject) => {
    request(options, (err, _response, body) => {
      if (err) {
        reject(err);
      }

      resolve(body);
    });
  });
};

exports.applePayValidate = async () => {
  let result = {};
  try {
    const { payfort = {} } = global?.payfortConfig || {};
    const { paymentService = {} } = payfort || {};
    const { validationUrl, apple_pay_merchant_id, websiteHost } = paymentService;

    const MERCHANT_IDENTIFIER = apple_pay_merchant_id;
    const MERCHANT_DOMAIN = websiteHost;
    const MERCHANT_DIAPLAY_NAME = websiteHost;

    const cert = await fs.readFile(process.env.MERCHANT_CERT_PATH);
    const key = await fs.readFile(process.env.MERCHANT_KEY_PATH);

    global.loggerInfo({ key, cert });

    const options = {
      url: validationUrl,
      cert: cert,
      key: key,
      method: 'POST',
      body: {
        merchantIdentifier: MERCHANT_IDENTIFIER,
        domainName: MERCHANT_DOMAIN,
        displayName: MERCHANT_DIAPLAY_NAME
      },
      json: true
    };
    global.loggerInfo(`Error at apple pay validate. Request : ${JSON.stringify(options)}`);

    result = await applePromise(options);
  } catch (e) {
    result = { error: e.message };
    global.loggerInfo(
      `Error at apple pay validate. Error : ${stringifyError(e)},Result obtained is: ${JSON.stringify(result)}`
    );
    global.loggerError(e, 'Error apple pay validate');
  }
  return result;
};

exports.applePay = async (req, res) => {
  try {
    const { body, headers, hostname } = req;
    logger.info(`Apple Payment Begin  headers : ${JSON.stringify(headers)} body : ${JSON.stringify(body)}`);
    const ipAddress = getIpAddress(req);
    const { quoteId, storeId } = body || {};
    const customerIp = headers['cf-connecting-ip'];
    console.log(`Find correct Ip : applePay : customer ip: ${customerIp}`);
    const { currency, useDeviceFingerPrint } = getCurrencyAndPrecision(storeId);
    body.paymentMode = 'apple';

    const orderDetails = await createOrder(body, headers);
    if (!orderDetails.incrementId) return handleOrderCreationFailure(res, quoteId, orderDetails);

    const apmTransaction = global.apm.startTransaction('applePayTransaction', 'request');

    const purchaseData = createPurchaseReq({
      body,
      currency,
      ipAddress,
      useDeviceFingerPrint,
      orderDetails,
      isApple: true
    });

    const client = getClient({ ...body, hostname, isApple: true });

    logger.info(
      `ApplePay Payment details. QuoteID : ${quoteId} ,Order Details :  ${JSON.stringify(
        orderDetails
      )} , Purchase Data : ${JSON.stringify(purchaseData)} ## Consul Data : ${JSON.stringify(client)}`
    );

    const { error, success } = await purchase(client, purchaseData, apmTransaction);
    res.setHeader('Content-Type', 'application/json');
    res.status(200);

    if (!isEmpty(error)) {
      return handlePurchaseError(res, error, quoteId, orderDetails, purchaseData);
    }

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
    apmTransaction.end();
  } catch (e) {
    global.loggerError(e, 'Error Apple Payment');
    res.status(500).send({ error: { paymentError: e.message } });
  }
};

function handleOrderCreationFailure(res, quoteId, orderDetails) {
  logger.info('QUOTE ID: ', JSON.stringify({ quoteId, orderDetails }));
  const error = orderDetails?.error;
  return res.status(500).send({
    statusCode: orderDetails.statusCode,
    error,
    orderNumber: orderDetails.incrementId,
    orderId: orderDetails.orderId
  });
}

function handlePurchaseError(res, error, quoteId, orderDetails, purchaseData) {
  logger.info(
    `Error at apple pay validate. Error : ${stringifyError(error)}, purchaseData ApplePay: ${JSON.stringify(
      purchaseData
    )},Quote ID : ${JSON.stringify({ quoteId })} , Order Details : ${JSON.stringify({ orderDetails })}`
  );

  const responseError = isGatewayTimeout(error)
    ? {
        message: error.message || '',
        name: error.name || '',
        order: orderDetails?.successResponse || {}
      }
    : Object.assign({}, error, {
        order: orderDetails?.successResponse || {}
      });

  res.send(
    JSON.stringify({
      statusCode: orderDetails?.successResponse.statusCode || 500,
      error: responseError,
      orderNumber: orderDetails?.incrementId || null,
      orderId: orderDetails?.orderId || null
    })
  );
}

function isGatewayTimeout(error) {
  return error.message && error.name && error.message.includes('Request failed with status code 504');
}

