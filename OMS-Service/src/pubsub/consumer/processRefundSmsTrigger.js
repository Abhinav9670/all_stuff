const { getRmaShipmentDetail } = require('../../helpers/rma');
const { logInfo } = require('../../utils');
const smsObj = require('../../helpers/sms');
const { processCreditMemo } = require('../../controllers/rest.controller');

const processRefundSmsTrigger = async (pubSubMessage) => {
  const { data, id } = pubSubMessage;

  try {
    console.log(`[PUB/SUB] Processing Refund SMS Trigger Message ID: ${id}`);

    const dataFromPubSub = JSON.parse(data.toString());
    logInfo('[PUB/SUB] Refund SMS Trigger Message Data:', dataFromPubSub);

    let returnIncrementId = dataFromPubSub?.returnIncrementId;
    let smsStatus = dataFromPubSub?.smsStatus;
    let timestamp = dataFromPubSub?.timestamp;
    let orderIncrementId = dataFromPubSub?.orderIncrementId;

    if (typeof dataFromPubSub === 'string') {
      const parsedData = JSON.parse(dataFromPubSub);
      returnIncrementId = parsedData?.returnIncrementId;
      smsStatus = parsedData?.smsStatus;
      timestamp = parsedData?.timestamp;
      orderIncrementId = parsedData?.orderIncrementId;
    }

    console.log(
      `[PUB/SUB] Extracted RETURN INCREMENT ID: ${returnIncrementId} | SMS STATUS: ${smsStatus}`
    );

    if (!returnIncrementId) {
      console.warn(`[PUB/SUB] Missing returnIncrementId. Message will be acknowledged and skipped.`);
      pubSubMessage.ack();
      return;
    }

    // ✅ Fetch RMA Shipment Detail
    const response = await getRmaShipmentDetail({ rmaIncrementId: returnIncrementId });
    let returnData = response?.data || {};

    if (!returnData) {
      console.warn(`[PUB/SUB] RETURN DATA NOT FOUND FOR: ${returnIncrementId}.`);
      pubSubMessage.ack();
      return;
    }
    if (!returnData?.creditMemo) {
      console.log("creditMemo not found. Retrying up to 3 times (every 10 seconds)...");

      for (let attempt = 1; attempt <= 3; attempt++) {
        await new Promise((resolve) => setTimeout(resolve, 10000)); // wait 10 seconds
        const newResponse = await getRmaShipmentDetail({ rmaIncrementId: returnIncrementId });
        returnData = newResponse?.data;

        console.log(
          `[Attempt ${attempt}] CREDIT MEMO DATA FOR REFUND SMS TRIGGER: ${JSON.stringify(returnData)}`
        );

        if (returnData?.creditMemo) break; // stop retrying if found
      }
    }

    const smsResponse = await smsObj.sendSMS({
      smsType: smsStatus,
      returnData,
      isReturn: true,
      timestamp,
      refundTrigger: true,
    });

    const processCreditMemoResponse = { status: true, smsStatus, smsResponse };
    await processCreditMemo({
      response: processCreditMemoResponse,
      increment_id: returnIncrementId,
    });

    console.log(`[PUB/SUB] Refund SMS Trigger Message ID: ${id} processed successfully.`);
    pubSubMessage.ack(); // mark done ✅
  } catch (error) {
    console.error(`[PUB/SUB] Error processing Refund SMS Trigger Message ID: ${id}`, error);
    global.logError(
      error.message.substring(0, 1000),
      `[PUB/SUB] ~~~Error processing Pub/Sub message ID: ${id} for Refund SMS Trigger~~~`
    );

    // ✅ Even if error — acknowledge so it doesn’t retry
    pubSubMessage.ack();
    console.log(`[PUB/SUB] Refund SMS Trigger Message ID: ${id} acknowledged after error.`);
  }
};

module.exports = {
  processRefundSmsTrigger,
};