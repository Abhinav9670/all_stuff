const { EARN_GET_LEDGER } = require('../constants/easEndpoints');
const axios = require('axios');
const catchAsync = require('../utils/catchAsync');
// const {
//   //   TOKEN: Token,
//   CONTENT_TYPE
// } = require('../constants');
const { getErrorResponse } = require('../utils/index');
// const { getJwtHeaders } = require('../utils');
const { getStoreConfigs } = require('../utils/config');
const { AUTH_INTERNAL_HEADER_BEARER_TOKEN } = process.env;
const internalAuthToken = AUTH_INTERNAL_HEADER_BEARER_TOKEN?.split(',')?.[0];

const getLedger = catchAsync(async (req, res) => {
  try {
    const { body } = req;
    const { customerId } = body;
    const response = await axios.post(EARN_GET_LEDGER, body, {
      headers: {
        Authorization: req.headers?.authorization || '',
        'authorization-token': internalAuthToken
      }
    });
    const { status, data } = response;
    const conversionRates = getStoreConfigs({
      key: ['currencyConversionRate', 'websiteId', 'storeCurrency']
    });
    res.status(status).json({ ...data, customerId, conversionRates });
  } catch (e) {
    global.logError(e);
    const { statusCode, errorMsg } = getErrorResponse(e);
    res.status(statusCode).json({ error: errorMsg });
  }
});

module.exports = {
  getLedger
};
