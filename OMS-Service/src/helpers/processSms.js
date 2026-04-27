const { getRmaShipmentDetail } = require('../helpers/rma');
const orderObj = require('./order');
const smsObj = require('./sms')
const { getStoreConfigs } = require('../utils/config');
const { updateCleverTapProfile } = require('./cleverTap');
exports.processSMS = async ({
  type,
  incrementId,
  template,
  entityId,
  codPartialCancelAmount,
  cpId,
  updateCleverTap,
  splitOrderData={},
  incrementIdData='',
}) => {
  try {
    let orderData = {};
    let returnData = {};
    let storeId = '';
    if (type.toLowerCase() === 'order') {
      orderData = await orderObj.getOrder({ incrementId, entityId });
      // if (updateCleverTap) {
      //   updateCleverTapProfile(orderData);
      // }
      storeId = orderData.store_id;
    } else {
      const rmaDetailResp = await getRmaShipmentDetail({
        rmaIncrementId: incrementId
      });
      returnData = rmaDetailResp.data;
      storeId = returnData?.rmaData?.store_id;
    }

    const currency = getStoreConfigs({
      key: 'storeCurrency',
      storeId
    })?.[0]?.storeCurrency;

    return await smsObj.sendSMS({
      currency,
      codPartialCancelAmount,
      smsType: template,
      orderData,
      returnData,
      isReturn: type.toLowerCase() === 'return',
      cpId,
      splitOrderData,
      incrementIdData,

    });
  } catch (e) {
    global.logError(
      `processSMS Error template : ${template}, incrementId : ${incrementId}, entityId : ${entityId}`,
      e
    );
  }
};
