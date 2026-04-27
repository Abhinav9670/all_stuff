const axios = require("axios");
const { logError, logInfo, mappedObject } = require("../helpers/utils");

exports.pushCurofyAnalytics = async ({ quote, xHeaderToken }) => {
  try {
    const endpoint = process.env.PUSH_CUROFY_ANALYTICS_ENDPOINT;
    const reqBody = quote?.quoteItem.map((product) => {
      const {
        qty,
        parentSku: sku,
        sku: childSku,
        flashSaleId,
        rowTotalInclTax : price,
        landedCost,
        caSource = "",
        caSourceType = "",
        caSourceValue = "",
        caBannerPromoName = "",
        caSearchId = "",
        quantityStock,
      } = product;
      const caBannerPromo = typeof caBannerPromoName === 'string' ? caBannerPromoName : "";
      return {
        qty,
        sku,
        childSku,
        flashSaleId,
        price,
        quoteId: quote.id,
        landedCost,
        caSource,
        caSourceType,
        caSourceValue,
        caBannerPromo,
        caSearchId,
        email: xHeaderToken,
        storeId: quote.storeId,
        quantityStock,
      };
    });
    const response = await axios.post(endpoint, reqBody, {
      headers: {
        "Content-Type": "application/json",
      },
    });
    // logInfo("curofy analytics response", response, xHeaderToken);
  } catch (e) {
    logError(e, "Error push data to curofy analytics", xHeaderToken);
  }
};
