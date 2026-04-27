const _ = require("lodash");
const moment = require("moment");
const { COUCHBASE_CLUSTER_BUCKET } = process.env;
const cache = require("memory-cache");
const fetchQuote = require("../../helpers/fetchQuote");
const { inventoryCheck } = require("../../javaApis/product");
const { getProducts } = require("../../elastic/actions");
const {
  getPrice,
  getSpecialPrice,
  getStoreConfig,
  getCurrency,
  logError
} = require('../../helpers/utils');
// const { logger } = require('../../helpers/utils');
const {
  collection,
  initcluster
} = require("../../config/couchbase.js");
/**
 * Process free Shipping webhook and store the details in active quote
 * @param {*} req
 */
exports.processAbandonedCart = async (req) => {
  const apm = global?.apm;
  let span;
  try {
    const cluster = await initcluster();
    const baseConfig = cache.get('baseConfig');
    const startDate = moment().subtract(baseConfig?.abandonedCart?.backStartDays || 2, 'days').format('YYYY-MM-DD');
    const endDate = moment().format('YYYY-MM-DD');
    const query = `select quoteItem, customerId, storeId from ${COUCHBASE_CLUSTER_BUCKET} where isActive=1 and updatedAt > '${startDate}' AND updatedAt < '${endDate}' and customerId <> "" and ARRAY_LENGTH(quoteItem) > 40`;
    const options = { timeout: 75000 }
    span = apm?.startSpan('CB: Query processAbandonedCart', 'db', 'couchbase', 'query');
    if (span) {
      span.setServiceTarget('couchbase', COUCHBASE_CLUSTER_BUCKET);
    }
    const quoteQueryData = await cluster.query(query, options);

    const quotes = (quoteQueryData?.rows || [])
      .filter(quoteDetails => quoteDetails.quoteItem.length > 2)
      .map(quoteDetails => {
        const limitedArray = quoteDetails.quoteItem.slice(0, 3);
        return {
          customerId: quoteDetails.customerId,
          quoteItems: limitedArray.map(e => ({ imgUrl: e.imgUrl, sku: e.sku })),
          storeId: quoteDetails.storeId,
          countryCode: getStoreConfig(quoteDetails.storeId, 'websiteCode').toUpperCase()
        };
      });
    // JSON.stringify('quotes::', quotes);
    return quotes;
  } catch (e) {
    logError('Error in procesing abandoded cart', e);
    return false;
  } finally {
    if (span) span.end();
  }
};
exports.abandonCartDetails = async (req, res) => {
  try {
    const quoteCollection = await collection();
    const cluster = await initcluster();
    const { storeId } = req.body;
    const xHeaderToken = "braze@stylishop.com";
    const customerId = String(req?.body?.customerId || "");
    const websiteIdentifier = getStoreConfig( storeId,'websiteIdentifier');
    if (!customerId){
      // logger.error('abandonCartDetails: Invalid CustomerId for abandoned cart processing');
      return false;
    }
    
    const quote = await fetchQuote.fetchQuote({
      identifier: customerId,
      storeId,
      collection:quoteCollection,
      cluster,
      type: "customer",
      res
    });
    
    if (!quote) {
      // logger.info(`abandonCartDetails: Quote not found for abandoned cart - customerId: ${customerId}, storeId: ${storeId}`);
      return false; // or throw an error
    }
    let optionIds = [];
    let childSkus = [];
    for (const item of quote.quoteItem || []) {
      optionIds.push(item.parentSku);
      childSkus.push(item.sku);
    }

    const promiseArray = [];
    const inventoryRequestObject = {
      skus: childSkus,
      storeId,
    };
    // 0 promise
    promiseArray.push(
      inventoryCheck({
        inventoryRequestObject,
        xHeaderToken,
        orderCreation: false,
      })
    );
    // 1 promise
    promiseArray.push(getProducts({ parentSkus: optionIds, storeId, xHeaderToken }));
    const resPromiseAll = await Promise.all(promiseArray).then((values) => {
      return values;
    });

    const invCheck = resPromiseAll[0];
    const {
      productArr: productDetailsResponse,
    } = resPromiseAll[1];
    let productsArr = [];
    let oosCount = 0;
    for (const item of quote.quoteItem) {
      const inventoryObj = _.find(invCheck, { sku: item.sku.toString() });
      const parentProductId = item.parentProductId;
      let finalPrice = item.priceInclTax;
      let rowTotalInclTax = Number(item.rowTotalInclTax || 0);
      let rowTotalOriginalPrice = Number(item.originalPrice || 0);
      let brand,is_gift = 0,productUrl;
      const currency = getCurrency({ storeId });
      if (productDetailsResponse) {
        const productObj = _.find(productDetailsResponse, { 'objectID': String(parentProductId) });
        brand = productObj?.brand_name;
        const priceObj = productObj?.price;
        if (priceObj && priceObj[currency]) {
          let price = getPrice({ priceObj: priceObj[currency] })
          let specialPrice = getSpecialPrice({ priceObj: priceObj[currency], price })
          const priceInclTax = specialPrice != null ? specialPrice.toFixed(2) : price.toFixed(2);
          rowTotalInclTax = priceInclTax * (Number(item.qty) || 1);
          rowTotalOriginalPrice = price * (Number(item.qty) || 1);
        } else {
          // logger.info(`abandonCartDetails: productObj for product ${JSON.stringify(productObj)}`);
          // logger.info(`abandonCartDetails: Price not found for product ${productObj?.objectID} - ${productObj?.name} in store ${storeId} for currency ${currency}`);
        }
        finalPrice = productObj?.is_gift ? 0 : finalPrice;
        is_gift = productObj?.is_gift ? 1 :0;
        productUrl = productObj?.url;
      }
      if(Number(inventoryObj?.value || 0) < Number(item.qty)){
        oosCount++;
      }
      let product = {
        name:item?.name,
        brand: brand,
        imgUrl: item?.imgUrl,
        parentSku: item.parentSku,
        sku: item.sku,
        quantity : Number(item.qty),
        quantityStock : Number(inventoryObj?.value || 0),
        OSP: Number(rowTotalOriginalPrice || 0).toFixed(2),
        RRP: Number(rowTotalInclTax || 0).toFixed(2),
        size: item.superAttributeLabel,
        currency: currency,
        isGift: is_gift,
        productUrl: productUrl
      };
      productsArr.push(product);
    }
    // logger.info(`abandonCartDetails: OOS count for customer ${customerId}: ${oosCount}`);
    if(productsArr.length){
    const allOOS = productsArr.length == oosCount ? true : false;
    // logger.info(`abandonCartDetails: Product array generated for customer ${customerId}, total products: ${productsArr.length}, allOOS: ${allOOS}`);
    return { 
      products: productsArr,
      allOOS: allOOS,
      country: websiteIdentifier
          };
    }
    return false;
  } catch (e) {
    logError(e,'Error in procesing abandoned cart');
    return false;
  }
};