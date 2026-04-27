const { logError, getStoreConfig } = require('../helpers/utils');

exports.updateItemQty = async ({ quote, requestedQty, sku, xHeaderToken }) => {
    const quoteMaxQtyAllowed = Number(getStoreConfig(quote.storeId, 'quoteProductMaxAddedQty') || 10);
    try {
        const updatedQuoteItems = quote.quoteItem?.map(item => {
            if (item.sku === sku) {
                item.qty = Math.min(Number(requestedQty), quoteMaxQtyAllowed);
            }
            return item
        })

        quote.quoteItem = updatedQuoteItems;
    } catch (e) {
        logError(e, 'error updating qty ', xHeaderToken);
        // console.log('error updating qty ',e.message);
    }
    return quote;
}