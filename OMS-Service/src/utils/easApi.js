const {
  EAS_ONDELIVERY_SUCCESS,
  EAS_ONRETURN_PICKUP_SUCCESS,
  EARN_IS_RATING_ON_ORDER,
  UPDATE_SHUKRAN_LEDGER
} = require('../constants/easEndpoints');
const orderObj = require('../helpers/order');
const axios = require('axios');
const { logInfo } = require('.');
const { getStoreConfigs } = require('./config');
const { AUTH_INTERNAL_HEADER_BEARER_TOKEN } = process.env;
const internalAuthToken = AUTH_INTERNAL_HEADER_BEARER_TOKEN?.split(',')?.[0];
const EARN_ERROR = 'Earn ERROR';

exports.sendEarnRequestOnDelivery = async req => {
  try {
    if (EAS_ONDELIVERY_SUCCESS) {
      await callEarnAPI(req, EAS_ONDELIVERY_SUCCESS);
    } else {
      console.log('Earn API else part sendEarnRequestOnDelivery');
    }
  } catch (e) {
    global.logError(EARN_ERROR, e);
  }
};

exports.sendEarnRequestOnPickupSuccess = async req => {
  try {
    if (EAS_ONRETURN_PICKUP_SUCCESS) {
      await callEarnAPI(req, EAS_ONRETURN_PICKUP_SUCCESS);
    } else {
      console.log('Earn API else part sendEarnRequestOnPickupSuccess');
    }
  } catch (e) {
    global.logError(EARN_ERROR, e);
  }
};

exports.sendEarnRequestCheckIsRatingOnOrder = async req => {
  try {
    if (EARN_IS_RATING_ON_ORDER) {
      const response = await axios.post(EARN_IS_RATING_ON_ORDER, req, {
        headers: {
          Authorization: req.headers?.authorization || '',
          'authorization-token': internalAuthToken
        }
      });
      const { status, data } = response;
      logInfo(`EarnResponse Status: ${status}`, data);
      if (data?.isRatingEnable) {
        return true;
      }
      return false;
    } else {
      console.log('Earn API else part sendEarnRequestCheckIsRatingOnOrder');
    }
  } catch (e) {
    global.logError(EARN_ERROR, e);
    return false;
  }
};

const callEarnAPI = async (req, reqUrl) => {
  try {
    const response = await axios.post(reqUrl, req, {
      headers: {
        Authorization: req.headers?.authorization || '',
        'authorization-token': internalAuthToken
      }
    });
    const { status, data } = response;
    logInfo(`EarnResponse Status: ${status}`, data);
  } catch (e) {
    global.logError(EARN_ERROR, e);
  }
};

exports.updateShukranLedger = async (increment_id, points, isUnlocked, reason) => {
  try {
    let incrementId = increment_id;
    if (increment_id.includes(global.config.shukranEnrollmentCommonCode)) {
      incrementId = incrementId.replace(
        global.config.shukranEnrollmentCommonCode,
        ''
      );
    }
    let stores = global.config?.environments?.[0]?.stores;
    const data = await orderObj.getOrder({
      incrementId,
      inclSubSales: true
    });
    const storeCurrency = await getStoreConfigs({
      key: 'storeCurrency',
      storeId: data.store_id
    });
    const storeIds = [];
    stores.forEach(s => {
      if (s.storeCurrency === storeCurrency[0].storeCurrency) {
        storeIds.push(s.storeId);
      }
    });
    const storeData = await getStoreConfigs({
      key: 'shukranPointConversion',
      storeId: data.store_id
    });
    const baseStoreData = await getStoreConfigs({
      key: 'shukranPointConversion',
      storeId: 1
    });
    const payload = {
      storeId: storeIds,
      customerId: data.customer_id,
      orderId: data.entity_id,
      orderIncrementId: data.increment_id,
      type: 1,
      typeDetail: isUnlocked ? 3 : 1,
      status: 1,
      reason: reason,
      otherDetail: { otherData: data },
      points: points,
      shukranProfileId:data.subSales.customer_profile_id,
      shukranCardNumber: data.subSales.shukran_card_number,
      cashValueInBaseCurrency:
        baseStoreData && baseStoreData.length > 0
          ? parseFloat(
              (
                parseFloat(points) *
                parseFloat(baseStoreData[0].shukranPointConversion)
              ).toFixed(2)
            )
          : parseFloat((parseFloat(points) * 0.05).toFixed(2)),
      cashValueInCurrency:
        storeData && storeData.length > 0
          ? parseFloat(
              (
                parseFloat(points) *
                parseFloat(storeData[0].shukranPointConversion)
              ).toFixed(2)
            )
          : parseFloat((parseFloat(points) * 0.05).toFixed(2))
    };
    logInfo('Shukran Ledger Payload:', JSON.stringify(payload));
    const response = await axios.post(UPDATE_SHUKRAN_LEDGER, payload, {
      headers: {
        'authorization-token': internalAuthToken
      }
    });
    const { status } = response;
    logInfo(`EarnResponse Status: ${status}`);
  } catch (e) {
    global.logError(EARN_ERROR, e);
  }
};
