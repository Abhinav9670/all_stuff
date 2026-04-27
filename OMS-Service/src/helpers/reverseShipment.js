/* eslint-disable max-lines-per-function */
/* eslint-disable max-lines */
const { getRmaShipmentDetail, isShortPickUp } = require('./rma');
const Models = require('../models/seqModels/index');
const sequelize = require('sequelize');
const { QueryTypes } = require('sequelize');
const { getCurrentTimestamp } = require('./utilities');
const { setShortPickedup, updateRmaStatus } = require('./rmaUpdateOps');
const { PubSubService } = require('../pubsub/services');
const {
  PICKED_UP_STATUS_CODE,
  RMA_UNDER_VERIFICATION,
  OUT_FOR_PICKUP_CODE,
  RECEIVED_BY_ADMIN_STATUS_CODE,
  ORDER_DELIVERED_CODE,
  OUT_FOR_PICKUP_STATUS_CODE,
  DROPPED_OFF_STATUS_CODE,
  ORDER_LOST_STATUS_CODE,
  ORDER_LOST,
  ORDER_CANCELLED_STATUS_CODE,
  ORDER_CANCELLED,
  PICKUP_FAILED,
  PICKUP_FAILED_STATUS_CODE,
  ORDER_TRACK_HISTORY,
  CASHONDELIVERY,
  RMA_ITEM_VERIFICATION_PASSED
} = require('../constants/order');

const {
  // ORDER_SHIPPED_CODE,
  OUT_FOR_DELIVERY_SHIPMENT_CODE,
  OUT_FOR_DELIVERY_SHIPMENT_STATUS_CODE,
  OUT_FOR_DELIVERY_SHIPMENT_STATUS_MESSAGE,
  RTO_DELIVERED_SHIPMENT_CODE,
  RTO_DELIVERED_SHIPMENT_STATUS_CODE,
  RTO_DELIVERED_SHIPMENT_STATUS_MESSAGE,
  DELIVERED_SHIPMENT_CODE,
  DELIVERED_SHIPMENT_STATUS_CODE,
  DELIVERED_SHIPMENT_STATUS_MESSAGE,
  REFUNDED_SHIPMENT_CODE,
  REFUNDED_SHIPMENT_STATUS_CODE,
  REFUNDED_SHIPMENT_STATUS_MESSAGE,
  RTO_INITIATED_SHIPMENT_CODE,
  RTO_INITIATED_SHIPMENT_STATUS_CODE,
  RTO_INITIATED_SHIPMENT_STATUS_MESSAGE,
  PICKED_UP_SHIPMENT_CODE,
  PICKED_UP_SHIPMENT_STATUS_CODE,
  PICKED_UP_SHIPMENT_STATUS_MESSAGE,
  RECEIVED_AT_WAREHOUSE_SHIPMENT_CODE,
  RECEIVED_AT_WAREHOUSE_SHIPMENT_STATUS_CODE,
  RECEIVED_AT_WAREHOUSE_SHIPMENT_STATUS_MESSAGE
} = require('../constants/orderStatusCode');

const smsObj = require('./sms');
const {
  updateNormalPickup,
  updateNormalPickupForNoAutoRefund
} = require('./reverseShipment2');
const { isFraudCustomer, setFraudPickedUp } = require('./fraudCustomerRma');
const { logInfo } = require('../utils');
const { getCityData } = require('./archivedRma');
const { RMA_ITEM_VERIFICATION_FAILED } = require('../constants/order');
// Import split order utilities at the top of the file
const { isSplitOrderPattern } = require('../utils/splitOrderUtils');
const {
  RETURN_PICKED_PREPAID,
  RETURN_PICKED_COD,
  RETURN_DELIVERED_QC_PASSED_COD,
  RETURN_DELIVERED_QC_PASSED_PREPAID,
  RETURN_DELIVERED_PARTIAL_QC_PASSED_COD,
  RETURN_DELIVERED_PARTIAL_QC_PASSED_PREPAID,
  RETURN_DELIVERED_QC_FAILED,
  LOST_DAMAGED_FORWARD_COD,
  LOST_DAMAGED_FORWARD_PREPAID
} = require('../constants/smsTemplateConstants');

function getOrderDetailsByStatus(status) {
  switch (status) {
    case OUT_FOR_DELIVERY_SHIPMENT_STATUS_CODE:
      return {
        code: OUT_FOR_DELIVERY_SHIPMENT_CODE,
        message: OUT_FOR_DELIVERY_SHIPMENT_STATUS_MESSAGE
      };

    case DELIVERED_SHIPMENT_STATUS_CODE:
      return {
        code: DELIVERED_SHIPMENT_CODE,
        message: DELIVERED_SHIPMENT_STATUS_MESSAGE
      };

    case RTO_DELIVERED_SHIPMENT_STATUS_CODE:
      return {
        code: RTO_DELIVERED_SHIPMENT_CODE,
        message: RTO_DELIVERED_SHIPMENT_STATUS_MESSAGE
      };

    case RTO_INITIATED_SHIPMENT_STATUS_CODE:
      return {
        code: RTO_INITIATED_SHIPMENT_CODE,
        message: RTO_INITIATED_SHIPMENT_STATUS_MESSAGE
      };

    case REFUNDED_SHIPMENT_STATUS_CODE:
      return {
        code: REFUNDED_SHIPMENT_CODE,
        message: REFUNDED_SHIPMENT_STATUS_MESSAGE
      };

    case PICKED_UP_SHIPMENT_STATUS_CODE:
      return {
        code: PICKED_UP_SHIPMENT_CODE,
        message: PICKED_UP_SHIPMENT_STATUS_MESSAGE
      };

    case RECEIVED_AT_WAREHOUSE_SHIPMENT_STATUS_CODE:
      return {
        code: RECEIVED_AT_WAREHOUSE_SHIPMENT_CODE,
        message: RECEIVED_AT_WAREHOUSE_SHIPMENT_STATUS_MESSAGE
      };

    default:
      return null; // or throw an error if invalid
  }
}

/**
 * Returns user-friendly status message for amasty_rma_request_status_history.status_msg.
 * Maps status code (e.g. picked_up, pickedup) to display message.
 * @param {string} status - Raw status from webhook (e.g. picked_up, pickedup, delivered)
 * @returns {string|null} - Message for status_msg column or null
 */
function getRmaStatusHistoryMessage(status) {
  if (!status || typeof status !== 'string') return null;
  const normalized = String(status).trim().toLowerCase().replace(/\s+/g, '_');
  const statusMessages = {
    picked_up: 'Your return item has been picked up',
    pickedup: 'Your return item has been picked up',
    out_for_delivery: 'Your return item is out for delivery',
    delivered: 'Your return item has been delivered',
    rto_delivered: 'Your return has been delivered to the warehouse (RTO)',
    rto_initiated: 'Return to origin has been initiated',
    refunded: 'Your refund has been processed',
    received_at_warehouse:
      'Your return item has been received at our warehouse',
    received_admin: 'Your return item has been received at our warehouse',
    out_for_pickup: 'Your return item is out for pickup',
    pickup_failed: 'Pickup attempt failed for your return',
    dropped_off: 'Your return item has been dropped off',
    order_lost: 'Your return has been marked as lost',
    order_cancelled: 'Your return has been cancelled',
    shipped: 'Your return shipment has been shipped'
  };
  return statusMessages[normalized] || null;
}

const receivedAtWarehouse = async updateParams => {
  const { rmaStatus, rmaItems } = updateParams;

  const actualQtyNotUpdated = rmaItems.find(item =>
    [null, undefined].includes(item.actual_qty_returned)
  );

  if (actualQtyNotUpdated && rmaStatus === RMA_UNDER_VERIFICATION) {
    return { status: false, errorMsg: 'Short pickup not updated' };
  }
  await updateRmaStatus({ ...updateParams });
};

const handleShortPickup = async ({
  rmaId,
  rmaItems,
  rmaStatusData,
  return_type,
  timestamp,
  orderId
}) => {
  const rmaVerificationStatusId = rmaStatusData.find(
    status => status.status_code === RMA_UNDER_VERIFICATION
  )?.status_id;
  return await setShortPickedup({
    rmaItems,
    rmaId,
    rmaVerificationStatusId,
    returnType: return_type,
    rmaStatusData,
    timestamp,
    orderId
  });
};

const handleFraudPickup = async ({
  rmaId,
  rmaItems,
  rmaStatusData,
  return_type,
  timestamp,
  orderId
}) => {
  const rmaVerificationStatusId = rmaStatusData.find(
    st => st.status_code === RMA_UNDER_VERIFICATION
  )?.status_id;
  return await setFraudPickedUp({
    rmaItems,
    rmaId,
    rmaVerificationStatusId,
    returnType: return_type,
    rmaStatusData,
    timestamp,
    orderId
  });
};

const handleNormalPickupWithAutoRefund = async ({
  statusTitle,
  rmaStatusId,
  orderId,
  rma_inc_id,
  paymentMethod,
  rmaPaymentMethod,
  smsStatus,
  rmaItems,
  rmaId,
  rmaStatusData,
  return_type,
  timestamp,
  returnedItems
}) => {
  return await updateNormalPickup({
    statusTitle,
    rmaStatusId,
    orderId,
    rma_inc_id,
    paymentMethod,
    rmaPaymentMethod,
    smsStatus,
    rmaItems,
    rmaId,
    status: PICKED_UP_STATUS_CODE,
    rmaStatusData,
    returnType: return_type,
    timestamp,
    returnedItems
  });
};

const handleNormalPickupWithoutAutoRefund = async ({
  rmaId,
  rmaItems,
  rmaStatusData,
  return_type,
  timestamp,
  orderId,
  isPaymentAutoRefunded
}) => {
  const rmaVerificationStatusId = rmaStatusData.find(
    st => st.status_code === RMA_UNDER_VERIFICATION
  )?.status_id;
  return await updateNormalPickupForNoAutoRefund({
    rmaItems,
    rmaId,
    rmaVerificationStatusId,
    returnType: return_type,
    rmaStatusData,
    timestamp,
    orderId,
    isPaymentAutoRefunded
  });
};

const updatePickup = async ({
  rmaId,
  rmaStatusData,
  return_type,
  smsStatus,
  remark,
  statusTitle,
  rmaStatusId,
  orderId,
  rma_inc_id,
  paymentMethod,
  rmaPaymentMethod,
  rmaItems,
  timestamp,
  returnedItems,
  address,
  isSplitOrder
}) => {
  console.log('updatePickup processing:', {
    rmaId,
    orderId,
    isSplitOrder,
    returnType: return_type
  });

  let paymentRefundUrl = '';
  let autoRefundResponse = {};

  if (isShortPickUp(remark)) {
    console.log(`Fraud RMA::::${orderId}-isShortPickUp-${remark}`);
    smsStatus = 'short_pickup';
    const { status, msg } = await handleShortPickup({
      rmaId,
      rmaItems,
      rmaStatusData,
      return_type,
      timestamp,
      orderId
    });
    if (!status) {
      return { status: false, errorMsg: msg };
    }
  } else if (await isFraudCustomer(orderId)) {
    console.log(`Fraud RMA::::${orderId}-isFraudCustomer`);
    smsStatus = 'fraud_pickedup';
    const { status, msg } = await handleFraudPickup({
      rmaId,
      rmaItems,
      rmaStatusData,
      return_type,
      timestamp,
      orderId
    });
    if (!status) {
      return { status: false, errorMsg: msg };
    }
  } else {
    console.log(
      `Fraud RMA::::${orderId}-updateNormalPickup-${rmaId}-${statusTitle}`
    );
    let isPaymentAutoRefunded = true;

    let cityData;
    if (address) {
      cityData = await getCityData(
        address.country ? address.country : address.country_id,
        address.regionId ? address.regionId : address.region_id,
        address.city
      );
    }
    if (cityData) {
      isPaymentAutoRefunded = cityData.is_payment_auto_refunded;
    }

    if (isPaymentAutoRefunded) {
      const {
        status,
        errorMsg: msg,
        smsStatus: sms,
        refundUrl,
        autoRefundResponse: autoRefund
      } = await handleNormalPickupWithAutoRefund({
        statusTitle,
        rmaStatusId,
        orderId,
        rma_inc_id,
        paymentMethod,
        rmaPaymentMethod,
        smsStatus,
        rmaItems,
        rmaId,
        rmaStatusData,
        return_type,
        timestamp,
        returnedItems
      });
      if (!status) {
        return { status: false, errorMsg: msg };
      }
      smsStatus = sms;
      paymentRefundUrl = refundUrl;
      autoRefundResponse = autoRefund;
    } else {
      const { status, msg } = await handleNormalPickupWithoutAutoRefund({
        rmaId,
        rmaItems,
        rmaStatusData,
        return_type,
        timestamp,
        orderId,
        isPaymentAutoRefunded
      });
      if (!status) {
        return { status: false, errorMsg: msg };
      }
    }
  }
  return {
    status: true,
    errorMsg: '',
    smsStatus,
    paymentRefundUrl,
    autoRefundResponse
  };
};

const handleSwitchCase = async params => {
  const { rmaData, remark, notificationId, address, isSplitOrder, waybill } =
    params;
  const {
    status_code: status,
    request_id: rmaId,
    return_type,
    order_status: currentOrderStatus,
    order_id: orderId
  } = rmaData;
  let smsStatus = '';
  let paymentRefundUrl = '';
  let autoRefundResponse = '';

  console.log('handleSwitchCase processing:', {
    notificationId,
    rmaId,
    orderId,
    isSplitOrder,
    status
  });

  switch (notificationId) {
    case 2:
      smsStatus =
        rmaData.method === CASHONDELIVERY
          ? RETURN_PICKED_COD
          : RETURN_PICKED_PREPAID;
    case 3:
      if (![PICKED_UP_STATUS_CODE, DROPPED_OFF_STATUS_CODE].includes(status)) {
        const updateResp = await updatePickup({
          rmaId,
          rmaStatusData: params?.rmaStatusData,
          return_type,
          smsStatus,
          remark,
          statusTitle: rmaData.status_title,
          rmaStatusId: rmaData.status_id,
          orderId,
          rma_inc_id: rmaData.rma_inc_id,
          paymentMethod: rmaData.method,
          rmaPaymentMethod: rmaData.rma_payment_method,
          rmaItems: params.rmaItems,
          timestamp: params.timestamp,
          returnedItems: params?.returnedItems,
          address,
          isSplitOrder // Pass split order flag
        });
        smsStatus = updateResp.smsStatus;
        paymentRefundUrl = updateResp.paymentRefundUrl;
        autoRefundResponse = updateResp.autoRefundResponse;
        if (!updateResp.status) {
          return { status: false, errorMsg: updateResp.errorMsg, smsStatus };
        }
      } else {
        return { status: false, errorMsg: 'order in same state', smsStatus };
      }
      break;
    case OUT_FOR_PICKUP_CODE:
      await updateRmaStatus({
        currentOrderStatus,
        rmaItems: params.rmaItems,
        rmaId,
        status: OUT_FOR_PICKUP_STATUS_CODE,
        rmaStatusData: params?.rmaStatusData,
        returnType: return_type,
        orderId,
        waybill,
        rmaData,
        isSplitOrder // Pass split order flag
      }).then(response => {
        if (response) smsStatus = OUT_FOR_PICKUP_CODE;
      });
      break;
    case PICKUP_FAILED:
      await updateRmaStatus({
        currentOrderStatus,
        rmaItems: params.rmaItems,
        rmaId,
        status: PICKUP_FAILED_STATUS_CODE,
        rmaStatusData: params?.rmaStatusData,
        returnType: return_type,
        orderId,
        waybill,
        rmaData,
        isSplitOrder // Pass split order flag
      }).then(response => {
        if (response) smsStatus = PICKUP_FAILED;
      });
      break;
    case ORDER_DELIVERED_CODE:
      if (
        status !== RECEIVED_BY_ADMIN_STATUS_CODE &&
        status !== RMA_UNDER_VERIFICATION &&
        status != RMA_ITEM_VERIFICATION_FAILED
      ) {
        await receivedAtWarehouse({
          currentOrderStatus,
          rmaItems: params.rmaItems,
          rmaId,
          status: RECEIVED_BY_ADMIN_STATUS_CODE,
          rmaStatusData: params?.rmaStatusData,
          returnType: return_type,
          orderId,
          waybill: params.waybill,
          rmaStatus: status,
          timestamp: params.timestamp,
          isSplitOrder // Pass split order flag
        });
      }
      if (
        (status === RECEIVED_BY_ADMIN_STATUS_CODE ||
          status === RMA_ITEM_VERIFICATION_PASSED) &&
        status !== RMA_UNDER_VERIFICATION
      ) {
        smsStatus =
          rmaData.method === CASHONDELIVERY
            ? RETURN_DELIVERED_QC_PASSED_COD
            : RETURN_DELIVERED_QC_PASSED_PREPAID;
      } else if (status !== RMA_UNDER_VERIFICATION) {
        let partiallyFailed = false;
        rmaItems.forEach(item => {
          if (item.qc_failed_qty) {
            partiallyFailed = true;
          }
        });
        if (partiallyFailed) {
          smsStatus =
            rmaData.method === CASHONDELIVERY
              ? RETURN_DELIVERED_PARTIAL_QC_PASSED_COD
              : RETURN_DELIVERED_PARTIAL_QC_PASSED_PREPAID;
        } else {
          smsStatus = RETURN_DELIVERED_QC_FAILED;
        }
      }
      break;
    case ORDER_LOST:
      await updateRmaStatus({
        currentOrderStatus,
        rmaItems: params.rmaItems,
        rmaId,
        status: ORDER_LOST_STATUS_CODE,
        rmaStatusData: params?.rmaStatusData,
        returnType: return_type,
        orderId,
        waybill,
        rmaData,
        isSplitOrder // Pass split order flag
      }).then(response => {
        if (response) {
          smsStatus =
            rmaData.method === CASHONDELIVERY
              ? LOST_DAMAGED_FORWARD_COD
              : LOST_DAMAGED_FORWARD_PREPAID;
        }
      });
      break;
    case ORDER_CANCELLED:
      await updateRmaStatus({
        currentOrderStatus,
        rmaItems: params.rmaItems,
        rmaId,
        status: ORDER_CANCELLED_STATUS_CODE,
        rmaStatusData: params?.rmaStatusData,
        returnType: return_type,
        orderId,
        waybill,
        rmaData,
        isSplitOrder // Pass split order flag
      });
      break;
    default:
      console.log(
        'Unhandled notification ID in reverse shipment:',
        notificationId
      );
  }

  return { status: true, smsStatus, paymentRefundUrl, autoRefundResponse };
};

exports.updateRevShipment = async ({
  rmaIncrementId,
  notificationId,
  remark,
  waybill,
  timestamp,
  items: returnedItems,
  cp_id = '',
  status: statusFromLogs
}) => {
  let errorMsg;
  let smsStatus = '';
  let refundUrl = '';
  let autoRefund = {};

  const {
    data = {},
    status: rmaDataStatus,
    msg: rmaErrorMsg
  } = (await getRmaShipmentDetail({
    rmaIncrementId
  })) || {};
  if (!rmaDataStatus) {
    return { status: false, rmaErrorMsg };
  }
  const { rmaData, rmaStatusData = [], rmaItems = [], address } = data || {};
  if (!rmaData) {
    errorMsg = `No Return Request found for Request ID # ${rmaIncrementId}`;
    return { status: false, errorMsg };
  }

  // Save each return shipment update to amasty_rma_request_status_history
  try {
    const historyCreatedAt = timestamp
      ? typeof timestamp === 'string'
        ? new Date(timestamp)
        : timestamp
      : new Date();
    const rawStatus = statusFromLogs || remark || null;
    const statusMsg = getRmaStatusHistoryMessage(rawStatus);
    await Models.RmaRequestStatusHistory.create({
      request_id: rmaData.request_id,
      reference_number: rmaIncrementId,
      status: rawStatus,
      status_message: statusMsg,
      created_at: historyCreatedAt,
      notification_event_id:
        notificationId != null ? String(notificationId) : null,
      waybill: waybill || null
    });
  } catch (historyErr) {
    global.logError(historyErr, {
      msg: 'amasty_rma_request_status_history create failed',
      rmaIncrementId
    });
  }

  // Detect if this is a split order based on the order increment_id
  const isSplitOrder =
    rmaData.order_inc_id &&
    isSplitOrderPattern(rmaData.order_inc_id.toString());

  console.log('Reverse shipment processing:', {
    rmaIncrementId,
    orderIncrementId: rmaData.order_inc_id,
    isSplitOrder,
    orderId: rmaData.order_id
  });

  logInfo('RMA request status', {
    statusId: rmaData.status_id,
    rmaIncrementId,
    isSplitOrder
  });

  if (`${rmaData.status_id}` === '12' || `${rmaData.status_id}` === '13') {
    return {
      status: false,
      errorMsg: 'Return have been cancelled'
    };
  }

  let refundAmount;

  if (!errorMsg) {
    const switchResponse = await handleSwitchCase({
      rmaData,
      rmaStatusData,
      notificationId,
      smsStatus,
      remark,
      rmaItems,
      waybill,
      timestamp,
      returnedItems,
      address,
      isSplitOrder // Pass split order flag to handleSwitchCase
    });

    const excludedMessageHeadings =
      global.baseConfig?.smsConfig?.excludedMessageHeadings || [];
    const SMSExcludedCourierPartners =
      global.baseConfig?.smsConfig?.SMSExcludedCourierPartners || [];
    if (
      excludedMessageHeadings.includes(switchResponse.smsStatus) ||
      SMSExcludedCourierPartners.includes(Number(cp_id))
    ) {
      switchResponse.smsStatus = '';
    }

    const {
      smsStatus: smsData,
      paymentRefundUrl,
      autoRefundResponse
    } = switchResponse;
    refundAmount = switchResponse?.refundAmount;
    smsStatus = smsData;
    refundUrl = paymentRefundUrl;
    autoRefund = autoRefundResponse;
    if (!switchResponse.status) {
      return { status: false, errorMsg: switchResponse.errorMsg };
    }
  }
  logInfo('sms request', { smsStatus, rmaIncrementId, isSplitOrder });
  let smsResponse;
  if (smsStatus) {
    const updatedData =
      (await getRmaShipmentDetail({
        rmaIncrementId
      })) || {};
    const returnData = updatedData?.data;
    returnData['returnUrl'] = refundUrl;
    returnData['autoRefundResponse'] = autoRefund;
    returnData['isSplitOrder'] = isSplitOrder; // Pass split order info to SMS processing
    smsResponse = await smsObj.sendSMS({
      smsType: smsStatus,
      returnData: returnData,
      isReturn: true,
      timestamp,
      refundTrigger: false,
      notificationId
    });
    smsResponse.orderIncrementId = rmaData.order_inc_id;
    if (smsResponse.errorMsg) {
      return { status: false, errorMsg: smsResponse.errorMsg, smsResponse };
    }
  }
  return { status: true, smsStatus, smsResponse };
};

exports.updateShipmentReverse = async (incrementId, status) => {
  try {
    console.log('incrementId', incrementId);
    console.log('status', status);
    let orderData;
    const queryResponse = await Models.sequelize.query(
      'SELECT customer_email, customer_id, quote_id,entity_id,status from sales_order where increment_id = ?',
      {
        replacements: [incrementId],
        type: QueryTypes.SELECT
      }
    );
    orderData = queryResponse[0];

    if (!orderData) {
      console.error(`No data found for order ${incrementId}`);
      return null;
    }
    const orderDetails = getOrderDetailsByStatus(status);
    const body = [
      {
        op: 'create',
        orderid: null,
        parentOrderId: orderData.entity_id,
        incrementId: incrementId,
        shipmentType: null,
        customerId: orderData?.customer_id,
        quoteId: orderData?.quote_id,
        customerEmail: orderData?.customer_email,
        statusMessage: [
          {
            statusId: orderDetails?.code || null,
            message: orderDetails?.message || orderData?.status,
            timestamp: getCurrentTimestamp()
          }
        ],
        skus: []
      }
    ];
    console.log('body', body);
    const meta = await PubSubService.publishMessage(ORDER_TRACK_HISTORY, body);
    console.log('meta', meta);
    return meta;
  } catch (e) {
    console.error('Error in updateShipment', e);
    return null;
  }
};
