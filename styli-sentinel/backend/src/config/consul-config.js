const consul = require('consul');

/**
 * init.
 */
async function init() {
  const consulObj = consul({
    host: process.env.CONSUL_HOST,
    port: process.env.CONSUL_PORT,
    defaults: {
      token: process.env.CONSUL_TOKEN
    }
  });
  try {
    await getGlobalConfig(consulObj);
  } catch (e) {
    global.logError(e);
  }
}

const getGlobalConfig = async consulObj => {
  // Global Config from consul start
  console.log(`CONSUL KEY:: sentinel/base_${process.env.NODE_ENV_ALT}`);
  const watchGlobal = consulObj.watch({
    method: consulObj.kv.get,
    options: {
      key: `sentinel/base_${process.env.NODE_ENV_ALT}`
    },
    backoffFactor: 1000,
    promisify: false
  });

  watchGlobal.on('change', data => {
    try {
      if (data) {
        global.globalConfig = JSON.parse(data.Value);
      } else {
        global.logError('Consul return undefined for @@getGlobalConfig:');
      }
    } catch (e) {
      global.logError(e);
    }
  });

  watchGlobal.on('error', err => {
    if (typeof global.globalConfig == 'undefined') {
      global.logError(err);
    }
  });
};

init();
