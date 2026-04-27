const { deleteQuote } = require('../helpers/deleteQuote');
const { logInfo, logError } = require('../helpers/utils');
const kafka = require('../helpers/kafka/init');
const axios = require('axios');
const { AUTH_INTERNAL_HEADER_BEARER_TOKEN } = process.env;
const internalAuthToken = AUTH_INTERNAL_HEADER_BEARER_TOKEN?.split(',')?.[0];

// console.log(
//   `Delete customer consumer topic : ${process.env.KAFKA_DELETE_CUSTOMERS_TOPIC}`
// );
// console.log(`Kafka group ID : ${process.env.KAFKA_GROUP_ID}`);

const DeleteCustomerConsumer = async () => {
  try {
    const consumer = kafka.consumer({
      groupId: `${process.env.KAFKA_GROUP_ID}_deleted_customers`,
    });
    await consumer.connect();
    await consumer.subscribe({
      topic: process.env.KAFKA_DELETE_CUSTOMERS_TOPIC,
      fromBeginning: true,
    });

    await consumer.run({
      eachBatchAutoResolve: false,
      eachBatch: async ({
        batch,
        resolveOffset,
        heartbeat,
        isRunning,
        isStale,
      }) => {
        for (const message of batch.messages) {
          if (!isRunning() || isStale()) {
            break;
          }
          try {
            const dataFromKafka = JSON.parse(message.value.toString());
            // logInfo('dataFromKafka DeleteCustomerConsumer', dataFromKafka);
            const status = await deleteQuote({dataFromKafka});
            // logInfo(`deleteQuote response ${status}`);
            customerStatusUpdate(dataFromKafka, status);
          } catch (e) {
            logError(e, "Error processing kafka message delete customer consumer");
          }
          resolveOffset(message.offset);
          await heartbeat();
        }
      },
    });
  } catch (e) {
    logError(e);
  }
};

const customerStatusUpdate = async (dataFromKafka, status) => {
  try {
    const { customerId } = dataFromKafka;
    const request = { customerId, task: 'quote-service', status };
    // logInfo("customerStatusUpdate url", process.env.CUSTOMER_DELETE_STATUS_UPDATE_URL);
    // logInfo("customerStatusUpdate request", request);
    await axios.post(
      process.env.CUSTOMER_DELETE_STATUS_UPDATE_URL,
      request,
      {
        headers: {
          'Content-Type': 'application/json',
          'authorization-token': internalAuthToken,
        },
      }
    );
  } catch (e) {
    logError(e);
  }
};

module.exports = { DeleteCustomerConsumer };
