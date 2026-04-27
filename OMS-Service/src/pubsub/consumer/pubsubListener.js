const { PubSubService } = require('../services');
const { processInventoryLogging } = require('./processInventoryLogging');
const { processOrderSuccess } = require('./processOrderSuccess');
const { processShipmentUpdate } = require('./processShipmentUpdate');
const { processReturnShipmentUpdate } = require('./processReturnShipmentUpdate');
const { processRefundSmsTrigger } = require('./processRefundSmsTrigger');

console.log(`[PUB SUB] Listening to topic`);

const pubsubConsumerList = [
  {
    subscriptionName: `${process.env.PUB_SUB_LOGGIN_TOPIC_SUB}`, 
    functionName: processInventoryLogging,
  },
  {
    subscriptionName: `${process.env.PUB_SUB_ORDER_SUCCESS_TOPIC_SUB}`,
    functionName: processOrderSuccess,
  },
  {
    subscriptionName: `${process.env.PUB_SUB_SHIPMENT_UPDATE_TOPIC_SUB}`,
    functionName: processShipmentUpdate,
  },
  {
    subscriptionName: `${process.env.PUB_SUB_SHIPMENT_RETURN_UPDATE_TOPIC_SUB}`,
    functionName: processReturnShipmentUpdate,
  },
  {
    subscriptionName: `${process.env.PUB_SUB_REFUND_SMS_TRIGGER_TOPIC_SUB}`,
    functionName: processRefundSmsTrigger,
  },
];

const spPubsubConsumerSet = async () => {
  console.log('[PUB SUB] ConsumerSet:::: Start');
  console.log('[PUB SUB] ConsumerSet - Environment: ', process.env.NODE_ENV);
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