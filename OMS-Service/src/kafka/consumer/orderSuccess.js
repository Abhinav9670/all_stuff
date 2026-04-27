const emailObj = require('../../helpers/email')
const { processSMS } = require('../../helpers/processSms');
const { splitOrderDataFetch } = require('../../utils/splitOrderUtils');
const kafka = require('../kafka');

console.log(`Kafka Logging topic : ${process.env.KAFKA_LOGGIN_TOPIC}`);
console.log(`Kafka group ID : ${process.env.KAFKA_GROUP_ID}`);
const orderSuccessConsumer = async () => {
  try {
    const consumer = kafka.consumer({
      groupId: process.env.KAFKA_GROUP_ID
    });
    await consumer.connect();
    await consumer.subscribe({
      topic: process.env.KAFKA_ORDER_SUCCESS_TOPIC,
      fromBeginning: true
    });

    await consumer.run({
      eachBatchAutoResolve: false,
      eachBatch: async ({
        batch,
        resolveOffset,
        heartbeat,
        isRunning,
        isStale
      }) => {
        for (const message of batch.messages) {
          if (!isRunning() || isStale()) {
            break;
          }
          try {
            const dataFromKafka = JSON.parse(message.value.toString());
            let orderId = dataFromKafka?.orderid;
            if (typeof dataFromKafka === 'string') {
              orderId = JSON.parse(dataFromKafka)?.orderid;
            }
            console.log({ m: message.value, dataFromKafka, orderId });
            console.log(`ORDER SUCCESS MESSAGE SERVICE STARTS NOW`)
            const {splitOrderValues, dataVal}  = await splitOrderDataFetch(orderId) || {};   //fetching split order data
            console.log(`ORDER ${orderId} VALUES IN SUCCESS EMAIL TEMPLATE DATAVAL ${JSON.stringify(dataVal)}#####Split values${JSON.stringify(splitOrderValues)}`)
            let successTemplate = dataVal.length === 0 ? 'order_place_success' : 'order_place_success_split_order';
            let typeTemplate = dataVal.length === 0 ? 'orderConfirm' : 'orderConfirm_split';
            console.log(`ORDER ${orderId} and its TEMPLATE VALUES FOR SMS ${successTemplate} AND EMAIL ${typeTemplate}`);
           emailObj.sendEmail({
              type : typeTemplate,
              orderId,
              splitOrderData : splitOrderValues
            });
            processSMS({
              type: 'order',
              template: successTemplate,
              entityId: orderId,
              updateCleverTap: true,
              splitOrderData : splitOrderValues
            });
            // for (const msg of dataFromKafka) {
            //   console.log(
            //     `Kafka Incoming Message Order success: ${JSON.stringify(msg)}`
            //   );
            // }
          } catch (e) {
            global.logError(
              e.message.substring(0, 1000),
              ' ~~~Error processing kafka message Order success consumer ~~~ '
            );
          }
          resolveOffset(message.offset);
          await heartbeat();
        }
      }
    });
  } catch (e) {
    global.logError(e);
  }
};

module.exports = orderSuccessConsumer;
