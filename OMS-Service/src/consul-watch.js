/* eslint-disable max-lines-per-function */
/* eslint-disable sonarjs/cognitive-complexity */
const { logInfo } = require('./utils');
const consulObj = require('consul')({
  host: process.env.CONSUL_HOST,
  port: process.env.CONSUL_PORT,
  defaults: {
    token: process.env.CONSUL_TOKEN
  }
});

const envMatch = {
  qa: 'qa',
  staging: 'qa',
  production: 'live',
  development: 'dev',
  uat: 'qa01'
};

/**
 *
 * @return {object}
 */
async function init() {
  logInfo('consul', `${process.env.CONSUL_HOST} ${process.env.CONSUL_TOKEN}`);
  try {
    await getGlobalAppConfig();
    await getGlobalBaseConfig();
    await getGlobalJavaServiceConfig();
    await getGlobalTaxConfig();
    await getGlobalFeatureConfig();
  } catch (e) {
    global.logError(e);
  }

  return consulObj;
}

const getGlobalAppConfig = async () => {
  /**
   * App Config
   */
  const watch = await consulObj.watch({
    method: consulObj.kv.get,
    options: {
      key: `appConfig_${envMatch[process.env.NODE_ENV] || 'live'}`
    },
    backoffFactor: 1000,
    promisify: false
  });

  watch.on('change', function (data) {
    try {
      if (data) {
        global.config = JSON.parse(data.Value);
      }
    } catch (e) {
      global.logError(e);
    }
  });

  watch.on('error', function (err) {
    global.logError(err);
  });
};

const getGlobalBaseConfig = async () => {
  /**
   * Base Config
   */
  const watchBaseConfig = await consulObj.watch({
    method: consulObj.kv.get,
    options: {
      key: `oms/base_${process.env.NODE_ENV}`
    },
    backoffFactor: 1000,
    promisify: false
  });

  watchBaseConfig.on('change', function (data) {
    try {
      console.log(`### CONSUL Changes ::: oms/base_${process.env.NODE_ENV}`);
      if (data) {
        global.baseConfig = JSON.parse(data.Value);
      }
    } catch (e) {
      global.logError(e);
    }
  });

  watchBaseConfig.on('error', function (err) {
    global.logError(err);
  });
};

const getGlobalJavaServiceConfig = async () => {
  /**
   * Java order-service Config
   */
  const javaOrderServiceConfig = await consulObj.watch({
    method: consulObj.kv.get,
    options: {
      key: `java/order-service/credentials_${
        envMatch[process.env.NODE_ENV] || 'live'
      }`
    },
    backoffFactor: 1000,
    promisify: false
  });

  javaOrderServiceConfig.on('change', function (data) {
    try {
      if (data) {
        global.javaOrderServiceConfig = JSON.parse(data.Value);
      }
    } catch (e) {
      global.logError(e);
    }
  });

  javaOrderServiceConfig.on('error', function (err) {
    global.logError(err);
  });
};

const getGlobalTaxConfig = async () => {
  /**
   * Tax Config
   */
  const watchTaxObj = await consulObj.watch({
    method: consulObj.kv.get,
    options: {
      key: `tax/in_tax_${envMatch[process.env.NODE_ENV] || 'live'}`
    },
    backoffFactor: 1000,
    promisify: false
  });

  watchTaxObj.on('change', function (data) {
    try {
      if (data) {
        global.taxConfig = JSON.parse(data.Value);
      }
    } catch (e) {
      global.logError(e);
    }
  });

  watchTaxObj.on('error', function (err) {
    global.logError(err);
  });
};

const getGlobalFeatureConfig = async () => {
  /**
   * Tax Config
   */
  const watchFeatureConfigObj = await consulObj.watch({
    method: consulObj.kv.get,
    options: {
      key: `feature_config_${envMatch[process.env.NODE_ENV] || 'live'}`
    },
    backoffFactor: 1000,
    promisify: false
  });

  watchFeatureConfigObj.on('change', function (data) {
    try {
      if (data) {
        global.featureConfig = JSON.parse(data.Value);
      }
    } catch (e) {
      global.logError(e);
    }
  });

  watchFeatureConfigObj.on('error', function (err) {
    global.logError(err);
  });
};

global.consul = init();

module.exports = { envMatch };
