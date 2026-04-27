const { BRAZE_BASE_URL } = process.env;

exports.BRAZE_LOG_CUSTOM_EVENT = `${BRAZE_BASE_URL}/users/track`;
exports.TRACKING_URL = 'https://track.stylishop.com/?waybill=';
