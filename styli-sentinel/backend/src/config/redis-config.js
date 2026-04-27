const redis = require('redis');

console.log('Redis URL:', process.env.REDIS_URL);
console.log('Redis Port:', process.env.REDIS_PORT);
console.log('Redis Auth:', process.env.REDIS_AUTH);

// Initialize Redis client
let client;
client = redis.createClient({
  url: `redis://${process.env.REDIS_URL}:${process.env.REDIS_PORT}`,
  password: process.env.REDIS_AUTH
});

client
  .connect()
  .then(() => {
    console.log('Connected to Redis');
  })
  .catch(err => {
    console.error('Connection error: ', err);
  });

// Handle connection errors
client.on('error', err => {
  console.error('Redis error: ', err);
});

module.exports = { client };
