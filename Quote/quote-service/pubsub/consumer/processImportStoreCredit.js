const { logError, logInfo } = require('../../helpers/utils');
const quote_controller = require('../../controllers/quoteController');

const StoreCreditPubSub = async (pubSubMessage) => {
    const { data, id } = pubSubMessage;
    try {
        logInfo(`Mulin<>SP Consumer StoreCreditPubSub pubSubMessage id : ${id}`);
        const dataFromPubSub = JSON.parse(data.toString());
        // logInfo('dataFromPubSub StoreCreditPubSub', dataFromPubSub);

        if (dataFromPubSub?.body && dataFromPubSub.body.length > 0) {
            const dataFromKafka = dataFromPubSub.body[0];
            if (dataFromKafka) {
                const customerId = dataFromKafka.customer_id;
                const key = 'store_credit_' + customerId;
                await quote_controller.storeDataFromKafka({ dataFromKafka, key });
                // logInfo('Customer Store Credit', dataFromKafka);
                logInfo('StoreCreditPubSub has been completed successfully.');
                pubSubMessage.ack();
            } else {
                logInfo('No data found in the pubsub message body.');
                pubSubMessage.ack();
            }
        } else {
            logInfo('No body found in the pubsub message.');
            pubSubMessage.ack();
        }
    } catch (e) {
        logError(e.message.substring(0, 1000), 'Error processing store credit pubsub message');
        pubSubMessage.nack();
    }
};

module.exports = { StoreCreditPubSub };