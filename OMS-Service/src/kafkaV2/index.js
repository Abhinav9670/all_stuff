const kafka2 = require('./kafka');
const producerV2 = kafka2.producer();
const ShipmentUpdateConsumer = require('./consumers/shipmentUpdate');
const returnShipmentUpdateConsumer = require('./consumers/returnShipmentUpdate');

const kafkaConsumersV2 = async () => {
  try {
    await producerV2.connect();
    console.log('Kafka V2 producer connected');
    ShipmentUpdateConsumer();
    returnShipmentUpdateConsumer();
  } catch (e) {
    global.logError(e, {
      custom: {
        message: 'KAFKA V2 PRODUCER CONNECTION LOST'
      }
    });
  }
};

module.exports = { kafkaConsumersV2, producerV2 };
