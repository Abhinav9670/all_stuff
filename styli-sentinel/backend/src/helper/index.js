const axios = require('axios');
const httpStatus = require('http-status');
const { getUserDetails, validateAPIRequest } = require('../services/auth.service');
const { getApigeeProxyPath } = require('./utils');
const logger = require('./logger');

exports.authServiceCheck = async (req, res, next) => {
  console.log('global?.globalConfig?.authCheck: ', global?.globalConfig?.authCheck);
  console.log('Req to validate:', JSON.stringify(req.headers));
  const token = req.headers?.authorization?.split(' ')[1];
  const firebaseResponse = await getUserDetails({
    token: token
  });
  const isSuperadmin = global.globalConfig?.superadmins.includes(firebaseResponse?.email);
  if (!global?.globalConfig?.authCheck || isSuperadmin) {
    return next();
  }
  let resStatus = httpStatus.UNAUTHORIZED;
  const authenticate = {
    code: httpStatus.UNAUTHORIZED,
    status: 'Failed',
    message: 'Unauthorised Token!!'
  };
  try {
    let headers = {
      authority: req.headers?.authority || req.headers?.host,
      authorization: req.headers?.authorization,
      host: req.headers?.host,
      origin: req.headers?.origin,
      referer: req.headers?.referer,
      'content-type': req.headers?.['content-type']
    };
    logger.info('middleware headers', headers)
    let finalUrlPath = req.originalUrl;
    const apigeeProxyPath = getApigeeProxyPath();
    if (finalUrlPath.startsWith(apigeeProxyPath)) {// used to remove sentinel proxy path from actual url(for apigee purposes)
      const prefixRegex = new RegExp(`^${apigeeProxyPath}`);
      finalUrlPath = finalUrlPath.replace(prefixRegex, '');
    }
    logger.info('final url path', finalUrlPath)
    logger.info('process.env.AUTH_SERVICE_URL', process.env.AUTH_SERVICE_URL)
    if (headers?.authority != headers.host) {
      return res.status(403).json({ error: 'header authority and host mistmatch', status: false });
    }
    const response = await validateAPIRequest({
      method: req.method,
      headers,
      action: finalUrlPath,
    });

    let validateStatus = response?.status ?? false;
    if (validateStatus) {
      return next();
    } else {
      resStatus = 403;
      authenticate.code = 403;
      authenticate.message = 'Access forbidden';
    }
  } catch (e) {
    logger.info('auth validate error', e)
    authenticate.code = e.response?.status;
    authenticate.message = e.response?.data?.message;
    authenticate.status = false;
    resStatus = e.response?.status;
  }
  return res.status(resStatus).json(authenticate);

  //   return true;
};