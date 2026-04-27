const { pushNotification } = require("./notifications");
const { formatPrice, getBaseConfig, logError, logInfo, getStoreConfig } = require("./utils");
const cache = require("memory-cache");
const { upsertQuote } = require("./upsertQuote");

exports.processPriceNudge = async ({
  quote,
  screenName,
  xHeaderToken,
  storeId,
  collection,
  optimized = false
}) => {
  // logInfo("processPriceNudge", "method start", xHeaderToken);
  let priceNudgeTosave = false;
  try {
    if (screenName?.toLowerCase() !== "bag") return false;
    const store = getStoreConfig(storeId);
    const priceNudgeConfig = getBaseConfig("priceNudge");

    if (!priceNudgeConfig || !store) return false;
    const countryConfig = priceNudgeConfig[store.websiteCode];
    if (!countryConfig) return false;

    // logInfo("processPriceNudge", "method processing", xHeaderToken);
    const notifications = priceNudgeConfig?.notifications;

    for (let i = quote.quoteItem?.length - 1; i >= 0; i--) {
      const droppedPrice = formatPrice(quote?.quoteItem[i]?.droppedPrice);
      if (droppedPrice) continue;
      const finalPrice = quote?.quoteItem[i]?.priceInclTax;
      const currentPrice = formatPrice(finalPrice);
      const currentPriceType = quote.quoteItem[i]?.flashSale
        ? "flash"
        : "normal";
      const priceHistory = quote.quoteItem[i]?.priceHistory || [];
      const last = priceHistory[priceHistory.length - 1];
      if (last) {
        // logInfo("processPriceNudge", "last object found", xHeaderToken);
        if (last.price != currentPrice) {
          // logInfo("processPriceNudge", "last object differ", xHeaderToken);
          if (priceNudgeTosave === false) priceNudgeTosave = true;
          priceHistory.push({
            price: currentPrice,
            type: currentPriceType,
          });
          if (last.price < currentPrice && last.type == "normal") {
            // logInfo("processPriceNudge", "last object PRICE_UP", xHeaderToken);
            pushNotification({
              quote,
              notifyData: notifications,
              notifyId: "priceChange",
              textKey: "PRICE_UP",
            });
          } else if (last.price < currentPrice && last.type == "flash") {
            // logInfo(
            //   "processPriceNudge",
            //   "last object PRICE_UP_FROM_FLASH",
            //   xHeaderToken
            // );
            pushNotification({
              quote,
              notifyData: notifications,
              notifyId: "priceChange",
              textKey: "PRICE_UP_FROM_FLASH",
            });
          } else if (last.price > currentPrice) {
            // logInfo(
            //   "processPriceNudge",
            //   "last object PRICE_DOWN",
            //   xHeaderToken
            // );
            quote.quoteItem[i].cartItemPriceDrop = Number((last.price - currentPrice).toFixed(2));
            pushNotification({
              quote,
              notifyData: notifications,
              notifyId: "priceChange",
              textKey: "PRICE_DOWN",
            });
          }
        }
      } else {
        // logInfo("processPriceNudge", "last object not found", xHeaderToken);
        if (priceNudgeTosave === false) priceNudgeTosave = true;
        priceHistory.push({
          price: currentPrice,
          type: currentPriceType,
        });
      }
      quote.quoteItem[i].priceHistory = priceHistory;
    }

    if (priceNudgeTosave) {
      logInfo("processPriceNudge", "upsert quote called", xHeaderToken);
      if(!optimized){
        await upsertQuote({ storeId, quote, collection, xHeaderToken });
      }
    }
    return true;
  } catch (e) {
    logError(e, "error settting quote price nudge notifications");
    return false;
  }
};
