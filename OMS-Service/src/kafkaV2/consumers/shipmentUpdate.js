const { handleShipmentUpdates } = require('../../helpers/shipment');
const { logInfo } = require('../../utils');
const kafka2 = require('../kafka');

console.log(
  `Shipment update consumer topic : ${process.env.KAFKA_SHIPMENT_UPDATE_TOPIC}`
);
console.log(`Kafka V2 group ID : ${process.env.KAFKA_GROUP_ID}_v2`);

const ShipmentUpdateConsumer = async () => {
  try {
    const consumer = kafka2.consumer({
      groupId: `${process.env.KAFKA_GROUP_ID}_v2`
    });
    await consumer.connect();
    await consumer.subscribe({
      topic: process.env.KAFKA_SHIPMENT_UPDATE_TOPIC,
      fromBeginning: true
    });

    await consumer.run({
      eachBatchAutoResolve: false,
      eachBatch: async ({
        batch,
        resolveOffset,
        heartbeat,
        isRunning,
        isStale
      }) => {
        for (const message of batch.messages) {
          if (!isRunning() || isStale()) {
            break;
          }
          try {
            const dataFromKafka = JSON.parse(message.value.toString());
            logInfo('dataFromKafka ShipmentUpdateConsumer', dataFromKafka);
            await handleShipmentUpdates(dataFromKafka);
          } catch (e) {
            global.logError(
              e.message.substring(0, 1000),
              ' ~~~Error processing kafka message shipment update consumer ~~~ '
            );
            resolveOffset(message.offset);
          }
          resolveOffset(message.offset);
          await heartbeat();
        }
      }
    });
  } catch (e) {
    global.logError(e);
  }
};

module.exports = ShipmentUpdateConsumer;
