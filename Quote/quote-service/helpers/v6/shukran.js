const axios = require("axios");
const axiosRetry = require('axios-retry').default;
const { client } = require('../../config/redis-config');
const { getAppConfigKey } = require("../../helpers/utils");
// const { logger } = require("../../helpers/utils");
const cache = require("memory-cache");
const { PROXY_HOST } = process.env;
const getShukranProfile = async ({ customerDetails }) => {
  try {

    const baseConfig = cache.get("baseConfig") || {};

    const {retryShukranProfileAPI = 1 } = baseConfig || {};
    
    axiosRetry(axios, {
      retries: retryShukranProfileAPI, // Number of retries
      retryDelay: retryCount => {
        // logger.info(`ShukranProfileTimeOut: Retrying shukran for ${customerDetails?.mobileNumber} - attempt ${retryCount}`);
        return 1000; // Exponential backoff
      },
      retryCondition: error => {
        // Retry only on network errors or 5xx responses
        // logger.error(`ShukranProfileTimeOut: ${customerDetails?.mobileNumber} - error: ${error?.message}, code: ${error?.code}`);
        return error !== null && error?.name === 'AxiosError' && error?.code === 'ERR_BAD_REQUEST' && error?.status == 400;
      },
      onRetry: (retryCount, error, requestConfig) => {
        // Log the retry attempt, URL, and payload before retrying
        // logger.info(`ShukranProfileTimeOut: Retry attempt #${retryCount} for ${customerDetails?.mobileNumber} - URL: ${requestConfig.url}, Payload: ${JSON.stringify(requestConfig.data)}`);
        // You can dynamically update the payload here if needed (e.g., refreshing a token)
      },
      onMaxRetryTimesExceeded: (error, retryCount) => {
        // logger.error(`ShukranProfileTimeOut: Reached maximum retry attempts (${retryCount}) for ${customerDetails?.mobileNumber}: ${error?.message}`);
      }
    });

    //API-3760
    const redisKey = getAppConfigKey('globalRedisKey') || '';
    // logger.info(`REDIS KEY: ${redisKey}`);
    let profileToken = '';
    if(redisKey){
      const redisToken = await client.get(redisKey);
      // logger.info(`REDIS TOKEN FROM EPSILON BUCKET for ${customerDetails?.mobileNumber}: ${!!redisToken}`);
      const token = JSON.parse(redisToken);
      profileToken = token?.access_token || '';
    }
    //API-3760 END
    
    let mobileNumber = customerDetails?.mobileNumber || "";
    if(mobileNumber){
      // Remove '+' and any spaces
      mobileNumber = mobileNumber.replace(/[+ ]/g, '');
    }

    // logger.info(`Shukran profile processing - customer: ${customerDetails?.mobileNumber}, formattedMobile: ${mobileNumber}, profileTokenExists: ${!!profileToken}, customerDetails: ${JSON.stringify(customerDetails)}`);

    const shukranCurrencyCode = customerDetails?.shukranCurrencyCode || "";
    const reqBody = {
        ProfileId: "",
        FieldName: "PhoneNumber",
        LookupValue: mobileNumber,
        Country: shukranCurrencyCode, //API-3760
    };
    //timeout in milliseconds = shukranGatewayTimeoutInSeconds * 1000
    const timeout = (baseConfig?.shukranGatewayTimeoutInSeconds || 3) * 1000;
    // logger.info(`Shukran profile request for ${customerDetails?.mobileNumber} - PROXY_HOST: ${PROXY_HOST}, timeout: ${timeout}ms, reqBody: ${JSON.stringify(reqBody)}`);

    const result = await axios.post(
        `${PROXY_HOST}/api/v1/infrastructure/scripts/GetProfileDetailsWithTier_V2/invoke`,
        reqBody,
        {
            headers: {
            "Content-Type": "application/json",
            Authorization: "OAuth "+profileToken,
            "Accept-Language": "en-US",
            "Program-Code": getAppConfigKey('shukranProgramCode') || 'SHUKRAN',
            "Source-Application": getAppConfigKey('shukranSourceApplication') || 'STYLISHOP.COM',
            },
            timeout,
        }
    );
    // logger.info(`Shukran profile response for ${customerDetails?.mobileNumber}: ${JSON.stringify(result?.data)}`);
    return result?.data;
  } catch (e) {
    // logger.error(`Shukran Profile Response Error for ${customerDetails?.mobileNumber}: ${e.message}`);
    // logError(e, `Error Shulkran profile API user info ${customerId}`);
    const defaultData = {
      "isShukranConnected":false,
      "QualifyingTranxCount": "0",
      "LastEvaluateDate": "",
      "TierExpiryDate": "",
      "ProfileId": "",
      "JoinDate": "",
      "TierCode": "SHUKRAN_DEFAULT_TIER",
      "TierName": "CLASSIC",
      "TierStartDate": "",
      "CardNumber": "",
      "JsonExternalData": {
          "AvailablePoints": 0.0,
          "AvailablePointsCashValue": 0.0,
          "ReqQualTxnForNxtTier": "0",
          "RetTierQualTxn": "0",
          "TierActivity": "",
          "PreviousTierName": "",
          "PreviousTierEndDate": "",
          "TIER_NUDGE_FLAG": "",
        }
    }
    // logger.info(`Shukran profile returning defaultData for ${customerDetails?.mobileNumber}: ${JSON.stringify(defaultData)}`);
    return defaultData;
  }
};
module.exports = { getShukranProfile };
