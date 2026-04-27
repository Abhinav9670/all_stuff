
const redis = require('redis');

// Initialize Redis client
let client;
// client = redis.createClient({
//   url: `redis://${process.env.GLOBAL_REDIS_URL}:${process.env.GLOBAL_REDIS_PORT}`,
//   password: process.env.GLOBAL_REDIS_AUTH
// });

// client
//   .connect()
//   .then(() => {
//     console.log('Connected to Redis');
//   })
//   .catch(err => {
//     console.error('Connection error: ', err);
//   });

// // Handle connection errors
// client.on('error', err => {
//   console.error('Redis error: ', err);
// });

module.exports = { client };