const _ = require("lodash");

exports.buildUrl = (type, responseObject, store, paymentConfig) => {
  const xSource = _.lowerCase(responseObject.xSource);
  switch (type) {
    case "success":
      const successUrl =
        _.isEqual(xSource, "msite") || _.isEqual(xSource, "oldmsite")
          ? replaceURLPlaceholder(paymentConfig?.successUrl, store)
          : replaceURLPlaceholder(paymentConfig.appSuccessUrl, store);
      return `${successUrl}?method=BNPL&quoteId=${responseObject.quoteId}&customerId=${responseObject.customerId}&storeId=${responseObject.storeId}`;
    case "cancel":
      const cancelUrl =
        _.isEqual(xSource, "msite") || _.isEqual(xSource, "oldmsite")
          ? replaceURLPlaceholder(paymentConfig.cancelUrl, store)
          : replaceURLPlaceholder(paymentConfig.appCancelUrl, store);
      return `${cancelUrl}?method=BNPL&quoteId=${responseObject.quoteId}`;
    case "failure":
      const failureUrl =
        _.isEqual(xSource, "msite") || _.isEqual(xSource, "oldmsite")
          ? replaceURLPlaceholder(paymentConfig.failureUrl, store)
          : replaceURLPlaceholder(paymentConfig.appFailureUrl, store);
      return `${failureUrl}?method=BNPL&quoteId=${responseObject.quoteId}`;
    default:
      const defaultUrl =
        _.isEqual(xSource, "msite") || _.isEqual(xSource, "oldmsite")
          ? replaceURLPlaceholder(paymentConfig.cancelUrl, store)
          : replaceURLPlaceholder(paymentConfig.appCancelUrl, store);
      return `${defaultUrl}?method=BNPL&quoteId=${responseObject.quoteId}`;
  }
};

const replaceURLPlaceholder = (url, store) => {
  const lang = this.getStoreLanguageFromStore(store);
  return _.replace(url, "LANG", _.trim(lang));
};

exports.getStoreLanguageFromStore = (store) =>
  _.split(store?.storeLanguage, "_", 1)[0] || "en";
