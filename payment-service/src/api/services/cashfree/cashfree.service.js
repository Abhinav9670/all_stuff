const axios = require('axios');
const logger = require('../../../config/logger');
const _ = require('lodash');

/**
 * Place the order for Cashfree
 * @param {*} order
 * @returns
 */
exports.createOrder = async order => {
  try {
    const { cahshfree, payfort } = global?.payfortConfig || {};
    const payload = JSON.stringify({
      order_id: order?.incrementId,
      order_amount: order?.amount,
      order_currency: order?.currency,
      order_note: 'Styli',
      customer_details: {
        customer_id: _.toString(order?.customerId),
        customer_email: order?.customerEmail,
        customer_phone: order?.customerPhone
      },
      order_meta: {
        return_url: `${payfort?.paymentService?.baseUrl}/v1/payment/cashfree/return?order_id={order_id}&order_token={order_token}&incrementId=${order?.incrementId}`,
        notify_url: cahshfree?.NOTIFICATIOIN_URL
      },
      order_tags: {
        orderId: _.toString(order?.orderId),
        storeId: _.toString(order?.storeId)
      }
    });

    const url = `${cahshfree.CF_BASE_URL}/orders`;
    const config = {
      headers: {
        'Content-Type': 'application/json',
        'x-client-id': cahshfree?.CF_APP_ID,
        'x-client-secret': cahshfree?.CF_SECRET,
        'x-api-version': cahshfree?.CF_VERSION
      }
    };
    logger.info(`Cashfree payment Request : ${JSON.stringify(payload)}`);
    const res = await axios.post(url, payload, config);
    const response = res?.data;
    logger.info(`Cashfree payment success. Response : ${JSON.stringify(response)}`);
    return {
      status: true,
      statusCode: '200',
      success: {
        cf_order_id: response?.cf_order_id,
        orderId: order?.orderId,
        incrementId: order?.incrementId,
        grandTotal: order?.amount,
        redirectUrl: response?.payment_link,
        order_token: response?.order_token,
        order_status: response?.order_status
      }
    };
  } catch (error) {
    logger.error(
      `Error in creating Cashfree Order : ${order?.incrementId}. Error: ${error}. Details : ${JSON.stringify(
        error?.response?.data
      )}`
    );
    return {
      error: {
        message: error?.response?.data?.message,
        errorCode: error?.response?.status,
        errorType: error?.response?.data?.type
      }
    };
  }
};

/**
 * Get the Cashfree payment status by Order Id
 * @param {*} orderId
 * @returns
 */
exports.getStatus = async orderId => {
  try {
    const { cahshfree } = global?.payfortConfig || {};
    const url = `${cahshfree.CF_BASE_URL}/orders/${orderId}`;
    const config = {
      headers: {
        'x-client-id': cahshfree?.CF_APP_ID,
        'x-client-secret': cahshfree?.CF_SECRET,
        'x-api-version': cahshfree?.CF_VERSION
      }
    };
    const response = await axios.get(url, config);
    try {
      logger.info(`Successfully fetched Payment Status for Order : ${JSON.stringify(response?.data)}`);
    } catch (error) {
      logger.error(`Successfully fetched Payment Status for Order : ${orderId}`);
    }
    const tags = response?.data?.order_tags;
    return {
      success: {
        status: response?.data?.order_status,
        response_code: response?.status,
        response_message: response?.statusText,
        orderId: Number(tags?.orderId),
        storeId: Number(tags?.storeId)
      }
    };
  } catch (error) {
    logger.error(`Error in getting Payment Status for Order : ${orderId}, Error: ${error}`);
    return { error: error };
  }
};
