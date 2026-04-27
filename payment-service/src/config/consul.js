const logger = require('./logger');
const consulObj = require('consul')({
  host: process.env.CONSUL_HOST,
  port: process.env.CONSUL_PORT,
  defaults: {
    token: process.env.CONSUL_TOKEN
  }
});

const envMatch = {
  qa: 'qa',
  production: 'live',
  dev: 'dev',
  development: 'dev',
  uat: 'qa01'
};

/**
 *
 * @return {object}
 */
const init = async () => {
  logger.info(`consul : ${process.env.CONSUL_HOST}:${process.env.CONSUL_PORT}`);
  try {
    await watchAppConfig();
    await watchBaseConfig();
    await watchOrderConfig();
    await watchCustomerConfig();
    await watchPaymentConfig();
  } catch (e) {
    global.logError(e);
  }

  return consulObj;
}

const watchCustomerConfig = async () => {
  const watchCustomerServiceAuthConfig = await consulObj.watch({
    method: consulObj.kv.get,
    options: {
      key: `java/customer-service/authorization_${envMatch[process.env.NODE_ENV]}`
    }
  });

  watchCustomerServiceAuthConfig.on('change', function (data) {
    try {
      if (data) {
        global.customerAuthConfig = JSON.parse(data.Value);
        console.log('Customer Auth Config Updated');
      }
    } catch (e) {
      global.logError(e);
    }
  });

  watchCustomerServiceAuthConfig.on('error', function (err) {
    global.logError(err);
  });
}

const watchOrderConfig = async () => {
  const watchOrderServiceConfig = await consulObj.watch({
    method: consulObj.kv.get,
    options: {
      key: `java/order-service/credentials_${envMatch[process.env.NODE_ENV]}`
    }
  });

  watchOrderServiceConfig.on('change', function (data) {
    try {
      if (data) {
        global.payfortConfig = JSON.parse(data.Value);
        console.log('Payfort Config Updated');
      }
    } catch (e) {
      global.logError(e);
    }
  });

  watchOrderServiceConfig.on('error', function (err) {
    global.logError(err);
  });
}

const watchAppConfig = async () => {
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
}

const watchBaseConfig = async () => {
  const watcher = await consulObj.watch({
    method: consulObj.kv.get,
    options: {
      key: `payment_methods_${envMatch[process.env.NODE_ENV]}`
    }
  });

  watcher.on('change', function (data) {
    try {
      if (data) {
        global.baseConfig = JSON.parse(data.Value);
        console.log('Base config updated');
      }
    } catch (e) {
      global.logError(e);
    }
  });

  watcher.on('error', function (err) {
    global.logError(err);
  });
}

const watchPaymentConfig = async () => {
  const paymentMethodKey = process.env.CONSUL_PAYMENT_METHODS;
  const paymentMethodsWatcher = consulObj.watch({
    method: consulObj.kv.get,
    options: { key: paymentMethodKey },
    backoffFactor: 1000
  });
  paymentMethodsWatcher.on('change', updatedData => {
    try {
      if (updatedData?.Value) {
        const paymentMethods = JSON.parse(updatedData?.Value);
        global.paymentMethods = paymentMethods;
        logger.info(`Payment Config Updated`);
      }
    } catch (e) {
      logger.error('Error in setting payment methods from consul ', e);
    }
  });
  paymentMethodsWatcher.on('error', function (err) {
    logger.error('Error in watching payment methods from consul', err);
  });
};
global.consul = init();
module.exports = { envMatch };

