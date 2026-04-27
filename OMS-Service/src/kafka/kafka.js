const { Kafka, logLevel } = require('kafkajs');

console.log(`Kafka brokerlist : ${process.env.KAFKA_BROKERLIST}`);
console.log(`Kafka clientid: ${process.env.KAFKA_CLIENT_ID}`);
const kafkaObj = new Kafka({
  clientId: process.env.KAFKA_CLIENT_ID,
  brokers: [`${process.env.KAFKA_BROKERLIST}`],
  logLevel: logLevel.ERROR
});
module.exports = kafkaObj;
