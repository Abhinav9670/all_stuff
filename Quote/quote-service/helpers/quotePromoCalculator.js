const _ = require('lodash');
const { formatPrice, getStoreConfig, getAdminStoreConfig } = require('../helpers/utils');
const { upsertQuote } = require("../helpers/upsertQuote");
const { calculateShippingCharges } = require('./freeShipping');

exports.promoCalculator = async ({ storeId, quote, collection, discountObject, xHeaderToken, upsert = true }) => {

    let shippingCharges = Number(getStoreConfig(storeId, 'shipmentCharges') || 0);
    let codCharges = Number(getStoreConfig(storeId, 'codCharges') || 0);

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
    }

    quote.codCharges = formatPrice((quote.quotePayment?.method == "cashondelivery") ? codCharges : 0);

    quote.calcSource = "promo";

    if (upsert) await upsertQuote({ storeId, quote, collection, xHeaderToken });

    return quote;

}
