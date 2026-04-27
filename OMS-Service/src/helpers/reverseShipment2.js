/* eslint-disable max-lines-per-function */
const { updateRmaStatus } = require('./rmaUpdateOps');
const moment = require('moment');
const {
  PICKED_UP_STATUS_CODE,
  ORDER_REFUNDED_STATUS_CODE,
  ORDER_STATE_CLOSED,
  ORDER_DELIVERED_STATUS_CODE,
  REFUND_TEMPLATE_MAP: refundTemplateMap
} = require('../constants/order');
const { updateStatusHistory } = require('./utilities');
const { updatePreviousComment } = require('./orderOps');
const { callRefund, callRefundold } = require('./refund');
const { updateOrderStateStatus, getTotalItemCount } = require('./order');
const { OrderComment } = require('../models/seqModels/index');
const { getUtcTIme, promiseAll } = require('../utils');
const { isSplitOrderPattern } = require('../utils/splitOrderUtils');
const region = process.env.REGION;
const _ = require('lodash');
const { publishMessage } = require('../utils/pubsubconfig');
// Function to update split_sales_order table status for split orders
const updateSplitOrderStatus = async (incrementId, status) => {
  if (incrementId && isSplitOrderPattern(incrementId)) {
    try {
      const { SplitSalesOrder } = require('../models/seqModels/index');
      const { checkAndUpdateMainOrderStatus } = require('../utils/splitOrderUtils');
      
      console.log('Updating split order status in return flow:', {
        incrementId,
        status
      });
      
      // Update the specific split order
      await SplitSalesOrder.update({ status }, {
        where: { increment_id: incrementId }
      });
      
      // Check if main order should be updated
      try {
        await checkAndUpdateMainOrderStatus(incrementId);
      } catch (error) {
        console.error('Error checking main order status in return flow:', error);
      }
    } catch (error) {
      console.error('Error updating split order status in return flow:', error);
    }
  }
};

exports.updateNormalPickup = async params => {
  const {
    statusTitle,
    rmaStatusId,
    orderId,
    rma_inc_id: returnIncrementId,
    paymentMethod,
    rmaPaymentMethod,
    returnedItems,
    storeId,
    orderIncrementId
  } = params;
  let commentStatus = '';
  let smsStatus = '';
  let apiResonse = '';
  const formattedTime = getUtcTIme(params.timestamp);
  const { javaUpdateRefund, cpPickupCheckEnabled , omsRefundPubSubEnabled } = global?.baseConfig?.configs || {};
  
  if (!['12', '13'].includes(rmaStatusId.toString())) {
    const orderedItemCount = await getTotalItemCount({ orderId });
    commentStatus = ORDER_DELIVERED_STATUS_CODE;
    const comment = `${returnIncrementId} ${
      params.returnType ? 'dropped off' : 'picked up'
    }`;

    const returnItemCount = getReturnItemCount({rmaItems: params?.rmaItems});

    const refundMethod = region === 'IN' ? rmaPaymentMethod : paymentMethod;
    smsStatus = refundTemplateMap[refundMethod];
    commentStatus = await this.updateStatus({
      orderId,
      returnItemCount,
      orderedItemCount,
      incrementId: params.incrementId
    });

    await OrderComment.create({
      parent_id: orderId,
      comment,
      status: commentStatus,
      entity_name: 'rma',
      created_at: formattedTime
    });
  } else {
    OrderComment.create({
      parent_id: orderId,
      comment: `Refund not triggerd for ${returnIncrementId} as status was ${statusTitle}`,
      status: statusTitle,
      entity_name: 'rma',
      created_at: formattedTime
    });
  }

  let autoRefundResponse;
  if (returnedItems && returnedItems.length > 0 && cpPickupCheckEnabled) {
    autoRefundResponse = autoRefundQcCheck(params);
    smsStatus = autoRefundResponse?.smsTemplate || smsStatus;
  }

  await updateRmaStatus({
    rmaItems: params?.rmaItems,
    rmaId: params.rmaId,
    status: PICKED_UP_STATUS_CODE,
    rmaStatusData: params.rmaStatusData,
    returnType: params.returnType,
    timestamp: params.timestamp,
    orderId,
    isRefunded: commentStatus === ORDER_REFUNDED_STATUS_CODE,
    itemStatusMap: autoRefundResponse?.itemStatusMap
  });

  if (javaUpdateRefund) {
    await callRefund({ returnIncrementId, orderId });
    smsStatus = '';
  } else {
    if (
      omsRefundPubSubEnabled &&
      process.env.OMS_REFUND_API_REARRANGEMENT_TOPIC
    ) {
      const data = { returnIncrementId, orderId, storeId, orderIncrementId, smsStatus, timestamp: params.timestamp };
      console.log(`DATA BEFORE PUBLISH FOR REARRANGEMENT TOPIC ${JSON.stringify(data)}`);
      publishMessage(process.env.OMS_REFUND_API_REARRANGEMENT_TOPIC, data);
      return {
        status: true,
        errorMsg: '',
        smsStatus,
        refundUrl: '',
        autoRefundResponse: ''
      };
    }
    apiResonse = await callRefundold({ returnIncrementId, orderId });
  }

  return {
    status: true,
    errorMsg: '',
    smsStatus,
    refundUrl: apiResonse?.refundUrl,
    autoRefundResponse
  };
}

exports.updateNormalPickupForNoAutoRefund = async (params) => {
  const {
    rmaId,
    rmaVerificationStatusId,
    rmaItems,
    returnType,
    rmaStatusData,
    timestamp,
    orderId,
    isPaymentAutoRefunded
  } = params;
  const promiseArr = [
    updateRmaStatus({
      rmaItems,
      rmaId,
      statusId: rmaVerificationStatusId,
      rmaStatusData,
      returnType,
      timestamp,
      orderId,
      isPaymentAutoRefunded
    }),
  ];

  const { success, errorMsg } = await promiseAll(promiseArr);

  return { status: success, msg: success ? "success" : errorMsg };
};

exports.updateStatus = async ({
  orderId,
  returnItemCount,
  orderedItemCount,
  isShortPicked,
  incrementId
}) => {
  const javaUpdateRefund = global?.baseConfig?.configs?.javaUpdateRefund;
  console.log(`javaUpdateRefund:${javaUpdateRefund} , orderedItemCount:${orderedItemCount} ,returnItemCount:${returnItemCount}`);
  if (returnItemCount === orderedItemCount) {
    if (!javaUpdateRefund || isShortPicked) {
      await updateOrderStateStatus({
        orderId,
        status: ORDER_REFUNDED_STATUS_CODE,
        state: ORDER_STATE_CLOSED
      });
      
      // Also update split_sales_order table if this is a split order
      await updateSplitOrderStatus(incrementId, ORDER_REFUNDED_STATUS_CODE);
      
      await updatePreviousComment({ orderId: orderId });
      
      let updateStatusObj = {};
      updateStatusObj.refunded_date = moment().format('YYYY-MM-DD HH:mm:ss');
      await updateStatusHistory(orderId, updateStatusObj);
    }
    return ORDER_REFUNDED_STATUS_CODE;
  }
  return ORDER_DELIVERED_STATUS_CODE;
};

/**
 * On shipment pickedup courier partner will sent the SKU with corresponding quantity. Accordingly
 * we've to process the auto refund.
 * @param {*} params Return Items and the payload from CP.
 * @return {*} Status & actualReturnedQty for each items.
 */
const autoRefundQcCheck = params => {
  const { rmaItems, returnedItems, paymentMethod } = params;
  const itemStatusMap = {};
  const response = {
    requestedQty: 0,
    returnedQty: 0
  };
  rmaItems.forEach(returnItem => {
    const { item_id: itemId, sku } = returnItem?.OrderItem || {};
    if (![12, 13, '12', '13'].includes(returnItem.item_status)) {
      const returnedItem = returnedItems?.find(
        item => String(item.sku) === String(sku)
      );
      const requestedQty = Number(returnItem?.qty);
      response.requestedQty += requestedQty;
      const returnedQty = Number(returnedItem?.quantity);
      if (!returnedItem) {
        itemStatusMap[itemId] = {
          status: 26, // Picked Up otherwise Partially Verified
          qcFailedQty: requestedQty
        };
        return;
      }
      response.returnedQty += returnedQty;
      itemStatusMap[itemId] = {
        status: requestedQty === returnedQty ? 15 : 26, // Picked Up otherwise Partially Verified
        actualRetrunedQty: _.min([returnedQty, requestedQty])
      };
    }
  });
  response.itemStatusMap = itemStatusMap;
  const qtyDiff = compare(response.requestedQty, response.returnedQty);
  const template = autoRefundQcSMSTemplate[`${qtyDiff}_${paymentMethod === 'cashondelivery' ? 'cod' : 'pp'}`];
  response.smsTemplate = template;
  return response;
};

const autoRefundQcSMSTemplate = {
  '-1_pp': 'autorefund_pp_less',
  '-1_cod': 'autorefund_cod_less',
  '0_pp': 'autorefund_pp_equal',
  '0_cod': 'autorefund_cod_equal',
  '1_pp': 'autorefund_pp_excess',
  '1_cod': 'autorefund_cod_excess'
};

const compare = (num1, num2) => {
  if (num1 === num2) return 0;
  else if (num1 > num2) return -1;
  else if (num1 < num2) return 1;
  else return 0;
};

const getReturnItemCount = ({rmaItems}) => {
  return rmaItems.reduce((count, item) => {
    if (!['12', '13'].includes(item.item_status.toString())) {
      count = count + Number(item.request_qty);
    }
    return count;
  }, 0)
}