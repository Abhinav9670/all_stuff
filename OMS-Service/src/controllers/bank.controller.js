const httpStatus = require('http-status');
const catchAsync = require('../utils/catchAsync');
const bankService = require('../services/bank.service');
const { uploadFileToBucket } = require('../config/googleStorage');
const csv = require('csvtojson');

const fetchBankTransfers = catchAsync(async (req, res) => {
  try {
    const data = await bankService.fetchBankTransfers(req.body);

    const payload = {
      status: !!data,
      statusCode: data ? '200' : '201',
      statusMsg: data
        ? 'Bank Transfers fetched successfully!'
        : 'Bank Transfers not found!',
      response: data?.length ? data[0].totalData : [],
      totalCount: data?.length ? data[0].totalCount[0]?.count : 0
    };
    res.status(httpStatus.OK).json(payload);
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});

const createBankTransfer = catchAsync(async (req, res) => {
  try {
    const payload = await bankService.createBankTransfer2(req.body);

    res.status(httpStatus.OK).json(payload);
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});

const getBankTransfersHistory = catchAsync(async (req, res) => {
  try {
    const data = await bankService.getBankTransfersHistory(req.body);

    const payload = {
      status: !!data,
      statusCode: data ? '200' : '201',
      statusMsg: data
        ? 'Bank Transfers History fetched successfully!'
        : 'Bank Transfers History not found!',
      response: data?.length ? data : [],
      totalCount: data?.length || 0
    };
    res.status(httpStatus.OK).json(payload);
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});

const purgeStatusUpdateData = catchAsync(async (req, res) => {
  try {
    const { result } = await bankService.purgeIbans();

    const payload = {
      status: result ? true : false,
      statusCode: result ? '200' : '201',
      statusMsg: result
        ? 'Bank Transfers iban data purged successfully!'
        : 'Issue while purging iban data!',
      response: result
    };
    res.status(httpStatus.OK).json(payload);
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});

const processBankTransfers = catchAsync(async (req, res) => {
  try {
    const { file, email } = req;

    const transferRequests = await csv().fromFile(file?.path);

    const { uploadResp } = await uploadFileToBucket({
      file,
      type: 'bankTransfer',
      email,
      asyncProcessing: true,
      numberOfRecords: transferRequests?.length || 0
    });

    const { insertedId } = uploadResp;

    bankService.processTransfers({ transferRequests, uploadId: insertedId });

    res
      .status(httpStatus.OK)
      .json({ status: true, statusMsg: 'Bank Transfer upload success' });
  } catch (e) {
    global.logError(e);
    res.status(500).json({ error: e.message });
  }
});

module.exports = {
  processBankTransfers,
  fetchBankTransfers,
  createBankTransfer,
  getBankTransfersHistory,
  purgeStatusUpdateData
};
