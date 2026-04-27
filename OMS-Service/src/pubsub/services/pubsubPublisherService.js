const { PubSub } = require('@google-cloud/pubsub');

class PubSubService {
  constructor() {
    const projectId =
      process.env.NODE_ENV === 'production' ? 'stylishopprod' : 'stylishopdev';
    this.pubsub = new PubSub({
      projectId: projectId
    });
    this.subscriptions = {};
  }

  async publishMessage(topicName, data) {
    console.log("topicName", topicName);
    console.log("data", data);
    const dataBuffer = Buffer.from(JSON.stringify(data));
    const messageId = await this.pubsub
      .topic(topicName)
      .publishMessage({ data: dataBuffer });
    console.log(`Message ${messageId} published to topic ${topicName}.`);
    return messageId;
  }

  async createSubscription(subscriptionName, messageHandler) {
    const subscription = this.pubsub.subscription(subscriptionName);
    subscription.on('message', messageHandler);
    this.subscriptions[subscriptionName] = {
      subscription,
      messageHandler
    };
  }

  async stopSubscription(subscriptionName) {
    if (this.subscriptions[subscriptionName]) {
      const { subscription, messageHandler } =
        this.subscriptions[subscriptionName];
      await subscription.removeListener('message', messageHandler);
      delete this.subscriptions[subscriptionName];
      console.log(`Subscription ${subscriptionName} stopped.`);
    } else {
      console.log(`Subscription ${subscriptionName} does not exist.`);
    }
  }
}

const pubSubInstance = new PubSubService();

module.exports = pubSubInstance;