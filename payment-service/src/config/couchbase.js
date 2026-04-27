const couchbase = require('couchbase');
console.log('Couchbase address : ', process.env.COUCHBASE_CLUSTER_ADDRESS);
console.log('Couchbase bucket : ', process.env.COUCHBASE_CLUSTER_BUCKET);
let connection;
const initConnection = async () => {
  try {
    connection = await couchbase.connect(process.env.COUCHBASE_CLUSTER_ADDRESS, {
      username: process.env.COUCHBASE_CLUSTER_USERNAME,
      password: process.env.COUCHBASE_CLUSTER_PASSWORD
    });
    console.log('Couchbase connection established!');
    return connection;
  } catch (error) {
    console.log('Error in establishing couchbase connection.');
  }
};

const customerBucket = async () => {
  return connection.bucket(process.env.COUCHBASE_CLUSTER_BUCKET);
};

const customerCollection = async () => {
  const bucket = await customerBucket();
  return bucket.defaultCollection();
};
initConnection();
module.exports = { initConnection, customerCollection };
