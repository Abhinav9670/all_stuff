const {
  sendEarnRequestOnDelivery,
  sendEarnRequestOnPickupSuccess,
  sendEarnRequestCheckIsRatingOnOrder
} = require('../../utils/easApi');
const { logInfo, getNumericValue } = require('../../utils');
const orderObj = require('../order');

exports.earnEventForDeliverySuccess = async (orderData) => {
  const {
    increment_id,
  } = orderData;
  logInfo(`earnEventForDeliverySuccess ${increment_id}`);
  try {
    const request = getEarnRequest(orderData);
    if (request.status) {
      logInfo('EarnRequest DeliverySuccess', request);
      await sendEarnRequestOnDelivery(request);
    }
    logInfo(`earnEventForDeliverySuccess error ${increment_id}`);
  } catch (e) {
    global.logError(`earnEventForDeliverySuccess ERROR, ${increment_id}, ${e.message? JSON.stringify(e.message): ''}, ${e}`);
  }
};

exports.earnEventForPickup = async ({ orderId }) => {
  try {
    const orderData = await orderObj.getOrder({
      entityId: orderId,
      inclSubSales: true
    });
    const { entity_id, customer_id, store_id, creditMemo } = orderData;
    const request = {
      customerId: customer_id,
      storeId: store_id,
      orderId: entity_id,
      subTotal: parseFloat(creditMemo?.amstorecredit_amount)
    };
    if (request) {
      logInfo('EarnRequest ForPickup', request);
      await sendEarnRequestOnPickupSuccess(request);
    }
  } catch (e) {
    global.logError('Earn ERROR', e);
  }
};

exports.earnCreditMemoResponse = (response, currency) => {
  const easCoins = Number(response.eas_coins)
    ? Number(response.eas_coins)
    : undefined;
  const easValueInCurrency = Number(response.eas_value_in_currency)
    ? `${currency} ${getNumericValue(response.eas_value_in_currency)}`
    : undefined;
  return { easCoins, easValueInCurrency };
};

exports.shukranCreditMemoResponse = (response, currency) => {
  const shukranPoints = Number(response.shukran_points_refunded)
    ? Number(response.shukran_points_refunded)
    : undefined;
  const shukranValueInCurrency = Number(response.shukran_points_refunded_value_in_currency)
    ? `${currency} ${getNumericValue(response.shukran_points_refunded_value_in_currency)}`
    : undefined;
  return { shukranPoints, shukranValueInCurrency };
};

exports.refundAmount = creditMemo => {
  let refunded = Number(creditMemo.grand_total);
  if (Number(creditMemo.amstorecredit_amount) > 0)
    refunded += Number(creditMemo.amstorecredit_amount);
  return refunded;
};

exports.earnCheckIsRatingOnOrder = async ({ orderId, smsType}) => {
  try {
    if (smsType == 'delivered' && orderId) {
      return await sendEarnRequestCheckIsRatingOnOrder({ orderId: orderId });
    }
    return false;
  } catch (e) {
    global.logError('Earn ERROR', e);
    return false;
  }
};

const getEarnRequest = orderData => {
  try {
    const {
      entity_id,
      customer_id,
      store_id,
      grand_total,
      shipping_amount = 0,
      cash_on_delivery_fee = 0,
      import_fee = 0,
      subSales,
      amstorecredit_amount = 0
    } = orderData.orderData;
    const { donation_amount = 0 } = subSales;
    if (entity_id && customer_id) {
      console.log(
        `EAS getEarnRequest:: customer_id: ${customer_id} store_id: ${store_id} entity_id: ${entity_id} grand_total: ${grand_total} shipping_amount: ${shipping_amount} cash_on_delivery_fee: ${cash_on_delivery_fee} import_fee: ${import_fee} donation_amount: ${donation_amount} `
      );
      let subTotal =
        parseFloat(grand_total) -
        (parseFloat(shipping_amount) +
          parseFloat(cash_on_delivery_fee) +
          parseFloat(import_fee) +
          parseFloat(donation_amount));
      if (amstorecredit_amount != null) {
        subTotal = subTotal + parseFloat(amstorecredit_amount);
      }
      return {
        status: true,
        customerId: customer_id,
        storeId: store_id,
        orderId: entity_id,
        subTotal: subTotal
      };
    }
  } catch (e) {
    global.logError('Earn ERROR Request Data', e);
    return { status: false };
  }
  return { status: false };
};
