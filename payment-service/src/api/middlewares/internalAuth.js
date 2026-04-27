const logger = require('../../config/logger');

const { AUTH_INTERNAL_HEADER_BEARER_TOKEN } = process.env;
const internalAuthTokens = AUTH_INTERNAL_HEADER_BEARER_TOKEN?.split(',');

exports.validateInternalAuth = async (req, res, next) => {
  const { headers } = req;
  const authorizationToken = headers?.['authorization-token'];

  if (!internalAuthTokens.includes(authorizationToken)) {
    logger.info('Invalid Internal Auth Token', authorizationToken);
    return res.status(401).json({
      status: false,
      statusCode: '401',
      statusMsg: 'Invalid Internal Auth Token'
    });
  }

  next();
};
