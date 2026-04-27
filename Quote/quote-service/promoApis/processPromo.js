const _ = require('lodash');
const axios = require('axios');
const { logError, logInfo } = require('../helpers/utils');

exports.fetchPromoResponse = async ({ quote, coupon, xHeaderToken }) => {
  // logInfo('inside processPromo', '', xHeaderToken);

  try {
    const promoBaseURL = process.env.CONSUL_PROMO_BASE_URL;
    // const promoUrl = cache.get(promoBaseURL) || process.env.PROMO_URL;
    const promoUrl = process.env.PROMO_URL;

    // logInfo(
    //   'process.env.PROMO_URL',
    //   `${promoUrl}/v3/coupon/validate`,
    //   xHeaderToken
    // );

    const headers = {
      'Content-Type': 'application/json'
    };
    quote.coupon = coupon;
    quote.check_for_auto_apply = true;
    if (!quote.quoteAddress) {
      quote.quoteAddress = [];
    }
    // logInfo('processPromo request', quote, xHeaderToken);

    return await axios.post(`${promoUrl}/v3/coupon/validate`, quote, {
      headers
    });
  } catch (e) {
    logError(e, 'Error fetching promo response : ', xHeaderToken);
    return {};
  }
};
