const mongoose = require('mongoose');

let authString = '';
if (process.env.MONGODB_USERNAME.trim()) {
  authString = `${process.env.MONGODB_USERNAME}:${encodeURIComponent(process.env.MONGODB_PASSWORD)}@`;
}
let dbOptions = {
  useNewUrlParser: true,
  useUnifiedTopology: true,
};

if (process.env.MONGODB_AUTH_DB != '') {
  dbOptions = {
    useNewUrlParser: true,
    useUnifiedTopology: true,
    authSource: process.env.MONGODB_AUTH_DB,
    tls: true,
    tlsInsecure: true
  };
}

if (process.env.MONGO_DB_OPTIONS) {
  dbOptions.authMechanism = 'SCRAM-SHA-256';
  dbOptions.readPreference = 'secondaryPreferred';
  dbOptions.replicaSet = 'ReplicaSet';
}
// Mongo Atlas
// const uri = `mongodb+srv://${authString}${process.env.MONGODB_URL}/${process.env.MONGODB_DB}`;
// Mongo Local
// const uri = `mongodb://${authString}${process.env.MONGODB_URL}/${process.env.MONGODB_DB}?directConnection=true`;
// Mongo Server
const uri = `mongodb://${authString}${process.env.MONGODB_URL}/${process.env.MONGODB_DB}`;
console.log(`Mongo URI :: ${uri}`);
mongoose.connect(uri, dbOptions).then(() => { console.log('mongo db connnected') }).catch(err => console.log("mongopp", err));
