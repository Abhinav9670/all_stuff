const cache = require('memory-cache');
const couchbase = require('couchbase');

const {
  collection,
  customerCollection,
  addressCollection,
  initcluster
} = require('../config/couchbase.js');
const { logError } = require('../helpers/utils');
const { processPriceDrop } = require('../helpers/webhook/priceDrop.js');
const ValidationError = require('../helpers/errors/ValidationError.js');
const { processFreeShipping } = require('../helpers/webhook/freeShipping.js');

exports.updateConfigValues = async function ({ req, res }) {
  const configCacheKey = process.env.CONSUL_KEY || 'appConfig';
  const existingCache = cache.get(configCacheKey);

  // console.log('existingCache', existingCache);
  res.status(200);
  res.json({
    status: true,
    statusCode: 200,
    statusMsg: 'config cache Data',
    response: existingCache
  });
};

exports.healthCheck = async ({ req, res, pool }) => {
  const LOG_PREFIX = '[QS-HealthCheck]';
  // const healthCheckStartTime = Date.now();
  
  // console.log(`${LOG_PREFIX} coming to the health check*****`);
  
  let status = true;
  const statusMsg = {
    couchbaseCon: true,
    mysqlCon: true
  };
  // const timings = {
  //   couchbase: null,
  //   mysql: null,
  //   total: null
  // };

  // Couchbase health check
  // const cbStartTime = Date.now();
  try {
    // console.log(`${LOG_PREFIX} Couchbase: checking`);
    const quoteCollection = await collection();
    if (!quoteCollection) {
      // console.log(`${LOG_PREFIX} Missing couchbase collection:: in miscCollection::`);
    }
    await quoteCollection.get('quote_');
    // timings.couchbase = Date.now() - cbStartTime;
    // console.log(`${LOG_PREFIX} Couchbase: SUCCESS (${timings.couchbase}ms)`);
  } catch (e) {
    // timings.couchbase = Date.now() - cbStartTime;
    if (!(e instanceof couchbase.DocumentNotFoundError)) {
      // console.log(`${LOG_PREFIX} Couchbase: FAILED (${timings.couchbase}ms) - ${e.message}`);
      status = false;
      statusMsg.couchbaseCon = false;
    } else {
      // console.log(`${LOG_PREFIX} Couchbase: OK (${timings.couchbase}ms) - ${e.message}`);
    }
  }

  // MySQL health check
  // const mysqlStartTime = Date.now();
  try {
    // console.log(`${LOG_PREFIX} MySQL: checking`);
    await pool.query('SELECT 1');
    // timings.mysql = Date.now() - mysqlStartTime;
    // console.log(`${LOG_PREFIX} MySQL: SUCCESS (${timings.mysql}ms)`);
  } catch (e) {
    // timings.mysql = Date.now() - mysqlStartTime;
    // console.log(`${LOG_PREFIX} MySQL: FAILED (${timings.mysql}ms) - ${e.message}`);
    status = false;
    statusMsg.mysqlCon = false;
  }

  // timings.total = Date.now() - healthCheckStartTime;
  const statusCode = status ? '200' : '500';  
  
  // // Warn if health check is taking too long (approaching liveness probe timeout)
  // if (timings.total > 2000) {
  //   console.error(`${LOG_PREFIX} health check TIMEOUT: ${timings.total}ms - exceeded 2s threshold!`);
  // } else if (timings.total > 1500) {
  //   console.warn(`${LOG_PREFIX} health check SLOW: ${timings.total}ms - approaching 2s timeout!`);
  // }

  // console.log(`${LOG_PREFIX} health check completed: status=${statusCode}, statusMsg=${JSON.stringify(statusMsg)}, total=${timings.total}ms, cb=${timings.couchbase}ms, mysql=${timings.mysql}ms`);

  return res.status(statusCode).json({
    status,
    statusCode,
    statusMsg
  });
};
exports.customerHealthCheck = async ({ req, res, pool }) => {
  let status = true;
  const statusMsg = {
    couchbaseCon: true
  };

  try {
    const quoteCustomerCollection = await customerCollection();
    await quoteCustomerCollection.get('fRules_');
  } catch (e) {
    if (!(e instanceof couchbase.DocumentNotFoundError)) {
      // logError(e, 'Error couchbase Healthcheck');
      status = false;
      statusMsg.couchbaseCon = false;
    }
  }

  const statusCode = status ? '200' : '500';
  return res.status(statusCode).json({
    status,
    statusCode,
    statusMsg
  });
};

exports.addressHealthCheck = async ({ req, res, pool }) => {
  let status = true;
  const statusMsg = {
    couchbaseCon: true
  };

  try {
    const quoteAddressCollection = await addressCollection();
    await quoteAddressCollection.get('address_');
  } catch (e) {
    if (!(e instanceof couchbase.DocumentNotFoundError)) {
      // logError(e, 'Error couchbase Healthcheck');
      status = false;
      statusMsg.couchbaseCon = false;
    }
  }

  const statusCode = status ? '200' : '500';
  return res.status(statusCode).json({
    status,
    statusCode,
    statusMsg
  });
};

exports.couchBaseHealthCheck = async ({ req, res }) => {
  try {
    let result = {};
    let statusCode = '200';
    let statusMsg = 'UP';
    let status = true;
    const cluster = await initcluster();
    if(!cluster){
      // console.log("Cluster not available in the couchbase healthcheck");
    }
    const cbIpArr =
      process?.env?.COUCHBASE_CLUSTER_ADDRESS?.split('//')?.[1]?.split(',') ||
      [];

    const totalNodeCount = cbIpArr.length;
    const query = await cluster.ping({
      serviceTypes: [couchbase.ServiceType.Query]
    });

    const kv = await cluster.ping({
      serviceTypes: [couchbase.ServiceType.KeyValue]
    });

    const queryNodeCount = query?.services?.n1ql?.reduce((nodeCount, node) => {
      if (node.status === 'ok') return (nodeCount = nodeCount + 1);
    }, 0);

    const kvNodeCount = kv?.services?.kv?.reduce((nodeCount, node) => {
      if (node.status === 'ok') return (nodeCount = nodeCount + 1);
    }, 0);

    if (queryNodeCount < 2 || kvNodeCount < 2) {
      statusCode = '503';
      statusMsg = 'DOWN';
      status = false;
    }

    result['query-health'] = query;
    result['kv-health'] = kv;
    res.status(200);
    res.json({
      status,
      statusCode,
      statusMsg,
      statusDetails: result,
      queryNodeCount: String(queryNodeCount || 0),
      kvNodeCount: String(kvNodeCount || 0),
      totalNodeCount: String(totalNodeCount || 0)
    });
  } catch (e) {
    // logError(e, `Error in couchbase health check`, req);
    res.status(500);
    res.json({
      status: false,
      statusCode: 500,
      statusMsg: e.message
    });
  }
};

exports.processPriceDrop = async (req, res) => {
  try {
    const result = await processPriceDrop(req);
    if (result) {
      res.status(200);
      res.json({
        status: true,
        statusCode: 200,
        statusMsg: "Quote updated successfully with price drop .",
      });
    } else {
      throw new Error("Could not process price drop webhook.");
    }
  } catch (e) {
    if (e instanceof ValidationError) {
      res.status(200);
      // console.error(`Error in processing price drop webhook. Error : ${e.message}`);
    } 
    else {
      res.status(500);
      // logError(e, `Error in processing price drop webhook`); 
    } 
    res.json({
      status: false,
      statusCode: 500,
      statusMsg: e.message,
    });
  }
};

exports.processFreeShipping = async (req, res) => {
  try {
    const result = await processFreeShipping(req);
    if (result) {
      res.status(200);
      res.json({
        status: true,
        statusCode: 200,
        statusMsg: "Customer updated successfully with free shipping .",
      });
    } else {
      throw new Error("Could not process free shipping webhook.");
    }
  } catch (e) {
    if (e instanceof ValidationError) {
      res.status(200);
      // console.error(`Error in processing free shipping webhook. Error : ${e.message}`);
    } 
    else {
      res.status(500);
      // logError(e, `Error in processing free shipping webhook`); 
    } 
    res.json({
      status: false,
      statusCode: 500,
      statusMsg: e.message,
    });
  }
};