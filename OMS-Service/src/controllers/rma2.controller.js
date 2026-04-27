const {
  getRmaStatus,
  getRmaStatusDetails,
  updateRmaStatusDetails,
  getReturnReasons,
  getCancelReasons
} = require('../helpers/rma');
const catchAsync = require('../utils/catchAsync');
const httpStatus = require('http-status');
const { getRmaComments } = require('../helpers/rmaOps');
const { getArchivedRmaComments } = require('../helpers/archivedRma');
const { map } = require('lodash');
const { getKSATime } = require('../helpers/moment');

exports.rmaStatusList = catchAsync(async (req, res) => {
  try {
    const response = await getRmaStatus();
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

exports.rmaStatusDetails = catchAsync(async (req, res) => {
  try {
    const statusId = req?.params?.statusId;
    const response = await getRmaStatusDetails(statusId);
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

exports.updateRMAStatusDetails = catchAsync(async (req, res) => {
  try {
    const reqObj = req?.body?.options || {};
    const updateObj = {};
    updateObj['statusId'] = reqObj.status_id;
    updateObj['isEnabled'] = reqObj.is_enabled;
    updateObj['title'] = reqObj.title;
    updateObj['statusCode'] = reqObj.status_code;
    updateObj['color'] = reqObj.color;
    updateObj['priority'] = reqObj.priority;
    const response = await updateRmaStatusDetails(updateObj);
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

exports.returnReasonList = catchAsync(async (req, res) => {
  try {
    const response = await getReturnReasons();
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

exports.cancelReasonList = catchAsync(async (req, res) => {
  try {
    res.status(httpStatus.OK).json({
      status: true,
      statusCode: '200',
      statusMsg: 'Success',
      response: await getCancelReasons()
    });
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});

exports.comments = catchAsync(async (req, res) => {
  const { rmaIncrementId, archived, orderId } = req.body;
  try {
    const response = archived
      ? await getArchivedRmaComments(rmaIncrementId, orderId)
      : await getRmaComments(rmaIncrementId, orderId);

    map(response || [], el => {
      el.created_at = getKSATime(el.created_at);
    });

    res.status(httpStatus.OK).json({
      status: true,
      statusCode: '200',
      statusMsg: 'Success',
      response: response,
      rmaIncrementId,
      archived
    });
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});
