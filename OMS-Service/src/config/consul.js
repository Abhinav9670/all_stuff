const consul = require('consul');

const consulObj = consul({
  host: process.env.CONSUL_HOST,
  port: process.env.CONSUL_PORT,
  promisify: true
});

module.exports = { consulObj };
