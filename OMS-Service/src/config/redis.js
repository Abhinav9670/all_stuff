const redis = require('redis');

console.log('Redis URL:', process.env.GLOBAL_REDIS_HOST, 'Redis Port:', process.env.GLOBAL_REDIS_PORT, 'Redis Auth:', process.env.GLOBAL_REDIS_AUTH);

// Initialize Redis client
let client;
client = redis.createClient({
  url: `redis://${process.env.GLOBAL_REDIS_HOST}:${process.env.GLOBAL_REDIS_PORT}`,
  password: process.env.GLOBAL_REDIS_AUTH
});


client
  .connect()
  .then(() => {
    global.logInfo('Connected to Redis');
  })
  .catch(err => {
    global.logError('Connection error: ', err);
  });

// Handle connection errors
client.on('error', err => {
  global.logError('Redis error: ', err);
});

module.exports = { client };