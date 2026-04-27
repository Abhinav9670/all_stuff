const { updateItemQty } = require("./updateQty");
const { findQuotesByCustomerAndFlash } = require("./upsertQuote");
const { getStoreConfig } = require("./utils");
const { logInfo } = require("./utils");

/**
 * Returns if flash sale is active or not
 * @param {*} flashSaleConfig
 * @returns
 */
exports.isFlashSaleActive = (flashSaleConfig) => {
  if (!flashSaleConfig) return false;
  try {
    let isFlashSaleActive = false;
    if (
      flashSaleConfig.active &&
      flashSaleConfig.start &&
      new Date() >= new Date(flashSaleConfig.start) &&
      new Date() <= new Date(flashSaleConfig.end)
    ) {
      isFlashSaleActive = true;
    }
    return isFlashSaleActive;
  } catch (e) {
    global.logError(e);
    return false;
  }
};

exports.handleSkuUpdate = async({ matchedSku, quote, xHeaderToken, res})=> {
  let existingQuote = quote;
  if (matchedSku?.isGift) {
    return existingQuote;
  }
  if (exports.isFlashSaleActive(quote.flashSale)) {
    const { capPerUser, flashsaleId: flashSaleId, stockCapExhausted } = matchedSku?.flashConfig || {};
    const parentSku = matchedSku.parentSku;

    const productsPurchased = await exports.findFlashProductsPerUser({
      quote,
      parentSku,
      flashSaleId,
    });

    const productsInQuote = matchedSku?.qty;
    const productTotal = productsPurchased + productsInQuote + 1;

    if (productTotal > capPerUser && Number(stockCapExhausted) !== 1) {
      return existingQuote;
    }
  }
  const quoteMaxQtyAllowed = Number(
    getStoreConfig(quote.storeId, 'quoteProductMaxAddedQty') || 10
  );

  if (matchedSku?.qty < quoteMaxQtyAllowed) {
    const requestedQty = matchedSku.qty + 1;

    quote = await updateItemQty({
      quote,
      requestedQty,
      sku: matchedSku.sku,
      xHeaderToken
    });

    quote.itemsCount = quote?.quoteItem?.length;

    const itemsQty = quote.quoteItem?.reduce((totalQty, quoteItem) => {
      return totalQty + Number(quoteItem.qty);
    }, 0);
    quote.itemsQty = itemsQty;

  } else {
    logInfo(
      `Reached max allowed quantity (${quoteMaxQtyAllowed}) for sku: ${matchedSku.sku}, nothing to perform here`
    );
  }
  return quote;
}



exports.findFlashProductsPerUser = async ({
  quote,
  parentSku,
  flashSaleId,
}) => {
  try {
    if (!quote.customerId) {
      return 0;
    }
    const quotes = await findQuotesByCustomerAndFlash({
      flashSaleId,
      customerId: quote.customerId,
      storeId: quote.storeId,
    });
    return quotes
      ?.filter((qf) => qf.id !== quote.id)
      ?.map((q) => {
        const matchedItems = q.quoteItem.filter(
          (item) => item.parentSku === parentSku
        );
        return matchedItems;
      })
      .flatMap((item) => item)
      .reduce((acc, item) => {
        return acc + item.qty;
      }, 0);
  } catch (error) {
    // console.error(`Error in finding falsh capping per user `);
  }
  return 0;
};