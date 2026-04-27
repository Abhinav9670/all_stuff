const cache = require('memory-cache');
const _ = require('lodash');
const axios = require('axios');
const { AxiosError } = require('axios');
const { nativeCalculator, nativeCalculatorIN } = require('../../helpers/v6/quoteNativeCalculator');
const { promoCalculator } = require('../../helpers/v6/quotePromoCalculator');
const {
  logError,
  logInfo,
  getBaseConfig,
  getStoreConfig,
  getStoreLanguage
} = require('../../helpers/utils');
const { filter, sortBy, map, keys, intersection } = require('lodash');

exports.fetchPromoResponse = async ({
  quote,
  coupon,
  xHeaderToken,
  xSource,
  xClientVersion,
  storeId
}) => {
  // logInfo('inside processPromo', '', xHeaderToken);
  quote.coupon = coupon;
  quote.check_for_auto_apply = true;
  if (!quote.quoteAddress) {
    quote.quoteAddress = [];
  }
  const promoBaseURL = process.env.CONSUL_PROMO_BASE_URL;
  const promoRedemptionUrl = cache.get('promoRedemptionUrl');

  // Extract base URL from defaultValidateEndpoint
  let promoUrl = null;
  if (promoRedemptionUrl?.defaultValidateEndpoint) {
    try {
      const url = new URL(promoRedemptionUrl.defaultValidateEndpoint);
      promoUrl = `${url.protocol}//${url.host}`;
    } catch (error) {
      // Fallback to environment variable if URL parsing fails
      promoUrl = process.env.PROMO_URL;
    }
  } else {
    promoUrl = process.env.PROMO_URL;
  }

  const logPrefix = `In fetchPromoResponse endpoint:::${new Date().toISOString()}`
  let finalPromoUrl = promoRedemptionUrl?.defaultValidateEndpoint;

  if (process.env.NODE_ENV === 'local') {
    finalPromoUrl = process.env.PROMO_URL;
  } else {
    if (promoRedemptionUrl.enabled && !promoRedemptionUrl.allowAllStores) {
      if (
        promoRedemptionUrl.allowInternalUsers &&
        xHeaderToken.includes('stylishop.com') &&
        !promoRedemptionUrl.excludeEmailId.includes(xHeaderToken)
      ) {
        finalPromoUrl = promoRedemptionUrl.validateEndpoint;
      } else if (
        promoRedemptionUrl.allowedStores !== null &&
        promoRedemptionUrl.allowedStores.includes(storeId)
      ) {
        finalPromoUrl = promoRedemptionUrl.validateEndpoint;
      }
    }
    if (promoRedemptionUrl.enabled && promoRedemptionUrl.allowAllStores) {
      finalPromoUrl = promoRedemptionUrl.validateEndpoint;
    }
  }

  if (xSource !== 'msite') {
    const clientVersionNumber = xClientVersion?.split('.').join('') || 10000;
    if (Number(clientVersionNumber) <= 306 && promoUrl) {
      finalPromoUrl = `${promoUrl}/v3/coupon/validate`;
    }
  }
  
  try {
      // logInfo('process.env.PROMO_URL', finalPromoUrl, xHeaderToken);
      const headers = {
          'Content-Type': 'application/json',
      };
      const res = await axios.post(finalPromoUrl,quote, { headers });
      // logInfo("PROCESS PROMO RESPONSE::",res)
      return res
  } catch (error) {
    // console.log(logPrefix, "Error in PROCESS PROMO RESPONSE:: - Axios Error",error,error?.message);
      if (error instanceof AxiosError) {
          // Handle Axios-specific errors
          // console.log(logPrefix, "Error in PROCESS PROMO RESPONSE:: - Axios Error instanceof",error,error?.message);
          logError(
              {
                  message: error.message,
                  status: error.response?.status,
                  data: error.response?.data,
                  headers: error.config?.headers,
              },
              'Error fetching promo response:',
              { finalPromoUrl, reqObj: quote }
          );
      } else {
          // console.log(logPrefix, "Error in PROCESS PROMO RESPONSE:: - Axios Unexpected Error",error,error?.message);
          logError(
              { message: error.message },
              'Unexpected error fetching promo response:',
              { finalPromoUrl, reqObj: quote }
          );
      }
    return {};
  }
};

exports.processPromoCalculations = async ({
  storeId,
  quote,
  productDetailsResponse,
  collection,
  xHeaderToken,
  upsert,
  xSource,
  xClientVersion,
  optimized = false,
  isVersion7 = false
}) => {
  try {
    const response = await this.fetchPromoResponse({
      quote,
      coupon: quote.couponCode,
      xHeaderToken,
      xSource,
      xClientVersion,
      storeId
    });
    // logInfo(`processPromoCalculations : response: ' , ${JSON.stringify(response.data)}`);
    const discountObject = response?.data?.response;
    quote = preparePromoGrouping(
      quote,
      response?.data?.response?.bundleGrouping || {},
      storeId
    );
    // logInfo('processPromoCalculations : Promo Response: ', '', response?.data);
    if (
      response?.data &&
      response?.data?.code == 200 &&
      this.promoResponseValid({ quote, promoResponse: response.data?.response })
    ) {
      // logInfo('processPromoCalculations : going to promo cal', '', xHeaderToken);
      quote = await promoCalculator({
        storeId,
        quote,
        collection,
        discountObject,
        xHeaderToken,
        upsert,
        optimized,
        isVersion7
      });
      return quote;
    }
    // logInfo(`processPromoCalculations : quote after promoCalculator',  ${JSON.stringify(quote)}`);
    // logInfo(
    //   'fallback to native cal',
    //   { invalidPromoResponse: response },
    //   xHeaderToken
    // );
    if(process.env.REGION == "IN"){
      quote = await nativeCalculatorIN({
        storeId,
        quote,
        productDetailsResponse,
        collection,
        xHeaderToken,
        upsert,
        prepaidPayable: (discountObject && discountObject.prepaidPayable) || {},
        optimized,
        isVersion7
      });
    }else{
      quote = await nativeCalculator({
        storeId,
        quote,
        productDetailsResponse,
        collection,
        xHeaderToken,
        upsert,
        prepaidPayable: (discountObject && discountObject.prepaidPayable) || {},
        optimized,
        isVersion7
      });
  
    }
    // logInfo(`processPromoCalculations : quote after nativeCalculatorIN',  ${JSON.stringify(quote)}`);
    return quote;
  } catch (e) {
    logError(e, 'Error prcessing promo response : ', xHeaderToken);
    // console.log('Error fetching promo response : ', e.message);
  }
};

exports.promoResponseValid = ({ quote, promoResponse }) => {
  const giftConfig = getBaseConfig('giftProducts') || {};

  const quoteProducts = giftConfig?.excludeFromPromo
    ? quote?.quoteItem?.filter(item => !item.isGift)
    : quote?.quoteItem;

  if (quoteProducts?.length != promoResponse.products?.length) return false;

  let productAndQtyMap = {};
  for (const item of quote.quoteItem) {
    productAndQtyMap[item.sku] = item.qty;
  }

  for (const product of promoResponse.products) {
    const qty = product.qty;
    if (!(product.sku in productAndQtyMap)) return false;
    if (qty != productAndQtyMap[product.sku]) return false;
  }

  return true;
};

const preparePromoGrouping = (quote, groupingData,storeId) => {
  const quoteItems = quote.quoteItem;

  const websiteCode = getStoreConfig(storeId, 'websiteCode');
  const storeLanguage = getStoreLanguage(storeId);
  const config = getBaseConfig('bundleNudge') || {};
  const { translations, bgColor } = config;
  const enabled = config[websiteCode];
  let groupedData = {};
  const appliedData = {};
  const possibileData = {};
  const unGroupedSkus = [];
  const qtyData = {};
  quote.groupedData = groupedData;
  try {
    if (enabled) {
      quoteItems.forEach(item => {
        const { sku, qty } = item;
        qtyData[sku] = qty;
        const gData = groupingData[sku] || {};
        const { applied, possibility } = gData;
        if (_.isEmpty(gData)) {
          unGroupedSkus.push(sku);
        }

        if (!_.isEmpty(applied)) {
          appliedData[applied?.id] = appliedData[applied?.id]
            ? `${appliedData[applied?.id]}_${sku}`
            : sku;
        }
        if (!_.isEmpty(possibility)) {
          possibileData[possibility?.id] = possibileData[possibility?.id]
            ? `${possibileData[possibility?.id]}_${sku}`
            : sku;
        }
      });

      let allAppliedSkus = [];
      for (const index in appliedData) {
        const skuStr = appliedData[index];
        if (!skuStr) continue; // Skip undefined/null values
        const skuArr = skuStr.split('_');
        const firstSku = skuArr[0];
        groupedData[skuStr] = {
          products: skuArr,
          applied: prepeareAppplied(groupingData?.[firstSku]?.applied)
        };

        allAppliedSkus = [...allAppliedSkus,...skuArr];

        //promoID exist in possibilities
        const posData = _.findKey(possibileData, value => value === skuStr);
        if (posData) {
          const prospectData = prepeareProspect(
            quoteItems,
            groupingData?.[firstSku]?.possibility,
            qtyData,
            skuArr
          );
          if (prospectData.addMore > 0) {
            groupedData[skuStr].prospect = prospectData;
          }else{
            groupedData[skuStr].errorProspect = prospectData;
          }
          delete possibileData[posData];
        }
      }

      for (const index in possibileData) {
        const skuStr = possibileData[index];
        if (!skuStr) continue; // Skip undefined/null values
        const skuArr = skuStr.split('_');
        const firstSku = skuArr[0];

        const filteredSkuArr = skuArr.filter((sku) =>  !allAppliedSkus.includes(sku))
        const finalSkuString  = filteredSkuArr.join("_");
        groupedData[finalSkuString] = {
          products: filteredSkuArr
        };

        const prosData = prepeareProspect(
          quoteItems,
          groupingData?.[firstSku]?.possibility,
          qtyData,
          filteredSkuArr
        );

        if (prosData.addMore > 0) {
          groupedData[finalSkuString].prospect = prosData;
        }else{
          groupedData[finalSkuString].errorProspect = prosData;
        }
      }

      if (unGroupedSkus.length)
        groupedData[unGroupedSkus.join('_')] = { products: unGroupedSkus };

      // console.log({ groupedData, possibileData, appliedData });

      quote.groupedData = {
        data: groupedData,
        configs: {
          translations: translations[storeLanguage],
          bgColor
        }
      };
    }
    return quote;
  } catch (e) {
    logError(e, 'Error preparePromoGrouping: ');
  }
};

const prepeareAppplied = promoAppliedData => {
  const { coupon_name, description,collections } = promoAppliedData;
  return {
    name: coupon_name,
    description,
    collections
  };
};

const prepeareProspect = (quoteItems, promoPossibleData, qtyData, skuArr) => {
  const { coupon_name, description, bundleCount, collections } =
    promoPossibleData;
  let context = collections[0];

  // API-2024 budle context value based on last item in prospect - starts
  try {
    const sortedItems = sortBy(
      filter(quoteItems, (el) => skuArr.includes(el.sku)),
      (obj) => new Date(obj.addedAt)
    );
    const lastItemAdded = sortedItems[sortedItems?.length - 1] || {};
    const enrichContexts = filter(keys(lastItemAdded?.enrich), (el) =>
      el.startsWith("c_")
    );
    const enrichContextsWithoutTheC = map(enrichContexts, (el) =>
      el.replace(/c_/g, "")
    );
    context = intersection(
      enrichContextsWithoutTheC,
      collections
    )[0];
    // console.log({ enrichContexts });
  } catch (e) {
    logError(e, "prepeareProspect exception");
  }
  // API-2024 budle context value based on last item in prospect - ends

  const addMore = skuArr.reduce((finalCount, sku) => {
    return (finalCount = finalCount - qtyData[sku]);
  }, bundleCount);
  return {
    name: coupon_name,
    description,
    context,
    addMore: Number(addMore),
  };
};
