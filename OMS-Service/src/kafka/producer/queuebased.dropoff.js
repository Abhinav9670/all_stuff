const { producer } = require('../index');

const queueBasedDropOff = async (returnIncrementId, orderId) => {
  const respose = await producer.send({
    topic: `${process.env.QUEUE_BASED_REFUND_DROPOFF}`,
    messages: [
      {
        key: 'refundData',
        value: JSON.stringify({ returnIncrementId, orderId })
      }
    ]
  });
  console.log('response   ', respose);
};

module.exports = {
  queueBasedDropOff
};
