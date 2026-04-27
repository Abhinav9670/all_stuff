const { logError } = require('../helpers/utils');
// const { logger } = require('./utils');

exports.removeItemFromQuote = async ({ skus, quote, res, xHeaderToken }) => {
    let error = null;
    let removedItem = {};

    try {
        // console.log("GWP missing debug log::: Inside `removeItemFromQuote` function", skus, "quoteId", quote?.id);
        // remove products from existing quote item
        const existingItems = quote.quoteItem?.filter(item => skus.indexOf(item.sku) > -1);
        if (existingItems?.length) {
            const updatedQuoteItem = quote.quoteItem?.filter(item => skus.indexOf(item.sku) === -1);
            const updatedParentSkus = updatedQuoteItem.map(quoteItem => quoteItem.parentSku);

            const updatedMetaData = removeMetadata({ metadata: quote.metadata, updatedParentSkus })
            const itemsQty = updatedQuoteItem.reduce((totalQty, quoteItem) => {
                return totalQty + Number(quoteItem.qty)
            }, 0);

            quote.itemsCount = updatedQuoteItem.length;
            quote.itemsQty = itemsQty;
            quote.metadata = updatedMetaData;
            quote.quoteItem = updatedQuoteItem;
            removedItem = existingItems[0];
            if (removedItem?.droppedPrice > 0) {
                quote.priceDropConfig = {};
                quote.priceDropData = { offerTime: 0 };
            }
            // logger.info("Updated Quote after item removal", { quote });
        } else {
            error = {
                status: false,
                statusCode: "500",
                statusMsg: "product not found",
            };
        }
    } catch (e) {
        logError(e, 'error removing item ', xHeaderToken);
    }
    return { error, quote,removedItem };
}

const removeMetadata = ({ metadata, updatedParentSkus }) => {
    //keep metadata data only for existing parent skus
    const updatedMetaData = metadata?.filter(data => updatedParentSkus.indexOf(data.sku) > -1);
    return updatedMetaData
}