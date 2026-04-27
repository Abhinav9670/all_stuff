const { formatPrice, getTaxIndia } = require("./utils");
const { createClient } = require("redis");
const _ = require('lodash');
// const logger = require('./logger');

let client = null
if(process.env.REGION == "IN"){
  client = createClient({ url: process.env.REDIS_URL });
  // client.on("error", (err) => logger.error("Redis Client Error", err));
  client.connect();
}

exports.inTaxCalculator = async ({ taxGroups, hsnCode, priceInclTax }) => {
  try {
    if (!hsnCode) {
      return getTaxGroup({ taxGroup : taxGroups.tax, priceInclTax: priceInclTax });
    } else {
      const taxKey = await client.hGet("HSN", hsnCode);
      if (!taxKey) {
        return getTaxGroup({ taxGroup : taxGroups.tax, priceInclTax: priceInclTax });
      }
      if(!taxGroups[taxKey]){
        return getTaxGroup({ taxGroup : taxGroups.tax, priceInclTax: priceInclTax });
      }
      return getTaxGroup({ taxGroup : taxGroups[taxKey], priceInclTax: priceInclTax });
    }
  } catch (e) {
    // logger.error(e, "Redis error");
    return getTaxGroup({ taxGroup : taxGroups.tax, priceInclTax: priceInclTax });
  }
};

const getTaxGroup = ({ taxGroup,  priceInclTax}) => {
  if (taxGroup.type == "FIXED") {
    return taxGroup.taxPercentage;
  }
  if (taxGroup.type == "RANGE") {
    for (const key in taxGroup.taxPercentage) {
      const [min, max] = key.split("-");
      if (max == 0) {
        return taxGroup.taxPercentage[key];
      }
      if (Number(priceInclTax) > Number(min) && Number(priceInclTax) < Number(max)) {
        return taxGroup.taxPercentage[key];
      }
    }
  }
};

exports.isIntraState = ({ address }) => {
  // to be changed if possible
  const stateCode = getTaxIndia("stateCode");
  // logger.info(
  //   "isIntraState",
  //   stateCode?.[address?.[0]?.region],
  //   getTaxIndia("warehouseIdStateCode")
  // );
  if(!address?.[0]?.region || !stateCode){
    return false;
  }
  return stateCode?.[_.toUpper(address?.[0]?.region)] ==
    getTaxIndia("warehouseIdStateCode")
    ? false
    : true;
};

exports.inTaxOnProductPrice = async ({
  taxGroups,
  hsnCode,
  item,
  priceObj,
  isIntraStateBool,
}) => {
  try {
    const taxObj = await this.inTaxCalculator({
      taxGroups,
      hsnCode,
      priceInclTax: item.priceInclTax,
    });

    if (!isIntraStateBool) {
      const taxPercentageCGST = taxObj.CGST;
      const taxPercentageSGST = taxObj.SGST;
      const taxPercentageCGSTAmount =
        (
          (priceObj.priceInclTax /
            (100 + taxPercentageCGST + taxPercentageSGST)) *
          taxPercentageCGST
        ).toFixed(2) * item.qty;

      const taxPercentageSGSTAmount =
        (
          (priceObj.priceInclTax /
            (100 + taxPercentageCGST + taxPercentageSGST)) *
          taxPercentageSGST
        ).toFixed(2) * item.qty;

      let rowTotal =
        priceObj.priceInclTax * item.qty -
        taxPercentageCGSTAmount -
        taxPercentageSGSTAmount;
      const taxAmount = taxPercentageCGSTAmount + taxPercentageSGSTAmount;
      // const rowTotal = finalPriceExclTax * item.qty;
      return {
        price: formatPrice(finalPrice),
        basePrice: formatPrice(finalPrice),
        taxPercent: taxPercentageCGST + taxPercentageSGST,
        taxAmount: formatPrice(taxAmount || 0),
        baseTaxAmount: formatPrice(taxAmount || 0),
        taxObj: {
          taxIGST: 0,
          taxIGSTAmount: formatPrice(0),
          taxCGST: taxPercentageCGST,
          taxCGSTAmount: formatPrice(taxPercentageCGSTAmount || 0),
          taxSGST: taxPercentageSGST,
          taxSGSTAmount: formatPrice(taxPercentageSGSTAmount || 0),
          intraState: isIntraStateBool,
        },
        rowTotal: formatPrice(rowTotal),
        baseRowTotal: formatPrice(rowTotal),
      };
    } else {
      const taxPercentage = taxObj.IGST;
      let finalPriceExclTax = (
        (priceObj.priceInclTax / (100 + taxPercentage)) *
        100
      ).toFixed(2);
      const taxAmount = item.qty * (priceObj.priceInclTax - finalPriceExclTax);
      const rowTotal = finalPriceExclTax * item.qty;
      return {
        price: formatPrice(finalPrice),
        basePrice: formatPrice(finalPrice),
        taxPercent: taxPercentage,
        taxAmount: formatPrice(taxAmount),
        baseTaxAmount: formatPrice(taxAmount),
        taxObj: {
          taxIGST: taxPercentage,
          taxIGSTAmount: formatPrice(taxAmount),
          taxCGST: 0,
          taxCGSTAmount: formatPrice(0),
          taxSGST: 0,
          taxSGSTAmount: formatPrice(0),
          intraState: isIntraStateBool,
        },
        rowTotal: formatPrice(rowTotal),
        baseRowTotal: formatPrice(rowTotal),
      };
    }
  } catch (e) {
    // logger.error(e, "Error Tax ");
    // console.log('Error fetching promo response : ', e.message);
  }
};
