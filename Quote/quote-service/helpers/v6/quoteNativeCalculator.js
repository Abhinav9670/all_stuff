const _ = require("lodash");
const {
  getCurrency,
  getStoreConfig,
  formatPrice,
  getPrice,
  getSpecialPrice,
  getChildPrice,
  getChildSpecialPrice,
  getTaxIndia,
  getAdminStoreConfig
} = require("../../helpers/utils");
const { upsertQuote } = require("../../helpers/upsertQuote");
const { inTaxOnProductPrice, isIntraState } = require("../tax");
const { calculateShippingCharges } = require("../freeShipping");
const { checkIsGwp } = require("../product");
const { calculateCodCharges } = require("./codCharges");
const cache = require('memory-cache');

exports.nativeCalculator = async ({
  storeId,
  quote,
  productDetailsResponse,
  collection,
  xHeaderToken,
  upsert = true,
  prepaidPayable = {},
  optimized = false,
  isVersion7 = false
}) => {
  try {
    const currency = getCurrency({ storeId });

    quote.updatedAt = new Date();

    const taxPercentage = Number(getStoreConfig(storeId, "taxPercentage") || 0);
    let shippingCharges = Number(
      getStoreConfig(storeId, "shipmentCharges") || 0
    );

    let subtotal = 0.0;
    let subTotalWithDiscount = 0.0;
    let rowTotalDiscountAmountForQuote = 0.0;

    let itemsCount = 0;
    let itemsQty = 0;

    for (const item of quote.quoteItem) {
      let priceInclTax = 0;

      if (productDetailsResponse && !productDetailsResponse.length > 0)
        continue;
      itemsCount++;
      itemsQty += item.qty;

      const parentProductId = item.parentProductId;
      let finalPrice = item.priceInclTax;
      const droppedPrice = item?.droppedPrice && item?.droppedPrice.toFixed(2);

      if (productDetailsResponse) {
        const productObj = _.find(productDetailsResponse, {
          objectID: String(parentProductId),
        });

        const childProductObj = _.find(productObj.configProducts, {
          id: String(item.productId),
        });
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

        finalPrice =
          specialPrice != null ? specialPrice.toFixed(2) : price.toFixed(2);

        if (droppedPrice) {
          finalPrice = droppedPrice;
        }
      }

      if (!item?.isGift) priceInclTax = finalPrice;

      const finalPriceExclTax = (
        (priceInclTax / (100 + taxPercentage)) *
        100
      ).toFixed(2);
      const taxAmount = item.qty * (priceInclTax - finalPriceExclTax);
      const rowTotal = finalPriceExclTax * item.qty;
      const rowTotalDiscountAmount = 0;
      const discountPercent = 0;
      const discountTaxCompensationAmount = 0;

      item.price = formatPrice(finalPrice);
      item.basePrice = formatPrice(finalPrice);
      item.taxPercent = taxPercentage;
      item.taxAmount = formatPrice(taxAmount);
      item.baseTaxAmount = formatPrice(taxAmount);
      item.rowTotal = formatPrice(rowTotal);
      item.baseRowTotal = formatPrice(rowTotal);
      item.priceInclTax = formatPrice(priceInclTax);
      item.basePriceInclTax = formatPrice(priceInclTax);
      item.rowTotalInclTax = formatPrice(priceInclTax * item.qty);
      item.baseRowTotalInclTax = formatPrice(priceInclTax * item.qty);
      item.discountPercent = discountPercent;
      item.discountAmount = formatPrice(rowTotalDiscountAmount);
      item.baseDiscountAmount = formatPrice(rowTotalDiscountAmount);
      item.discountTaxCompensationAmount = formatPrice(
        discountTaxCompensationAmount
      );
      item.baseDiscountTaxCompensationAmount = formatPrice(
        discountTaxCompensationAmount
      );

      // subtotal += Math.trunc((finalPrice * item.qty || 0) * 100) / 100;

      const formattedFinalPrice = formatPrice(priceInclTax);

      subtotal += formattedFinalPrice * (item.qty || 0);

      rowTotalDiscountAmountForQuote += rowTotalDiscountAmount;

      item.appliedCouponValue = [];
      item.isGwp = checkIsGwp({
        enrich: item?.enrich,
        storeId,
        xHeaderToken,
      });
    }

    quote.couponDiscount = null;
    quote.couponCode = null;
    quote.autoPromoCode = null;
    quote.autoPromoAmount = null;
    quote.discountData = [];

    quote.itemsCount = itemsCount;
    quote.itemsQty = itemsQty;
    
    const { shippingCharges: calculatedShippingCharges, shippingThreshold } = await calculateShippingCharges({
      quote,
      storeId,
      xHeaderToken,
      subtotalWithDiscount: subtotal
    });
    shippingCharges = calculatedShippingCharges;
    quote.shippingThreshold = shippingThreshold;

    quote.shippingCharges = formatPrice(shippingCharges);

    const baseConfig = cache.get('baseConfig') || {};
    const enableAdvancedCodCharges = baseConfig?.enableAdvancedCodCharges ?? false;
    // console.log('[COD CHARGES] quoteNativeCalculator - enableAdvancedCodCharges:', enableAdvancedCodCharges, 'isVersion7:', isVersion7, 'quote:', quote.id);
    
    if (enableAdvancedCodCharges && isVersion7) {
      // console.log('[COD CHARGES] quoteNativeCalculator - Using advanced COD calculation (flag enabled and version7)');
      const codChargesResult = calculateCodCharges({
        quoteItems: quote.quoteItem || [],
        quote,
        storeId,
        paymentMethod: quote.quotePayment?.method || ''
      });
      
      quote.codCharges = codChargesResult.totalCodCharges;
      quote.expressCodCharges = codChargesResult.expressCodCharges;
      quote.globalCodCharges = codChargesResult.globalCodCharges;
      // console.log('[COD CHARGES] quoteNativeCalculator - Set COD charges on quote:', {
      //   codCharges: quote.codCharges,
      //   expressCodCharges: quote.expressCodCharges,
      //   globalCodCharges: quote.globalCodCharges
      // });
    } else {
      // console.log('[COD CHARGES] quoteNativeCalculator - Using traditional COD calculation (flag disabled or not version7)');
      const codCharges = Number(getStoreConfig(storeId, 'codCharges') || 0);
      quote.codCharges = formatPrice((quote.quotePayment?.method == "cashondelivery") ? codCharges : 0);
      // console.log('[COD CHARGES] quoteNativeCalculator - Set traditional codCharges:', quote.codCharges);
    }

    subTotalWithDiscount = formatPrice(
      subtotal - rowTotalDiscountAmountForQuote
    );

    quote.subtotal = formatPrice(subtotal);
    quote.baseSubtotal = formatPrice(subtotal);
    quote.subtotalWithDiscount = subTotalWithDiscount;
    quote.baseSubtotalWithDiscount = subTotalWithDiscount;

    let newGwpSubtotal = 0;
    quote.quoteItem.forEach(item => {
      if (item.isGwp) {
        const rowTotalWithDiscount = parseFloat(item.rowTotalInclTax - item.discountAmount || 0);
        newGwpSubtotal += rowTotalWithDiscount;
      }
    });
    quote.newGwpSubtotal = formatPrice(newGwpSubtotal);

    quote.calcSource = "native";
    quote.prepaidPayable = prepaidPayable;

    if (upsert && !optimized) await upsertQuote({ storeId, quote, collection, xHeaderToken });

    return quote;
  } catch (e) {
    logError(e, "Error prcessing promo response : ", xHeaderToken);
    return quote;
    // console.log('Error fetching promo response : ', e.message);
  }
};

exports.nativeCalculatorIN = async ({
  storeId,
  quote,
  productDetailsResponse,
  collection,
  xHeaderToken,
  upsert = true,
  prepaidPayable = {},
  optimized = false,
  isVersion7 = false
}) => {
  try {
  const currency = getCurrency({ storeId });

  quote.updatedAt = new Date();

  let shippingCharges = Number(getStoreConfig(storeId, "shipmentCharges") || 0)

  let subtotal = 0.0;
  let subTotalWithDiscount = 0.0;
  let rowTotalDiscountAmountForQuote = 0.0;

  let itemsCount = 0;
  let itemsQty = 0;

  /** Indian tax */
  const taxGroups = getTaxIndia("taxGroups");
  const isIntraStateBool = isIntraState({ address: quote.quoteAddress });

  for (let i=0; i < quote.quoteItem.length; i++) {
    let item = quote.quoteItem[i];
    let priceInclTax = 0;

    if (productDetailsResponse && !productDetailsResponse.length > 0) continue;
    itemsCount++;
    itemsQty += item.qty;

    const parentProductId = item.parentProductId;
    let finalPrice = item.priceInclTax;
    const droppedPrice = item?.droppedPrice && item?.droppedPrice.toFixed(2);

    if (productDetailsResponse) {
      const productObj = _.find(productDetailsResponse, {
        objectID: String(parentProductId),
      });

      const childProductObj = _.find(productObj.configProducts, {
        id: String(item.productId),
      });
      let price = getChildPrice({ childProductObj });
      let specialPrice = getChildSpecialPrice({ childProductObj, price });

      if (!price) {
        const priceObj = productObj?.price;
        price = getPrice({ priceObj: priceObj[currency] });
        specialPrice = getSpecialPrice({ priceObj: priceObj[currency], price });
      }

      finalPrice =
        specialPrice != null ? specialPrice.toFixed(2) : price.toFixed(2);

      if (droppedPrice) {
        finalPrice = droppedPrice;
      }
    }
    if (!item?.isGift) priceInclTax = finalPrice;

    // Discount set to 0
    const rowTotalDiscountAmount = 0;
    const discountPercent = 0;
    const discountTaxCompensationAmount = 0;

    let priceObj = {
      price: formatPrice(finalPrice),
      basePrice: formatPrice(finalPrice),
      priceInclTax: formatPrice(priceInclTax),
      basePriceInclTax: formatPrice(priceInclTax),
      rowTotalInclTax: formatPrice(priceInclTax * item.qty),
      baseRowTotalInclTax: formatPrice(priceInclTax * item.qty),
      discountPercent: discountPercent,
      discountAmount: formatPrice(rowTotalDiscountAmount),
      baseDiscountAmount: formatPrice(rowTotalDiscountAmount),
      discountTaxCompensationAmount: formatPrice(discountTaxCompensationAmount),
      baseDiscountTaxCompensationAmount: formatPrice(
        discountTaxCompensationAmount
      ),
    };
    /** Indian Tax */
    const inPriceObj = await inTaxOnProductPrice({
      taxGroups,
      hsnCode: item?.hsnCode,
      item,
      priceObj,
      isIntraStateBool,
    });
    item = { ...item, ...priceObj, ...inPriceObj };
    
    const formattedFinalPrice = formatPrice(priceInclTax);
    subtotal += formattedFinalPrice * (item.qty || 0);
    rowTotalDiscountAmountForQuote += rowTotalDiscountAmount;

    // subtotal += Math.trunc((finalPrice * item.qty || 0) * 100) / 100;
    item.appliedCouponValue = [];
    item.isGwp = checkIsGwp({
      enrich: item?.enrich,
      storeId,
      xHeaderToken,
    });
    quote.quoteItem[i] = item;
  }

  quote.couponDiscount = null;
  quote.couponCode = null;
  quote.autoPromoCode = null;
  quote.autoPromoAmount = null;
  quote.discountData = [];

  quote.itemsCount = itemsCount;
  quote.itemsQty = itemsQty;


  const { shippingCharges: calculatedShippingCharges, shippingThreshold } = await calculateShippingCharges({
    quote,
    storeId,
    xHeaderToken,
    subtotalWithDiscount: subtotal
  });
  shippingCharges = calculatedShippingCharges;
  quote.shippingThreshold = shippingThreshold;

  quote.shippingCharges = formatPrice(shippingCharges);

  const baseConfig = cache.get('baseConfig') || {};
  const enableAdvancedCodCharges = baseConfig?.enableAdvancedCodCharges ?? false;
  // console.log('[COD CHARGES] quoteNativeCalculatorIN - enableAdvancedCodCharges:', enableAdvancedCodCharges, 'isVersion7:', isVersion7, 'quote:', quote.id);
  
  if (enableAdvancedCodCharges && isVersion7) {
    // console.log('[COD CHARGES] quoteNativeCalculatorIN - Using advanced COD calculation (flag enabled and version7)');
    const codChargesResult = calculateCodCharges({
      quoteItems: quote.quoteItem || [],
      quote,
      storeId,
      paymentMethod: quote.quotePayment?.method || ''
    });
    
    quote.codCharges = codChargesResult.totalCodCharges;
    quote.expressCodCharges = codChargesResult.expressCodCharges;
    quote.globalCodCharges = codChargesResult.globalCodCharges;
    // console.log('[COD CHARGES] quoteNativeCalculatorIN - Set COD charges on quote:', {
    //   codCharges: quote.codCharges,
    //   expressCodCharges: quote.expressCodCharges,
    //   globalCodCharges: quote.globalCodCharges
    // });
  } else {
    // Use traditional simple COD calculation
    // console.log('[COD CHARGES] quoteNativeCalculatorIN - Using traditional COD calculation (flag disabled or not version7)');
    const codCharges = Number(getStoreConfig(storeId, 'codCharges') || 0);
    quote.codCharges = formatPrice((quote.quotePayment?.method == "cashondelivery") ? codCharges : 0);
    // console.log('[COD CHARGES] quoteNativeCalculatorIN - Set traditional codCharges:', quote.codCharges);
  }

  subTotalWithDiscount = formatPrice(subtotal - rowTotalDiscountAmountForQuote);

  quote.subtotal = formatPrice(subtotal);
  quote.baseSubtotal = formatPrice(subtotal);
  quote.subtotalWithDiscount = subTotalWithDiscount;
  quote.baseSubtotalWithDiscount = subTotalWithDiscount;

  let newGwpSubtotal = 0;
  quote.quoteItem.forEach(item => {
    if (item.isGwp) {
      const rowTotalWithDiscount = parseFloat(item.rowTotalInclTax - item.discountAmount || 0);
      newGwpSubtotal += rowTotalWithDiscount;
    }
  });
  quote.newGwpSubtotal = formatPrice(newGwpSubtotal);
  
  quote.calcSource = "native";
  quote.prepaidPayable = prepaidPayable;

  if (upsert && !optimized) await upsertQuote({ storeId, quote, collection, xHeaderToken });

  return quote;
} catch (e) {
  logError(e, "Error prcessing promo response : ", xHeaderToken);
  // console.log('Error fetching promo response : ', e.message);
}
};
