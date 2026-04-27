const moment = require('moment');
const axios = require('axios');
const { ORDER_SORCE_MAP } = require('../constants/order');
const { isEmpty } = require('lodash');
const { logInfo } = require('../utils');
const { getFirstOrder } = require('./orderOps');

exports.eventForDelivery = async ({ orderData }) => {
  try {
    const {
      entity_id,
      customer_id,
      increment_id,
      customer_email,
      shippingAddress,
      source,
      subSales
    } = orderData;

    if (entity_id && customer_id) {
      const eventObject = {
        identity: customer_email,
        ts: moment().unix(),
        type: 'event',
        evtName: 'order_delivered',
        evtData: {
          order_id: increment_id,
          user_id: customer_id,
          // guest_id: '',
          country: shippingAddress?.country_id,
          device_id: subSales?.device_id || '',
          platform: ORDER_SORCE_MAP?.[source]
        }
      };

      const request = { d: [eventObject] };

      await logCleverTapEvent(request);
    }
  } catch (e) {
    global.logError('Error in cleverTap eventForDelivery', e);
  }
};

const logCleverTapEvent = async request => {
  logInfo('clever tap request ', request);
  try {
    const { eventPushUrl, accountId, passcode } =
      global?.baseConfig?.extrenalApis?.cleverTap || {};

    if (eventPushUrl) {
      const response = await axios.post(eventPushUrl, request, {
        headers: {
          'Content-Type': 'application/json',
          'X-CleverTap-Account-Id': accountId,
          'X-CleverTap-Passcode': passcode
        }
      });
      logInfo('clever tap response ', response);
    }
  } catch (e) {
    global.logError(
      e,
      `Error clevertap req : ${JSON.stringify(request || {})}`
    );
  }
};

const getProfileData = async orderData => {
  const profile = {};
  const {
    customer_id,
    customer_email: customerEmail,
    created_at,
    store_id: storeId
  } = orderData;
  if (customer_id) {
    const firstOrder = await getFirstOrder({ customerEmail, storeId });
    const firstOrderDate = firstOrder?.createdAt
      ? moment(firstOrder?.createdAt).format('DD-MM-YYYY hh:mm:ss')
      : '';
    profile.customerEmail = customerEmail;
    profile.recentOrderDate = moment(created_at).format('DD-MM-YYYY hh:mm:ss');
    profile.firstOrderDate = firstOrderDate;
  }
  return profile;
};

exports.updateCleverTapProfile = async orderData => {
  const profileData = await getProfileData(orderData);
  if (!isEmpty(profileData)) {
    const reqObj = {
      identity: profileData.customerEmail,
      type: 'profile',
      profileData: {}
    };
    for (const profileKey in profileData) {
      if (!isEmpty(profileData[profileKey])) {
        reqObj.profileData[profileKey] = profileData[profileKey];
      }
    }
    await logCleverTapEvent({ d: [reqObj] });
  }
};
