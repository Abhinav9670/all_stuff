const couchbase = require('couchbase');
// const { logger } = require('../helpers/utils');

let cluster = null; // Singleton cluster instance
let initPromise = null; // Lock to prevent race conditions during initialization
let isClusterHealthy = false; // Track if cluster is healthy to avoid unnecessary reconnections

// Cluster Initialization Logic (with Lazy Initialization and Retry)
const initcluster = async (retries = 10, delay = 1000) => {
  if (cluster && isClusterHealthy) return cluster; // Return active cluster if healthy

  if (initPromise) {
    // logger.info('Couchbase cluster is already initializing, waiting for completion...');
    return initPromise; // Wait for the first initialization to complete
  }

  // Start the initialization and store the promise to prevent multiple attempts
  initPromise = (async () => {
    for (let i = 0; i < retries; i++) {
      try {
        // logger.info(`Couchbase connection attempt ${i + 1}/${retries} to ${process.env.COUCHBASE_CLUSTER_ADDRESS}`);
        cluster = await couchbase.connect(process.env.COUCHBASE_CLUSTER_ADDRESS, {
          username: process.env.COUCHBASE_CLUSTER_USERNAME,
          password: process.env.COUCHBASE_CLUSTER_PASSWORD,
          kvTimeout: parseInt(process.env.COUCHBASE_CLUSTER_KV_TIMEOUT, 10),
          queryTimeout: parseInt(process.env.COUCHBASE_CLUSTER_N1QL_TIMEOUT, 10),
        });

        // logger.info('Couchbase cluster established successfully!');
        isClusterHealthy = true; // Mark the cluster as healthy
        return cluster;
      } catch (error) {
        // logger.error(`Couchbase connection failed (attempt ${i + 1}/${retries}): ${error.message}`);
        if (i < retries - 1) {
          // logger.info(`Retrying Couchbase connection in ${delay}ms...`);
          await new Promise((resolve) => setTimeout(resolve, delay));
        } else {
          // logger.error('Failed to connect to Couchbase after multiple attempts.');
          isClusterHealthy = false;
          throw error;
        }
      }
    }
  })();

  // Wait for the initialization promise to resolve or reject
  try {
    return await initPromise;
  } finally {
    initPromise = null; // Reset the lock after initialization attempt
  }
};

// Ensure active connection or reconnect if needed
const reconnectIfNeeded = async () => {
  try {
    if (!cluster) await initcluster(); // Initialize if not connected
    await cluster.ping(); // Ping to ensure the connection is active
    isClusterHealthy = true; // Mark cluster as healthy if ping succeeds
  } catch (error) {
    // logger.error(`Couchbase cluster ping failed, attempting to reconnect: ${error.message}`);
    isClusterHealthy = false;
    cluster = null; // Reset cluster and reconnect
    await initcluster(); // Reinitialize the cluster
  }
};

// Helper to get a Couchbase bucket with reconnection support
const getBucket = async (bucketName) => {
  await reconnectIfNeeded(); // Ensure active connection
  return cluster.bucket(bucketName);
};

// Collection retrieval functions
const collection = async () => {
  const bucket = await getBucket(process.env.COUCHBASE_CLUSTER_BUCKET);
  return bucket.defaultCollection();
};

const customerCollection = async () => {
  const bucket = await getBucket(process.env.COUCHBASE_CLUSTER_CUSTOMER_BUCKET);
  return bucket.defaultCollection();
};

const addressCollection = async () => {
  const bucket = await getBucket(process.env.COUCHBASE_CLUSTER_ADDRESS_BUCKET);
  return bucket.defaultCollection();
};

// Initialize cluster on startup (optional)
initcluster().catch((err) => {
  // logger.error(`Couchbase initialization failed: ${err.message}`);
});

// Export functions and objects
module.exports = {
  initcluster,
  collection,
  customerCollection,
  addressCollection,
  couchbase,
};