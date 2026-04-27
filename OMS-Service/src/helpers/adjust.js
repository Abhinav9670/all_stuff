// const axios = require('axios');
const { ORDER_SORCE_MAP } = require('../constants/order');
const { logInfo } = require('../utils');

const logAdjustEvent = async (request = {}) => {
  logInfo('skipping logAdjustEvent', 'skipping logAdjustEvent');
  // logInfo('adjust tap request ', request);
  // try {
  //   const { endpoint, appToken } =
  //     global?.baseConfig?.extrenalApis?.adjust || {};

  //   const { eventToken, eventData = {} } = request;
  //   const deviceId = '28BFA785-63D2-4F7A-B563-D0E5C0554AB4';

  //   const adjustUrl = `${endpoint}&event_token=${eventToken}&app_token=${appToken}&adid=${deviceId}&gps_adid=${deviceId}&idfa=${deviceId}&callback_params=${JSON.stringify(
  //     eventData
  //   )}`;
  //   if (adjustUrl || endpoint) {
  //     const response = await axios.get(adjustUrl, {
  //       headers: {
  //         'Content-Type': 'application/x-www-form-urlencoded'
  //       }
  //     });
  //     logInfo('Adjust tap response ', response);
  //   }
  // } catch (e) {
  //   global.logError(
  //     e,
  //     `Error adjustDeliveredEvent req : ${JSON.stringify(request || {})}`
  //   );
  // }
};

exports.adjustDeliveredEvent = async ({ orderData }) => {
  try {
    const {
      entity_id,
      customer_id,
      increment_id,
      // customer_email,
      shippingAddress,
      source,
      subSales
    } = orderData;
    logInfo(
      `Adjust Request  Entity for Delivery - ${increment_id} }`
    );
    if (entity_id && customer_id) {
      const { delivered_event_token } =
        global?.baseConfig?.extrenalApis?.adjust || {};
      const deviceId = subSales?.device_id || '';
      const eventObject = {
        eventName: 'order_delivered',
        eventToken: delivered_event_token,
        deviceId,
        eventData: {
          order_id: `${increment_id}`,
          user_id: `${customer_id}`,
          // guest_id: '',
          country: shippingAddress?.country_id,
          platform: ORDER_SORCE_MAP?.[source]
        }
      };

      await logAdjustEvent(eventObject);
      logInfo(
        `Adjust Request  Entity for Delivery Ended - ${increment_id} }`
      );
    }
  } catch (e) {
    global.logError(`Error in adjust adjustDeliveredEvent, ${e.message? JSON.stringify(e.message):''}, ${e}`);
  }
};
