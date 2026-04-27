const logger = require('../../config/logger');
const { createOrder } = require('../services/order.service');
const cashfreeService = require('../services/cashfree/cashfree.service');
const URL = require('url');
const { getCurrencyAndPrecision } = require('../utils');

exports.cfPayment = async (req, res) => {
  const { body, headers } = req;
  const { quoteId } = body || {};
  try {
    logger.info(`Cashfree Payment Begin  headers : ${JSON.stringify(headers)} body : ${JSON.stringify(body)}`);
    const orderDetails = await createOrder(body, headers);
    if (!orderDetails.incrementId) {
      logger.info(`Order Created for Quote : ${quoteId}, Response: ${JSON.stringify(orderDetails)}`);
      const error = orderDetails?.error;
      return res.status(500).send({
        statusCode: orderDetails.statusCode,
        error,
        orderNumber: orderDetails.incrementId,
        orderId: orderDetails.orderId
      });
    }

    const { currency } = getCurrencyAndPrecision(Number(body?.storeId));
    orderDetails['customerId'] = body.customerId;
    orderDetails['customerEmail'] = body.customerEmail;
    orderDetails['customerPhone'] = body.customerPhone;
    orderDetails['currency'] = currency;
    orderDetails['storeId'] = body.storeId;
    const cfOrder = await cashfreeService.createOrder(orderDetails);
    logger.info(`Cashfree response : ${JSON.stringify(cfOrder)}, Quote ID: ${quoteId}`);
    if (cfOrder?.success) res.status(200).send(cfOrder);
    else res.status(400).send({ status: false, statusCode: '400', error: cfOrder?.error });
  } catch (e) {
    logger.error(`Error in cashfree payment. Quote : ${quoteId}. Error : ${e}`);
    res.status(500).send({ status: false, statusCode: '400', error: { paymentError: e.message } });
  }
};

/**
 * Handle Payment return URL
 * @param {*} req
 * @param {*} res
 */
exports.cfReturn = async (req, res) => {
  const payload = req.method === 'GET' ? req.query : req.body;
  logger.info(`Cashfree Paymen return : ${JSON.stringify(payload)}`);
  const { error, success } = await cashfreeService.getStatus(payload?.order_id);
  const { orderId, response_code = '', response_message = '', storeId } = success || error || {};
  const { payfort } = global?.payfortConfig || {};
  const urlFormat = {
    protocol: 'https',
    host: payfort?.paymentService?.websiteHost,
    query: ''
  };
  const { storeUrl } = getCurrencyAndPrecision(storeId);
  if (success && success?.status == 'PAID') {
    const returnUrl = URL.format(
      Object.assign({}, urlFormat, {
        pathname: `/${storeUrl}/en/checkout/payment/confirmation`,
        query: {
          orderId,
          paymentStatus: 'success',
          storeId,
          orderNumber: payload?.order_id
        }
      })
    );
    res.redirect(returnUrl.toString());
  } else {
    const retPathname = `/${storeUrl}/en/checkout/payment/replica`;
    const returnUrl = URL.format(
      Object.assign({}, urlFormat, {
        pathname: retPathname,
        query: {
          orderNumber: payload?.order_id,
          orderId,
          response_code,
          response_message,
          storeId,
          paymentStatus: 'failed'
        }
      })
    );
    res.redirect(returnUrl.toString());
  }
};
