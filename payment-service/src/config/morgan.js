const morgan = require('morgan');
const logger = require('./logger');

morgan.token('request', req => JSON.stringify(req.body));

const successResponseFormat = `:remote-addr - :method :url :status - :response-time ms`;
const errorResponseFormat = `:remote-addr - :method :url :status - :response-time ms - request_body: :request`;

const successHandler = morgan(successResponseFormat, {
  skip: (_req, res) => res.statusCode >= 400,
  stream: {
    write: message => logger.info(message.trim())
  }
});

const errorHandler = morgan(errorResponseFormat, {
  skip: (_req, res) => res.statusCode < 400,
  stream: { write: message => logger.error(message.trim()) }
});

module.exports = {
  successHandler,
  errorHandler
};
