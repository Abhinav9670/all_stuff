const cache = require('memory-cache');
const { validateTokenOld, validateTokenCustomer, compareVersions } = require("./validateToken");
// const { logger } = require('./utils');

function validateOldToken(req, res, quote_jwt_token_enable, customerId) {
  if (!quote_jwt_token_enable) return validateTokenOld(req, res, false);

  const xHeaderToken = req.headers["x-header-token"];
  if (!xHeaderToken) {
    if (global.logError) {
      global.logError(null, {
        status: false,
        statusCode: "400",
        statusMsg: "XHeaderToken missing in request",
      });
    }
    return res.status(400).json({
      status: false,
      statusCode: "400",
      statusMsg: "XHeaderToken missing in request",
    });
  }
  return validateTokenCustomer(req, res);
}

function validateNewToken(req, res, quote_jwt_token_enable, customerId) {
  if (!quote_jwt_token_enable) return validateTokenOld(req, res, false);

  const newToken = true;

  const deviceID = req.headers["device-id"];
  if (!deviceID) {
    if (global.logError) {
      global.logError(null, {
        status: false,
        statusCode: "400",
        statusMsg: "device-id missing in request",
      });
    }
    return res.status(400).json({
      status: false,
      statusCode: "400",
      statusMsg: "device-id missing in request",
    });
  }
  return validateTokenCustomer(req, res, newToken);
}

exports.validateJwt = async (req, res) => {
  const { headers, body } = req;
  const { customerId = undefined } = body;

  let quote_jwt_token_enable = 0;
  let refresh_jwt_token_enable = 0;
  let refresh_jwt_token_app_version = 0;
  let deviceId = headers['device-id'];

  try {
    const cacheValue = cache.get(process.env.CONSUL_CUSTOMER_AUTH_CONFIG);
    if (cacheValue) {
      quote_jwt_token_enable = cacheValue?.quote_jwt_token_enable;
      refresh_jwt_token_enable = cacheValue?.refresh_jwt_token_enable;
      refresh_jwt_token_app_version = cacheValue?.refresh_jwt_token_app_version;
    }
  } catch (e) {
    // logger.error(`Could not fetch quote_jwt_token_enable from consul: ${e.message}`);
  }
  
  if (!customerId) return validateTokenOld(req, res, false);

  const isMobileApp = headers['x-source'] !== 'msite';
  const versionComparison = isMobileApp 
    ? compareVersions(headers['x-client-version'], refresh_jwt_token_app_version)
    : null;

  if (!deviceId) {
    if (refresh_jwt_token_enable) {
      if (isMobileApp && versionComparison < 0) {
        return validateOldToken(req, res, quote_jwt_token_enable, customerId);
      }
      else if(!isMobileApp){
        return res.status(401).json({
          status: false,
          statusCode: "401",
          statusMsg: "Device ID not found in headers",
          isForceLogout: true
        });
      }
      
    }
    else {
      return validateOldToken(req, res, quote_jwt_token_enable, customerId);
    }
    return res.status(401).json({
      status: false,
      statusCode: "401",
      statusMsg: "Device ID not found in Headers!",
      isForceLogout: true
    });  
  }
  else {
    if (refresh_jwt_token_enable) {
      if (isMobileApp) {
        if (versionComparison >= 0) {
          return validateNewToken(req, res, quote_jwt_token_enable, customerId);
        } else {
          return validateOldToken(req, res, quote_jwt_token_enable, customerId);
        }
      } 
      return validateNewToken(req, res, quote_jwt_token_enable, customerId);
      
    }
    else {
      return validateOldToken(req, res, quote_jwt_token_enable, customerId);
    }
  }
};