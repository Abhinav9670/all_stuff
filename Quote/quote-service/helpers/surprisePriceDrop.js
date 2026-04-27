const {
  getBaseConfig,
  getStoreConfig,
  getArrayRandomValue,
  logInfo,
  logError,
  formatPrice,
  logger,
} = require("./utils");
const _ = require("lodash");
const moment = require("moment");

const applyOffer = ({
  quote,
  offerTimeMinutes,
  priceRange,
  invCheck,
  dropPercent,
  xHeaderToken,
}) => {
  const { quoteItem, priceDropData } = quote;
  const { to: toPrice, from: fromPrice } = priceRange;
  const eligibleItems = quoteItem.filter((item) => {
    const stockQty = _.find(invCheck, { sku: item.sku })?.value;
    //API-3964 Surprise price drop coupon enhancement
    if (getBaseConfig("surprizepricedrop_oncoupon")) {
      // Ensure 'appliedCouponValue' exists and contains items
      const checkAutoCoupon =
        item?.appliedCouponValue?.length > 0 &&
        item?.appliedCouponValue.filter((coupon) => coupon.type === "AUTO")
          .length > 0;
      // logger.info(
      //   `Surprise price drop: checkAutoCoupon for item ${item.sku}: ${checkAutoCoupon}`
      // );
      return (
        !checkAutoCoupon && // Check if the coupon has 'AUTO' type
        stockQty > 0 && // Ensure stock is greater than 0
        Number(item.qty) === 1 &&
        Number(item.priceInclTax) >= fromPrice && // Ensure price is greater than or equal to 'fromPrice'
        Number(item.priceInclTax) <= toPrice // Ensure price is less than or equal to 'toPrice'
      );
    } else {
      return (
        item?.appliedCouponValue?.length === 0 &&
        stockQty > 0 &&
        Number(item.qty) === 1 &&
        Number(item.priceInclTax) >= fromPrice &&
        Number(item.priceInclTax) <= toPrice
      );
    }
  });

  // logger.info(
  //   `Surprise price drop: All eligible items for quote ${
  //     quote?.id
  //   }: ${JSON.stringify(eligibleItems)}`
  // );

  if (eligibleItems.length) {
    const finalItem = getArrayRandomValue(eligibleItems);
    const { priceInclTax } = finalItem;
    finalItem.actualPrice = priceInclTax;
    const droppedPrice = formatPrice(
      Number(priceInclTax) - (Number(dropPercent) / 100) * Number(priceInclTax)
    );
    finalItem.priceInclTax = droppedPrice;
    finalItem.droppedPrice = droppedPrice;
    const noOfferQuoteItems = quoteItem.filter(
      (item) => item.sku !== finalItem.sku
    );
    const updatedQuoteItems = [finalItem, ...noOfferQuoteItems];
    quote.quoteItem = updatedQuoteItems;
    const offerStart = new Date();
    const offerEnd = moment(offerStart).add(offerTimeMinutes, "minutes");

    const pdData = {
      ...priceDropData,
      offerTime: offerTimeMinutes,
      offerStart,
      offerTimeMinutes: offerTimeMinutes,
      offerEnd,
      offersCompleted: Number(priceDropData.offersCompleted || 0) + 1,
      usingWebhookConfig: !_.isEmpty(quote.priceDropConfig),
    };
    quote.priceDropData = pdData;
    // logInfo(
    //   `price drop applied : ${quote?.id}. Details : ${JSON.stringify(pdData)}`,
    //   null,
    //   xHeaderToken
    // );
  }

  return quote;
};

const removeOffer = ({ quote, xHeaderToken }) => {
  const quoteItems = quote?.quoteItem?.map((item) => {
    return {
      ...item,
      // priceInclTax: item.actualPrice || item.priceInclTax,
      droppedPrice: 0,
    };
  });

  quote.quoteItem = quoteItems;
  const currentPriceDropData = quote.priceDropData || {};
  quote.priceDropData = { ...currentPriceDropData, offerTime: 0 };

  // logInfo(
  //   "price drop removed",
  //   { ...quote.priceDropData, quoteId: quote?.id },
  //   xHeaderToken
  // );

  return quote;
};

const updateOfferData = ({ priceDropData = {}, xHeaderToken, quoteId }) => {
  const { offerEnd } = priceDropData;
  const offerEndMoment = offerEnd ? moment(offerEnd) : null;
  const momentCurrentDay = moment();
  const offerRemainingMinutes = offerEndMoment
    ? moment.duration(offerEndMoment.diff(momentCurrentDay))?.asMinutes()
    : 0;
  const updatedPriceDropData = {
    ...priceDropData,
    offerTime: offerRemainingMinutes,
  };

  // logInfo(
  //   "price drop updated",
  //   { ...updatedPriceDropData, quoteId },
  //   xHeaderToken
  // );
  return updatedPriceDropData;
};

exports.getOfferValidity = (quote, priceDropData = {}) => {
  let valid = false;
  const momentCurrentDay = moment();
  const { offerEnd } = priceDropData;
  const offerEndMoment = offerEnd ? moment(offerEnd) : null;
  const offerEndDays = offerEndMoment
    ? moment.duration(offerEndMoment.diff(momentCurrentDay))?.asDays()
    : 0;

  if (offerEndDays > 0) valid = true;
  else if (priceDropData.usingWebhookConfig) {
    quote.priceDropConfig = {};
    quote.priceDropData.usingWebhookConfig = false;
  }

  return valid;
};

const getQuoteTotal = ({ currentItems, invCheck }) => {
  return currentItems?.reduce((total, item) => {
    const stockQty = _.find(invCheck, { sku: item.sku })?.value;
    if (Number(stockQty) > 0) {
      total += Number(item.rowTotalInclTax);
    }
    return total;
  }, 0);
};

const fetchPriceDropData = ({ quote, total, xSource }) => {
  if (quote?.priceDropConfig && !_.isEmpty(quote.priceDropConfig))
    return fetchWebhookPriceDropData({ quote, total, xSource });

  let applicable = false;
  const {
    storeId,
    createdAt,
    updatedAt,
    priceDropData = {},
    customerId,
  } = quote;
  const {
    offersCompleted = 0,
    offerStart,
    offerEnd,
    offerTime,
  } = priceDropData;
  let isFirstOffer = !offerEnd;

  const momentCurrentDay = moment();
  const createdAtMoment = moment(createdAt);
  const updatedAtMoment = moment(updatedAt);
  const offerEndMoment = offerEnd ? moment(offerEnd) : null;

  // offer expired how much time ago
  const offerEndDays = offerEndMoment
    ? moment.duration(momentCurrentDay.diff(offerEndMoment))?.asDays()
    : 0;

  const createdAtDays = moment
    .duration(momentCurrentDay.diff(createdAtMoment))
    ?.asDays();

  const updatedAtDays = moment
    .duration(momentCurrentDay.diff(updatedAtMoment))
    ?.asDays();

  const websiteCode = getStoreConfig(storeId, "websiteCode");
  const surprizeConfig =
    getBaseConfig("surprizePriceDrop")?.[websiteCode] || {};
  const {
    enabled,
    webEnabled,
    minQuoteVal,
    updatedAtMinDays,
    createdAtMaxDays,
    repeatIntervalDays,
    offerTimeMinutes,
    offerMaxCount,
    priceRange,
    dropPercent,
  } = surprizeConfig;

  let finalEnabled = enabled && !!customerId;

  if (
    finalEnabled &&
    createdAtDays > createdAtMaxDays &&
    updatedAtDays < updatedAtMinDays &&
    (isFirstOffer || offerEndDays > repeatIntervalDays) &&
    total > minQuoteVal &&
    offersCompleted < offerMaxCount
  ) {
    applicable = true;
  }

  return {
    applicable,
    offerTimeMinutes,
    priceRange,
    dropPercent,
    enabled: finalEnabled,
  };
};

exports.processPriceDrop = ({ quote, invCheck, xHeaderToken, xSource }) => {
  try {
    const { quoteItem: currentItems, priceDropData = {} } = quote;
    const offerValid = this.getOfferValidity(quote, priceDropData);
    const total = getQuoteTotal({ currentItems, invCheck });
    const { applicable, offerTimeMinutes, priceRange, dropPercent, enabled } =
      fetchPriceDropData({
        quote,
        total,
        xSource,
      });

    if (!enabled) {
      quote = removeOffer({ quote, xHeaderToken });
      return quote;
    }

    if (!offerValid) {
      quote = removeOffer({ quote, xHeaderToken });
    } else {
      const updatePriceDropData = updateOfferData({
        priceDropData,
        xHeaderToken,
        quoteId: quote?.id,
      });
      quote.priceDropData = updatePriceDropData;
    }

    if (applicable && !offerValid) {
      quote = applyOffer({
        quote,
        offerTimeMinutes,
        priceRange,
        invCheck,
        dropPercent,
        xHeaderToken,
      });
    }
  } catch (e) {
    logError(e, "Error Applying Surpise price drop", xHeaderToken);
  }
  return quote;
};

const fetchWebhookPriceDropData = ({ quote, total, xSource }) => {
  const priceDropConfig = quote.priceDropConfig;
  const { offersCompleted = 0 } = quote?.priceDropData || {};

  let enabled = true;

  const applicable =
    total > Number(priceDropConfig?.minBagValue) &&
    offersCompleted <= Number(priceDropConfig?.offerMaxCount);

  return {
    applicable,
    offerTimeMinutes: Number(priceDropConfig?.offerTimeMinutes),
    priceRange: {
      from: Number(priceDropConfig?.priceFrom),
      to: Number(priceDropConfig?.priceTo),
    },
    dropPercent: Number(priceDropConfig?.dropPercent),
    enabled,
  };
};
