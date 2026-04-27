const { deleteQuote } = require('../../helpers/deleteQuote');
const { logInfo, logError } = require('../../helpers/utils');
const axios = require('axios');
const { AUTH_INTERNAL_HEADER_BEARER_TOKEN } = process.env;
const internalAuthToken = AUTH_INTERNAL_HEADER_BEARER_TOKEN?.split(',')?.[0];

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

const DeleteCustomerPubSub = async (pubSubMessage) => {
  const { data, id } = pubSubMessage;
  try {
    logInfo(`Mulin<>SP Consumer DeleteCustomerPubSub pubSubMessage id : ${id}`);
    const parsedData = JSON.parse(data.toString());
    // logInfo('dataFromPubSub DeleteCustomerPubSub', parsedData);
    const status = await deleteQuote({ dataFromKafka: parsedData });
    // logInfo(`deleteQuote response ${status}`);
    await customerStatusUpdate(parsedData, status);

    if (status) {
      logInfo('DeleteCustomerPubSub has been completed successfully.');
      pubSubMessage.ack();
    }
  } catch (e) {
    logError(e, 'Error processing pubsub message delete customer');
    pubSubMessage.nack();
  }
};

module.exports = { DeleteCustomerPubSub };