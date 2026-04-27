/* eslint-disable max-lines-per-function */
const { sequelize } = require('../models/seqModels/index');
const { QueryTypes } = require('sequelize');
const { getNumericValue, logInfo } = require('../utils');
const { paymentMethodsLabels } = require('./order');
const { insertOne } = require('../utils/mongo');
const mongoUtil = require('../utils/mongoInit');
const INVOICE_ADDRESS_LITERAL = 'invoice address';
const moment = require('moment');
const _ = require('lodash');
const { getStoreWiseHeadings } = require('./invoiceHeadings');
const { getGstInPan, getStateCode } = require('./tax');
const dayjs = require('dayjs');
const numWords = require('num-words');
const { getStoreConfigs } = require('../utils/config');
const fs = require('fs');

/* const getFormattedPrice = (currency, value) => {
  // return value && Number(value) ? `${currency} ${getNumericValue(value)}` : 0;
  return value && Number(value) ? `${getNumericValue(value)}` : 0;
}; */

const getFormattedPrice = (value, currency = false) => {
  if (Number(value)) {
    return `${getNumericValue(value)}`;
  }
  return currency ? `${currency} 0.00` : undefined;
};

const getTaxPercentage = value => {
  if (Number(value)) {
    return `${getNumericValue(value)}`;
  }
  return 0;
};

const getExcludingVatData = ({ invoiceData, apiResponse }) => {
  const { storeId } = apiResponse || {};
  const { apiTotals } = invoiceData;
  const {
    subtotalInclTax,
    discountAmount,
    shippingAmount = 0,
    codCharges = 0,
    taxAmount,
    currency,
    storeCreditAmount,
    taxPercent,
    coinToCurrency = 0
  } = apiTotals;

  const configValue = getStoreConfigs({
    key: 'currencyConversionRate',
    storeId
  });
  let factor = 1;
  if (configValue.length) {
    factor = configValue[0].currencyConversionRate;
  }

  invoiceData.totals.payableAmount = invoiceData.totals.grandTotal;
  invoiceData.totals.basePayableAmount = getNumericValue(
    invoiceData.totals.grandTotal * factor
  );
  let grandTotalExcludingVat = null;
  if (['1', '3'].includes(storeId)) {
    let shippingAmountTax =
      parseFloat(Number(shippingAmount) / (100 + Number(taxPercent))).toFixed(
        4
      ) * Number(taxPercent);
    shippingAmountTax = parseFloat(
      Math.round(shippingAmountTax * 100) / 100
    ).toFixed(2);
    let codChargeTax =
      parseFloat(Number(codCharges) / (100 + Number(taxPercent))).toFixed(4) *
      Number(taxPercent);
    codChargeTax = parseFloat(Math.round(codChargeTax * 100) / 100).toFixed(2);

    const grandTotalIncludingTax =
      Number(subtotalInclTax) -
      Number(discountAmount) +
      Number(shippingAmount) +
      Number(codCharges);

    const taxValue =
      Number(taxAmount) + Number(shippingAmountTax) + Number(codChargeTax);

    grandTotalExcludingVat = grandTotalIncludingTax - taxValue;

    invoiceData.totals.taxAmount = getFormattedPrice(taxValue, currency);

    invoiceData.grandTotalExcludingVat = getFormattedPrice(
      grandTotalExcludingVat,
      currency
    );

    const payableAmount =
      grandTotalIncludingTax - storeCreditAmount - Number(coinToCurrency);
    invoiceData.totals.payableAmount = getFormattedPrice(
      payableAmount > 0 ? payableAmount : 0,
      currency
    );
    invoiceData.totals.grandTotal = getFormattedPrice(
      grandTotalIncludingTax,
      currency
    );
  }
};

/**
 * Java invoice payload can repeat the same SKU (split / merged lines). Use for cod RTO credit
 * memo PDF only so normal invoices stay unchanged.
 */
const dedupeInvoiceProductsBySku = products => {
  const bySku = new Map();
  for (const item of products || []) {
    const sku = item?.sku;
    if (!sku) continue;
    const existing = bySku.get(sku);
    if (!existing) {
      bySku.set(sku, item);
      continue;
    }
    const newQty = parseFloat(item.qty) || 0;
    const existingQty = parseFloat(existing.qty) || 0;
    if (newQty > existingQty) bySku.set(sku, item);
  }
  const kept = new Set(bySku.values());
  return (products || []).filter(i => !i.sku || kept.has(i));
};

const prepareProducts = (currency, products, showTax) => {
  let finalProducts = [];
  (products || []).forEach(el => {
    el.price = `${getNumericValue(el.price)}`;
    el.qty = Number(el.qty);
    el.rowTotalInclTax = Number(el.rowTotalInclTax)
      ? `${getNumericValue(el.rowTotalInclTax)}`
      : '0.00';
    el.taxAmount = Number(el.taxAmount)
      ? `${getNumericValue(el.taxAmount)}`
      : '0.00';
    el.discount = Number(el.discount)
      ? `${getNumericValue(el.discount)}`
      : '0.00';
    el.finalPrice = Number(el.finalPrice)
      ? `${getNumericValue(el.finalPrice)}`
      : '0.00';
    el.showTax = showTax;
    el.taxObjects = prepareTaxObject(el.taxObjects);

    const totalExclTax = Number(el.finalPrice) - Number(el.taxAmount);
    el.rowTotalExclTax = Number(totalExclTax)
      ? `${getNumericValue(totalExclTax)}`
      : '0.00';

    el.taxPercentage = Number(el.taxPercentage)
      ? `${getNumericValue(el.taxPercentage)}`
      : '0.00';
    el.unitPriceExclTax = Number(el.unitPriceExclTax)
      ? `${getNumericValue(el.unitPriceExclTax)}`
      : '0.00';
    el.subTotalExclTax = Number(el.subTotalExclTax)
      ? `${getNumericValue(el.subTotalExclTax)}`
      : '0.00';
    el.totalDiscountExclTaxProduct = Number(el.totalDiscountExclTaxProduct)
      ? `${getNumericValue(el.totalDiscountExclTaxProduct)}`
      : '0.00';
    el.taxablePriceProduct = Number(el.taxablePriceProduct)
      ? `${getNumericValue(el.taxablePriceProduct)}`
      : '0.00';
    el.totalTaxAmountProduct = Number(el.totalTaxAmountProduct)
      ? `${getNumericValue(el.totalTaxAmountProduct)}`
      : '0.00';
    el.totalPriceInclTaxProduct = Number(el.totalPriceInclTaxProduct)
      ? `${getNumericValue(el.totalPriceInclTaxProduct)}`
      : '0.00';
  });

  finalProducts = products;

  if (products.length > 8) {
    const productsFirstBlock = products.slice(0, 9);
    const productsLastBlock = products.slice(9, products.length);
    finalProducts = [...productsFirstBlock, ...productsLastBlock];
  }

  return finalProducts;
};

const prepareTaxObject = taxObjects => {
  if (taxObjects) {
    return ['IGST', 'CGST', 'SGST'].map(taxKey => {
      const taxObj = taxObjects.find(v => v.taxType === taxKey);
      return {
        taxType: taxObj?.taxType,
        taxAmount: getNumericValue(taxObj?.taxAmount),
        taxPercentage: getNumericValue(taxObj?.taxPercentage)
      };
    });
  }
  return [];
};

function formatTotals(totals, currency, configTax) {
  totals.discountAmount = getFormattedPrice(totals.discountAmount, currency);
  totals.taxAmount = getFormattedPrice(totals.taxAmount, currency);
  totals.codCharges = getFormattedPrice(totals.codCharges, currency);
  totals.donationAmount = getFormattedPrice(totals.donationAmount, currency);
  totals.importFeesAmount = getFormattedPrice(
    totals.importFeesAmount,
    currency
  );
  totals.subtotal = getFormattedPrice(totals.subtotal, currency);
  totals.baseSubtotal = getFormattedPrice(totals.baseSubtotal, currency);
  totals.subtotalInclTax = getFormattedPrice(totals.subtotalInclTax, currency);
  totals.baseSubtotalInclTax = getFormattedPrice(
    totals.baseSubtotalInclTax,
    currency
  );
  totals.taxPercent = getTaxPercentage(configTax);
  totals.shippingAmount = getFormattedPrice(totals.shippingAmount, currency);
  totals.grandTotal = getFormattedPrice(totals.grandTotal, currency);
  totals.coinToCurrency = getFormattedPrice(totals.coinToCurrency, currency);
}

function prepareInvoicedAmount(
  totals,
  apiTotals,
  currency,
  factor,
  zatcaStatus
) {
  if (zatcaStatus) {
    totals.totalPriceExclTax = getFormattedPrice(
      totals.totalPriceExclTax,
      currency
    );
    totals.totalBasePriceExclTax = getFormattedPrice(
      totals.totalBasePriceExclTax,
      currency
    );
    totals.roundingAmount = getFormattedPrice(totals.roundingAmount);
    totals.totalTaxableAmount = getFormattedPrice(totals.totalTaxableAmount);
    totals.invoicedAmount = getFormattedPrice(totals.invoicedAmount);
    totals.baseInvoicedAmount = getFormattedPrice(
      totals.invoicedAmount * factor,
      currency
    );
    totals.isCOD = Number(apiTotals?.codCharges) > 0;
    totals.isShipping = Number(apiTotals?.shippingAmount) > 0;
    totals.isImportFee = Number(apiTotals?.importFeesAmount) > 0;
    totals.isDonation = Number(apiTotals?.donationAmount) > 0;
    totals.isRounding = Number(apiTotals?.roundingAmount) !== 0;
    totals.codCharges = getFormattedPrice(apiTotals?.codCharges);
    totals.codTaxCharges = getFormattedPrice(apiTotals?.codTaxCharges) || 0;
    totals.codChargesTotal =
      Number(apiTotals?.codCharges) + Number(apiTotals?.codTaxCharges);
    totals.shippingAmount = getFormattedPrice(apiTotals?.shippingAmount);
    totals.shippingTaxAmount =
      getFormattedPrice(apiTotals?.shippingTaxAmount) || 0;
    totals.shippingAmountTotal =
      Number(apiTotals?.shippingAmount) + Number(apiTotals?.shippingTaxAmount);
    totals.importFeesAmount = getFormattedPrice(apiTotals?.importFeesAmount);
    totals.donationAmount = getFormattedPrice(apiTotals?.donationAmount);
    totals.giftVoucherAmount = getFormattedPrice(apiTotals?.giftVoucherAmount);
    totals.storeCreditAmount = getFormattedPrice(totals?.storeCreditAmount);
    totals.giftVoucherAmount = getFormattedPrice(totals?.giftVoucherAmount);
  } else {
    const invoicedAmount =
      Number(totals.grandTotal) +
      Number(totals.storeCreditAmount) +
      Number(totals?.coinToCurrency);

    totals.invoicedAmount = getFormattedPrice(invoicedAmount, currency);
    totals.giftVoucherAmount = getFormattedPrice(
      totals?.giftVoucherAmount,
      currency
    );
    totals.storeCreditAmount = getFormattedPrice(
      totals.storeCreditAmount,
      currency
    );
    totals.baseInvoicedAmount = getFormattedPrice(
      invoicedAmount * factor,
      currency
    );
  }
}

function applyIndianRegionAdjustments(totals, currency) {
  totals.totalTaxAmount =
    Number(totals.taxAmount) +
    Number(totals.codTaxCharges) +
    Number(totals.shippingTaxAmount);
  totals.totalTaxAmount = getFormattedPrice(totals.totalTaxAmount, currency);

  totals.codCharges = getFormattedPrice(
    Number(totals.codCharges) - Number(totals.codTaxCharges),
    currency
  );

  totals.shippingAmount = getFormattedPrice(
    Number(totals.shippingAmount) - Number(totals.shippingTaxAmount),
    currency
  );

  totals.shippingTaxAmount = getFormattedPrice(
    totals.shippingTaxAmount,
    currency
  );
  totals.codTaxCharges = getFormattedPrice(totals.codTaxCharges, currency);

  const invoicedAmountArray = totals.invoicedAmount.split('.');
  totals.invoicedAmountInWords = numWords(Number(invoicedAmountArray[0]));

  if (invoicedAmountArray[1]) {
    if (Number(invoicedAmountArray[1])) {
      totals.invoicedAmountInWords =
        totals.invoicedAmountInWords +
        ' Point ' +
        numWords(Number(invoicedAmountArray[1]));
    }
  }
}

exports.prepareInvoice = async ({ apiResponse, showTax = true, configTax, splitOrderId }) => {
  let invoiceData = {};
  let {
    orders,
    orderIncrementId,
    orderCreatedAt,
    totals,
    paymentInformation,
    storeId,
    zatcaStatus = null
  } = apiResponse || {};

  let splitOrder;
  if (splitOrderId) {
    splitOrder = orders.find(order => order.orderId === +splitOrderId);
    orderIncrementId = splitOrder.orderIncrementId;
    totals = splitOrder.totals;
  }
  const apiTotals = { ...totals };
  const { ccNumber, ccType } = paymentInformation;
  const { currency } = totals;

  const invoicedAmount =
    Number(totals.grandTotal) +
    Number(totals.storeCreditAmount) +
    Number(totals?.coinToCurrency);

  const subtotalExclTax =
    Number(invoicedAmount) -
    Number(totals?.taxAmount) -
    Number(totals?.shippingAmount) -
    Number(totals?.codCharges) -
    Number(totals?.importFeesAmount) -
    Number(totals?.donationAmount);

  totals.subtotalExclTax = getFormattedPrice(subtotalExclTax, currency);
  formatTotals(totals, currency, configTax);

  const configValue = getStoreConfigs({
    key: 'currencyConversionRate',
    storeId
  });

  let factor = 1;
  if (configValue.length) {
    factor = configValue[0].currencyConversionRate;
  }

  prepareInvoicedAmount(totals, apiTotals, currency, factor, zatcaStatus);

  totals.invoicedAmountInWords = '';
  totals.totalTaxAmount = totals.taxAmount;

  if (process.env?.REGION?.toUpperCase() === 'IN') {
    applyIndianRegionAdjustments(totals, currency);
  }

  invoiceData = apiResponse;
  invoiceData.apiTotals = apiTotals;
  invoiceData.apiTotals.taxPercent = getTaxPercentage(configTax);
  invoiceData.donationTexts = global?.baseConfig?.donationConfig?.text;
  invoiceData.donationTextsExclTax =
    global?.baseConfig?.donationConfigExclTax?.text;
  invoiceData.paymentModeEn =
    paymentMethodsLabels[invoiceData.paymentInformation?.paymentMethod]?.en;
  invoiceData.paymentModeAr =
    paymentMethodsLabels[invoiceData.paymentInformation?.paymentMethod]?.ar;

  if (!invoiceData.paymentInformation?.paymentMethod.includes('tabby')) {
    if (ccType) invoiceData.ccType = ccType;
    if (ccNumber) invoiceData.ccNumber = ccNumber.slice(ccNumber.length - 6);
  }

  if (invoiceData.paymentInformation?.paymentMethod.includes('cashfree')) {
    invoiceData.ccNumber = '';
  }

  invoiceData.orderIncrementId = orderIncrementId || 'N/A';
  invoiceData.orderCreatedAt = orderCreatedAt;

  invoiceData.products = prepareProducts(
    currency,
    splitOrder ? splitOrder.products : invoiceData.products,
    showTax
  );

  const { addressEn, addressAr } = this.getCompanyAddress(
    invoiceData.warehouseId
  );
  invoiceData.companyAddressEn = addressEn;
  invoiceData.companyAddressAr = addressAr;

  if (process.env?.REGION?.toUpperCase() === 'IN') {
    const { gstIN, panNo } = getGstInPan(invoiceData.warehouseId);
    invoiceData.gstIN = gstIN;
    invoiceData.panNo = panNo;
    invoiceData.shippingAddress.stateCode = getStateCode(
      invoiceData.shippingAddress.region
    );
    invoiceData.warehouseIdStateCode = global?.taxConfig?.warehouseIdStateCode;
    invoiceData.warehouseState = global?.taxConfig?.warehouseState;
  }

  invoiceData.totals = { ...totals };
  invoiceData.showTax = showTax;
  invoiceData.eDD = dayjs(invoiceData.estimatedDeliveryTime).format(
    'DD-MMM-YYYY'
  );
  invoiceData.eDDAr = dayjs(invoiceData.estimatedDeliveryTime).format(
    'MMM-DD-YYYY'
  );

  if (!zatcaStatus) {
    getExcludingVatData({ invoiceData, apiResponse });
  }

  invoiceData.currencyConversionRate = factor;
  invoiceData.headers = getStoreWiseHeadings(invoiceData);

  return invoiceData;
};

exports.dedupeInvoiceProductsBySku = dedupeInvoiceProductsBySku;

exports.getCompanyAddress = warehouseId => {
  let addressEn = [
    'Retail Cart Trading Company Sole Person Company',
    'Riyadh Gallery Mall',
    'King Fahad Road',
    'P.O. Box 86003 Riyadh – 11622,',
    'KSA VAT Registration No: 310415422600003',
    'CRN: 1010589431'
  ];
  let addressAr = [
    'شركة ريتيل كارت للتجارة شركة شخص واحد',
    'مجمع رياض جاليري ',
    'طريق الملك فهد',
    'ص.ب 86003 ',
    'الرياض – 11622، المملكة العربية السعودية',
    'رقم التسجيل ضريبة القيمة المضافة : 310415422600003',
    '1010589431: CRN'
  ];

  if (warehouseId) {
    const mapper = global.javaOrderServiceConfig?.inventory_mapping || [];
    if (mapper.length) {
      const match = mapper.find(
        el => Number(el.warehouse_id) == Number(warehouseId)
      );
      if (match?.INVOICE_ADDRESS_EN) addressEn = match?.INVOICE_ADDRESS_EN;
      else logInfo(INVOICE_ADDRESS_LITERAL, 'INVOICE_ADDRESS_EN not found!');
      if (match?.INVOICE_ADDRESS_AR) addressAr = match?.INVOICE_ADDRESS_AR;
      else logInfo(INVOICE_ADDRESS_LITERAL, 'INVOICE_ADDRESS_AR not found!');
    }
  } else {
    logInfo(INVOICE_ADDRESS_LITERAL, 'warehouseId not found!');
  }
  return { addressEn, addressAr };
};

exports.findOrders = async payload => {
  const { fromDate, toDate, country } = payload;

  const appConfig = global.config;
  const storesData = appConfig?.environments?.[0]?.stores;
  const storeId = storesData
    ?.filter(st => _.includes(country, st.websiteCode))
    .map(st => _.toUpper(st.storeId));

  try {
    const orders = await sequelize.query(
      `SELECT si.order_id,sa.customer_email FROM sales_invoice si INNER JOIN sales_order sa ON si.order_id = sa.entity_id
        WHERE si.created_at BETWEEN ? AND ? AND sa.store_id IN (?)`,
      {
        replacements: [fromDate, toDate, storeId],
        type: QueryTypes.SELECT
      }
    );
    return orders?.map(order => {
      return { orderId: order.order_id, customerEmail: order.customer_email };
    });
  } catch (error) {
    console.log('Error in finding orders. Error Details :', error);
  }
  return [];
};

exports.storeInvoice = async payload => {
  try {
    const ttl = global.baseConfig?.bulkInvoice?.mongoTtlInSeconds || 1296000;
    payload.created_at = new Date();
    payload.expire_at = moment(new Date()).add(ttl, 'seconds').toDate();
    await insertOne({
      collection: 'bulk_invoices',
      data: payload
    });
  } catch (error) {
    console.error('Error in Storing Invoice details. ', error);
  }
};

exports.findAllInvoice = async () => {
  try {
    const db = mongoUtil.getDb();
    return await db.collection('bulk_invoices').find().toArray();
  } catch (error) {
    console.error('Error in fetching Invoice details. ', error);
  }
  return [];
};

exports.getInvoiceTemplateSource = ({ zatcaStatus }) => {
  let templateSource = '';
  if (process.env?.REGION?.toUpperCase() === 'IN') {
    templateSource = fs.readFileSync(
      './src/templates/in/invoiceNew.html',
      'utf8'
    );
  } else {
    templateSource = zatcaStatus
      ? fs.readFileSync('./src/templates/invoiceZatca.html', 'utf8')
      : fs.readFileSync('./src/templates/invoiceNew.html', 'utf8');
  }
  return templateSource;
};

exports.getSecondReturnTemplateSource = () => {
  return fs.readFileSync(
    './src/templates/invoiceZatcaSecondReturn.html',
    'utf8'
  );
};

exports.getCodRtoCreditMemoTemplateSource = () => {
  return fs.readFileSync('./src/templates/codRtoCreditMemoZatca.html', 'utf8');
};