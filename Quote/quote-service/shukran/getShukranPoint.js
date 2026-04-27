const {
  logError,
  logInfo,
  getStoreConfig,
  getAppConfigKey,
  getShukranPointConversion
} = require("../helpers/utils");
// const logger = require('../helpers/utils');
exports.processBurnShukranCoinCalculations = async ({
  quote,
  appliedShukranPoint,
  shukranAvailablePoint,
  shukranAvailableCashValue,
  storeId,
}) => {
  try {
    let appliedShukranCashValue = 0;
    let isAvailableShukranChanged = false;
    const currencyConversionRate = Number(
      getStoreConfig(storeId, "currencyConversionRate") || 1
    );
    const shukranPointConversion =
      getStoreConfig(storeId, "shukranPointConversion") || 0.05 ;
    // logger.info(`processBurnShukranCoinCalculations: Shukran point conversion rate: ${shukranPointConversion}`);
    
    let totalPrice = quote.quoteItem.reduce((sum, item) => {
      const rowTotalInclTax = item?.rowTotalInclTax || 0;
      const droppedPrice = item?.droppedPrice || 0;

      return (
        sum +
        (droppedPrice > 0 ? droppedPrice * item.qty : rowTotalInclTax) -
        item.discountAmount
      ); // Extract the price from the object
    }, 0);
    if(quote?.coinDiscountData?.isCoinApplied){
      totalPrice -= (Number(quote?.coinDiscountData?.storeCoinValue) || 0);
    }
    const setDefaultShukranValues = () => {
      if(quote?.appliedShukranPoint <= 0){
        quote.appliedShukranCashValue = 0;
        quote.appliedShukranPoint = 0;
      }else{
        appliedShukranPoint = quote?.appliedShukranPoint || 0;
        appliedShukranCashValue = quote?.appliedShukranCashValue || 0;
      }
      
    };

    if (
      appliedShukranPoint === undefined && totalPrice >= quote?.appliedShukranCashValue && shukranAvailableCashValue >= quote?.appliedShukranCashValue
    ) {
      //No Payload received and get from quote
      setDefaultShukranValues();
      quote.isAvailableShukranChanged = isAvailableShukranChanged;
      return quote;
    }
    if(appliedShukranPoint === undefined){
      //No Payload received and get from quote
      setDefaultShukranValues();
    }

    if (appliedShukranPoint === 0 || appliedShukranPoint === 0.0 || appliedShukranPoint === 0.00) {
      appliedShukranPoint = 0;
      appliedShukranCashValue = 0;
    } else {
      appliedShukranCashValue = appliedShukranPoint * shukranPointConversion;  
    }
  
    const totalQty = quote.quoteItem.reduce((sum, item) => {
      return sum + item.qty; // Extract the price from the object
    }, 0);

    // logger.info(`processBurnShukranCoinCalculations: Quote ${quote.id} - Total Price: ${totalPrice}, Total Qty: ${totalQty}`);

    //Check isAvailableShukranChanged based on point specific
    if (shukranAvailablePoint < appliedShukranPoint) {
      isAvailableShukranChanged = true;
    }

    if(shukranAvailablePoint < 0){
      isAvailableShukranChanged = false;
    }

    //Check available cash value less than applied cash value
    if (shukranAvailableCashValue < appliedShukranCashValue) {
      appliedShukranCashValue = shukranAvailableCashValue;
      appliedShukranPoint = shukranAvailablePoint;
    }

    if(shukranAvailablePoint <= 0){
      appliedShukranCashValue = 0;
      appliedShukranPoint = 0;
    }

    //Check Cart value less than applied cash value
    if (totalPrice < appliedShukranCashValue) {
      appliedShukranCashValue = totalPrice;

      //Calculating the applied point for the cash value
      appliedShukranPoint = appliedShukranCashValue / shukranPointConversion;
      appliedShukranPoint = Math.ceil(appliedShukranPoint);
    }

    // logger.info(`processBurnShukranCoinCalculations: Quote ${quote.id} - Applied Shukran Cash Value: ${appliedShukranCashValue}, Applied Shukran Point: ${appliedShukranPoint}`);

    const allocatedValues = quote.quoteItem.map((item) => {
      const price = item.rowTotalInclTax;
      const pricePerUnit = price - item.discountAmount - (item?.styliCashBurnInCurrency || 0); // Calculate price per unit
      const allocatedValue = (pricePerUnit / totalPrice) * appliedShukranCashValue;
      
      // logger.info(`processBurnShukranCoinCalculations: Item ${item.sku} - RowTotal: ${item.rowTotalInclTax}, Discount: ${item.discountAmount}, PricePerUnit: ${pricePerUnit}, AllocatedValue: ${allocatedValue}`);
      
      return {
        ...item,
        allocatedValue: parseFloat(allocatedValue.toFixed(2))
      };
    });

    let totalShukranBurnInCurrency = parseFloat(
      appliedShukranCashValue.toFixed(2)
    );
    let totalShukranBurnInBaseCurrency = parseFloat(
      (totalShukranBurnInCurrency * currencyConversionRate).toFixed(2)
    );

    // logger.info(`processBurnShukranCoinCalculations: Quote ${quote.id} - Total Shukran Burn in Currency: ${totalShukranBurnInCurrency}, in Base Currency: ${totalShukranBurnInBaseCurrency}`);

    //added in item level
    quote.quoteItem.forEach((item, index) => {
      item.shukranBurn =  parseFloat(allocatedValues[index].allocatedValue/shukranPointConversion).toFixed(2);
      item.shukranBurnInCurrency = allocatedValues[index].allocatedValue;
      item.shukranBurnInBaseCurrency = parseFloat(
        (
          allocatedValues[index].allocatedValue * currencyConversionRate
        ).toFixed(2)
      );
    });

    //API-3815 -START
    quote.totalShukranBurn = appliedShukranPoint;
    quote.totalShukranBurnValueInCurrency = totalShukranBurnInCurrency;
    quote.totalShukranBurnValueInBaseCurrency = totalShukranBurnInBaseCurrency;
    quote.appliedShukranPoint = appliedShukranPoint;
    quote.appliedShukranCashValue = appliedShukranCashValue;
    quote.shukranAvailablePoint = shukranAvailablePoint;
    quote.shukranAvailableCashValue = shukranAvailableCashValue;
    quote.isAvailableShukranChanged = isAvailableShukranChanged;
    //API-3815 - END

    return quote;
  } catch (e) {
    logError(e, "Error calculating  ShukranBurnTotal : ", e);
    // logger.error(`processBurnShukranCoinCalculations: Error calculating Shukran burn total - ${e.message}`);
  }
};
