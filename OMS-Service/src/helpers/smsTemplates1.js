/* eslint-disable max-lines */
const {
  COMPLETELY_VERIFIED,
  DELIVERED,
  DROPOFF_SMS,
  FRAUD_PICKEDUP,
  LOST_DAMAGED_FORWARD_COD,
  LOST_DAMAGED_FORWARD_PREPAID,
  ORDER_COD_CANCEL,
  ORDER_COD_FULLY_UNFULFILMENT,
  ORDER_COD_PARTIAL_UNFULFILMENT,
  ORDER_PAYMENT_HOLD_CANCEL,
  ORDER_PLACE_SUCCESS,
  ORDER_PLACE_SUCCESS_SPLIT_ORDER,
  ORDER_PREPAID_CANCEL,
  ORDER_PREPAID_FULLY_UNFULFILMENT,
  ORDER_PREPAID_PARTIAL_UNFULFILMENT,
  ORDER_PREPAID_UNFULFILMENT_SPLIT_ORDER,
  ORDER_TABBY_INSTALLMENT_CANCEL,
  ORDER_TABBY_PAYLATER_CANCEL,
  OUT_FOR_DELIVERY,
  OUT_FOR_PICKUP,
  PICKUP_FAILED,
  QC_SORT_SOME_REFUND,
  REFUND_COMPLETED_COD,
  REFUND_COMPLETED_ONLINE,
  REFUND_COMPLETED_TABBY_BNPL,
  REFUND_COMPLETED_TABBY_INS,
  REGISTRATION_SUCCESS,
  RETURN_ALL_QC_FAILED,
  RETURN_AWB_CREATE,
  RETURN_CREATE,
  RETURN_DELIVERED_PARTIAL_QC_PASSED_COD,
  RETURN_DELIVERED_PARTIAL_QC_PASSED_PREPAID,
  RETURN_DELIVERED_QC_FAILED,
  RETURN_DELIVERED_QC_PASSED_COD,
  RETURN_DELIVERED_QC_PASSED_PREPAID,
  RETURN_PICKED_COD,
  RETURN_PICKED_PREPAID,
  RETURN_RAISED,
  RETURN_SOME_QC_FAILED,
  RTO_INITIATED,
  RTO_REFUND_INITIATED,
  SHIPPED,
  SHORT_PICKUP,
  SHORT_PICKUP_REFUND_COD,
  SHORT_PICKUP_REFUND_ONLINE,
  SOME_SORT_ITEMS,
  UNDELIVERED
} = require('../constants/smsTemplateConstants');
const { autoRefundTemplate } = require('./smsTemplates2');
const { getStoreLink } = require('./store');
const { getCountryURL } = require('./utilities');

/* eslint-disable max-lines-per-function */
const getTemplateMap4 = inputData => {
  const {
    smsType,
    firstname,
    incrementId,
    codPartialCancelAmount,
    onlineRefundAmount,
    order_currency_code,
    courierName,
    incrementIdData=''
  } = inputData;
  let replaceMap;
  const rm1 = {
    '{{first_name}}': firstname,
    '{{currency_type}}': order_currency_code,
    '{{order_id}}': incrementId,
    '{{amount}}': onlineRefundAmount,
    '{{shipment_id}}': incrementIdData,
    '{{reorderLink}}' : getStoreLink(inputData?.storeId),
     '{{amount}}': onlineRefundAmount,
  };
  switch (smsType) {
    case ORDER_PREPAID_FULLY_UNFULFILMENT:
    case ORDER_PREPAID_UNFULFILMENT_SPLIT_ORDER:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{currency_type}}': order_currency_code,
        '{{order_id}}': incrementId,
        '{{amount}}': codPartialCancelAmount,
        '{{shipment_id}}': incrementIdData,
        '{{reorderLink}}' : getStoreLink(inputData?.storeId),
        '{{shipment_id}}': incrementId,
        '{{reorderLink}}': getStoreLink(inputData?.storeId),
        '{{reorder_link}}': `${getStoreLink(inputData?.storeId)}/account/orderview/${incrementId}`,
        '{{homepage_link}}': getCountryURL(Number(inputData?.storeId))
      };
      break;
    case ORDER_PREPAID_CANCEL:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{currency_type}}': order_currency_code,
        '{{shipment_id}}': incrementId,
        '{{amount}}': codPartialCancelAmount,
         '{{reorder_link}}': `${getStoreLink(inputData?.storeId)}/account/orderview/${incrementId}`
      };
      break;
    case ORDER_TABBY_INSTALLMENT_CANCEL:
    case ORDER_TABBY_PAYLATER_CANCEL:
    case 'order_prepaid_cancel_split_order':
    case 'order_tabby_installment_cancel_split_order':
    case 'order_tabby_paylater_cancel_split_order':  
      replaceMap = rm1;
      break;
    case UNDELIVERED:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{order_id}}': incrementId,
        '{{courier_name}}': courierName
      };
      break;

    default:
  }
  return replaceMap;
};
const getTemplateMap3 = inputData => {
  const {
    smsType,
    firstname,
    incrementId,
    onlineRefundAmount,
    creditMemoCurrency,
    returnItemText,
    creditRefunded,
    missingItems,
    canceledItemCount,
    order_currency_code,
    codPartialCancelAmount,
    returnId,
    incrementIdData='',
    returnAwb='',
    entityId=0
  } = inputData;
  let replaceMap;
  const finalOnlineRefundAmount =
    onlineRefundAmount > -1 && !codPartialCancelAmount
      ? onlineRefundAmount
      : codPartialCancelAmount;
  switch (smsType) {
    case REFUND_COMPLETED_TABBY_INS:
    case REFUND_COMPLETED_TABBY_BNPL:
    case REFUND_COMPLETED_ONLINE:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{order_id}}': incrementId,
        '{{return_id}}': returnId,
        '{{currency_type}}': creditMemoCurrency || inputData?.currency,
        '{{amount}}': finalOnlineRefundAmount,
        '{{items}}': returnItemText,
        '{{return_awb}}': returnAwb || returnId,
        '{{return_id}}': returnId
      };
      break;
    case SHORT_PICKUP_REFUND_COD:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{currency_type}}': creditMemoCurrency || inputData?.currency,
        '{{items}}': returnItemText,
        '{{amount}}': finalOnlineRefundAmount,
        '{{missing_items}}': missingItems
      };
      break;
      case LOST_DAMAGED_FORWARD_PREPAID:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{currency_type}}': creditMemoCurrency || inputData?.currency,
        '{{amount}}': finalOnlineRefundAmount,
        '{{shipment_id}}': incrementIdData,
        '{{reorder_link}}': `${getStoreLink(inputData?.storeId)}/account/orderview/${incrementIdData}`
      };
      break;
    case LOST_DAMAGED_FORWARD_COD:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{shipment_id}}': incrementIdData,
        '{{reorder_link}}': `${getStoreLink(inputData?.storeId)}/account/orderview/${incrementIdData}`
      };
      break;
    case SHORT_PICKUP_REFUND_ONLINE:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{currency_type}}': creditMemoCurrency || inputData?.currency,
        '{{items}}': returnItemText,
        '{{amount}}': finalOnlineRefundAmount,
        '{{missing_items}}': missingItems
      };
      break;
    case ORDER_COD_PARTIAL_UNFULFILMENT:
    case ORDER_PREPAID_PARTIAL_UNFULFILMENT:
    case 'order_cod_partial_unfulfilment_split_order':
    case 'order_prepaid_partial_unfulfilment_split_order':
      replaceMap = {
        '{{first_name}}': firstname,
        '{{items}}': canceledItemCount,
        '{{currency_type}}': order_currency_code,
        '{{order_id}}': incrementId,
        '{{amount}}': codPartialCancelAmount,
        '{{shipment_id}}':incrementIdData,
        '{{orderLink}}' : `${getStoreLink(inputData?.storeId)}/account/orders`,
        '{{item_count}}': canceledItemCount,
        '{{shipment_id}}': incrementIdData,
        '{{order_link}}': `${getStoreLink(inputData?.storeId)}/account/orders`,
        '{{order_details_link}}': `${getStoreLink(inputData?.storeId)}/account/orderview/${incrementId}`,
        '{{item_count}}': canceledItemCount,
        '{{homepage_link}}': getCountryURL(Number(inputData?.storeId))
      };
      break;
    case RTO_REFUND_INITIATED:
    case 'rto_refund_initiated_split_order':
      replaceMap = {
        '{{first_name}}': firstname,
        '{{currency_type}}': order_currency_code,
        '{{order_id}}': incrementId,
        '{{amount}}': codPartialCancelAmount,
        '{{shipment_id}}':incrementIdData,
      };
      break;
    case 'refund_initiated_cashgram':
      replaceMap = {
        '{{first_name}}': firstname,
        '{{order_id}}': incrementId,
        '{{currency_type}}': creditMemoCurrency || inputData?.currency,
        '{{cashgram_link}}': inputData?.returnUrl,
        '{{amount}}': finalOnlineRefundAmount
      };
      break;
    default:
  }
  return replaceMap;
};

const getTemplateMap2 = inputData => {
  const {
    smsType,
    returnId,
    firstname,
    incrementId,
    courierName,
    returnAwb,
    returnItemText,
    creditMemoCurrency,
    creditRefunded,
    codPartialCancelAmount,
    returnedItemsResponse,
    onlineRefundAmount,
    itemReturned,
    canceledItemCount,
     cpId,
    returnData
  } = inputData;
  const cpIdMapping = global?.baseConfig?.cpIdMapping || {};
  let ItemCount = 0;
  returnData?.rmaItems?.forEach(item => {
    ItemCount += Number(item.qty)
  });
  const transformedCpId = cpId
    ? cpIdMapping[cpId.toLowerCase()] || cpId
    : 'Unknown';
  const finalOnlineRefundAmount =
    onlineRefundAmount > -1 && !codPartialCancelAmount
      ? onlineRefundAmount
      : codPartialCancelAmount;
  let replaceMap;
  switch (smsType) {
    case RETURN_CREATE:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{order_id}}': incrementId,
        '{{return_id}}': returnId
      };
      break;
    case RETURN_AWB_CREATE:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{order_id}}': incrementId,
        '{{return_id}}': returnId,
        '{{Return_AWB}}': returnAwb,
        '{{return_awb}}': returnAwb,
        '{{courier_name}}': courierName
      };
      break;
    case DROPOFF_SMS:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{order_id}}': incrementId,
        '{{return_id}}': returnId,
        '{{Return_AWB}}': returnAwb,
        '{{courier_name}}': transformedCpId,
         '{{courier_partner}}': transformedCpId,
        '{{shipment_id}}': incrementId,
        '{{item_count}}': ItemCount|| canceledItemCount,
      };
      break;
    case OUT_FOR_PICKUP:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{items}}': returnItemText,
        '{{courier_name}}': courierName,
        '{{AWB_number}}': returnAwb,
        '{{tracking_link}}': url
      };
      break;

    case SHORT_PICKUP:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{items}}': returnItemText,
        '{{courier_name}}': courierName
      };
      break;
    case FRAUD_PICKEDUP:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{items}}': itemReturned,
        '{{courier_partner}}': courierName
      };
      break;
    case REFUND_COMPLETED_COD:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{order_id}}': incrementId,
        '{{return_id}}': returnId,
        '{{currency_type}}': creditMemoCurrency || inputData?.currency,
        '{{amount}}': finalOnlineRefundAmount
      };
      break;
    case RETURN_ALL_QC_FAILED:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{items}}': returnedItemsResponse.totalRmaCount
      };
      break;
    case RETURN_SOME_QC_FAILED:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{items}}': returnedItemsResponse.totalRmaCount,
        '{{qc_fail_items}}': returnedItemsResponse.qcFailedQty,
        '{{currency_type}}': creditMemoCurrency,
        '{{amount}}': onlineRefundAmount,
        '{{return_awb}}': returnAwb,
        '{{return_details_link}}': `${getStoreLink(inputData?.storeId)}/account/orderview/${incrementId}`
      };
      break;
    case QC_SORT_SOME_REFUND:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{items}}': returnedItemsResponse.totalRmaCount,
        '{{qc_fail_items}}':
          returnedItemsResponse.qcFailedQty +
          returnedItemsResponse.missingCount,
        '{{currency_type}}': creditMemoCurrency,
        '{{amount}}': onlineRefundAmount
      };
      break;
    case SOME_SORT_ITEMS:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{items}}': returnedItemsResponse.totalRmaCount,
        '{{missing_items}}': returnedItemsResponse.missingCount,
        '{{currency_type}}': creditMemoCurrency,
        '{{amount}}': onlineRefundAmount
      };
      break;
    case COMPLETELY_VERIFIED:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{items}}': returnedItemsResponse.totalRmaCount,
        '{{currency_type}}': creditMemoCurrency,
        '{{amount}}': onlineRefundAmount
      };
      break;
     case RTO_INITIATED:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{currency_type}}': creditMemoCurrency,
        '{{amount}}': onlineRefundAmount,
        '{{order_id}}': incrementId,
        '{{shipment_id}}': incrementId,
      };
      break;
    case RETURN_RAISED:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{item_count}}': ItemCount || returnedItemsResponse?.totalRmaCount,
        '{{shipment_id}}': incrementId,
      };
      break;
    case RETURN_PICKED_PREPAID:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{item_count}}': ItemCount || returnedItemsResponse?.totalRmaCount,
        '{{currency_type}}': creditMemoCurrency,
        '{{amount}}': onlineRefundAmount,
        '{{return_awb}}': returnAwb
      };
      break;
    case RETURN_PICKED_COD:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{item_count}}': ItemCount || returnedItemsResponse?.totalRmaCount,
        '{{currency_type}}': creditMemoCurrency,
        '{{amount}}': onlineRefundAmount,
        '{{return_awb}}': returnAwb
      };
      break;
    case RETURN_DELIVERED_QC_PASSED_PREPAID:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{currency_type}}': creditMemoCurrency,
        '{{amount}}': onlineRefundAmount,
        '{{return_awb}}': returnAwb,
      };
      break;
    case RETURN_DELIVERED_QC_PASSED_COD:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{currency_type}}': creditMemoCurrency,
        '{{amount}}': onlineRefundAmount,
        '{{return_awb}}': returnAwb,
      };
      break;
    case RETURN_DELIVERED_PARTIAL_QC_PASSED_PREPAID:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{currency_type}}': creditMemoCurrency,
        '{{amount}}': onlineRefundAmount,
        '{{return_awb}}': returnAwb,
         '{{return_details_link}}': `${getStoreLink(inputData?.storeId)}/account/orderview/${incrementId}`
      };
      break;
    case RETURN_DELIVERED_PARTIAL_QC_PASSED_COD:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{currency_type}}': creditMemoCurrency,
        '{{amount}}': onlineRefundAmount,
        '{{return_awb}}': returnAwb,
        '{{return_details_link}}': `${getStoreLink(inputData?.storeId)}/account/orderview/${incrementId}`
      };
      break;
    case RETURN_DELIVERED_QC_FAILED:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{return_awb}}': returnAwb,
        '{{return_details_link}}': `${getStoreLink(inputData?.storeId)}/account/orderview/${incrementId}`
      };
      break;
    case LOST_DAMAGED_FORWARD_PREPAID:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{currency_type}}': creditMemoCurrency,
        '{{amount}}': onlineRefundAmount,
        '{{shipment_id}}': incrementId,
      };
      break;
    case LOST_DAMAGED_FORWARD_COD:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{currency_type}}': creditMemoCurrency,
        '{{amount}}': onlineRefundAmount,
        '{{return_awb}}': returnAwb,
        "{{return_details_link}}": ""
      };
      break;
    default:
  }
  return replaceMap;
};

exports.getTemplateMap = inputData => {
  const {
    smsType,
    registerCustomerName,
    firstname,
    incrementId,
    courierName,
    awbNumber,
    estimatedDelivery,
    entityId,
    pickupFailedDate,
    cpId,
    splitOrderData={},
    incrementIdData = ''
  } = inputData;
  try {
  let replaceMap;
  let newEstDelivery = estimatedDelivery.includes("<sup>") ? estimatedDelivery.replace(/<sup>|<\/sup>/g, "") : estimatedDelivery
  let newPickupFailedDate = pickupFailedDate.includes("<sup>") ? pickupFailedDate.replace(/<sup>|<\/sup>/g, "") : pickupFailedDate
  const replaceMap1 = {
    '{{first_name}}': firstname,
    '{{order_id}}': incrementId,
    '{{order_entity_id}}': entityId,
    '{{shipment_id}}': incrementIdData,
    '{{reorderLink}}' : getStoreLink(inputData?.storeId)

  };
  switch (smsType) {
    case REGISTRATION_SUCCESS:
      replaceMap = {
        '{{customer_name}}': registerCustomerName
      };
      break;
    case ORDER_PLACE_SUCCESS:
      replaceMap = {
        '{{customer_name}}': firstname,
        '{{order_id}}': incrementId,
        '{{delivery_date}}': newEstDelivery
      };
      break;
     case ORDER_PLACE_SUCCESS_SPLIT_ORDER: {
      const inc1 = splitOrderData?.increment1 != null ? String(splitOrderData.increment1) : '';
      const inc2 = splitOrderData?.increment2 != null ? String(splitOrderData.increment2) : '';
      const est1 = splitOrderData?.est1 != null ? String(splitOrderData.est1) : '';
      const est2 = splitOrderData?.est2 != null ? String(splitOrderData.est2) : '';
      const hasTwoShipments = Boolean(inc2 && est2);
      // Optional parts: only non-empty when 2nd shipment exists – use in template to avoid ", #undefined" and "& undefined"
      const secondShipmentPart = inc2 ? `, #${inc2}` : '';
      const secondDeliveryPart = est2 ? ` & ${est2}` : '';
      const shipmentsPhrase = hasTwoShipments ? 'two different shipments' : 'a shipment';
      replaceMap = {
        '{{customer_name}}': firstname ?? '',
        '{{order_id}}': incrementId ?? '',
        '{{shipment_id1}}': inc1,
        '{{shipment_id2}}': inc2,
        '{{delivery_date1}}': est1,
        '{{delivery_date2}}': est2,
        '{{second_shipment_part}}': secondShipmentPart,
        '{{second_delivery_part}}': secondDeliveryPart,
        '{{shipments_phrase}}': shipmentsPhrase,
      };
      break;
    }    
    case SHIPPED: {
      const trackingBaseUrl =
        global?.baseConfig?.configs?.trackingBaseUrl ||
        'https://track.stylishop.com/';
      let url = trackingBaseUrl + `?waybill=${awbNumber}`;
      if (global?.baseConfig?.smsConfig?.deeplinkUrlShipped){
        url = 'https://stylishop.com/web?type=tracking&url=' + url;
      }
      replaceMap = {
        '{{first_name}}': firstname,
        '{{order_id}}': incrementId,
        '{{courier_name}}': courierName,
        '{{delivery_date}}': newEstDelivery,
        '{{link}}': encodeURIComponent(url),
        '{{AWB}}': awbNumber ?? ''
      };
      break;
    }
    case OUT_FOR_DELIVERY:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{order_id}}': incrementId,
        '{{courier_name}}': courierName,
        '{{AWB}}': awbNumber
      };
      break;
    case PICKUP_FAILED:
      replaceMap = {
        '{{first_name}}': firstname,
        '{{order_id}}': incrementId,
        '{{courier_name}}': courierName,
        '{{delivery_date}}': newPickupFailedDate,
        '{{AWB_number}}': awbNumber
      };
      break;
    case DELIVERED:
    case RTO_INITIATED:
    case 'rto_initiated_split_order':
    case 'order_lost':
    case ORDER_COD_CANCEL:
    case 'order_cod_cancel_split_order':
    case ORDER_PAYMENT_HOLD_CANCEL:
    case ORDER_COD_FULLY_UNFULFILMENT:
    case 'order_cod_fully_unfulfilment_split_order':  
      replaceMap = replaceMap1;
      break;

      default:
    }
    replaceMap =
      replaceMap ||
      getTemplateMap2(inputData) ||
      getTemplateMap3(inputData) ||
      getTemplateMap4(inputData) ||
      autoRefundTemplate(inputData);
    console.log(`RELPACE MAPSS DATASSS FOR REFUND ISSUE FOR AWB NUMBER  = ${awbNumber} >>>>>>>>>RELPACE DATA>>>>>>> ${JSON.stringify(replaceMap)}`)    
    return replaceMap;
  } catch (e) {
    console.error('Error in getTemplateMap', e.message ? e.message : '', e);
    return {};
  }
};
