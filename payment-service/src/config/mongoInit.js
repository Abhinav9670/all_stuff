const { MongoClient } = require('mongodb');

let _db;

module.exports = {
  connectToServer(callback) {
    const options = {
      useUnifiedTopology: true,
      useNewUrlParser: true,
      tls: true,
      tlsInsecure: true
    };
    if (process.env.MONGODB_USER) {
      options.auth = {
        user: process.env.MONGODB_USER,
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
    MongoClient.connect(process.env.MONGODB_URL, options, (err, client) => {
      if (err) {
        console.log(`Failed to connect to the database. ${err.stack}`);
        global.logError(err);
        return undefined;
        // process.exit(1);
      } else {
        console.log('Connected to MongoDB');
        _db = client.db(process.env.MONGODB_DB);
        return callback(err, _db);
      }
    });
  },

  getDb() {
    return _db;
  }
};
