const catchAsync = require('../utils/catchAsync');
const { updateRmaActualQty } = require('../helpers/shortPicked');
const { saveShortCheckQcHistory } = require('../helpers/shortCheckQcHistory');
const { callRefundold } = require('../helpers/refund');
const smsObj = require('../helpers/sms');
const { getRmaShipmentDetail, saveShukranPrSuccessfulInDb, getRmaDetail } = require('../helpers/rma');
const { pushOrderToWms, pushRtoOrders } = require('../helpers/wms');
const { uploadFileToBucket } = require('../config/googleStorage');
const { fetchDocs } = require('../utils/mongo');
const { processSMS } = require('../helpers/processSms');
const { getCityData } = require('../helpers/archivedRma');
const { updateShipment } = require('../helpers/forwardShipment');
const { updateShipmentReverse } = require('../helpers/reverseShipment');
const {
  getCreditMemoDocument,
  processCreditmemoEmail
} = require('../services/creditmemo.service');
const { logInfo, sleepInMilliSeconds } = require('../utils');
const {
  pushShipmentUpdatesToKafka1, pushShipmentUpdatesToKafka2
} = require('../kafkaV2/producers/shipment.updates.push');
const { PubSubService } = require('../pubsub/services');
const { updateRevShipment } = require('../helpers/reverseShipment');
const { updateFwdShipment, handleShukranRtoCancelledLogic } = require('../helpers/forwardShipment');
const { getTotalItemCount, findFailedPrOrders, findFailedRTOrders } = require('../helpers/order');
const { updateStatus } = require('../helpers/reverseShipment2');
const { sendSgEmail } = require('../services/email.service');
const { STORE_LANG_MAP } = require('../constants');
const {
  shukranTransactionApi,
  shukranTransactionCreate
} = require('../shukran/action');
const {
  revertFailedOrderShukranTransaction
} = require('../shukran/revertShukranCalls');
const {
  createSellerAsnRecord,
  updateSellerAsnStatus,
  pushToIncreffInwardAPI,
  getOpenAsnRecordsOlderThan,
  getOpenAsnRecordsWithThresholds,
  publishSellerCentralAsnClose,
  updateSplitSellerOrderStatus,
  bulkUpdateAsnStatusToClosed
} = require('../helpers/sellerAsnHelper');
const sellerShipmentService = require('../services/seller-shipment.service');
const { Op, fn, col, where } = require('sequelize');
const { SplitSellerOrder } = require('../models/seqModels/index');
const EMAIL_ERROR_CREDITMEMO = 'Error sending creditmemo email.';
const cancelOrderTemplates = [
  'order_tabby_installment_cancel',
  'order_tabby_paylater_cancel'
];
const {  sequelize, StatusHistory } = require('../models/seqModels/index');
const { SMS_TYPE_IS_RESTRICTED } = require('../constants/errorTypes');

const isSellerOrder = (referenceNumber) => {
  if (typeof referenceNumber !== "string") return false;
  const parts = referenceNumber.split("-");
  return parts.length > 2; // must have at least 3 parts
}


const isSplitOrderPattern = (incrementId) => {
  if (!incrementId || typeof incrementId !== 'string') {
    return false;
  }
  const splitPattern = /^\d+-[LG]-?\d+$/;
  
  return splitPattern.test(incrementId);
};


exports.retryPrCall = catchAsync(async (req, res) => {
  const { incrementId } = req.body;
  try {
    const result = await shukranTransactionCreate({ incrementId });
    return res.status(200).json({
      ...(result?.data ?? {})
    });
  } catch (e) {
    res.status(500).json({ error: e });
  }
});

exports.handleShukranRtoCancelled = catchAsync(async (req, res) => {
  const { incrementId } = req.body;
  
  if (!incrementId) {
    return res.status(400).json({
      status: false,
      errorMsg: 'incrementId is required'
    });
  }
  
  try {
    console.log(`[handleShukranRtoCancelled] Endpoint called with incrementId: ${incrementId}`);
    const result = await handleShukranRtoCancelledLogic(incrementId);
    
    if (result.status) {
      return res.status(200).json({
        status: true,
        message: result.message || 'RTO/Cancelled Shukran logic processed successfully',
        data: result
      });
    } else {
      return res.status(200).json({
        status: false,
        errorMsg: result.errorMsg || 'RTO/Cancelled Shukran logic did not process',
        data: result
      });
    }
  } catch (e) {
    global.logError('Error in handleShukranRtoCancelled endpoint', e);
    res.status(500).json({
      status: false,
      errorMsg: e.message || 'Internal server error',
      error: e
    });
  }
});

exports.shipmentUpdate = catchAsync(async (req, res) => {
  try {
    const { body = {} } = req;
    console.log(`### shipment update data: ${JSON.stringify(body)}`);
    
    const usePubSub = global?.baseConfig?.isPubSubEnabled;
    const useKafka =  global?.baseConfig?.shipmentUpdatesToKafka;
    const incrementId = body?.additional?.latest_status?.reference_number;
    const status = body?.additional?.latest_status?.status;

    if (useKafka || usePubSub) {
      let meta;
      if (body.additional?.is_rvp === false) {
        if (useKafka) {
          meta = await pushShipmentUpdatesToKafka1(body);
          if (incrementId && isSplitOrderPattern(incrementId)) {
          console.log("incrementId is a split order forward Kafka", incrementId);
          await updateShipment(incrementId, status);
          }
        } else {
          console.log(`### shipment update data: pushing to pubsub -shipmentUpdatesToPubSub`);
          meta = await PubSubService.publishMessage(
            process.env.PUB_SUB_SHIPMENT_UPDATE_TOPIC,
            body
          );
          //call updateShipment function to push data to pubsub

          if (incrementId && isSplitOrderPattern(incrementId)) {
            console.log("incrementId is a split order forward PubSub", incrementId);
            await updateShipment(incrementId, status);
            }
        }
        return res.status(200).json({
          status: true,
          statusMsg: 'Shipment update push successfull!',
          meta
        });
      } else {
        if (useKafka) {
          meta = await pushShipmentUpdatesToKafka2(body);
          if (incrementId && isSplitOrderPattern(incrementId)) {
            console.log("incrementId is a split order reverse Kafka", incrementId);
          await updateShipmentReverse(incrementId, status);
          }
        } else {
          console.log(`### shipment update data: pushing to pubsub -returnShipmentUpdatesToPubSub`);
          meta = await PubSubService.publishMessage(
            process.env.PUB_SUB_SHIPMENT_RETURN_UPDATE_TOPIC,
            body
          );
          if (incrementId && isSplitOrderPattern(incrementId)) {
            console.log("incrementId is a split order reverse PubSub", incrementId);
          await updateShipmentReverse(incrementId, status);
          }
        }
        return res.status(200).json({
          status: true,
          statusMsg: 'Shipment update push successfull!',
          meta
        });
      }
    } else {
      const { waybill, additional = {}, rtoAwb, status: reqStatus } = body;
      const {
        latest_status = {},
        notification_event_id: notificationId,
        ndr_status_description,
        is_rvp: isReturn
      } = additional;

      const {
        reference_number: increment_id,
        timestamp,
        remark,
        items,
        status: latestStatus
      } = latest_status;
      let response = {};
      if (isReturn) {
        response = await updateRevShipment({
          rmaIncrementId: increment_id,
          remark,
          notificationId,
          waybill,
          timestamp,
          items,
          status: latestStatus
        });
      } else {
        response = await updateFwdShipment({
          increment_id,
          timestamp,
          notificationId,
          waybill,
          ndr_status_description,
          rtoAwb,
          reqStatus
        });
      }

    if(incrementId && isSplitOrderPattern(incrementId)) {
      console.log("incrementId is a split order", incrementId);
      updateSplitOrderStatus({
        increment_id : increment_id,
      });
    }

      // Assuming smsResponse?.creditmemoEntityId is returned only in case of return shipment update
      this.processCreditMemo({ response, increment_id });

      if (!response.status) {
        return res.status(500).json({
          status: false,
          statusMsg: response.errorMsg
        });
      }

    // Update split_sales_order status to delivered if applicable (default path)
    if (isReturn === false) {
      await updateSplitSalesOrderDeliveredStatus(incrementId, status);
    }

      res.status(200).json({
        status: response.status,
        statusMsg: response.status
          ? 'Shipment Update Successfull'
          : response.errorMsg
      });
    }
  } catch (e) {
    global.logError("error in shipment update", e?.message ? JSON.stringify(e.message) : '', e);
    res.status(500).json({ error: e.message });
  }
});

const updateSplitOrderStatus = async ({ increment_id, order_id }) => {
  try {
    if (!increment_id) {
      return {
        status: false,
        message: `increment_id is required`
      };
    }

    const splitSalesRecords = await sequelize.query(
      `
      SELECT order_id, increment_id 
      FROM split_sales_order 
      WHERE increment_id = :increment_id
    `,
      {
        replacements: { increment_id: increment_id },
        type: sequelize.QueryTypes.SELECT
      }
    );

    if (!splitSalesRecords || splitSalesRecords.length === 0) {
      return {
        status: false,
        message: `No split order records found for increment_id: ${increment_id}`
      };
    }

    splitSalesRecords.map(async record => {
      const statusHistoryData = {
        split_order_id: record.order_id.toString(),
        split_order_increment_id: record.increment_id
      };

      return await StatusHistory.update(statusHistoryData, {
        where: {
          order_id: record.order_id.toString()
        }
      });
    });

    return {
      status: true,
      message: `Successfully processed ${splitSalesRecords.length} split order records for increment_id: ${increment_id}`
    };
  } catch (error) {
    console.error('Error in updateSplitOrderStatus:', error);
    return {
      status: false,
      error: error.message,
      message: `Failed to process records for increment_id: ${increment_id}`
    };
  }
};

const updateSplitSalesOrderDeliveredStatus = async (incrementId = '', status = '') => {
  try {
    const defaultFallBackStatus = {
      status: true,
      updated: false
    };
    // Only proceed if status is 'delivered'
    if (!status || status.toLowerCase() !== 'delivered') {
      console.log(`[#SFP-1062-updateFunc] Status-${status}, skipping update`);
      return {
        ...defaultFallBackStatus,
        message: `Status is not delivered: ${status}, skipping update`
      };
    }

    if (!incrementId) {
      console.log('[#SFP-1062-updateFunc] incrementId is required');
      return {
        ...defaultFallBackStatus,
        message: `incrementId is required`
      };
    }

    // First, check if the order exists in split_seller_order and get its current status
    const splitSellerOrderRecords = await sequelize.query(
      `
    SELECT entity_id, main_order_id, split_order_id, increment_id, status 
    FROM split_seller_order 
    WHERE increment_id = :increment_id`,
      {
        replacements: { increment_id: incrementId },
        type: sequelize.QueryTypes.SELECT
      }
    );

    if (!splitSellerOrderRecords || splitSellerOrderRecords.length === 0) {
      console.log(`[#SFP-1062-updateFunc] No records found: ${incrementId}`);
      return {
        ...defaultFallBackStatus,
        message: `No records found for incrementId: ${incrementId}`
      };
    }

    // Filter records that are outward_ml
    const recordsToUpdate = splitSellerOrderRecords.filter(
      record => record.status && record.status.toLowerCase() === 'outward_ml'
    );

    if (recordsToUpdate.length === 0) {
      console.log(`[#SFP-1062-updateFunc] No records to update: ${incrementId}`);
      return {
        ...defaultFallBackStatus,
        message: 'No records to update',
      };
    }

    console.log(`[#SFP-1062-updateFunc] Updating ${recordsToUpdate.length} `);

    // Update the status to 'delivered' for records that are not already delivered
    const entityIds = recordsToUpdate.map(record => record.entity_id);

    const [updateCount] = await SplitSellerOrder.update(
      { status: 'delivered' },
      {
        where: {
          entity_id: {
            [Op.in]: entityIds
          },
          [Op.and]: where(fn('LOWER', col('status')), { [Op.ne]: 'delivered' })
        }
      }
    );
    console.log(`[#SFP-1062-updateFunc] Successfully updated ${updateCount}`);

    return {
      status: true,
      message: `Successfully updated ${updateCount} record(s) to delivered status`,
      updated: true,
      totalRecords: splitSellerOrderRecords.length
    };
  } catch (error) {
    console.error('[#SFP-1062-updateFunc] Error:', error);
    return {
      ...defaultFallBackStatus,
      error: error.message,
      message: `Failed to update split_seller_order for incrementId: ${incrementId}`
    };
  }
};

const processRefundAndSms = async ({
  updateResponse,
  isFraudPickedUp,
  paymentMethod,
  isPaymentAutoRefunded,
  rmaIncrementId,
  orderId,
  orderedItemCount,
  omsShortCheckV2Enable
}) => {
  updateResponse.isFraudPickedUp = isFraudPickedUp;
  const template = findTemplate(
    paymentMethod,
    updateResponse,
    isPaymentAutoRefunded
  );

  const updatedRmaDetail = await getRmaShipmentDetail({ rmaIncrementId });

  const returnItemCount = updatedRmaDetail?.data?.rmaItems?.reduce(
    (count, item) => {
      if (![12, 13, '12', '13'].includes(item.item_status)) {
        count = count + Number(item.actual_qty_returned);
      }
      return count;
    },
    0
  );

  updateStatus({
    orderId,
    returnItemCount,
    orderedItemCount,
    isShortPicked: true
  });

  omsShortCheckV2Enable ? smsObj.sendSMS({
    smsType: template,
    returnData: updatedRmaDetail.data,
    isReturn: true,
    missingItems: updateResponse?.missingCount,
    returnedItemsResponse: updateResponse
  }) : await smsObj.sendSMS({
    smsType: template,
    returnData: updatedRmaDetail.data,
    isReturn: true,
    missingItems: updateResponse?.missingCount,
    returnedItemsResponse: updateResponse
  });
};

const processShortPickupData = async (rmaIncrementId, omsShortCheckV2Enable) => {
  const rmaDetail = await getRmaShipmentDetail({ rmaIncrementId, omsShortCheckV2Enable });
  const returnData = rmaDetail?.data;
  const { rmaData, rmaItems, address } = returnData;
  const {
    is_short_pickedup: isShortPickedUp,
    is_fraud_pickedup: isFraudPickedUp,
    request_id: requestId,
    order_id: orderId,
    method: paymentMethod
  } = rmaData || {};

  let isPaymentAutoRefunded = true;
  if (address) {
    const cityData = await getCityData(
      address.country ? address.country : address.country_id,
      address.regionId ? address.regionId : address.region_id,
      address.city
    );
    if (cityData) {
      isPaymentAutoRefunded = cityData.is_payment_auto_refunded;
    }
  }

  return {
    rmaItems,
    isShortPickedUp,
    isFraudPickedUp,
    requestId,
    orderId,
    paymentMethod,
    isPaymentAutoRefunded
  };
};

const processShortPickupRefund = async ({
  rmaIncrementId,
  orderId,
  isFraudPickedUp,
  isPaymentAutoRefunded,
  updateResponse,
  paymentMethod, orderedItemCount, omsShortCheckV2Enable
}) => {
  const { shortCheckV2Delay = 5000 } = global.baseConfig || {};
  await sleepInMilliSeconds(shortCheckV2Delay);
  console.log(`shortPickupUpdate timeout over secs: ${shortCheckV2Delay}`);

  const refundResponse = await callRefundold({
    returnIncrementId: rmaIncrementId,
    orderId
  });

  const shouldProcessRefund = refundResponse.status && (refundResponse.sendSms || isFraudPickedUp || !isPaymentAutoRefunded);
  if (shouldProcessRefund) {
    await processRefundAndSms({
      updateResponse,
      isFraudPickedUp,
      paymentMethod,
      isPaymentAutoRefunded,
      rmaIncrementId,
      orderId,
      orderedItemCount,
      omsShortCheckV2Enable
    });
  }
};

const getShouldSkipProcessing = ({ isPaymentAutoRefunded, isShortPickedUp, isFraudPickedUp, rmaIncrementId, forwardOrderCode }) => {
  return isPaymentAutoRefunded && (
    !(isShortPickedUp || isFraudPickedUp) ||
    rmaIncrementId === forwardOrderCode
  );
};

exports.shortPickupUpdate = catchAsync(async (req, res) => {
  try {
    const { apiOptimization = {} } = global.baseConfig || {};
    const omsShortCheckV2Enable = apiOptimization.omsShortCheckV2Enable ?? false;

    const { body = {} } = req;
    const {
      returnOrderCode: rmaIncrementId,
      orderItems: reqItems = [],
      forwardOrderCode,
      waybill
    } = body;

    const {
      rmaItems,
      isShortPickedUp,
      isFraudPickedUp,
      requestId,
      orderId,
      paymentMethod,
      isPaymentAutoRefunded
    } = await processShortPickupData(rmaIncrementId, omsShortCheckV2Enable);

    const shouldSkipProcessing = getShouldSkipProcessing({ isPaymentAutoRefunded, isShortPickedUp, isFraudPickedUp, rmaIncrementId, forwardOrderCode });

    if (shouldSkipProcessing) {
      return res.status(200).json({
        hasError: false,
        errorMessage: ''
      });
    }

    if (!requestId) {
      return res.status(400).json({
        hasError: true,
        errorMessage: 'Return Order not found'
      });
    }

    const updateResponse = await updateRmaActualQty({
      reqItems,
      rmaItems,
      rmaIncrementId,
      requestId,
      orderId,
      isFraudPickedUp,
      isPaymentAutoRefunded
    });

    if (!updateResponse.status) {
      return res.status(500).json({
        hasError: true,
        errorMessage: updateResponse.errorMsg
      });
    }

    if (reqItems.length > 0) {
      await saveShortCheckQcHistory({
        requestId,
        rmaIncrementId,
        reqItems,
        waybill
      });
    }

    const orderedItemCount = await getTotalItemCount({ orderId });

    await processShortPickupRefund({
      rmaIncrementId,
      orderId,
      isFraudPickedUp,
      isPaymentAutoRefunded,
      updateResponse,
      paymentMethod,
      orderedItemCount,
      omsShortCheckV2Enable
    });

    return res.status(200).json({
      hasError: false,
      errorMessage: 'Short pickup updated'
    });
  } catch (e) {
    global.logError(e);
    res.status(500).json({ hasError: true, errorMessage: e.message });
  }
});

const getPaymentMethodTemplate = (paymentMethod, isPaymentAutoRefunded, isFraudPickedUp) => {
  if (isPaymentAutoRefunded && !isFraudPickedUp) {
    return paymentMethod === 'cashondelivery' || paymentMethod === 'free'
      ? 'short_pickup_refund_cod'
      : 'short_pickup_refund_online';
  }
  return null;
};

const getQcFailedTemplate = (qcFailedQty, totalRmaCount, missingCount) => {
  if (qcFailedQty === totalRmaCount || missingCount + qcFailedQty === totalRmaCount) {
    return 'return_all_qc_failed';
  }

  return null;
};

const getPartialTemplate = (qcFailedQty, missingCount, totalRmaCount) => {
  if (qcFailedQty === 0 && missingCount > 0) {
    return 'some_sort_items';
  }
  if (qcFailedQty > 0 && missingCount === 0) {
    return 'return_some_qc_failed';
  }
  const lessThanRmaCount = missingCount + qcFailedQty < totalRmaCount;
  const notZero = missingCount !== 0 && qcFailedQty !== 0;
  if (notZero && lessThanRmaCount) {
    return 'qc_sort_some_refund';
  }
  return null;
};

const findTemplate = (
  paymentMethod,
  updateResponse,
  isPaymentAutoRefunded = true
) => {
  const {
    totalRmaCount,
    qcFailedQty,
    isFraudPickedUp,
    missingCount
  } = updateResponse;

  const paymentTemplate = getPaymentMethodTemplate(paymentMethod, isPaymentAutoRefunded, isFraudPickedUp);
  if (paymentTemplate) return paymentTemplate;

  const qcTemplate = getQcFailedTemplate(qcFailedQty, totalRmaCount, missingCount);
  if (qcTemplate) return qcTemplate;

  const partialTemplate = getPartialTemplate(qcFailedQty, missingCount, totalRmaCount);
  if (partialTemplate) return partialTemplate;

  if (missingCount === 0 && qcFailedQty === 0) {
    return 'completely_verified';
  }
};

const processCreditmemoEmailLogic = async (response, incrementId, template) => {
  const filePath = `./downloads/creditmemo_${response.creditmemoEntityId}.pdf`;
  const document = await getCreditMemoDocument({
    entityId: response.creditmemoEntityId,
    filePath
  });
  if (document.error) logInfo(EMAIL_ERROR_CREDITMEMO);

  processCreditmemoEmail({
    document,
    filePath,
    entityId: response.creditmemoEntityId,
    html: response.sms,
    orderIncrementId: incrementId,
    template,
    orderData: response?.orderData,
    incrementIdData: response?.incrementId
  });
};

const prepareCancelOrderEmailContent = (response, incrementId, storeId) => {
  if (STORE_LANG_MAP[Number(storeId)] == 'ar') {
    return {
      content: `<p dir="rtl">${response?.sms}</p>`,
      subject: `تحديث لطلب الاسترجاع - رقم الطلب ${incrementId}`
    };
  }
  return {
    content: `<p>${response?.sms}</p>`,
    subject: `Refund Update - Order #${incrementId}`
  };
};

const processCancelOrderEmail = async (response, incrementId, template) => {
  if (template == 'order_payment_hold_cancel') {
    return { shouldReturn: true, statusCode: 200, message: `${template} sms sent successfully` };
  }

  const storeId = response.orderData?.store_id || 1;
  const { content, subject } = prepareCancelOrderEmailContent(response, incrementId, storeId);

  const toEmail = response.orderData?.customer_email;
  const { fromEmail, fromName } = global?.baseConfig?.emailConfig || {};

  sendSgEmail({
    to: toEmail,
    from: { email: fromEmail, name: fromName },
    subject,
    html: content
  });

  return { shouldReturn: false };
};

const getNotCreditMemoAndSms = (response, template) => !response.creditmemoEntityId && response.sms && cancelOrderTemplates.includes(template);

exports.orderSms = catchAsync(async (req, res) => {
  try {
    const { body = {} } = req;
    const { incrementId, type, template, codPartialCancelAmount, cpId } = body;

    console.log(`SMS :::: Sending SMS by 3rd party app for ${JSON.stringify({ incrementId, type, template, codPartialCancelAmount, cpId })}`);
    const excludedMessageHeadings = global.baseConfig?.smsConfig?.excludedMessageHeadings || [];
    const SMSExcludedCourierPartners = global.baseConfig?.smsConfig?.SMSExcludedCourierPartners || [];
    if (excludedMessageHeadings.includes(template) ||
      SMSExcludedCourierPartners.includes(Number(cpId))) {
      console.log('SMS :::: Excluded SMS is Encountered ')
      return res
        .status(200)
        .json({ status: true, msg: `${template} ${SMS_TYPE_IS_RESTRICTED}` });
    }

    const response = await processSMS({
      type,
      incrementId,
      template,
      codPartialCancelAmount,
      cpId,
      incrementIdData:incrementId
    });

    if (!response.status) {
      return res.status(500).json({ error: response.errorMsg });
    }
    try {
      const { creditmemoEntityId, sms } = response;
      if (creditmemoEntityId && sms) {
        logInfo('email invoke for creditmemo ', response.creditmemoEntityId);
        await processCreditmemoEmailLogic(response, incrementId, template);
      }
      const notCreditMemoAndSms = getNotCreditMemoAndSms(response, template);
      if (notCreditMemoAndSms) {
        const result = await processCancelOrderEmail(response, incrementId, template);
        if (result.shouldReturn) {
          return res
            .status(result.statusCode)
            .json({ status: true, msg: result.message });
        }
      }
    } catch (e) {
      logInfo(EMAIL_ERROR_CREDITMEMO);
      global.logError(e);
    }

    return res
      .status(200)
      .json({ status: true, msg: `${template} sms sent successfully` });
  } catch (e) {
    global.logError(e.message);
    res.status(500).json({ error: e.message });
  }
});

exports.returnPushToWms = catchAsync(async (req, res) => {
  const { body = {} } = req;
  const { rmaId } = body;

  try {
    const rmaDetailResponse = await getRmaDetail({ requestId: rmaId });
    await pushOrderToWms({ rmaDetail: rmaDetailResponse, isReturnFlow: true });
    return res.status(200).json({
      status: true,
      msg: `RMA Request : ${rmaId} pushed to warehouse`
    });
  } catch (e) {
    const increffError = e?.response?.data?.message;
    const errorDetails = increffError ? `Increff Error : ${increffError}` : e.message;
    const errorMessage = `Return ID ${rmaId} ${errorDetails}`;

    global.logError(errorMessage);
    res.status(500).json({ error: errorMessage });
  }
});

exports.uploadFile = catchAsync(async (req, res) => {
  try {
    const { file, email, body } = req;
    const { type } = body || {};
    if (!type || !file) {
      res.status(400).json({ error: 'type/file missing' });
    }
    await uploadFileToBucket({ file, type, email });
    return res.status(200).json({
      status: true,
      msg: 'File uploaded'
    });
  } catch (e) {
    global.logError(e.message);
    res.status(500).json({ error: e.message });
  }
});

exports.fetchUploads = catchAsync(async (req, res) => {
  try {
    const { type, offset = 0, pagesize } = req.body || {};
    const listData = await fetchDocs({
      collection: 'uploads',
      filters: { type },
      offset,
      sort: { createdAt: -1 },
      pagesize
    });
    return res.status(200).json({
      status: true,
      listData
    });
  } catch (e) {
    global.logError(e.message);
    res.status(500).json({ error: e.message });
  }
});

exports.rtoPushWms = catchAsync(async (req, res) => {
  try {
    await pushRtoOrders();
    return res.status(200).json({ status: true });
  } catch (e) {
    global.logError(e.message, { msg: 'error pushing RTO to WMS' });
    res.status(500).json({ error: e.message });
  }
});

exports.processCreditMemo = async ({ response, increment_id }) => {
  const { smsResponse = {}, smsStatus } = response || {};

  if (smsResponse.creditmemoEntityId && smsResponse.sms) {
    logInfo('email invoke for creditmemo ', smsResponse.creditmemoEntityId);
    try {
      const filePath = `./downloads/creditmemo_${smsResponse.creditmemoEntityId}.pdf`;
      const document = await getCreditMemoDocument({
        entityId: smsResponse.creditmemoEntityId,
        filePath
      });
      if (document.error) logInfo(EMAIL_ERROR_CREDITMEMO);

      processCreditmemoEmail({
        document,
        filePath,
        entityId: smsResponse.creditmemoEntityId,
        html: smsResponse.sms,
        orderIncrementId: smsResponse.orderIncrementId || smsResponse.incrementId,
        template: smsStatus,
        increment_id,
        awbNumber: smsResponse.awbNumber,
        pickupFailedDate: smsResponse.pickupFailedDate.includes("<sup>") ? smsResponse.pickupFailedDate.replace(/<sup>|<\/sup>/g, "") : smsResponse.pickupFailedDate
      });
    } catch (e) {
      logInfo(EMAIL_ERROR_CREDITMEMO);
      global.logError(e);
    }
  }
};

const isValidTransactionNetTotal = (transactionNetTotal) => {
  return transactionNetTotal && transactionNetTotal !== 0 && transactionNetTotal !== '0';
};

exports.shukranTransaction = catchAsync(async (req, res) => {
  const payload = req.body;

  if (!payload?.ProfileId) {
    throw new Error('Missing profile id');
  }
  if (!isValidTransactionNetTotal(payload.TransactionNetTotal)) {
    await saveShukranPrSuccessfulInDb(payload.TransactionNumber);
    throw new Error('Invalid transaction net total');
  }
  try {
    const result = await shukranTransactionApi({
      payload,
    });
    global.logInfo('result data ', result);

    return res.status(200).json({
      status: true,
      statusMsg: 'success',
      response: result?.data
    });
  } catch (e) {
    global.logError(e.message, { msg: 'error in shurkranTransaction' });
    res.status(500).json({ error: e });
  }
});

const processFailedPrOrders = async (failedPrIncrementIds) => {
  const sendEmailFailedIncrementIds = [];
  for (const incrementId of failedPrIncrementIds) {
    try {
      const result = await shukranTransactionCreate({ incrementId });
      if (result?.data?.status === 200) {
        console.log(`Transaction succeeded for Increment ID: ${incrementId}`);
      } else {
        console.log(`Transaction failed for Increment ID: ${incrementId}`);
        sendEmailFailedIncrementIds.push(incrementId);
      }
    } catch (error) {
      console.error(`Error processing Increment ID ${incrementId}:`, error);
      sendEmailFailedIncrementIds.push(incrementId);
    }
  }
  return sendEmailFailedIncrementIds;
};

exports.retryFailedPrOrders = catchAsync(async (req, res) => {
  try {
    const { intervalInHours } = global?.baseConfig?.prCallFailedEmailConfig || {};
    const failedPrIncrementIds = await findFailedPrOrders(intervalInHours);
    const sendEmailFailedIncrementIds = await processFailedPrOrders(failedPrIncrementIds);
    if (sendEmailFailedIncrementIds.length > 0) {
      sendEmailWithFailedPROrders(sendEmailFailedIncrementIds);
    }
    return res.status(200).json({
      message: 'Processing completed'
    });
  } catch (e) {
    console.error('Unexpected error in processing failed PR orders:', e);
    return res
      .status(500)
      .json({ error: e.message || 'Internal Server Error' });
  }
});

async function sendEmailWithFailedPROrders(sendEmailFailedIncrementIds) {
  try {
    if (sendEmailFailedIncrementIds.length === 0) {
      console.log('No failed PR orders to send.');
      return;
    }
    const { prCallFailedEmailConfig = {}, emailConfig = {} } = global?.baseConfig || {};
    const { receiverEmails } = prCallFailedEmailConfig;

    if (!receiverEmails) {
      console.error('No recipient email is configured for failed PR orders');
      return;
    }
    let subject = `Failed PR call Orders`;
    const failedOrdersList = sendEmailFailedIncrementIds.join(', ');
    const content = `
      <p>Failed PR Call Orders:</p>
      <p>${failedOrdersList}</p>
    `;
    const toEmail = receiverEmails;

    const { fromEmail, fromName } = emailConfig;
    if (!fromEmail || !fromName) {
      console.error('Sender email or name is not configured');
      return;
    }
    await sendPRAndRTEmail({ toEmail, fromEmail, fromName, subject, content });
  } catch (error) {
    console.error('Unexpected error in sendEmailWithFailedPROrders:', error);
  }
}

exports.revertFailedOrderPrCalls = catchAsync(async (req, res) => {
  try {
    await revertFailedOrderShukranTransaction(req.body.incrementId);
    return res.status(200).json({
      message: 'Processing completed'
    });
  } catch (err) {
    console.log(err);
    return res
      .status(500)
      .json({ error: err.message || 'Internal Server Error' });
  }
});

exports.findFailedRTOrders = catchAsync(async (req, res) => {
  try {
    const { intervalInHours } = global?.baseConfig?.rtCallFailedEmailConfig || {};
    const failedRTIncrementIds = await findFailedRTOrders(intervalInHours);
    if (failedRTIncrementIds.length > 0) {
      sendEmailWithFailedRTOrders(failedRTIncrementIds);
    }

    return res.status(200).json({
      message: 'Processing completed'
    });
  } catch (e) {
    console.error('Unexpected error in processing failed RT orders:', e);
    return res
      .status(500)
      .json({ error: e.message || 'Internal Server Error' });
  }
});

async function sendEmailWithFailedRTOrders(failedRTIncrementIds) {
  try {
    if (failedRTIncrementIds.length === 0) {
      console.log('No failed RT orders to send.');
      return;
    }
    const { rtCallFailedEmailConfig = {}, emailConfig = {} } = global?.baseConfig || {};
    const { receiverEmails } = rtCallFailedEmailConfig;
    if (!receiverEmails) {
      console.error('No recipient email is configured for failed RT orders');
      return;
    }
    let subject = `Failed RT call Orders`;
    const failedRTOrdersList = failedRTIncrementIds.join(', ');
    const content = `
      <p>Failed RT Call Orders:</p>
      <p>${failedRTOrdersList}</p>
    `;
    const toEmail = receiverEmails;

    const { fromEmail, fromName } = emailConfig;
    if (!fromEmail || !fromName) {
      console.error('Sender email or name is not configured');
      return;
    }
    await sendPRAndRTEmail({ toEmail, fromEmail, fromName, subject, content });
  } catch (error) {
    console.error('Unexpected error in sendEmailWithFailedRTOrders:', error);
  }
}

async function sendPRAndRTEmail({ toEmail, fromEmail, fromName, subject, content }) {
  try {
    await sendSgEmail({
      to: toEmail,
      from: { email: fromEmail, name: fromName },
      subject,
      html: content
    });
    console.log('Email sent successfully');
  } catch (emailError) {
    console.error('Error sending email:', emailError);
  }
}

/**
 * ASN Close CRON - Push open ASN records to Increff and close them.
 * Standard ASNs (seller_id=NULL) use asnTimeThresholdMinutes (default 30min).
 * Seller Central ASNs (seller_id NOT NULL) use SELLER_CENTRAL_ASN_CLOSE_MINUTES (default 60min).
 * For Seller Central ASNs, also publishes close notification to PubSub.
 */
exports.pushToIncreff = catchAsync(async (req, res) => {
  try {
    console.log('### [ASN Close CRON] Starting batch push to Increff');
    
    const consulConfig = global.baseConfig || {};
    const standardThreshold = consulConfig.asnTimeThresholdMinutes || 30;
    const sellerCentralThreshold = consulConfig.SELLER_CENTRAL_ASN_CLOSE_MINUTES || 60;

    console.log(`[ASN Close] Thresholds - Standard: ${standardThreshold}min, Seller Central: ${sellerCentralThreshold}min`);

    const queryResult = await getOpenAsnRecordsWithThresholds(standardThreshold, sellerCentralThreshold);
    
    if (!queryResult.success) {
      return res.status(500).json({ status: false, statusMsg: 'Failed to fetch open ASN records', error: queryResult.message });
    }
    
    const { standardRecords, sellerCentralRecords } = queryResult;
    const allRecords = [...standardRecords, ...sellerCentralRecords];
    
    if (allRecords.length === 0) {
      return res.status(200).json({
        status: true,
        statusMsg: 'No open ASN records found older than threshold',
        data: { processed_count: 0, standard_threshold: standardThreshold, seller_central_threshold: sellerCentralThreshold }
      });
    }
    
    console.log(`[ASN Close] Processing ${standardRecords.length} Standard ASNs and ${sellerCentralRecords.length} Seller Central ASNs`);
    
    const results = [];
    let successCount = 0;
    let failureCount = 0;
    let successAsnIds = [];
    let failedAsnIds = [];
    
    for (const asnRecord of allRecords) {
      try {
        const isSellerCentral = asnRecord.seller_id !== null;
        const asnType = isSellerCentral ? 'Seller Central' : 'Standard';
        
        console.log(`[ASN Close] Processing ${asnType} ASN ID: ${asnRecord.id}, seller_id: ${asnRecord.seller_id || 'NULL'}`);
        
        if (!asnRecord.SellerAsnDetails || asnRecord.SellerAsnDetails.length === 0) {
          console.log(`[ASN Close] Skipping ASN ${asnRecord.id} - no associated details`);
          results.push({ asnId: asnRecord.id, type: asnType, success: false, message: 'No associated seller details found' });
          failureCount++;
          continue;
        }
        
        const increffResult = await pushToIncreffInwardAPI({ asnId: asnRecord.id });
        
        if (increffResult.success) {
          console.log(`[ASN Close] Successfully pushed ${asnType} ASN ${asnRecord.id} to Increff`);
          
          if (isSellerCentral) {
            const pubsubResult = await publishSellerCentralAsnClose(asnRecord);
            console.log(`[ASN Close] Seller Central PubSub for ASN ${asnRecord.id}: ${pubsubResult.success ? 'SUCCESS' : 'FAILED'}`);
          }
          
          successCount++;
          successAsnIds.push(asnRecord.id);
        } else {
          console.error(`[ASN Close] Failed to push ${asnType} ASN ${asnRecord.id} to Increff: ${increffResult.message}`);
          failureCount++;
          failedAsnIds.push(asnRecord.id);
        }
        
        results.push({
          asnId: asnRecord.id,
          type: asnType,
          seller_id: asnRecord.seller_id,
          increment_id: asnRecord.SellerAsnDetails[0]?.increment_id,
          success: increffResult.success,
          message: increffResult.message
        });
        
      } catch (error) {
        console.error(`[ASN Close] Error processing ASN ${asnRecord.id}:`, error.message);
        results.push({ asnId: asnRecord.id, success: false, message: error.message, error: true });
        failureCount++;
      }
    }
    
    if (successAsnIds.length > 0) {
      const bulkUpdateResult = await bulkUpdateAsnStatusToClosed(successAsnIds);
      console.log(`[ASN Close] Bulk status update: ${bulkUpdateResult.success ? 'SUCCESS' : 'FAILED'} for ${successAsnIds.length} ASNs`);
    }
    
    console.log(`[ASN Close] CRON completed - Success: ${successCount}, Failures: ${failureCount}`);
    
    res.status(200).json({
      status: true,
      statusMsg: 'Batch push to Increff completed',
      data: {
        total_processed: allRecords.length,
        standard_asn_count: standardRecords.length,
        seller_central_asn_count: sellerCentralRecords.length,
        success_count: successCount,
        failure_count: failureCount,
        success_asn_ids: successAsnIds,
        failed_asn_ids: failedAsnIds,
        standard_threshold_minutes: standardThreshold,
        seller_central_threshold_minutes: sellerCentralThreshold,
        results
      }
    });

  } catch (e) {
    console.error('[ASN Close] CRON error:', e.message);
    res.status(500).json({ status: false, statusMsg: 'Internal server error during batch processing', error: e.message });
  }
});

exports.sellerShipmentUpdate = catchAsync(async (req, res) => {
  try {
    const { body = {} } = req;
    
    // Use the service to process the seller shipment update
    const result = await sellerShipmentService.processSellerShipmentUpdate(body);
    
    // Return appropriate HTTP status based on result
    if (!result.success) {
      const statusCode = result.errorCode === 'INVALID_ORDER_TYPE' ? 400 : 500;
      return res.status(statusCode).json({
        status: result.status,
        statusMsg: result.statusMsg,
        errorCode: result.errorCode
      });
    }

    res.status(200).json({
      status: result.status,
      statusMsg: result.statusMsg,
      data: result.data
    });
  } catch (e) {
    global.logError("error in seller shipment update", e?.message ? JSON.stringify(e.message) : '', e);
    res.status(500).json({ 
      status: false,
      statusMsg: 'Internal server error',
      error: e.message 
    });
  }
});