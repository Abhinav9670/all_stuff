const jwtDecode = require('jwt-decode');
const secretKey = process.env.jwt_secret;
const jwt = require('jsonwebtoken');
const logger = require('../../../config/logger');

exports.validateTokenOld = async (req, res, next,matchGuestXHeader = false) => {
  const { headers } = req;
  const { token } = headers;
  const xHeaderToken = headers["x-header-token"];
  // if(!xHeaderToken) return res.status(401).json({ "status": false, "statusCode": "401", statusMsg: "x-header-token missing in request" });
  // if (xHeaderToken.indexOf('guest') === -1) {
  if (!token) {
    logger.info("jwt error", "Token missing in request", xHeaderToken);
    return res.status(401).json({
      status: false,
      statusCode: "401",
      statusMsg: "Token missing in request",
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
    global.logError(e, "error validating JWT", xHeaderToken);
  }

  if (!decodedToken) {
    logger.info("jwt error", jwtError, xHeaderToken);
    return res.status(401).json({
      status: false,
      statusCode: "401",
      statusMsg: jwtError,
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
      logger.info("jwt error", "Invalid request for a guest!", xHeaderToken);
      return res.status(401).json({
        status: false,
        statusCode: "401",
        statusMsg: "Invalid request for a guest!",
      });
    }
  }
  next();
};

exports.compareVersions = (version1, version2) => {
  const v1 = version1.split('.').map(Number);
  const v2 = version2.split('.').map(Number);

  const maxLength = Math.max(v1.length, v2.length);

  for (let i = 0; i < maxLength; i++) {
    const num1 = v1[i] || 0;
    const num2 = v2[i] || 0;

    if (num1 > num2) return 1;  // version1 is greater
    if (num1 < num2) return -1; // version1 is smaller
  }

  return 0; // versions are equal
};

const validateNewTokenFlow = (token, deviceID) => {
  if (!token) {
    return { error: "Token missing in request" };
  }

  const jwtToken = token.split(" ").pop();
  try {
    const decoded = jwt.verify(jwtToken, secretKey);
    return { decoded };
  } catch (err) {
    global.logError(err, "error validating JWT", deviceID);
    return { error: err.message };
  }
};

const validateOldTokenFlow = (token, xHeaderToken) => {
  if (!token) {
    return { error: "Token missing in request" };
  }

  const jwtToken = token.split(" ").pop();
  try {
    const decoded = jwt.verify(jwtToken, secretKey);
    return { decoded };
  } catch (err) {
    global.logError(err, "error validating JWT", xHeaderToken);
    return { error: err.message };
  }
};

const respondUnauthorized = (res, msg) => {
  return res.status(401).json({
    status: false,
    statusCode: "401",
    statusMsg: msg,
  });
};

exports.validateTokenCustomer = async (req, res, next, newToken = false) => {
  const { headers, body } = req;
  const { token } = headers;
  const customerId = body.customerId;
  const deviceID = headers["device-id"];
  const xHeaderToken = headers["x-header-token"];

  const context = { token, customerId, deviceID, xHeaderToken };

  const { decoded, error } = newToken
    ? validateNewTokenFlow(token, deviceID)
    : validateOldTokenFlow(token, xHeaderToken);

  if (error) {
    logger.info("jwt error", error, deviceID || xHeaderToken);
    return respondUnauthorized(res, error);
  }

  const valid = newToken
    ? isValidNewToken(context, decoded)
    : isValidOldToken(context, decoded);

  if (!valid.success) {
    return respondUnauthorized(res, valid.message);
  }

  return next();
};

function isValidNewToken({ customerId, deviceID, xHeaderToken }, decoded) {
  const { sub: tokenDeviceId, exp: expiryTime } = decoded;
  const now = Math.floor(Date.now() / 1000);

  if (now > expiryTime) {
    return { success: false, message: "JWT token has expired" };
  }

  if (customerId) {
    if (!isDeviceIdMatch(tokenDeviceId, deviceID)) {
      return { success: false, message: "JWT Token device ID mismatched" };
    }
  } else if (!isGuestAccess(xHeaderToken)) {
    return { success: false, message: "Invalid access without customerId!" };
  }

  return { success: true };
}

function isValidOldToken({ customerId, xHeaderToken }, decoded) {
  const { customerId: tokenCustomerId, sub: tokenEmail } = decoded;

  if (customerId) {
    if (!tokenCustomerId) {
      return { success: false, message: "Customer ID not found in JWT!" };
    }

    const idMatch = Number(tokenCustomerId) === Number(customerId);
    const emailMatch = tokenEmail?.toLowerCase() === xHeaderToken?.toLowerCase();

    if (!idMatch || !emailMatch) {
      return { success: false, message: "Invalid JWT Token" };
    }
  } else if (!isGuestAccess(tokenEmail)) {
    return { success: false, message: "Invalid access without customerId!" };
  }

  return { success: true };
}

function isDeviceIdMatch(tokenDeviceId, deviceID) {
  return tokenDeviceId?.toLowerCase() === deviceID?.toLowerCase();
}

function isGuestAccess(value) {
  return value?.toLowerCase() === "guest@stylishop.com";
}


exports.uuidError = res => {
  return res.status(401).json({
    status: false,
    statusCode: '401',
    statusMsg: 'JWT uuid absent/mismatch!',
    isForceLogout: false
  });
};