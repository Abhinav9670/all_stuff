const {
  getRmaRequests,
  getRmaDetail,
  removeTracking
} = require('../helpers/rma');
const catchAsync = require('../utils/catchAsync');
const httpStatus = require('http-status');
const axios = require('axios');
const {
  RMA_UPDATE_ENDPOINT,
  RMA_CREATE_ENDPOINT,
  RMA_INIT_ENDPOINT,
  ORDER_DETAIL_ENDPOINT,
  RMA_CREATE_AWB_ENDPOINT
} = require('../constants/javaEndpoints');
const { RmaTracking, sequelize } = require('../models/seqModels/index');
const { QueryTypes } = require('sequelize');
const { uploadFileToBucket, updateHitsWithSignedUrls, generateSignedUrl } = require('../config/googleStorage');
const { validateRecreate } = require('../helpers/rmaRecreate');
const { addAdminLog } = require('../helpers/logging');
const {
  getArchivedRmaRequests,
  getArchivedRmaDetail
} = require('../helpers/archivedRma');
const { AUTH_INTERNAL_HEADER_BEARER_TOKEN } = process.env;
const internalAuthToken = AUTH_INTERNAL_HEADER_BEARER_TOKEN?.split(',')?.[0];

exports.rmaList = catchAsync(async (req, res) => {
  const { offset, pageSize = 500, filters, query, archived } = req.body;
  if (!pageSize || Number.isNaN(+pageSize)) {
    return res
      .status(httpStatus.BAD_REQUEST)
      .json({
        code: 400,
        message:
          'Invalid page size parameter. Page size must be a positive integer.'
      });
  }
  const limit = pageSize && typeof pageSize === 'string' ? parseInt(pageSize) : pageSize;
  try {
    const object = {
      offset,
      limit: limit,
      filters,
      query
    };
    let response = archived
      ? await getArchivedRmaRequests(object)
      : await getRmaRequests(object);
    response.hits = await updateHitsWithSignedUrls(response.hits);
    res.status(httpStatus.OK).json({
      status: true,
      statusCode: '200',
      statusMsg: 'RMA Requests fetched!',
      response: { offset, pageSize: limit, ...response }
    });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});
exports.rmaDetail = catchAsync(async (req, res) => {
  const { requestId, storeId, archived } = req.body;
  try {
    let response = archived
      ? await getArchivedRmaDetail({ requestId, storeId })
      : await getRmaDetail({ requestId, storeId });
    
    // Check if GCS shipping label is enabled from Consul
    const gcsShippingLabelEnabled = global?.baseConfig?.configs?.gcsShippingLabelEnabled || false;
    
    if (gcsShippingLabelEnabled) {
      // New flow: Call Java API to get shipping label from GCS
      try {
        const javaResponse = await axios.get(
          `${RMA_CREATE_AWB_ENDPOINT}/${response?.rma_inc_id}`,
          {
            headers: {
              Authorization: req.headers?.authorization || ''
            }
          }
        );
        // Add shippingLabelUrl from Java response if available
        if (javaResponse?.data?.shippingLabelUrl) {
          response.shipping_label = [javaResponse.data.shippingLabelUrl];
        }else{
          // Fallback to old flow if shippingLabelUrl not present
          const filename = response?.shipping_label?.split('/').pop(); 
          if(filename){
            const signedUrl = await generateSignedUrl(filename); 
            response.shipping_label = signedUrl;
          }
        }
      } catch (error) {
        global.logError('Error fetching shipping label from Java API:', error.message);
        // Fallback to old flow if Java API fails
        const filename = response?.shipping_label?.split('/').pop(); 
        if(filename){
          const signedUrl = await generateSignedUrl(filename); 
          response.shipping_label = signedUrl;
        }
      }
    } else {
      // Old flow: Generate signed URL from GCS bucket
      const filename = response?.shipping_label?.split('/').pop(); 
      if(filename){
        const signedUrl = await generateSignedUrl(filename); 
        response.shipping_label = signedUrl;
      }
    }
    
    return res.status(httpStatus.OK).json({
      status: true,
      statusCode: '200',
      statusMsg: 'RMA Detail fetched!',
      response
    });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});
exports.rmaUpdate = catchAsync(async (req, res) => {
  try {
    const { body, email } = req;
    const response = await axios.post(RMA_UPDATE_ENDPOINT, body, {
      headers: {
        Authorization: req.headers?.authorization || ''
      }
    });
    const { status, data } = response;
    addAdminLog({
      type: 'return',
      data: body,
      email,
      desc: 'RMA updated'
    });
    return res.status(status).json(data);
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});

exports.rmaInit = catchAsync(async (req, res) => {
  try {
    const { body, email } = req;
    const { orderId, reqObj = undefined } = body;
    let request = {};
    if (!reqObj) {
      const response = await axios.post(
        ORDER_DETAIL_ENDPOINT,
        { orderId: orderId, rmaItemQtyProcessed: false },
        {
          headers: {
            'authorization-token': internalAuthToken
          }
        }
      );
      const { status, data } = response;
      if (data?.statusCode !== '200') return res.status(status).json(data);
      const order = data.response;
      request.orderId = orderId;
      request.customerId = order.customerId;
      request.storeId = order.storeId;
      const items = [];
      order.products.forEach(el => pushIfAllowed(el, items));
      request.items = items;
    } else {
      request = reqObj;
    }

    if (!request.items.length) {
      return res.status(201).json({
        status: false,
        statusCode: '201',
        statusMsg: 'There is nothing to return!'
      });
    }
    const response = await axios.post(
      RMA_INIT_ENDPOINT,
      { ...request, omsRequest: true },
      {
        headers: {
          Authorization: req.headers?.authorization || ''
        }
      }
    );
    const { status, data = {} } = response;
    const { statusCode, statusMsg } = data;
    if (statusCode != '200') return res.status(500).json({ error: statusMsg });
    const dropoffAddress =
      global?.javaOrderServiceConfig?.navik?.dropoff_address || {};
    data.response.dropOff = false;
    if (dropoffAddress[request.storeId]) {
      data.response.dropOff = true;
    }
    data.response.storeId = request.storeId;
    addAdminLog({
      type: 'return',
      data: body,
      email,
      desc: 'RMA inititated'
    });
    res.status(status).json(data);
  } catch (e) {
    global.logError(e.message);
    res.status(500).json({ error: e.message });
  }
});

const pushIfAllowed = (el, items) => {
  const availableToReturn =
    parseInt(el.qty) - parseInt(el.qtyReturned) - parseInt(el.qtyCanceled);
  if (
    availableToReturn > 0 &&
    ![true, 'true'].includes(el.returnCategoryRestriction)
  )
    items.push({
      parentOrderItemId: el.parentOrderItemId,
      returnQuantity: availableToReturn,
      reasonId: 17
    });
};

exports.rmaCreate = catchAsync(async (req, res) => {
  try {
    const { body, email } = req;
    const response = await axios.post(
      RMA_CREATE_ENDPOINT,
      { ...body, omsRequest: true },
      {
        headers: {
          Authorization: req.headers?.authorization || ''
        }
      }
    );
    const { status, data } = response;
    addAdminLog({
      type: 'return',
      data: body,
      email,
      desc: 'RMA Create'
    });
    res.status(status).json(data);
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});

exports.rmaRemoveTracking = catchAsync(async (req, res) => {
  const { body, email } = req;
  try {
    removeTracking({ tracking_id: body?.tracking_id });
    const payload = {
      status: true,
      statusCode: '200',
      statusMsg: 'RMA Tracking removed!'
    };
    addAdminLog({
      type: 'return',
      data: body,
      email,
      desc: 'RMA Tracking removed'
    });
    res.status(httpStatus.OK).json(payload);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

exports.rmaCreateTracking = catchAsync(async (req, res) => {
  const { requestId } = req.params;
  try {
    const response = await axios.get(
      `${RMA_CREATE_AWB_ENDPOINT}/${requestId}`,
      {
        headers: {
          Authorization: req.headers?.authorization || ''
        }
      }
    );
    const { status, data } = response;
    res.status(status).json(data);
    addAdminLog({
      type: 'return',
      data: { requestId },
      email: req.email,
      desc: 'RMA Create AWB'
    });
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});

exports.rmaRecreate = catchAsync(async (req, res) => {
  try {
    const { file, email } = req;
    const { incrementIds = '', awbNumbers = '' } = req.body;
    const incIdsArr = incrementIds?.split(',');
    const awbArr = awbNumbers?.split(',');
    const awbIncMap = incIdsArr.reduce((awbMap, incId, index) => {
      awbMap[incId] = awbArr[index];
      return awbMap;
    }, {});
    const processResponse = await validateRecreate({ incIdsArr, awbIncMap });
    if (processResponse?.hasError) {
      return res.status(203).json({
        status: false,
        error: 'Error in uploaded data',
        errorData: processResponse.errorData
      });
    }
    if (!processResponse?.availableRmaIds?.length) {
      return res.status(203).json({
        status: false,
        error: 'None of the RMA ids present in system '
      });
    }

    const baseConfig = global.baseConfig;
    const omsRmaRecreate = baseConfig?.apiOptimization?.omsRmaRecreate ?? false;
    if(omsRmaRecreate){
      const promiseArray = [];
      sequelize.query(
        `UPDATE amasty_rma_request
         SET rma_inc_id = CONCAT(rma_inc_id, '_1')
         WHERE rma_inc_id IN (:incIds)`,
        {
          replacements: { incIds: incIdsArr },
          type: QueryTypes.UPDATE,
        }
      );
      promiseArray.push(
        RmaTracking.destroy({
          where: { tracking_number: awbArr }
        })
      );
      Promise.all(promiseArray).then(async () => {
        await uploadFileToBucket({
          file,
          type: 'rmaRecreate',
          email,
          adminLogType: 'return',
          adminLogDesc: 'RMA recreate upload'
        });
      });
    } else {
      const promiseArray = [];
      const updateQuery = `update amasty_rma_request set rma_inc_id=concat(rma_inc_id, "_1") where rma_inc_id in ("${incIdsArr.join(
        '","'
      )}")`;
      await sequelize.query(updateQuery, {
        type: QueryTypes.UPDATE
      });
      promiseArray.push(
        RmaTracking.destroy({
          where: { tracking_number: awbArr }
        })
      );
      await Promise.all(promiseArray).then(async () => {

        await uploadFileToBucket({
          file,
          type: 'rmaRecreate',
          email,
          adminLogType: 'return',
          adminLogDesc: 'RMA recreate upload'
        });
      });
    }
    return res
      .status(200)
      .json({ status: true, statusMsg: 'Re Create upload success' });
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});