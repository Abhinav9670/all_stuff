const axios = require("axios");
const { BNPL_PAYMENT_METHODS } = require("./constants");
const { logError, paymentMethodsFromConfig, getStoreConfig } = require("./utils");
// const { logger } = require("./utils");
const { CUSTOMER_GET_PROFILE_URL, AUTH_INTERNAL_HEADER_BEARER_TOKEN,CUSTOMER_UPDATE_WISHLIST_URL } = process.env;
const internalAuthToken = AUTH_INTERNAL_HEADER_BEARER_TOKEN?.split(",")?.[0];
const cache = require('memory-cache');

const getCustomerInfo = async (customerId) => {
  try {
    const reqBody = { customerId };
    const result = await axios.post(CUSTOMER_GET_PROFILE_URL, reqBody, {
      headers: {
        "Content-Type": "application/json",
        "authorization-token": internalAuthToken,
      },
    });
    return result?.data?.response?.customer;
  } catch (e) {
    logError(e, `Error fetching user info ${customerId}`);
    return;
  }
};

const evaluatePreferredMethod = (previousOrderList, responseObject) => {
  const baseConfig = cache.get('baseConfig');
  const enabledPaymentMethods = paymentMethodsFromConfig(responseObject?.storeId);
  const ignorPrefferedPayments = baseConfig?.ignorPrefferedPayments || [];
  const bnplEligiblepayments = responseObject?.paymentsConfig ? Object.keys(responseObject?.paymentsConfig) : [];
  const orderList = previousOrderList
    ?.filter(
      (order) =>
        !(
          ignorPrefferedPayments?.includes(order.paymentMethod) ||
          "payment_failed" === order.status
        )
    )
    ?.filter((order) => enabledPaymentMethods?.includes(order.paymentMethod));

  
  orderList.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
  const distinctPaymentMethods = [];

  for (const order of orderList) {
    const ifExists = distinctPaymentMethods?.filter(ord => ord.paymentMethod === order.paymentMethod);
      if (ifExists?.length == 0) {
        distinctPaymentMethods.push(order);
      }
  }
  for (const order of distinctPaymentMethods) {
    if (BNPL_PAYMENT_METHODS?.includes(order.paymentMethod)) {
      if (bnplEligiblepayments?.includes(order.paymentMethod)) {
        responseObject.preferredPaymentMethod = order.paymentMethod;
        break;
      }
  } else {
      responseObject.preferredPaymentMethod = order.paymentMethod;
      responseObject.preferredCardbin = order.usedCardBin;
      break;
    }
  }
}
const updateCustomerWishlist = async (reqBody, xHeaderToken, token) => {
  try {
    const headers = {
      'X-Header-Token': xHeaderToken,
      Token: token,
      'Content-Type': 'application/json'
    };
    const result = await axios.post(CUSTOMER_UPDATE_WISHLIST_URL, reqBody, {
      headers: headers,
    });
    // logger.info(`updateCustomerWishlist: URL=${CUSTOMER_UPDATE_WISHLIST_URL}, reqBody=${JSON.stringify(reqBody)}, response=${JSON.stringify(result.data)}`);
    return result?.data?.response;
  } catch (e) {
    logError(e, `Error updating user wishlist ${reqBody?.customerId}`);
    return;
  }
};

const checkShukranEnabled = (customerId, storeId, shukranProfileId, shukranLinkFlag) => {
  try{
    const isShukranEnable = getStoreConfig(storeId, 'isShukranEnable') || false;
    // const baseConfig = cache.get("baseConfig") || {};
    // logger.info(`Shukran check - customerId: ${customerId}, storeId: ${storeId}, shukranProfileId: ${shukranProfileId}, isShukranEnable: ${isShukranEnable}, shukranLinkFlag: ${shukranLinkFlag}, baseConfig: ${JSON.stringify(baseConfig)}`);
    return (customerId && shukranProfileId && isShukranEnable && shukranLinkFlag);
  } catch(e){
    return false;
  }
}

const getCustomerDefaultAddress = async (customerId) => {
  try {
    // logger.info(`defaultaddress: Getting default address for customer: ${customerId}`);
    const reqBody = { customerId };
    const result = await axios.post(CUSTOMER_GET_PROFILE_URL, reqBody, {
      headers: {
        "Content-Type": "application/json",
        "authorization-token": internalAuthToken,
      },
    });
    // logger.info(`defaultaddress: Successfully retrieved default address for customer: ${customerId}`);
    return result?.data?.response?.defaultAddress;
  } catch (e) {
    // logger.error(`defaultaddress: Error fetching user address info for customer ${customerId}: ${e.message}`);
    return;
  }
};

module.exports = {
    getCustomerInfo,
    evaluatePreferredMethod,
    updateCustomerWishlist,
    checkShukranEnabled,
    getCustomerDefaultAddress
};
