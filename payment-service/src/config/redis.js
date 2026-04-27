const Redis = require('ioredis');

let redis;
// maxRetriesPerRequest = null means every command will wait forever until the connection is alive again.
const redisOptions = {
  maxRetriesPerRequest: 0,
  showFriendlyErrorStack: true,
  retryStrategy(times) {
    const time = Math.min(times * 50, 2000);
    console.log('Retrying Redis after seconds: ' + time);
    return time;
  }
};

console.log('REDIS CONFIG: ', redisOptions, process.env.REDIS_PORT, process.env.REDIS_HOST, process.env.REDIS_PASS);

if (process.env.REDIS_HOST) {
  redis = new Redis({
    ...redisOptions,
    port: process.env.REDIS_PORT,
    host: process.env.REDIS_HOST,
    password: process.env.REDIS_AUTH_KEY
  });
} else {
  redis = new Redis(redisOptions);
}

module.exports = redis;
