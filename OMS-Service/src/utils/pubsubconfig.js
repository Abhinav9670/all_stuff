const { PubSub } = require('@google-cloud/pubsub');
const EventEmitter = require('events');
const projectId = process.env.GCP_PROJECT_ID;
console.log(`GCP Project ID : ${projectId}`);

const pubSubClient = new PubSub({
    projectId,
});
const eventEmitter = new EventEmitter();


const publishMessage = async (topicName, data) => {
    try {
        const topic = pubSubClient.topic(topicName);
        const messageBuffer = Buffer.from(JSON.stringify(data));
        const messageId = await topic.publishMessage({ data: messageBuffer });
        console.log(`Message ${messageId} published to topic ${topicName}`);
    } catch (error) {
        console.error(`Error publishing message to ${topicName}:`, error);
    }
};

// const subscribe = async (topicKey) => {
//     const subscriptionName = subscriptions[topicKey];
//     if (!subscriptionName) {
//         console.error(`Subscription for topicKey ${topicKey} not found.`);
//         return;
//     }

//     const subscription = pubSubClient.subscription(subscriptionName);

//     subscription.on('message', message => {
//         console.log(`Received message on ${topicKey}:`, message.data.toString());
//         eventEmitter.emit(topicKey, JSON.parse(message.data.toString()));
//         message.ack();
//     });

//     subscription.on('error', error => {
//         console.error(`Error receiving messages for ${topicKey}:`, error);
//     });

//     console.log(`Subscribed to ${topicKey} (${subscriptionName})`);
// };

const on = (topicKey, listener) => {
    eventEmitter.on(topicKey, listener);
};

// Object.keys(subscriptions).forEach(subscribe);

module.exports = { publishMessage, on };
