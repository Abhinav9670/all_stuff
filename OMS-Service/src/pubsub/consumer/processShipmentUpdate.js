const { handleShipmentUpdates } = require('../../helpers/shipment');
const { logInfo } = require('../../utils');

const processShipmentUpdate = async (pubSubMessage) => {
  const { data, id } = pubSubMessage;
  try {
    console.log(`[PUB/SUB] Processing Shipment Update Message ID: ${id}`);

    const dataFromPubSub = JSON.parse(data.toString());
    logInfo('[PUB/SUB] Shipment Update Message Data:', dataFromPubSub);

    await handleShipmentUpdates(dataFromPubSub);

    pubSubMessage.ack();
    console.log(`[PUB/SUB] Shipment Update Message ID: ${id} processed and acknowledged.`);
  } catch (error) {
    console.error(`[PUB/SUB] Error processing Shipment Update Message ID: ${id}`, error);
    global.logError(
      error.message.substring(0, 1000),
      `[PUB/SUB] ~~~Error processing Pub/Sub message ID: ${id} for Shipment Update~~~`
    );
    pubSubMessage.nack();
    console.log(`[PUB/SUB] Shipment Update Message ID: ${id} negatively acknowledged.`);
  }
};

module.exports = {
  processShipmentUpdate,
};
