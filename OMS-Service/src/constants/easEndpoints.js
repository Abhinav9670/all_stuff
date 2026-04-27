const { EAS_URL } = process.env;

exports.EAS_ONDELIVERY_SUCCESS = `${EAS_URL}/api/v1/onDeliverySuccess`;
exports.EAS_ONRETURN_PICKUP_SUCCESS = `${EAS_URL}/api/v1/onReturnPickupSuccess`;
exports.EARN_GET_LEDGER = `${EAS_URL}/api/v1/getCustomerLedger`;
exports.EARN_IS_RATING_ON_ORDER = `${EAS_URL}/api/v1/isRatingOnOrder`;
exports.UPDATE_SHUKRAN_LEDGER = `${EAS_URL}/api/v1/saveLedger`;
