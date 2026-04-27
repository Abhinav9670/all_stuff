const { default: axios } = require('axios');
const auth = require('./auth');
const httpStatus = require('http-status');
const jwt = require('jsonwebtoken');

const { AbilityBuilder, Ability } = require('@casl/ability');
const authValidate = index => async (req, res, next) => {
const { sentinelAuthValidation = false } = global?.baseConfig || {};
let fromStoreFront = req?.headers?.origin === process.env.MAIN_WEBSITE_HOST_NAME ? true : false;
let mode ;

if (fromStoreFront) {
mode = 'storeFront';
} 
else if (sentinelAuthValidation && !fromStoreFront) {
mode = 'sentinel';
}
else {
mode = 'default';
}
switch (mode) {
  case 'storeFront': {
    const decoded = jwt.decode(req?.headers?.token?.replace(/^KEY\s*/, ''));
    if (decoded?.sub === req.body.customerEmail) {
      next();
    } else {
      return res.status(401).json({
        status: true,
        statusCode: '401',
        statusMsg: 'You are not authenticated for this request'
      });
    }
    break;
  }
  case 'sentinel': {
    console.log('calling sentinel to validate');
    return new Promise(resolve => {
      verifyAuth(index, req, res, resolve);
    })
      .then(() => next())
      .catch(error => {
        next();
      });
  }
  default: {
    console.log('calling internal firebase auth to validate');
    auth(req.path)(req, res, next);
  }
}
};

const verifyAuth = async (index, req, res, resolve) => {
  const { can, rules } = new AbilityBuilder();
  const headers = {
    authority: req.headers?.authority || req.headers?.host,
    authorization: req.headers?.authorization,
    host: req.headers?.host,
    origin: req.headers?.origin,
    referer: req.headers?.referer,
    'content-type': req.headers?.['content-type']
  };
  if (process.env.NODE_ENV == 'development') {
    req.headers['dev-token'] = 'ABCXYZ';
    req.headers['authority'] = 'oms-api-qa.stylishop.store';
  }
  let reqPath = req.originalUrl;
  if (index) {
    reqPath = validatePath(req.originalUrl, index);
  }
  try {
    const authData = await axios.post(
      `${process.env.AUTH_SERVICE_URL}/validate`,
      {
        headers: headers,
        action: reqPath,
        method: req.method
      }
    );
    const { data } = authData || {};
    console.log('res:', JSON.stringify(data));
    if (data?.status) {
      req.email = data?.user?.email;
      req.uid = data?.user?.uuid;
      can('manage', 'all');
      const ability = new Ability(rules);
      const newReq = req;
      newReq.ability = ability;
      resolve();
    } else {
      return res.status(403).json({
        status: true,
        statusCode: '403',
        statusMsg: 'Access forbidden'
      });
    }
  } catch (e) {
    console.log('sentinel validate api res error:', e);
    return res.status(e?.response ? e.response.status: 500).json({
      status: false,
      statusCode: e?.response ? e.response.status : 500,
      statusMsg: e?.message ? e.message : e
    });
  }
};

const validatePath = (path, index) => {
  const pathArr = path.split('/');
  let newPathArray = pathArr;
  if (pathArr[index + 1]) newPathArray = pathArr.slice(0, index + 1);
  if (newPathArray[index]) newPathArray[index] = '';
  return newPathArray.join('/');
};

module.exports = authValidate;
