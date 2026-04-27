const { MongoClient } = require('mongodb');

let _db;
let _dbc;

module.exports = {
  async connectToServer(callback) {
    const options = {
      tls: true,
      tlsInsecure: true,
      directConnection: true
    };
    if (process.env.MONGODB_USER) {
      options.auth = {
        username: process.env.MONGODB_USER,
        password: process.env.MONGODB_PASS
      };
      options.authSource = process.env.MONGODB_AUTH_SOURCE;

      if (process.env.MONGODB_READ_PREFERENCE) {
        options.readPreference = process.env.MONGODB_READ_PREFERENCE;
      }

      if (process.env.MONGODB_REPLICASET) {
        options.replicaSet = process.env.MONGODB_REPLICASET;
      }
    }
    try {
      const client = new MongoClient(`${process.env.MONGODB_URL}`, options);
      await client.connect();
      console.log('Connected to MongoDB');
      const dbName = process.env.MONGODB_DB_ADRSMPR || 'adrsmpr';
      _db = client.db(dbName);
      _dbc = client.db(process.env.MONGODB_DB_CUSTOMERMG);
      callback(null, _db);
    } catch (err) {
      console.log(`Failed to connect to the database. ${err.stack}`);
      global.logError(err);
      callback(err);
    }
  },

  getDb() {
    return _db;
  },
  getCustomerMGDB() {
    return _dbc;
  }
};
