const { collection, initcluster } = require("../../config/couchbase");
const ValidationError = require("../errors/ValidationError");
const fetchQuote  = require("../fetchQuote");
const { upsertQuote } = require("../upsertQuote");
const { findStore, getStoreConfig, getBaseConfig } = require("../utils");

/**
 * Process Price drop webhook and store the details in active quote
 * @param {*} req 
 */
exports.processPriceDrop = async (req) => {
  const payload = req.body;
  const store = findStore(payload);
  const storeId = Number(store?.storeId);
  const quoteCollection = await collection();
  const cluster = await initcluster();
  if (isPriceDropEnabledInConsul(storeId))
    throw new ValidationError(`Price drop already enabled in Consul. Can't process webhook.`)
  const quote = await fetchQuote.fetchQuote({
    identifier: payload?.customerId,
    storeId: storeId,
    collection:quoteCollection,
    cluster,
    type: "customer",
  });
  if (!quote)
    throw new ValidationError(`Quote not found the customer : ${payload?.customerId}`)
  
  const isPriceDropApplied = quote?.quoteItem?.find(item => item.droppedPrice > 0);
  if (isPriceDropApplied)
    throw new ValidationError(`Could not process price drop webhook. Price Drop already applied : ${payload?.customerId}`)
  
  quote["priceDropConfig"] = payload;
  return upsertQuote({
    storeId,
    quote,
    collection:quoteCollection,
    xHeaderToken: "",
  });
};

/**
 * Check if price drop already enabled from consul
 * @param {*} storeId 
 * @returns 
 */
const isPriceDropEnabledInConsul = (storeId) => {
  const websiteCode = getStoreConfig(storeId, "websiteCode");
  const surprizeConfig =
    getBaseConfig("surprizePriceDrop")?.[websiteCode] || {};
  return surprizeConfig?.enabled;
};