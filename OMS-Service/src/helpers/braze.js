const { TRACKING_URL } = require('../constants/brazeEndpoints');
const { logBrazeCustomEvent } = require('../utils/brazeApi');
const moment = require('moment');

exports.logBrazeCustomEventForDelivery = async (orderData) => {
  try {
    const { entity_id, customer_id, subSales: {total_shukran_coins_earned = 0, total_shukran_earned_value_in_currency = 0} = {}, increment_id } = orderData.orderData;
    global.logInfo(`Braze Request  Entity for Shukran earn - ${increment_id}, ${total_shukran_coins_earned} earn in curreny ${total_shukran_earned_value_in_currency} ==== ${JSON.stringify(orderData.orderData)}`);
    if (entity_id && customer_id) {
      const request = {};
      const eventArr = [];
      const properties = {
        order_id: entity_id,
        user_id: customer_id,
        total_shukran_coins_earned,
        total_shukran_earned_value_in_currency
      };
      const eventObject = {
        external_id: customer_id,
        name: 'order_delivered',
        time: moment.utc(Date.now()).format('YYYY-MM-DD HH:mm:ss'),
        properties: properties
      };
      eventArr.push(eventObject);
      request.events = eventArr;

      global.logInfo('Braze Request Entity ', entity_id, ' Cust ', customer_id);

      await logBrazeCustomEvent(request);
      global.logInfo('braze event successful ', increment_id);
    }
  } catch (e) {
    global.logError(`braze event error, ${increment_id}, ${e.message? JSON.stringify(e.message):''} ,${e}`);
  }
};

exports.logBrazeCustomEventForShipped = async (orderData) => {
  try {
    const { entity_id, customer_id, increment_id } = orderData.orderData;
    global.logInfo(`Braze Request  Entity for increment id - ${increment_id} === ${JSON.stringify(orderData.orderData)}`);
    if (entity_id && customer_id) {
      const request = {};
      const eventArr = [];
      const properties = {
        order_id: entity_id,
        user_id: customer_id,
        increment_id : increment_id
      };
      const eventObject = {
        external_id: customer_id,
        name: 'order_shipped',
        time: moment.utc(Date.now()).format('YYYY-MM-DD HH:mm:ss'),
        properties: properties
      };
      eventArr.push(eventObject);
      request.events = eventArr;

      global.logInfo('Braze Request Entity ', entity_id, ' Cust ', customer_id);

      await logBrazeCustomEvent(request);
      global.logInfo('braze event successful ', increment_id);
    }
  } catch (e) {
    global.logError(`braze event error, ${increment_id}, ${e.message? JSON.stringify(e.message):''} ,${e}`);
  }
}

exports.logBrazecustomEventForFailedDelivery = async ({ data }) => {
  const { entity_id='', customer_id='', count = 1, orderedItems=[], increment_id='' } = data;

  try {
    if (customer_id && entity_id) {
      const request = {};
      const eventArr = [];
      const properties = {
        order_id: increment_id.split('-')[0],
        user_id: customer_id,
        shipment_id: increment_id,
        product_name: orderedItems[0].name || "",
        delivery_attempt_counter: count || 1,
        order_details_link: TRACKING_URL + increment_id
      }

      const eventObject = {
        external_id: customer_id,
        name: 'delivery_attempt_failed',
        time: moment.utc(Date.now()).format('YYYY-MM-DD HH:mm:ss'),
        properties: properties
      };

      eventArr.push(eventObject);
      request.events = eventArr;

      global.logInfo('Braze Request Entity ', entity_id, ' Cust ', customer_id);

      await logBrazeCustomEvent(request);
      global.logInfo('braze event successful ', increment_id);

    }
  } catch (e) {
    global.logError(`braze event error, ${increment_id}, ${e.message ? JSON.stringify(e.message) : ''} ,${e}`);
  }
}

exports.logBrazecustomEventForFailedPickupAttempt = async ({ data }) => {
  const { entity_id='', customer_id='', count = 1, return_id='', orderedItems=[],increment_id='',orderData={} } = data;

  global.logInfo(`Braze Request Entity (Pickup Attempt Failed) for increment id - ${increment_id} === ${JSON.stringify(orderData)}`);
  try {
    if (customer_id && entity_id) {
      const request = {};
      const eventArr = [];
      const properties = {
        order_id: orderData.order_inc_id || entity_id,
        user_id: customer_id,
        shipment_id: increment_id,
        return_id: orderData.rma_inc_id ||return_id || "",
        product_name: orderedItems[0].name || "",
        return_attempt_counter: count || 1,
        return_details_link: TRACKING_URL + increment_id
      }

      const eventObject = {
        external_id: customer_id,
        name: 'pickup_attempt_failed',
        time: moment.utc(Date.now()).format('YYYY-MM-DD HH:mm:ss'),
        properties: properties
      };

      eventArr.push(eventObject);
      request.events = eventArr;

      global.logInfo('Braze Request Entity ', entity_id, ' Cust ', customer_id);

      await logBrazeCustomEvent(request);
      global.logInfo('braze event successful ', increment_id);

    }
  } catch (e) {
    global.logError(`braze event error, ${increment_id}, ${e.message ? JSON.stringify(e.message) : ''} ,${e}`);
  }
}

exports.logBrazecustomEventForPickupCancel = async ({ data }) => {
  const { entity_id='', increment_id='', customer_id='', orderedItems=[], count = 0,return_id='' } = data;

  // Order_id, shipment_id, return_id, product name, return_details_link, return_attempt_counter

  global.logInfo(`Braze Request Entity (Pickup Canceled) for increment id - ${increment_id}`);
  try {
    if (customer_id && entity_id) {
      const request = {};
      const eventArr = [];
      const properties = {
        order_id: entity_id,
        user_id: customer_id,
        shipment_id: increment_id,
        return_id: return_id || "",
        product_name: orderedItems[0].name || "",
        return_attempt_counter: count || 1,
        return_details_link: TRACKING_URL + return_id
      }

      const eventObject = {
        external_id: customer_id,
        name: 'pickup_cancelled',
        time: moment.utc(Date.now()).format('YYYY-MM-DD HH:mm:ss'),
        properties: properties
      };

      eventArr.push(eventObject);
      request.events = eventArr;

      global.logInfo('Braze Request Entity ', entity_id, ' Cust ', customer_id);

      await logBrazeCustomEvent(request);
      global.logInfo('braze event successful ', increment_id);

    }
  } catch (e) {
    global.logError(`braze event error, ${increment_id}, ${e.message ? JSON.stringify(e.message) : ''} ,${e}`);
  }
}