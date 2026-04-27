const PubSubService = require('./pubsubService');
const { DeleteCustomerPubSub } = require('./processDeleteCustomers'); 
const { PriceDropPubSub } = require('./processPriceDropSkus'); 

console.log(`[PUB SUB] Listening to topics`);
console.log('PUBSUB_DELETE_CUSTOMERS_SUBSCRIPTION', process.env.PUBSUB_DELETE_CUSTOMERS_SUBSCRIPTION);
console.log('PUBSUB_PRICE_DROP_SUBSCRIPTION', process.env.PUBSUB_PRICE_DROP_SUBSCRIPTION);

const pubsubConsumerList = [
  {
    subscriptionName: process.env.PUBSUB_DELETE_CUSTOMERS_SUBSCRIPTION,
    functionName: DeleteCustomerPubSub, 
  },
  {
    subscriptionName: process.env.PUBSUB_PRICE_DROP_SUBSCRIPTION, 
    functionName: PriceDropPubSub, 
  }
];

const spPubsubConsumerSet = async () => {
  console.log('[PUB SUB] ConsumerSet:::: Start');
  console.log('[PUB SUB] ConsumerSet - Environment: ', process.env.STYLI_ENV);
  try {
    pubsubConsumerList.forEach((data) => {
      PubSubService.createSubscription(
        data.subscriptionName,
        data.functionName
      );
    });
  } catch (e) {
    global.logError(e, {
      custom: {
        message: 'PUB SUB PRODUCER CONNECTION LOST',
      },
    });
  }
};

module.exports = {
  spPubsubConsumerSet,
};