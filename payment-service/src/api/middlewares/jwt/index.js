const { validateTokenOld, validateTokenCustomer, compareVersions } = require('./validateToken');

function validateOldToken(req, res, next, quote_jwt_token_enable) {
  if (!quote_jwt_token_enable) return validateTokenOld(req, res, next, false);

  const xHeaderToken = req.headers['x-header-token'] || req.headers['X-Header-Token'];
  if (!xHeaderToken) {
    if (global.logError) {
      global.logError(null, {
        status: false,
        statusCode: '400',
        statusMsg: 'XHeaderToken missing in request'
      });
    }
    return res.status(400).json({
      status: false,
      statusCode: '400',
      statusMsg: 'XHeaderToken missing in request'
    });
  }
  return validateTokenCustomer(req, res, next);
}

function validateNewToken(req, res, next, quote_jwt_token_enable) {
  if (!quote_jwt_token_enable) return validateTokenOld(req, res, next, false);

  const deviceID = req.headers['device-id'];
  if (!deviceID) {
    if (global.logError) {
      global.logError(null, {
        status: false,
        statusCode: '400',
        statusMsg: 'device-id missing in request'
      });
    }
    return res.status(401).json({
      status: false,
      statusCode: '401',
      statusMsg: 'device-id missing in request'
    });
  }
  return validateTokenCustomer(req, res, next, true);
}

exports.validateJwt = async (req, res, next) => {
  if (!req.headers['x-source'] && !req.headers['X-Source'] && req.body.xSourceHeader) {
    req.headers['x-source'] = req.body.xSourceHeader;
  }

  if (!req.headers['x-client-version'] && !req.headers['X-Client-Version'] && req.body.appVersion) {
    req.headers['x-client-version'] = req.body.appVersion;
  }

  if (req.url === '/apple' && !req.headers['device-id'] && !req.headers['Device-Id'] && req.body.deviceId) {
    req.headers['device-id'] = req.body.deviceId;
  }

  const { headers, body } = req;
  console.log(
    'validate jwt token headers data',
    JSON.stringify(headers),
    'body data',
    JSON.stringify(body),
    'url data',
    req.url
  );
  const { customerId = undefined } = body;
  // const deviceId = headers['device-id'] || headers['Device-Id'];
  const isMobileApp = headers['x-source'] !== 'msite';

  // Fetch global configuration safely
  const quoteJwtTokenEnable = global?.customerAuthConfig?.quote_jwt_token_enable || 0;
  const refreshJwtTokenEnable = global?.customerAuthConfig?.refresh_jwt_token_enable || 0;
  const refreshJwtTokenAppVersion = global?.customerAuthConfig?.refresh_jwt_token_app_version || 0;

  if (!customerId) return validateTokenOld(req, res, next, false);

  // if (!deviceId) {
  //   return handleMissingDeviceId(req, res, next, refreshJwtTokenEnable, quoteJwtTokenEnable);
  // }

  if (!isMobileApp) {
    return handleMsiteXsource(req, res, next, refreshJwtTokenEnable, quoteJwtTokenEnable);
  }
  const versionComparison = isMobileApp
    ? compareVersions(headers['x-client-version'], refreshJwtTokenAppVersion)
    : null;

  if (refreshJwtTokenEnable) {
    if (isMobileApp && versionComparison >= 0) {
      return validateNewToken(req, res, next, quoteJwtTokenEnable);
    }
    return validateOldToken(req, res, next, quoteJwtTokenEnable);
  }

  return validateOldToken(req, res, next, quoteJwtTokenEnable);
};

// const handleMissingDeviceId = (req, res, next, refreshJwtTokenEnable, quoteJwtTokenEnable) => {
//   if (refreshJwtTokenEnable) {
//     return res.status(401).json({
//       status: false,
//       statusCode: '401',
//       statusMsg: 'Device ID not found in headers',
//       isForceLogout: true
//     });
//   } else {
//     return validateOldToken(req, res, next, quoteJwtTokenEnable);
//   }
// };

const handleMsiteXsource = (req, res, next, refreshJwtTokenEnable, quoteJwtTokenEnable) => {
  if (refreshJwtTokenEnable) {
    return validateNewToken(req, res, next, quoteJwtTokenEnable);
  } else {
    return validateOldToken(req, res, next, quoteJwtTokenEnable);
  }
};
