const { logInfo, logError } = require('../../helpers/utils');
const {customerStatusUpdate} = require('../../consumers/priceDropSkus');

const PriceDropPubSub = async (pubSubMessage) => {
  const { data, id } = pubSubMessage;
  try {
    logInfo(`Mulin<>SP Consumer PriceDropPubSub pubSubMessage id : ${id}`);
    const dataFromPubSub = JSON.parse(data.toString());
    // logInfo('dataFromPubSub PriceDropPubSub', dataFromPubSub);

    if (dataFromPubSub?.skus) {
      const rawData = dataFromPubSub.skus;
      await customerStatusUpdate(rawData);
      logInfo('PriceDropPubSub has been completed successfully.');
      pubSubMessage.ack();
    } else {
      logInfo('No skus found in the pubSub message.');
      pubSubMessage.ack();
    }
  } catch (e) {
    logError(e, 'Error processing pubsub message for price drop');
    pubSubMessage.nack();
  }
};



module.exports = { PriceDropPubSub };