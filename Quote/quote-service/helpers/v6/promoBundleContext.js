const { upsertQuote } = require("../upsertQuote");
// const { logger } = require("../utils");
const moment = require("moment");
const { filter } = require("lodash");

const handleRetroQuotesForItemsSequence = async ({
  quote,
  storeId,
  collection,
  xHeaderToken,
  optimized = false
}) => {
  const logPrefix = `handleRetroQuotesForItemsSequence:::${new Date().toISOString()}`;
  try {
    if (!collection) {
      // logger.info(`${logPrefix} - Couchbase collection missing:`);
    }
    // logger.info(
    //   `${logPrefix} - Starting retro quote processing for quote: ${quote?.id}`
    // );
    const dataAbsent = filter(quote?.quoteItem, (el) => !el.addedAt);
    if (dataAbsent.length === 0) {
      // logger.info(
      //   `${logPrefix} - All quoteItems already have addedAt timestamps for quote: ${quote?.id}`
      // );
      return quote;
    }

    let curr = new Date();
    const skuList = [];
    for (const item of quote.quoteItem) {
      item.addedAt = curr;
      curr = moment(curr).subtract(1, "m").toDate();
      skuList.push(item.sku);
    }
    if (skuList && skuList.length > 0) {
      quote.skus = skuList;
    }
    if(!optimized){
      await upsertQuote({ storeId, quote, collection, xHeaderToken });
    }
    // logger.info(`${logPrefix} - Successfully saved retro quote: ${quote?.id}`);

    return quote;
  } catch (e) {
    // logger.info(`${logPrefix} - Error processing quote: ${quote?.id}`);
    // logger.error(e);
  }
};

module.exports = {
  handleRetroQuotesForItemsSequence,
};
