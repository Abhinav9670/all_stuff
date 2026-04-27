const kafka = require('./kafka');
const inventoryLogginConsumer = require('./consumer/inventory.logging');
const orderSuccessConsumer = require('./consumer/orderSuccess');
const producer = kafka.producer();
const kafkaConsumers = async () => {
  try {
    await producer.connect();
    console.log('Kafka consumer connected');
    inventoryLogginConsumer();
    orderSuccessConsumer();
  } catch (e) {
    global.logError(e, {
      custom: {
        message: 'KAFKA PRODUCER CONNECTION LOST'
      }
    });
  }
};

module.exports = {
  kafkaConsumers,
  producer
};
