const httpStatus = require('http-status');
const ApiError = require('../utils/ApiError');
const mongoUtil = require('../utils/mongoInit');
const { IBAN_COUNTRY_MAP } = require('../constants');
const {
  AmastyStoreCredit,
  AmastyStoreCreditHistory
} = require('../models/seqModels/index');
const moment = require('moment');
// const mongoose = require('mongoose');
const { getNumericValue, promiseAll, errObj } = require('../utils');

const {
  tranferSuccess,
  transferFailure,
  processError,
  sendSubmitSms,
  bankTransferReqSchema
} = require('../helpers/bankTransfer/index');
const { fetchDocs } = require('../utils/mongo');
const { getStoreConfigs } = require('../utils/config');

const fetchBankTransfers = async reqObj => {
  const db = mongoUtil.getDb();

  const { filters, query, pageSize = 10, offset = 0 } = reqObj;
  const { status, fromDate, toDate, name, iban } = filters;
  try {
    let findObject = {};
    if (status) {
      findObject = { ...findObject, status: status };
    }
    if (query) {
      findObject = {
        ...findObject,
        name: { $regex: query, $options: 'i' }
      };
    }
    if (name) findObject = { ...findObject, name };
    if (iban) findObject = { ...findObject, iban };

    if (fromDate)
      findObject = {
        ...findObject,
        created_at: {
          ...(findObject?.['created_at'] || {}),
          $gte: new Date(fromDate)
        }
      };
    if (toDate)
      findObject = {
        ...findObject,
        created_at: {
          ...(findObject?.['created_at'] || {}),
          $lte: new Date(toDate)
        }
      };

    return await db
      .collection('bank_transfers')
      .aggregate([
        { $match: findObject },
        { $sort: { created_at: -1 } },
        {
          $facet: {
            totalData: [{ $skip: offset * pageSize }, { $limit: pageSize }],
            totalCount: [{ $group: { _id: null, count: { $sum: 1 } } }]
          }
        }
      ])
      .toArray();
  } catch (e) {
    global.logError(e);
    console.log([`Exception while fetching bank transfers - ${e.message}`]);
    throw new ApiError(
      httpStatus.INTERNAL_SERVER_ERROR,
      'Bank Transfers read failed'
    );
  }
};

const createBankTransfer2 = async reqObj => {
  const db = mongoUtil.getDb();
  try {
    const bankTransferObj = {
      ...reqObj,
      status: 'pending',
      created_at: new Date(),
      updated_at: new Date()
    };

    const promiseArray = [
      db.collection('bank_transfers').insert(bankTransferObj),
      sendSubmitSms({ transactionData: bankTransferObj })
    ];
    await promiseAll(promiseArray);
    return {
      status: true,
      statusCode: '200',
      statusMsg: 'Bank Transfers created successfully!'
    };
  } catch (e) {
    console.log([`Exception while creating bank transfers - ${e.message}`]);
    throw new ApiError(httpStatus.INTERNAL_SERVER_ERROR, e.message);
  }
};

const createBankTransfer = async reqObj => {
  const db = mongoUtil.getDb();
  const { iban, amount, customerId, storeId } = reqObj;

  const { error } = bankTransferReqSchema.validate(reqObj);
  if (error) return errObj('400', error.message);
  // console.log({ value, error });

  try {
    if (!iban.includes(IBAN_COUNTRY_MAP[storeId]))
      return errObj('201', 'IBAN is invalid!');

    const amastyStoreCredit = await AmastyStoreCredit.findOne({
      where: { customer_id: customerId }
    });
    if (!amastyStoreCredit)
      return errObj('202', 'Invalid customer_id/store credit entry not found!');

    const requestedAmount = Number(getNumericValue(amount));
    const configValue = getStoreConfigs({
      key: 'currencyConversionRate',
      storeId
    });
    let factor;
    if (configValue.length) {
      factor = configValue[0].currencyConversionRate;
    }
    if (!factor) errObj('204', 'Conversion factor missing!');
    const convertedRequestedAmount = getNumericValue(requestedAmount * factor);
    const SCAmount = Number(getNumericValue(amastyStoreCredit.store_credit));
    const SCRAmount = Number(
      getNumericValue(amastyStoreCredit.returnable_amount)
    );

    if (
      SCRAmount < convertedRequestedAmount ||
      SCAmount < convertedRequestedAmount
    )
      return errObj('203', 'Amount cannot be deducted from store credit!');

    const bankTransferObj = {
      ...reqObj,
      status: 'pending',
      created_at: new Date(),
      updated_at: new Date()
    };

    const promiseArray = [
      saveStoreCredit({
        SCAmount,
        SCRAmount,
        requestedAmount: convertedRequestedAmount,
        customerId
      }),
      saveStoreCreditHistory({
        SCAmount,
        requestedAmount: convertedRequestedAmount,
        customerId,
        storeId
      }),
      db.collection('bank_transfers').insert(bankTransferObj),
      sendSubmitSms({ transactionData: bankTransferObj })
    ];
    await promiseAll(promiseArray);
    return {
      status: true,
      statusCode: '200',
      statusMsg: 'Bank Transfers created successfully!'
    };
  } catch (e) {
    console.log([`Exception while creating bank transfers - ${e.message}`]);
    throw new ApiError(httpStatus.INTERNAL_SERVER_ERROR, e.message);
  }
};

const saveStoreCredit = async ({
  SCAmount,
  SCRAmount,
  requestedAmount,
  customerId
}) => {
  await AmastyStoreCredit.update(
    {
      store_credit: getNumericValue(SCAmount - requestedAmount),
      returnable_amount: getNumericValue(SCRAmount - requestedAmount)
    },
    { where: { customer_id: customerId } }
  );
};

const saveStoreCreditHistory = async ({
  SCAmount,
  requestedAmount,
  customerId,
  storeId
}) => {
  const newCustomerHistoryId = await getCustmerHistoryId({ customerId });
  await AmastyStoreCreditHistory.create({
    customer_id: customerId,
    customer_history_id: newCustomerHistoryId,
    is_deduct: 1,
    difference: requestedAmount,
    store_credit_balance: Number(getNumericValue(SCAmount - requestedAmount)),
    action: 2,
    message: 'Bank Transfer Initiated',
    created_at: new Date(),
    store_id: storeId
  });
};

const getCustmerHistoryId = async ({ customerId }) => {
  const statusHistories = await AmastyStoreCreditHistory.findAll({
    where: { customer_id: customerId }
  });
  let newCustomerHistoryId = 1;
  if (statusHistories.length) {
    const lastHistory = statusHistories[statusHistories.length - 1];
    newCustomerHistoryId = lastHistory.customer_history_id + 1;
  }
  return newCustomerHistoryId;
};

const getBankTransfersHistory = async reqObj => {
  try {
    const { pageSize = 10, offset = 0 } = reqObj;
    return await fetchDocs({
      collection: 'uploads',
      filters: { type: 'bankTransfer' },
      offset,
      sort: { createdAt: -1 },
      pagesize: pageSize
    });
  } catch (e) {
    global.logError(e);
    console.log([`Exception while fetching bank transfers - ${e.message}`]);
    throw new ApiError(
      httpStatus.INTERNAL_SERVER_ERROR,
      'Bank Transfers read failed'
    );
  }
};

const purgeIbans = async () => {
  const db = mongoUtil.getDb();
  const offset =
    global.baseConfig?.smsConfig?.bankTransfer?.purgeIbansOffsetInDays || 14;
  const pointDate = moment().subtract(offset, 'days').toDate();
  const filter = { created_at: { $lt: pointDate } };
  // const filter = { _id: mongoose.Types.ObjectId('61c44db7e336b00bd04d0645') };
  return await db
    .collection('bank_transfers')
    .updateMany(filter, { $set: { iban: '', updated_at: new Date() } });
  // return await db.collection('bank_transfers').updateMany(filter, {
  //   $set: { token: 1234 }
  // });
};

const processTransfers = async ({ transferRequests, uploadId }) => {
  try {
    if (transferRequests?.length) {
      for (const index in transferRequests) {
        const row = JSON.parse(JSON.stringify(transferRequests[Number(index)]));
        const { status, _id, amount } = row;
        const lowerStatus = status.toLowerCase();
        if (lowerStatus === 'success') {
          await tranferSuccess({ requestId: _id, uploadId });
        } else if (['failure', 'rejected'].includes(lowerStatus)) {
          await transferFailure({
            requestId: row?._id,
            uploadId,
            newStatus: lowerStatus,
            amount,
            getCustmerHistoryId
          });
        }
      }
    }
  } catch (e) {
    global.logError(e, {
      msg: `error processing bank transfers : ${e.message}`
    });
    processError({ uploadId, error: e });
  }
};

module.exports = {
  fetchBankTransfers,
  createBankTransfer,
  createBankTransfer2,
  getBankTransfersHistory,
  purgeIbans,
  processTransfers
};
