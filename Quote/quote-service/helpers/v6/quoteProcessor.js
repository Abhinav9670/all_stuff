const _ = require("lodash");
const { getProducts } = require("../../elastic/actions.js");
const { checkQuoteIssues } = require("../../helpers/v6/checkQuoteIssues");
const {
  inventoryCheckFromDB,
  inventoryCheck,
} = require("../../javaApis/product");
const { getCustomerStoreCredit } = require("../../javaApis/customer");
const {
  getCurrency,
  getAppConfig,
  getAppConfigKey,
  getStoreConfig,
  formatPrice,
  getPrice,
  getSpecialPrice,
  logInfo,
  sanitiseImageUrl,
  getChildPrice,
  getChildSpecialPrice,
  logError,
  paymentMethodsFromConfig,
  getShukranPointConversion,
  getBaseConfig
} = require("../../helpers/utils");
// const { logger } = require("../../helpers/utils");
const {
  getMetaData,
  getContextId,
  checkIsGwp,
  sortProducts,
} = require("../../helpers/product");
const { getOrderedCount } = require("../../javaApis/orderedCount");
const { upsertQuote } = require("../../helpers/upsertQuote");
const { calcQuoteDonation } = require("../../helpers/donation");
const cache = require("memory-cache");
const { processCoinCalculations } = require("../../easApis/getStoreCoin");
const { processBurnShukranCoinCalculations } = require("../../shukran/getShukranPoint");
const { evaluateFreeShipping } = require("./freeShipping.js");
const { processFreeGift, isVersionCompatible } = require("../freeGifts/index.js");
const { resetNotifications } = require("../notifications/index.js");
const { forEach, filter, keys } = require("lodash");
const { updateCustomerWishlist } = require("../../helpers/customer");
const { collection } = require("../../config/couchbase.js");
const { findFlashProductsPerUser } = require("../flashUtils.js");
const { setStyliCashBurn, setStyliCashApplicableTotal } = require("../styliCash.js");
const {calculateHighestSLA} = require("./appendCityDetails");


const checkForTheLatestVersion = (
  { currentVersion,
    latestVersion }
) => {
  try {
  currentVersion = getBaseConfig('VersionConfigs')?.['giftRemoveVersion'] || '5.3.000'
  if(latestVersion.startsWith('v')){
    return false
  }
  const v1 = latestVersion.split('.').map(Number);
  const v2 = currentVersion.split('.').map(Number);

  const maxLength = Math.max(v1.length, v2.length);

  for (let i = 0; i < maxLength; i++) {
    const a = v1[i] ?? 0;
    const b = v2[i] ?? 0;

    if (a > b) return true;
    if (a < b) return false;
  }

  return false;
  } catch (error) {
    return false;
  }
}


const addDataFromElastic = async ({
  elasticData,
  flashSale,
  quote,
  storeId,
  token,
  xHeaderToken,
  invCheck,
  shukranEnabled,
  tierName,
  newFlashSale,
  optimized = false,
  xClientVersion
}) => {
  const updatedQuoteItems = [];
  const quoteMetaData = [];
  const wishListArr = [];
  const isCustomerGuest = quote.customerIsGuest === 1;
  const guestOosItems = [];
  let response = {};
  let quoteCollection;
  if(!optimized){
    quoteCollection = await collection();
  }

  // API-3732
  const customerId = quote?.customerId || 0;
  // API-3732 END

  
   const currentVersion = getBaseConfig('VersionConfigs')?.['giftRemoveVersion'] || '5.3.000'
   const LatestVersion = checkForTheLatestVersion({ currentVersion, latestVersion: xClientVersion });
  //  console.log("LatestVersionv2", LatestVersion);

  if (quote?.quoteItem && quote.quoteItem.length > 0) {
    for (const quoteItem of quote.quoteItem) {
      const productObj = _.find(elasticData, {
        objectID: String(quoteItem.parentProductId),
      });
      processQuoteItem({
        quoteItem,
        productObj,
        updatedQuoteItems,
        wishListArr,
        quoteMetaData,
        invCheck,
        isCustomerGuest,
        guestOosItems,
        storeId,
        customerId,
        tierName,
        shukranEnabled,
        LatestVersion
      });
    }

    quote.showOOSWishlist =
      isCustomerGuest && guestOosItems.length ? true : false;
    quote.oosItemCount = isCustomerGuest ? guestOosItems.length : 0;
    if (wishListArr.length >= 1 && quote.customerId) {
      const reqBody = {
        customerId: quote?.customerId,
        storeId,
        wishList: wishListArr,
      };
      // logger.info(`updateCustomerWishlist: Calling update wishlist for customer ${quote?.customerId}, items count: ${wishListArr.length}`);
      updateCustomerWishlist(reqBody, xHeaderToken, token);
      quote.showOOSWishlist = true;
      quote.oosItemCount = wishListArr.length;
    }

    const itemsQty = updatedQuoteItems?.reduce((totalQty, quoteItem) => {
      return totalQty + Number(quoteItem.qty);
    }, 0);
    

    quote.newFlashSale = newFlashSale;
    quote.itemsCount = updatedQuoteItems?.length;
    quote.itemsQty = itemsQty;
    quote.quoteItem = updatedQuoteItems;
    quote.flashSale = flashSale;
    quote.quoteMetaData = quoteMetaData;

    if(!optimized){
      await upsertQuote({ storeId, quote, collection:quoteCollection, xHeaderToken });
    }
  }
  const hits = quote?.quoteItem || {};
  let productArr = hits?.map(product => ({
    ...product,
    objectID: String(product.objectID),
  })) || [];
  if(!quote.fromMigrate) {
    // Process gift product filtering
    ({ productArr, isRemoved = false } = processGiftProductFiltering(productArr, hits, quote, storeId, xHeaderToken));
    const message = getBaseConfig('notificationMessage')?.['giftRemoved'] ?? 'Selected Gift removed due to Insufficient cart value.';
    if (isRemoved) {
      if(!quote.notifications) quote.notifications = [];
      quote.notifications.push({
        screen: 'bag',
        name: 'giftRemoved',
        content: {
          message,
          bgColor: "#E4ECF1",
          color: "#1C759B",
        },
        type: 'strip'
      })
    }
  }
  quote.quoteItem = productArr;
  return quote;
};

// Helper function to calculate shukran earn amount
const calculateShukranEarn = (itemPrice, shukranBurnInCurrency, convertFactor, item) => {
  const styliCashBurnInCurrency = item?.styliCashBurnInCurrency || 0;
  const earnAmount = convertFactor * (itemPrice - shukranBurnInCurrency - styliCashBurnInCurrency);
  return Math.max(0, Math.round(earnAmount));
};

const setShukranEarn = ({childProductData, item, storeId, tierName}) => {
  const childProductObj = childProductData;
  const price = getChildPrice({ childProductObj });
  const specialPrice = getChildSpecialPrice({ childProductObj, price });
  const couponApplied = item?.appliedCouponValue || [];
  const rowTotalInclTax = item?.rowTotalInclTax || 0;
  const isFlashSaleProduct = item?.flashSale || false;
  const droppedPrice = item?.droppedPrice || 0;
  
  let voucherTotal = 0;
  let isSale = 0; //Default marking as non sale
  couponApplied.forEach((coup) => {
    if(coup?.isGiftVoucher && coup?.type == 'MANUAL'){
      voucherTotal += Number(coup?.discount) || 0;
    }
    if(coup?.type == 'AUTO') {
      isSale = 1; //If coupon applied type if AUTO, then mark it as sale
    }
  });
  if((isSale == 0) && (specialPrice != null)){ //If Base price and Special price are not equal, mark as sale
    isSale = 1;
  }
  if(droppedPrice > 0){
    isSale = 1;
  }
  if(isFlashSaleProduct){
    isSale = 1;
  }
  
  let itemPrice = (droppedPrice > 0 ? droppedPrice * (item?.qty || 1) : rowTotalInclTax); //Calculating line total of item
  const discountAmount = (item?.discountAmount || 0) - Number(voucherTotal);
  if(discountAmount > 0){
    itemPrice -= discountAmount; //Subtract coupon discount if any for that product
  }
  
  let shukranEarn = 0;
  let shukranBasicEarn = 0;
  let shukranBonousEarn = 0;
  const {environments = {}, shukranBonus = {}} = getAppConfig() || {};

  const appConfig = {};
  environments?.[0]?.stores.map(v => {
    appConfig[v.storeId] = v;
  });

  const shukranConfig = appConfig[storeId]?.shukranConfig || {};
  const bonusVal = shukranBonus?.[tierName] || 0;
  let shukranBurnInCurrency = item?.shukranBurnInCurrency || 0;
  if (shukranConfig && Object.keys(shukranConfig).length > 0 && shukranConfig.constructor === Object) {      
    const isSaleConfig = isSale == 1 ? shukranConfig?.sale : shukranConfig?.non_sale;
    const convertFactor = isSaleConfig?.conversion_factor || 0;

    shukranEarn = calculateShukranEarn(itemPrice, shukranBurnInCurrency, convertFactor, item);
    shukranBasicEarn = shukranEarn;    
    if (bonusVal > 0) {
    shukranBonousEarn = shukranEarn * (bonusVal / 100);
    shukranBonousEarn = Math.floor(shukranBonousEarn); // Truncate to integer part

    shukranEarn *= (1 + (bonusVal / 100)); // Adding bonus value to shukranEarn if any
  }

    // Truncate shukranEarn to its integer part
    shukranEarn = Math.floor(shukranEarn);

  }
  item['shukranEarn'] = shukranEarn;
  item['shukranBasicEarn'] = shukranBasicEarn;
  item['shukranBonousEarn'] = shukranBonousEarn;
  item['isSale'] = isSale;
}

const processQuoteItem = ({
  quoteItem,
  productObj,
  updatedQuoteItems,
  wishListArr,
  quoteMetaData,
  invCheck,
  isCustomerGuest,
  guestOosItems,
  storeId,
  customerId,
  tierName,
  shukranEnabled,
  LatestVersion
}) => {
  let itemMetaData = {};
  const childProductData =
    productObj?.configProducts?.filter(
      (configProd) => configProd.sku === quoteItem.sku
    )[0] || {};
  if (
    _.isEmpty(_.find(quoteMetaData, { sku: quoteItem.parentSku })) &&
    productObj
  ) {
    const metaData = getMetaData({ elasticData: productObj });
    itemMetaData = metaData;
    quoteMetaData.push(metaData);
  }
  let enrich = productObj?.enrich || {};
  try {
    const enrichArrFromRoot = filter(keys(productObj), (el) =>
      el.startsWith("c_")
    );
    forEach(enrichArrFromRoot, (el) => (enrich[`${el}`] = 1));
  } catch (e) {
    // logger.error(`Error while fetching enrich from root for quote ${quote.id}: ${e.message}`);
  }
  const inventoryObj = _.find(invCheck, { sku: quoteItem.sku.toString() });
  
  const objValues = {
    ...quoteItem,
    enrich,
    flashSale: productObj?.flash_sale,
    flashSaleId: productObj?.flashSaleId,
    superAttributeId: childProductData.superAttributeId || "",
    superAttributeValue: childProductData.sizeOptionId || "",
    superAttributeLabel: childProductData.size || "",
    soldBy: productObj?.sold_by,
    brandName: productObj?.brand_name,
    productCalloutTag: productObj?.productCalloutTag,
    metadata: itemMetaData,
    hsnCode: productObj?.hsn_code,
    disableSpecialPrice: false,
    l4_category: productObj?.categoryIds[3] || "",
    isDangerousProduct:
      Boolean(
        productObj?.is_dangerous_product === 1 ||
          productObj?.is_dangerous_product === true ||
          productObj?.productAttributes?.is_dangerous_product === 1 ||
          productObj?.productAttributes?.is_dangerous_product === true
      ) ||
      quoteItem?.isDangerousProduct ||
      false,
    shortDescription:
      productObj?.short_description ||
      productObj?.productAttributes?.short_description ||
      quoteItem?.shortDescription ||
      "",
    variantSku:
      childProductData?.variant_sku ||
      productObj?.variant_sku ||
      productObj?.productAttributes?.variant_sku ||
      quoteItem?.variantSku ||
      "",
  };
  if (
    Number(inventoryObj?.value) <= 0 ||
    Number(quoteItem?.qty) > Number(inventoryObj?.value)
  ) {
    if (isCustomerGuest) {
      if(LatestVersion && quoteItem.isGift){
        objValues.isOOS = true;
        objValues.notificationType='out_of_stock';
        updatedQuoteItems.push(objValues);
      }
      else{
       guestOosItems.push(objValues);
      }
    } else {
      const wishlistObj = {
        parentProductId: quoteItem?.parentProductId,
        productId: quoteItem?.productId,
        comments: "",
        quantity: 1,
        wishListItemId: 0,
      };
      if(LatestVersion && quoteItem.isGift){
        objValues.isOOS = true;
        objValues.notificationType='out_of_stock';
        updatedQuoteItems.push(objValues);
      }else{
        wishListArr.push(wishlistObj);
      } 
    }
  } else {
    updatedQuoteItems.push(objValues);
  }
};

exports.processQuote = async function ({
  storeId,
  quote,
  collection,
  orderCreation,
  xHeaderToken,
  token,
  pool,
  xSource,
  xClientVersion,
  bagView,
  statusCoinApllied,
  screenName,
  resetNotifs,
  retryPayment = false,
  shukranEnabled,
  appliedShukranPoint,
  shukranAvailablePoint,
  shukranAvailableCashValue,
  tierName,
  orderSplitFlag,
  isVersion7 = false
}) {
  quote.showOOSWishlist = false;
  quote.oosItemCount = 0;
  // logInfo("in processQuote", "", xHeaderToken);
  // logInfo("in processQuote shukranEnabled : ", shukranEnabled);
  const quoteId = quote.id;

  const currencyConversionRate = Number(
    getStoreConfig(storeId, "currencyConversionRate") || 1
  );
  const shukranPointConversion = Number(
    getStoreConfig(storeId, "shukranPointConversion") || getShukranPointConversion(storeId)
  );
  const logPrefix = `processQuote:::${new Date().toISOString()}`;
  if(!collection){
    // logger.error(`${logPrefix} Couchbase collection missing`)
  }
  const baseConfig = cache.get("baseConfig") || {};
  // logInfo('baseConfig', baseConfig);
  const taxPercentage = Number(getStoreConfig(storeId, "taxPercentage") || 0);
  // Get address/payment objects
  const addressObject =
    _.filter(
      quote.quoteAddress || [],
      (el) => el.addressType == "shipping"
    )[0] || {};
  addressObject.cityMapper = quote.city || {};
  const paymentObject = quote.quotePayment;
  const isShukranEnable = getStoreConfig(storeId, 'isShukranEnable') || false;
    
  // Fetch product details from elasticSearch
  let parentSkus = [];
  let childSkus = [];
  let products = [];
  let productsMap = {};

  for (const item of quote.quoteItem || []) {
    parentSkus.push(item.parentSku);
    childSkus.push(item.sku);
    products.push({
      productId: item.productId,
      sku: item.sku,
    });
    productsMap[item.productId] = item.sku;
  }
  const inventoryRequestObject = {
    skus: childSkus,
    storeId,
  };

  // logInfo("parentSkus", parentSkus, xHeaderToken);

  // promises start
  const promisesArray = [];
  if (!retryPayment) {
    promisesArray.push(getProducts({ parentSkus, storeId, xHeaderToken }));
  }

  // promisesArray.push(
  //   inventoryCheckFromDB({ productsMap, orderCreation, pool, xHeaderToken })
  // );
  promisesArray.push(
    inventoryCheck({ inventoryRequestObject, xHeaderToken, orderCreation })
  );

  if (quote.customerId)
    promisesArray.push(
      getCustomerStoreCredit({
        customer: quote.customerId,
        pool,
        xHeaderToken,
      })
    );
  /*if (quote.customerId)
    promisesArray.push(
      getStoreCoinBalance({ customerId: quote.customerId, quote, token, xHeaderToken })
    );*/
  const resPromiseAll = await Promise.all(promisesArray).then((values) => {
    return values;
  });
  // promises end

  const {
    productArr: productDetailsResponse,
    flashSale,
    newFlashSale,
    errorMessage,
  } = resPromiseAll[0];

  const isSLAEnabled = getStoreConfig(storeId, 'isSLAEnabled') || false;

  if (isSLAEnabled) {
    try {
      // UIB-7055 as part sla check we are calling inventory check API default false we are sending orderCreation: false
      const invCheckResult = await inventoryCheck({ inventoryRequestObject, xHeaderToken, orderCreation: false });
      const uniqueWarehouseIds = [...new Set(invCheckResult?.filter(item => item?.warehouseId)?.map(item => item.warehouseId) || [])];

      if (uniqueWarehouseIds.length && addressObject?.cityMapper?.id) {
        // logger.info("warehouseIds", uniqueWarehouseIds);
        const slaDetails = await calculateHighestSLA({
          uniqueWarehouseIds,
          cityId: addressObject.cityMapper.id,
          countryId: addressObject.cityMapper.countryId
        });
        // logger.info("slaDetails", slaDetails);
        if (slaDetails) addressObject.cityMapper = slaDetails;
      }
    } catch (error) {
      // logger.error("SLA processing error:", error.message);
    }
  }

  if (errorMessage) {
    return {
      status: false,
      statusCode: "204",
      statusMsg: errorMessage,
    };
  }

  const productIdsInQuote = quote.quoteItem?.map((el) => el.productId);
  let productIdsInElasticResponse = [];
  for (let key in productDetailsResponse) {
    if (productDetailsResponse.hasOwnProperty(key)) {
      const product = productDetailsResponse[key];
      product.configProducts?.map((el) => {
        productIdsInElasticResponse.push(el.id);
      });
    }
  }

  if (
    orderCreation &&
    !retryPayment &&
    !productIdsInQuote.every((v) => productIdsInElasticResponse.includes(v))
  ) {
    return {
      status: false,
      statusCode: "203",
      statusMsg: "products mismatch!",
    };
  }

  // logInfo("quote.totalOrderedCount", quote.totalOrderedCount, xHeaderToken);
  if (quote.totalOrderedCount === undefined && !retryPayment)
    quote.totalOrderedCount = await getOrderedCount({
      quote,
      customerId: quote.customerId,
      xHeaderToken,
      storeId,
      token,
      pool,
    });

  const invCheck = resPromiseAll[1];

  if (!retryPayment) {
    quote = await addDataFromElastic({
      elasticData: productDetailsResponse,
      flashSale,
      quote,
      storeId,
      xHeaderToken,
      token,
      invCheck,
      shukranEnabled,
      tierName,
      newFlashSale,
      xClientVersion
    });
  }
// handleBnplIssue
const handleBnplIssue = baseConfig?.handleBnplIssue || false;
const isInstallmentMethod = ["tamara_installments_3", "tabby_installments"].includes(paymentObject.method);
// logger.info(`processQuote: Quote ${quote.id} - isInstallmentMethod: ${isInstallmentMethod}, orderCreation: ${orderCreation}, handleBnplIssue: ${handleBnplIssue}, paymentObject: ${JSON.stringify(paymentObject)}`);
  // handle issue with qutoe and promo and/or calculations - tabby Or tamara
  if (handleBnplIssue && orderCreation && isInstallmentMethod) {
    // logger.info(`processQuote: Quote ${quote.id} - BNPL issue handling enabled, skipping quote issues check`);
  } else {
    quote = await checkQuoteIssues({
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
      retryPayment,
      isVersion7
    });
  } 
  
  if (!quote) return null;
  // calculation for styli coin
  if (quote.customerId && statusCoinApllied != undefined && !retryPayment) {
    quote = await processCoinCalculations({
      storeId,
      quote,
      collection,
      upsert: true,
      token,
      xHeaderToken,
      statusCoinApllied,
    });
    // console.log(quote.coinDiscountData);
    if (!quote) return null;
  }

  if (shukranEnabled && !retryPayment){
    quote = await processBurnShukranCoinCalculations({        
      quote, 
      appliedShukranPoint,
      shukranAvailablePoint,
      shukranAvailableCashValue,
      storeId
    }); 
    if (!quote) return null;
  }

  if (!retryPayment) {
    quote = await processFreeGift({
      quote,
      invCheck,
      xHeaderToken,
      xClientVersion,
      screenName,
      collection,
      bagView
    });
  }

  let storeCreditObject;
  let storeCreditObjectStoreValue;
  let storeCreditIndex = 2;
  if (retryPayment) {
    storeCreditIndex = 1;
  }
  const storeCreditResponse = resPromiseAll[storeCreditIndex];
  if (quote.customerId && storeCreditResponse) {
    const [rows, fields] = storeCreditResponse;
    storeCreditObject = rows[0]?.store_credit;
    if (storeCreditObject)
      storeCreditObjectStoreValue = formatPrice(
        storeCreditObject / currencyConversionRate
      );
  }

  let totalTaxAmount = 0;
  let totalPrice = 0;
  let productsArr = [];

  for (const item of quote.quoteItem) {
    const currency = getCurrency({ storeId });

    const inventoryObj = _.find(invCheck, { sku: item.sku.toString() });

    let product = {};
    let productObj = {};
    let childProductObj = {};
    product.sizes = [];

    if (!retryPayment) {
      const parentProductId = item.parentProductId;
      productObj = _.find(productDetailsResponse, {
        objectID: String(parentProductId),
      });

      const priceObj = productObj?.price;

      childProductObj = _.find(productObj?.configProducts || [], {
        id: String(item.productId),
      }) || {};

      let price = getChildPrice({ childProductObj });
      let specialPrice = getChildSpecialPrice({ childProductObj, price });

      if (!price) {
        price = getPrice({ priceObj: priceObj[currency] });
        specialPrice = getSpecialPrice({
          priceObj: priceObj[currency],
          price,
        });
      }

      price = price.toFixed(2);
      if (specialPrice) specialPrice = specialPrice.toFixed(2);

      product.productStatus = !productObj.isDisabled;
      product.brandName = productObj?.brand_name;
      product.landedCost = String(productObj?.original_base_price || 0);

      product.isOOS = item.isOOS || false;
      product.notificationType = item.notificationType ?? '';
      product.images = {
        image: sanitiseImageUrl(productObj?.image_url),
      };

      if ("returnable" in productObj) {
        product.returnable = Boolean(productObj?.returnable);
      }

      if ("returnable" in productObj) {
        product.isReturnable = Boolean(productObj?.returnable);
      } else if ("isReturnApplicable" in productObj) {
        product.isReturnable = Boolean(productObj?.isReturnApplicable);
      }

      if (specialPrice && !item.disableSpecialPrice) {
        const discount = parseFloat(((price - specialPrice) / price) * 100);
        const discountPercentage = parseInt(Math.round(discount));
        product.discount = String(discountPercentage);
      } else {
        product.discount = "0";
      }
      product.landedCost = String(productObj?.original_base_price || 0);
      product.gender = String(productObj?.gender || "");
      if (productObj?.isDisabled) product.quantityStock = "0";

      if (productObj?.configProducts?.length) {
        const sizeArray = _.map(productObj.configProducts, (el) => {
          return {
            label: el.size,
            sku: el.sku,
            productId: el.id,
          };
        });
        product.sizes = sizeArray;
      }

      product.prices = {
        price: String(formatPrice(price).toFixed(2)),
        specialPrice:
          specialPrice && !item.disableSpecialPrice
            ? String(formatPrice(specialPrice).toFixed(2))
            : null,
        droppedPrice: !item.disableSpecialPrice
          ? String(formatPrice(item.droppedPrice).toFixed(2))
          : null,
      };

      totalPrice += price * item.qty;
    }

    product.parentProductId = item.parentProductId;
    product.productId = item.productId;
    product.parentSku = item.parentSku;
    product.sku = item.sku;
    product.name = item.name;
    product.cartItemPriceDrop = item.cartItemPriceDrop || 0;
    product.l4_category = item?.l4_category || '';
    product.qtySold = await qtySold(item?.qtySold, baseConfig);
    product.soldBy = item?.soldBy || "";
    
    if (retryPayment) {
      product.brandName = item.brandName;
      product.landedCost = item.landedCost;
      product.prices = {
        price: String(item.price),
        specialPrice: String(item.specialPrice),
        droppedPrice: String(item.droppedPrice),
      };
      product.discount = String(item.discountAmount);
      product.landedCost = String(item.landedCost);
      product.gender = item.gender;
      totalPrice += item.price * item.qty;
    }

    if (item.isGift) {
      product.isGift = true;
      product.prices.specialPrice = String(formatPrice(0).toFixed(2));
    }

     // Optimized: Batch assign order split properties if needed
     if(orderSplitFlag){
      Object.assign(product, {
        midMileLocationName: item?.midMileLocationName,
        firstMileLocationId: item?.warehouseId,
        firstMileLocationName: item?.warehouseName,
        fulfillmentType: item?.fulfillmentType,
        shipmentType: item?.shipmentType,
        sellerId: item?.sellerId,
        sellerName: item?.sellerName,
        fulfilmentMode: item?.fulfilmentMode,
        warehouseId: item?.warehouseId,
        city: item?.city,
        country: item?.country,
        midMileLocationId: item?.midMileLocationId,
        lastmileWarehouseId: item?.lastmileWarehouseId,
        lastmileLocationName: item?.lastmileLocationName,
        isStyliRegisteredSeller: item?.isStyliRegisteredSeller,
        countryCode: item?.countryCode,
        deliveryType: item?.deliveryType,
        fulfillmentMode: item?.fulfillmentMode
      });
    }
    product.contextId = getContextId({
      enrich: item?.enrich,
      storeId,
      xHeaderToken,
    });

    product.size = item.superAttributeLabel;
    product.quantity = String(item.qty);
    product.quantityStock = String(inventoryObj?.value || 0);
    product.isGwp = checkIsGwp({
      enrich: item?.enrich,
      storeId,
      xHeaderToken,
    });

    /** Tax part */
    if (process.env.REGION == "IN") {
      product.taxObj = {
        taxIGST: String(formatPrice(item.taxObj.taxIGST)),
        taxIGSTAmount: String(formatPrice(item.taxObj.taxIGSTAmount)),
        taxCGST: String(formatPrice(item.taxObj.taxCGST)),
        taxCGSTAmount: String(formatPrice(item.taxObj.taxCGSTAmount)),
        taxSGST: String(formatPrice(item.taxObj.taxSGST)),
        taxSGSTAmount: String(formatPrice(item.taxObj.taxSGSTAmount)),
        intraState: item.taxObj.intraState,
      };
      product.hsnCode = productObj?.hsn_code;
    }
    product.taxAmount = String(formatPrice(item.taxAmount));
    product.taxPercent = String(item.taxPercent || 0);
    product.rowTotal = String(formatPrice(item.rowTotal));
    product.rowTotalWithDiscount = String(
      formatPrice(item.rowTotalWithDiscount)
    );
    product.discountAmount = String(formatPrice(item.discountAmount));
    product.discountPercent = String(item.discountPercent);
    product.priceInclTax = String(formatPrice(item.priceInclTax));
    product.rowTotalInclTax = String(formatPrice(item.rowTotalInclTax));
    product.price = String(formatPrice(item.price));
    product.discountTaxCompensationAmount = String(
      formatPrice(item.discountTaxCompensationAmount)
    );
    /** Tax part */

    product.flashSale = item.flashSale;

    product.flashConfig = {};
    if (item.flashConfig) {
      const { capPerUser, flashsaleId: flashSaleId } = item.flashConfig;
      
      const parentSku = item.parentSku;

      const productsPurchased = await findFlashProductsPerUser({
        quote,
        parentSku,
        flashSaleId,
      });

      product.flashConfig = item.flashConfig
        ? { capPerUser: Number(capPerUser) - Number(productsPurchased) }
        : {};
    }

    product.superAttributeId = String(item.superAttributeId);
    product.superAttributeValue = String(item.superAttributeValue);
    product.superAttributeLabel = String(item.superAttributeLabel);

    product.isMulin = item.isMulin;

    product.appliedCouponValue = item.appliedCouponValue;

    totalTaxAmount += item.taxAmount;

    product.productCalloutTag = item.productCalloutTag;
    product.metadata = item.metadata;

    //API-3732 & API-3791
    if(isShukranEnable && !retryPayment){
      const parentProductId = item.parentProductId;
      const productObj = _.find(productDetailsResponse, {
        objectID: String(parentProductId),
      });

      const childProductData = _.find(productObj.configProducts, {
        id: String(item.productId),
      });
      setShukranEarn({childProductData, item, storeId, tierName});
   }
    if (item?.hasOwnProperty('isSale')) product.isSale = item?.isSale
    if (item?.hasOwnProperty('shukranEarn')) product.shukranEarn = (item?.shukranEarn || 0) 
    if (item?.hasOwnProperty('shukranBasicEarn')) product.shukranBasicEarn = (item?.shukranBasicEarn || 0) 
    if (item?.hasOwnProperty('shukranBonousEarn')) product.shukranBonousEarn = (item?.shukranBonousEarn || 0)       
    //API-3732 END
    
    // Add productAttributes fields only when orderCreation is true
    if (orderCreation) {
      // Set from stored values first
      product.isDangerousProduct = item.hasOwnProperty('isDangerousProduct') ? item.isDangerousProduct : false;
      product.shortDescription = item.hasOwnProperty('shortDescription') ? item.shortDescription : "";
      product.variantSku = item.hasOwnProperty('variantSku') ? item.variantSku : "";
      
      if (productObj && productObj.objectID) {
        // Use fresh data from productDetail API - prioritize root level, fallback to productAttributes, but preserve stored values
        const isDangerousFromElastic =
          productObj.is_dangerous_product === 1 ||
          productObj.is_dangerous_product === true ||
          productObj.productAttributes?.is_dangerous_product === 1 ||
          productObj.productAttributes?.is_dangerous_product === true;
        
        // Only override if elastic data has the field
        if (productObj.hasOwnProperty('is_dangerous_product') || productObj.productAttributes?.hasOwnProperty('is_dangerous_product')) {
          product.isDangerousProduct = Boolean(isDangerousFromElastic);
        }
        
        // Only override if elastic has a value
        if (productObj.short_description || productObj.productAttributes?.short_description) {
          product.shortDescription =
            productObj.short_description ||
            productObj.productAttributes?.short_description ||
            "";
        }
        
        if (childProductObj?.variant_sku || productObj.variant_sku || productObj.productAttributes?.variant_sku) {
          product.variantSku =
            childProductObj?.variant_sku ||
            productObj.variant_sku ||
            productObj.productAttributes?.variant_sku ||
            "";
        }
      }
    }
    productsArr.push(product);
  }  

  productsArr = sortProducts(productsArr);
  let newGwpSubtotal = 0;
  productsArr.forEach(product => {
    if (product.isGwp) {
      const rowTotalWithDiscount = parseFloat(product.rowTotalInclTax - product.discountAmount || 0);
      newGwpSubtotal += rowTotalWithDiscount;
    }
  });
  quote.newGwpSubtotal = formatPrice(newGwpSubtotal);
  let responseObject = {};
  const donationAmount = quote.donationAmount;
  let adjustedDonation = 0;
  // Donation changes
  if (donationAmount > 0) {
    const donationResponse = calcQuoteDonation({
      quote,
      newDonation: donationAmount,
      isGetBag: true,
    });
    quote = donationResponse.quote;
    adjustedDonation = donationResponse.adjustedDonation;
  }
  if (shukranEnabled){
    responseObject.profileId                            = quote.shukranProfileId;
    responseObject.shukranCardNumber                    = quote.shukranCardNumber;
    responseObject.appliedShukranPoint                  = quote?.appliedShukranPoint || appliedShukranPoint || 0;
    responseObject.appliedShukranCashValue              = quote?.appliedShukranCashValue || 0;
    responseObject.shukranAvailablePoint                = shukranAvailablePoint ?? quote?.shukranAvailablePoint ?? 0;
    responseObject.shukranAvailableCashValue            = shukranAvailableCashValue ?? quote?.shukranAvailableCashValue ?? 0;
    responseObject.totalShukranBurn                     = quote?.totalShukranBurn || 0;
    responseObject.totalShukranBurnValueInCurrency      = quote?.totalShukranBurnValueInCurrency || 0;
    responseObject.totalShukranBurnValueInBaseCurrency  = quote?.totalShukranBurnValueInBaseCurrency || 0;
    responseObject.isAvailableShukranChanged = quote?.isAvailableShukranChanged || false;
   }

  responseObject.donationAmount = String(adjustedDonation);

  //API-3732 & API3791
  if(isShukranEnable && !retryPayment){
    const totalShukranEarn = productsArr?.reduce((totalShukran, quoteItem) => {
      return totalShukran + Number(quoteItem?.shukranEarn);
    }, 0) || 0; // Fallback to 0 if updatedQuoteItems is undefined
    quote.totalShukranEarn = totalShukranEarn || 0;
    responseObject.totalShukranEarn = totalShukranEarn || 0;
    //API-3815 - START
    const totalshukranBasicEarn = productsArr?.reduce((totalBasicEarn, quoteItem) => {
      return totalBasicEarn + Number(quoteItem?.shukranBasicEarn);
    }, 0) || 0; 
    quote.shukranBasicEarnPoint = totalshukranBasicEarn || 0;
    responseObject.shukranBasicEarnPoint = totalshukranBasicEarn || 0;
    const totalshukranBonousEarn = productsArr?.reduce((totalBonousEarn, quoteItem) => {
      return totalBonousEarn + Number(quoteItem?.shukranBonousEarn);
    }, 0) || 0; 
    quote.shukranBonousEarnPoint = totalshukranBonousEarn || 0;
    responseObject.shukranBonousEarnPoint = totalshukranBonousEarn || 0;
    quote.totalShukranEarnValueInCurrency = parseFloat((totalShukranEarn * shukranPointConversion ).toFixed(2));
    quote.totalShukranEarnValueInBaseCurrency = parseFloat((quote.totalShukranEarnValueInCurrency * currencyConversionRate ).toFixed(2));
    responseObject.totalShukranEarnValueInCurrency      = quote?.totalShukranEarnValueInCurrency || 0;
    responseObject.totalShukranEarnValueInBaseCurrency  = quote?.totalShukranEarnValueInBaseCurrency || 0;
     //API-3815 - END 
  }
  if(isShukranEnable){
    responseObject.shukranBasicEarnPoint = quote?.shukranBasicEarnPoint || 0;
    responseObject.shukranBonousEarnPoint = quote?.shukranBonousEarnPoint || 0;
    responseObject.totalShukranEarnValueInCurrency      = quote?.totalShukranEarnValueInCurrency || 0;
    responseObject.totalShukranEarnValueInBaseCurrency  = quote?.totalShukranEarnValueInBaseCurrency || 0;
    responseObject.totalShukranEarn = quote?.totalShukranEarn || 0;
  }
  //API-3732 END

  responseObject.storeId = String(storeId);
  responseObject.quoteId = String(quoteId);
  responseObject.customerId = quote.customerId;
  responseObject.customerEmail = quote.customerEmail;
  responseObject.customerPhoneNumber = quote.customerPhoneNumber;
  responseObject.customerIsGuest = quote.customerIsGuest ? "1" : "0";
  responseObject.customerFirstname = quote.customerFirstname;
  responseObject.customerLastname = quote.customerLastname;
  responseObject.customerDob = quote.customerDob;
  responseObject.itemsCount = String(quote.itemsCount);
  responseObject.itemsQty = String(quote.itemsQty);
  responseObject.flashSale = quote.flashSale;
  responseObject.newFlashSale = quote.newFlashSale;

  // [Sum for all products (Qty * Offer Price)]
  responseObject.subtotal = String(formatPrice(quote.subtotal));
  responseObject.subtotalInclTax = String(formatPrice(quote.subtotal));
  responseObject.subtotalExclTax = String(
    formatPrice(quote.subtotal - totalTaxAmount)
  );
  responseObject.subtotalWithDiscount = String(
    formatPrice(quote.subtotalWithDiscount)
  );
  responseObject.newGwpSubtotal = String(formatPrice(quote.newGwpSubtotal || 0));
  try {
    const baseConfig = cache.get("baseConfig") || {};
    responseObject.crossSellBagContextId = baseConfig?.crossSellBagContextId;
    
    // Get country config for GWP variables
    const websiteCode = getStoreConfig(storeId, 'websiteCode');
    const giftConfig = getBaseConfig('giftProducts');
    const countryConfig = giftConfig?.[websiteCode] || {};
    const { enable_gift_with_purchase_module_feature, eligible_products_context_id } = countryConfig || {};
    
    responseObject.enableGWPContextFeature = enable_gift_with_purchase_module_feature || false;
    responseObject.enableGWPContextId = enable_gift_with_purchase_module_feature ? eligible_products_context_id : null;
  } catch (e) {
    logError(e, "Error in getting Base Config");
  }

  // dummy calulation data

  // quote.subtotal = 119;
  // quote.subtotalWithDiscount = 107.1;
  // quote.shippingCharges = 10;
  // quote.codCharges = 9;
  // quote.amstorecreditAmount = 75.59;
  // storeCreditObjectStoreValue = 75.59;
  // quote.amstorecreditUse = true;

  //	After discount/promos/taxes/shipping charges
  const d = quote.subtotal - quote.subtotalWithDiscount;
  const fd = formatPrice(d);
  const discountAmount = formatPrice(
    quote.subtotal - quote.subtotalWithDiscount
  );

  const customDutiesPercentage = Number(
    getStoreConfig(storeId, "customDutiesPercentage") || 0
  );
  const importMinFeePercentage = Number(
    getStoreConfig(storeId, "importFeePercentage") || 0
  );
  const importMaxFeePercentage = Number(
    getStoreConfig(storeId, "importMaxFeePercentage") || 0
  );
  const minimumDutiesAmount = Number(
    getStoreConfig(storeId, "minimumDutiesAmount") || 0
  );

  const showAddLowValueAmount = Number(
    getStoreConfig(storeId, "showAddLowValueAmount") || 0
  );
 
  if (!retryPayment) {
    if (handleBnplIssue && orderCreation && isInstallmentMethod) {
      // logger.info(`processQuote: Quote ${quote.id} - BNPL issue handling enabled, skipping free shipping evaluation`);
    } else {
      await evaluateFreeShipping({
        quote,
        responseObject,
        storeId,
        xHeaderToken,
        collection,
      });
    }
  } 
  const shukranFreeShippingTier = getAppConfigKey('shukranFreeShippingTier') || ["platinum"];
  const isFreeShippingEligible = quote.shippingCharges > 0 && quote.tier && shukranFreeShippingTier.includes(quote.tier.toLowerCase()) && isShukranEnable;
  if (handleBnplIssue && orderCreation && isInstallmentMethod) {
    // logger.info(`processQuote: Quote ${quote.id} - BNPL issue handling enabled, preserving original shipping charges`);
  } else {
    isFreeShippingEligible && (quote.shippingCharges = 0);
  }
  // A
  const estimatedTotal =
    quote.subtotal - discountAmount + quote.shippingCharges + quote.codCharges;
  // B

  let customDutiesAmount = 0;
  let importFeePercentage = importMinFeePercentage;
  if (estimatedTotal > minimumDutiesAmount) {
    customDutiesAmount = (estimatedTotal * customDutiesPercentage) / 100;
    importFeePercentage = importMaxFeePercentage;
  }
  // A + B
  const estimatedTotalWithCustoms = estimatedTotal + customDutiesAmount;
  // C
  let importFeesAmount =
    (estimatedTotalWithCustoms * importFeePercentage) / 100;
  // A + B + C
  // let grandTotal =
  //   estimatedTotalWithCustoms + importFeesAmount + adjustedDonation;
  // logInfo("import fee before round off:", importFeesAmount);
  importFeesAmount = formatPrice(importFeesAmount + customDutiesAmount);
  // logInfo("importFeesAmount:", importFeesAmount);
  // logInfo("customDutiesAmount:", customDutiesAmount);
  // UIB-4047 - START
  let grandTotal;
  if (handleBnplIssue && orderCreation && isInstallmentMethod) {
    //Don't re calculate grandTotal, set grandTotalCopy to  grandTotal
    grandTotal =  quote.grandTotalCopy;
    // logger.info(`processQuote: Quote ${quote.id} - BNPL issue handling enabled, using cached grandTotal: ${grandTotal}`);
  } else {
    grandTotal =
      quote.subtotal +
      quote.shippingCharges +
      quote.codCharges +
      importFeesAmount +
      adjustedDonation -
      discountAmount;
      quote.grandTotalCopy = grandTotal;
      // logger.info(`processQuote: Quote ${quote.id} - Calculated new grandTotal: ${grandTotal}`);
  }
  // UIB-4047 - END
  // logInfo("grandTotal::", grandTotal);
  responseObject.estimatedTotal = String(formatPrice(estimatedTotal));
  responseObject.importFeesAmount = String(importFeesAmount);

  //	[Sum for all products (Qty * Base Price)]
  responseObject.total = String(formatPrice(totalPrice));

  //	Styli Discount (Sum of (Base Price - Offer Price))
  responseObject.discount = String(formatPrice(totalPrice - quote.subtotal));

  //	Coupon Code Discount
  responseObject.couponDiscount = String(formatPrice(quote.couponDiscount));
  responseObject.autoCouponApplied = quote.autoPromoCode;
  responseObject.autoCouponDiscount = quote.autoPromoAmount;
  responseObject.shippingThreshold = String(
    formatPrice(quote.shippingThreshold)
  );
  responseObject.shippingMethod = addressObject?.shippingMethod;
  responseObject.shippingWaived = true;
  responseObject.shippingDescription = addressObject?.shippingDescription;
  responseObject.shippingAmount = String(formatPrice(quote.shippingCharges));
  responseObject.shippingFreeCeiling = null;
  if (quote.shippingThreshold > quote.subtotalWithDiscount) {
    responseObject.shippingFreeCeiling = String(
      formatPrice(quote.shippingThreshold - quote.subtotalWithDiscount)
    );
  }
  responseObject.shippingInclTax = String(formatPrice(quote.shippingCharges));
  responseObject.selectedAddressId = addressObject?.customerAddressId;
  responseObject.selectedPaymentMethod = paymentObject.method;
  responseObject.isCouponApplicable =
    quote.couponCode != undefined && quote.couponCode != null;
  responseObject.couponCodeApplied = quote.couponCode;
  responseObject.currency = quote.storeCurrencyCode;

  responseObject.storeCreditApplied = null;
  responseObject.storeCreditBalance = null;

  if (quote.paidStyliCredit) {
    storeCreditObjectStoreValue = quote.paidStyliCredit;
  }
  if (quote.customerId && storeCreditObjectStoreValue) {
    responseObject.storeCreditBalance =
      String(Math.trunc((storeCreditObjectStoreValue || 0) * 100) / 100) ||
      null;
    if (quote.amstorecreditUse && quote.amstorecreditAmount) {
      const applicableAmountForStoreCredit = Math.min(
        grandTotal,
        quote.amstorecreditAmount,
        storeCreditObjectStoreValue
      );
      // Doubts on the below value. not in use, but verify.
      // quote.amstorecreditBaseAmount = applicableAmountForStoreCredit / currencyConversionRate;
      quote.amstorecreditAmount = applicableAmountForStoreCredit;

      if (applicableAmountForStoreCredit == 0) {
        quote.amstorecreditBaseAmount = null;
        quote.amstorecreditAmount = null;
        quote.amstorecreditUse = false;

        await upsertQuote({ storeId, quote, collection, xHeaderToken });
      }

      // responseObject.storeCreditApplied =
      //   String(Math.trunc((quote.amstorecreditAmount || 0) * 100) / 100) ||
      //   null;
      if (quote.paidStyliCredit) {
        responseObject.storeCreditApplied = "0";
      } else {
        responseObject.storeCreditApplied =
          String(quote.amstorecreditAmount) || null;
      }

      grandTotal = grandTotal - Number(quote.amstorecreditAmount || 0);
    }
  }

  if (quote.customerId && quote?.coinDiscountData?.isCoinApplied) {
    grandTotal =
      grandTotal - Number(quote.coinDiscountData?.storeCoinValue || 0);
  }
  const cleanTotal =
    Number(grandTotal) -
    Number(quote.codCharges || 0) -
    Number(adjustedDonation) -
    Number(quote.shippingCharges || 0);
  const shippingThresholdDiff = Number(quote.shippingThreshold) - cleanTotal;
  responseObject.showLowValueCta =
    shippingThresholdDiff > 0 &&
    shippingThresholdDiff < Number(showAddLowValueAmount);
  responseObject.showAddLowValueAmount = showAddLowValueAmount;

  // logInfo(
  //   "getQuoteValues",
  //   {
  //     estimatedTotal,
  //     subtotal: quote.subtotal,
  //     shippingCharges: quote.shippingCharges,
  //     customDutiesAmount,
  //     importFeesAmount,
  //     discountAmount,
  //     appliedStoreCredit: quote.amstorecreditAmount,
  //     grandTotal,
  //   },
  //   xHeaderToken
  // );

  /**setting for Enabale & disable stylicredeit **/

  // logInfo("totalOrderedCount", quote.totalOrderedCount);
  let grandTotalExcludeCodAndDonation =
    grandTotal - quote.codCharges - adjustedDonation;
  const minimumFirstOrderValue = Number(
    getStoreConfig(storeId, "minimumFirstOrderValue") || 0
  );

  responseObject.isStoreCreditEnabled = true;

  if (
    quote.totalOrderedCount == 0 &&
    grandTotalExcludeCodAndDonation < minimumFirstOrderValue
  ) {
    responseObject.isStoreCreditEnabled = false;
  }

  responseObject.addQuoteAmountToEnableStyliCredit = String(
    formatPrice(minimumFirstOrderValue)
  );
  
  responseObject.grandTotal = String(formatPrice(grandTotal));
  responseObject.baseGrandTotal = String(formatPrice(grandTotal));

  if (shukranEnabled){ 
    let shukranBurnCashValue = quote?.totalShukranBurnValueInCurrency || 0;
    responseObject.estimatedTotal = String(formatPrice(responseObject.estimatedTotal-shukranBurnCashValue));
    responseObject.grandTotal = String(formatPrice(grandTotal - shukranBurnCashValue) );
    responseObject.baseGrandTotal = String(formatPrice(grandTotal - shukranBurnCashValue));
  }
  responseObject.taxAmount = String(formatPrice(totalTaxAmount));
  responseObject.taxPercent = String(taxPercentage);

  responseObject.codCharges = String(formatPrice(quote.codCharges));
  responseObject.calcSource = quote.calcSource;
  setStyliCashApplicableTotal({quote, finalGrandTotal: responseObject.grandTotal, adjustedDonation, responseObject});
  const matchingProducts = quote.quoteItem
    .map((item) =>
      productsArr.find(
        (product) =>
          product.productId === item.productId &&
          Number(product.quantity) === Number(item.qty)
      )
    )
    .filter(Boolean);
  responseObject.quoteProducts = matchingProducts;

  if (shukranEnabled){
    const safeValue = (value) => (value === null || isNaN(value) ? 0 : value);
    responseObject.quoteProducts.forEach((product, index) => {
      if (quote.quoteItem[index]) {
          product.shukranBurn = safeValue(quote.quoteItem[index].shukranBurn);
          product.shukranBurnInCurrency = safeValue(quote.quoteItem[index].shukranBurnInCurrency);
          product.shukranBurnInBaseCurrency = safeValue(quote.quoteItem[index].shukranBurnInBaseCurrency);
      }
    });
  }

  setStyliCashBurn(quote, responseObject);
  responseObject.products = productsArr;
  responseObject.discountData = quote.discountData;

  responseObject.shippingAddress = addressObject;
  responseObject.isWhitelistedCustomer = quote.isWhitelistedCustomer;
  responseObject.otpFlag = quote.otpFlag;
  responseObject.tabbyPaymentId = orderCreation ? quote.tabbyPaymentId : "";
  responseObject.availablePaymentMethods = paymentMethodsFromConfig(
    quote.storeId
  );
  if (responseObject.totalShukranBurn > 0) {
    const shukranPaymentMethod =  getAppConfigKey('shukranPaymentMethod');
    responseObject.availablePaymentMethods = [
      ...responseObject.availablePaymentMethods,
      shukranPaymentMethod,                  
    ];
  }
  responseObject.priceDropData = quote.priceDropData;
  responseObject.coinDiscountData = quote.coinDiscountData;

  if (bagView !== 1) {
    const paymentRestriction = this.getPaymentRestriction({
      appliedFRulesOutput: quote.appliedFRulesOutput,
      grandTotal: responseObject.grandTotal,
      codCharges: responseObject.codCharges,
      storeCreditApplied: responseObject.storeCreditApplied,
      quoteAddress: quote.quoteAddress,
      isWhitelistedCustomer: quote.isWhitelistedCustomer,
      donationAmount: quote.donationAmount,
    });

    responseObject.paymentRestriction = paymentRestriction;
  }

  if (
    ["3.2.0", "3.2.1", "3.2.2"].includes(`${xClientVersion}`) &&
    responseObject.selectedPaymentMethod === null &&
    Number(responseObject.storeCreditBalance) >=
      Number(responseObject.grandTotal) &&
    !orderCreation &&
    !bagView
  ) {
    responseObject.storeCreditBalance = "0.1";
    responseObject.grandTotal = "0.00";
  }

  responseObject.notifications = quote.notifications || [];
  responseObject.configShipmentCharges = quote.configShipmentCharges;
  // logInfo("Config Shipment Charges:", quote.configShipmentCharges);
  if (quote?.notifications?.length && resetNotifs) {
    resetNotifications({ quote, collection, xHeaderToken });
  }
  // logInfo("quote info before return");
  responseObject.promoGroups = quote.groupedData;

  responseObject.retryPayment = quote.retryPayment;
  responseObject.showOOSWishlist = quote.showOOSWishlist;
  responseObject.oosItemCount = quote.oosItemCount;
  if (productsArr.length === 0 && quote.customerIsGuest === 1) {
    responseObject.showOOSWishlist = false;
    responseObject.oosItemCount = 0;
  }
  return {
    status: true,
    statusCode: "200",
    statusMsg: "success",
    response: responseObject,
  };
};

exports.processQuoteOptimized = async function ({
  storeId,
  quote,
  collection,
  orderCreation,
  xHeaderToken,
  token,
  pool,
  xSource,
  xClientVersion,
  bagView,
  statusCoinApllied,
  screenName,
  resetNotifs,
  retryPayment = false,
  shukranEnabled,
  appliedShukranPoint,
  shukranAvailablePoint,
  shukranAvailableCashValue,
  tierName,
  orderSplitFlag,
  isVersion7
}) {
  quote.showOOSWishlist = false;
  quote.oosItemCount = 0;
  // logInfo("in processQuote", "", xHeaderToken);
  // logInfo("in processQuote shukranEnabled : ", shukranEnabled);
  const quoteId = quote.id;

  const currencyConversionRate = Number(
    getStoreConfig(storeId, "currencyConversionRate") || 1
  );
  const shukranPointConversion = Number(
    getStoreConfig(storeId, "shukranPointConversion") || getShukranPointConversion(storeId)
  );
  const logPrefix = `processQuote:::${new Date().toISOString()}`;
  if(!collection){
    // logger.error(`${logPrefix} Couchbase collection missing`)
  }
  const baseConfig = cache.get("baseConfig") || {};
  // logInfo('baseConfig', baseConfig);
  const taxPercentage = Number(getStoreConfig(storeId, "taxPercentage") || 0);
  // Get address/payment objects
  const addressObject =
    _.filter(
      quote.quoteAddress || [],
      (el) => el.addressType == "shipping"
    )[0] || {};
  addressObject.cityMapper = quote.city || {};
  const paymentObject = quote.quotePayment;
  const isShukranEnable = getStoreConfig(storeId, 'isShukranEnable') || false;
    
  // Fetch product details from elasticSearch
  let parentSkus = [];
  let childSkus = [];
  let products = [];
  let productsMap = {};

  for (const item of quote.quoteItem || []) {
    parentSkus.push(item.parentSku);
    childSkus.push(item.sku);
    products.push({
      productId: item.productId,
      sku: item.sku,
    });
    productsMap[item.productId] = item.sku;
  }
  const inventoryRequestObject = {
    skus: childSkus,
    storeId,
  };

  // logInfo("parentSkus", parentSkus, xHeaderToken);

  // promises start
  const promisesArray = [];
  if (!retryPayment) {
    promisesArray.push(getProducts({ parentSkus, storeId, xHeaderToken }));
  }

  // promisesArray.push(
  //   inventoryCheckFromDB({ productsMap, orderCreation, pool, xHeaderToken })
  // );
  promisesArray.push(
    inventoryCheck({ inventoryRequestObject, xHeaderToken, orderCreation: orderCreation ?? false })
  );

  if (quote.customerId)
    promisesArray.push(
      getCustomerStoreCredit({
        customer: quote.customerId,
        pool,
        xHeaderToken,
      })
    );
  /*if (quote.customerId)
    promisesArray.push(
      getStoreCoinBalance({ customerId: quote.customerId, quote, token, xHeaderToken })
    );*/
  const resPromiseAll = await Promise.all(promisesArray).then((values) => {
    return values;
  });
  // promises end
  
  const {
    productArr: productDetailsResponse,
    flashSale,
    newFlashSale,
    errorMessage,
  } = resPromiseAll[0];

  const isSLAEnabled = getStoreConfig(storeId, 'isSLAEnabled') || false;

  if (isSLAEnabled) {
    try {
      // UIB-7055 as part sla check we are calling inventory check API default false we are sending orderCreation: false
      let invCheckResult;
      if(!orderCreation)
        invCheckResult = resPromiseAll[1];
      else
        invCheckResult = await inventoryCheck({ inventoryRequestObject, xHeaderToken, orderCreation: false });
      const uniqueWarehouseIds = [...new Set(invCheckResult?.filter(item => item?.warehouseId)?.map(item => item.warehouseId) || [])];

      if (uniqueWarehouseIds.length && addressObject?.cityMapper?.id) {
        // logger.info("warehouseIds", uniqueWarehouseIds);
        const slaDetails = await calculateHighestSLA({
          uniqueWarehouseIds,
          cityId: addressObject.cityMapper.id,
          countryId: addressObject.cityMapper.countryId
        });
        // logger.info("slaDetails", slaDetails);
        if (slaDetails) addressObject.cityMapper = slaDetails;
      }
    } catch (error) {
      // logger.error("SLA processing error:", error.message);
    }
  }

  if (errorMessage) {
    return {
      status: false,
      statusCode: "204",
      statusMsg: errorMessage,
    };
  }

  const productIdsInQuote = quote.quoteItem?.map((el) => el.productId);
  let productIdsInElasticResponse = [];
  for (let key in productDetailsResponse) {
    if (productDetailsResponse.hasOwnProperty(key)) {
      const product = productDetailsResponse[key];
      product.configProducts?.map((el) => {
        productIdsInElasticResponse.push(el.id);
      });
    }
  }

  if (
    orderCreation &&
    !retryPayment &&
    !productIdsInQuote.every((v) => productIdsInElasticResponse.includes(v))
  ) {
    return {
      status: false,
      statusCode: "203",
      statusMsg: "products mismatch!",
    };
  }

  // logInfo("quote.totalOrderedCount", quote.totalOrderedCount, xHeaderToken);
  if (quote.totalOrderedCount === undefined && !retryPayment)
    quote.totalOrderedCount = await getOrderedCount({
      quote,
      customerId: quote.customerId,
      xHeaderToken,
      storeId,
      token,
      pool,
      optimized: true
    });

  const invCheck = resPromiseAll[1];

  if (!retryPayment) {
    quote = await addDataFromElastic({
      elasticData: productDetailsResponse,
      flashSale,
      quote,
      storeId,
      xHeaderToken,
      token,
      invCheck,
      shukranEnabled,
      tierName,
      newFlashSale,
      optimized: true,
      xClientVersion
    });
  }
// handleBnplIssue
const handleBnplIssue = baseConfig?.handleBnplIssue || false;
const isInstallmentMethod = ["tamara_installments_3", "tabby_installments"].includes(paymentObject.method);
// logger.info(`processQuote: Quote ${quote.id} - isInstallmentMethod: ${isInstallmentMethod}, orderCreation: ${orderCreation}, handleBnplIssue: ${handleBnplIssue}, paymentObject: ${JSON.stringify(paymentObject)}`);
  // handle issue with qutoe and promo and/or calculations - tabby Or tamara
  if (handleBnplIssue && orderCreation && isInstallmentMethod) {
    // logger.info(`processQuote: Quote ${quote.id} - BNPL issue handling enabled, skipping quote issues check`);
  } else {
    quote = await checkQuoteIssues({
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
      retryPayment,
      optimized: true,
      isVersion7
    });
  } 
  
  if (!quote) return null;
  // calculation for styli coin
  if (quote.customerId && statusCoinApllied != undefined && !retryPayment) {
    quote = await processCoinCalculations({
      storeId,
      quote,
      collection,
      upsert: false,
      token,
      xHeaderToken,
      statusCoinApllied,
    });
    // console.log(quote.coinDiscountData);
    if (!quote) return null;
  }

  if (shukranEnabled && !retryPayment){
    quote = await processBurnShukranCoinCalculations({        
      quote, 
      appliedShukranPoint,
      shukranAvailablePoint,
      shukranAvailableCashValue,
      storeId
    });
    if (!quote) return null;
  }

  if (!retryPayment) {
    quote = await processFreeGift({
      quote,
      invCheck,
      xHeaderToken,
      xClientVersion,
      screenName,
      collection,
      bagView,
      optimized: true
    });
    
  }

  let storeCreditObject;
  let storeCreditObjectStoreValue;
  let storeCreditIndex = 2;
  if (retryPayment) {
    storeCreditIndex = 1;
  }
  const storeCreditResponse = resPromiseAll[storeCreditIndex];
  if (quote.customerId && storeCreditResponse) {
    const [rows, fields] = storeCreditResponse;
    storeCreditObject = rows[0]?.store_credit;
    if (storeCreditObject)
      storeCreditObjectStoreValue = formatPrice(
        storeCreditObject / currencyConversionRate
      );
  }

  let totalTaxAmount = 0;
  let totalPrice = 0;
  let productsArr = [];

  for (const item of quote.quoteItem) {
    const currency = getCurrency({ storeId });

    const inventoryObj = _.find(invCheck, { sku: item.sku.toString() });

    let product = {};
    let productObj = {};
    let childProductObj = {};
    product.sizes = [];

    if (!retryPayment) {
      const parentProductId = item.parentProductId;
      productObj = _.find(productDetailsResponse, {
        objectID: String(parentProductId),
      });

      const priceObj = productObj?.price;

      childProductObj = _.find(productObj?.configProducts || [], {
        id: String(item.productId),
      }) || {};

      let price = getChildPrice({ childProductObj });
      let specialPrice = getChildSpecialPrice({ childProductObj, price });

      if (!price) {
        price = getPrice({ priceObj: priceObj[currency] });
        specialPrice = getSpecialPrice({
          priceObj: priceObj[currency],
          price,
        });
      }

      price = price.toFixed(2);
      if (specialPrice) specialPrice = specialPrice.toFixed(2);

      product.productStatus = !productObj.isDisabled;
      product.brandName = productObj?.brand_name;
      product.landedCost = String(productObj?.original_base_price || 0);
      product.isOOS = item.isOOS || false;
      product.notificationType = item.notificationType ?? '';

      product.images = {
        image: sanitiseImageUrl(productObj?.image_url),
      };

      if ("returnable" in productObj) {
        product.returnable = Boolean(productObj?.returnable);
      }

      if ("returnable" in productObj) {
        product.isReturnable = Boolean(productObj?.returnable);
      } else if ("isReturnApplicable" in productObj) {
        product.isReturnable = Boolean(productObj?.isReturnApplicable);
      }

      if (specialPrice && !item.disableSpecialPrice) {
        const discount = parseFloat(((price - specialPrice) / price) * 100);
        const discountPercentage = parseInt(Math.round(discount));
        product.discount = String(discountPercentage);
      } else {
        product.discount = "0";
      }
      product.landedCost = String(productObj?.original_base_price || 0);
      product.gender = String(productObj?.gender || "");
      if (productObj?.isDisabled) product.quantityStock = "0";

      if (productObj?.configProducts?.length) {
        const sizeArray = _.map(productObj.configProducts, (el) => {
          return {
            label: el.size,
            sku: el.sku,
            productId: el.id,
          };
        });
        product.sizes = sizeArray;
      }

      product.prices = {
        price: String(formatPrice(price).toFixed(2)),
        specialPrice:
          specialPrice && !item.disableSpecialPrice
            ? String(formatPrice(specialPrice).toFixed(2))
            : null,
        droppedPrice: !item.disableSpecialPrice
          ? String(formatPrice(item.droppedPrice).toFixed(2))
          : null,
      };

      totalPrice += price * item.qty;
    }

    product.parentProductId = item.parentProductId;
    product.productId = item.productId;
    product.parentSku = item.parentSku;
    product.sku = item.sku;
    product.name = item.name;
    product.cartItemPriceDrop = item.cartItemPriceDrop || 0;
    product.l4_category = item?.l4_category || '';
    product.qtySold = await qtySold(item?.qtySold, baseConfig);
    product.soldBy = item?.soldBy || ""

    if (retryPayment) {
      product.brandName = item.brandName;
      product.landedCost = item.landedCost;
      product.prices = {
        price: String(item.price),
        specialPrice: String(item.specialPrice),
        droppedPrice: String(item.droppedPrice),
      };
      product.discount = String(item.discountAmount);
      product.landedCost = String(item.landedCost);
      product.gender = item.gender;
      totalPrice += item.price * item.qty;
    }

    if (item.isGift) {
      product.isGift = true;
      product.prices.specialPrice = String(formatPrice(0).toFixed(2));
    }

     // Optimized: Batch assign order split properties if needed
     if(orderSplitFlag){
      Object.assign(product, {
        midMileLocationName: item?.midMileLocationName,
        firstMileLocationId: item?.warehouseId,
        firstMileLocationName: item?.warehouseName,
        fulfillmentType: item?.fulfillmentType,
        shipmentType: item?.shipmentType,
        sellerId: item?.sellerId,
        sellerName: item?.sellerName,
        fulfilmentMode: item?.fulfilmentMode,
        warehouseId: item?.warehouseId,
        city: item?.city,
        country: item?.country,
        midMileLocationId: item?.midMileLocationId,
        lastmileWarehouseId: item?.lastmileWarehouseId,
        lastmileLocationName: item?.lastmileLocationName,
        isStyliRegisteredSeller: item?.isStyliRegisteredSeller,
        countryCode: item?.countryCode,
        deliveryType: item?.deliveryType,
        fulfillmentMode: item?.fulfillmentMode
      });
    }
    product.contextId = getContextId({
      enrich: item?.enrich,
      storeId,
      xHeaderToken,
    });

    product.size = item.superAttributeLabel;
    product.quantity = String(item.qty);
    product.quantityStock = String(inventoryObj?.value || 0);
    product.isGwp = checkIsGwp({
      enrich: item?.enrich,
      storeId,
      xHeaderToken,
    });

    /** Tax part */
    if (process.env.REGION == "IN") {
      product.taxObj = {
        taxIGST: String(formatPrice(item.taxObj.taxIGST)),
        taxIGSTAmount: String(formatPrice(item.taxObj.taxIGSTAmount)),
        taxCGST: String(formatPrice(item.taxObj.taxCGST)),
        taxCGSTAmount: String(formatPrice(item.taxObj.taxCGSTAmount)),
        taxSGST: String(formatPrice(item.taxObj.taxSGST)),
        taxSGSTAmount: String(formatPrice(item.taxObj.taxSGSTAmount)),
        intraState: item.taxObj.intraState,
      };
      product.hsnCode = productObj?.hsn_code;
    }
    product.taxAmount = String(formatPrice(item.taxAmount));
    product.taxPercent = String(item.taxPercent || 0);
    product.rowTotal = String(formatPrice(item.rowTotal));
    product.rowTotalWithDiscount = String(
      formatPrice(item.rowTotalWithDiscount)
    );
    product.discountAmount = String(formatPrice(item.discountAmount));
    product.discountPercent = String(item.discountPercent);
    product.priceInclTax = String(formatPrice(item.priceInclTax));
    product.rowTotalInclTax = String(formatPrice(item.rowTotalInclTax));
    product.price = String(formatPrice(item.price));
    product.discountTaxCompensationAmount = String(
      formatPrice(item.discountTaxCompensationAmount)
    );
    /** Tax part */

    product.flashSale = item.flashSale;

    product.flashConfig = {};
    if (item.flashConfig) {
      const { capPerUser, flashsaleId: flashSaleId } = item.flashConfig;
      
      const parentSku = item.parentSku;

      const productsPurchased = await findFlashProductsPerUser({
        quote,
        parentSku,
        flashSaleId,
      });

      product.flashConfig = item.flashConfig
        ? { capPerUser: Number(capPerUser) - Number(productsPurchased) }
        : {};
    }

    product.superAttributeId = String(item.superAttributeId);
    product.superAttributeValue = String(item.superAttributeValue);
    product.superAttributeLabel = String(item.superAttributeLabel);

    product.isMulin = item.isMulin;

    product.appliedCouponValue = item.appliedCouponValue;

    totalTaxAmount += item.taxAmount;

    product.productCalloutTag = item.productCalloutTag;
    product.metadata = item.metadata;

    //API-3732 & API-3791
    if(isShukranEnable && !retryPayment){
      const parentProductId = item.parentProductId;
      const productObj = _.find(productDetailsResponse, {
        objectID: String(parentProductId),
      });

      const childProductData = _.find(productObj.configProducts, {
        id: String(item.productId),
      });
      setShukranEarn({childProductData, item, storeId, tierName});
   }
    if (item?.hasOwnProperty('isSale')) product.isSale = item?.isSale
    if (item?.hasOwnProperty('shukranEarn')) product.shukranEarn = (item?.shukranEarn || 0) 
    if (item?.hasOwnProperty('shukranBasicEarn')) product.shukranBasicEarn = (item?.shukranBasicEarn || 0) 
    if (item?.hasOwnProperty('shukranBonousEarn')) product.shukranBonousEarn = (item?.shukranBonousEarn || 0)       
    //API-3732 END
    
    // Add productAttributes fields only when orderCreation is true
    if (orderCreation) {
      // Set from stored values first
      product.isDangerousProduct = item.hasOwnProperty('isDangerousProduct') ? item.isDangerousProduct : false;
      product.shortDescription = item.hasOwnProperty('shortDescription') ? item.shortDescription : "";
      product.variantSku = item.hasOwnProperty('variantSku') ? item.variantSku : "";
      
      if (productObj && productObj.objectID) {
        // Use fresh data from productDetail API - prioritize root level, fallback to productAttributes, but preserve stored values
        const isDangerousFromElastic =
          productObj.is_dangerous_product === 1 ||
          productObj.is_dangerous_product === true ||
          productObj.productAttributes?.is_dangerous_product === 1 ||
          productObj.productAttributes?.is_dangerous_product === true;
        
        // Only override if elastic data has the field
        if (productObj.hasOwnProperty('is_dangerous_product') || productObj.productAttributes?.hasOwnProperty('is_dangerous_product')) {
          product.isDangerousProduct = Boolean(isDangerousFromElastic);
        }
        
        // Only override if elastic has a value
        if (productObj.short_description || productObj.productAttributes?.short_description) {
          product.shortDescription =
            productObj.short_description ||
            productObj.productAttributes?.short_description ||
            "";
        }
        
        if (childProductObj?.variant_sku || productObj.variant_sku || productObj.productAttributes?.variant_sku) {
          product.variantSku =
            childProductObj?.variant_sku ||
            productObj.variant_sku ||
            productObj.productAttributes?.variant_sku ||
            "";
        }
      }
    }
    
    productsArr.push(product);
  }
  
  productsArr = sortProducts(productsArr);
  let newGwpSubtotal = 0;
  productsArr.forEach(product => {
    if (product.isGwp) {
      const rowTotalWithDiscount = parseFloat(product.rowTotalInclTax - product.discountAmount || 0);
      newGwpSubtotal += rowTotalWithDiscount;
    }
  });
  quote.newGwpSubtotal = formatPrice(newGwpSubtotal);
  let responseObject = {};
  const donationAmount = quote.donationAmount;
  let adjustedDonation = 0;
  // Donation changes
  if (donationAmount > 0) {
    const donationResponse = calcQuoteDonation({
      quote,
      newDonation: donationAmount,
      isGetBag: true,
    });
    quote = donationResponse.quote;
    adjustedDonation = donationResponse.adjustedDonation;
  }
  if (shukranEnabled){
    responseObject.profileId                            = quote.shukranProfileId;
    responseObject.shukranCardNumber                    = quote.shukranCardNumber;
    responseObject.appliedShukranPoint                  = quote?.appliedShukranPoint || appliedShukranPoint || 0;
    responseObject.appliedShukranCashValue              = quote?.appliedShukranCashValue || 0;
    responseObject.shukranAvailablePoint                = shukranAvailablePoint ?? quote?.shukranAvailablePoint ?? 0;
    responseObject.shukranAvailableCashValue            = shukranAvailableCashValue ?? quote?.shukranAvailableCashValue ?? 0;
    responseObject.totalShukranBurn                     = quote?.totalShukranBurn || 0;
    responseObject.totalShukranBurnValueInCurrency      = quote?.totalShukranBurnValueInCurrency || 0;
    responseObject.totalShukranBurnValueInBaseCurrency  = quote?.totalShukranBurnValueInBaseCurrency || 0;
    responseObject.isAvailableShukranChanged = quote?.isAvailableShukranChanged || false;
   }

  responseObject.donationAmount = String(adjustedDonation);

  //API-3732 & API3791
  if(isShukranEnable && !retryPayment){
    const totalShukranEarn = productsArr?.reduce((totalShukran, quoteItem) => {
      return totalShukran + Number(quoteItem?.shukranEarn);
    }, 0) || 0; // Fallback to 0 if updatedQuoteItems is undefined
    quote.totalShukranEarn = totalShukranEarn || 0;
    responseObject.totalShukranEarn = totalShukranEarn || 0;
    //API-3815 - START
    const totalshukranBasicEarn = productsArr?.reduce((totalBasicEarn, quoteItem) => {
      return totalBasicEarn + Number(quoteItem?.shukranBasicEarn);
    }, 0) || 0; 
    quote.shukranBasicEarnPoint = totalshukranBasicEarn || 0;
    responseObject.shukranBasicEarnPoint = totalshukranBasicEarn || 0;
    const totalshukranBonousEarn = productsArr?.reduce((totalBonousEarn, quoteItem) => {
      return totalBonousEarn + Number(quoteItem?.shukranBonousEarn);
    }, 0) || 0; 
    quote.shukranBonousEarnPoint = totalshukranBonousEarn || 0;
    responseObject.shukranBonousEarnPoint = totalshukranBonousEarn || 0;
    quote.totalShukranEarnValueInCurrency = parseFloat((totalShukranEarn * shukranPointConversion ).toFixed(2));
    quote.totalShukranEarnValueInBaseCurrency = parseFloat((quote.totalShukranEarnValueInCurrency * currencyConversionRate ).toFixed(2));
    responseObject.totalShukranEarnValueInCurrency      = quote?.totalShukranEarnValueInCurrency || 0;
    responseObject.totalShukranEarnValueInBaseCurrency  = quote?.totalShukranEarnValueInBaseCurrency || 0;
     //API-3815 - END 
  }
  if(isShukranEnable){
    responseObject.shukranBasicEarnPoint = quote?.shukranBasicEarnPoint || 0;
    responseObject.shukranBonousEarnPoint = quote?.shukranBonousEarnPoint || 0;
    responseObject.totalShukranEarnValueInCurrency      = quote?.totalShukranEarnValueInCurrency || 0;
    responseObject.totalShukranEarnValueInBaseCurrency  = quote?.totalShukranEarnValueInBaseCurrency || 0;
    responseObject.totalShukranEarn = quote?.totalShukranEarn || 0;
  }
  //API-3732 END

  responseObject.storeId = String(storeId);
  responseObject.quoteId = String(quoteId);
  responseObject.customerId = quote.customerId;
  responseObject.customerEmail = quote.customerEmail;
  responseObject.customerPhoneNumber = quote.customerPhoneNumber;
  responseObject.customerIsGuest = quote.customerIsGuest ? "1" : "0";
  responseObject.customerFirstname = quote.customerFirstname;
  responseObject.customerLastname = quote.customerLastname;
  responseObject.customerDob = quote.customerDob;
  responseObject.itemsCount = String(quote.itemsCount);
  responseObject.itemsQty = String(quote.itemsQty);
  responseObject.flashSale = quote.flashSale;
  responseObject.newFlashSale = quote.newFlashSale;

  // [Sum for all products (Qty * Offer Price)]
  responseObject.subtotal = String(formatPrice(quote.subtotal));
  responseObject.subtotalInclTax = String(formatPrice(quote.subtotal));
  responseObject.subtotalExclTax = String(
    formatPrice(quote.subtotal - totalTaxAmount)
  );
  responseObject.subtotalWithDiscount = String(
    formatPrice(quote.subtotalWithDiscount)
  );
  responseObject.newGwpSubtotal = String(formatPrice(quote.newGwpSubtotal || 0));
  try {
    const baseConfig = cache.get("baseConfig") || {};
    responseObject.crossSellBagContextId = baseConfig?.crossSellBagContextId;
    
    // Get country config for GWP variables
    const websiteCode = getStoreConfig(storeId, 'websiteCode');
    const giftConfig = getBaseConfig('giftProducts');
    const countryConfig = giftConfig?.[websiteCode] || {};
    const { enable_gift_with_purchase_module_feature, eligible_products_context_id } = countryConfig || {};
    
    responseObject.enableGWPContextFeature = enable_gift_with_purchase_module_feature || false;
    responseObject.enableGWPContextId = enable_gift_with_purchase_module_feature ? eligible_products_context_id : null;
  } catch (e) {
    logError(e, "Error in getting Base Config");
  }

  // dummy calulation data

  // quote.subtotal = 119;
  // quote.subtotalWithDiscount = 107.1;
  // quote.shippingCharges = 10;
  // quote.codCharges = 9;
  // quote.amstorecreditAmount = 75.59;
  // storeCreditObjectStoreValue = 75.59;
  // quote.amstorecreditUse = true;

  //	After discount/promos/taxes/shipping charges
  const d = quote.subtotal - quote.subtotalWithDiscount;
  const fd = formatPrice(d);
  const discountAmount = formatPrice(
    quote.subtotal - quote.subtotalWithDiscount
  );

  const customDutiesPercentage = Number(
    getStoreConfig(storeId, "customDutiesPercentage") || 0
  );
  const importMinFeePercentage = Number(
    getStoreConfig(storeId, "importFeePercentage") || 0
  );
  const importMaxFeePercentage = Number(
    getStoreConfig(storeId, "importMaxFeePercentage") || 0
  );
  const minimumDutiesAmount = Number(
    getStoreConfig(storeId, "minimumDutiesAmount") || 0
  );

  const showAddLowValueAmount = Number(
    getStoreConfig(storeId, "showAddLowValueAmount") || 0
  );
 
  if (!retryPayment) {
    if (handleBnplIssue && orderCreation && isInstallmentMethod) {
      // logger.info(`processQuote: Quote ${quote.id} - BNPL issue handling enabled, skipping free shipping evaluation`);
    } else {
      await evaluateFreeShipping({
        quote,
        responseObject,
        storeId,
        xHeaderToken,
        collection,
        optimized: true
      });
    }
  } 
  const shukranFreeShippingTier = getAppConfigKey('shukranFreeShippingTier') || ["platinum"];
  const isFreeShippingEligible = quote.shippingCharges > 0 && quote.tier && shukranFreeShippingTier.includes(quote.tier.toLowerCase()) && isShukranEnable;
  if (handleBnplIssue && orderCreation && isInstallmentMethod) {
    // logger.info(`processQuote: Quote ${quote.id} - BNPL issue handling enabled, preserving original shipping charges`);
  } else {
    isFreeShippingEligible && (quote.shippingCharges = 0);
  }
  // A
  const estimatedTotal =
    quote.subtotal - discountAmount + quote.shippingCharges + quote.codCharges;
  // B

  let customDutiesAmount = 0;
  let importFeePercentage = importMinFeePercentage;
  if (estimatedTotal > minimumDutiesAmount) {
    customDutiesAmount = (estimatedTotal * customDutiesPercentage) / 100;
    importFeePercentage = importMaxFeePercentage;
  }
  // A + B
  const estimatedTotalWithCustoms = estimatedTotal + customDutiesAmount;
  // C
  let importFeesAmount =
    (estimatedTotalWithCustoms * importFeePercentage) / 100;
  // A + B + C
  // let grandTotal =
  //   estimatedTotalWithCustoms + importFeesAmount + adjustedDonation;
  // logInfo("import fee before round off:", importFeesAmount);
  importFeesAmount = formatPrice(importFeesAmount + customDutiesAmount);
  // logInfo("importFeesAmount:", importFeesAmount);
  // logInfo("customDutiesAmount:", customDutiesAmount);
  // UIB-4047 - START
  let grandTotal;
  if (handleBnplIssue && orderCreation && isInstallmentMethod) {
    //Don't re calculate grandTotal, set grandTotalCopy to  grandTotal
    grandTotal =  quote.grandTotalCopy;
    // logger.info(`processQuote: Quote ${quote.id} - BNPL issue handling enabled, using cached grandTotal: ${grandTotal}`);
  } else {
    grandTotal =
      quote.subtotal +
      quote.shippingCharges +
      quote.codCharges +
      importFeesAmount +
      adjustedDonation -
      discountAmount;
      quote.grandTotalCopy = grandTotal;
      // logger.info(`processQuote: Quote ${quote.id} - Calculated new grandTotal: ${grandTotal}`);
  }
  // UIB-4047 - END
  // logInfo("grandTotal::", grandTotal);
  responseObject.estimatedTotal = String(formatPrice(estimatedTotal));
  responseObject.importFeesAmount = String(importFeesAmount);

  //	[Sum for all products (Qty * Base Price)]
  responseObject.total = String(formatPrice(totalPrice));

  //	Styli Discount (Sum of (Base Price - Offer Price))
  responseObject.discount = String(formatPrice(totalPrice - quote.subtotal));

  //	Coupon Code Discount
  responseObject.couponDiscount = String(formatPrice(quote.couponDiscount));
  responseObject.autoCouponApplied = quote.autoPromoCode;
  responseObject.autoCouponDiscount = quote.autoPromoAmount;
  responseObject.shippingThreshold = String(
    formatPrice(quote.shippingThreshold)
  );
  responseObject.shippingMethod = addressObject?.shippingMethod;
  responseObject.shippingWaived = true;
  responseObject.shippingDescription = addressObject?.shippingDescription;
  responseObject.shippingAmount = String(formatPrice(quote.shippingCharges));
  responseObject.shippingFreeCeiling = null;
  if (quote.shippingThreshold > quote.subtotalWithDiscount) {
    responseObject.shippingFreeCeiling = String(
      formatPrice(quote.shippingThreshold - quote.subtotalWithDiscount)
    );
  }
  responseObject.shippingInclTax = String(formatPrice(quote.shippingCharges));
  responseObject.selectedAddressId = addressObject?.customerAddressId;
  responseObject.selectedPaymentMethod = paymentObject.method;
  responseObject.isCouponApplicable =
    quote.couponCode != undefined && quote.couponCode != null;
  responseObject.couponCodeApplied = quote.couponCode;
  responseObject.currency = quote.storeCurrencyCode;

  responseObject.storeCreditApplied = null;
  responseObject.storeCreditBalance = null;

  if (quote.paidStyliCredit) {
    storeCreditObjectStoreValue = quote.paidStyliCredit;
  }
  if (quote.customerId && storeCreditObjectStoreValue) {
    responseObject.storeCreditBalance =
      String(Math.trunc((storeCreditObjectStoreValue || 0) * 100) / 100) ||
      null;
    if (quote.amstorecreditUse && quote.amstorecreditAmount) {
      const applicableAmountForStoreCredit = Math.min(
        grandTotal,
        quote.amstorecreditAmount,
        storeCreditObjectStoreValue
      );
      // Doubts on the below value. not in use, but verify.
      // quote.amstorecreditBaseAmount = applicableAmountForStoreCredit / currencyConversionRate;
      quote.amstorecreditAmount = applicableAmountForStoreCredit;

      if (applicableAmountForStoreCredit == 0) {
        quote.amstorecreditBaseAmount = null;
        quote.amstorecreditAmount = null;
        quote.amstorecreditUse = false;

        // await upsertQuote({ storeId, quote, collection, xHeaderToken });
      }

      // responseObject.storeCreditApplied =
      //   String(Math.trunc((quote.amstorecreditAmount || 0) * 100) / 100) ||
      //   null;
      if (quote.paidStyliCredit) {
        responseObject.storeCreditApplied = "0";
      } else {
        responseObject.storeCreditApplied =
          String(quote.amstorecreditAmount) || null;
      }

      grandTotal = grandTotal - Number(quote.amstorecreditAmount || 0);
    }
  }

  if (quote.customerId && quote?.coinDiscountData?.isCoinApplied) {
    grandTotal =
      grandTotal - Number(quote.coinDiscountData?.storeCoinValue || 0);
  }
  const cleanTotal =
    Number(grandTotal) -
    Number(quote.codCharges || 0) -
    Number(adjustedDonation) -
    Number(quote.shippingCharges || 0);
  const shippingThresholdDiff = Number(quote.shippingThreshold) - cleanTotal;
  responseObject.showLowValueCta =
    shippingThresholdDiff > 0 &&
    shippingThresholdDiff < Number(showAddLowValueAmount);
  responseObject.showAddLowValueAmount = showAddLowValueAmount;

  // logInfo(
  //   "getQuoteValues",
  //   {
  //     estimatedTotal,
  //     subtotal: quote.subtotal,
  //     shippingCharges: quote.shippingCharges,
  //     customDutiesAmount,
  //     importFeesAmount,
  //     discountAmount,
  //     appliedStoreCredit: quote.amstorecreditAmount,
  //     grandTotal,
  //   },
  //   xHeaderToken
  // );

  /**setting for Enabale & disable stylicredeit **/

  // logInfo("totalOrderedCount", quote.totalOrderedCount);
  let grandTotalExcludeCodAndDonation =
    grandTotal - quote.codCharges - adjustedDonation;
  const minimumFirstOrderValue = Number(
    getStoreConfig(storeId, "minimumFirstOrderValue") || 0
  );

  responseObject.isStoreCreditEnabled = true;

  if (
    quote.totalOrderedCount == 0 &&
    grandTotalExcludeCodAndDonation < minimumFirstOrderValue
  ) {
    responseObject.isStoreCreditEnabled = false;
  }

  responseObject.addQuoteAmountToEnableStyliCredit = String(
    formatPrice(minimumFirstOrderValue)
  );
  
  responseObject.grandTotal = String(formatPrice(grandTotal));
  responseObject.baseGrandTotal = String(formatPrice(grandTotal));

  if (shukranEnabled){ 
    let shukranBurnCashValue = quote?.totalShukranBurnValueInCurrency || 0;
    responseObject.estimatedTotal = String(formatPrice(responseObject.estimatedTotal-shukranBurnCashValue));
    responseObject.grandTotal = String(formatPrice(grandTotal - shukranBurnCashValue) );
    responseObject.baseGrandTotal = String(formatPrice(grandTotal - shukranBurnCashValue));
  }
  responseObject.taxAmount = String(formatPrice(totalTaxAmount));
  responseObject.taxPercent = String(taxPercentage);

  responseObject.codCharges = String(formatPrice(quote.codCharges));
  responseObject.calcSource = quote.calcSource;
  setStyliCashApplicableTotal({quote, finalGrandTotal: responseObject.grandTotal, adjustedDonation, responseObject});
  const matchingProducts = quote.quoteItem
    .map((item) =>
      productsArr.find(
        (product) =>
          product.productId === item.productId &&
          Number(product.quantity) === Number(item.qty)
      )
    )
    .filter(Boolean);
  responseObject.quoteProducts = matchingProducts;

  if (shukranEnabled){
    const safeValue = (value) => (value === null || isNaN(value) ? 0 : value);
    responseObject.quoteProducts.forEach((product, index) => {
      if (quote.quoteItem[index]) {
          product.shukranBurn = safeValue(quote.quoteItem[index].shukranBurn);
          product.shukranBurnInCurrency = safeValue(quote.quoteItem[index].shukranBurnInCurrency);
          product.shukranBurnInBaseCurrency = safeValue(quote.quoteItem[index].shukranBurnInBaseCurrency);
      }
    });
  }

  setStyliCashBurn(quote, responseObject);
  responseObject.products = productsArr;
  responseObject.discountData = quote.discountData;

  responseObject.shippingAddress = addressObject;
  responseObject.isWhitelistedCustomer = quote.isWhitelistedCustomer;
  responseObject.otpFlag = quote.otpFlag;
  responseObject.tabbyPaymentId = orderCreation ? quote.tabbyPaymentId : "";
  responseObject.availablePaymentMethods = paymentMethodsFromConfig(
    quote.storeId
  );
  if (responseObject.totalShukranBurn > 0) {
    const shukranPaymentMethod =  getAppConfigKey('shukranPaymentMethod');
    responseObject.availablePaymentMethods = [
      ...responseObject.availablePaymentMethods,
      shukranPaymentMethod,                  
    ];
  }
  responseObject.priceDropData = quote.priceDropData;
  responseObject.coinDiscountData = quote.coinDiscountData;

  if (bagView !== 1) {
    const paymentRestriction = this.getPaymentRestriction({
      appliedFRulesOutput: quote.appliedFRulesOutput,
      grandTotal: responseObject.grandTotal,
      codCharges: responseObject.codCharges,
      storeCreditApplied: responseObject.storeCreditApplied,
      quoteAddress: quote.quoteAddress,
      isWhitelistedCustomer: quote.isWhitelistedCustomer,
      donationAmount: quote.donationAmount,
    });

    responseObject.paymentRestriction = paymentRestriction;
  }

  if (
    ["3.2.0", "3.2.1", "3.2.2"].includes(`${xClientVersion}`) &&
    responseObject.selectedPaymentMethod === null &&
    Number(responseObject.storeCreditBalance) >=
      Number(responseObject.grandTotal) &&
    !orderCreation &&
    !bagView
  ) {
    responseObject.storeCreditBalance = "0.1";
    responseObject.grandTotal = "0.00";
  }

  responseObject.notifications = quote.notifications || [];
  responseObject.configShipmentCharges = quote.configShipmentCharges;
  // logInfo("Config Shipment Charges:", quote.configShipmentCharges);
  if (quote?.notifications?.length && resetNotifs) {
    resetNotifications({ quote, collection, xHeaderToken });
  }
  // logInfo("quote info before return");
  responseObject.promoGroups = quote.groupedData;

  responseObject.retryPayment = quote.retryPayment;
  responseObject.showOOSWishlist = quote.showOOSWishlist;
  responseObject.oosItemCount = quote.oosItemCount;
  if (productsArr.length === 0 && quote.customerIsGuest === 1) {
    responseObject.showOOSWishlist = false;
    responseObject.oosItemCount = 0;
  }
  
  return {
    status: true,
    statusCode: "200",
    statusMsg: "success",
    response: responseObject,
  };
};

exports.getPaymentRestriction = ({
  appliedFRulesOutput = [],
  grandTotal = 0,
  codCharges = 0,
  storeCreditApplied = 0,
  quoteAddress = [],
  isWhitelistedCustomer,
  donationAmount = 0,
}) => {
  const { cityMapper } = quoteAddress[0] || {};
  const { cod_verification, threshold, max_threshold } = cityMapper || {};
  // logInfo("payment restriction totals", {
  //   grandTotal,
  //   codCharges,
  //   donationAmount,
  //   storeCreditApplied,
  // });
  const cleanGrandTotal =
    Number(grandTotal) - Number(codCharges) - Number(donationAmount);
  let codEnabled = true;
  let otpValidation = false;
  let otpValidationType = "NONE";
  let otpFlag = 0;
  let cancelOrder = false;

  // logInfo("payment restriction conditions", {
  //   cleanGrandTotal,
  //   appliedFRulesOutput,
  //   cod_verification,
  //   threshold,
  //   max_threshold,
  // });

  if (
    (appliedFRulesOutput.indexOf("otpValidation") > -1 &&
      !isWhitelistedCustomer) ||
    (cod_verification && cleanGrandTotal <= threshold) ||
    (cod_verification &&
      cleanGrandTotal >= max_threshold &&
      Number(max_threshold || 0) > 0)
  ) {
    otpValidationType = "CITYMAPPER";
    otpFlag = 1;
    if (
      Number(max_threshold || 0) > 0 &&
      cod_verification &&
      cleanGrandTotal >= max_threshold
    ) {
      otpFlag = 3;
    }
    if (
      appliedFRulesOutput.indexOf("otpValidation") > -1 &&
      !isWhitelistedCustomer
    ) {
      otpValidationType = "FRAUD";
      otpFlag = 2;
    }

    otpValidation = true;
  }

  if (appliedFRulesOutput.indexOf("restrictCod") > -1 && !isWhitelistedCustomer)
    codEnabled = false;

  return { codEnabled, otpValidation, otpValidationType, otpFlag ,cancelOrder };
};

const qtySold = async (qty, baseConfig) => {
  if (
    qty &&
    baseConfig?.isSoldDataToBeSend &&
    baseConfig?.soldDataMultiplier &&
    baseConfig?.soldDataThreshold
  ) {
    const qtyValue = Math.floor(qty * baseConfig.soldDataMultiplier);
    return qtyValue >= baseConfig.soldDataThreshold ? qtyValue : 0;
  }
  return 0;
};

// Helper function to check gift product thresholds using quote object (similar to processFreeGift)
const checkGiftProductThresholds = (quote, storeId, xHeaderToken) => {
  try {
    if (!quote || !quote.quoteItem) {
      return { shouldBlock: false, reason: "No quote or quote items available", isRemoved: false };
    }

    const { subtotalWithDiscount } = quote;
    let priceToCompare = Number(subtotalWithDiscount);

    quote?.discountData_v2.forEach(discount => {
      if (discount && discount.redeemType === 'BANK') {
        priceToCompare += Number(discount.value || 0);
      }
    })
    
    //MOB-7147 removing styli cash from the subtotal check for free gifts
    // Adjust for coin discount if applied (same logic as processFreeGift)
    // if (quote.customerId && quote?.coinDiscountData?.isCoinApplied) {
    //   priceToCompare = priceToCompare - Number(quote.coinDiscountData?.storeCoinValue || 0);
    // }

    const websiteCode = getStoreConfig(storeId, 'websiteCode');
    const giftConfig = getBaseConfig('giftProducts');
    const countryConfig = giftConfig?.[websiteCode] || {};
    const { enabled, collections } = countryConfig;

    // Find gift products in quote items (same logic as processFreeGift)
    const toRemoveSkuObj = quote?.quoteItem?.find(item => item.isGift);
    
    if (!toRemoveSkuObj?.sku) {
        return { shouldBlock: false, reason: "No gift products found in quote", isRemoved: false };
    }

    // Check if gift feature is enabled
    if (!enabled) {
         return { shouldBlock: true, reason: "Gift products feature is disabled", isRemoved: false };
    }

    // Get context ID and minimum bag value (same logic as processFreeGift)
    const contextId = getContextId({
      enrich: toRemoveSkuObj?.enrich,
      storeId,
      xHeaderToken
    });
    
    const minBagVal = Number(
      collections?.find(col => col.contextId === contextId)?.minBagVal || 0
    );

    // if(contextId === '') {
    //   return { 
    //     shouldBlock: true, 
    //     reason: `Gift product not available for this context`,
    //     priceToCompare,
    //     contextId 
    //   };
    // }

    // Check threshold (same logic as processFreeGift)
    if (minBagVal==0 || (minBagVal > 0 && Number(priceToCompare) < minBagVal)) {
      return { 
        shouldBlock: true, 
        reason: `Cart total (${priceToCompare}) is below minimum threshold (${minBagVal}) for gift products`,
        minBagVal,
        priceToCompare,
        contextId,
        isRemoved: true
      };
    }

     return { shouldBlock: false, reason: null, isRemoved: false };
  } catch (error) {
    logError(error, 'Error checking gift product thresholds', xHeaderToken);
    return { shouldBlock: false, reason: "Error in threshold check", isRemoved: false };
  }
};

// Helper function to create gift product map from quote items
const createGiftProductMap = (quoteItems) => {
  const giftProductMap = new Map();
  if (quoteItems?.length) {
    for (const item of quoteItems) {
      if (item.sku) {
        giftProductMap.set(item.sku, Boolean(item.isGift));
      }
    }
  }
  return giftProductMap;
};

// Helper function to filter gift products based on simple rules
const filterGiftProductsSimple = (productArr) => {
    let isGiftRemoved = false;
  const giftProducts = productArr.filter(product => product?.is_gift === 1);
  if (giftProducts.length >= 2) {
    // logger.info(`[GIFT_REMOVAL] Removing ${giftProducts.length} gift products due to multiple gift rule:`, 
    //   giftProducts.map(p => ({ 
    //     sku: p.sku, 
    //     is_gift: p.is_gift, 
    //     name: p.name,
    //     objectID: p.objectID 
    //   })));
      isGiftRemoved = true;
    return { productArr: productArr.filter(product => product?.is_gift !== 1), isRemoved: isGiftRemoved };
  }
  return { productArr, isRemoved: isGiftRemoved };
};

// Helper function to filter gift products based on quote thresholds
const filterGiftProductsWithQuote = (productArr, quote, storeId, xHeaderToken) => {
  const thresholdCheck = checkGiftProductThresholds(quote, storeId, xHeaderToken);
  if (!thresholdCheck.shouldBlock) {
    return { productArr, isRemoved: thresholdCheck.isRemoved };
  }

  // logger.info(`[GIFT_REMOVAL] Threshold check failed: ${thresholdCheck.reason}`);
  
  const giftProductMap = createGiftProductMap(quote.quoteItem);
  const productsBeforeFilter = productArr.length;
  
  // logger.info(`[GIFT_REMOVAL] Before filtering: ${productsBeforeFilter} products`);
  
  const filteredProducts = productArr.filter(product => {
    if (product?.isGift === 1) {
      // logger.info(`[GIFT_REMOVAL] Removing product due to is_gift=1:`, {
      //   sku: product.sku,
      //   name: product.name,
      //   objectID: product.objectID
      // });
      return false;
    }
    
    if (Array.isArray(product.sku)) {
      for (const sku of product.sku) {
        if (giftProductMap.get(sku)) {
          // logger.info(`[GIFT_REMOVAL] Removing product due to gift SKU in quote:`, {
          //   productSku: product.sku,
          //   matchedSku: sku,
          //   productName: product.name,
          //   objectID: product.objectID
          // });
          return false;
        }
      }
    }
    
    return true;
  });
  
  const removedCount = productsBeforeFilter - filteredProducts.length;
  // logger.info(`[GIFT_REMOVAL] After filtering: ${filteredProducts.length} products remaining, ${removedCount} products removed`);
  
   return { productArr: filteredProducts, isRemoved: removedCount > 0 };
};

// Helper function to process gift product filtering
const processGiftProductFiltering = (productArr, hits, quote, storeId, xHeaderToken) => {
  if (!hits?.length) return { productArr, isRemoved: false };
  const giftProducts = hits.filter(product => product?.is_gift === 1);
if (quote) {
  // Single gift product rule
  if (hits.length === 1 && hits[0]?.is_gift === 1) {
    // logger.info(`[GIFT_REMOVAL] Removing single gift product:`, {
    //   sku: productArr[0]?.sku,
    //   name: productArr[0]?.name,
    //   objectID: productArr[0]?.objectID
    // });
    return {productArr:productArr.filter(product => product?.is_gift !== 1), isRemoved: true};
  }
  // Multiple gift products rule
  else if (giftProducts.length >= 2) {
    return filterGiftProductsSimple(productArr);
  }
  // Quote-based filtering
  else {
    return filterGiftProductsWithQuote(productArr, quote, storeId, xHeaderToken);
  }
}
  return { productArr, isRemoved: false };
};