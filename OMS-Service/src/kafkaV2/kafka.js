const { Kafka, logLevel } = require('kafkajs');

console.log(`KafkaV2 brokerlist : ${process.env.KAFKA_V2_BROKERLIST}`);
console.log(`KafkaV2 clientid: ${process.env.KAFKA_CLIENT_ID}_v2`);
const kafkaObjV2 = new Kafka({
  clientId: `${process.env.KAFKA_CLIENT_ID}_v2`,
  brokers: [`${process.env.KAFKA_V2_BROKERLIST}`],
  logLevel: logLevel.ERROR
});
module.exports = kafkaObjV2;
