/* eslint-disable max-lines-per-function */
const {
  RMA_ITEM_VERIFICATION_FAILED,
  RMA_ITEM_VERIFICATION_PASSED,
  RMA_UNDER_VERIFICATION
} = require('../constants/order');
const { getRmaStatus } = require('./rma');
const { updateRmaStatus } = require('./rmaUpdateOps');

const getQcPassReason = ()=>{
  return global.javaOrderServiceConfig?.order_details?.QC_PASS_REASON? global.javaOrderServiceConfig.order_details.QC_PASS_REASON:[];
}

const getRItemId= (itemCode) =>{
  return itemCode?.split('_')?.[0];
}

const updateValidValue = (rmaItemIdArr, rItemId)=>{
  let valid = true;
  if (!rmaItemIdArr.includes(`${rItemId}`)) {
    valid = false;
  }
  return valid;
}

const checkIfIncludesQcPassReason =(rItem)=>{
  const qcPassReason= getQcPassReason();
  let isReasonExists = false;
  if(rItem && qcPassReason?.length>0 && qcPassReason.includes(rItem.qcReason)){
    isReasonExists = true;
  }
  return isReasonExists;
}

const getRequestCountMap = ({ reqItems, rmaItemIdArr, isFraudPickedUp, isPaymentAutoRefunded= true }) => {
  const passCheck =
    global.javaOrderServiceConfig?.order_details?.SHORT_PICK_PASS_CHECK;
  
  let valid = true;
  const reqItemCountMap = reqItems.reduce((rMap, rItem) => {
    const { itemCode, channelSkuCode } = rItem;
    const rItemId = getRItemId(itemCode);

    valid = updateValidValue(rmaItemIdArr, rItemId);
    if (!rMap[rItemId]) {
      rMap[rItemId] = { qty: null, sku: channelSkuCode, qcFailedQty: null };
    }
    const isQcPassReasonExists = checkIfIncludesQcPassReason(rItem);
    if (rItem.qcReason !== 'PRODUCT_MISSING') {
      if (passCheck) {
        if (rItem.qcStatus === 'PASS') {
          ++rMap[rItemId].qty;
        } else if(isQcPassReasonExists){
          ++rMap[rItemId].qty;
        } else if(isFraudPickedUp || !isPaymentAutoRefunded) {
          ++rMap[rItemId].qcFailedQty;
        }
      } else {
        ++rMap[rItemId].qty;
      }
    } else if(isQcPassReasonExists){
      ++rMap[rItemId].qty;
    } else if (rItem.qcReason === 'PRODUCT_MISSING') {
      if (rMap[rItemId].qty === null) rMap[rItemId].qty = 0;
      else if (rMap[rItemId].qty === 0) ++rMap[rItemId].qty;
    }

    return rMap;
  }, {});

  return { reqItemCountMap, valid };
};

exports.updateRmaActualQty = async ({
  reqItems,
  rmaItems,
  rmaIncrementId,
  requestId,
  isFraudPickedUp,
  isPaymentAutoRefunded
}) => {
  const response = { status: true, errorMsg: '' };
  let isValid = true;
  const existingQtyMap = {};
  const itemStatusMap = {};
  let totalRmaCount = 0;
  let totalReqCount = 0;
  let totalQcFailed = 0;
  const rmaItemIdArr = rmaItems.map(returnItem => {
    const { item_id: itemId } = returnItem?.OrderItem || {};
    if (![12, 13, '12', '13'].includes(returnItem.item_status)) {
      existingQtyMap[`${itemId}`] = Number(returnItem?.qty);
    }
    return `${itemId}`;
  });

  const { reqItemCountMap, valid } = getRequestCountMap({
    reqItems,
    rmaItemIdArr,
    isFraudPickedUp,
    isPaymentAutoRefunded
  });

  isValid = valid;
  if (!isValid) {
    response.status = false;
    response.errorMsg = `"sent item data doesn't match with RMA Id: ${rmaIncrementId}`;
    return response;
  }

  const rmaStatusData = await getRmaStatus();

  const verifyFailId = rmaStatusData.find(
    status => status.status_code === RMA_ITEM_VERIFICATION_FAILED
  )?.status_id;

  const verifyPassId = rmaStatusData.find(
    status => status.status_code === RMA_ITEM_VERIFICATION_PASSED
  )?.status_id;

  let requestStatusId = verifyPassId;

  for (const requestItemId in existingQtyMap) {
    const reqItemQty = reqItemCountMap[requestItemId]?.qty;
    const qcFailedQty = reqItemCountMap[requestItemId]?.qcFailedQty;
    const existingQty = existingQtyMap[requestItemId];

    totalRmaCount = totalRmaCount + Number(existingQty);
    totalReqCount = totalReqCount + Number(reqItemQty) + Number(qcFailedQty);
    totalQcFailed = totalQcFailed + Number(qcFailedQty);

    if (reqItemQty > existingQty) {
      response.status = false;
      response.errorMsg = `SKU ${reqItemCountMap[requestItemId]?.sku} Qty Mismatched Error: RMA was created for Qty ${existingQty} but sent Qty from WMS is ${reqItemQty}.RMA Id: ${rmaIncrementId}`;
      return response;
    }

    // itemStatusMap[requestItemId].status = verifyPassId;

    itemStatusMap[requestItemId] = {
      status: verifyPassId,
      actualRetrunedQty: reqItemQty,
      qcFailedQty
    };

    if (reqItemQty !== existingQty || qcFailedQty > 0) {
      requestStatusId = verifyFailId;
      itemStatusMap[requestItemId].status = verifyFailId;
    }
  }
  
  const updateRmaResp = await updateRmaStatus({
    rmaItems,
    rmaId: requestId,
    statusId: requestStatusId,
    rmaStatusData: [],
    itemStatusMap
  });

  if (!updateRmaResp) {
    return { status: updateRmaResp, errorMsg: 'Error upadating RMA Stataus' };
  }
  response.missingCount = totalRmaCount - totalReqCount;
  response.qcFailedQty = totalQcFailed;
  response.totalRmaCount = totalRmaCount;
  response.totalReturnedQty = totalReqCount;
  return response;
};

exports.rollbackToUnderVerification = async ({ returnData, orderId }) => {
  const { rmaData, rmaItems } = returnData;
  const { request_id: requestId } = rmaData || {};
  const rmaStatusData = await getRmaStatus();
  const itemStatusMap = {};
  const underVerificationId = rmaStatusData.find(
    status => status.status_code === RMA_UNDER_VERIFICATION
  )?.status_id;

  rmaItems.forEach(item => {
    if (![12, 13, '12', '13'].includes(item.item_status)) {
      itemStatusMap[item.order_item_id] = {
        status: underVerificationId,
        actualRetrunedQty: null
      };
    }
  });

  await updateRmaStatus({
    rmaItems,
    isShortPickup: true,
    rmaId: requestId,
    statusId: underVerificationId,
    rmaStatusData,
    itemStatusMap,
    orderId
  });
};