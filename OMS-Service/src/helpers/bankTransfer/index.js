const ObjectId = require('mongodb').ObjectID;
const Handlebars = require('handlebars');
const { updateOne, fetchDocs } = require('../../utils/mongo');
const {
  AmastyStoreCredit,
  AmastyStoreCreditHistory
  // Customer
} = require('../..//models/seqModels/index');
const { getNumericValue } = require('../../utils');
const { STORE_LANG_MAP } = require('../../constants/index');
const { sendKaleyraSMS } = require('../../services/misc.service');
const { sendSgEmail } = require('../../services/email.service');
const { getStoreConfigs } = require('../../utils/config');
const Joi = require('joi');
const { getCustomerInfo } = require('../customer');

exports.tranferSuccess = async ({ requestId, uploadId }) => {
  const oId = new ObjectId(requestId);
  const existingDoc = await fetchDocs({
    collection: 'bank_transfers',
    filters: { _id: oId }
  });
  if (existingDoc?.[0]?.status === 'pending') {
    await updateOne({
      collection: 'bank_transfers',
      filter: { _id: oId },
      update: { status: 'success' }
    });
  }
  await updatePrcessedCount({ uploadId });
};

exports.transferFailure = async ({
  requestId,
  uploadId,
  newStatus,
  amount,
  getCustmerHistoryId
}) => {
  const oId = new ObjectId(requestId);
  const existingDocArr = await fetchDocs({
    collection: 'bank_transfers',
    filters: { _id: oId }
  });
  const existingDoc = existingDocArr?.[0];
  const { status, customerId, storeId } = existingDoc;
  const configValue = getStoreConfigs({
    key: 'currencyConversionRate',
    storeId
  });
  let factor;
  if (configValue.length) {
    factor = configValue[0].currencyConversionRate;
  }
  if (!factor) {
    global.logError(
      'Conversion factor missing while reverting bank transfer request money!'
    );
  }
  if (status === 'pending' && factor) {
    await updateOne({
      collection: 'bank_transfers',
      filter: { _id: oId },
      update: { status: newStatus }
    });
    await revertCreditTransfer({
      customerId,
      amount,
      storeId,
      newStatus,
      getCustmerHistoryId,
      factor
    });
    sendFailureSms({ transactionData: existingDoc, newStatus });
  }
  await updatePrcessedCount({ uploadId });
};

const revertCreditTransfer = async ({
  customerId,
  amount,
  newStatus,
  storeId,
  getCustmerHistoryId,
  factor
}) => {
  const amastyStoreCredit = await AmastyStoreCredit.findOne({
    where: { customer_id: customerId }
  });
  const creditAmount = Number(getNumericValue(amastyStoreCredit.store_credit));
  const returnableAmount = Number(
    getNumericValue(amastyStoreCredit.returnable_amount)
  );
  const convertedRequestedAmount = getNumericValue(amount * factor);

  const newCreditAmt = creditAmount + Number(convertedRequestedAmount);
  const newReturnableAmount =
    returnableAmount + Number(convertedRequestedAmount);

  const newCustomerHistoryId = await getCustmerHistoryId({ customerId });
  await AmastyStoreCreditHistory.create({
    customer_id: customerId,
    customer_history_id: newCustomerHistoryId,
    is_deduct: 0,
    difference: convertedRequestedAmount,
    store_credit_balance: Number(getNumericValue(newCreditAmt)),
    action: 1,
    message: `Bank Transfer ${newStatus}`,
    created_at: new Date(),
    store_id: storeId
  });

  await AmastyStoreCredit.update(
    {
      store_credit: getNumericValue(newCreditAmt),
      returnable_amount: getNumericValue(newReturnableAmount)
    },
    { where: { customer_id: customerId } }
  );
};

const updatePrcessedCount = async ({ uploadId }) => {
  const uploadedFile = await fetchDocs({
    collection: 'uploads',
    filters: { _id: uploadId }
  });

  const currentCount = Number(uploadedFile?.[0]?.processedCount);
  const totalCount = Number(uploadedFile?.[0]?.totalCount);

  const updateObj = { processedCount: currentCount + 1 };
  if (totalCount === currentCount + 1) updateObj.status = 'completed';

  if (currentCount !== undefined) {
    await updateOne({
      collection: 'uploads',
      filter: { _id: uploadId },
      update: updateObj
    });
  }

  if (updateObj.status === 'completed') {
    const uploaderEmail = uploadedFile?.[0]?.email;
    const notifyEmails =
      global?.baseConfig?.emailConfig?.bankTransferNotify?.split(',') || [];
    notifyEmails.push(uploaderEmail);
    const emailMsg = `Bank transfers uploaded by ${uploaderEmail} successfully processed `;
    sendSgEmail({
      to: notifyEmails?.join(','),
      subject: 'Bank Transfer Upload Success',
      html: emailMsg
    });
  }
};

exports.sendSubmitSms = async ({ transactionData }) => {
  const bankTransferConfig =
    global?.baseConfig?.smsConfig?.bankTransfer?.submitSmsTemplate;
  const { storeId, phoneNumber } = transactionData;
  const language = STORE_LANG_MAP[Number(storeId)];
  const smsTemplate = bankTransferConfig?.[language];
  const finalTemplate = Handlebars.compile(smsTemplate);
  const currencyObject = getStoreConfigs({
    key: 'storeCurrency',
    storeId: storeId
  });
  const dataObject = {
    first_name: transactionData.name,
    transfer_amount: transactionData.amount
  };
  if (currencyObject.length) {
    const currency = currencyObject[0].storeCurrency;
    if (currency) dataObject.currency = currency;
  }
  const a = finalTemplate(dataObject);
  sendKaleyraSMS({ msg: a, phone: phoneNumber });
};

const sendFailureSms = async ({ transactionData, newStatus }) => {
  const bankTransferConfig =
    global?.baseConfig?.smsConfig?.bankTransfer?.failSmsTemplate;
  const { storeId } = transactionData;
  const customerInfo = await getCustomerInfo(transactionData?.customerId);
  if (customerInfo) {
    const language = STORE_LANG_MAP[Number(storeId)];
    const smsTemplate = bankTransferConfig?.[newStatus]?.[language];
    const finalTemplate = Handlebars.compile(smsTemplate);
    const a = finalTemplate(transactionData);
    sendKaleyraSMS({ msg: a, phone: customerInfo?.mobileNumber });
  }
};

exports.processError = async ({ uploadId, error }) => {
  try {
    updateOne({
      collection: 'uploads',
      filter: { _id: uploadId },
      update: { status: 'terminated' }
    });
    const uploadedFile = await fetchDocs({
      collection: 'uploads',
      filters: { _id: uploadId }
    });
    const uploaderEmail = uploadedFile?.[0]?.email;
    const notifyEmails =
      global?.baseConfig?.emailConfig?.bankTransferNotify?.split(',') || [];
    notifyEmails.push(uploaderEmail);
    sendSgEmail({
      to: notifyEmails?.join(','),
      subject: 'Bank Transfer Upload Failure',
      html: `Bank transfers uploaded by ${uploaderEmail} Failed .Error : ${error?.message}`
    });
  } catch (e) {
    global.logError(e, {
      msg: `error in processError function bank transfers : ${e.message}`
    });
  }
};

exports.bankTransferReqSchema = Joi.object()
  .keys({
    amount: Joi.number().required().min(1),
    iban: Joi.string().length(24).required(),
    name: Joi.string().required(),
    // email: Joi.string().required(),
    customerId: Joi.required(),
    storeId: Joi.required(),
    bankName: Joi.string().required(),
    swiftCode: Joi.string().required()
    // phoneNumber: Joi.string().required()
  })
  .unknown();
