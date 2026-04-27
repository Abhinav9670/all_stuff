const { saveInventoryLog } = require('../../services/logging.service');
const kafka = require('../kafka');

console.log(`Kafka Logging topic : ${process.env.KAFKA_LOGGIN_TOPIC}`);
console.log(`Kafka group ID : ${process.env.KAFKA_INVENTORY_GROUP_ID}`);
const inventoryLogginConsumer = async () => {
  try {
    const consumer = kafka.consumer({
      groupId: process.env.KAFKA_INVENTORY_GROUP_ID
    });
    await consumer.connect();
    await consumer.subscribe({
      topic: process.env.KAFKA_LOGGIN_TOPIC,
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
            console.log(
              `Inventory Logging Message: ${JSON.stringify(dataFromKafka)}`
            );
            for (const msg of dataFromKafka) {
              await saveInventoryLog(msg);
            }
          } catch (e) {
            global.logError(
              e.message.substring(0, 1000),
              ' ~~~Error processing kafka message ~~~ '
            );
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

module.exports = inventoryLogginConsumer;
