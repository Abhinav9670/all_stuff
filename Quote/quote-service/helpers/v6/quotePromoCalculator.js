const _ = require('lodash');
const { formatPrice, getStoreConfig, getBaseConfig, getAdminStoreConfig } = require('../../helpers/utils');
const { upsertQuote } = require("../../helpers/upsertQuote");
const couponLabels = require("../../assets/couponLabels.json");
const { calculateShippingCharges } = require('../freeShipping');
const { checkIsGwp } = require('../product');
const { calculateCodCharges } = require('./codCharges');
const cache = require('memory-cache');

exports.promoCalculator = async ({ storeId, quote, collection, discountObject, xHeaderToken, upsert = true, optimized = false, isVersion7 = false }) => {
   
    const giftConfig = getBaseConfig('giftProducts') || {};

    let shippingCharges = Number(getStoreConfig(storeId, 'shipmentCharges') || 0);
    const discountData = [];

    quote.updatedAt = new Date();

    quote.subtotal = formatPrice(discountObject.subtotal);
    quote.baseSubtotal = formatPrice(discountObject.subtotal);
    quote.subtotalWithDiscount = formatPrice(discountObject.subtotalWithDiscount);
    quote.baseSubtotalWithDiscount = formatPrice(discountObject.subtotalWithDiscount);

    quote.couponCode = null;
    quote.couponDiscount = null;
    quote.autoPromoCode = null;
    quote.autoPromoAmount = null;

    let autoPromoCode = '';
    let autoPromoAmount = null;

    for (const discount of discountObject.discounts) {
        if (!discount.error_code) {
            discountData.push({
                couponCode: discount.coupon_code,
                discountType: discount.discount_type,
                redeemType: discount.redeem_type,
                value: discount.value,
                label: JSON.stringify(couponLabels[discount.redeem_type] || {}),
                onOsp: discount?.onOsp ?? false,
                isGiftVoucher: discount?.is_gift_voucher ?? false
            })
        }
        if (discount.redeem_type == "MANUAL") {
            quote.couponCode = discount.error_code ? null : discount.coupon_code;
            quote.couponDiscount = discount.error_code ? null : discount.value;
        }
        if (discount.redeem_type == "AUTO") {
            autoPromoAmount = discount.error_code ? autoPromoAmount : autoPromoAmount + Number(discount.value || 0)
            autoPromoCode = discount.error_code && discount.coupon_code ? autoPromoCode : `${autoPromoCode || ''} ${discount.coupon_code}`

        }
    }

    quote.autoPromoAmount = autoPromoAmount;
    quote.autoPromoCode = autoPromoCode.trim();

    quote.discountData = discountData;
    quote.prepaidPayable = discountObject.prepaidPayable;
    
    const { shippingCharges: calculatedShippingCharges, shippingThreshold } = await calculateShippingCharges({
        quote,
        storeId,
        xHeaderToken,
        subtotalWithDiscount: quote.subtotalWithDiscount
    });
    shippingCharges = calculatedShippingCharges;
    quote.shippingThreshold = shippingThreshold;
    quote.shippingCharges = formatPrice(shippingCharges);

    for (const item of quote.quoteItem) {
        const productObj = _.find(discountObject.products, { 'sku': item.sku });
        if(!productObj &&  giftConfig?.excludeFromPromo) continue;

        item.taxAmount = formatPrice(productObj.taxAmount);
        item.baseTaxAmount = formatPrice(productObj.taxAmount);
        item.taxPercent = productObj.taxPercent;
        item.rowTotal = formatPrice(productObj.rowTotal);
        item.baseRowTotal = formatPrice(productObj.rowTotal);
        item.discountAmount = formatPrice(productObj.discountAmount);
        item.baseDiscountAmount = formatPrice(productObj.discountAmount);
        item.discountPercent = productObj.discountPercent;
        item.priceInclTax = formatPrice(productObj.priceInclTax);
        item.basePriceInclTax = formatPrice(productObj.priceInclTax);
        item.rowTotalInclTax = formatPrice(productObj.rowTotalInclTax);
        item.baseRowTotalInclTax = formatPrice(productObj.rowTotalInclTax);
        item.price = formatPrice(productObj.price);
        item.basePrice = formatPrice(productObj.price);
        item.discountTaxCompensationAmount = formatPrice(productObj.discountTaxCompensationAmount);
        item.baseDiscountTaxCompensationAmount = formatPrice(productObj.discountTaxCompensationAmount);
        item.appliedCouponValue = productObj.appliedCouponValue;
        item.disableSpecialPrice = productObj?.disableSpecialPrice ?? false;
        item.taxObj = productObj?.taxObj;
        item.isGwp = checkIsGwp({
          enrich: item?.enrich,
          storeId,
          xHeaderToken,
        });
    }

    const baseConfig = cache.get('baseConfig') || {};
    const enableAdvancedCodCharges = baseConfig?.enableAdvancedCodCharges ?? false;
    // console.log('[COD CHARGES] quotePromoCalculator - enableAdvancedCodCharges:', enableAdvancedCodCharges, 'isVersion7:', isVersion7, 'quote:', quote.id);
    
    if (enableAdvancedCodCharges && isVersion7) {
      // console.log('[COD CHARGES] quotePromoCalculator - Using advanced COD calculation (flag enabled and version7)');
      const codChargesResult = calculateCodCharges({
        quoteItems: quote.quoteItem || [],
        quote,
        storeId,
        paymentMethod: quote.quotePayment?.method || ''
      });
      quote.codCharges = codChargesResult.totalCodCharges;
      quote.expressCodCharges = codChargesResult.expressCodCharges;
      quote.globalCodCharges = codChargesResult.globalCodCharges;
    } else {
      // console.log('[COD CHARGES] quotePromoCalculator - Using traditional COD calculation (flag disabled or not version7)');
      const codCharges = Number(getStoreConfig(storeId, 'codCharges') || 0);
      quote.codCharges = formatPrice((quote.quotePayment?.method == "cashondelivery") ? codCharges : 0);
      // console.log('[COD CHARGES] quotePromoCalculator - Set traditional codCharges:', quote.codCharges);
    }

    let newGwpSubtotal = 0;
    quote.quoteItem.forEach(item => {
      if (item.isGwp) {
        const rowTotalWithDiscount = parseFloat(item.rowTotalInclTax - item.discountAmount || 0);
        newGwpSubtotal += rowTotalWithDiscount;
      }
    });

    quote.newGwpSubtotal = formatPrice(newGwpSubtotal);

    quote.calcSource = "promo";
    
    if (upsert && !optimized) await upsertQuote({ storeId, quote, collection, xHeaderToken });

    return quote;

}
