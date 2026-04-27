const { AUTH_EXTERNAL_HEADER_BEARER_TOKEN } = process.env;
console.log({ AUTH_EXTERNAL_HEADER_BEARER_TOKEN });
const restAuth = (req, res, next) => {
  const { headers } = req;
  const tokenArr = AUTH_EXTERNAL_HEADER_BEARER_TOKEN.split(',');
  const authEnabled = global.javaOrderServiceConfig?.is_external_auth_enable;

  if (!tokenArr.includes(headers['authorization-token']) && authEnabled) {
    return res.status(401).send('Unauthorized');
  }
  return next();
};

module.exports = restAuth;
