const _ = require('lodash');
const { getCurrency, getStoreConfig, formatPrice, getPrice, getSpecialPrice,getAdminStoreConfig } = require('../helpers/utils');
const { upsertQuote } = require("../helpers/upsertQuote");
const { calculateShippingCharges } = require('./freeShipping');

exports.nativeCalculator = async ({ storeId, quote, productDetailsResponse, collection, xHeaderToken, upsert = true }) => {

    const currency = getCurrency({ storeId });

    quote.updatedAt = new Date();

    const taxPercentage = Number(getStoreConfig(storeId, 'taxPercentage') || 0);
    let shippingCharges = Number(getStoreConfig(storeId, 'shipmentCharges') || 0);
    let codCharges = Number(getStoreConfig(storeId, 'codCharges') || 0);

    let subtotal = 0.0;
    let subTotalWithDiscount = 0.0;
    let rowTotalDiscountAmountForQuote = 0.0;

    let itemsCount = 0;
    let itemsQty = 0;

    for (const item of quote.quoteItem) {
        if (productDetailsResponse && !productDetailsResponse.length > 0) continue;
        itemsCount++;
        itemsQty += item.qty;

        const parentProductId = item.parentProductId;
        let finalPrice = item.priceInclTax;

        if (productDetailsResponse) {
            const productObj = _.find(productDetailsResponse, { 'objectID': String(parentProductId) });
            const priceObj = productObj?.price;
            let price = getPrice({ priceObj: priceObj[currency] })
            let specialPrice = getSpecialPrice({ priceObj: priceObj[currency], price })
            finalPrice = specialPrice != null ? specialPrice.toFixed(2) : price.toFixed(2);
        }

        const finalPriceExclTax = (finalPrice / (100 + taxPercentage) * 100).toFixed(2);
        const taxAmount = item.qty * (finalPrice - finalPriceExclTax);
        const rowTotal = finalPriceExclTax * item.qty;
        const rowTotalDiscountAmount = 0;
        const discountPercent = 0;
        const discountTaxCompensationAmount = 0;

        item.price = formatPrice(finalPriceExclTax);
        item.basePrice = formatPrice(finalPriceExclTax);
        item.taxPercent = taxPercentage;
        item.taxAmount = formatPrice(taxAmount);
        item.baseTaxAmount = formatPrice(taxAmount);
        item.rowTotal = formatPrice(rowTotal);
        item.baseRowTotal = formatPrice(rowTotal);
        item.priceInclTax = formatPrice(finalPrice);
        item.basePriceInclTax = formatPrice(finalPrice);
        item.rowTotalInclTax = formatPrice(finalPrice * item.qty);
        item.baseRowTotalInclTax = formatPrice(finalPrice * item.qty);
        item.discountPercent = discountPercent;
        item.discountAmount = formatPrice(rowTotalDiscountAmount);
        item.baseDiscountAmount = formatPrice(rowTotalDiscountAmount);
        item.discountTaxCompensationAmount = formatPrice(discountTaxCompensationAmount);
        item.baseDiscountTaxCompensationAmount = formatPrice(discountTaxCompensationAmount);

        const formattedFinalPrice = formatPrice(finalPrice);
        subtotal += formattedFinalPrice * (item.qty || 0);

        // subtotal += Math.trunc((finalPrice * item.qty || 0) * 100) / 100;
        // subtotal += Number((finalPrice * item.qty || 0).toFixed(2));
      
        rowTotalDiscountAmountForQuote += rowTotalDiscountAmount;

    }

    quote.couponDiscount = null;
    quote.couponCode = null;

    quote.autoPromoCode = null;
    quote.autoPromoAmount = null;

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

    quote.codCharges = formatPrice((quote.quotePayment?.method == "cashondelivery") ? codCharges : 0);

    subTotalWithDiscount = formatPrice(subtotal - rowTotalDiscountAmountForQuote);

    quote.subtotal = formatPrice(subtotal);
    quote.baseSubtotal = formatPrice(subtotal);
    quote.subtotalWithDiscount = subTotalWithDiscount;
    quote.baseSubtotalWithDiscount = subTotalWithDiscount;

    quote.calcSource = "native";

    if (upsert) await upsertQuote({ storeId, quote, collection, xHeaderToken });


    return quote;
}
