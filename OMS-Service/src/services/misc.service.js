const axios = require('axios');
const { MULIN_URL } = process.env;
const { stringifyError } = require('../utils');
exports.sendKaleyraSMS = async ({ msg, phone, smsTemplateId = '' }) => {
  const kalyeraConfig = global?.baseConfig?.smsConfig?.kaleyra;
  const smsPhoneNumber = global?.baseConfig?.smsConfig?.phoneNumber;
  console.log(
    `#### Fraud Customer SMS : ${JSON.stringify(
      msg
    )}, Phone Number : ${smsPhoneNumber}`
  );

  const { key, url, senderId, max_retry_count } = kalyeraConfig;
  const smsURL = url;
  let reqBody = {};
  if (process.env?.REGION?.toUpperCase() === 'IN') {
    if (!smsTemplateId) {
      global.logError('Invalid Template ID');
      return;
    }
    console.log(`Template ID:: ${smsTemplateId} `);
    reqBody = {
      to: smsPhoneNumber || phone,
      sender: senderId,
      source: 'API',
      type: 'TXN',
      body: msg,
      template_id: smsTemplateId
    };
  } else {
    reqBody = {
      params: {
        api_key: key,
        sender: 'STYLI',
        message: msg,
        to: smsPhoneNumber || phone,
        method: 'sms',
        unicode: '1'
      }
    };
  }

  console.log(`### QC Fail SMS kaleyra Request : ${JSON.stringify(reqBody)}`);
 
  let retryCount = 0;
  while (retryCount < max_retry_count) {
    try {
      const result = await getKaleyraResponse(smsURL, reqBody, key);

      if (result === false) {
        global.logInfo('getKaleyraResponse returned false. Retrying...');
        retryCount++;
      } else {
        console.log(
          `### QC Fail SMS kaleyra Response : ${JSON.stringify(result)}`
        );
        return result;
      }
    } catch (e) {
      const stringifiedError = stringifyError(e);
      global.logInfo(
        `QC Fail SMS kaleyra Request Error : ${stringifiedError}`
      );
    }
  }
  return false;
};

exports.getProductsBySKU = async ({ skus = [] }) => {
  const reqBody = { skus, searchVariants: true };
  let response = {};
  try {
    const mulinResponse = await axios.post(
      `${MULIN_URL}/v1/products/productsBySku`,
      reqBody
    );
    response = mulinResponse?.data?.response;
  } catch (e) {
    global.logError(e);
  }
  return response;
};

const getKaleyraResponse = async (smsURL, reqBody, key) => {
  try {
    let kaleyraResponse = {};

    if (process.env?.REGION?.toUpperCase() === 'IN') {
      kaleyraResponse = await axios.post(smsURL, reqBody, {
        headers: { 'api-key': key }
      });
      return kaleyraResponse?.data?.status === 'OK';
    } else {
      kaleyraResponse = await axios.get(smsURL, reqBody);
      return kaleyraResponse?.data?.status === 'OK';
    }
  } catch (e) {
    const stringifiedError = stringifyError(e);
    global.logInfo(
      `QC Fail SMS kaleyra Request Error in getKaleyraResponse function  : ${stringifiedError}`
    );
    return false;
  }
};
