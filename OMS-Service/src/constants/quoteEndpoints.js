const { QUOTE_BASE_URL } = process.env;

exports.QUOTE_GET_ENDPOINT = `${QUOTE_BASE_URL}/rest/quote/auth/v6/get`;
exports.QUOTE_DELETE_ENDPOINT = `${QUOTE_BASE_URL}/rest/quote/auth/v5`;
exports.QUOTE_PAYMENT_ENDPOINT = `${QUOTE_BASE_URL}/rest/quote/auth/v6/get/totals`;
exports.QUOTE_COUPON_ENDPOINT = `${QUOTE_BASE_URL}/rest/quote/auth/v5/coupon`;
exports.QUOTE_ADDRESS_ENDPOINT = `${QUOTE_BASE_URL}/rest/quote/auth/v5/address`;
