const { collection, cluster } = require("../../config/couchbase");
const ValidationError = require("../errors/ValidationError");
const { findStore, getShippingKey, logError } = require("../utils");
const moment = require("moment");
const client = require("../../helpers/redisConn");

/**
 * Process free Shipping webhook and store the details in active quote
 * @param {*} req
 */
exports.processFreeShipping = async (req) => {
  const payload = req.body;
  const store = findStore(payload);
  const storeId = Number(store?.storeId);
  if (!payload?.customerId) throw new ValidationError(`CustomerId required`);
  if (isNaN(payload?.minOrderValue) || payload?.minOrderValue < 0)
    throw new ValidationError(`minOrderValue only number & can't less than 0`);
  if (
    !payload?.expireInHour ||
    isNaN(payload?.expireInHour) ||
    payload?.expireInHour <= 0
  )
    throw new ValidationError(`expireInHour required & accept number`);
  const reqData = { ...payload, storeId: storeId, requestedAt: moment() };
  const key = getShippingKey(payload?.customerId, storeId);
  try {
    const redisSavedtoDB = await client.set(key, JSON.stringify(reqData), {
      EX: payload?.expireInHour * 60 * 60,
    });
    if (!redisSavedtoDB) {
      throw new ValidationError(`Data is not updated in radis`);
    }
  } catch (e) {
    logError(e, `${key} - Error upserting data in Redis`);
    return false;
  }
  return true;
};
