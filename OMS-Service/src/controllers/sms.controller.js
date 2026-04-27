const catchAsync = require('../utils/catchAsync');
const httpStatus = require('http-status');
const smsService = require('../services/sms.service');

const sendFailedOrderSMS = catchAsync(async (req, res) => {
  try {
    smsService.sendfailedOrderSMS();
    res
      .status(httpStatus.OK)
      .json({ status: '200', statusMsg: 'Request acknowledged!' });
  } catch (e) {
    res.status(500).json({});
  }
});

const SMSList = catchAsync(async (req, res) => {
  try {
    const data = await smsService.fetchSMSList(req.body);

    const payload = {
      status: !!data,
      statusCode: data ? '200' : '201',
      statusMsg: data
        ? 'SMS List fetched successfully!'
        : 'SMS List not found!',
      response: data?.length ? data[0].totalData : [],
      totalCount: data?.length ? data[0].totalCount[0]?.count : 0
    };
    res.status(httpStatus.OK).json(payload);
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});

module.exports = { sendFailedOrderSMS, SMSList };
