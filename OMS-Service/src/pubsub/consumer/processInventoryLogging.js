const { saveInventoryLog } = require('../../services/logging.service');

const processInventoryLogging = async (pubSubMessage) => {
  const { data, id } = pubSubMessage;
  try {
    console.log(`[PUB/SUB] Processing Inventory Logging Message ID: ${id}`);

    const dataFromPubSub = JSON.parse(data.toString());
    console.log(`[PUB/SUB] Inventory Logging Message Data: ${JSON.stringify(dataFromPubSub)}`);

    if (!Array.isArray(dataFromPubSub)) {
      throw new Error('Data from Pub/Sub is not an array. Expected an array of messages.');
    }

    for (const msg of dataFromPubSub) {
      console.log(`[PUB/SUB] Processing individual message: ${JSON.stringify(msg)}`);
      await saveInventoryLog(msg);
    }

    pubSubMessage.ack();
    console.log(`[PUB/SUB] Inventory Logging Message ID: ${id} processed and acknowledged.`);
  } catch (error) {

    console.error(`[PUB/SUB] Error processing Inventory Logging Message ID: ${id}`, error);
    global.logError(
      error.message.substring(0, 1000),
      `[PUB/SUB] ~~~Error processing Pub/Sub message ID: ${id}~~~`
    );
    pubSubMessage.nack();
    console.log(`[PUB/SUB] Inventory Logging Message ID: ${id} negatively acknowledged.`);
  }
};

module.exports = {
  processInventoryLogging,
};