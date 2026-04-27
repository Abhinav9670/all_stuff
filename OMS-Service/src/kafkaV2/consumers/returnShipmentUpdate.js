const { handleShipmentUpdates } = require('../../helpers/shipment');
const { logInfo } = require('../../utils');
const kafka2 = require('../kafka');

console.log(
  `Shipment update consumer topic : ${process.env.KAFKA_SHIPMENT_RETURN_UPDATE_TOPIC}`
);
console.log(`Kafka V2 group ID : ${process.env.KAFKA_GROUP_ID}_v3`);

const returnShipmentUpdateConsumer = async () => {
  try {
    const consumer = kafka2.consumer({
      groupId: `${process.env.KAFKA_GROUP_ID}_v3`
    });

    await consumer.connect();
    await consumer.subscribe({
      topic: process.env.KAFKA_SHIPMENT_RETURN_UPDATE_TOPIC,
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
            logInfo(
              `Stopping batch processing: isRunning=${isRunning()}, isStale=${isStale()}`
            );
            break;
          }

          try {
            const messageKey = message.key?.toString() || 'unknown_key';
            const dataFromKafka = JSON.parse(message.value.toString());

            logInfo(`dataFromKafka ShipmentUpdateConsumer - Key: ${messageKey}`, dataFromKafka);
            await handleShipmentUpdates(dataFromKafka);

            resolveOffset(message.offset);
            await consumer.commitOffsets([
              {
                topic: batch.topic,
                partition: batch.partition,
                offset: (Number(message.offset) + 1).toString() 
              }
            ]);
          } catch (e) {
            global.logError(
              `Error processing Kafka message (Key: ${message.key}): ${e.message.substring(0, 1000)}`,
              '~~~ Shipment Update Consumer Error ~~~'
            );
          }

          await heartbeat(); 
        }
      }
    });
  } catch (e) {
    global.logError('Error in returnShipmentUpdateConsumer', e.message);
  }
};

module.exports = returnShipmentUpdateConsumer;
