const { pushNotification } = require('../notifications');
const { getContextId } = require('../product');
const { removeItemFromQuote } = require('../removeItem');
const { upsertQuote } = require('../upsertQuote');
const { getBaseConfig, getStoreConfig, logError } = require('../utils');
// const { logger } = require('../utils');

/**
 * Parse version string to comparable number
 * @param {string} version - Version string like "5.2.7000" or "v25.10.17"
 * @returns {number} - Comparable number like 527000 or 25010170
 * @note This function handles v-prefixed versions for parsing, but isVersionCompatible excludes them
 */
const parseVersionToNumber = (version) => {
  if (!version) return 0;

  try {
    // Remove 'v' prefix if present and split by dots
    const cleanVersion = version.replace(/^v/i, '');
    const parts = cleanVersion.split('.').map(part => part.padStart(3, '0'));
    // Join and convert to number
    return parseInt(parts.join(''), 10);
  } catch (e) {
    // logger.error(`Error parsing version ${version}:`, e);
    return 0;
  }
};

/**
 * Check if client version meets minimum requirement
 * @param {string} clientVersion - Client version from x-client-version header
 * @param {string} minVersion - Minimum required version from config
 * @returns {boolean} - Whether client version meets requirement
 */
const isVersionCompatible = (clientVersion, minVersion) => {
  if (!clientVersion || !minVersion) return false;

  // Exclude versions with 'v' prefix - they should not work
  if (clientVersion.toLowerCase().startsWith('v')) {
    // logger.info(`Version check: client version ${clientVersion} has 'v' prefix - GWP validation disabled`);
    return false;
  }

  const clientVersionNum = parseVersionToNumber(clientVersion);
  const minVersionNum = parseVersionToNumber(minVersion);

  // logger.info(`Version check: client=${clientVersion}(${clientVersionNum}) >= min=${minVersion}(${minVersionNum}) = ${clientVersionNum >= minVersionNum}`);

  return clientVersionNum >= minVersionNum;
};

const checkForTheLatestVersion = (
  { currentVersion,
    latestVersion }
) => {
  try {
  currentVersion = getBaseConfig('VersionConfigs')?.['giftRemoveVersion'] || '5.3.000'
  if(latestVersion.startsWith('v')){
    return false;
  }
  const v1 = latestVersion.split('.').map(Number);
  const v2 = currentVersion.split('.').map(Number);

  const maxLength = Math.max(v1.length, v2.length);

  for (let i = 0; i < maxLength; i++) {
    const a = v1[i] ?? 0;
    const b = v2[i] ?? 0;

    if (a > b) return true;
    if (a < b) return false;
  }

  return false;
} catch (error) {
      // logger.error(`Error checking for latest version: ${error}`);
      return false;
    }
}

exports.processFreeGift = async ({
  quote,
  invCheck,
  xHeaderToken,
  xClientVersion,
  screenName,
  collection,
  bagView,
  optimized = false
}) => {
  try {
    //  invCheck = [{sku: '700141650505', value: '9858.0000'},{sku: '4024171206', value: '92.0000'}];
    if (screenName?.toLowerCase() === 'bag' || bagView) {
      const { storeId, subtotalWithDiscount} = quote;
      let priceToCompare = Number(subtotalWithDiscount);
      //MOB-7147 removing styli cash from the subtotal check for free gifts
      // if (quote.customerId && quote?.coinDiscountData?.isCoinApplied) {
      //   priceToCompare =
      //   priceToCompare - Number(quote.coinDiscountData?.storeCoinValue || 0);
      // }
      const websiteCode = getStoreConfig(storeId, 'websiteCode');
      const giftConfig = getBaseConfig('giftProducts');
      const countryConfig = giftConfig?.[websiteCode] || {};
      const notifications = giftConfig?.notifications;
      const { enabled, enable_gift_with_purchase_module_feature, collections } = countryConfig;
      const minClientVersion = giftConfig?.minClientVersion;
      const toRemoveSkuObj = quote?.quoteItem?.find(item => item.isGift);
      const latestVersion = checkForTheLatestVersion({ latestVersion: xClientVersion });
      let inStock =
        invCheck?.find(item => item.sku === toRemoveSkuObj?.sku)?.value > 0;
      const contextId = getContextId({
        enrich: toRemoveSkuObj?.enrich,
        storeId,
        xHeaderToken
      });
      const minBagVal =
        Number(collections?.find(col => col.contextId === contextId)?.minBagVal || 0);
      // logger.info(`processFreeGift for quote ${quote?.id}: subtotal=${priceToCompare}, minBagVal=${minBagVal}, enabled=${enabled}, inStock=${inStock}, removesku=${toRemoveSkuObj?.sku}`);

      if (latestVersion) {
        inStock = true;
      }

      if (
        toRemoveSkuObj?.sku &&
        (!enabled || !inStock || (minBagVal > 0 && Number(priceToCompare) < minBagVal))
      ) {
        // logger.info(`processFreeGift: Removing free gift for quote ${quote?.id}`);
        // console.log("GWP missing debug log::: remove item from `processFreeGift`", toRemoveSkuObj, "quoteId", quote?.id);
        const removeQuoteResponse = await removeItemFromQuote({
          quote,
          skus: [toRemoveSkuObj.sku],
          xHeaderToken
        });
        const productToRemove = removeQuoteResponse?.removedItem?.sku;
        const newData = {};
        Object.entries(quote?.groupedData?.data).forEach(([key, entry]) => {
          const products = entry.products;
          const filteredProducts = products.filter(product => product !== productToRemove);

          if (filteredProducts.length > 0) {
            const newKey = filteredProducts.join('_');
            newData[newKey] = {
              products: filteredProducts
            };
          }
        });
        // logger.info(`processFreeGift: Updated grouped data for quote ${quote?.id}: ${JSON.stringify(newData)}`);
        quote.groupedData = {
          data: newData,
          configs: quote.groupedData?.configs
        };
        quote = removeQuoteResponse.quote;
        pushNotification({
          quote,
          notifyData: notifications,
          notifyId: 'giftRemoved',
          textKey: 'REMOVAL_MSG'
        });
        if (!optimized) {
          await upsertQuote({ storeId, quote, collection, xHeaderToken });
        }
      }
      if (enable_gift_with_purchase_module_feature && isVersionCompatible(xClientVersion, minClientVersion)) {
        // logger.info(`processFreeGift: Processing GWP validation for quote ${quote?.id}`);
        const giftItems = quote?.quoteItem?.filter(item => item.isGift);
        if (giftItems.length > 0) {
          // logger.info(`processFreeGift: Found ${giftItems.length} gift items in quote ${quote?.id}`);
          const normalProducts = quote?.quoteItem?.filter(item => !item.isGift);
          // logger.info(`processFreeGift: Found ${normalProducts.length} normal products in quote ${quote?.id}`);
          let newGwpSubtotal = 0;
          const eligibleNormalProducts = [];
          const { eligible_products_context_id } = countryConfig || {};

          for (const product of normalProducts) {
            if (product?.enrich?.[`c_${eligible_products_context_id}`] === 1) {
              const rowTotalWithDiscount = parseFloat((product.rowTotalInclTax || 0) - (product.discountAmount || 0));
              newGwpSubtotal += rowTotalWithDiscount;
              eligibleNormalProducts.push({
                sku: product.sku,
                contextId: eligible_products_context_id,
                rowTotalWithDiscount: rowTotalWithDiscount
              });
              // logger.info(`processFreeGift: Normal product ${product.sku} has eligible_products_context_id ${eligible_products_context_id} in enrich, adding ${rowTotalWithDiscount} to GWP subtotal`);
            }
          }

          // logger.info(`processFreeGift: GWP subtotal from eligible normal products: ${newGwpSubtotal}, eligible products count: ${eligibleNormalProducts.length}`);

          // Check only the first (lowest) threshold
          let meetsFirstThreshold = false;
          const firstThreshold = collections && collections.length > 0 ? Number(collections[0].minBagVal || 0) : 0;

          if (firstThreshold > 0 && newGwpSubtotal >= firstThreshold) {
            meetsFirstThreshold = true;
            // logger.info(`processFreeGift: GWP subtotal ${newGwpSubtotal} meets first threshold ${firstThreshold} for collection ${collections[0].contextId}`);
          } else {
            // logger.info(`processFreeGift: GWP subtotal ${newGwpSubtotal} does not meet first threshold ${firstThreshold}`);
          }

          const giftSkusToRemove = [];

          for (const giftItem of giftItems) {
            const giftInStock = invCheck?.find(item => item.sku === giftItem.sku)?.value > 0;

            if (!giftInStock) {
              if (!latestVersion) {
                giftSkusToRemove.push(giftItem.sku);
                // logger.info(`processFreeGift: Gift item ${giftItem.sku} marked for removal - out of stock`);
              }
            }
            else if (!meetsFirstThreshold) {
              giftSkusToRemove.push(giftItem.sku);
              // logger.info(`processFreeGift: Gift item ${giftItem.sku} marked for removal - GWP first threshold not met (${newGwpSubtotal} < ${firstThreshold})`);
            } else {
              // logger.info(`processFreeGift: Gift item ${giftItem.sku} kept - GWP first threshold met (${newGwpSubtotal} >= ${firstThreshold})`);
            }
          }

          if (giftSkusToRemove.length > 0) {
            // logger.info(`processFreeGift: Removing ${giftSkusToRemove.length} gift items from quote ${quote?.id}`);
            // console.log("GWP missing debug log::: remove item from `processFreeGift`", giftSkusToRemove, "quoteId", quote?.id);
            const removeQuoteResponse = await removeItemFromQuote({
              quote,
              skus: giftSkusToRemove,
              xHeaderToken
            });

            // Update grouped data
            const removedSkus = giftSkusToRemove;
            const newData = {};
            Object.entries(quote?.groupedData?.data || {}).forEach(([key, entry]) => {
              const products = entry.products;
              const filteredProducts = products.filter(product => !removedSkus.includes(product));

              if (filteredProducts.length > 0) {
                const newKey = filteredProducts.join('_');
                newData[newKey] = {
                  products: filteredProducts
                };
              }
            });

            quote.groupedData = {
              data: newData,
              configs: quote.groupedData?.configs
            };
            quote = removeQuoteResponse.quote;

            pushNotification({
              quote,
              notifyData: notifications,
              notifyId: 'giftRemoved',
              textKey: 'REMOVAL_MSG'
            });
            if(!optimized){
              await upsertQuote({ storeId, quote, collection, xHeaderToken });
            }
          } else {
            // logger.info(`processFreeGift: No gift items to remove from quote ${quote?.id}`);
          }
        } else {
          // logger.info(`processFreeGift: No gift items found in quote ${quote?.id}`);
        }
      } else {
        if (enable_gift_with_purchase_module_feature && !isVersionCompatible(xClientVersion, minClientVersion)) {
          // logger.info(`processFreeGift: GWP module enabled but client version ${xClientVersion} does not meet minimum requirement ${minClientVersion}`);
        } else {
          // logger.info(`processFreeGift: GWP module not enabled for quote ${quote?.id}`);
        }
      }
    }
  } catch (e) {
    logError(e, 'Error prcessing free gift', xHeaderToken);
  }
  return quote;
};
