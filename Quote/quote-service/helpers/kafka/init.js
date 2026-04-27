const { Kafka, logLevel } = require('kafkajs');
// const { logger } = require('../utils');

// logger.info(`Kafka configuration - Brokerlist: ${process.env.KAFKA_BROKERLIST}, ClientID: ${process.env.KAFKA_CLIENT_ID}, Environment: ${process.env.NODE_ENV}`);
const kafkaObj = new Kafka({
  clientId: `${process.env.KAFKA_CLIENT_ID}_${process.env.NODE_ENV}`,
  brokers: [`${process.env.KAFKA_BROKERLIST}`],
  logLevel: logLevel.ERROR,
});
module.exports = kafkaObj;
