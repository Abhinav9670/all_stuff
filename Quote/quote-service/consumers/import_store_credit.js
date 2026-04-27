const { Kafka } = require('kafkajs');
const { logError, logInfo } = require('../helpers/utils');
const quote_controller = require('../controllers/quoteController');

const kafka = new Kafka({
    clientId: 'store-credit',
    brokers: [`${process.env.KAFKA_BROKERLIST}`]
})

const StoreCreditConsumer = async () => {
    const consumer = kafka.consumer({ groupId: 'store-credit7' })
    await consumer.connect()
    await consumer.subscribe({ topic: `${process.env.STORE_CREDIT_TOPIC}`, fromBeginning: true });

    await consumer.run({
        eachBatchAutoResolve: false,
        eachBatch: async ({ batch, resolveOffset, heartbeat, isRunning, isStale }) => {
            for (let message of batch.messages) {
                if (!isRunning() || isStale()) break

                try {
                    const dataFromKafka = JSON.parse(message.value.toString()).body[0];
                    if(dataFromKafka) {
                        const customerId = dataFromKafka.customer_id;
                        const key = 'store_credit_' + customerId
                        quote_controller.storeDataFromKafka({ dataFromKafka, key });
                    }
                    // logInfo('Customer Store Credit', dataFromKafka);
                } catch (e) {
                    logError(e.message.substring(0, 1000), 'Error processing store credit');
                    await processFailedMessages(message.value, e, producer);
                }

                resolveOffset(message.offset)
                await heartbeat()
            }
        }
    })
}

exports.StoreCreditConsumer = StoreCreditConsumer;