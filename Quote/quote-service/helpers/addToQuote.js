const _ = require('lodash');
const { getProducts } = require('../elastic/actions');
const { formatQuoteItem, getMetaData } = require('../helpers/product');
const { formatAddress } = require('../helpers/address');
const {
  getCurrency,
  mappedObject,
  getNewQuoteId,
  getStoreConfig
} = require('../helpers/utils');
// const { logger } = require('../helpers/utils');
const { findFlashProductsPerUser } = require('./flashUtils');
const { getSellerWarehouseDetails, fetchFulfillmentMode } = require('../javaApis/product');

exports.addProductToQuote = async ({
  quote,
  storeId,
  source,
  customerId,
  addToQuoteProductsRequests,
  res,
  fromMigrate = false,
  couponCode,
  xHeaderToken,
  warehouseId,
  fulfillmentMode,
  cityId,
  isVersion6 = false
}) => { 
  let error = null;
  const quoteMaxQtyAllowed = Number(
    getStoreConfig(storeId, 'quoteProductMaxAddedQty') || 10
  );

  let isGift = false;
  let productData = {};
  // const outOfStockData = [];
  let quoteMetaData = [];
  let existingQuoteItems = {};
  const skus = [];
  const parentSkus = [];
  let productAddCount = 0;

  if (_.isEmpty(quote)) {
    quote = await createQuote({ customerId, storeId, source });
    quote.customerEmail = xHeaderToken;
  } else {
    existingQuoteItems = mappedObject({
      dataArray: quote.quoteItem,
      mapKey: 'sku'
    });
    quoteMetaData = quote.metadata || [];
  }

  for (const productIndex in addToQuoteProductsRequests) {
    const productInfo = addToQuoteProductsRequests[productIndex];
    parentSkus.push(productInfo.parentSku);
    skus.push(productInfo.sku);
    productData[productInfo.sku] = productInfo;
  }

  const {flashSale, productArr: elasticProducts} = await getProducts({
    parentSkus,
    storeId,
    xHeaderToken
  });
  try {
    if (!_.isEmpty(elasticProducts)) {
      for (const prodSku in productData) {
        const requestData = productData[prodSku];
        const elasticData = elasticProducts?.filter(prod => prod.sku?.includes(requestData.parentSku))?.[0];
        const existingQuoteItem = existingQuoteItems[prodSku];
        const existingQty = Number(existingQuoteItem?.qty || 0);
        const requestedQty = Number(requestData.quantity);
        let toUpdateQty = requestData.overrideQuantity
          ? requestedQty
          : existingQty + requestedQty;
        toUpdateQty = Math.min(Number(toUpdateQty || 1), quoteMaxQtyAllowed);
        let quoteItem = formatQuoteItem({
          elasticData,
          storeId,
          requestData,
          toUpdateQty,
          existingQuoteItem
        });
        if(warehouseId && isVersion6){
          let inventoryResponse = await getSellerWarehouseDetails({ warehouseId });
          let fulfillmentModeResponse = await fetchFulfillmentMode({ skus, storeId, cityId});
          // console.log("inventoryResponse",inventoryResponse);
          // console.log("fulfillmentModeResponse",fulfillmentModeResponse);
          if(fulfillmentMode){
          fulfillmentMode = fulfillmentMode.toLowerCase() === "express" ? "local" : fulfillmentMode;
          }
          if (fulfillmentModeResponse || fulfillmentMode) {
            fulfillmentMode = fulfillmentModeResponse && fulfillmentModeResponse.length > 0 ? fulfillmentModeResponse : fulfillmentMode;
            inventoryResponse = {
              ...inventoryResponse,
              fulfillmentMode: fulfillmentMode
            };
          }
          quoteItem = { ...quoteItem, ...(inventoryResponse || {}) };
        }
        if (flashSale) {
          const flashSaleId = flashSale?.flashSaleId;
          const activeFlashConfig = elasticData?.flashSale?.filter(flash => flash.flashsaleId === flashSaleId)?.[0];
          quoteItem.flashConfig = activeFlashConfig || {};
          const { capPerUser, stockCapExhausted } = activeFlashConfig || {};
          const parentSku = requestData.parentSku;
          
          const productsPurchased = await findFlashProductsPerUser({ quote, parentSku, flashSaleId });

          const productsInQuote = quote.quoteItem
          ?.filter((item) => item.parentSku === parentSku)
          .reduce((acc, item) => acc + item.qty, 0);
    
          const productTotal = productsPurchased + productsInQuote;
          
          if (productTotal >= capPerUser && Number(stockCapExhausted) !== 1) {
            error = {
              status: false,
              statusCode: "500",
              statusMsg: "Flashsale restriction for product.",
            };
            return { quote, error ,isGift};
          }
        }
  
        if (quoteItem === undefined) continue;
        // logger.info('prodSku and quoteItem.sku', { prodSku, quoteItemSku: quoteItem.sku });
        if (prodSku === quoteItem.sku) {
          existingQuoteItems[prodSku] = quoteItem;
          if (_.isEmpty(_.find(quoteMetaData, { sku: quoteItem.parentSku }))) {
            const metaData = getMetaData({ elasticData });
            quoteMetaData.push(metaData);
          }
          productAddCount++;
        }
        if (elasticData.is_gift) {
          isGift = true;
        }
      }
      const finalQuoteitems = Object.values(existingQuoteItems);
  
      const itemsQty = finalQuoteitems.reduce((totalQty, quoteItem) => {
        return totalQty + Number(quoteItem.qty);
      }, 0);
  
      quote.quoteItem = finalQuoteitems;
      quote.itemsCount = finalQuoteitems.length;
      quote.itemsQty = itemsQty;
      quote.metadata = quoteMetaData;
    }
  } catch (error) {
    // logger.error(`addProductToQuote ERROR ${xHeaderToken}: ${error.message}`);
    error = {
      status: false,
      statusCode: '500',
      statusMsg: 'product not found'
    };
  }



  if (productAddCount < 1 && !fromMigrate) {
    error = {
      status: false,
      statusCode: '500',
      statusMsg: 'product not found'
    };
  }

  if (fromMigrate && couponCode) quote.couponCode = couponCode;

  if(fromMigrate) {
    quote.fromMigrate = true;
  }

  return { quote, error ,isGift};
};

const createQuote = async ({ customerId, storeId, source }) => {
  let customer = {};
  let defaultAddress = {};

  const quoteAddress = [
    formatAddress({ address: defaultAddress, email: customer.email })
  ];

  const newQuoteId = await getNewQuoteId();

  const newQuote = {
    id: newQuoteId,
    storeId,
    source,
    customerId: String(customerId || ''),
    customerIsGuest: customerId ? 0 : 1,
    quoteItem: [],
    convertedAt: null,
    isActive: 1,
    itemsCount: 0,
    itemsQty: 0,
    storeToBaseRate: 0,
    baseCurrencyCode: getCurrency({ storeId }),
    storeCurrencyCode: getCurrency({ storeId }),
    grandTotal: 0,
    baseGrandTotal: 0,
    appliedRuleIds: [],
    couponCode: null,
    subtotal: 0,
    baseSubtotal: 0,
    subtotalWithDiscount: 0,
    baseSubtotalWithDiscount: 0,
    autoPromoCode: null,
    autoPromoAmount: 0,
    triggerRecollect: 0,
    amstorecreditUse: null,
    amstorecreditBaseAmount: null,
    amstorecreditAmount: null,
    promoAppliedAt: null,
    quoteAddress,
    quotePayment: {
      method: null,
      additionalInformation: null,
      createdAt: null,
      updatedAt: null
    },
    metadata: [],
    createdAt: new Date()
  };

  return newQuote;
};

exports.validateAddProductReq = ({ addToQuoteProductsRequests }) => {
  const invalidObjects = addToQuoteProductsRequests.filter(addProduct => {
    return !addProduct.sku || !addProduct.parentSku || !addProduct.quantity;
  });
  return (
    addToQuoteProductsRequests.length &&
    typeof addToQuoteProductsRequests === 'object' &&
    invalidObjects.length === 0
  );
};