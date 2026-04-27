const moment = require('moment');
const {
  RmaRequest,
  RmaRequestItem,
  OrderComment
} = require('../models/seqModels/index');
const {
  RECEIVED_BY_ADMIN_STATUS_CODE,
  STATUS_HISTORY_MAP,
  ORDER_CANCELLED_STATUS_CODE,
  PICKUP_FAILED_STATUS_CODE
} = require('../constants/order');
const { promiseAll, stringifyError } = require('../utils');
const { Op } = require('sequelize');
const { isEmpty } = require('lodash');
const { updateStatusHistory } = require('./utilities');
const { logBrazecustomEventForPickupCancel, logBrazecustomEventForFailedPickupAttempt } = require('./braze');

exports.updateRmaStatus = async params => {
  const excludedStatusIds = [45, 47];

 const { status, rmaStatusData, orderId, waybill, isPaymentAutoRefunded = true, rmaData = { customer_id: '' }, rmaItems, rmaId } = params;
  const { customer_id } = rmaData;
  if (status === ORDER_CANCELLED_STATUS_CODE) {
    // const { entity_id, increment_id, customer_id, orderedItems, count } = data;
    logBrazecustomEventForPickupCancel({ data: { increment_id: waybill, entity_id: orderId, customer_id: customer_id, orderData: rmaData,orderedItems: rmaItems, return_id: rmaId } });
  } else if (status === PICKUP_FAILED_STATUS_CODE) {
     logBrazecustomEventForFailedPickupAttempt({ data: { increment_id: waybill, entity_id: orderId, customer_id: customer_id, orderData: rmaData, orderedItems: rmaItems, return_id: rmaId } });
  }

  try {
    global.logInfo("shipment-update ===========================", params);
    const promiseArray = [];
    let finalStatusId =
      params.statusId ||
      rmaStatusData.find(s => s.status_code === status)?.status_id;
      
    const updateObj = { status: finalStatusId };
    if (params.isShortPickup) {
      updateObj.is_short_pickedup = 1;
    }
    if (params.isFraudPickup) {
      updateObj.is_fraud_pickedup = 1;
    }

    if (isPaymentAutoRefunded && params.returnType && !params.isShortPickup && !params.isFraudPickup && !excludedStatusIds.includes(finalStatusId) && status !== RECEIVED_BY_ADMIN_STATUS_CODE) {
      const dropedOffId = rmaStatusData.find(
        s => s.status_code === 'dropped_off'
      )?.status_id;
      global.logInfo("shipment-update ===========I'm Here================", dropedOffId);
      updateObj.status = dropedOffId;
      finalStatusId = dropedOffId;
    }

    if (status === RECEIVED_BY_ADMIN_STATUS_CODE) {
      promiseArray.push(
        OrderComment.create({
          parent_id: orderId,
          comment: `RMA: ${waybill} received at warehouse`,
          status: params.currentOrderStatus || '',
          entity_name: 'rma'
        })
      );
    }
   
    promiseArray.push(
      RmaRequest.update(updateObj, { where: { request_id: params.rmaId } })
    );

    promiseArray.push(
      this.updateRmaItemStatus({
        rmaItems: params.rmaItems,
        parentStatusId: finalStatusId,
        itemStatusMap: params.itemStatusMap
      })
    );
    await this.updateRmaHistory(
      { ...params, statusId: finalStatusId },
      promiseArray
    );

    await Promise.allSettled(promiseArray)
      .then(values => {
        console.log({ values });
        values.forEach(value => {
          if (value.status === 'rejected') {
            global.logError(value.reason, { msg: 'error updating rma status' });
            return false;
          }
        });
      })
      .catch(err => {
        global.logError(err);
      });
    return true;
  } catch (e) {
    global.logError(e, {
      msg: `Exception block : error updating rma status : ${e.message}`
    });
    return false;
  }
};

exports.setShortPickedup = async ({
  rmaId,
  rmaVerificationStatusId,
  rmaItems,
  returnType,
  rmaStatusData,
  timestamp,
  orderId
}) => {
  const promiseArr = [
    this.updateRmaStatus({
      rmaItems,
      rmaId,
      statusId: rmaVerificationStatusId,
      rmaStatusData,
      returnType,
      isShortPickup: true,
      timestamp,
      orderId
    })
  ];

  const { success, errorMsg } = await promiseAll(promiseArr);

  return { status: success, msg: success ? 'success' : errorMsg };
};

exports.updateRmaItemStatus = async ({
  rmaItems = [],
  parentStatusId,
  itemStatusMap
}) => {
  const validReqItemIds = rmaItems?.reduce((idArr, rItem) => {
    if (![12, 13, '12', '13'].includes(rItem.item_status)) {
      idArr.push(`${rItem.request_item_id}`);
    }
    return idArr;
  }, []);

  if (isEmpty(itemStatusMap)) {
    return await RmaRequestItem.update(
      { item_status: parentStatusId },
      {
        // where: { request_item_id: [validReqItemIds] }
        where: { request_item_id: { [Op.in]: validReqItemIds } }
      }
    );
  } else {
    const itemIds = Object.keys(itemStatusMap);
    console.log('Requested Items', JSON.stringify(itemIds));
    const output = [];
    for (const itemId of itemIds) {
      const updateData = {
        item_status: itemStatusMap[Number(itemId)].status,
        actual_qty_returned: itemStatusMap[Number(itemId)].actualRetrunedQty,
        qc_failed_qty: itemStatusMap[Number(itemId)].qcFailedQty
      };

      try {
        await RmaRequestItem.update(updateData, {
          where: { order_item_id: itemId, request_id: rmaItems[0]?.request_id }
        });
        output.push(itemId); // Assuming you want to push the itemId to the output array
      } catch (error) {
        const stringifiedError = stringifyError(error);
        console.log(`Error in processing : ${stringifiedError}`);
        global.logError(error);
        // Handle the error here as per your application's requirements
      }
    }
    console.log('Processed Item', JSON.stringify(output));
  }
};

exports.updateRmaHistory = async (
  { statusId, status, orderId, timestamp, isRefunded },
  promiseArray
) => {
  let finalStatus = status;
  if (['19', '15', '23'].includes(`${statusId}`)) {
    finalStatus = 'picked_up';
  }
  const toUpdateDate = STATUS_HISTORY_MAP[finalStatus];

  if (toUpdateDate && timestamp) {
    const updateStatusObj = {
      [toUpdateDate]: timestamp
        ? moment.utc(timestamp).format('YYYY-MM-DD HH:mm:ss')
        : ''
    };
    if (isRefunded) {
      updateStatusObj.refunded_date = timestamp
        ? moment.utc(timestamp).format('YYYY-MM-DD HH:mm:ss')
        : '';
    }
    // promiseArray.push(
    //   StatusHistory.update(updateStatusObj, {
    //     where: { order_id: orderId }
    //   })
    // );
    promiseArray.push(updateStatusHistory(orderId, updateStatusObj));
  }
};