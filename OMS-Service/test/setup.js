// Global test setup file
// This file runs before all tests to set up required environment variables

// Set required environment variables for tests BEFORE any modules are loaded
// This must happen synchronously at the top level
if (!process.env.NODE_ENV) {
  process.env.NODE_ENV = 'test';
}
if (!process.env.MONGODB_URL) {
  process.env.MONGODB_URL = 'mongodb://localhost:27017/test';
}
if (!process.env.PORT) {
  process.env.PORT = '3000';
}
// Set optional MongoDB auth variables to empty strings if not set
if (!process.env.MONGODB_USER) {
  process.env.MONGODB_USER = '';
}
if (!process.env.MONGODB_PASS) {
  process.env.MONGODB_PASS = '';
}
// Set MongoDB database name if not set
if (!process.env.MONGODB_DB) {
  process.env.MONGODB_DB = 'oms';
}
// Set AUTH_INTERNAL_HEADER_BEARER_TOKEN to prevent split errors
if (!process.env.AUTH_INTERNAL_HEADER_BEARER_TOKEN) {
  process.env.AUTH_INTERNAL_HEADER_BEARER_TOKEN = 'test-token';
}
// Disable TLS for local test MongoDB connections (if using localhost)
if (process.env.MONGODB_URL && process.env.MONGODB_URL.includes('localhost')) {
  // TLS will be handled by mongoInit.js based on the URL
}

// Mock dotenv-expand to prevent recursion issues
jest.mock('dotenv-expand', () => jest.fn((env) => env));

// Mock Google Cloud PubSub to prevent credential errors
jest.mock('@google-cloud/pubsub', () => {
  const mockTopic = {
    publishMessage: jest.fn().mockResolvedValue('mock-message-id'),
    publish: jest.fn().mockResolvedValue('mock-message-id')
  };
  
  const mockSubscription = {
    on: jest.fn(),
    removeListener: jest.fn(),
    close: jest.fn()
  };
  
  return {
    PubSub: jest.fn().mockImplementation(() => ({
      topic: jest.fn().mockReturnValue(mockTopic),
      subscription: jest.fn().mockReturnValue(mockSubscription),
      createTopic: jest.fn().mockResolvedValue([mockTopic]),
      createSubscription: jest.fn().mockResolvedValue([mockSubscription])
    }))
  };
});

// Mock PubSub services to prevent Google Cloud credential errors
jest.mock('../src/pubsub/services/pubsubPublisherService', () => ({
  publishMessage: jest.fn().mockResolvedValue('mock-message-id'),
  createSubscription: jest.fn().mockResolvedValue(undefined),
  stopSubscription: jest.fn().mockResolvedValue(undefined)
}));

jest.mock('../src/pubsub/consumer/pubsubListener', () => ({
  spPubsubConsumerSet: jest.fn().mockResolvedValue(undefined)
}));

jest.mock('../src/utils/pubsubconfig', () => ({
  publishMessage: jest.fn().mockResolvedValue('mock-message-id')
}));

// Mock Kafka to prevent connection issues
jest.mock('../src/kafka', () => ({
  kafkaConsumers: jest.fn(),
  producer: {
    send: jest.fn().mockResolvedValue({})
  }
}));

jest.mock('../src/kafkaV2', () => ({
  kafkaConsumersV2: jest.fn(),
  producer: {
    send: jest.fn().mockResolvedValue({})
  }
}));

// Mock consul-watch to prevent connection issues
jest.mock('../src/consul-watch', () => {
  const envMatch = {
    qa: 'qa',
    staging: 'qa',
    production: 'live',
    development: 'dev',
    uat: 'qa01',
    test: 'qa' // Add test environment mapping
  };
  
  return {
    envMatch: envMatch,
    init: jest.fn().mockResolvedValue(undefined),
    watchConsul: jest.fn()
  };
});

// Mock mongoInit to use environment variables but handle test mode gracefully
// Create a jest mock function for getDb that tests can override
const mockGetDbFn = jest.fn();

jest.mock('../src/utils/mongoInit', () => {
  const MongoClient = require('mongodb').MongoClient;
  const logger = require('../src/config/logger');
  
  // Default mock database object
  const defaultMockDb = {
    collection: () => ({
      find: () => ({ toArray: () => Promise.resolve([]) }),
      findOne: () => Promise.resolve(null),
      insertOne: () => Promise.resolve({ insertedId: 'test-id' }),
      updateOne: () => Promise.resolve({ modifiedCount: 0 }),
      deleteOne: () => Promise.resolve({ deletedCount: 0 }),
      command: () => Promise.resolve({ ok: 1, hosts: [] })
    })
  };
  
  // Set default return value for mockGetDbFn
  mockGetDbFn.mockReturnValue(defaultMockDb);
  
  let _db = defaultMockDb;
  
  // Helper function to build MongoDB connection options
  function buildMongoOptions() {
    const options = {
      useUnifiedTopology: true,
      useNewUrlParser: true,
    };
    
    // Only enable TLS for non-localhost connections
    const isNonLocalhost = process.env.MONGODB_URL && !process.env.MONGODB_URL.includes('localhost');
    if (isNonLocalhost) {
      options.tls = true;
      options.tlsInsecure = true;
    }
    
    // Add auth options if user is provided
    const mongoUser = process.env.MONGODB_USER?.trim();
    if (mongoUser) {
      options.authSource = process.env.MONGODB_AUTH_SOURCE || 'oms';
      if (process.env.MONGODB_READ_PREFERENCE) {
        options.readPreference = process.env.MONGODB_READ_PREFERENCE;
      }
      if (process.env.MONGODB_REPLICASET) {
        options.replicaSet = process.env.MONGODB_REPLICASET;
      }
    }
    
    return options;
  }
  
  // Helper function to build MongoDB connection URL with auth
  function buildMongoUrl() {
    const mongoUser = process.env.MONGODB_USER?.trim();
    const authString = mongoUser 
      ? `${mongoUser}:${encodeURIComponent(process.env.MONGODB_PASS || '')}@`
      : '';
    
    const mongourlParts = process.env.MONGODB_URL.split('//');
    return `${mongourlParts[0]}//${authString}${mongourlParts[1]}`;
  }
  
  // Helper function to handle connection success
  function handleConnectionSuccess(client, callback) {
    logger.info('Connected to MongoDB');
    _db = client.db(process.env.MONGODB_DB || 'oms');
    mockGetDbFn.mockReturnValue(_db);
    if (callback) {
      callback(null, _db);
    }
  }
  
  // Helper function to handle connection error
  function handleConnectionError(err, callback) {
    logger.error(`Failed to connect to the database. ${err.stack}`);
    if (global.logError) {
      global.logError(err);
    }
    logger.warn('MongoDB connection failed in test mode, using mock database');
    _db = defaultMockDb;
    if (callback) {
      callback(null, _db);
    }
  }
  
  return {
    connectToServer: function (callback) {
      const options = buildMongoOptions();
      const mongoUrl = buildMongoUrl();
      
      MongoClient.connect(mongoUrl, options, function (err, client) {
        if (err) {
          handleConnectionError(err, callback);
        } else {
          handleConnectionSuccess(client, callback);
        }
      });
    },
    getDb: function () {
      // Return the result of the mock function, or the stored _db
      return mockGetDbFn() || _db;
    }
  };
});

// Export mockGetDbFn so tests can override it
global.mockGetDbFn = mockGetDbFn;

// Auto-mock config module to prevent validation errors
// Tests can override this by calling jest.mock() again in their file
jest.mock('../src/config/config', () => {
  // Ensure env vars are set
  const nodeEnv = process.env.NODE_ENV || 'test';
  const mongodbUrl = process.env.MONGODB_URL || 'mongodb://localhost:27017/test';
  const port = parseInt(process.env.PORT || '3000', 10);
  
  return {
    env: nodeEnv,
    port: port,
    mongo: {
      url: mongodbUrl + (nodeEnv === 'test' ? '-test' : ''),
      options: {
        useCreateIndex: true,
        useNewUrlParser: true,
        useUnifiedTopology: true
      }
    },
    mongoose: {
      url: mongodbUrl + (nodeEnv === 'test' ? '-test' : ''),
      options: {
        useCreateIndex: true,
        useNewUrlParser: true,
        useUnifiedTopology: true
      }
    },
    jwt: {
      secret: process.env.JWT_SECRET || 'test-secret',
      accessExpirationMinutes: process.env.JWT_ACCESS_EXPIRATION_MINUTES || 30,
      refreshExpirationDays: process.env.JWT_REFRESH_EXPIRATION_DAYS || 30,
      resetPasswordExpirationMinutes: 10
    },
    email: {
      smtp: {
        host: process.env.SMTP_HOST || 'localhost',
        port: parseInt(process.env.SMTP_PORT || '587', 10),
        auth: {
          user: process.env.SMTP_USERNAME || '',
          pass: process.env.SMTP_PASSWORD || ''
        }
      },
      from: process.env.EMAIL_FROM || 'test@example.com'
    }
  };
}, { virtual: false });

