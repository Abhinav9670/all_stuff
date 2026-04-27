const httpStatus = require('http-status');
const logger = require('../../config/logger');
const { paymentOptions, clearSession } = require('../services/payment.service');
const { countryCodeMap, getConfig } = require('../utils');
const _ = require('lodash');
const APIError = require('../errors/api-error');
const redisClient = require('../../config/redis');
const mysql = require('../../config/mysql');

exports.configs = async (req, res) => {
  try {
    const { countryCode } = req.body;
    const country = countryCodeMap(countryCode);
    if (!country) return res.status(400).json({ status: false, statusCode: '400', statusMsg: 'Country code invalid!' });
    const { payfort = {} } = global.payfortConfig;
    const config = payfort?.[`${country}_credentials`];
    if (!config) return res.status(400).json({ status: false, statusCode: '500', statusMsg: 'Config not found!' });

    const countryUC = country.toUpperCase();
    const storeConfig = global.config;
    const stores = storeConfig?.environments[0]?.stores;
    const store = _.find(stores, s => s.websiteCode == countryCode, 0);
    if (!store)
      throw new APIError({
        message: 'Error In Processing Payments! Store ID not found!'
      });

    const response = {
      card: {
        'Merchant Identifier': getConfig(config, countryUC, 'merchantIdentifier', 'CARD'),
        'Access Code': getConfig(config, countryUC, 'accessCode', 'CARD'),
        'Request SHA phrase': getConfig(config, countryUC, 'requestSHA', 'CARD'),
        'Response SHA phrase': getConfig(config, countryUC, 'responseSHA', 'CARD'),
        'order url': payfort['CARD_ORDER_URL'] || '',
        order_url: payfort['CARD_ORDER_URL'] || '',
        merchant_identifier: getConfig(config, countryUC, 'merchantIdentifier', 'CARD'),
        access_code: getConfig(config, countryUC, 'accessCode', 'CARD'),
        request_sha_phrase: getConfig(config, countryUC, 'requestSHA', 'CARD'),
        response_sha_phrase: getConfig(config, countryUC, 'responseSHA', 'CARD')
      },
      applePay: {
        'Merchant Identifier': getConfig(config, countryUC, 'merchantIdentifier', 'APPLE'),
        'Access Code': getConfig(config, countryUC, 'accessCode', 'APPLE'),
        'Request SHA phrase': getConfig(config, countryUC, 'requestSHA', 'APPLE'),
        'Response SHA phrase': getConfig(config, countryUC, 'responseSHA', 'APPLE'),
        'order url': payfort['APPLE_ORDER_URL'] || '',
        order_url: payfort['APPLE_ORDER_URL'] || '',
        merchant_identifier: getConfig(config, countryUC, 'merchantIdentifier', 'APPLE'),
        access_code: getConfig(config, countryUC, 'accessCode', 'APPLE'),
        request_sha_phrase: getConfig(config, countryUC, 'requestSHA', 'APPLE'),
        response_sha_phrase: getConfig(config, countryUC, 'responseSHA', 'APPLE')
      }
    };

    if (store.isPayfortAuthorized != undefined) {
      if (store.isPayfortAuthorized) {
        response.payfortSucessStatus = payfort['PAYFORT_AUTHORIZATION_SUCCESS_STATUS'];
        response.payfortSucessStatusCode = payfort['PAYFORT_AUTHORIZATION_SUCCESS_RESPONSE_CODE'];
      } else {
        response.payfortSucessStatus = payfort['PAYFORT_PURCHASE_SUCCESS_STATUS'];
        response.payfortSucessStatusCode = payfort['PAYFORT_PURCHASE_SUCCESS_RESPONSE_CODE'];
      }
      console.log('payment.controller - configs - payfortSucessStatus: ', response.payfortSucessStatus);
      console.log('payment.controller - configs - payfortSucessStatusCode: ', response.payfortSucessStatusCode);
      console.log('payment.controller - configs - response: ', response);
    }

    return res.status(200).json({ status: true, statusCode: '200', statusMsg: 'Success!', response });
  } catch (e) {
    global.logError(e, 'Payment config api error');
    return res.status(500).json({ status: true, statusCode: '500', statusMsg: 'Something went wrong!' });
  }
};

exports.paymentOptions = async (req, res) => {
  const apiStartTime = new Date();
  const quoteId = req.body?.id;
  const apmTransaction = global.apm ? global.apm.startTransaction('PaymentOptionsTransaction', 'request') : null;
  try {
    const body = req.body;
    logger.info(`### Payment options request ### Quote: ${quoteId} ### Body: ${JSON.stringify(body)}`);
    logger.info(`### Payment options request headers ### Quote: ${quoteId} ### Headers: ${JSON.stringify(req.headers)}`);
    const response = await paymentOptions(req, apmTransaction);

    const apiEndTime = new Date();
    const apiTotalTime = apiEndTime - apiStartTime;
    logger.info(`### checkLatency ### Quote: ${quoteId} ### Total /options API time: ${apiTotalTime}ms`);
    logger.info(`## Payment options response : ${JSON.stringify(response)}`);
    if (apmTransaction) apmTransaction.end();
    res.status(httpStatus.OK);
    return res.json({ status: true, statusMsg: 'success', response });
  } catch (error) {
    const apiEndTime = new Date();
    const apiTotalTime = apiEndTime - apiStartTime;
    logger.info(`### checkLatency ### Quote: ${quoteId} ### Total /options API time (error): ${apiTotalTime}ms`);
    if (apmTransaction) apmTransaction.end();
    res.status(httpStatus.PARTIAL_CONTENT);
    return res.json({ message: error.message });
  }
};

/**
 * Clear the BNPL session from redis
 * @param {*} req
 * @param {*} res
 * @returns
 */
exports.clearSession = async (req, res) => {
  try {
    const body = req.body;
    logger.info(`### Clear session request : ${JSON.stringify(body)}`);
    const response = await clearSession(body);
    res.status(httpStatus.OK);
    return res.json({ status: true, statusMsg: 'success', response });
  } catch (error) {
    res.status(httpStatus.PARTIAL_CONTENT);
    return res.json({ message: error.message });
  }
};

exports.healthCheck = async ({ res }) => {
  let status = true;
  let statusMsg = {};
  if (redisClient) {
    statusMsg.redis = true;
  }
  const statusCode = status ? '200' : '500';
  return res.status(statusCode).json({
    status,
    statusCode,
    statusMsg
  });
};

exports.payFortLogs = async (req, res) => {
  try {
    const { body } = req;
    const { incrementID } = body;
    const current_time = new Date().toISOString();
    let paymentRecords = [];
    const increment_ID = Number(incrementID);

    logger.info(`### PayFortLogs request body: ${JSON.stringify(body)}, current_time: ${current_time}`);

    if (!incrementID) return [];

    const getEntityAndOrderCreationQuery = `
      SELECT entity_id, created_at 
      FROM sales_order
      WHERE increment_id = ${increment_ID}
    `;

    try {
      const [rows] = await mysql.query(getEntityAndOrderCreationQuery);
      paymentRecords = rows[0];
    } catch (error) {
      logger.error(
        error,
        `Error in fetching entity_Id and orderCreation for incrementID: ${incrementID}`,
        `Query: ${getEntityAndOrderCreationQuery}`
      );
    }
    logger.info(
      `### PayfortLogs Payment options logs, EntityId: ${paymentRecords?.entity_id} OrderCreationTime: ${paymentRecords?.created_at} Current_time: ${current_time}`
    );

    const response = {
      entity_id: paymentRecords?.entity_id,
      order_creation_time: paymentRecords?.created_at,
      current_time: current_time
    };

    return res.status(httpStatus.OK).json({ status: true, statusCode: '200', statusMsg: 'Success!', response });
  } catch (e) {
    global.logError(e, 'Error in payfort logs');
    return res.status(500).json({ status: true, statusCode: '500', statusMsg: 'Something went wrong!' });
  }
};
