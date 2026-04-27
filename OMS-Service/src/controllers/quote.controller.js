const {
  QUOTE_GET_ENDPOINT,
  QUOTE_DELETE_ENDPOINT,
  QUOTE_PAYMENT_ENDPOINT,
  QUOTE_COUPON_ENDPOINT,
  QUOTE_ADDRESS_ENDPOINT
} = require('../constants/quoteEndpoints');
const axios = require('axios');
const catchAsync = require('../utils/catchAsync');
const {
  //   TOKEN: Token,
  CONTENT_TYPE
} = require('../constants');
const { getErrorResponse } = require('../utils/index');
const { getJwtHeaders } = require('../utils');

const getQuote = catchAsync(async (req, res) => {
  try {
    const { body, headers } = req;
    const jwtHeaders = getJwtHeaders(headers);

    const response = await axios.post(QUOTE_GET_ENDPOINT, body, {
      headers: {
        'Content-Type': CONTENT_TYPE,
        ...jwtHeaders
      }
    });
    const { status, data } = response;
    res.status(status).json(data);
  } catch (e) {
    global.logError(e);
    const { statusCode, errorMsg } = getErrorResponse(e);
    res.status(statusCode).json({ error: errorMsg });
  }
});

const removeProduct = catchAsync(async (req, res) => {
  try {
    const { body, headers } = req;
    console.log('in removeProduct', JSON.stringify(body));
    const jwtHeaders = getJwtHeaders(headers);

    const response = await axios.delete(QUOTE_DELETE_ENDPOINT, {
      data: body,
      headers: {
        ...jwtHeaders,
        'Content-Type': CONTENT_TYPE
      }
    });
    const { status, data } = response;
    // console.log('response', response);
    res.status(status).json(data);
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});

const setPayment = catchAsync(async (req, res) => {
  try {
    const { body, headers } = req;
    console.log('in setPayment', JSON.stringify(body));
    const jwtHeaders = getJwtHeaders(headers);

    const response = await axios.post(QUOTE_PAYMENT_ENDPOINT, body, {
      headers: {
        ...jwtHeaders,
        'Content-Type': CONTENT_TYPE
      }
    });
    const { status, data } = response;
    console.log('response', response);
    res.status(status).json(data);
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});

const setQuoteAddress = catchAsync(async (req, res) => {
  try {
    const { body, headers } = req;
    const jwtHeaders = getJwtHeaders(headers);
    console.log('in setQuoteAddress', JSON.stringify(body));

    const response = await axios.post(QUOTE_ADDRESS_ENDPOINT, body, {
      headers: {
        ...jwtHeaders,
        'Content-Type': CONTENT_TYPE
      }
    });
    const { status, data } = response;
    console.log('response', response);
    res.status(status).json(data);
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});

const applyCoupon = catchAsync(async (req, res) => {
  try {
    const { body, headers } = req;
    console.log('in applyCoupon', JSON.stringify(body));
    const jwtHeaders = getJwtHeaders(headers);

    const response = await axios.post(QUOTE_COUPON_ENDPOINT, body, {
      headers: {
        ...jwtHeaders,
        'Content-Type': CONTENT_TYPE
      }
    });
    const { status, data } = response;
    console.log('response', response);
    res.status(status).json(data);
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});

const removeCoupon = catchAsync(async (req, res) => {
  try {
    const { body, headers } = req;
    console.log('in removeCoupon', JSON.stringify(body));
    const jwtHeaders = getJwtHeaders(headers);

    const response = await axios.delete(QUOTE_COUPON_ENDPOINT, {
      data: body,
      headers: {
        ...jwtHeaders,
        'Content-Type': CONTENT_TYPE
      }
    });
    const { status, data } = response;
    console.log('response', response);
    res.status(status).json(data);
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});

module.exports = {
  getQuote,
  removeProduct,
  setPayment,
  applyCoupon,
  removeCoupon,
  setQuoteAddress
};
