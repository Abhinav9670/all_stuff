const { getNumericValue } = require("../utils");

/**
 * Credit memo lines carry actual refunded amounts (row_total_incl_tax, tax_amount). When there is
 * no order line or order line has tax_percent 0, the catalog fallback used price_incl_tax × qty
 * and dropped tax — use memo amounts so getZatcaTotals matches grand_total.
 */
const buildZatcaFromMemoLine = (itemObject, Qty) => {
  const rowIncl = Number(itemObject.row_total_incl_tax) || 0;
  if (rowIncl <= 0 || !Qty || Qty <= 0) return null;

  const rowTax = Number(itemObject.tax_amount) || 0;
  let rowExcl = Number(itemObject.row_total);
  if (!Number.isFinite(rowExcl) || rowExcl < 0) {
    rowExcl = roundingTo2Decimal(rowIncl - rowTax);
  }
  const taxPercent =
    rowExcl > 0 ? roundingTo2Decimal((rowTax / rowExcl) * 100) : 0;
  const unitPriceInclTax = roundingTo2Decimal(rowIncl / Qty);
  const unitPriceExclTax = roundingTo2Decimal(rowExcl / Qty);

  return {
    taxPercent,
    unitPriceInclTax,
    unitPriceExclTax,
    subTotalExclTax: rowExcl,
    discountProductLevelExclTax: 0,
    discountCouponExclTaxProduct: 0,
    discountExclTaxProduct: 0,
    taxablePriceProduct: rowExcl,
    totalTaxAmountProduct: rowTax,
    totalPriceInclTaxProduct: rowIncl
  };
};

const shouldUseMemoLineForZatca = (itemObject, orderItem, Qty) => {
  const rowIncl = Number(itemObject.row_total_incl_tax) || 0;
  if (rowIncl <= 0 || !Qty || Qty <= 0) return false;
  if (!orderItem) return true;
  return Number(orderItem.tax_percent) === 0;
};

const formatZatcaItemDetails = m => ({
  taxPercent: getFormattedPrice(m.taxPercent),
  unitPriceInclTax: getFormattedPrice(m.unitPriceInclTax),
  unitPriceExclTax: getFormattedPrice(m.unitPriceExclTax),
  discountProductLevelExclTax: getFormattedPrice(m.discountProductLevelExclTax),
  discountCouponExclTaxProduct: getFormattedPrice(m.discountCouponExclTaxProduct),
  discountExclTaxProduct: getFormattedPrice(m.discountExclTaxProduct),
  taxablePriceProduct: getFormattedPrice(m.taxablePriceProduct),
  totalTaxAmountProduct: getFormattedPrice(Number(m.totalTaxAmountProduct)),
  totalPriceInclTaxProduct: getFormattedPrice(m.totalPriceInclTaxProduct),
  subTotalExclTax: getFormattedPrice(m.subTotalExclTax)
});

const getZatcaItemDetails = ({ itemObject, orderItem, status, paymentMethod }) => {
  const Qty = Number(itemObject.qty);

  if (shouldUseMemoLineForZatca(itemObject, orderItem, Qty)) {
    const fromMemo = buildZatcaFromMemoLine(itemObject, Qty);
    if (fromMemo) return formatZatcaItemDetails(fromMemo);
  }

  if (!orderItem) {
    const taxPercent = 0;
    const unitPriceInclTax = roundingTo2Decimal(Number(itemObject.price_incl_tax) || 0);
    const unitPriceExclTax = unitPriceInclTax;
    const subTotalExclTax = unitPriceExclTax * Qty;
    const discountProductLevelExclTax = 0;
    const discountCouponExclTaxProduct = 0;
    const discountExclTaxProduct = 0;
    const taxablePriceProduct = subTotalExclTax;
    const totalTaxAmountProduct = 0;
    const totalPriceInclTaxProduct = taxablePriceProduct;
    return {
      taxPercent: getFormattedPrice(taxPercent),
      unitPriceInclTax: getFormattedPrice(unitPriceInclTax),
      unitPriceExclTax: getFormattedPrice(unitPriceExclTax),
      discountProductLevelExclTax: getFormattedPrice(discountProductLevelExclTax),
      discountCouponExclTaxProduct: getFormattedPrice(discountCouponExclTaxProduct),
      discountExclTaxProduct: getFormattedPrice(discountExclTaxProduct),
      taxablePriceProduct: getFormattedPrice(taxablePriceProduct),
      totalTaxAmountProduct: getFormattedPrice(totalTaxAmountProduct),
      totalPriceInclTaxProduct: getFormattedPrice(totalPriceInclTaxProduct),
      subTotalExclTax: getFormattedPrice(subTotalExclTax)
    };
  }
  const taxFactor = getExclTaxfactor(orderItem.tax_percent);
  const unitPriceInclTax = roundingTo2Decimal(orderItem.original_price);
  const unitPriceExclTax = roundingTo2Decimal(
    orderItem.original_price / taxFactor
  );
  const subTotalExclTax = unitPriceExclTax * Qty;
  let discountProductLevelExclTax = roundingTo2Decimal(
    ((unitPriceInclTax - Number(itemObject.price_incl_tax)) * Qty) / taxFactor
  );
  const tempPriceAftProdLvlDisExclTax =
    roundingTo2Decimal(Number(itemObject.price_incl_tax) / taxFactor) * Qty;
  const prodRounding = roundingTo2Decimal(
    unitPriceExclTax * Qty -
      discountProductLevelExclTax -
      tempPriceAftProdLvlDisExclTax
  );
  if (prodRounding != 0) {
    discountProductLevelExclTax = discountProductLevelExclTax + prodRounding;
  }

  let discountCouponExclTaxProduct = roundingTo2Decimal(
    (Number(itemObject.discount_amount) - Number(itemObject.voucher_amount)) /
      taxFactor
  );


  let discountExclTaxProduct =
    discountCouponExclTaxProduct + discountProductLevelExclTax;
  if(status?.toUpperCase().includes('RTO') && paymentMethod?.toUpperCase() !== "CASHONDELIVERY"){
    discountCouponExclTaxProduct = 0;
    
    discountProductLevelExclTax= itemObject.discount_amount ? roundingTo2Decimal(Number(itemObject.discount_amount)): 0;
    discountExclTaxProduct = discountProductLevelExclTax;
  }

  

  const taxablePriceProduct = subTotalExclTax - discountExclTaxProduct;
  const totalTaxAmountProduct = roundingTo2Decimal(
    (taxablePriceProduct * Number(orderItem.tax_percent)) / 100
  );
  const totalPriceInclTaxProduct = taxablePriceProduct + totalTaxAmountProduct;
  return {
    taxPercent: getFormattedPrice(Number(orderItem.tax_percent)),
    unitPriceInclTax: getFormattedPrice(unitPriceInclTax),
    unitPriceExclTax: getFormattedPrice(unitPriceExclTax),
    discountProductLevelExclTax: getFormattedPrice(discountProductLevelExclTax),
    discountCouponExclTaxProduct: getFormattedPrice(
      discountCouponExclTaxProduct
    ),
    discountExclTaxProduct: getFormattedPrice(discountExclTaxProduct),
    taxablePriceProduct: getFormattedPrice(taxablePriceProduct),
    totalTaxAmountProduct: getFormattedPrice(Number(totalTaxAmountProduct)),
    totalPriceInclTaxProduct: getFormattedPrice(totalPriceInclTaxProduct),
    subTotalExclTax: getFormattedPrice(subTotalExclTax)
  };
};

const getZatcaTotals = items => {
  const taxPercent = Number(items?.[0]?.taxPercent || 0);
  let zatcaSubtotalExclTax = 0;
  let zatcaProductLevelDiscountExclTax = 0;
  let zatcaCouponDiscountExclTax = 0;
  let zatcaTaxablePrice = 0;
  for (const item of items) {
    zatcaSubtotalExclTax = zatcaSubtotalExclTax + Number(item.subTotalExclTax);
    zatcaProductLevelDiscountExclTax =
      zatcaProductLevelDiscountExclTax +
      Number(item.discountProductLevelExclTax);
    zatcaCouponDiscountExclTax =
      zatcaCouponDiscountExclTax + Number(item.discountCouponExclTaxProduct);
    zatcaTaxablePrice = zatcaTaxablePrice + Number(item.taxablePriceProduct);
  }
  zatcaTaxablePrice = roundingTo2Decimal(zatcaTaxablePrice);
  const sumLineTax = (items || []).reduce(
    (s, item) => s + (Number(item.totalTaxAmountProduct) || 0),
    0
  );
  const zatcaTaxAmount =
    taxPercent > 0
      ? roundingTo2Decimal((zatcaTaxablePrice / 100) * taxPercent)
      : roundingTo2Decimal(sumLineTax);
  const zatcaTotalInclTax = zatcaTaxablePrice + zatcaTaxAmount;
  return {
    zatcaSubtotalExclTax: roundingTo2Decimal(zatcaSubtotalExclTax),
    zatcaProductLevelDiscountExclTax: roundingTo2Decimal(
      zatcaProductLevelDiscountExclTax
    ),
    zatcaCouponDiscountExclTax: roundingTo2Decimal(zatcaCouponDiscountExclTax),
    zatcaTaxablePrice: roundingTo2Decimal(zatcaTaxablePrice),
    zatcaTaxAmount: roundingTo2Decimal(zatcaTaxAmount),
    zatcaTotalInclTax: roundingTo2Decimal(zatcaTotalInclTax)
  };
};

const getExclTaxfactor = tax => {
  return (100 + Number(tax)) / 100;
};

const getFormattedPrice = (value, currency = false) => {
  if (Number(value)) {
    return `${getNumericValue(roundingTo2Decimal(Number(value)))}`;
  }
  return currency ? `${currency} 0.00` : "0.00";
};

const roundingTo2Decimal = value => {
  return parseFloat(Math.round(value * 100) / 100);
};

module.exports = {
  getZatcaItemDetails,
  getZatcaTotals,
  roundingTo2Decimal
};
