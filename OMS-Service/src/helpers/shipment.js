const {
  getCreditMemoDocument,
  processCreditmemoEmail
} = require('../services/creditmemo.service');
const { logInfo } = require('../utils');
const { updateFwdShipment } = require('./forwardShipment');
const rs = require('./reverseShipment');
const EMAIL_ERROR_CREDITMEMO = 'Error sending creditmemo email.';

exports.handleShipmentUpdates = async (dataFromKafka = {}) => {
  // console.log('in handleShipmentUpdates', dataFromKafka);
  const request = dataFromKafka;
  let finalResponse = {};
  try {
    // Check if this is the new payload format and map if needed
    const processedData = dataFromKafka;

    const {
      waybill,
      additional = {},
      rtoAwb,
      status: reqStatus,
      cp_id = ''
    } = processedData;
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
      response = await rs.updateRevShipment({
        rmaIncrementId: increment_id,
        remark,
        notificationId,
        waybill,
        timestamp,
        items,
        cp_id,
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
        reqStatus,
        cp_id
      });
    }

    finalResponse = response;

    const { smsResponse } = response || {};
    // Assuming smsResponse?.creditmemoEntityId is returned only in case of return shipment update
    if (smsResponse?.creditmemoEntityId && smsResponse?.sms) {
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
          orderIncrementId: smsResponse.orderIncrementId
        });
      } catch (ex) {
        logInfo(EMAIL_ERROR_CREDITMEMO);
        global.logError(ex);
        finalResponse.emailSmsError = ex?.message;
      }
    }

    if (!response.status)
      logInfo('handleShipmentUpdates failed', response.errorMsg);
  } catch (e) {
    global.logError(e);
    finalResponse.error = e?.message;
  }

  logInfo('handleShipmentUpdates LOGS', { request, finalResponse });
};
