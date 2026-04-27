const axios = require('axios');
const { logInfo } = require('../utils');

exports.updateFraudulent = async ({ customerId, email }) => {
  try {
    const endpoint = global?.baseConfig?.extrenalApis?.fraudulentOrderUpdate;

    const body = { customerId, email };
    logInfo(
      `Farudulent api call customerId :  ${customerId} , email: ${email}`
    );
    const response = await axios.post(endpoint, body);
    const { data } = response;
    const { status, statusCode, statusMsg } = data;
    logInfo(
      `Farudulent api call  response customerId :  ${customerId} , email: ${email}`,
      data
    );
    if (!status || ![200, '200'].includes(statusCode)) {
      return {
        status: false,
        errorMsg: `response from Farudulent API: ${statusMsg}`
      };
    }
    return { status: true };
  } catch (e) {
    global.logError(e);
    return {
      status: false,
      errorMsg: `Error from Farudulent API: ${e.message}`
    };
  }
};
