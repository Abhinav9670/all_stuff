const _ = require("lodash");
const fetchQuote = require("../../helpers/fetchQuote");
const { upsertQuote } = require("../../helpers/upsertQuote");
const quoteProcessor = require("../../helpers/v6/quoteProcessor");
const { removeItemFromQuote } = require('../../helpers/removeItem');
const {
  addProductToQuote,
  validateAddProductReq
} = require('../../helpers/addToQuote');
const {
  formatPrice,
  getStoreConfig,
  getAppConfigKey,
  logError,
  paymentMethodsFromConfig,
  logInfo,
  getAppConfig,
  bnblCalculation,
  getBaseConfig
} = require("../../helpers/utils");
// const { logger } = require("../../helpers/utils");
const {
  collection,
  initcluster,
  customerCollection,
} = require("../../config/couchbase.js");
const { processPromoCalculations } = require("../../promoApis/v6/processPromo");
const { appendCityDetails } = require("../../helpers/v6/appendCityDetails");
const { calcQuoteDonation } = require("../../helpers/donation");
const { checkFraud } = require("../../helpers/v6/checkFraud");
const paymentService = require("../../helpers/payments/paymentService");
const { uuidCheckFailed, uuidError } = require("../../helpers/validateToken");
const cache = require("memory-cache");
const { getCustomerOrderList } = require("../../javaApis/orderedCount");
const { processCoinCalculations } = require("../../easApis/getStoreCoin");
const { evaluateFreeShipping } = require("../../helpers/v6/freeShipping");
const {
  handleRetroQuotesForItemsSequence,
} = require("../../helpers/v6/promoBundleContext");
const { getCustomerInfo, evaluatePreferredMethod, checkShukranEnabled } = require("../../helpers/customer");
const { inventoryCheck } = require("../../javaApis/product");
const { processFreeGift } = require("../../helpers/freeGifts");
const { checkQuoteIssues } = require("../../helpers/v6/checkQuoteIssues");
const { getProducts } = require("../../elastic/actions");
const { getRegisteredSince } = require("../../helpers/payments/tabbyService");
const { getShukranProfile } = require("../../helpers/v6/shukran.js");
const { getCustomerDefaultAddress } = require("../../helpers/customer");
const moment = require("moment");

const { calculateOrderSplit } = require("../../helpers/orderSplitHelper.js");
const { getProductSLADetails } = require("../../javaApis/product.js");
const { processOrderSplitLogic } = require("../../helpers/v7/processOrderSplit.js");
const { setStyliCashApplicableTotal } = require("../../helpers/styliCash.js");
const { processBurnShukranCoinCalculations } = require("../../shukran/getShukranPoint.js");
const { calculateHighestSLA } = require("../../helpers/v6/appendCityDetails");
const { calculateCodCharges } = require("../../helpers/v6/codCharges.js");


exports.getQuoteBasicData = async ({ req, res, pool }) => {
  const { uuid } = req;
  const { body } = req.body;

  const { headers } = req;  
  const xHeaderToken = headers["x-header-token"] || "";
  const xSource = headers["x-source"] || "";
  const xClientVersion = headers["x-client-version"] || "";

  const { storeId } = body;
  const quoteId = String(body?.quoteId || "");
  const customerId = String(body?.customerId || "");
  let quote;
  const basicDataColl = await collection();
  const basicDataCluster = await initcluster();
  if (!customerId) {
    quote = await fetchQuote.fetchQuote({
      identifier: quoteId,
      storeId,
     collection:basicDataColl,
     cluster:basicDataCluster,
      type: "guest",
      res,
    });
  } else {
    quote = await fetchQuote.fetchQuote({
      identifier: customerId,
      storeId,
      collection:basicDataColl,
      cluster:basicDataCluster,
      type: "customer",
      res,
    });
  }
  if (!quote) {
    return res.status(200).json({
      status: false,
      statusCode: "202",
      statusMsg: "quote not found!",
    });
  }

  try {
    if (uuidCheckFailed({ uuid, quote })) return uuidError(res);

    let parentSkus = [];
    let childSkus = [];
    for (const item of quote.quoteItem || []) {
      parentSkus.push(item.parentSku);
      childSkus.push(item.sku);
    }

    const promisesArray = [];
    const inventoryRequestObject = {
      skus: childSkus,
      storeId,
    };
    // 0 promise
    promisesArray.push(
      inventoryCheck({
        inventoryRequestObject,
        xHeaderToken,
        orderCreation: false,
      })
    );
    // 1 promise
    promisesArray.push(getProducts({ parentSkus, storeId, xHeaderToken }));

    const resPromiseAll = await Promise.all(promisesArray).then((values) => {
      return values;
    });

    const invCheck = resPromiseAll[0];
    const {
      productArr: productDetailsResponse,
    } = resPromiseAll[1];

    // handle issue with qutoe and promo and/or calculations
    quote = await checkQuoteIssues({
      quote,
      storeId,
      productDetailsResponse,
      collection:basicDataColl,
      xHeaderToken,
      xSource,
      xClientVersion,
      invCheck,
      screenName: "bag",
      orderCreation: false,
      processPromo: false
    });

    let responseObject = {};
    responseObject.storeId = String(storeId);
    responseObject.quoteId = String(quoteId);
    responseObject.customerId = quote.customerId;

    responseObject.holdOrder = getStoreConfig(storeId,'holdOrder');

    let productsArr = [];
    for (const item of quote.quoteItem) {
      let product = {};
      product.parentSku = item.parentSku;
      product.sku = item.sku;
      const inventoryObj = _.find(invCheck, { sku: item.sku.toString() });
      product.quantity = String(item.qty);
      product.quantityStock = String(inventoryObj?.value || 0);
      productsArr.push(product);
    }
    responseObject.products = productsArr;

    quote = await processFreeGift({
      quote,
      invCheck,
      xHeaderToken,
      xClientVersion,
      screenName: "bag",
      collection:basicDataColl
    });

    responseObject.flashSale = quote.flashSale;
    responseObject.priceDropData = quote.priceDropData;
    responseObject.notifications = quote.notifications || [];

    await evaluateFreeShipping({
      quote,
      responseObject,
      storeId,
      xHeaderToken,
      collection:basicDataColl,
      upsertFlag: false,
    });

    return res.status(200).json({
      status: true,
      statusCode: "200",
      statusMsg: "success",
      response: responseObject,
    });
  } catch (e) {
    // logError(e, "get basic quote issue", xHeaderToken);
    return res.status(500).json({
      status: false,
      statusCode: "500",
      statusMsg: e.message,
    });
  }
};

exports.getQuoteTotals = async ({ req, res, pool }) => {
  const { headers, uuid, body } = req;
  const xHeaderToken = headers["x-header-token"] || headers["X-Header-Token"] || "";
  const xClientVersion = headers["x-client-version"] || headers["X-Client-Version"] || "";
  const xSource = headers["x-source"] || headers["X-Source"] || "";
  const baseConfig = cache.get("baseConfig") || {};
  const logPrefix = `getQuoteTotals:::${new Date().toISOString()}`;
  // console.log(logPrefix, body?.customerId ? `Request Body: ${JSON.stringify(body)} for customer :: ${body?.customerId}` : body?.quoteId ? `Request Body: ${JSON.stringify(body)} for quote :: ${body?.quoteId}` : '');
  
    try {
    await pool.query("SELECT 1");
  } catch (e) {
    logError(e, "Error mysql connection in getQuoteTotals", xHeaderToken);
    return res.status(500).json({
      status: true,
      statusCode: "500",
      statusMsg: "mysql connection issue! - " + e.message,
    });
  }
  const quoteTotalColl = await collection();
  const customerColl = await customerCollection();
  const customerCluster = await initcluster();

  if(!quoteTotalColl || !customerColl || !customerCluster){
    // console.log(logPrefix, "couchbase connection missing in totals API call::")
  }

  let {
    storeId,
    applyStoreCredit = applyStoreCredit || true,
    cardBin,
    donationAmount,
    isFirstCall,
    retryPayment = false,
    couponApplied,
    isClubShipment,
    cityId,
    countryId,
    isExpressCheckout = false
  } = body;


  const quoteId = String(body?.quoteId || "");
  const customerId = String(body?.customerId || "");
  let paymentMethod = String(body?.paymentMethod || "");
  const isVersion7 = body?.isVersion7 || false;
  const codCharges = Number(getStoreConfig(storeId, 'codCharges') || 0);
  const shukranQP = Number(getStoreConfig(storeId, 'shukranQP') || 0);
  // logInfo("codCharges during assign:", codCharges);
  const enableParallelDataFetch = baseConfig?.enableParallelDataFetch ?? false;
  const enableAdvancedCodCharges = baseConfig?.enableAdvancedCodCharges ?? false;
  let customerInfo;
  let quote;
  let previousOrderList;
  
  if (enableParallelDataFetch) {
    // logInfo("Using parallel data fetch optimization", "", xHeaderToken);
    
    const parallelPromises = [];
    
    parallelPromises.push(
      getCustomerInfo(customerId).catch(err => {
        // logError(err, "Error fetching customer info in parallel", xHeaderToken);
        return null;
      })
    );
    
    if (retryPayment) {
      parallelPromises.push(
        fetchQuote.fetchQuote({
          identifier: quoteId,
          storeId,
          collection:quoteTotalColl,
          cluster:customerCluster,
          type: "guest",
          res,
          retryPaymentReplica: true
        }).catch(err => {
          // logError(err, "Error fetching quote in parallel (retry)", xHeaderToken);
          return null;
        })
      );
    } else {
      if (!customerId) {
        parallelPromises.push(
          fetchQuote.fetchQuote({
            identifier: quoteId,
            storeId,
            collection:quoteTotalColl,
            cluster:customerCluster,
            type: "guest",
            res,
          }).catch(err => {
            // logError(err, "Error fetching quote in parallel (guest)", xHeaderToken);
            return null;
          })
        );
      } else {
        parallelPromises.push(
          fetchQuote.fetchQuote({
            identifier: customerId,
            storeId,
            collection:quoteTotalColl,
            cluster:customerCluster,
            type: "customer",
            res,
          }).catch(err => {
            // logError(err, "Error fetching quote in parallel (customer)", xHeaderToken);
            return null;
          })
        );
      }
    }
    
    const [customerInfoResult, quoteResult] = await Promise.all(parallelPromises);
    customerInfo = customerInfoResult;
    quote = quoteResult;
    
    if (quote) {
      previousOrderList = await getCustomerOrderList({
        quote,
        customerEmail: xHeaderToken,
        xHeaderToken,
      }).catch(err => {
        // logError(err, "Error fetching previous orders", xHeaderToken);
        return [];
      });
    } else {
      previousOrderList = [];
    }
  } else {
    customerInfo = await getCustomerInfo(customerId);
  }
  
  const isShukranEnable = getStoreConfig(storeId, 'isShukranEnable') || false;
  const orderSplitFlag = getStoreConfig(storeId, "orderSplitFlag") || false;
  
  // API-3677
  let shukranEnabled = false;
  if(customerId){
    const shukranProfileId = customerInfo?.profileId || '';
    const shukranLinkFlag = customerInfo?.shukranLinkFlag ?? false;
    shukranEnabled = checkShukranEnabled(customerId, storeId, shukranProfileId, shukranLinkFlag);
  }

  // console.log(`API-3761 - shukranEnabled TOTAL API @@@ ${shukranEnabled}`);

  let tierName = 'classic';
  if (shukranEnabled && !retryPayment){ 
    const shukranCurrencyCode = await getShukranCurrencyCode(storeId);
    let customerDetails = customerInfo;
    customerDetails['shukranCurrencyCode'] = shukranCurrencyCode;
    const getProfile = await getShukranProfile({customerDetails});
    tierName = (getProfile?.TierName || 'classic').toLowerCase();
  }

  // logInfo("customerInfo:",customerInfo);
  if(customerInfo && customerInfo?.customerBlocked && baseConfig && baseConfig.enableCustomerBlock){
    return res.status(200).json({
      status: false,
      statusCode: "204",
      statusMsg: `User Id: ${customerId} Is Blocked`,
      isForceLogout: false,
    });
  }
  if (customerInfo?.isActive == 2) {
    return res.status(403).json({
      status: false,
      statusCode: '403',
      statusMsg: 'jwt malformed',
      isForceLogout: true,
    });
  }

  if (donationAmount && donationAmount < 0) {
    res.status(200);
    res.json({
      status: false,
      statusCode: "202",
      statusMsg: "invalid donation amount!",
    });
    return;
  }

  if (!enableParallelDataFetch) {
    if(retryPayment) {
      quote = await fetchQuote.fetchQuote({
        identifier: quoteId,
        storeId,
        collection:quoteTotalColl,
        cluster:customerCluster,
        type: "guest",
        res,
        retryPaymentReplica: true
      });
      if(quote && !quote.retryPayment) {
        return res.status(200).json({
          status: true,
          statusCode: "200",
          statusMsg: "Retry payment is not allowed",
          response: {},
        });
      }
    } else {
      if (!customerId) {
        quote = await fetchQuote.fetchQuote({
          identifier: quoteId,
          storeId,
          collection:quoteTotalColl,
          cluster:customerCluster,
          type: "guest",
          res,
        });
      } else {
        quote = await fetchQuote.fetchQuote({
          identifier: customerId,
          storeId,
          collection:quoteTotalColl,
          cluster:customerCluster,
          type: "customer",
          res,
        });
      } 
    }
    
    if (!quote) {
      return handleQuoteNotFound(res);
    }

    if (uuidCheckFailed({ uuid, quote })) return uuidError(res);

    previousOrderList = await getCustomerOrderList({
      quote,
      customerEmail: xHeaderToken,
      xHeaderToken,
    });
  } else {
    // Parallel fetch path - validate quote
    if (!quote) {
      return handleQuoteNotFound(res);
    }
    
    if (uuidCheckFailed({ uuid, quote })) return uuidError(res);
  }
  
  quote.previousOrderList = previousOrderList;

  let paymentObject = quote.quotePayment;
  if (!paymentMethod) {
    paymentMethod = paymentObject.method;
    cardBin = paymentObject.cardBin;
  }

  const availablePaymentMethods = paymentMethodsFromConfig(storeId);
  // logInfo("availablePaymentMethods:",availablePaymentMethods)
  if (availablePaymentMethods.indexOf(paymentMethod) < 0) {
    res.status(200);
    res.json({
      status: false,
      statusCode: "202",
      statusMsg: "invalid payment method!",
    });
    return;
  }

  // logInfo("retrypaymentMethod",retryPayment)
  if(!retryPayment) {
    quote = await appendCityDetails({
      quote,
      cluster:customerCluster,
      xHeaderToken,
      paymentMethod,
    });
  }
  
  quote = await checkFraud({
    quote,
    cluster:customerCluster,
    customerCollection:customerColl,
    xHeaderToken,
    pool,
  });
  const addressObject =
    _.filter(
      quote?.quoteAddress || [],
      (el) => el.addressType == "shipping"
    )[0] || {};
  addressObject.cityMapper = quote?.city || {};

//  logInfo("addressObject::",addressObject);
  quote.quotePayment.method = paymentMethod;
  quote.quotePayment.updatedAt = new Date();
  quote.quotePayment.cardBin =
    ["md_payfort_cc_vault", "md_payfort"].indexOf(paymentMethod) > -1
      ? cardBin
      : "";
  quote.quotePayment.applyStoreCredit = applyStoreCredit;
  if(!retryPayment)
    quote.discountData = [];

  paymentObject = quote.quotePayment;
  let storeCreditObject;
  let storeCreditObjectStoreValue;

  const currencyConversionRate = Number(
    getStoreConfig(storeId, "currencyConversionRate") || 1
  );
  quote.currencyConversionRate = currencyConversionRate;
  if(!retryPayment)
    quote = await processPromoCalculations({
      storeId,
      quote,
      productDetailsResponse: null,
      collection:quoteTotalColl,
      upsert: false,
      xSource,
      xClientVersion,
      xHeaderToken,
      isVersion7,
    });

  let totalPrice = 0;
  let originalPricesAvailable = true;
  if (quote && quote.quoteItem) {
    for (const item of quote.quoteItem) {
      if (!item.originalPrice) {
        originalPricesAvailable = false;
        break;
      }
      totalPrice += item.originalPrice * item.qty;
    }
  } else {
    originalPricesAvailable = false;
  }

  let responseObject = {};
  if(!retryPayment)
    await evaluateFreeShipping({
      quote,
      responseObject,
      storeId,
      xHeaderToken,
      collection:quoteTotalColl,
      customerInfo
    });

  let adjustedDonation = 0;
  const donationResponse = calcQuoteDonation({
    quote,
    newDonation: donationAmount,
  });
  quote = donationResponse.quote;
  adjustedDonation = donationResponse.adjustedDonation;

 
  if(isVersion7 && orderSplitFlag){
   quote.orderSplitFlag = orderSplitFlag;
  }

  responseObject.donationAmount = String(adjustedDonation);

  responseObject.storeId = String(quote.storeId);
  responseObject.quoteId = String(quote.id);
  responseObject.subtotal = String(formatPrice(quote.subtotal));
  responseObject.total = String(formatPrice(quote.subtotal));
  responseObject.discount = String("0");
  if (originalPricesAvailable) {
    responseObject.total = String(formatPrice(totalPrice));
    responseObject.discount = String(formatPrice(totalPrice - quote.subtotal));
  }
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
  // logInfo("retryPayment:", retryPayment , "");
  // logInfo("paymentMethod:", paymentMethod);
  // logInfo("codCharges::", codCharges);
  // console.log('[COD CHARGES] getQuoteTotals - enableAdvancedCodCharges:', enableAdvancedCodCharges, 'isVersion7:', isVersion7, 'retryPayment:', retryPayment);
  if(retryPayment){
    if (!enableAdvancedCodCharges || !isVersion7) { 
      // console.log('[COD CHARGES] getQuoteTotals - Using legacy COD calculation (flag disabled or not version7)');
      quote.codCharges = formatPrice((paymentMethod === "cashondelivery") ? codCharges : 0);
    }else{
      // console.log('[COD CHARGES] getQuoteTotals - Using advanced COD calculation (flag enabled and version7)');
      const codChargesResult = calculateCodCharges({
        quoteItems: quote.quoteItem || [],
        quote,
        storeId,
        paymentMethod: paymentMethod
      });
      // console.log('[COD CHARGES] getQuoteTotals - codChargesResult:', JSON.stringify(codChargesResult));
      // Set COD charges directly on quote object
      quote.codCharges = codChargesResult.totalCodCharges;
      quote.expressCodCharges = codChargesResult.expressCodCharges;
      quote.globalCodCharges = codChargesResult.globalCodCharges;
    }
  }
  // API-3624
  quote.tier = tierName;
  const shukranFreeShippingTier = getAppConfigKey('shukranFreeShippingTier') || ["platinum"];
  if(quote.shippingCharges > 0 && quote.tier && shukranFreeShippingTier.includes(quote.tier.toLowerCase()) && isShukranEnable){
     quote.shippingCharges = 0;
  }
  
  // A
  const estimatedTotal =
    quote.subtotal - discountAmount + quote.shippingCharges + quote.codCharges;

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
  // let grandTotal = estimatedTotalWithCustoms + importFeesAmount + adjustedDonation;
  // logInfo("import fee before round off:", importFeesAmount);
  importFeesAmount = formatPrice(importFeesAmount + customDutiesAmount);
  // logInfo("importFeesAmount:", importFeesAmount);
  // logInfo("customDutiesAmount:", customDutiesAmount);
  const minimumFirstOrderValue = Number(
    getStoreConfig(storeId, "minimumFirstOrderValue") || 0
  );

  let grandTotal =
    quote.subtotal +
    quote.shippingCharges +
    quote.codCharges +
    importFeesAmount +
    adjustedDonation -
    discountAmount;

  

  const totalOrderedCount = quote.totalOrderedCount;

  
  /** this value set for retry payment order if order placed by using styli credit **/
  if (quote.paidStyliCredit) {
    responseObject.paidStyliCredit = String(formatPrice(quote.paidStyliCredit));
    grandTotal = grandTotal - Number(quote.paidStyliCredit || 0);
  }

  const isSLAEnabled = getStoreConfig(storeId, 'isSLAEnabled') || false;
  if (isSLAEnabled) {

    const inventoryRequestObject = {
      skus: quote.quoteItem?.map(item => item.sku) || [],
      storeId,
    };

    let invCheckResult = null;
    let uniqueWarehouseIds = null;
    try {
      invCheckResult = await inventoryCheck({ inventoryRequestObject, xHeaderToken, orderCreation: false });
      uniqueWarehouseIds = [...new Set(invCheckResult?.filter(item => item?.warehouseId)?.map(item => item.warehouseId) || [])];
      invCheckResult = null;

      if (uniqueWarehouseIds.length && addressObject?.cityMapper?.id) {
        const slaDetails = await calculateHighestSLA({ uniqueWarehouseIds, cityId: addressObject.cityMapper.id, countryId: addressObject.cityMapper.countryId });
        if (slaDetails) addressObject.cityMapper = slaDetails;
      }
      // Clean up array reference
      uniqueWarehouseIds = null;
    } catch (error) {
      invCheckResult = null;
      uniqueWarehouseIds = null;
    }
  }

  // logInfo("totalOrderedCount", totalOrderedCount);
  let grandTotalExcludeCodAndDonation =
    grandTotal - quote.codCharges - adjustedDonation;
  responseObject.isStoreCreditEnabled = true;

  if (
    totalOrderedCount == 0 &&
    grandTotalExcludeCodAndDonation < minimumFirstOrderValue
  ) {
    responseObject.isStoreCreditEnabled = false;
  }

  responseObject.estimatedTotal = String(formatPrice(estimatedTotal));
  responseObject.importFeesAmount = String(importFeesAmount);
  responseObject.couponDiscount = String(formatPrice(quote.couponDiscount));
  responseObject.isCouponApplicable =
    quote.couponCode != undefined && quote.couponCode != null;
  responseObject.couponCodeApplied = quote.couponCode;
  responseObject.autoCouponApplied = quote.autoPromoCode;
  responseObject.autoCouponDiscount = quote.autoPromoAmount;
  responseObject.selectedPaymentMethod = paymentObject.method;
  responseObject.shippingAmount = String(formatPrice(quote.shippingCharges));
  responseObject.shippingAddress = addressObject;
  responseObject.addQuoteAmountToEnableStyliCredit = String(
    formatPrice(minimumFirstOrderValue)
  );

  responseObject.discountData = quote.discountData;
  //API-3838 START
  let statusCoinApllied=0;
  const stopStyliCoinBurning = baseConfig?.stopStyliCoinBurning ?? false;
  //If stopStyliCoinBurning is not TRUE get statusCoinApllied from quote.coinDiscountData
  if (!stopStyliCoinBurning) {
    statusCoinApllied  = quote.coinDiscountData
    ? quote.coinDiscountData?.isCoinApplied
    : 0;
  } 
  //API-3838 END
  if (quote.customerId && statusCoinApllied == 1 && !retryPayment) {
    quote = await processCoinCalculations({
      storeId,
      quote,
      collection:quoteTotalColl,
      upsert: true,
      statusCoinApllied,
      xHeaderToken,
    });
  }
  if (quote.coinDiscountData?.coins > 0 && quote.customerId && statusCoinApllied == 1) {
    grandTotal =
      grandTotal - Number(quote.coinDiscountData?.storeCoinValue || 0);
  }
  if (shukranEnabled){
    quote = (grandTotal < (quote?.totalShukranBurnValueInCurrency || 0)) ? await processBurnShukranCoinCalculations({        
      quote, 
      appliedShukranPoint: quote?.appliedShukranPoint || 0,
      shukranAvailablePoint: quote?.shukranAvailablePoint || 0,
      shukranAvailableCashValue: quote?.shukranAvailableCashValue || 0,
      storeId
    }) : quote;
    responseObject.totalShukranBurn = quote?.totalShukranBurn || 0;
    responseObject.totalShukranBurnValueInCurrency = quote?.totalShukranBurnValueInCurrency || 0;
    responseObject.totalShukranBurnValueInBaseCurrency = quote?.totalShukranBurnValueInBaseCurrency || 0;
  
    grandTotal = grandTotal - quote?.totalShukranBurnValueInCurrency || 0;
    responseObject.estimatedTotal = String(formatPrice(responseObject.estimatedTotal-quote?.totalShukranBurnValueInCurrency));
  }
  // API-3677 END
  //API-3732 & API-3791
  if(isShukranEnable){
    let totalShukranEarn = 0;
    for (const item of quote.quoteItem) {
      totalShukranEarn += (item?.shukranEarn || 0)
    }
    responseObject.totalShukranEarn = totalShukranEarn;
  }  
  
  if (quote.customerId) {
    const [rows, fields] = await pool.query(
      "select store_credit from amasty_store_credit where customer_id = ?",
      [quote.customerId]
    );
    storeCreditObject = rows[0]?.store_credit;
    if (storeCreditObject)
      storeCreditObjectStoreValue = formatPrice(
        storeCreditObject / currencyConversionRate
      );
    responseObject.storeCreditBalance =
      String(storeCreditObjectStoreValue) || null;
    if (
      applyStoreCredit &&
      storeCreditObjectStoreValue &&
      responseObject.isStoreCreditEnabled
    ) {
      const applicableAmountForStoreCredit = Math.min(
        grandTotal,
        storeCreditObjectStoreValue
      );
      quote.amstorecreditUse = true;
      quote.amstorecreditAmount = applicableAmountForStoreCredit;
      responseObject.storeCreditApplied =
        String(quote.amstorecreditAmount) || null;
      // Clamp to 0 to avoid negative grand total (store credit cannot exceed balance due)
      const grandTotalAfterStoreCredit = Math.max(
        0,
        Number(grandTotal) - Number(quote.amstorecreditAmount || 0)
      );
      grandTotal = formatPrice(grandTotalAfterStoreCredit);
    } else {
      quote.amstorecreditUse = false;
      responseObject.storeCreditApplied = null;
    }
    if(quote.paidStyliCredit){
      quote.amstorecreditUse = true;
    }
  }
  
  const prepaidPayableData = quote.prepaidPayable || {};
  if (prepaidPayableData.total) {
    let prepaidPayableTotal = prepaidPayableData.total
      ? Number(prepaidPayableData.total || 0) +
      Number(quote.shippingCharges || 0) +
      Number(importFeesAmount || 0)
      : null;

    if (quote?.quotePayment?.applyStoreCredit) prepaidPayableTotal = -1;

    quote.prepaidPayable = {
      ...prepaidPayableData,
      total: prepaidPayableTotal,
    };
  }
  let boolCheck = grandTotal && couponApplied;
  // logInfo(
  //   "totalsValues",
  //   {
  //     estimatedTotal,
  //     subtotal: quote.subtotal,
  //     shippingCharges: quote.shippingCharges,
  //     customDutiesAmount,
  //     importFeesAmount,
  //     discountAmount,
  //     appliedStoreCredit: quote.amstorecreditAmount,
  //     grandTotal,
  //     couponApplied,
  //     boolCheck,
  //   },
  //   xHeaderToken
  // );
  if(0 == grandTotal && couponApplied){
    quote.quotePayment.freeOrder = couponApplied;
  } 
  responseObject.prepaidPayable = quote.prepaidPayable;
  // Ensure grand total is never negative (e.g. store credit exceeding balance, or rounding)
  const safeGrandTotal = Math.max(0, parseFloat(grandTotal) || 0);
  responseObject.grandTotal = String(formatPrice(safeGrandTotal));
  responseObject.baseGrandTotal = String(formatPrice(safeGrandTotal));
  responseObject.codCharges = String(formatPrice(quote.codCharges));
  // console.log('[COD CHARGES] getQuoteTotals - Setting root codCharges:', responseObject.codCharges);
  if (enableAdvancedCodCharges && isVersion7) {
    if (quote.expressCodCharges !== undefined) {
      responseObject.expressCodCharges = String(formatPrice(quote.expressCodCharges));
      // console.log('[COD CHARGES] getQuoteTotals - Added expressCodCharges:', responseObject.expressCodCharges);
    }
    if (quote.globalCodCharges !== undefined) {
      responseObject.globalCodCharges = String(formatPrice(quote.globalCodCharges));
      // console.log('[COD CHARGES] getQuoteTotals - Added globalCodCharges:', responseObject.globalCodCharges);
    }
  } else {
    // console.log('[COD CHARGES] getQuoteTotals - Feature flag disabled, using legacy COD calculation');
  }

  setStyliCashApplicableTotal({quote, finalGrandTotal: grandTotal, adjustedDonation, responseObject});

  let paymentRestriction = quoteProcessor.getPaymentRestriction({
    appliedFRulesOutput: quote?.appliedFRulesOutput,
    grandTotal: responseObject.grandTotal,
    codCharges: responseObject.codCharges,
    storeCreditApplied: responseObject.storeCreditApplied,
    quoteAddress: quote?.quoteAddress,
    isWhitelistedCustomer: quote?.isWhitelistedCustomer,
    donationAmount: quote?.donationAmount || 0,
  });
  if (baseConfig?.checkFraudRules) {
    await applyConsulFraudRules({
      baseConfig,
      previousOrderList,
      responseObject,
      quote,
      paymentRestriction
    });
  }
  if (paymentRestriction?.cancelOrder === true) {
    // console.log("applyConsulFraudRules: cancelOrder is set to true. Forbid this user to place order");
    quote.cancelOrder = true;
    responseObject.cancelOrder = true;
    await deleteQuote({ quote, collection:quoteTotalColl, xHeaderToken });
    return; 
  }

  if (!paymentRestriction.codEnabled && paymentMethod === "cashondelivery") {
    res.status(200);
    res.json({
      status: false,
      statusCode: "202",
      statusMsg: "Cash on Delivery blocked for the user!",
    });
    return;
  }

  responseObject.paymentRestriction = paymentRestriction;
  responseObject.coinDiscountData = quote.coinDiscountData;
  quote.otpFlag = responseObject.paymentRestriction.otpFlag;
  responseObject.xSource = xSource;
  responseObject.xClientVersion = xClientVersion;
  responseObject.customerId = quote.customerId;

  //API-3822 START
  // Check for qualifying purchase condition
  const styliCoinDiscount = quote?.coinDiscountData?.isCoinApplied ? quote?.coinDiscountData?.storeCoinValue || 0 : 0;
  const cartQualityAmount = findQuoteCartValue(quote) - styliCoinDiscount + (quote?.codCharges || 0); // Include COD charges
  const isQualifyingPurchase = cartQualityAmount >= shukranQP;
  quote.isQualifyingPurchase = isQualifyingPurchase;
  responseObject.isQualifyingPurchase = isQualifyingPurchase;
 //API-3822 END
 // API-3677
 //API-3835 - START
 responseObject.shukranBasicEarnPoint = quote?.shukranBasicEarnPoint || 0;
 responseObject.shukranBonousEarnPoint = quote?.shukranBonousEarnPoint || 0;
 const isZeroOrUndefined = (value) => (value || 0) === 0;
 const remainingCartValue = findQuoteCartValue(quote) - (quote?.totalShukranBurnValueInCurrency || 0);
 const isFullyPaidWithShukran = remainingCartValue === 0;
 if (isFullyPaidWithShukran && isZeroOrUndefined(quote?.codCharges) && isZeroOrUndefined(quote?.shippingCharges) && isZeroOrUndefined(quote?.donationAmount)) {
  quote.selectedPaymentMethod = getAppConfigKey('shukranPaymentMethod');
  responseObject.selectedPaymentMethod = quote.selectedPaymentMethod;
}
 //API-3835 - END
 
  const paymentMethods = cache.get('paymentMethods');
  const applePaySkipPaymentService = baseConfig?.applePaySkipPaymentService
  const usePaymentService = paymentMethods?.usePaymentService;
  if (isExpressCheckout && applePaySkipPaymentService){ 
    // console.log("Skipping payment service call for apple pay as per config");
  }else{
    if (usePaymentService){
      await paymentService.paymentMethodsV2(quote, responseObject, headers,xClientVersion,xSource,xHeaderToken);
    }
    else{
      await paymentService.paymentMethods(quote, responseObject, isFirstCall);
    }
  }


  await upsertQuote({ storeId, quote, collection : quoteTotalColl, xHeaderToken });

  if (isFirstCall && baseConfig?.prefferedPaymentsEnable) {
    //Preferred payment method.
    evaluatePreferredMethod(previousOrderList, responseObject);
  }

  responseObject.payfortMerchantReference = quote.payfortMerchantReference;
  responseObject.retryPaymentMethods = cache.get('retryPaymentMethods');
  responseObject.retryPayment = quote.retryPayment;

  let hold = getStoreConfig(storeId,'holdOrder');
  if(hold === '') {
    hold = false;
  }
  const applePayHold = getStoreConfig(storeId,'enableApplepayholdOrder');
      if (applePayHold){
        responseObject.holdOrder = applePayHold;
      }else{
        responseObject.holdOrder =
    responseObject.selectedPaymentMethod === "apple_pay"
      ? quote.retryPayment && quote.failedPaymentMethod?.length > 0
        ? hold
        : false
      : hold;
      }

  if(quote.previousOrderList){

    const pendingOrderList = quote.previousOrderList.filter(order => order.status == "pending_payment" &&
    order.holdOrder == 1);
    // logInfo("pendingOrderList:", pendingOrderList);
    // logInfo("hold order count:", baseConfig?.hold_order_limit);
    if(pendingOrderList?.length && pendingOrderList?.length >= baseConfig?.hold_order_limit){
      responseObject.holdOrder = false;
    }
  }
  responseObject.isShukranEnable = getStoreConfig(storeId, 'isShukranEnable') || false;
    
  //API-3732 & API-3791 END
  if(isVersion7 && orderSplitFlag){
  let childSkus = [];

  for (const item of quote.quoteItem || []) {
    childSkus.push(item.sku);
  }
    const orderSplitResponse = await processOrderSplitLogic({
      productsArr: quote.quoteItem,
      quote,
      responseObject,
      orderSplitFlag,
      isClubShipment,
      storeId,
      xHeaderToken,
      cityId :responseObject?.shippingAddress?.cityMapper?.id || cityId,
      countryId,
      childSkus,
      getStoreConfig,
      getProductSLADetails,
      calculateOrderSplit,
      logInfo,
      formatPrice,
      resetNotifs: false,
      collection: quoteTotalColl,
      addressObject,
      paymentMethodsFromConfig,
      bagView: false,
      orderCreation: false,
      grandTotal: parseFloat(responseObject.grandTotal || 0) || 0,
      isVersion7
    });
    responseObject.isClubShipment = orderSplitResponse?.orderSplitInfo?.isClubShipment;
    responseObject.products = quote.quoteItem || [];
    responseObject = orderSplitResponse;
    const paymentMethods = cache.get('paymentMethods');
    const usePaymentService = paymentMethods?.usePaymentService;
    if (usePaymentService)
      await paymentService.paymentMethodsV2(quote, responseObject, headers,xClientVersion,xSource,xHeaderToken);
    else
      await paymentService.paymentMethods(quote, responseObject, isFirstCall);

    const enableCod = getStoreConfig(storeId, 'enableCod') || false;
    // console.log("value of ~ enableCod:", enableCod)
    if (enableCod === false || enableCod === 'false' || enableCod === 0) {
      if (responseObject?.availablePaymentMethods && Array.isArray(responseObject.availablePaymentMethods)) {
        responseObject.availablePaymentMethods = responseObject.availablePaymentMethods.filter(
          method => method !== 'cashondelivery' && method !== 'cashOnDelivery' && method !== 'CASH_ON_DELIVERY'
        );
        // console.log('[V6 TOTAL API] Removed cashondelivery from availablePaymentMethods because enableCod is false');
      }
    }

    await upsertQuote({ storeId, quote, collection : quoteTotalColl, xHeaderToken });
    return responseObject;
  }
  const itemsQuantity = quote.quoteItem.reduce(
    (total, item) => total + (item.qty || 0),
    0
  );
  responseObject.itemsQty = quote?.itemsQty ? quote.itemsQty : itemsQuantity;
  return res.status(200).json({
    status: true,
    statusCode: "200",
    statusMsg: "quote found!",
    response: responseObject,
  });
};

exports.getQuote = async function ({ req, res, pool, resetNotifs = true }) {
  const { uuid, body } = req;
  const logPrefix = `getQuote:::${new Date().toISOString()}`;
  const { headers } = req;
  const xHeaderToken = headers["x-header-token"] || "";
  const xSource = headers["x-source"] || headers["X-Source"] || "";
  const xOs = headers["x-os"] || "";
  const xBrowser = headers["x-browser"] || "";
  const xClientVersion = headers["x-client-version"] || "";
  const getQuoteColl = await collection();
  const getQuoteCluster = await initcluster();
  try {
    // logger.info('Winston log for quote');
    // console.log("In GET Quote",logPrefix);
    if(!getQuoteColl || !getQuoteCluster){
      // console.log(logPrefix,"Couchbase connection is missing : in Get Quote API : ",getQuoteColl,getQuoteCluster)
    }
    await pool.query("SELECT 1");
  } catch (e) {
    logError(e, "Error mysql connection in getQuote", xHeaderToken);
    return res.status(500).json({
      status: true,
      statusCode: "500",
      statusMsg: "mysql connection issue! - " + e.message,
    });
  }

  try {
    const {
      storeId,
      bagView,
      orderCreation,
      screenName,
      statusCoinApllied,
      retryPayment = false,
      donationAmount,
      appliedShukranPoint,
      isClubShipment = false,
      cityId
    } = body;
    const quoteId = String(body?.quoteId || "");
    const customerId = String(body?.customerId || "");
    let quote = {};
    const isVersion7 = body?.isVersion7 || false;
    const orderSplitFlag = getStoreConfig(storeId, "orderSplitFlag") || false;

    // console.log(logPrefix,` In GET Quote :: retry payment :${retryPayment} Key :: ${quoteId}`);
    let shukranAvailablePoint;
    let shukranAvailableCashValue;
    let shukranWelcomeBonous;
    let shukranQualifingPurchase;
    let tierName;
    let reqQualTxnForNxtTier;
    let retTierQualTxn;
    let tierNudgeFlag;
    let qualifyingTranxCount;
    let lastEvaluateDate;
    let tierExpiryDate;
    let shukranNumberOfQualifingPurchase;
    let isQualifyingPurchase = false;
    let action;
    let nextTier;
    if(retryPayment) {
      // console.log(logPrefix,` In to  retryPayment Quote ${retryPayment} Key :: ${quoteId}`);
      quote = await fetchQuote.fetchQuote({
        identifier: quoteId,
        storeId,
        collection:getQuoteColl,
        cluster:getQuoteCluster,
        type: "guest",
        res,
        retryPaymentReplica: true
      });
      // console.log(logPrefix,"In v6 get :: quote response in retryPayment",quoteId, quote?JSON.stringify(quote): "quote is null")
      if(quote && !quote.retryPayment) {
        // console.log(logPrefix,"In v6 get :: quote response in retryPayment and its not found");
        return res.status(200).json({
          status: true,
          statusCode: "200",
          statusMsg: "Retry payment is not allowed",
          response: {},
        });
      }
    } else if (!customerId) {
      quote = await fetchQuote.fetchQuote({
        identifier: quoteId,
        storeId,
        collection:getQuoteColl,
        cluster:getQuoteCluster,
        type: "guest",
        res,
      });
    } else {
      quote = await fetchQuote.fetchQuote({
        identifier: customerId,
        storeId,
        collection:getQuoteColl,
        cluster:getQuoteCluster,
        type: "customer",
        res,
      });
    }
    
    if (!quote) {
      return handleQuoteNotFound(res);
    }

    quote.discountData_v2 = quote?.discountData ? [...quote?.discountData] : [];


    const baseConfig = cache.get('baseConfig');
    let shukranEnabled = false;
    let shukranLinkFlag = false;
    let isShukranConnected = true;
    let customerDetails = {};
    let mobileNumber;
    let isCrossBorderFlag = "";
    let preferredPaymentMethod = quote?.preferred_payment || "";
    let lastPaymentMethodEnable = baseConfig?.lastPaymentMethodEnable;
    const isMacOsSafari = xOs.toLowerCase() === 'macos' && xBrowser.toLowerCase() === 'safari';
    const isIos = xSource.toLowerCase() === 'ios';
    let defaultPaymentMethod = (isIos || isMacOsSafari) ? 'apple_pay' : baseConfig?.defaultPaymentMethod;
    let exclusionListForPreferredPayment = Array.isArray(baseConfig?.exclusionListForPreferredPayment) ? baseConfig.exclusionListForPreferredPayment : [];
    let paymentSelectionFlagFromCustomer = quote?.paymentSelectionFlagFromCustomer || false;
    if(customerId){
      let shukranProfileId;
      try {
        //API-3791 - If `shukranProfileId` exists in the quote, use it. Otherwise, call the customer info API.
        if (quote?.shukranProfileId && paymentSelectionFlagFromCustomer) {
            shukranProfileId = quote?.shukranProfileId || '';
             shukranLinkFlag = quote?.shukranLinkFlag ?? false;           
             mobileNumber =  quote?.customerPhoneNumber || '';
            customerDetails.mobileNumber = mobileNumber;
            customerDetails.profileId = shukranProfileId;
            customerDetails.cardNumber = quote?.shukranCardNumber || '';
            if (lastPaymentMethodEnable){
              const isQuoteMethodValid = quote?.preferred_payment && !exclusionListForPreferredPayment.includes(quote.preferred_payment);
              preferredPaymentMethod = isQuoteMethodValid ? quote.preferred_payment : defaultPaymentMethod;
            } 
        } else {
            customerDetails = await getCustomerInfo(customerId);
            shukranProfileId = customerDetails?.profileId || '';
            shukranLinkFlag = customerDetails?.shukranLinkFlag ?? false;
            mobileNumber = customerDetails?.mobileNumber || '';
            if(lastPaymentMethodEnable && !paymentSelectionFlagFromCustomer){
              let customerPreferredMethod = "";
              if (Array.isArray(customerDetails?.preferredPaymentMethod)) {
                const storeSpecificMethod = customerDetails.preferredPaymentMethod.find(
                    (entry) => entry.storeId === Number(storeId)
                );
                if (storeSpecificMethod?.method && !exclusionListForPreferredPayment.includes(storeSpecificMethod.method)) {
                    customerPreferredMethod = storeSpecificMethod.method;
                    if (customerPreferredMethod === "md_payfort_cc_vault") {
                        customerPreferredMethod = "md_payfort";
                  }
                }
              }
             if (quote?.preferred_payment && !exclusionListForPreferredPayment.includes(quote.preferred_payment)) {
              preferredPaymentMethod = quote?.preferred_payment;
              } else if (!customerPreferredMethod) {
                preferredPaymentMethod = "";
              } else {
                preferredPaymentMethod = customerPreferredMethod;
              }
            quote.paymentSelectionFlagFromCustomer = true;
            quote.preferred_payment = preferredPaymentMethod;
          }
        }
        // Check if Shukran is enabled based on customer and store details
        shukranEnabled = checkShukranEnabled(customerId, storeId, shukranProfileId, shukranLinkFlag);
    } catch (error) {
        // logError(error, `Failed to retrieve Shukran profile information for customerId: ${customerId}`);
    }
    }    
    // logger.info(`Shukran Enabled: ${shukranEnabled}, Customer ID: ${customerId}`);


    quote.configShipmentCharges = Number(getStoreConfig(storeId, 'shipmentCharges') || 0);
    if(orderSplitFlag && isVersion7){
    quote.orderSplitFlag = orderSplitFlag;
    }

    // Recalculate COD charges for retry payment so v7/get reflects correct COD fees
    const enableAdvancedCodCharges = baseConfig?.enableAdvancedCodCharges ?? false;
    const paymentMethod = quote?.quotePayment?.method || '';
    if (retryPayment) {
      if (!enableAdvancedCodCharges || !isVersion7) {
        const legacyCodCharges = Number(getStoreConfig(storeId, 'codCharges') || 0);
        quote.codCharges = formatPrice(paymentMethod === "cashondelivery" ? legacyCodCharges : 0);
      } else {
        const codChargesResult = calculateCodCharges({
          quoteItems: quote.quoteItem || [],
          quote,
          storeId,
          paymentMethod
        });
        quote.codCharges = codChargesResult.totalCodCharges;
        quote.expressCodCharges = codChargesResult.expressCodCharges;
        quote.globalCodCharges = codChargesResult.globalCodCharges;
      }
    }

    // In customer oms details - customer mobileNumber should map
    quote.customerPhoneNumber = mobileNumber || '';
    tierName = 'classic';
    nextTier = 'silver'; // default next tier
    if (shukranEnabled && !retryPayment) { 
      customerDetails.shukranCurrencyCode = await getShukranCurrencyCode(storeId);
      const getProfile = await getShukranProfile({customerDetails});
      const userCountry = getStoreConfig(storeId, 'shukranCurrencyCode') || "";
      if (userCountry && getProfile?.Country && userCountry === getProfile.Country){
        isCrossBorderFlag = 'N';
      } else {
        isCrossBorderFlag = 'Y';
      }
      isShukranConnected = getProfile?.isShukranConnected ?? true;
      tierName = (getProfile?.TierName || 'classic').toLowerCase();
      quote.tier = tierName;
      shukranAvailablePoint = getProfile?.JsonExternalData?.AvailablePoints || 0;
      shukranAvailableCashValue = getProfile?.JsonExternalData?.AvailablePointsCashValue || 0;
      reqQualTxnForNxtTier =  getProfile?.JsonExternalData?.ReqQualTxnForNxtTier || "0";
      retTierQualTxn =  getProfile?.JsonExternalData?.RetTierQualTxn || "0";
      qualifyingTranxCount = getProfile?.QualifyingTranxCount || "";
      lastEvaluateDate = getProfile?.LastEvaluateDate || "";
      tierExpiryDate = getProfile?.TierExpiryDate || "";
      
      quote.shukranProfileId = getProfile?.ProfileId || '';
      quote.shukranCardNumber = getProfile?.CardNumber || '';
      quote.shukranLinkFlag = shukranLinkFlag ?? false;
     
      shukranNumberOfQualifingPurchase =  getAppConfigKey('shukranQualifingPurchase');
      shukranQualifingPurchase = shukranNumberOfQualifingPurchase?.[tierName] ?? 0;
      shukranWelcomeBonous =  Number(getStoreConfig(storeId, 'shukranWelcomeBonous') || 0);
      action = "maintain";
      tierNudgeFlag = getProfile?.JsonExternalData?.TIER_NUDGE_FLAG || "";
      if (tierNudgeFlag?.toUpperCase().includes("UPGRADE")) {
        action = "unlock";
      }
      //If tier name is classic always action - unlock
      if (tierName?.toUpperCase().includes("CLASSIC")) {
        action = "unlock";
      }
      //If tier name is platinum always action - maintain
      if (tierName?.toUpperCase().includes("PLATINUM")) {
        action = "maintain";
      }
      const shukranNextTier = getAppConfigKey('shukranNextTier');
      if (shukranNextTier && typeof shukranNextTier === 'object') {
        nextTier = shukranNextTier[tierName];
      }
      //If tier name is platinum always next tier - platinum
      if (tierName?.toUpperCase().includes("PLATINUM")) {
        nextTier = "platinum";
      }
        
      // console.log(`API-3761 - shukranAvailablePoint @@@ ${shukranAvailablePoint} shukranAvailableCashValue @@@ ${shukranAvailableCashValue}`);
    }

    if (!retryPayment) {
      // console.log(logPrefix,'retryPayment is false', quoteId);
      const previousOrderList = await getCustomerOrderList({
        quote,
        customerEmail: xHeaderToken,
        xHeaderToken,
      });
      quote.previousOrderList = previousOrderList;

      if (uuid && baseConfig?.uuid_validation) {
        // console.log(logPrefix,"uuid and base config block: ",quoteId)
        // logInfo('UUID present in JWT', 'UUID present in JWT', xHeaderToken);
        if (quote?.uuid && quote?.uuid != uuid)
          return res.status(400).json({
            status: false,
            statusCode: '400',
            statusMsg: 'JWT uuid absent/mismatch!',
          });
        else if (!quote?.uuid) {
          // logInfo(
          //   'UUID not present in quote',
          //   'saving UUID to quote',
          //   xHeaderToken
          // );
          quote.uuid = uuid;
          // console.log(logPrefix,"UUID not present in quote: ",quoteId)
          await upsertQuote({ storeId, quote, collection : getQuoteColl, xHeaderToken });
        }
      }
    }

    // handle retro quotes for insertion order API-2024
    if (!quote.itemsSequence && !retryPayment)
      quote = await handleRetroQuotesForItemsSequence({
        quote,
        storeId,
        collection:getQuoteColl,
        xHeaderToken,
      });

    // process bagview flag
    if (bagView == 1 && !retryPayment) {
      // console.log(logPrefix,` GetQuote ID bag view is 1 :: ${quoteId}`);
      const existingEmail = quote.customerEmail || xHeaderToken;
      quote.customerEmail =
        (existingEmail || "").toLowerCase() === "guest"
          ? "guest@stylishop.com"
          : existingEmail;
      if (quote.quotePayment) {
        // console.log(logPrefix,` GetQuote ID quotePayment is true ${quoteId}`);
        quote.quotePayment.method = null;
        quote.quotePayment.cardBin = "";
        if(!orderCreation) {
          quote.discountData = [];
        }
          
        quote.quotePayment.applyStoreCredit = null;
        quote.donationAmount = 0;
        quote.returnOrderCount = undefined;
      }
      quote.amstorecreditUse = false;
      calcQuoteDonation({
        quote,
        newDonation: donationAmount,
      });
    }
    let responseJson = {};
    try {
      // console.log(logPrefix, "Into quote processQuote ",quoteId);
      //API-3838 START
      const stopStyliCoinBurning = baseConfig?.stopStyliCoinBurning ?? false;
      //API-3838 END
      responseJson = await quoteProcessor.processQuote({
        storeId,
        quote,
        collection:getQuoteColl,
        orderCreation,
        xHeaderToken,
        token: headers.token,
        xSource,
        xClientVersion,
        pool,
        bagView,
        statusCoinApllied:stopStyliCoinBurning?0:statusCoinApllied,
        screenName,
        resetNotifs,
        retryPayment,
        shukranEnabled,
        appliedShukranPoint,
        shukranAvailablePoint,
        shukranAvailableCashValue,
        tierName,
        orderSplitFlag,
        isVersion7
      });
      // console.log(logPrefix, "Into quote processQuote response ",quoteId,responseJson?JSON.stringify(responseJson): "response JSON is Null");
    } catch (e) {
      // logError(e, "error in quote ");
      // logInfo("error in process quote", e.message);
    }    

    if (req?.body?.orderCreation && responseJson?.response && quote) {      
      responseJson.response["bnplSessionAmount"] = quote.bnplSessionAmount;
    }

    //API-3791 - Add profile nudge
    if (shukranEnabled && responseJson?.response && !retryPayment) {
      let shukranQP = 0, shukranPointConversion = 0;
      // Validate the shukranQP value
      const shukranQPValue = getStoreConfig(storeId, 'shukranQP');
      if (shukranQPValue !== null && shukranQPValue !== undefined && !isNaN(Number(shukranQPValue))) {
        shukranQP = Number(shukranQPValue);
      }
      const shukranPointConversionValue = getStoreConfig(storeId, 'shukranPointConversion');
      if (shukranPointConversionValue !== null && shukranPointConversionValue !== undefined && !isNaN(Number(shukranPointConversionValue))) {
        shukranPointConversion = Number(shukranPointConversionValue);
      }
      const paymentObject = quote?.quotePayment || {};
      const isCashOnDelivery = paymentObject?.method === "cashondelivery";
      const codCharges = quote?.codCharges || 0;
      const styliCoinDiscount = quote?.coinDiscountData?.isCoinApplied ? quote?.coinDiscountData?.storeCoinValue || 0 : 0;
      const totalPrice = findQuoteCartValue(quote) - styliCoinDiscount + (isCashOnDelivery ? codCharges : 0);
      isQualifyingPurchase = totalPrice >= shukranQP;
      quote.isQualifyingPurchase = isQualifyingPurchase;
      quote.shukranWelcomeBonous = shukranWelcomeBonous;
      quote.shukranPointConversion = shukranPointConversion;
      quote.shukranQualifingPurchase = shukranQualifingPurchase;
      quote.tierNudgeFlag = tierNudgeFlag;
      quote.tierName = tierName;
      quote.reqQualTxnForNxtTier = reqQualTxnForNxtTier;
      quote.retTierQualTxn = retTierQualTxn;
      quote.qualifyingTranxCount = qualifyingTranxCount;
      quote.lastEvaluateDate = lastEvaluateDate;
      quote.tierExpiryDate = tierExpiryDate;
      quote.shukranNumberOfQualifingPurchase = shukranNumberOfQualifingPurchase;
      quote.action = action;
      quote.nextTier = nextTier;
    }
    
    if(responseJson && responseJson.response) {
      responseJson.response["shukranLinkFlag"] = quote?.shukranLinkFlag ?? false; 
      responseJson.response["isShukranEnable"] = getStoreConfig(storeId, 'isShukranEnable') || false;
      responseJson.response["isShukranConnected"] = isShukranConnected;
      responseJson.response["isCrossBorderFlag"] =  isCrossBorderFlag || "N";
      if (shukranEnabled) {
        responseJson.response["shukranWelcomeBonous"] = quote?.shukranWelcomeBonous || 0;
        responseJson.response["shukranQualifingPurchase"] = quote?.shukranQualifingPurchase || 0;
        responseJson.response["tierNudgeFlag"] = quote?.tierNudgeFlag || "";
        responseJson.response["tierName"] = quote?.tierName || tierName;
        responseJson.response["reqQualTxnForNxtTier"] = quote?.reqQualTxnForNxtTier || "0";
        responseJson.response["retTierQualTxn"] = quote?.retTierQualTxn || "0";
        responseJson.response["qualifyingTranxCount"] = quote?.qualifyingTranxCount || "0";
        responseJson.response["lastEvaluateDate"] = quote?.lastEvaluateDate || "";
        responseJson.response["tierExpiryDate"] = quote?.tierExpiryDate || "";
        responseJson.response["shukranNumberOfQualifingPurchase"] = quote?.shukranNumberOfQualifingPurchase || {};
        responseJson.response["isQualifyingPurchase"] = quote?.isQualifyingPurchase || false;
        if(!retryPayment){
          responseJson.response["shukranPointConversion"] = quote?.shukranPointConversion || 0;
        }
        responseJson.response["action"] = quote?.action || action;
        responseJson.response["nextTier"] = quote?.nextTier || nextTier;
      }
        responseJson.response['lastPaymentMethodEnable'] = baseConfig?.lastPaymentMethodEnable;
        responseJson.response['defaultPaymentMethod'] = defaultPaymentMethod ;
        responseJson.response["preferred_payment"] = preferredPaymentMethod ;
        const websiteCode = getStoreConfig(storeId, 'websiteCode');
        const giftConfig = getBaseConfig('giftProducts');
        const countryConfig = giftConfig?.[websiteCode] || {};
        const { enable_gift_with_purchase_module_feature, eligible_products_context_id } = countryConfig || {};
        
        responseJson.response["enableGWPContextFeature"] = enable_gift_with_purchase_module_feature || false;
        responseJson.response["enableGWPContextId"] = enable_gift_with_purchase_module_feature ? eligible_products_context_id : null;
      let hold = getStoreConfig(storeId,'holdOrder');
      if(hold === '') {
        hold = false;
      }
      responseJson.response["holdOrder"] = hold;
      bnblCalculation({baseConfig, responseObject: responseJson?.response, quote, retryPayment});
    }

    if(req?.body?.orderCreation && responseJson?.response && quote){
      responseJson.response["freeOrder"] = quote.quotePayment.freeOrder;
      responseJson.response["failedPaymentMethod"] = quote.failedPaymentMethod;
    }    

    if (bagView == 1) {
      // console.log(logPrefix, "Into quote bagView is 1 ",quoteId)
      paymentService.bnplBagCall({
        quote,
        collection:getQuoteColl,
        xHeaderToken,
        responseObject: responseJson?.response,
      });
      // console.log(logPrefix, "Into quote bagView is 1 end ",quoteId)
    }
    let skuList = [];
    for (const item of quote.quoteItem) {
      skuList.push(item.sku);
    }
    if(skuList && skuList.length > 0){
      quote.skus = skuList;
      const websiteId = Number(
        getStoreConfig(storeId, 'websiteId') || 0
      );
        let webSiteArray = []
        webSiteArray.push(websiteId);
        quote.websiteId = webSiteArray;
    }
    try{
      // delete fromMigrate flag before upsert
      if(quote.fromMigrate) {
        delete quote.fromMigrate;
      }
      // console.log(logPrefix, "Into quote upsertQuote In ",quoteId)
    await upsertQuote({ storeId, quote, collection :getQuoteColl, xHeaderToken });
    // console.log(logPrefix, "Into quote upsertQuote after update ",quoteId)
    }catch(e){
      // console.log(logPrefix, "Into quote upsertQuote error ",quoteId)
    }

    try {
      if (
        customerId && (!responseJson?.response?.shippingAddress?.countryId || responseJson?.response?.shippingAddress?.countryId.trim() === "")) {      
        // console.log(`defaultaddress : Fetching default address for customerId: ${customerId}`);
        const fetchedAddress = await getCustomerDefaultAddress(customerId);
        // console.log(`defaultaddress : Fetched address for customerId ${customerId}:`, JSON.stringify(fetchedAddress));
    
        if (fetchedAddress) {
          if (!responseJson.response) {
            responseJson.response = {};
          }
        
          // Preserve existing cityMapper before overwriting shippingAddress to avoid memory issues
          const existingCityMapper = responseJson?.response?.shippingAddress?.cityMapper;
        
          responseJson.response["shippingAddress"] = {
            addressType: "shipping",
            area: fetchedAddress?.area,
            buildingNumber: fetchedAddress?.buildingNumber,
            city: fetchedAddress?.city,
            countryId: fetchedAddress?.country,
            customerAddressId: fetchedAddress?.addressId?.toString(),
            defaultAddress: fetchedAddress?.defaultAddress ?? true,
            email: fetchedAddress?.email ?? "",
            firstname: fetchedAddress?.firstName,
            formattedAddress: fetchedAddress?.formattedAddress ?? "",
            lastname: fetchedAddress?.lastName,
            latitude: fetchedAddress?.latitude,
            locationType: "",
            longitude: fetchedAddress?.longitude,
            mobileNumber: fetchedAddress?.mobileNumber,
            nationalId: fetchedAddress?.nationalId ?? null,
            nearestLandmark: fetchedAddress?.landMark ?? "",
            postcode: fetchedAddress?.postCode ?? "",
            region: fetchedAddress?.region,
            regionId: fetchedAddress?.regionId?.toString(),
            shippingDescription: "",
            shippingMethod: "",
            street: `${fetchedAddress?.streetAddress ?? ""} ${fetchedAddress?.buildingNumber ?? ""}`.trim(),
            telephone: fetchedAddress?.telephone ?? fetchedAddress?.mobileNumber,
            cityMapper: existingCityMapper,
            unitNumber: fetchedAddress?.unitNumber || "",
            postalCode: fetchedAddress?.postalCode || "",
            shortAddress: fetchedAddress?.shortAddress || ""
          };
          
          // console.log(`defaultaddress : Shipping address set for customerId ${customerId}`);
        } else {
          // console.log(`defaultaddress : No default address found for customerId ${customerId}`);
        }
      } else {
        // console.log(`defaultaddress : customerId is not provided, skipping address fetch.`);
      }
    } catch (e) {
      // console.log(`defaultaddress : Error while setting shippingAddress for customerId: ${customerId}`, e);
    }

    if(orderSplitFlag && isVersion7 && responseJson?.response?.products?.length > 0){
      const orderSplitResponse = await processOrderSplitLogic({
        productsArr: responseJson?.response?.products,
        quote,
        responseObject: responseJson?.response,
        orderSplitFlag,
        isClubShipment,
        storeId,
        xHeaderToken,
        cityId : isVersion7 ? (responseJson?.response?.shippingAddress?.cityMapper?.id) || cityId: cityId,
        countryId : isVersion7 ? responseJson?.response?.shippingAddress?.countryId : "",
        childSkus: responseJson?.response?.products?.map(product => product.sku),
        getStoreConfig,
        getProductSLADetails,
        calculateOrderSplit,
        logInfo,
        formatPrice,
        grandTotal: responseJson?.response?.grandTotal,
        isVersion7,
        orderCreation
      });
      responseJson.response.isClubShipment = isClubShipment;
      responseJson.response = orderSplitResponse;
    }
      
    if (responseJson == null) {
      // console.log(logPrefix, "Into quote responseJson is null ",quoteId)
      return res.status(500).json({
        status: true,
        statusCode: "500",
        statusMsg: "Something went wrong!",
      });
    } else {
      // console.log(logPrefix, "Get Quote success :::",quoteId);
      if(isVersion7){
        return responseJson
      }else{
        return res.status(200).json(responseJson);
      }
    }
  } catch (e) {
      // logError(e, "get quote issue", xHeaderToken);
    return res.status(500).json({
      status: false,
      statusCode: "500",
      statusMsg: e.message,
    });
  }
};

exports.getQuoteOptimized = async function ({ req, res, pool, resetNotifs = true }) {
  const { uuid, body } = req;
  const logPrefix = `getQuote:::${new Date().toISOString()}`;
  const { headers } = req;
  const xHeaderToken = headers["x-header-token"] || "";
  const xSource = headers["x-source"] || headers["X-Source"] || "";
  const xOs = headers["x-os"] || "";
  const xBrowser = headers["x-browser"] || "";
  const xClientVersion = headers["x-client-version"] || "";

  const promiseArray = [];
  promiseArray.push(pool.query("SELECT 1"));
  promiseArray.push(collection());
  promiseArray.push(initcluster());
  const [poolResult, getQuoteColl, getQuoteCluster] = await Promise.all(promiseArray);
  if(!poolResult || !getQuoteColl || !getQuoteCluster){
    if(!poolResult){
      // logError(poolResult, "Error mysql connection in getQuote", xHeaderToken);
      return res.status(500).json({
        status: true,
        statusCode: "500",
        statusMsg: "mysql connection issue! - " + poolResult.message,
      });
    }
    // console.log(logPrefix,"Couchbase connection is missing : in Get Quote API : ")
  }
  
  try {
    const {
      storeId,
      bagView,
      orderCreation,
      screenName,
      statusCoinApllied,
      retryPayment = false,
      donationAmount,
      appliedShukranPoint,
      isClubShipment = false,
      cityId
    } = body;
    const quoteId = String(body?.quoteId || "");
    const customerId = String(body?.customerId || "");
    let quote = {};
    const isVersion7 = body?.isVersion7 || false;
    const orderSplitFlag = getStoreConfig(storeId, "orderSplitFlag") || false;

    // logger.info(`${logPrefix} In GET Quote :: retry payment :${retryPayment} Key :: ${quoteId}`);
    let shukranAvailablePoint;
    let shukranAvailableCashValue;
    let shukranWelcomeBonous;
    let shukranQualifingPurchase;
    let tierName;
    let reqQualTxnForNxtTier;
    let retTierQualTxn;
    let tierNudgeFlag;
    let qualifyingTranxCount;
    let lastEvaluateDate;
    let tierExpiryDate;
    let shukranNumberOfQualifingPurchase;
    let isQualifyingPurchase = false;
    let action;
    let nextTier;
    if(retryPayment) {
      // console.log(logPrefix,` In to  retryPayment Quote ${retryPayment} Key :: ${quoteId}`);
      quote = await fetchQuote.fetchQuote({
        identifier: quoteId,
        storeId,
        collection:getQuoteColl,
        cluster:getQuoteCluster,
        type: "guest",
        res,
        retryPaymentReplica: true
      });
      // console.log(logPrefix,"In v6 get :: quote response in retryPayment",quoteId, quote?JSON.stringify(quote): "quote is null")
      if(quote && !quote.retryPayment) {
        // console.log(logPrefix,"In v6 get :: quote response in retryPayment and its not found");
        return res.status(200).json({
          status: true,
          statusCode: "200",
          statusMsg: "Retry payment is not allowed",
          response: {},
        });
      }
    } else if (!customerId) {
      quote = await fetchQuote.fetchQuote({
        identifier: quoteId,
        storeId,
        collection:getQuoteColl,
        cluster:getQuoteCluster,
        type: "guest",
        res,
      });
    } else {
      quote = await fetchQuote.fetchQuote({
        identifier: customerId,
        storeId,
        collection:getQuoteColl,
        cluster:getQuoteCluster,
        type: "customer",
        res,
      });
    }
    
    if (!quote) {
      return handleQuoteNotFound(res);
    }

    quote.discountData_v2 = quote?.discountData ? [...quote?.discountData] : [];

    const baseConfig = cache.get('baseConfig');
    let shukranEnabled = false;
    let shukranLinkFlag = false;
    let isShukranConnected = true;
    let customerDetails = {};
    let mobileNumber;
    let isCrossBorderFlag = "";
    let preferredPaymentMethod = quote?.preferred_payment || "";
    let lastPaymentMethodEnable = baseConfig?.lastPaymentMethodEnable;
    const isMacOsSafari = xOs.toLowerCase() === 'macos' && xBrowser.toLowerCase() === 'safari';
    const isIos = xSource.toLowerCase() === 'ios';
    let defaultPaymentMethod = (isIos || isMacOsSafari) ? 'apple_pay' : baseConfig?.defaultPaymentMethod;
    let exclusionListForPreferredPayment = Array.isArray(baseConfig?.exclusionListForPreferredPayment) ? baseConfig.exclusionListForPreferredPayment : [];
    let paymentSelectionFlagFromCustomer = quote?.paymentSelectionFlagFromCustomer || false;
    if(customerId){
      let shukranProfileId;
      try {
        //API-3791 - If `shukranProfileId` exists in the quote, use it. Otherwise, call the customer info API.
        if (quote?.shukranProfileId && paymentSelectionFlagFromCustomer) {
          shukranProfileId = quote?.shukranProfileId || '';
          shukranLinkFlag = quote?.shukranLinkFlag ?? false;           
          mobileNumber =  quote?.customerPhoneNumber || '';
          customerDetails.mobileNumber = mobileNumber;
          customerDetails.profileId = shukranProfileId;
          customerDetails.cardNumber = quote?.shukranCardNumber || '';
          if (lastPaymentMethodEnable){
            const isQuoteMethodValid = quote?.preferred_payment && !exclusionListForPreferredPayment.includes(quote.preferred_payment);
            preferredPaymentMethod = isQuoteMethodValid ? quote.preferred_payment : defaultPaymentMethod;
          }
        } else {
          customerDetails = await getCustomerInfo(customerId);
          shukranProfileId = customerDetails?.profileId || '';
          shukranLinkFlag = customerDetails?.shukranLinkFlag ?? false;
          mobileNumber = customerDetails?.mobileNumber || '';
          quote.customerCreatedAt = customerDetails?.createdAt || '';
          if(lastPaymentMethodEnable && !paymentSelectionFlagFromCustomer){
            let customerPreferredMethod = "";
            if (Array.isArray(customerDetails?.preferredPaymentMethod)) {
              const storeSpecificMethod = customerDetails.preferredPaymentMethod.find(
                  (entry) => entry.storeId === Number(storeId)
              );
              if (storeSpecificMethod?.method && !exclusionListForPreferredPayment.includes(storeSpecificMethod.method)) {
                  customerPreferredMethod = storeSpecificMethod.method;
                  if (customerPreferredMethod === "md_payfort_cc_vault") {
                      customerPreferredMethod = "md_payfort";
                }
              }
            }
            if (quote?.preferred_payment && !exclusionListForPreferredPayment.includes(quote.preferred_payment)) {
              preferredPaymentMethod = quote?.preferred_payment;
            } else if (!customerPreferredMethod) {
              preferredPaymentMethod = "";
            } else {
              preferredPaymentMethod = customerPreferredMethod;
            }
            quote.paymentSelectionFlagFromCustomer = true;
            quote.preferred_payment = preferredPaymentMethod;
          }
        }
        // Check if Shukran is enabled based on customer and store details
        shukranEnabled = checkShukranEnabled(customerId, storeId, shukranProfileId, shukranLinkFlag);
      } catch (error) {
        logError(error, `Failed to retrieve Shukran profile information for customerId: ${customerId}`);
      }
    }    
    // logger.info(`Shukran Enabled: ${shukranEnabled}, Customer ID: ${customerId}`);

    quote.configShipmentCharges = Number(getStoreConfig(storeId, 'shipmentCharges') || 0);
    if(orderSplitFlag && isVersion7){
      quote.orderSplitFlag = orderSplitFlag;
    }

       // Recalculate COD charges for retry payment so v7/get reflects correct COD fees
       const enableAdvancedCodCharges = baseConfig?.enableAdvancedCodCharges ?? false;
       const paymentMethod = quote?.quotePayment?.method || '';
       if (retryPayment) {
         if (!enableAdvancedCodCharges || !isVersion7) {
           const legacyCodCharges = Number(getStoreConfig(storeId, 'codCharges') || 0);
           quote.codCharges = formatPrice(paymentMethod === "cashondelivery" ? legacyCodCharges : 0);
         } else {
           const codChargesResult = calculateCodCharges({
             quoteItems: quote.quoteItem || [],
             quote,
             storeId,
             paymentMethod
           });
           quote.codCharges = codChargesResult.totalCodCharges;
           quote.expressCodCharges = codChargesResult.expressCodCharges;
           quote.globalCodCharges = codChargesResult.globalCodCharges;
         }
       }
 
    // In customer oms details - customer mobileNumber should map
    quote.customerPhoneNumber = mobileNumber || '';
    tierName = 'classic';
    nextTier = 'silver'; // default next tier
    if (shukranEnabled && !retryPayment) { 
      customerDetails.shukranCurrencyCode = await getShukranCurrencyCode(storeId);
      const getProfile = await getShukranProfile({customerDetails});
      const userCountry = getStoreConfig(storeId, 'shukranCurrencyCode') || "";
      if (userCountry && getProfile?.Country && userCountry === getProfile.Country){
        isCrossBorderFlag = 'N';
      } else {
        isCrossBorderFlag = 'Y';
      }
      isShukranConnected = getProfile?.isShukranConnected ?? true;
      tierName = (getProfile?.TierName || 'classic').toLowerCase();
      quote.tier = tierName;
      shukranAvailablePoint = getProfile?.JsonExternalData?.AvailablePoints || 0;
      shukranAvailableCashValue = getProfile?.JsonExternalData?.AvailablePointsCashValue || 0;
      reqQualTxnForNxtTier =  getProfile?.JsonExternalData?.ReqQualTxnForNxtTier || "0";
      retTierQualTxn =  getProfile?.JsonExternalData?.RetTierQualTxn || "0";
      qualifyingTranxCount = getProfile?.QualifyingTranxCount || "";
      lastEvaluateDate = getProfile?.LastEvaluateDate || "";
      tierExpiryDate = getProfile?.TierExpiryDate || "";
      
      quote.shukranProfileId = getProfile?.ProfileId || '';
      quote.shukranCardNumber = getProfile?.CardNumber || '';
      quote.shukranLinkFlag = shukranLinkFlag ?? false;
     
      shukranNumberOfQualifingPurchase =  getAppConfigKey('shukranQualifingPurchase');
      shukranQualifingPurchase = shukranNumberOfQualifingPurchase?.[tierName] ?? 0;
      shukranWelcomeBonous =  Number(getStoreConfig(storeId, 'shukranWelcomeBonous') || 0);
      action = "maintain";
      tierNudgeFlag = getProfile?.JsonExternalData?.TIER_NUDGE_FLAG || "";
      if (tierNudgeFlag?.toUpperCase().includes("UPGRADE")) {
        action = "unlock";
      }
      //If tier name is classic always action - unlock
      if (tierName?.toUpperCase().includes("CLASSIC")) {
        action = "unlock";
      }
      //If tier name is platinum always action - maintain
      if (tierName?.toUpperCase().includes("PLATINUM")) {
        action = "maintain";
      }
      const shukranNextTier = getAppConfigKey('shukranNextTier');
      if (shukranNextTier && typeof shukranNextTier === 'object') {
        nextTier = shukranNextTier[tierName];
      }
      //If tier name is platinum always next tier - platinum
      if (tierName?.toUpperCase().includes("PLATINUM")) {
        nextTier = "platinum";
      }
        
      // console.log(`API-3761 - shukranAvailablePoint @@@ ${shukranAvailablePoint} shukranAvailableCashValue @@@ ${shukranAvailableCashValue}`);
    }

    if (!retryPayment) {
      // logger.info(`${logPrefix} retryPayment is false ${quoteId}`);
      // Run getCustomerOrderList and handleRetroQuotesForItemsSequence in parallel
      const promises = [
        getCustomerOrderList({
          quote,
          customerEmail: xHeaderToken,
          xHeaderToken,
          optimized: true
        })
      ];
      
      if (!quote.itemsSequence) {
        promises.push(
          handleRetroQuotesForItemsSequence({
            quote,
            storeId,
            collection:getQuoteColl,
            xHeaderToken,
            optimized: true
          })
        );
      }
      
      const results = await Promise.all(promises);
      const previousOrderList = results[0];
      
      // Update quote with results
      if (!quote.itemsSequence && results[1]) {
        quote = results[1];
      }
      quote.previousOrderList = previousOrderList;

      if (uuid && baseConfig?.uuid_validation) {
        // console.log(logPrefix,"uuid and base config block: ",quoteId)
        // logInfo('UUID present in JWT', 'UUID present in JWT', xHeaderToken);
        if (quote?.uuid && quote?.uuid != uuid)
          return res.status(400).json({
            status: false,
            statusCode: '400',
            statusMsg: 'JWT uuid absent/mismatch!',
          });
        else if (!quote?.uuid) {
          // logInfo(
          //   'UUID not present in quote',
          //   'saving UUID to quote',
          //   xHeaderToken
          // );
          quote.uuid = uuid;
          // console.log(logPrefix,"UUID not present in quote: ",quoteId)
        }
      }

      // process bagview flag
      if (bagView == 1) {
        // logger.info(`${logPrefix} GetQuote ID bag view is 1 :: ${quoteId}`);
        const existingEmail = quote.customerEmail || xHeaderToken;
        quote.customerEmail =
          (existingEmail || "").toLowerCase() === "guest"
            ? "guest@stylishop.com"
            : existingEmail;
        if (quote.quotePayment) {
          // logger.info(`${logPrefix} GetQuote ID quotePayment is true ${quoteId}`);
          quote.quotePayment.method = null;
          quote.quotePayment.cardBin = "";
          if(!orderCreation) {
            quote.discountData = [];
          }
            
          quote.quotePayment.applyStoreCredit = null;
          quote.donationAmount = 0;
          quote.returnOrderCount = undefined;
        }
        quote.amstorecreditUse = false;
        calcQuoteDonation({
          quote,
          newDonation: donationAmount,
        });
      }
    }

    let responseJson = {};
    try {
      // logger.info(`${logPrefix} Into quote processQuote ${quoteId}`);
      //API-3838 START
      const stopStyliCoinBurning = baseConfig?.stopStyliCoinBurning ?? false;
      //API-3838 END
      
      responseJson = await quoteProcessor.processQuoteOptimized({
        storeId,
        quote,
        collection:getQuoteColl,
        orderCreation,
        xHeaderToken,
        token: headers.token,
        xSource,
        xClientVersion,
        pool,
        bagView,
        statusCoinApllied:stopStyliCoinBurning?0:statusCoinApllied,
        screenName,
        resetNotifs,
        retryPayment,
        shukranEnabled,
        appliedShukranPoint,
        shukranAvailablePoint,
        shukranAvailableCashValue,
        tierName,
        orderSplitFlag,
        isVersion7
      });
      
      // logger.info(`${logPrefix} Into quote processQuote response ${quoteId} ${responseJson ? JSON.stringify(responseJson) : "response JSON is Null"}`);
    } catch (e) {
      // logger.error(`${logPrefix} error in quote ${e.message}`);
    }    

    if (req?.body?.orderCreation && responseJson?.response && quote) {      
      responseJson.response["bnplSessionAmount"] = quote.bnplSessionAmount;
    }

    //API-3791 - Add profile nudge
    if (shukranEnabled && responseJson?.response && !retryPayment) {
      let shukranQP = 0, shukranPointConversion = 0;
      // Validate the shukranQP value
      const shukranQPValue = getStoreConfig(storeId, 'shukranQP');
      if (shukranQPValue !== null && shukranQPValue !== undefined && !isNaN(Number(shukranQPValue))) {
        shukranQP = Number(shukranQPValue);
      }
      const shukranPointConversionValue = getStoreConfig(storeId, 'shukranPointConversion');
      if (shukranPointConversionValue !== null && shukranPointConversionValue !== undefined && !isNaN(Number(shukranPointConversionValue))) {
        shukranPointConversion = Number(shukranPointConversionValue);
      }
      const paymentObject = quote?.quotePayment || {};
      const isCashOnDelivery = paymentObject?.method === "cashondelivery";
      const codCharges = quote?.codCharges || 0;
      const styliCoinDiscount = quote?.coinDiscountData?.isCoinApplied ? quote?.coinDiscountData?.storeCoinValue || 0 : 0;
      const totalPrice = findQuoteCartValue(quote) - styliCoinDiscount + (isCashOnDelivery ? codCharges : 0);
      isQualifyingPurchase = totalPrice >= shukranQP;
      quote.isQualifyingPurchase = isQualifyingPurchase;
      quote.shukranWelcomeBonous = shukranWelcomeBonous;
      quote.shukranPointConversion = shukranPointConversion;
      quote.shukranQualifingPurchase = shukranQualifingPurchase;
      quote.tierNudgeFlag = tierNudgeFlag;
      quote.tierName = tierName;
      quote.reqQualTxnForNxtTier = reqQualTxnForNxtTier;
      quote.retTierQualTxn = retTierQualTxn;
      quote.qualifyingTranxCount = qualifyingTranxCount;
      quote.lastEvaluateDate = lastEvaluateDate;
      quote.tierExpiryDate = tierExpiryDate;
      quote.shukranNumberOfQualifingPurchase = shukranNumberOfQualifingPurchase;
      quote.action = action;
      quote.nextTier = nextTier;
    }
    
    if(responseJson && responseJson.response) {
      responseJson.response["shukranLinkFlag"] = quote?.shukranLinkFlag ?? false; 
      responseJson.response["isShukranEnable"] = getStoreConfig(storeId, 'isShukranEnable') || false;
      responseJson.response["isShukranConnected"] = isShukranConnected;
      responseJson.response["isCrossBorderFlag"] =  isCrossBorderFlag || "N";
      if (shukranEnabled) {
        responseJson.response["shukranWelcomeBonous"] = quote?.shukranWelcomeBonous || 0;
        responseJson.response["shukranQualifingPurchase"] = quote?.shukranQualifingPurchase || 0;
        responseJson.response["tierNudgeFlag"] = quote?.tierNudgeFlag || "";
        responseJson.response["tierName"] = quote?.tierName || tierName;
        responseJson.response["reqQualTxnForNxtTier"] = quote?.reqQualTxnForNxtTier || "0";
        responseJson.response["retTierQualTxn"] = quote?.retTierQualTxn || "0";
        responseJson.response["qualifyingTranxCount"] = quote?.qualifyingTranxCount || "0";
        responseJson.response["lastEvaluateDate"] = quote?.lastEvaluateDate || "";
        responseJson.response["tierExpiryDate"] = quote?.tierExpiryDate || "";
        responseJson.response["shukranNumberOfQualifingPurchase"] = quote?.shukranNumberOfQualifingPurchase || {};
        responseJson.response["isQualifyingPurchase"] = quote?.isQualifyingPurchase || false;
        if(!retryPayment){
          responseJson.response["shukranPointConversion"] = quote?.shukranPointConversion || 0;
        }
        responseJson.response["action"] = quote?.action || action;
        responseJson.response["nextTier"] = quote?.nextTier || nextTier;
      }
      responseJson.response['lastPaymentMethodEnable'] = baseConfig?.lastPaymentMethodEnable;
      responseJson.response['defaultPaymentMethod'] = defaultPaymentMethod ;
      responseJson.response["preferred_payment"] = preferredPaymentMethod ;
      const websiteCode = getStoreConfig(storeId, 'websiteCode');
      const giftConfig = getBaseConfig('giftProducts');
      const countryConfig = giftConfig?.[websiteCode] || {};
      const { enable_gift_with_purchase_module_feature, eligible_products_context_id } = countryConfig || {};
      
      responseJson.response["enableGWPContextFeature"] = enable_gift_with_purchase_module_feature || false;
      responseJson.response["enableGWPContextId"] = enable_gift_with_purchase_module_feature ? eligible_products_context_id : null;
      let hold = getStoreConfig(storeId,'holdOrder');
      if(hold === '') {
        hold = false;
      }
      responseJson.response["holdOrder"] = hold;
      bnblCalculation({baseConfig, responseObject: responseJson?.response, quote, retryPayment});
    }

    if(req?.body?.orderCreation && responseJson?.response && quote){
      responseJson.response["freeOrder"] = quote.quotePayment.freeOrder;
      responseJson.response["failedPaymentMethod"] = quote.failedPaymentMethod;
    }    

    if (bagView == 1) {
      // logger.info(`${logPrefix} Into quote bagView is 1 ${quoteId}`);
      paymentService.bnplBagCall({
        quote,
        collection:getQuoteColl,
        xHeaderToken,
        responseObject: responseJson?.response,
      });
      // logger.info(`${logPrefix} Into quote bagView is 1 end ${quoteId}`);
    }
    let skuList = [];
    for (const item of quote.quoteItem) {
      skuList.push(item.sku);
    }
    if(skuList && skuList.length > 0){
      quote.skus = skuList;
      const websiteId = Number(
        getStoreConfig(storeId, 'websiteId') || 0
      );
        let webSiteArray = []
        webSiteArray.push(websiteId);
        quote.websiteId = webSiteArray;
    }

    try {
      if (
        customerId && (!responseJson?.response?.shippingAddress?.countryId || responseJson?.response?.shippingAddress?.countryId.trim() === "")) {      
        // logger.info(`${logPrefix} defaultaddress : Fetching default address for customerId: ${customerId}`);        
        const fetchedAddress = await getCustomerDefaultAddress(customerId);
        // logger.info(`${logPrefix} defaultaddress : Fetched address for customerId ${customerId}: ${JSON.stringify(fetchedAddress)}`);
    
        if (fetchedAddress) {
          if (!responseJson.response) {
            responseJson.response = {};
          }
        
          // Preserve existing cityMapper before overwriting shippingAddress to avoid memory issues
          const existingCityMapper = responseJson?.response?.shippingAddress?.cityMapper;
        
          responseJson.response["shippingAddress"] = {
            addressType: "shipping",
            area: fetchedAddress?.area,
            buildingNumber: fetchedAddress?.buildingNumber,
            city: fetchedAddress?.city,
            countryId: fetchedAddress?.country,
            customerAddressId: fetchedAddress?.addressId?.toString(),
            defaultAddress: fetchedAddress?.defaultAddress ?? true,
            email: fetchedAddress?.email ?? "",
            firstname: fetchedAddress?.firstName,
            formattedAddress: fetchedAddress?.formattedAddress ?? "",
            lastname: fetchedAddress?.lastName,
            latitude: fetchedAddress?.latitude,
            locationType: "",
            longitude: fetchedAddress?.longitude,
            mobileNumber: fetchedAddress?.mobileNumber,
            nationalId: fetchedAddress?.nationalId ?? null,
            nearestLandmark: fetchedAddress?.landMark ?? "",
            postcode: fetchedAddress?.postCode ?? "",
            region: fetchedAddress?.region,
            regionId: fetchedAddress?.regionId?.toString(),
            shippingDescription: "",
            shippingMethod: "",
            street: `${fetchedAddress?.streetAddress ?? ""} ${fetchedAddress?.buildingNumber ?? ""}`.trim(),
            telephone: fetchedAddress?.telephone ?? fetchedAddress?.mobileNumber,
            cityMapper: existingCityMapper,
            unitNumber: fetchedAddress?.unitNumber || "",
            postalCode: fetchedAddress?.postalCode || "",
            shortAddress: fetchedAddress?.shortAddress || ""
          };
          quote.quoteAddress = [responseJson.response.shippingAddress];
          
          // logger.info(`${logPrefix} defaultaddress : Shipping address set for customerId ${customerId}`);
        } else {
          // logger.info(`${logPrefix} defaultaddress : No default address found for customerId ${customerId}`);
        }
      } else {
        // logger.info(`${logPrefix} defaultaddress : customerId is not provided, skipping address fetch.`);
      }
    } catch (e) {
      // logger.error(`${logPrefix} defaultaddress : Error while setting shippingAddress for customerId: ${customerId} ${e.message}`);
    }

    try{
      // delete fromMigrate flag before upsert
      if(quote.fromMigrate) {
        delete quote.fromMigrate;
      }
      // logger.info(`${logPrefix} Into quote upsertQuote In ${quoteId}`);
      await upsertQuote({ storeId, quote, collection :getQuoteColl, xHeaderToken });
      
      // logger.info(`${logPrefix} Into quote upsertQuote after update ${quoteId}`);
    }catch(e){
      // logger.error(`${logPrefix} Into quote upsertQuote error ${quoteId} ${e.message}`);
    }

    if(orderSplitFlag && isVersion7 && responseJson?.response?.products?.length > 0){
      const orderSplitResponse = await processOrderSplitLogic({
        productsArr: responseJson?.response?.products,
        quote,
        responseObject: responseJson?.response,
        orderSplitFlag,
        isClubShipment,
        storeId,
        xHeaderToken,
        cityId : isVersion7 ? (responseJson?.response?.shippingAddress?.cityMapper?.id) || cityId: cityId,
        countryId : isVersion7 ? responseJson?.response?.shippingAddress?.countryId : "",
        childSkus: responseJson?.response?.products?.map(product => product.sku),
        getStoreConfig,
        getProductSLADetails,
        calculateOrderSplit,
        logInfo,
        formatPrice,
        grandTotal: responseJson?.response?.grandTotal,
        isVersion7,
        orderCreation
      });
      
      responseJson.response.isClubShipment = isClubShipment;
      responseJson.response = orderSplitResponse;
    }

    // const newLogPrefix = `GWP missing debug log:::`;
    // logger.info(`${newLogPrefix} Quote ID: ${quoteId} Request Body: ${JSON.stringify(req.body)}`);
    
    // GWP Debug Log - Gift With Purchase details
    // const gwpWebsiteCode = getStoreConfig(storeId, 'websiteCode');
    // const gwpGiftConfig = getBaseConfig('giftProducts');
    // const gwpCountryConfig = gwpGiftConfig?.[gwpWebsiteCode] || {};
    // const gwpDebugInfo = {
    //   quoteId,
    //   containsGift: quote?.quoteItem?.some(item => item.isGift) || false,
    //   giftProductDetails: quote?.quoteItem?.filter(item => item.isGift)?.map(item => ({
    //     sku: item.sku,
    //     parentSku: item.parentSku,
    //     name: item.name,
    //     qty: item.qty,
    //     price: item.price,
    //     isGift: item.isGift,
    //     inStock: item.inStock ?? item.quantityStock ?? 'N/A'
    //   })) || [],
    //   allProductsInStock: responseJson?.response?.products?.map(p => ({
    //     sku: p.sku,
    //     inStock: p.inStock ?? p.quantityStock ?? 'N/A',
    //     isGift: p.isGift || false
    //   })) || [],
    //   gwpEnabled: gwpCountryConfig?.enable_gift_with_purchase_module_feature || false,
    //   gwpConfig: {
    //     websiteCode: gwpWebsiteCode,
    //     countryConfig: gwpCountryConfig,
    //     eligibleContextId: gwpCountryConfig?.eligible_products_context_id || null
    //   },
    //   totalsInfo: {
    //     grandTotal: responseJson?.response?.grandTotal,
    //     subTotal: responseJson?.response?.subTotal,
    //     discount: responseJson?.response?.discount,
    //     shippingAmount: responseJson?.response?.shippingAmount,
    //     itemsCount: responseJson?.response?.itemsCount,
    //     itemsQty: responseJson?.response?.itemsQty
    //   }
    // };
    // logger.info(`${newLogPrefix} GWP Debug Info: ${JSON.stringify(gwpDebugInfo)}`);
    
      
    if (responseJson == null) {
      // logger.error(`${logPrefix} Into quote responseJson is null ${quoteId}`);
      return res.status(500).json({
        status: true,
        statusCode: "500",
        statusMsg: "Something went wrong!",
      });
    } else {
      // logger.info(`${logPrefix} Get Quote success ::: ${quoteId}`);
      if(isVersion7){
        return responseJson
      }else{
        return res.status(200).json(responseJson);
      }
    }
  } catch (e) {
    // logger.error(`${logPrefix} get quote issue ${e.message} ${xHeaderToken}`);
    return res.status(500).json({
      status: false,
      statusCode: "500",
      statusMsg: e.message,
    });
  }
};

exports.retryPaymentFailedReplica = async ({ req, res, pool }) => {
  const { payfortMerchantReference, quoteId, failedPaymentMethod, storeId, paidStyliCredit } =
    req.body;
    // logInfo("retryPayment")
    // logInfo("retryPayment payfortMerchantReference:", payfortMerchantReference);
    // logInfo(" retryPayment quoteId:", quoteId);
    // logInfo("retryPayment failedPaymentMethod:", failedPaymentMethod);
    // logInfo("retryPayment storeId:", storeId);
    // logInfo("retryPayment paidStyliCredit:", paidStyliCredit);

    const baseConfig = cache.get('baseConfig');
    const paymentColl = await collection();
    const paymentCluster = await initcluster()
  const { headers } = req;
  const xHeaderToken = headers["x-header-token"] || "";

  try {
    const quote = await fetchQuote.fetchQuote({
      identifier: quoteId,
      storeId,
      collection:paymentColl,
      cluster:paymentCluster,
      type: 'guest',
      res,
      retryPaymentReplica: true,
    });

    // logInfo("retryPayment Quote Data: ",quote);

    if (!quote) {
      res.status(200);
      res.json({
        status: false,
        statusCode: '202',
        statusMsg: 'quote not found!',
      });
      return;
    }

    // logInfo("retryPayment baseConfig: ",baseConfig);
    
    if (
      baseConfig?.retry_payment_limit &&
      quote?.failedPaymentMethod?.length >= baseConfig?.retry_payment_limit
    ) {
      res.status(200);
      res.json({
        status: false,
        statusCode: '204',
        statusMsg: 'Retry Payment count exceeded the allowed limit',
      });
      return;
    }


    quote.retryPayment = true;
    const method = quote?.failedPaymentMethod || [];
    method.push(failedPaymentMethod);
    quote.failedPaymentMethod = method;
    quote.payfortMerchantReference = payfortMerchantReference;
    quote.paidStyliCredit = paidStyliCredit;
    if(paidStyliCredit){
    quote.amstorecreditUse = true;
    }
    
    try {
      await upsertQuote({ storeId, quote, collection :paymentColl, xHeaderToken });
      // logInfo('retryPayment successfully upsert');
    } catch (e) {
      // logInfo('retryPayment error in upsert try');
      // logError(e, 'Error in saving quote for payment failed', xHeaderToken);
      res.status(500);
      res.json({
        status: false,
        statusCode: '500',
        statusMsg: 'Error in saving quote for payment failed ' + e.message,
      });
      return;
    }

    res.status(200);
    res.json({
      status: true,
      statusCode: '200',
      statusMsg: 'success, quote enabled: ' + quoteId,
      quoteId,
      customerId: quote.customerId,
      triedPaymentMethods: method,
      triedPaymentCount: method.length,
      retryPaymentThreshold: baseConfig?.retry_payment_limit,
    });
    // logInfo('retryPayment successfullly return');
    return;
  } catch (e) {
    // logInfo('retryPayment retry payment in replica ');
    // logError(e, 'Error in retry payment in replica', xHeaderToken);
    res.status(500);
    res.json({
      status: false,
      statusCode: '500',
      statusMsg: 'Error in retry payment in replica ' + e.message,
    });
    return;
  }
};

exports.removeWishlistMessageFromBag = async ({ req, res}) => {
  try {
    const { body, headers } = req;
    const storeId = String(body?.storeId || "");
    const quoteId = String(body?.quoteId || "");
    const customerId = String(body?.customerId || "");
    const messageCollection = await collection();
    const messageCluster = await initcluster();
    let quote;
      if (!customerId) {
        quote = await fetchQuote.fetchQuote({
          identifier: quoteId,
          storeId,
          collection:messageCollection,
          cluster:messageCluster,
          type: "guest",
          res,
        });
      } else {
        quote = await fetchQuote.fetchQuote({
          identifier: customerId,
          storeId,
          collection :messageCollection,
          cluster:messageCluster,
          type: "customer",
          res,
        });
      }
    if (quote) {
      quote.showOOSWishlist = false;
      await upsertQuote({
        storeId,
        quote,
        collection :messageCollection,
        xHeaderToken: headers["x-header-token"],
      });

      return res.status(200).json({
        status: true,
        statusCode: "200",
        statusMsg: "Success",
        response: {
          showOOSWishlist: quote.showOOSWishlist,
        },
      });
    } else {
      return res.status(404).json({
        status: false,
        statusCode: "404",
        statusMsg: "Quote not found",
      });
    }
  } catch (e) {
    // logError(
    //   e,
    //   "Error removing message from bag",
    //   req?.headers["x-header-token"]
    // );
    return res.status(500).json({
      status: false,
      statusCode: "500",
      statusMsg: e.message,
    });
  }
};


async function applyConsulFraudRules({
  baseConfig,
  previousOrderList,
  responseObject,
  quote,
  paymentRestriction
}) {
  paymentRestriction.cancelOrder = paymentRestriction.cancelOrder || false;
  try {
    const action = await processFraudRulesFromConsul({
      baseConfig,
      previousOrderList,
      quote,
      cartValue: responseObject.grandTotal,
      paymentMethod: responseObject.paymentMethod,
    });

    if (action) {
      switch (action.action) {
        case "otp_validation":
          paymentRestriction.otpValidation = true;
          break;
        case "restrictCod":
          paymentRestriction.codEnabled = false;
          break;
          case "restrict_order": 
          paymentRestriction.cancelOrder = true;
          break;
        default:
          // console.log("applyConsulFraudRules : No specific action required.");
      }
    } else {
      // console.log("applyConsulFraudRules : No fraud actions needed.");
    }
  } catch (error) {
    // console.error("applyConsulFraudRules : Error processing fraud rules:", error);
  }
}

async function processFraudRulesFromConsul({
  baseConfig,
  previousOrderList,
  quote,
  cartValue,
  paymentMethod,
}) {
  const fraudRules = baseConfig?.fraudRules || {};

  for (const ruleSetKey in fraudRules) {
    if (fraudRules.hasOwnProperty(ruleSetKey)) {
      const ruleSet = fraudRules[ruleSetKey];
      const {
        enabled,
        account_age_minutes: accountAgeMinutes,
        order_count_in_past_hours: {
          hours: hoursToBeChecked,
          min_orders: minOrderCountInPastHours,
        },
        payment_mode: paymentMode,
        undelivered_orders_in_last_x_hours: xHours,
        undelivered_order_count: undeliveredOrders,
        cart_value_exceeds: cartValueExceeds,
        actions: { otp_validation, restrictCod ,restrict_order},
      } = ruleSet;

      // Skip if the rule set is not enabled
      if (!enabled) {
        // console.log(`applyConsulFraudRules : Rule set ${ruleSetKey} is not enabled.`);
        continue;
      }

      // Parse parameters and handle "NA" values
      const parsedAccountAgeMinutes = accountAgeMinutes !== "NA" ? parseInt(accountAgeMinutes, 10) : null;
      const parsedHoursToBeChecked = hoursToBeChecked !== "NA" ? parseInt(hoursToBeChecked, 10) : null;
      const parsedMinOrderCountInPastHours = minOrderCountInPastHours !== "NA" ? parseInt(minOrderCountInPastHours, 10) : null;
      const parsedCartValueExceeds = cartValueExceeds !== "NA" ? parseFloat(cartValueExceeds) : null;
      const parsedXHours = xHours !== "NA" ? parseInt(xHours, 10) : null;
      const parsedUndeliveredOrders = undeliveredOrders !== "NA" ? parseInt(undeliveredOrders, 10) : null;

      const isValidAccountAge = parsedAccountAgeMinutes !== null;
      const isValidOrderCount = parsedHoursToBeChecked !== null && parsedMinOrderCountInPastHours !== null;
      const isValidPaymentMode = paymentMode !== "NA";
      const isValidUndeliveredOrders = parsedUndeliveredOrders !== null;
      const isValidCartValue = parsedCartValueExceeds !== null;
      const isValidParsedXHours = parsedXHours !== null;

      try {
        const recentOrderCount = isValidOrderCount
          ? getRecentOrderCount(previousOrderList, parsedHoursToBeChecked)
          : null;
        if (isValidOrderCount) {
          // console.log(
          //   `applyConsulFraudRules : Order count in the past ${parsedHoursToBeChecked} hours: ${recentOrderCount}`
          // );
        }

        const undeliveredOrderCount = isValidParsedXHours
          ? getUndeliveredOrderCount(previousOrderList, parsedXHours,baseConfig)
          : null;
        if (isValidUndeliveredOrders) {
          // console.log(
          //   `applyConsulFraudRules : Undelivered order count: ${undeliveredOrderCount}`
          // );
        }

        const registeredSince = await getRegisteredSince(quote?.customerId);
        if (registeredSince) {
          const registeredMoment = moment(registeredSince);
          const now = moment();
          const ageInMinutes = now.diff(registeredMoment, "minutes");

          if (
            (isValidOrderCount ? recentOrderCount >= parsedMinOrderCountInPastHours : true) &&
            (isValidPaymentMode ? paymentMode.toLowerCase().trim() === paymentMethod.toLowerCase().trim() : true) &&
            (isValidCartValue ? cartValue >= parsedCartValueExceeds : true) &&
            (isValidUndeliveredOrders ? undeliveredOrderCount >= parsedUndeliveredOrders : true) &&
            (isValidAccountAge ? ageInMinutes < parsedAccountAgeMinutes : true)
          ) {
            // console.log(
            //   `applyConsulFraudRules : All conditions are true. Confirmed Fraud Customer: ${quote?.customerId}`
            // );

            if (otp_validation) {
              // console.log(
              //   "applyConsulFraudRules : OTP validation is required."
              // );
              return { action: "otp_validation" };
            }
            if (restrictCod) {
              // console.log(
              //   "applyConsulFraudRules : COD restriction is required."
              // );
              return { action: "restrictCod" };
            }
            if (restrict_order) {
              // console.log("applyConsulFraudRules: Forbid this order.");
              return { action: "restrict_order" };
            }
          }
        }
      } catch (error) {
        // console.log(
        //   `applyConsulFraudRules : Error processing fraud rule set ${ruleSetKey}:`,
        //   error
        // );
      }
    }
  }
  return null;
}

function getRecentOrderCount(previousOrderList, hoursToBeChecked) {
  if (previousOrderList != null) {
    const now = new Date();
    const timeWindowStart = new Date(
      now.getTime() - hoursToBeChecked * 60 * 60 * 1000
    );
    return previousOrderList.filter((order) => {
      const orderDate = new Date(order.createdAt);
      return orderDate >= timeWindowStart;
    }).length;
  }
  return 0;
}

function getUndeliveredOrderCount(previousOrderList, xHours,baseConfig) {
  const undeliveredStatuses = baseConfig?.undeliveredStatuses?.statuses || [];

  if (previousOrderList != null && xHours != null) {
    const now = new Date();
    const timeWindowStart = new Date(now.getTime() - xHours * 60 * 60 * 1000);
    const recentOrders = previousOrderList.filter((order) => {
      const orderDate = new Date(order.createdAt); 
      return orderDate >= timeWindowStart;
    });
    const undeliveredOrderCount = recentOrders.filter((order) => {
      return undeliveredStatuses.includes(order.status);
    }).length;

    return undeliveredOrderCount;
  }
  return 0;
}

async function deleteQuote({ quote, collection, xHeaderToken }) {
  let response;
  const apm = global?.apm;
  const key = `quote_${quote.id}`;
  let span;

  try {
    span = apm?.startSpan('CB: Delete Quote', 'db', 'couchbase', 'delete');
    if (span) {
        span.setServiceTarget('couchbase', process.env.COUCHBASE_CLUSTER_BUCKET);
    }
    await collection.remove(key, { timeout: 10000 });
    // console.log(`applyConsulFraudRules: Deleted Quote From DB. Quote ID: ${quote.id}`);
    response = true;
  } catch (e) {
    // console.log(e, `${key} - Error deleting quote, xHeaderToken: ${xHeaderToken}`);
    response = false;
  } finally {
    if (span) span.end();
  }
  
  return response;
}

const findQuoteCartValue = (quote) => {
  let totalPrice = Number(0);
  if (quote?.quoteItem) {
    quote.quoteItem.forEach((product, index) => {
    const item = quote.quoteItem[index];
    if (item) {
      let subPrice = (item.priceInclTax || 0) * (item.qty || 1);
      let droppedPrice = item.droppedPrice || 0;
      if (droppedPrice > 0) {
        subPrice = (item.droppedPrice || 0) * (item.qty || 1);
      }
      const discountAmount = (item.discountAmount || 0);
      subPrice -= discountAmount;
      totalPrice += subPrice;
    }
   });
  }
  return totalPrice;
}

exports.updateQuotewithShukrandata = async function ({ req, res, pool, resetNotifs = true }) {
  const { body } = req;
  const logPrefix = `getQuote:::${new Date().toISOString()}`;
  const { headers } = req;
  const xHeaderToken = headers["x-header-token"] || "";
  let getQuoteColl, getQuoteCluster;
  try {
    const dependencies = await initializeDependencies(pool, logPrefix);
    getQuoteColl = dependencies.getQuoteColl;
    getQuoteCluster = dependencies.getQuoteCluster;
  } catch (e) {
    logError(e, "Couchbase connection in getQuote", xHeaderToken);
    return res.status(500).json({
      status: true,
      statusCode: "500",
      statusMsg: "Couchbase connection issue: " + e.message,
    });
  }
  
  try{
    const quoteId = String(body?.quoteId || "");
    const customerId = String(body?.customerId || "");
    const storeId = body?.storeId
    const phoneNumber = body?.phoneNumber || "";
    let quote = {};

    quote = await fetchQuote.fetchQuote({
      identifier: quoteId || customerId,
      storeId,
      collection: getQuoteColl,
      cluster: getQuoteCluster,
      type: quoteId ? "guest" : "customer",
      res,
    });

    if (!quote) {
      return handleQuoteNotFound(res);
    }

    if (typeof phoneNumber === "string" && phoneNumber.trim() !== "") {
      quote.customerPhoneNumber = phoneNumber;
    }
  
    try {
      // Upsert the updated quote into the database
      await upsertQuote({ storeId, quote, collection : getQuoteColl, xHeaderToken });
      res.status(200).json({
        status: true,
        statusCode: "200",
        statusMsg: "Quote updated successfully",
        data: { quoteId: quoteId , customerId:customerId, phoneNumber },
      });
    } catch (upsertError) {
      // console.error(logPrefix, "Error during upsert operation", upsertError);
      res.status(500).json({
        status: false,
        statusCode: "500",
        statusMsg: "Failed to update the quote",
      });
    }
  } catch (error) {
    // console.error(logPrefix, "Unexpected error", error);
    res.status(500).json({
      status: false,
      statusCode: "500",
      statusMsg: "Unexpected error occurred",
    });
  }
};
function handleQuoteNotFound(res) {
  res.status(200).json({
    status: false,
    statusCode: "202",
    statusMsg: "quote not found!",
  });
}

async function initializeDependencies(pool, logPrefix) {
  try {
    // console.log("Initializing dependencies", logPrefix);
    const getQuoteColl = await collection();
    const getQuoteCluster = await initcluster();

    if (!getQuoteColl || !getQuoteCluster) {
      throw new Error("Couchbase connection is missing");
    }

    await pool.query("SELECT 1");

    return { getQuoteColl, getQuoteCluster };
  } catch (e) {
    // console.error(logPrefix, "Error initializing dependencies");
    throw e;
  }
}

async function getShukranCurrencyCode(storeId) {
  let shukranCurrencyCode = getStoreConfig(storeId, "shukranCurrencyCode");
  if (!shukranCurrencyCode || typeof shukranCurrencyCode !== 'string') {
    shukranCurrencyCode= 'SAU'; // default shukranCurrencyCode
  } 
  // console.log(`get ShukranCurrencyCode ${shukranCurrencyCode}`);

  return shukranCurrencyCode;
}

exports.verifyPreferredPayment = async function ({ req, res }) {
  try{
    const { body , headers } = req;
    const storeId = String(body?.storeId || "");
    const customerId = String(body?.customerId || "");
    const xHeaderToken = headers["x-header-token"] || "";
    const xSource = headers["x-source"] || headers["X-Source"] || "";
    const xOs = headers["x-os"] || "";
    const xBrowser = headers["x-browser"] || "";
    const xClientVersion = headers["x-client-version"] || "";
    const basicDataColl = await collection();
    const basicDataCluster = await initcluster();
    const baseConfig = cache.get('baseConfig');
    const isMacOsSafari = xOs.toLowerCase() === 'macos' && xBrowser.toLowerCase() === 'safari';
    const isIos = xSource.toLowerCase() === 'ios';
    let defaultPaymentMethod = (isIos || isMacOsSafari) ? 'apple_pay' : baseConfig?.defaultPaymentMethod;
    const bnplList = baseConfig?.bnplListForPreferredPayment || [];
    let lastPaymentMethodEnable = baseConfig?.lastPaymentMethodEnable;

    if (!customerId || !lastPaymentMethodEnable) {
        return res.status(200).json({
        status: true, 
        statusCode: "200",
        statusMsg: "Preferred payment method verify successfully",
        data: { preferred_payment : defaultPaymentMethod },
      });
    }
    
    const quote = await fetchQuote.fetchQuote({
      identifier: customerId,
      storeId,
      collection: basicDataColl,
      cluster: basicDataCluster,
      type: "customer",
      res,
    });

    if (!quote) {
      return res.status(404).json({
        status: false,
        statusCode: "404",
        statusMsg: "Quote not found for the given customer",
      });
    }

    let preferredPayment = !quote?.preferred_payment || quote?.preferred_payment === '' ? defaultPaymentMethod : quote.preferred_payment;

    const isAndroidOrNonSafariMac = (xSource.toLowerCase() === 'android') || (xOs.toLowerCase() === 'macos' && xBrowser.toLowerCase() !== 'safari');
    if (isAndroidOrNonSafariMac && preferredPayment === 'apple_pay') {
      preferredPayment = defaultPaymentMethod;
    }

    if (!bnplList.includes(preferredPayment)) {
      return res.status(200).json({
      status: true, 
      statusCode: "200",
      statusMsg: "Preferred payment method verify successfully",
      data: { preferred_payment : preferredPayment },
      });
    }

      const safeGrandTotal = typeof quote.grandTotalCopy === 'number' && !isNaN(quote.grandTotalCopy) ? quote.grandTotalCopy : 0;
      const estimatedTotal = safeGrandTotal || quote.subtotalWithDiscount + (quote.shippingCharges || 0) + (quote.codCharges || 0);
      const responseObject = {
        quoteId: quote.id,
        storeId: quote.storeId,
        grandTotal: String(quote.grandTotalCopy || 0),
        estimatedTotal: String(estimatedTotal),
        codCharges: String(quote.codCharges || 0),
        discountData: quote.discountData || [],
        selectedPaymentMethod: quote.quotePayment?.method || defaultPaymentMethod,
        availablePaymentMethods: [], 
        paymentsConfig: {},          
        xSource: xSource || "",      
        xClientVersion: xClientVersion || "",
        xHeaderToken: xHeaderToken || "",
      };
      const paymentMethods = cache.get('paymentMethods');

      const usePaymentService = paymentMethods?.usePaymentService;
      if (usePaymentService){
        await paymentService.paymentMethodsV2(quote, responseObject, headers,xClientVersion,xSource,xHeaderToken);
      }else{
        let isFirstCall = true ;
        await paymentService.paymentMethods(quote, responseObject, isFirstCall);
      }

      const availableMethods = responseObject["availablePaymentMethods"] || [];
      const finalPaymentMethod = availableMethods.includes(preferredPayment) ? preferredPayment : defaultPaymentMethod;
      return res.status(200).json({
        status: true,
        statusCode: "200",
        statusMsg: "Preferred payment method verify successfully",
        data: { preferred_payment: finalPaymentMethod },
      });
    } catch (error) {
      // console.error("verifyPreferredPayment error:", error);
      return res.status(500).json({
        status: false,
        statusCode: "500",
        statusMsg: "Something went wrong while verifying preferred payment",
        error: "Internal Server Error",
      });
  }
}

exports.addToQuote = async ({ req, res }) => {
  const { headers } = req;
  const xHeaderToken = headers['x-header-token'] || '';
  const logPrefix = `addToQuote:::${new Date().toISOString()}`;
  
  // Helper functions to reduce code duplication
  const sendResponse = (statusCode, status, statusMsg, additionalData = {}) => {
    res.status(statusCode);
    res.json({
      status,
      statusCode: String(statusCode),
      statusMsg,
      ...additionalData
    });
  };

  const logWithPrefix = (message, ...args) => {
    logInfo(logPrefix, message, ...args);
  };

  // Helper function to validate and setup quote
  const validateAndSetupQuote = async (quoteId, customerId, storeId, connStr, clusterObj) => {
    const isGuest = !customerId;
    logWithPrefix(
      `In customerId is ${isGuest ? "not" : ""} there${
        isGuest ? "" : `: ${customerId}`
      }`
    );

    const quote = await fetchQuote.fetchQuote({
      identifier: isGuest ? quoteId : customerId,
      storeId,
      collection: connStr,
      cluster: clusterObj,
      type: isGuest ? "guest" : "customer",
      res,
    });
    
    if (!quote && quoteId) {
      logWithPrefix(`quote is not there:::`);
      return { success: false, error: 'quote not found!' };
    }
    
    return { success: true, quote };
  };

  // Helper function to update quote with SKU and website data
  const updateQuoteData = (quote, addToQuoteProductsRequests, storeId) => {
    if (!quote) return quote;
    
    // logWithPrefix(`quote is there in addToQuoteProductsRequests`);
    const skuList = quote?.skus || [];
    const sku = addToQuoteProductsRequests.map(addProduct => addProduct.sku);
    // logInfo('sku', sku);
    
    if (sku?.length > 0) {
      skuList.push(sku[0]);
    }
    
    if (skuList && skuList?.length > 0) {
      quote.skus = skuList;
    }
    
    const websiteId = Number(getStoreConfig(storeId, 'websiteId') || 0);
    let webSiteArray = [];
    webSiteArray.push(websiteId);
    quote.websiteId = webSiteArray;
    
    return quote;
  };

  // Helper function to validate UUID
  const validateUUID = (uuid, quote, baseConfig) => {
    if (!uuid || !baseConfig?.uuid_validation) return true;
    
    // logWithPrefix(`In the baseConfig `);
    // logInfo('UUID present in JWT', 'UUID present in JWT', xHeaderToken);
    
    return !(quote?.uuid && quote?.uuid != uuid);
  };

  // Helper function to validate bag count limit
  const validateBagCountLimit = (quote) => {
    const itemsCount = quote?.quoteItem?.length || 0;
    
    if (!process.env.MAX_BAG_COUNT) return true;
    
    if (itemsCount + 1 > Number(process.env.MAX_BAG_COUNT)) {
      // logInfo(
      //   `Maximum limit of products reached `,
      //   `${quote?.id} item count : ${itemsCount}`,
      //   xHeaderToken
      // );
      return false;
    }
    
    return true;
  };

  // Helper function to handle gift removal
  const handleGiftRemoval = async (addToQuoteResponse, quote, addToQuoteProductsRequests) => {
    if (!addToQuoteResponse?.isGift) return null;
    
    const requestSku = addToQuoteProductsRequests[0]?.sku;
    const toRemoveSkuObj = quote?.quoteItem?.find(
      (item) => item.isGift && item.sku !== requestSku
    );

    if (!toRemoveSkuObj?.sku) return null;
    // console.log("GWP missing debug log::: remove item from `handleGiftRemoval`", toRemoveSkuObj, "quoteId", quote?.id);
    return removeItemFromQuote({
      quote,
      skus: [toRemoveSkuObj.sku],
      xHeaderToken,
    });
  };

  // Helper function to set UUID if needed
  const setUUIDIfNeeded = (uuid, quote) => {
    if (uuid && !quote?.uuid) {
      // logInfo("UUID not present in quote. Saving UUID to quote.", xHeaderToken);
      quote.uuid = uuid;
    }
    return quote;
  };

  // Helper function to process quote upsert and gift removal
  const processQuoteUpdates = async (quote, removeGiftPromise, storeId, connStr) => {
    const [removeQuoteResponse, upsertResponse] = await Promise.all([
      removeGiftPromise,
      upsertQuote({
        storeId,
        quote,
        collection: connStr,
        xHeaderToken,
      }),
    ]);

    if (removeQuoteResponse?.quote) {
      quote = removeQuoteResponse.quote;
    }

    return { quote, upsertResponse };
  };

  try {
    const [connStr, clusterObj] = await Promise.all([
      collection(),
      initcluster(),
    ]);
    
    if (!connStr || !clusterObj) {
      // console.log(logPrefix, "Couchbase connection is missing : in Add to Quote API : ");
    }
    
    const { body, uuid } = req;
    const { storeId, source, addToQuoteProductsRequests } = body;
    
    // logWithPrefix(` In Add TO Quote storeId, ${storeId}`);
    // logWithPrefix(` In Add TO Quote source, ${source}`);
    
    const quoteId = String(body?.quoteId || "");
    const customerId = String(body?.customerId || "");
    const cityId = body?.cityId || "";
    let { warehouseId = "", fulfillmentMode = "" } = addToQuoteProductsRequests[0] || {};
    
    // logWithPrefix(` In Add TO Quote customerId, ${customerId}`);
    // logWithPrefix(` In Add TO Quote quoteId, ${quoteId}`);
    
    if (!validateAddProductReq({ addToQuoteProductsRequests })) {
      sendResponse(200, false, 'Invalid request');
      return '';
    }
    
    // logWithPrefix(`In validateAddProductReq`);
    
    // Validate and setup quote
    const quoteValidation = await validateAndSetupQuote(quoteId, customerId, storeId, connStr, clusterObj);
    if (!quoteValidation.success) {
      sendResponse(200, false, quoteValidation.error);
      return;
    }
    
    let quote = quoteValidation.quote;
    
    // Update quote with SKU and website data
    quote = updateQuoteData(quote, addToQuoteProductsRequests, storeId);
    
    // Validate UUID
    const baseConfig = cache.get('baseConfig');
    // logInfo('baseConfig', baseConfig);
    
    if (!validateUUID(uuid, quote, baseConfig)) {
      return sendResponse(400, false, 'JWT uuid absent/mismatch!');
    }
    
    // Validate bag count limit
    if (!validateBagCountLimit(quote)) {
      return sendResponse(500, false, 'Maximum limit of products reached');
    }
    // Encode warehouseId if it is present
    if(warehouseId){
      warehouseId = encodeURIComponent(warehouseId);
    }
    
    // Add product to quote
    const addToQuoteResponse = await addProductToQuote({
      storeId,
      source,
      quote,
      customerId,
      addToQuoteProductsRequests,
      collection: connStr,
      res,
      xHeaderToken,
      warehouseId,
      fulfillmentMode,
      cityId,
      isVersion6: true
    });

    // logWithPrefix(`addToQuoteResponse: ${JSON.stringify(addToQuoteResponse)}`);

    if (addToQuoteResponse?.error) {
      // logWithPrefix(addToQuoteProductsRequests, "Product not found", xHeaderToken);
      sendResponse(200, false, addToQuoteResponse.error.statusMsg || 'Product not found', addToQuoteResponse.error);
      return;
    }

    quote = addToQuoteResponse.quote;

    // Handle gift removal
    const removeGiftPromise = await handleGiftRemoval(addToQuoteResponse, quote, addToQuoteProductsRequests);
    
    // Set UUID if needed
    quote = setUUIDIfNeeded(uuid, quote);
    
    // Process quote updates
    const { upsertResponse } = await processQuoteUpdates(quote, removeGiftPromise, storeId, connStr);

    if (upsertResponse) {
      sendResponse(200, true, 'Success!', {
        customerId,
        error: null,
        quoteId: String(quote.id)
      });
      return;
    }
    
  } catch (e) {
    // logError(e, 'Error addToQuote : ', logPrefix, xHeaderToken);
    return sendResponse(500, false, e.message);
  }
}