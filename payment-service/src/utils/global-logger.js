const { serializeError } = require('serialize-error');
const cls = require('cls-hooked');
const traceNamespace = cls.createNamespace('traceApp');
const logger = require('../config/logger');

const simpleStringify = object => {
  try {
    let simpleObject = {};
    for (const prop in object) {
      if (!Object.prototype.hasOwnProperty.call(object, prop)) {
        continue;
      }
      if (typeof object[prop] == 'object') {
        simpleObject[prop] = object[prop];
      }
      if (typeof object[prop] == 'function') {
        continue;
      }
      simpleObject[prop] = object[prop];
    }
    return JSON.stringify(simpleObject); // returns cleaned up JSON
  } catch (e) {
    return {};
  }
};

global.loggerInfo = (logMessage, logDetails) => {
  const message = logDetails || logMessage;
  const payload = {
    logtype: `info`,
    traceId: traceNamespace.get('traceId'),
    key: typeof logMessage === 'object' ? simpleStringify(logMessage) : logMessage,
    message: typeof message === 'object' ? simpleStringify(message) : message
  };
  logger.info(JSON.stringify(payload));
};

global.loggerError = (e, msg = '') => {
  const payload = {
    logtype: `error`,
    traceId: traceNamespace.get('traceId'),
    key: e.message,
    message: typeof msg === 'object' ? simpleStringify(msg) : msg,
    error_stack: serializeError(e)
  };
  if (global.logError) {
    global.logError(e, payload);
  }
  logger.error(JSON.stringify(payload));
};

module.exports = { traceNamespace };
