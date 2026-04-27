const {
  REFUND_STATUS_UPDATE_ENDPINIT,
  REFUND_LIST_ENDPOINT,
  ORDER_CREATE_ENDPOINT,
  ORDER_RECREATE_ENDPOINT,
  SKU_INVENTORY_ENDPOINT,
  CREATE_JWT_V2
} = require('../constants/javaEndpoints');
const catchAsync = require('../utils/catchAsync');
const axios = require('axios');
const { CreditmemoComment } = require('../models/seqModels/index');
const { getStoreConfigs } = require('../utils/config');
const { TOKEN: Token } = require('../constants');
const httpStatus = require('http-status');
const { addAdminLog } = require('../helpers/logging');
const { getJwtHeaders, logInfo } = require('../utils');
const logBrazeCustomEvent = '../utils/brazeApi';
const CONTENT_TYPE_LITERAL = 'application/json';
const { AUTH_INTERNAL_HEADER_BEARER_TOKEN } = process.env;
const internalAuthToken = AUTH_INTERNAL_HEADER_BEARER_TOKEN?.split(',')?.[0];

const creditmemoUpdate = catchAsync(async (req, res) => {
  const { body, email } = req;
  const { creditmemoId, comment } = body;
  let success = false;
  let statusMsg = 'Error adding comment';
  try {
    await CreditmemoComment.create({
      parent_id: creditmemoId,
      comment
    });
    success = true;
    statusMsg = 'Comment added';
  } catch (e) {
    global.logError(e);
  }
  addAdminLog({
    type: 'order',
    data: body,
    email,
    desc: 'Creditmemo comment added'
  });
  res.status(httpStatus.OK).json({
    status: success,
    statusCode: '200',
    statusMsg
  });
});

const createOrder = catchAsync(async (req, res) => {
  try {
    const { body, headers } = req;
    console.log('in createOrder', JSON.stringify(body));
    const jwtHeaders = getJwtHeaders(headers);

    const response = await axios.post(ORDER_CREATE_ENDPOINT, body, {
      headers: {
        ...jwtHeaders,
        'Content-Type': CONTENT_TYPE_LITERAL,
        'authorization-token': internalAuthToken
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

const recreateOrder = catchAsync(async (req, res) => {
  try {
    const { body, email } = req;
    logInfo('recreateOrder body', body);

    const response = await axios.post(ORDER_RECREATE_ENDPOINT, body, {
      headers: {
        'Content-Type': 'application/json',
        Token,
        'x-header-token': email,
        Authorization: req.headers?.authorization || '',
        'authorization-token': internalAuthToken
      }
    });
    const { status, data } = response;

    if (data?.response) {
      let showTax = true;
      const configValue = getStoreConfigs({
        key: 'taxPercentage',
        storeId: data?.response?.storeId
      });
      if (configValue.length) {
        const taxPercentage = configValue[0].taxPercentage;
        if (!taxPercentage || taxPercentage === 0) showTax = false;
      }
      data.response.showTax = showTax;
    }

    logInfo('recreateOrder response', data);
    addAdminLog({
      type: 'order',
      data: body,
      email,
      desc: 'Recreate Order'
    });
    res.status(status).json(data);
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});

const fetchJwt = catchAsync(async (req, res) => {
  try {
    const { body } = req;
    const response = await axios.post(CREATE_JWT_V2, body, {
      headers: {
        'authorization-token': internalAuthToken
      }
    });

    const { data } = response ?? {};
    res.status(200).json({ ...data });
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});

const logCustomEvent = catchAsync(async (req, res) => {
  try {
    const response = logBrazeCustomEvent(req);
    console.log('response', response);
    const { message } = response;
    res.status(message).json(response);
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});

const checkInventory = catchAsync(async (req, res) => {
  try {
    const body = req.body;
    const headers = {
      'Content-Type': CONTENT_TYPE_LITERAL,
      'authorization-token': internalAuthToken
    };
    logInfo('checkInventory headers', headers);
    logInfo('checkInventory url', SKU_INVENTORY_ENDPOINT);
    logInfo('checkInventory body', body);
    const response = await axios.post(SKU_INVENTORY_ENDPOINT, body, {
      headers
    });
    const { status, data } = response;
    logInfo('checkInventory response', data);
    res.status(status).json(data);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

const rtoRefundList = catchAsync(async (req, res) => {
  try {
    const { body } = req;
    if (body.ordersSource === 'archive') body.useArchive = true;
    // let url = ORDER_LIST_ENDPOINT;
    // if (body.ordersSource === 'archive') url = ARCHIVE_ORDER_LIST_ENDPOINT;
    const { filters } = body;
    const appVersionFromClient = filters?.appVersion
      ? filters?.appVersion?.split(',')
      : [];
    body.filters = {
      ...filters,
      appVersion: appVersionFromClient.map(el => {
        return el.trim();
      })
    };
    const response = await axios.post(REFUND_LIST_ENDPOINT, body, {
      headers: {
        Authorization: req.headers?.authorization || ''
      }
    });
    const { status, data } = response;
    res.status(status).json(data);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

const statusUpdate = catchAsync(async (req, res) => {
  try {
    const { body } = req;
    const response = await axios.post(REFUND_STATUS_UPDATE_ENDPINIT, body, {
      headers: {
        Authorization: req.headers?.authorization || '',
        'authorization-token': internalAuthToken
      }
    });
    const { statusCode, status, data, statusMsg } = response;
    if (statusCode == '200') {
      res.status(status).json(data);
    } else {
      res.status(status).json({ error: statusMsg });
    }
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

module.exports = {
  creditmemoUpdate,
  createOrder,
  recreateOrder,
  fetchJwt,
  logCustomEvent,
  checkInventory,
  rtoRefundList,
  statusUpdate
};
