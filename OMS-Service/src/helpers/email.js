/* eslint-disable max-lines-per-function */
const moment = require('moment');
const Handlebars = require('handlebars');
const fs = require('fs');
const axios = require('axios');
const { sendSgEmail } = require('../services/email.service');
const templateMap = getTemplate();
const { ORDER_DETAIL_ENDPOINT } = require('../constants/javaEndpoints');
const { getNumericValue, getPercentage, maskPhoneNumber } = require('../utils');
const { getShipmentIncId } = require('./forwardShipment');
const { STORE_LANG_MAP } = require('../constants');
const { AUTH_INTERNAL_HEADER_BEARER_TOKEN } = process.env;
const internalAuthToken = AUTH_INTERNAL_HEADER_BEARER_TOKEN?.split(',')?.[0];
const { getStoreLink, getWebsiteLink, getTrackUrl, getProductUrl, getEstDelivery } = require('./store');
const { isGlobal, isSplitOrderPattern } = require('../utils/splitOrderUtils');

const formatTotals = (totals, currency) => {
  totals.subtotal = `${currency} ${getNumericValue(totals.subtotal)}`;
  totals.subtotalInclTax = `${currency} ${getNumericValue(
    totals.subtotalInclTax
  )}`;
  // totals.shippingAmount = `${currency} ${getNumericValue(
  //   totals.shippingAmount
  // )}`;

  totals.shippingAmount = Number(totals.shippingAmount)
    ? `${currency} ${getNumericValue(totals.shippingAmount)}`
    : undefined;

  totals.codCharges = Number(totals.codCharges)
    ? `${currency} ${getNumericValue(totals.codCharges)}`
    : undefined;

  totals.importFeesAmount = Number(totals.importFeesAmount)
    ? `${currency} ${getNumericValue(totals.importFeesAmount)}`
    : undefined;

  const netDiscount = totals?.discountData?.reduce((net, el) => {
    net = net + Number(el.value);
    return net;
  }, 0);

  // totals.discountAmount = `${currency} ${getNumericValue(
  //   totals.discountAmount
  // )}`;
  totals.discountAmount = `${currency} ${getNumericValue(netDiscount)}`;

  totals.spendCoin =
    Number(totals.spendCoin) > 0 ? Number(totals.spendCoin) : undefined;

  totals.coinToCurrency =
    Number(totals.spendCoin) > 0 && totals.coinToCurrency
      ? `${currency} ${getNumericValue(totals.coinToCurrency)}`
      : undefined;

  // totals.taxAmount = `${currency} ${getNumericValue(totals.taxAmount)}`;
  totals.taxAmount = Number(totals.taxAmount)
    ? `${currency} ${getNumericValue(totals.taxAmount)}`
    : undefined;
  totals.grandTotal = `${currency} ${getNumericValue(totals.grandTotal)}`; 
};

exports.getOrderData = async ({ orderId, showSellerCancelled } = {}) => {
  const payload = { orderId };

  if (typeof showSellerCancelled !== 'undefined') {
    payload.showSellerCancelled = showSellerCancelled;
  }
  console.log(`getOrderData: Payload for orderId ${orderId}:`, JSON.stringify(payload));

  const result = await axios.post(
    ORDER_DETAIL_ENDPOINT,
    payload,
    {
      headers: {
        'authorization-token': internalAuthToken
      }
    }
  );
  
  return result?.data?.response || {};
};

exports.sendEmail= async ({ type, orderId,splitOrderData={}, incrementIdData = '' }) => {
  try {
    const emailConfig = global?.baseConfig?.emailConfig;
    const { fromEmail, fromName, paymentMethodsLabel = {} } = emailConfig;
    let shipmentIncId = '';

    const isSplitOrder = (incrementIdData && isSplitOrderPattern(incrementIdData)) || false;

    if(isSplitOrder && type === "shipped"){
      type = "shipped_split";
    }

    const showSellerCancelled = null;
    const orderData = await this.getOrderData({ orderId, showSellerCancelled });
    const {
      orderIncrementId,
      shippingAddress,
      paymentInformation,
      customerIp,
      products,
      totals,
      shippingDescription,
      storeId,
      email,
      estimatedDeliveryTime,
      orderIds,
      orders
    } = orderData;
    const { streetAddress, area, city, landMark } = shippingAddress;
    const language = STORE_LANG_MAP[Number(storeId)];
    let subject = language === 'ar' ? `!تم تأكيد طلبك مع ستايلي `: `Your Styli Order is Confirmed!`;
    let finalDiscount = 0;

    const addressLine2 = `${landMark ? landMark + ',' : ''} ${
      streetAddress ? streetAddress + ',' : ''
    }`;

    if(totals.shukranPhoneNumber){
      totals.shukranPhoneNumber = await maskPhoneNumber(totals.shukranPhoneNumber);
    }

    const currency = totals?.currency;
    if (type === 'shipped_split' && incrementIdData) {
      const totalDiscount = orderData?.products
        ?.filter(p => p.splitOrderIncrementId === incrementIdData)
        ?.reduce((sum, p) => {
          return sum + Number(parseFloat(p.discount).toFixed(2));
        }, 0);

      finalDiscount = totalDiscount.toFixed(2);
      const matchedOrder = orders.find(
        order => order.orderIncrementId === incrementIdData
      );

      if (matchedOrder) {
        newTotalValue = matchedOrder.totals;
        formatTotals(newTotalValue, currency);
      }
    }
    else {
      formatTotals(totals, currency);
    }

    const deliveryDateMap = (orderIds || []).reduce((acc, order) => {
      if (order?.incrementId) {
        acc[order.incrementId] = order.estimatedDeliveryDate;
      }
      return acc;
    }, {});

    const filteredProducts =
    type === 'shipped_split'
    ? products.filter(
        product => product?.splitOrderIncrementId === incrementIdData
      )
    : products;

    const formattedProducts = filteredProducts.map(product => {
      return {
        name: language === 'ar' ? product.nameAr : product.name,
        sku: product.sku,
        image: product.image,
        returnCategoryRestriction: product.returnCategoryRestriction ,
        size:product.size,
        originalPrice: `${currency} ${getNumericValue(product.originalPrice * product.qty)}`,
        rowTotalInclTax: `${currency} ${getNumericValue(
          product.rowTotalInclTax
        )}`,
        taxObjects: product.taxObjects.map(taxObj => {
          return {
            taxType: taxObj.taxType,
            taxAmount: `${currency} ${getNumericValue(taxObj.taxAmount)}`,
            taxPercentage: taxObj.taxPercentage
          };
        }),
        qty: Number(product.qty),
        baseSubtotal: `${currency} ${getNumericValue(product.baseSubtotal)}`,
        discount: `${currency} ${getNumericValue(product.discount)}`,
        discountPercentage : getPercentage(getNumericValue(product.originalPrice),getNumericValue(product.price)),
        brandName: product?.brandName,
        url: `${getProductUrl(storeId, [product])[0]}`,
        splitOrderIncrementId : product?.splitOrderIncrementId,
        isGlobalProduct : isGlobal(product?.splitOrderIncrementId),
        deliveryDate : deliveryDateMap[product?.splitOrderIncrementId] || null,
      };
    });

    const localDeliveryDate =
      formattedProducts.find(p => !p.isGlobalProduct)?.deliveryDate || null;

    const globalDeliveryDate =
      formattedProducts.find(p => p.isGlobalProduct)?.deliveryDate || null;

    const globalShipmentNumber =
      formattedProducts.find(p => p.isGlobalProduct)?.splitOrderIncrementId || null;

    const localShipmentNumber =
      formattedProducts.find(p => !p.isGlobalProduct)?.splitOrderIncrementId || null;

    console.log("sendEmail : ALL FORMATTED PRODUCTS FOR EMAIL:", JSON.stringify(formattedProducts));
    console.log("sendEmail : TOTALS FOR EMAIL:", JSON.stringify(totals));

    paymentInformation.paymentMethod =
      paymentMethodsLabel[paymentInformation.paymentMethod] ??
      paymentInformation.paymentMethod;

    if (type === 'shipped' || type === 'shipped_split') {
      console.log(`sendEmail: Processing 'shipped' email for orderId: ${orderId}`);
      shipmentIncId = await getShipmentIncId(orderId);
      subject = language === 'ar' ? `تم شحن طلبك من ستايلي`: `Your Styli Order has been shipped`;
    }
    console.log(`TEMPLATE VALUES FOR SPLIT ORDER ${orderId} AND template TYPE ${type}`)
    const html = this.prepareHtml({
      template: ((Number(storeId) === 15 || Number(storeId) === 17) && (type === 'orderConfirm')) ? `${type}_qatar_${language}` : `${type}_${language}`,
      data: {
        orderIncrementId,
        shippingAddress,
        addressLine2,
        addressLine3: `${area},${city}`,
        paymentInformation,
        estimatedDeliveryTime: getEstDelivery(estimatedDeliveryTime),
        customerIp,
        products: formattedProducts || [],
        totals :type === "shipped_split" ? newTotalValue : totals,
        shipmentIncId,
        shippingDescription,
        storeLink:getStoreLink(storeId),
        websiteLink: getWebsiteLink(storeId),
        trackingBaseUrl: getTrackUrl(storeId, orderId),
        localDeliveryDate:getEstDelivery(localDeliveryDate)|| null,
        globalDeliveryDate:getEstDelivery(globalDeliveryDate)|| null,
        globalShipmentNumber,
        localShipmentNumber,
        shippedDeliveryDate: getEstDelivery(formattedProducts[0].deliveryDate) || null,
        incrementIdData,
        finalDiscount
      }
    });

    await sendSgEmail({
      to: email,
      from: { email: fromEmail, name: fromName },
      subject,
      html
    });
  } catch (e) {
    global.logError(`${type} Email Error OrderId : ${orderId}`, e);
  }
};

/**
 *
 * @param {*} param0
 * @return {string}
 */
exports.prepareHtml = ({ template, data })=> {
  const templatePath = templateMap[template];

  const html = fs.readFileSync(templatePath, 'utf8');
  const htmlTemplate = Handlebars.compile(html);
  return htmlTemplate(data);
}

Handlebars.registerHelper('gt', function(a, b) {
  return Number(a) > Number(b);
});

Handlebars.registerHelper('mul', function(a, b) {
  return (Number(a) * Number(b)).toFixed(2); 
});

Handlebars.registerHelper('floor', function(value) {
  return Math.floor(value);
});

Handlebars.registerHelper('eq', function (a, b) {
  return a === b;
});
Handlebars.registerHelper('toFixed', function(value, precision) {
  return Number(value).toFixed(precision);
});

Handlebars.registerHelper('chunkString', function(value, size, delimeter = '-') {
  const splitted = splitStringByLength(value, size);
  return splitted.join(delimeter);
});
function splitStringByLength(str, chunkSize) {
  const result = [];
  for (let i = 0; i < str.length; i += chunkSize) {
    result.push(str.slice(i, i + chunkSize));
  }
  return result;
}
/**
 *
 * @param {*} param0
 * @return {string}
 */
function getTemplate() {
  if (process.env?.REGION?.toUpperCase() === 'IN') {
    return {
      orderConfirm_en: './src/templates/in/orderConfirmationEn.html',
      shipped_en: './src/templates/in/shippingEn.html',
      orderConfirm_ar: './src/templates/in/orderConfirmationAr.html',
      shipped_ar: './src/templates/in/shippingAr.html'
    };
  }
  return {
    orderConfirm_en: './src/templates/orderConfirmationEn.html',
    orderConfirm_qatar_en: './src/templates/orderConfirmationQatarEn.html',
    orderConfirm_split_en: './src/templates/orderConfirmationSplitEn.html',
    orderConfirm_split_ar: './src/templates/orderConfirmationSplitAr.html',
    shipped_en: './src/templates/shippingEn.html',
    shipped_split_en: './src/templates/shippingSplitEn.html',
    shipped_split_ar: './src/templates/shippingSplitAr.html',
    orderConfirm_ar: './src/templates/orderConfirmationAr.html',
    orderConfirm_qatar_ar: './src/templates/orderConfirmationQatarAr.html',
    shipped_ar: './src/templates/shippingAr.html',
    order_prepaid_cancel_en : './src/templates/orderCancellationEn.html',
    order_prepaid_cancel_split_order_split_en : './src/templates/orderCancellationSplitEn.html',
    order_prepaid_cancel_split_order_split_ar : './src/templates/orderCancellationSplitAr.html',
    order_cod_cancel_en: './src/templates/orderCancellationEn.html',
    order_cod_cancel_split_order_en: './src/templates/orderCancellationEn.html',
    order_prepaid_cancel_ar : './src/templates/orderCancellationAr.html',
    order_cod_cancel_ar: './src/templates/orderCancellationAr.html',
    order_cod_cancel_split_order_ar: './src/templates/orderCancellationAr.html',
    delivered_split_en:"./src/templates/deliveredSplitEn.html",
    delivered_split_ar:"./src/templates/deliveredSplitAr.html",
    delivered_en:"./src/templates/deliveredEn.html",
    delivered_ar:"./src/templates/deliveredAr.html",
    returnRequest_en: './src/templates/returnRequestEn.html',
    returnRequest_ar: './src/templates/returnRequestAr.html',
    return_awb_create_ar: './src/templates/awbBillReadyAr.html',
    refund_completed_online_ar: './src/templates/refundOnTheWayAr.html',
    return_awb_create_en: './src/templates/awbBillReadyEn.html',
    refund_completed_online_en: './src/templates/refundOnTheWayEn.html',
    default_en:'./src/templates/smsDefaultEn.html',
    default_ar:'./src/templates/smsDefaultAr.html',
    refund_completed_cod_en: './src/templates/refundOnTheWayEn.html',
    refund_completed_cod_ar:'./src/templates/refundOnTheWayAr.html',
    pickup_failed_en:'./src/templates/pickupFailedEn.html',
    pickup_failed_ar:'./src/templates/pickupFailedAr.html'
  };
}