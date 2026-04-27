const { PubSub } = require('@google-cloud/pubsub');

// Initialize Pub/Sub client
const pubSubInit = async () => {
  const pubSubProjectId = process.env.STYLI_ENV === 'live' ? 'stylishopprod' : 'stylishopdev';
  // console.log('pubsubProjectId--', pubSubProjectId, 'process.env.STYLI_ENV', process.env.STYLI_ENV, 'STYLI_ENV', process.env.STYLI_ENV === 'live');
  let pubSubClient;
  // console.log(process.env.PUBSUB_DETAILS, '--  pubsub --', process.env.STYLI_ENV, '--  pubsub --', pubSubProjectId);
  try {
    pubSubClient = new PubSub({
      projectId: pubSubProjectId
      // credentials: credentials,
    });
    // console.log('PubSub client initialized Successfully');
    return pubSubClient;
  } catch (e) {
    // console.error('Error initializing PubSub client:', e.message);
    throw e;
  }
};

// Publish message function
const pubSubProducer = async (data,topicName) => {

  try {
    // Initialize Pub/Sub client
    const pubSubClient = await pubSubInit();
    const dataBuffer = Buffer.from(JSON.stringify(data));
    // console.log('Readable Data:', dataBuffer.toString('utf-8'));
    // console.log(`Using Pub/Sub topic name: ${topicName}`);

    // Verify topic existence
    const topic = pubSubClient.topic(topicName);
    const [topicExists] = await topic.exists();
    if (!topicExists) {
      throw new Error(`Topic ${topicName} does not exist`);
    }
    // console.log(`Topic ${topicName} exists.`);

    // Publish message
    // console.log('Publishing data to Pub/Sub topic:', topicName);
    const messageId = await topic.publishMessage({ data: dataBuffer });
    // console.log(`Message published with ID: ${messageId}`);
    return messageId;
  } catch (error) {
    // console.error('Error publishing message:', error.message);
    throw error; 
  }
};

module.exports = {
  pubSubProducer,
};