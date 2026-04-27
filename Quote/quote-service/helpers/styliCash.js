const { formatPrice } = require("../helpers/utils");
// const logger = require("../helpers/utils").logger;
const getAllocatedCashValue = (quote, totalPrice, coinDiscountData) => {
    try {
        const { coins = 0, storeCoinValue = '', baseCurrencyValue = '', isCoinApplied = 0 } = coinDiscountData;
        if(isCoinApplied == 0){
            return [];
        }
        
        return quote.quoteItem.map((item) => {
            const price = item.rowTotalInclTax;
            const pricePerUnit = price - item.discountAmount; // Calculate price per unit
            const percentPrice = (pricePerUnit / totalPrice);
            const allocatedCoins = percentPrice * Number(coins);
            const allocatedCashValue = percentPrice * Number(storeCoinValue);
            const allocatedCashValueInBaseCurrency = percentPrice * Number(baseCurrencyValue);
            return {
                ...item,
                allocatedCoins: parseFloat(allocatedCoins.toFixed(2)),
                allocatedCashValue: parseFloat(allocatedCashValue.toFixed(2)),
                allocatedCashValueInBaseCurrency: parseFloat(allocatedCashValueInBaseCurrency.toFixed(2))
            };
        });
    } catch (error) {
        // logger.info('getAllocatedCashValue: Error occurred while calculating allocated cash value');
        return [];
    }
};
const setStyliCashBurnItemWise = (quote, statusCoinApllied) => {
    try {
        if(Number(statusCoinApllied) == 0){
            quote.quoteItem.forEach((item) => {
                item.styliCashBurn = 0;
                item.styliCashBurnInCurrency = 0;
                item.styliCashBurnInBaseCurrency = 0;
            });
            return quote;
        }
        const totalPrice = quote.quoteItem.reduce((sum, item) => {
            const rowTotalInclTax = item?.rowTotalInclTax || 0;
            const droppedPrice = item?.droppedPrice || 0;

            return (
                sum +
                (droppedPrice > 0 ? droppedPrice * item.qty : rowTotalInclTax) -
                item.discountAmount
            ); // Extract the price from the object
        }, 0);
        const coinDiscountData = quote?.coinDiscountData || {};
        const allocatedCashValueList = getAllocatedCashValue(quote, totalPrice, coinDiscountData);
        if(allocatedCashValueList.length == 0){
            return quote;
        }
        quote.quoteItem.forEach((item, index) => {
            item.styliCashBurn = allocatedCashValueList[index].allocatedCoins;
            item.styliCashBurnInCurrency = allocatedCashValueList[index].allocatedCashValue;
            item.styliCashBurnInBaseCurrency = allocatedCashValueList[index].allocatedCashValueInBaseCurrency;
        });
        return quote;
    } catch (error) {
        // logger.info('setStyliCashBurnItemWise: Error occurred while calculating styli cash burn');
        return quote;
    }
};
const setStyliCashBurn = (quote, responseObject) => {
    try {
        if (quote?.coinDiscountData?.isCoinApplied) {
            const safeValue = (value) => (value === null || isNaN(value) ? 0 : value);

            // Create lookup map for quote items by productId
            const quoteItemLookup = new Map();
            quote.quoteItem.forEach(item => {
                quoteItemLookup.set(item.productId, item);
            });
            responseObject.quoteProducts.forEach(product => {
                const quoteItem = quoteItemLookup.get(product.productId);
                if (quoteItem) {
                    Object.assign(product, {
                        styliCashBurn: safeValue(quoteItem.styliCashBurn),
                        styliCashBurnInCurrency: safeValue(quoteItem.styliCashBurnInCurrency),
                        styliCashBurnInBaseCurrency: safeValue(quoteItem.styliCashBurnInBaseCurrency)
                    });
                }
            });
        }
    } catch (error) {
        // logger.info('setStyliCashBurn: Error occurred while calculating styli cash burn');
        return quote;
    }
};
const setStyliCashApplicableTotal = ({quote, finalGrandTotal, adjustedDonation, responseObject}) => {
    try {
        const applicableTotal = finalGrandTotal - (quote?.codCharges || 0) - (adjustedDonation || 0) - (quote?.shippingCharges || 0) - Number(responseObject?.importFeesAmount || 0);
        responseObject.styliCashApplicableTotal = String(formatPrice(applicableTotal < 0 ? 0 : applicableTotal));
    }
    catch (error) {
        // logger.info('setStyliCashApplicableTotal: Error occurred while calculating coins grand total');
        responseObject.styliCashApplicableTotal = String(formatPrice(Math.max(0, finalGrandTotal)));
    }
};
module.exports = {
    setStyliCashBurnItemWise, setStyliCashBurn, setStyliCashApplicableTotal
};