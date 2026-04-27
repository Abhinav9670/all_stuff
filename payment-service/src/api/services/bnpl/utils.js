const _ = require('lodash');
const redisClient = require('../../../config/redis');

function isMsiteSource(source) {
  return source === 'msite' || source === 'oldmsite';
}

const replaceURLPlaceholder = (url, store) => {
  const lang = exports.getStoreLanguageFromStore(store);
  return _.replace(url, 'LANG', _.trim(lang));
};

exports.buildUrl = (type, responseObject, store, paymentConfig) => {
  const xSource = _.lowerCase(responseObject.xSource);
  const isMsite = isMsiteSource(xSource);
  const { quoteId, customerId, storeId } = responseObject;

  const statusParams = {
    success: `&customerId=${customerId}&storeId=${storeId}`,
    failure: '',
    cancel: '',
    default: ''
  };

  const baseUrls = {
    success: isMsite ? paymentConfig.successUrl : paymentConfig.appSuccessUrl,
    cancel: isMsite ? paymentConfig.cancelUrl : paymentConfig.appCancelUrl,
    failure: isMsite ? paymentConfig.failureUrl : paymentConfig.appFailureUrl,
    default: isMsite ? paymentConfig.cancelUrl : paymentConfig.appCancelUrl
  };

  const status = ['success', 'cancel', 'failure'].includes(type) ? type : 'default';
  const baseUrl = replaceURLPlaceholder(baseUrls[status], store);
  const extraParams = statusParams[status];

  return `${baseUrl}?method=BNPL&quoteId=${quoteId}${extraParams}&status=${status}`;
};

exports.getStoreLanguageFromStore = store =>
  _.split(store?.storeLanguage, '_', 1)[0] || 'en';

exports.setTTLForRedisKey = key => {
  const paymentMethods = global.paymentMethods;
  const expiryAt = paymentMethods.cacheExpiryInSec;
  redisClient.expire(key, expiryAt || 600);
};
