const jwtDecode = require("jwt-decode");
const secretKey = process.env.JWT_SECRET;
const { logError, logInfo } = require("../helpers/utils");
const jwt = require("jsonwebtoken");
const cache = require("memory-cache");

exports.validateTokenOld = async (req, res, matchGuestXHeader = false) => {
  const { headers, url } = req;
  const { token } = headers;
    const xHeaderToken = headers["x-header-token"];
    // if(!xHeaderToken) return res.status(401).json({ "status": false, "statusCode": "401", statusMsg: "x-header-token missing in request" });
    // if (xHeaderToken.indexOf('guest') === -1) {
    if (!token) {
      // logInfo("jwt error", "Token missing in request", xHeaderToken);
      return res.status(401).json({
        status: false,
        statusCode: "401",
        statusMsg: "Token missing in request",
        isForceLogout: shouldForceLogout(url, xHeaderToken),
      });
    }

    const jwtToken = token?.split("KEY ")[1];
    let jwtError;
    let decodedToken;
    try {
      decodedToken = jwtDecode(jwtToken);
    } catch (e) {
      decodedToken = "";
      jwtError = e.message;
      logError(e, "error validating JWT", xHeaderToken);
    }

    if (!decodedToken) {
      logInfo("jwt error", jwtError, xHeaderToken);
      return res.status(401).json({
        status: false,
        statusCode: "401",
        statusMsg: jwtError,
        isForceLogout: shouldForceLogout(url, xHeaderToken),
      });
    }

    // console.log("decodedToken", decodedToken);

    const { sub: tokenEmail, uuid } = decodedToken;
    req.uuid = uuid;
    if (matchGuestXHeader) {
      if (
        xHeaderToken !== "guest@stylishop.com" ||
        tokenEmail !== "guest@stylishop.com"
      ) {
        logInfo("jwt error", "Invalid request for a guest!", xHeaderToken);
        return res.status(401).json({
          status: false,
          statusCode: "401",
          statusMsg: "Invalid request for a guest!",
          isForceLogout: shouldForceLogout(url, xHeaderToken),
        });
      }
    }
};

exports.compareVersions = (version1, version2) => {
  // Handle undefined/null versions to prevent crashes
  if (!version1 || !version2) {
    // console.warn(`[QS-compareVersions] Missing version: v1=${version1}, v2=${version2}`);
    return 0; // Treat as equal if either is missing
  }
  
  const v1 = String(version1).split('.').map(Number);
  const v2 = String(version2).split('.').map(Number);

  const maxLength = Math.max(v1.length, v2.length);

  for (let i = 0; i < maxLength; i++) {
    const num1 = v1[i] || 0;
    const num2 = v2[i] || 0;

    if (num1 > num2) return 1;  // version1 is greater
    if (num1 < num2) return -1; // version1 is smaller
  }

  return 0; // versions are equal
};

exports.validateTokenCustomer = async (req, res,newToken = false) => {
  const { headers, body, url } = req;
  const { token } = headers;
  const { customerId = undefined } = body;

  if(newToken){
    const deviceID = headers["device-id"];

    if (!token) {
      logInfo("jwt error", "Token missing in request", deviceID);
      return res.status(401).json({
        status: false,
        statusCode: "401",
        statusMsg: "Token missing in request",
        isForceLogout: shouldForceLogout(url, deviceID),
      });
    }
  
    let jwtToken = token?.split("KEY ")[1];
    if(token?.includes("Bearer")){
      jwtToken = token?.split("Bearer ")[1];
    }
    // logInfo("jwt token value: " , jwtToken, "secretKey value: " , secretKey, "customerId value: ", customerId?customerId:"");
    let jwtError;
    let decodedToken;
  
    try {
      decodedToken = jwt.verify(jwtToken, secretKey);
    } catch (e) {
      decodedToken = "";
      jwtError = e.message;
      logError(e, "error validating JWT", deviceID);
    }
  
    if (!decodedToken) {
      logInfo("jwt error", jwtError, deviceID);
      return res.status(401).json({
        status: false,
        statusCode: "401",
        statusMsg: jwtError,
        isForceLogout: shouldForceLogout(url, deviceID),
      });
    }
  
    const {  sub: tokenDeviceId, exp: expiryTime } = decodedToken;

      const currentTime = Math.floor(Date.now() / 1000);

      if(currentTime > expiryTime){
        return res.status(401).json({
          status: false,
          statusCode: "401",
          statusMsg: "JWT token has expired",
          isForceLogout: shouldForceLogout(url, deviceID),
        });
      }
  
    if (customerId) {
      if (
        tokenDeviceId?.toLowerCase() !== deviceID.toLowerCase()
      ) {
        return res.status(401).json({
          status: false,
          statusCode: "401",
          statusMsg: "JWT Token device ID mismatched",
          isForceLogout: shouldForceLogout(url, deviceID),
        });
      }
    } else {
      if (headers["x-header-token"] !== "guest@stylishop.com") {
        return res.status(401).json({
          status: false,
          statusCode: "401",
          statusMsg: "Invalid access without customerId!",
          isForceLogout: shouldForceLogout(url, deviceID),
        });
      }
    }
  }
  else {
    const xHeaderToken = headers["x-header-token"];

  if (!token) {
    logInfo("jwt error", "Token missing in request", xHeaderToken);
    return res.status(401).json({
      status: false,
      statusCode: "401",
      statusMsg: "Token missing in request",
      isForceLogout: shouldForceLogout(url, xHeaderToken),
    });
  }

  const jwtToken = token?.split(" ")?.pop();
  let jwtError;
  let decodedToken;

  try {
    decodedToken = jwt.verify(jwtToken, secretKey);
  } catch (e) {
    decodedToken = "";
    jwtError = e.message;
    logError(e, "error validating JWT", xHeaderToken);
  }

  if (!decodedToken) {
    logInfo("jwt error", jwtError, xHeaderToken);
    return res.status(401).json({
      status: false,
      statusCode: "401",
      statusMsg: jwtError,
      isForceLogout: shouldForceLogout(url, xHeaderToken),
    });
  }

  const { customerId: tokenCustomerId, sub: tokenEmail } = decodedToken;

  if (customerId) {
    if (!tokenCustomerId) {
      return res.status(401).json({
        status: false,
        statusCode: "401",
        statusMsg: "Customer ID not found in JWT!",
        isForceLogout: shouldForceLogout(url, xHeaderToken),
      });
    }
    if (
      Number(tokenCustomerId) !== Number(customerId) ||
      tokenEmail?.toLowerCase() !== xHeaderToken.toLowerCase()
    ) {
      return res.status(401).json({
        status: false,
        statusCode: "401",
        statusMsg: `Invalid JWT Token for this client ${customerId}`,
        isForceLogout: shouldForceLogout(url, xHeaderToken),
      });
    }
  } else {
    if (tokenEmail !== "guest@stylishop.com") {
      return res.status(401).json({
        status: false,
        statusCode: "401",
        statusMsg: "Invalid access without customerId!",
        isForceLogout: shouldForceLogout(url, xHeaderToken),
      });
    }
  }
  }
};

exports.uuidError = (res) => {
  return res.status(401).json({
    status: false,
    statusCode: "401",
    statusMsg: "JWT uuid absent/mismatch!",
    isForceLogout: false,
  });
};

exports.uuidCheckFailed = ({ uuid, quote }) => {
  let result = false;
  const baseConfig = cache.get("baseConfig");
  // logInfo("baseConfig", baseConfig);
  if (baseConfig?.uuid_validation) {
    if (uuid && quote?.uuid != uuid) result = true;
  }
  return result;
};

const shouldForceLogout = (url, xHeaderToken = "",deviceID="") => {
  if (!url) return false;
  try {
    const urlToCheck = url.split("auth")[1];
    const forceLogoutConfig = cache.get("baseConfig")?.forceLogout;
    if (!forceLogoutConfig || !forceLogoutConfig.enable) return false;
    return (forceLogoutConfig.urls || []).includes(urlToCheck);
  } catch (e) {
    logError(e, "error while checking forceLogout", xHeaderToken ? xHeaderToken : deviceID);
    return false;
  }
};

/**
 * Valiadte requst with external token
 * @param {*} req 
 * @param {*} res 
 * @param {*} next 
 * @returns 
 */
exports.validateExternalToken = (req, res, next) => {
  const { authorization } = req.headers;
  const externalToken = process.env.AUTH_EXTERNAL_HEADER_BEARER_TOKEN;
  if (!authorization || !externalToken) {
    // logInfo(`Token missing, Request Token : ${authorization}, External Token ${externalToken}`);
    return res.status(401).json({
      status: false,
      statusCode: "401",
      statusMsg: "Authentication failed!",
    });
  } else {
    const tokens = typeof externalToken === 'string' ? externalToken.split(",") : [];
    const match = tokens.find(token => token === authorization);
    if (!match) {
      // logInfo(`Token not matching, Request Token : ${authorization}, External Token ${externalToken}`);
      return res.status(401).json({
        status: false,
        statusCode: "401",
        statusMsg: "Authentication failed!",
      });
    }
  }
  next();
};

exports.validateInternalToken = (req, res, next) => {
  const authToken = req.headers['authorization-token'];
  const internalToken = process.env.AUTH_INTERNAL_HEADER_BEARER_TOKEN;
  if (!authToken || !internalToken) {
    // logInfo(`Token missing, Request Token : ${authToken}, Internal Token ${internalToken}`);
    return res.status(401).json({
      status: false,
      statusCode: "401",
      statusMsg: "Authentication failed!",
    });
  } else {
    const tokens = typeof internalToken === 'string' ? internalToken.split(",") : [];
    const match = tokens.find(token => token === authToken);
    if (!match) {
      // logInfo(`Token not matching, Request Token : ${authToken}, Internal Token ${internalToken}`);
      return res.status(401).json({
        status: false,
        statusCode: "401",
        statusMsg: "Authentication failed!",
      });
    }
  }
  next();
};