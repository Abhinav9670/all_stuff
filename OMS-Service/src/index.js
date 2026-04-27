const app = require('./app');
const config = require('./config/config');
const logger = require('./config/logger');

// Print Seller Central topic at startup (before any function uses it)
const SELLER_CENTRAL_ORDER_TOPIC = process.env.SELLER_CENTRAL_ORDER_TOPIC || 'seller-central-create-order-production';
logger.info(`[Startup] SELLER_CENTRAL_ORDER_TOPIC env: ${process.env.SELLER_CENTRAL_ORDER_TOPIC ?? 'undefined'}, resolved: ${SELLER_CENTRAL_ORDER_TOPIC}`);


const { kafkaConsumers } = require('./kafka');
kafkaConsumers();
const { kafkaConsumersV2 } = require('./kafkaV2');
kafkaConsumersV2();

const { spPubsubConsumerSet } = require('./pubsub/consumer/pubsubListener');

const mongoUtil = require('./utils/mongoInit');

let server;
mongoUtil.connectToServer(async function (err, client) {
  if (err) {
    logger.error('Failed to connect to MongoDB:', err);
    process.exit(1);
  }
  
  app.db = client;
  
  // Initialize Pub/Sub consumers after MongoDB connection is established
  try {
    await spPubsubConsumerSet();
    logger.info('Pub/Sub consumers initialized successfully');
  } catch (pubsubError) {
    logger.error('Failed to initialize Pub/Sub consumers:', pubsubError);
    // Don't exit the process for Pub/Sub errors, just log them
  }
  
  server = app.listen(config.port, () => {
    logger.info(`Listening to port ${config.port}`);
  });
});

const exitHandler = () => {
  if (server) {
    server.close(() => {
      logger.info('Server closed');
      process.exit(1);
    });
  } else {
    process.exit(1);
  }
};

const unexpectedErrorHandler = error => {
  logger.error(error);
  global.logError(`unhandled Exception error : ${error?.message}`, error);
  exitHandler();
};

process.on('uncaughtException', unexpectedErrorHandler);
process.on('unhandledRejection', unexpectedErrorHandler);
 
process.on('SIGTERM', async() => {
  logger.info('SIGTERM received');
  if (server) {
    server.close();
  }
});