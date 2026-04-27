const {
  ORDER_SHIPMENT_CREATE_ENDPOINT,
  ORDER_SELLER_SHIPMENT_CREATE_ENDPOINT,
  ORDER_AWB_CREATE_ENDPOINT,
  ORDER_SELLER_AWB_CREATE_ENDPOINT,
  ORDER_SELLER_CANCEL_ENDPOINT,
  ORDER_ADDRESS_UPDATE_ENDPOINT,
  ORDER_STATUS_UPDATE_ENDPOINT
} = require('../constants/javaEndpoints');
const catchAsync = require('../utils/catchAsync');
const axios = require('axios');
const httpStatus = require('http-status');
const { addAdminLog } = require('../helpers/logging');
const { updateOrderStatusDetail, validateShipmentSkus } = require('../helpers/order');
const { AUTH_INTERNAL_HEADER_BEARER_TOKEN } = process.env;
const internalAuthToken = AUTH_INTERNAL_HEADER_BEARER_TOKEN?.split(',')?.[0];

const createAwb = catchAsync(async (req, res) => {
  try {
    const { query, email } = req?.body || {};
    const { orderCode, shipmentCode } = query;
    const response = await axios.get(ORDER_AWB_CREATE_ENDPOINT, {
      headers: {
        Authorization: req.headers?.authorization || '',
        'authorization-token': internalAuthToken
      },
      params: { orderCode, shipmentCode }
    });
    console.log({ response });
    const { data } = response ?? {};
    if (data?.hasError) {
      res.status(500).json({ error: data?.errorMessage });
    } else {
      res.status(200).json(data);
    }
    addAdminLog({
      type: 'order',
      data: query,
      email,
      desc: 'Order Create AWB'
    });
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});

const createSellerAwb = catchAsync(async (req, res) => {
  try {
    const { query, email } = req?.body || {};
    const { orderCode, shipmentCode } = query;
    
    console.log({ orderCode, shipmentCode }, "Seller AWB Parameters");
    
    const response = await axios.get(ORDER_SELLER_AWB_CREATE_ENDPOINT, {
      headers: {
        Authorization: req.headers?.authorization || '',
        'authorization-token': internalAuthToken
      },
      params: { orderCode, shipmentCode }
    });
    console.log({ response });
    const { data } = response ?? {};
    if (data?.hasError) {
      res.status(500).json({ error: data?.errorMessage });
    } else {
      res.status(200).json(data);
    }
    addAdminLog({
      type: 'order',
      data: { orderCode, shipmentCode },
      email,
      desc: 'Seller AWB Create'
    });
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});

const cancelSellerOrder = catchAsync(async (req, res) => {

  try {
    const { body, email } = req;
    console.log({ body },"BODY......");
    const response = await axios.put(ORDER_SELLER_CANCEL_ENDPOINT, body, {
      headers: {
        Authorization: req.headers?.authorization || '',
        'authorization-token': internalAuthToken
      }
    });
    console.log({ response });
    const { data } = response ?? {};
    addAdminLog({
      type: 'order',
      data: body,
      email,
      desc: 'Seller Order Cancel'
    });
    return res.status(200).json(data);
  } catch (e) {
    global.logError(e);
    return res.status(500).json({ error: e.message });
  }
});

const createShipment = catchAsync(async (req, res) => {
  try {
    const { body, email } = req;
    const { orderCode, packboxDetailsList } = body;

    // Validate SKUs before creating shipment
    if (orderCode && packboxDetailsList) {
      const validationResult = await validateShipmentSkus(orderCode, packboxDetailsList);
      
      if (!validationResult.isValid) {
        return res.status(400).json({
          status: false,
          statusCode: '400',
          statusMsg: validationResult.error,
          response: null,
          orderId: null,
          error: null,
          shipmentCode: null,
          shipmentItems: [],
          hasError: true,
          errorMessage: ''
        });
      }
    }

    const response = await axios.post(ORDER_SHIPMENT_CREATE_ENDPOINT, body, {
      headers: {
        Authorization: req.headers?.authorization || '',
        'authorization-token': internalAuthToken
      }
    });
    console.log({ response });
    const { data } = response ?? {};
    
    // Log admin action asynchronously to prevent blocking response
    try {
      addAdminLog({
        type: 'order',
        data: body,
        email,
        desc: 'Order Create Shipment'
      });
    } catch (logError) {
      // Log error but don't fail the request
      global.logError('Failed to save admin log:', logError);
    }
    
    return res.status(200).json(data);
  } catch (e) {
    global.logError(e);
    return res.status(500).json({ error: e.message });
  }
});

const createSellerShipment = catchAsync(async (req, res) => {
  try {
    const { body, email } = req;
    console.log({ body },"BODY......");
    const response = await axios.post(ORDER_SELLER_SHIPMENT_CREATE_ENDPOINT, body, {
      headers: {
        Authorization: req.headers?.authorization || '',
        'authorization-token': internalAuthToken
      }
    });
    console.log({ response });
    const { data } = response ?? {};
    console.log({ data },"DATTAAAAA......");
    addAdminLog({
      type: 'order',
      data: body,
      email,
      desc: 'Seller Shipment Create'
    });
    return res.status(200).json(data);
  } catch (e) {
    global.logError(e);
    return res.status(500).json({ error: e.message });
  }
});

const updateOrderStatusDetails = catchAsync(async (req, res) => {
  try {
    const reqObj = req.body?.options;
    const updateObj = {};
    updateObj['statusCode'] = reqObj.status;
    updateObj['label'] = reqObj.label;
    updateObj['colorState'] = reqObj.color_state;
    updateObj['isDefault'] = reqObj.is_default;
    updateObj['visibleOnFront'] = reqObj.visible_on_front;
    updateObj['step'] = reqObj.step;
    const response = await updateOrderStatusDetail(updateObj);
    res.status(httpStatus.OK).json({
      status: true,
      statusCode: '200',
      statusMsg: 'Success',
      response
    });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

const address = catchAsync(async (req, res) => {
  try {
    const { body, email } = req;
    const { beforeData, afterData } = body;
    const response = await axios.put(ORDER_ADDRESS_UPDATE_ENDPOINT, afterData, {
      headers: {
        Authorization: req.headers?.authorization || ''
      }
    });
    const { data } = response ?? {};
    afterData.regionId = afterData.regionId.toString();
    const logData = {
      before: beforeData,
      after: afterData
    };
    addAdminLog({
      type: 'order',
      data: logData,
      email,
      desc: 'Order Address Update'
    });
    res.status(200).json(data);
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});

const status = catchAsync(async (req, res) => {
  try {
    const { body } = req;
    const response = await axios.put(ORDER_STATUS_UPDATE_ENDPOINT, body, {
      headers: {
        Authorization: req.headers?.authorization || ''
      }
    });
    console.log({ response });
    const { data } = response ?? {};
    res.status(200).json(data);
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});

module.exports = {
  createShipment,
  createSellerShipment,
  createAwb,
  createSellerAwb,
  cancelSellerOrder,
  updateOrderStatusDetails,
  address,
  status
};
