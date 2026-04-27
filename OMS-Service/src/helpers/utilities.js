const { forEach } = require('lodash');
const axios = require('axios');
const { StatusHistory } = require('../models/seqModels/index');
// const { logInfo } = require('../utils');
const { getWebsiteStoreMap } = require('../utils/config');
const { getArchivedOrders } = require('./orderOpsArchive');
const { setInMatch, setExactMatch } = require('./sequalizeFilters');
const { Order, OrderPayment } = require('../models/seqModels/index');
const crypto = require('crypto');
const { URLSearchParams } = require('url');
const { CUSTOMER_DETAIL_ENDPOINT } = require('../constants/javaEndpoints');
const { AUTH_INTERNAL_HEADER_BEARER_TOKEN } = process.env;
const internalAuthToken = AUTH_INTERNAL_HEADER_BEARER_TOKEN?.split(',')?.[0];

const updateStatusHistory = async (orderId, updateStatusObj) => {
  try {
    const schObject = await StatusHistory.findOne({
      where: { order_id: orderId }
    });
    const id = schObject?.id;
    if (id) {
      global.logInfo('updateStatusHistory id', id);
      const result = await StatusHistory.update(updateStatusObj, {
        where: { id }
      });
      global.logInfo('updateStatusHistory result', result);
    }
    return true;
  } catch (e) {
    global.logError(e);
    global.logInfo('updateStatusHistory error', `Update failed: ${e.message}`);
    return false;
  }
};

const getLifetimeOrders = async ({ customerId, customerEmail, websiteId }) => {
  if (!customerId && !customerEmail)
    return { error: 'Required parameters missing!' };
  try {
    if (customerEmail) {
      try {
        const customerResponse = await axios.post(
          CUSTOMER_DETAIL_ENDPOINT,
          { customerEmail },
          {
            headers: {
              'Content-Type': 'application/json',
              'authorization-token': internalAuthToken
            }
          }
        );
        if (customerResponse?.data?.response?.customer?.customerId) {
          customerId = customerResponse.data.response.customer.customerId;
        }
      } catch (error) {
        console.error('Error fetching customer ID from email:', error.message);
      }
    }
    const websiteStoreMap = getWebsiteStoreMap();
    const storeIds = websiteStoreMap[websiteId] || [];
    let where = {};
    if (storeIds.length) where = setInMatch(where, 'store_id', storeIds);

    if (customerId) {
      where = setExactMatch(where, 'customer_id', customerId);
    } else {
      if (customerEmail) {
        where = setExactMatch(where, 'customer_email', customerEmail);
      }
    }

    const orders = await Order.findAll({
      where,
      include: [{ model: OrderPayment }]
    });
    const archivedOrders = await getArchivedOrders({ where });
    const totalOrders = [...archivedOrders, ...orders];

    const responseList = totalOrders.map(el => {
      const { OrderPayments } = el;
      const sop = OrderPayments?.[0]?.dataValues;
      const paymentMethod = sop?.method?.toLowerCase();
      let usedCardMask = '';
      if (paymentMethod?.includes('md_payfort')) {
        try {
          const paymentInfo = JSON.parse(sop?.additional_information);
          const cardNumber = paymentInfo?.card_number;
          usedCardMask = cardNumber?.slice(-4);
        } catch (error) {
          console.error('Error in parsing card. ', error?.message);
        }
      }
      return {
        paymentMethod: paymentMethod,
        usedCardBin: usedCardMask,
        status: el.status,
        incrementId: el.increment_id,
        createdAt: el.created_at,
        holdOrder: el.retry_payment
      };
    });

    const itemized = {};
    forEach(totalOrders, el => {
      if (itemized[el.status]) itemized[el.status] = ++itemized[el.status];
      else itemized[el.status] = 1;
    });
    return {
      response: { total: totalOrders?.length, itemized },
      responseList
    };
  } catch (e) {
    global.logError(
      `Error fetching customer previous orderList ${JSON.stringify({
        customerEmail,
        customerId
      })}`,
      e
    );
    return { error: e.message };
  }
};

const getCurrentTimestamp = () => {
  const now = new Date();

  const day = String(now.getDate()).padStart(2, "0");
  const month = String(now.getMonth() + 1).padStart(2, "0"); 
  const year = now.getFullYear();

  const hours = String(now.getHours()).padStart(2, "0");
  const minutes = String(now.getMinutes()).padStart(2, "0");
  const seconds = String(now.getSeconds()).padStart(2, "0");

  return `${day}/${month}/${year} ${hours}:${minutes}:${seconds}`;
};

function encryptAWB(waybill, secretKey, salt) {
  try {
    const iterations = 10000;
    const keyLength = 128 / 8; // 16 bytes (128 bits)

    // Step 1: Derive encryption key using PBKDF2
    const key = crypto.pbkdf2Sync(
      secretKey,
      salt,
      iterations,
      keyLength,
      'sha256'
    );

    // Step 2: Encrypt using AES-128-ECB
    const cipher = crypto.createCipheriv('aes-128-ecb', key, null);

    // Note: ECB mode doesn't use IV, so we pass null
    let encrypted = cipher.update(waybill, 'utf8', 'base64');
    encrypted += cipher.final('base64');

    // Step 3: URL encode the Base64 string
    const urlEncoded = encodeURIComponent(encrypted);

    return urlEncoded;
  } catch (error) {
    console.error('Error encrypting AWB:', error);
    return null;
  }
}

function decryptAWB(encryptedAWB, secretKey, salt) {
  try {
    const iterations = 10000;
    const keyLength = 128 / 8; // 16 bytes (128 bits)

    // Step 1: URL decode
    const base64Encoded = decodeURIComponent(encryptedAWB);

    // Step 2: Derive decryption key using PBKDF2
    const key = crypto.pbkdf2Sync(
      secretKey,
      salt,
      iterations,
      keyLength,
      'sha256'
    );

    // Step 3: Decrypt using AES-128-ECB
    const decipher = crypto.createDecipheriv('aes-128-ecb', key, null);

    let decrypted = decipher.update(base64Encoded, 'base64', 'utf8');
    decrypted += decipher.final('utf8');

    return decrypted;
  } catch (error) {
    console.error('Error decrypting AWB:', error);
    return null;
  }
}

function getCountryURL(countryCode) {
  let suffix = 'sa/en';
  switch (countryCode) {
    case 1: {
      suffix = 'sa/en';
      break;
    }
    case 3: {
      suffix = 'sa/ar';
      break;
    }
    case 7: {
      suffix = 'ae/en';
      break;
    }
    case 11: {
      suffix = 'ae/ar';
      break;
    }
    case 12: {
      suffix = 'kw/en';
      break;
    }
    case 13: {
      suffix = 'kw/ar';
      break;
    }
    case 15: {
      suffix = 'qa/en';
      break;
    }
    case 17: {
      suffix = 'qa/ar';
      break;
    }
    case 19: {
      suffix = 'bh/en';
      break;
    }
    case 21: {
      suffix = 'bh/ar';
      break;
    }
    case 23: {
      suffix = 'om/en';
      break;
    }
    case 25: {
      suffix = 'om/ar';
      break;
    }
  }

  return `https://stylishop.com/${suffix}`;
}


exports.encryptAWB = encryptAWB;
exports.decryptAWB = decryptAWB;
exports.updateStatusHistory = updateStatusHistory;
exports.getLifetimeOrders = getLifetimeOrders;
exports.getCurrentTimestamp = getCurrentTimestamp;
exports.getCountryURL = getCountryURL;