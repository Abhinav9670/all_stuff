// make bluebird default Promise
const { port, env } = require('./config/vars');
const logger = require('./config/logger');
const app = require('./config/express');
const mongoInit = require('./config/mongoInit');
require('./config/config');

global.logError = (errorData, custom) => {
  logger.error(JSON.stringify(errorData), custom);
};

if (process.env.ELASTIC_APM_SERVICE_NAME) {
  console.log('ELASTIC_APM_SERVER_URL', process.env.ELASTIC_APM_SERVER_URL);
  try {
    const apm = require('elastic-apm-node').start({
      // Allowed characters: a-z, A-Z, 0-9, -, _, and space
      serviceName: `${process.env.ELASTIC_APM_SERVICE_NAME}`,
      secretToken: `${process.env.APMKEY}`,
      serverUrl: `${process.env.ELASTIC_APM_SERVER_URL}`,
      captureBody: 'all'
    });

    const envLabels = [
      'NODE_ENV',
      'NODE_ENV_ALT',
      'PROJECT',
      'BUILD',
      'REV',
      'BRANCH',
      'TAG_NAME',
      'COMMIT_SHA',
      'REPO_NAME',
      'TEST_ENV_NAME',
      'NODE_VERSION',
      'CONSUL_HOST',
      'ELASTIC_HOST'
    ];
    const labels = {};

    envLabels.forEach(l => {
      if (process.env[l]) labels[l] = process.env[l];
    });

    global.logError = (e, custom = {}) => {
      try {
        if (apm) {
          const payload = {
            custom: {
              stack: e?.stack,
              ...custom
            }
          };
          apm.addLabels(labels);
          console.error(e);
          if (e && e.map) {
            e.map(error => {
              apm.captureError(error, { ...payload });
            });
          } else {
            apm.captureError(e, { ...payload });
          }
        } else {
          stringifyError(e, custom);
        }
      } catch (err) {
        stringifyError(err, custom);
      }
      stringifyError(e, custom);
    };

    const stringifyError = (err, custom) => {
      try {
        const customMessage = JSON.stringify(custom);
        const errorMessage = JSON.stringify(err, ['message', 'arguments', 'type', 'name', 'stack']);
        console.error(`#### Global error handler. Error : ${errorMessage}. Custom Message : ${customMessage}`);
      } catch (e) {
        console.error(err);
      }
    };

    global.apm = apm;
  } catch (e) {
    logger.error(`Errro In APM init ${e.message}`);
  }
}



mongoInit.connectToServer((err, client) => {
  if (err) {
    console.log('Error Connecting to Mongo @connectToServer, main.js', err);
  }
  app.db = client;
  // listen to requests
  app.listen(port, () => logger.info(`server started on port ${port} (${env})`));
});

/**
 * Exports express
 * @public
 */
module.exports = app;
