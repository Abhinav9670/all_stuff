const _ = require("lodash");
const { processPromoCalculations } = require("../../promoApis/v6/processPromo");
const {
  getCurrency,
  getPrice,
  getSpecialPrice,
  getStoreConfig,
  logInfo,
  getChildPrice,
  getChildSpecialPrice,
  getTaxIndia,
} = require("../../helpers/utils");
const { removeItemFromQuote } = require("../../helpers/removeItem");
const { processPriceDrop, getOfferValidity } = require("../surprisePriceDrop");
const { processPriceNudge } = require("../priceNudge");
const { upsertQuote } = require("../upsertQuote");
const { inTaxCalculator, isIntraState } = require("../tax");

// Items rows missing in quote_item - not required
// Product Price changes
// row missing in quote_payment - not required
// External coupon - 24 hrs validity, rest call application check
// Internal coupon 24 hrs validity, available, expiry_date, coupon rule modification is recent than quote modification
exports.checkQuoteIssues = async ({
  quote,
  storeId,
  productDetailsResponse,
  collection,
  xHeaderToken,
  xSource,
  xClientVersion,
  invCheck,
  screenName,
  orderCreation,
  retryPayment = false,
  processPromo = true,
  optimized = false,
  isVersion7 = false
}) => {
  const currency = getCurrency({ storeId });

  let skusToRemove = [];

  const hasSurpriseDrop = getOfferValidity(quote, quote?.priceDropData);
  
  let taxGroups = null;
  let isIntraStateBool = false;
  if (process.env.REGION == "IN") {
    taxGroups = getTaxIndia("taxGroups");
    isIntraStateBool = isIntraState({ address: quote.quoteAddress });
  }

  if (!retryPayment) {
    for (let i = quote.quoteItem?.length - 1; i >= 0; i--) {
      const productObj = _.find(productDetailsResponse, {
        objectID: String(quote.quoteItem[i].parentProductId),
      });

      if (productObj) {
        productObj.configProducts =
          productObj.configProducts === undefined
            ? []
            : productObj.configProducts;
        const childProductObj = _.find(productObj.configProducts, {
          id: String(quote.quoteItem[i].productId),
        });
        if (childProductObj) {
          let price = getChildPrice({ childProductObj });
          let specialPrice = getChildSpecialPrice({ childProductObj, price });

          if (!price) {
            const priceObj = productObj?.price;
            price = getPrice({ priceObj: priceObj[currency] });
            specialPrice = getSpecialPrice({
              priceObj: priceObj[currency],
              price,
            });
          }

          const finalPrice =
            specialPrice != null ? specialPrice.toFixed(2) : price.toFixed(2);

          let priceInclTax = productObj?.is_gift ? 0 : finalPrice;

          const droppedPrice =
            quote?.quoteItem[i]?.droppedPrice &&
            quote?.quoteItem[i]?.droppedPrice?.toFixed(2);
          if (hasSurpriseDrop && droppedPrice > 0) {
            priceInclTax = droppedPrice;
          }

          quote.quoteItem[i].name = productObj.name || quote.quoteItem[i].name;
          quote.quoteItem[i].priceInclTax = priceInclTax;
          quote.quoteItem[i].originalPrice = price.toFixed(2);
          if(specialPrice) {
            quote.quoteItem[i].specialPrice = specialPrice.toFixed(2);
          }
            
          quote.quoteItem[i].rowTotalInclTax =
            priceInclTax * quote.quoteItem[i].qty;
          if (process.env.REGION == 'IN') {
            const taxObjIn = await inTaxCalculator({
              taxGroups,
              hsnCode: quote.quoteItem[i]?.hsnCode,
              priceInclTax,
            });
            quote.quoteItem[i].taxPercent = isIntraStateBool
              ? Number(taxObjIn.IGST || 0)
              : Number(taxObjIn.CGST + taxObjIn.SGST || 0);
            // tax Object create
            const taxObj = {
              taxIGST: isIntraStateBool ? Number(taxObjIn.IGST || 0) : 0,
              taxCGST: isIntraStateBool ? 0 : Number(taxObjIn.CGST || 0),
              taxSGST: isIntraStateBool ? 0 : Number(taxObjIn.SGST || 0),
              intraState: isIntraStateBool,
            };
            quote.quoteItem[i].taxObj = taxObj;
          } else {
            // const taxPercentage = Number(getStoreConfig(storeId, 'taxPercentage') || 0);
            quote.quoteItem[i].taxPercent = Number(
              getStoreConfig(storeId, 'taxPercentage') || 0
            );
          }

          quote.quoteItem[i].isMulin = productObj?.isMulin || false;
          continue;
        }
      }

      skusToRemove.push(quote.quoteItem[i].sku);
    }
  }

  if (skusToRemove.length) {
    // console.log("GWP missing debug log::: remove item from `checkQuoteIssues`", skusToRemove, "quoteId", quote?.id);
    const removeQuoteResponse = await removeItemFromQuote({
      quote,
      skus: skusToRemove,
      collection,
    });
    quote = removeQuoteResponse.quote;
  }

  const currencyConversionRate = Number(
    getStoreConfig(storeId, "currencyConversionRate") || 1
  );
  quote.currencyConversionRate = currencyConversionRate;

  if (!orderCreation && !retryPayment) {
    quote = processPriceDrop({ quote, invCheck, xHeaderToken, xSource });
  }

  if (!retryPayment) {
    if (processPromo) {

      quote = await processPromoCalculations({
        storeId,
        quote,
        productDetailsResponse,
        collection,
        xHeaderToken,
        xSource,
        xClientVersion,
        optimized,
        isVersion7
      });
    }

    // << API-1832
    await processPriceNudge({
      quote,
      screenName,
      xHeaderToken,
      storeId,
      collection,
      optimized
    });
    // API-1832 >>
  }
  
  return quote;
};