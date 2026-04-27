const MongoClient = require('mongodb').MongoClient;

const logger = require('../config/logger');

let _db;

module.exports = {
  connectToServer: function (callback) {

    let options = {
      useUnifiedTopology: true,
      useNewUrlParser: true,
      // directConnection: true,
      tls: true,
      tlsInsecure: true
    };
    if (process.env.MONGODB_USER) {

      options.authSource = process.env.MONGODB_AUTH_SOURCE || 'oms';

      if (process.env.MONGODB_READ_PREFERENCE) {
        options.readPreference = process.env.MONGODB_READ_PREFERENCE;
      }

      if (process.env.MONGODB_REPLICASET) {
        options.replicaSet = process.env.MONGODB_REPLICASET;
      }
    }
    let authString = '';
    if (process.env.MONGODB_USER.trim()) {
      authString = `${process.env.MONGODB_USER}:${encodeURIComponent(process.env.MONGODB_PASS)}@`;
    }
    const mongourlParts = process.env.MONGODB_URL.split('//');
    const mongoUrl = `${mongourlParts[0]}//${authString}${mongourlParts[1]}`;
    console.log(mongoUrl);
    MongoClient.connect(mongoUrl, options, function (
      err,
      client
    ) {
      if (err) {
        logger.error(`Failed to connect to the database. ${err.stack}`);
        global.logError(err);
        process.exit(1);
      } else {
        logger.info('Connected to MongoDB');
        _db = client.db(process.env.MONGODB_DB || 'oms');
        if (process.env.NODE_ENV !== 'test') {
          return callback(err, _db);
        }
      }
    });
  },

  getDb: function () {
    return _db;
  }
};