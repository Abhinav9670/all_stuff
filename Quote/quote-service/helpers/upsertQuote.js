const { initcluster, couchbase } = require('../config/couchbase');
const { logError, getCurrency } = require('../helpers/utils');
// const { logger } = require('../helpers/utils');
// const { produceQuote } = require('./kafka/producer');

exports.upsertQuote = async ({ storeId, quote, collection, expiration = 0, xHeaderToken }) => {
    let response;
    const apm = global?.apm;
    const key = `quote_${quote?.id}`; 
    const logPrefix = `upsert quote:::${new Date().toISOString()}`;
    let span;
    try {
      if(!collection){
        // logger.error(`${logPrefix} Couchbase collection missing in upsert quote`);
      }
        quote.updatedAt = new Date();
        quote.storeId = Number(storeId);
        quote.baseCurrencyCode = 'SAR';
        quote.storeCurrencyCode = getCurrency({ storeId });
        span = apm?.startSpan('CB: Upsert Quote', 'db', 'couchbase', 'upsert');
        if (span) {

            span.setServiceTarget('couchbase', process.env.COUCHBASE_CLUSTER_BUCKET);
        }
        const result = await collection.upsert(key, quote, { expiry: Number(expiration) });
        response = true
    } catch (e) {
        logError(e, `${key} - Error upserting quote`, xHeaderToken);
        response = false;
    } finally {
        if (span) span.end();
    }
    // produceQuote({quote,xHeaderToken})

    return response;
}

exports.findQuotesByCustomerAndFlash = async (request) => {
  const apm = global?.apm;
  let span;
  try {
    const cluster = await initcluster();
    const { flashSaleId, customerId, storeId } = request;
    const storefilter = storeFilter(storeId);
    const findQuotesQuery = `select q.* from ${process.env.COUCHBASE_CLUSTER_BUCKET} as q where q.flashSale.flashSaleId = '${flashSaleId}' and q.storeId IN [${storefilter}] and q.customerId = '${customerId}'`;
    span = apm?.startSpan('CB: Query findQuotesByCustomerAndFlash', 'db', 'couchbase', 'query');
    if (span) {
      span.setServiceTarget('couchbase', process.env.COUCHBASE_CLUSTER_BUCKET);
    }
    const result = await cluster.query(findQuotesQuery);
    return result.rows;
  } catch (error) {
    logError(`Error in fetching the quotes by customer & flashId`);
  } finally {
    if (span) span.end();
  }
  return [];
};


const storeFilter = (storeId) => {
  if (storeId === 1 || storeId === 3) {
    return [1, 3];
  } else if (storeId === 7 || storeId === 11) {
    return [7, 11];
  } else if (storeId === 12 || storeId === 13) {
    return [12, 13];
  } else if (storeId === 15 || storeId === 17) {
    return [15, 17];
  } else if (storeId === 19 || storeId === 21) {
    return [19, 21];
  } else if (storeId === 23 || storeId === 25) {
    return [23, 25];
  } else if (storeId === 51) {
    return [51];
  }
};

/**
 * Optimized version that updates only selected keys in the quote
 * Uses Couchbase mutateIn for partial updates (more efficient than full document replacement)
 * 
 * @param {Object} params - Function parameters
 * @param {string|number} params.quoteId - Quote ID
 * @param {Object} params.quote - Quote object containing only the fields to update
 * @param {Array<string>} params.keysToUpdate - Array of keys/paths to update (e.g., ['quoteItem', 'subtotal', 'quotePayment.method'])
 * @param {Object} params.collection - Couchbase collection instance
 * @param {string} params.xHeaderToken - Header token for logging
 * @param {number} params.expiration - Expiration time in seconds (optional, for document creation)
 * @returns {Promise<boolean>} Success status
 */
exports.upsertQuotePartial = async ({ 
  quoteId, 
  quote, 
  keysToUpdate, 
  collection, 
  xHeaderToken,
  expiration
}) => {
  if (!quoteId) {
    // logger.error('upsertQuotePartial: Quote ID is missing');
    return false;
  }

  if (!collection) {
    // logger.error('upsertQuotePartial: Couchbase collection is missing');
    return false;
  }

  if (!Array.isArray(keysToUpdate) || keysToUpdate.length === 0) {
    // logger.error('upsertQuotePartial: keysToUpdate must be a non-empty array');
    return false;
  }

  const key = `quote_${quoteId}`;
  const apm = global?.apm;
  let span;
  
  try {
    span = apm?.startSpan('CB: Upsert Quote Partial', 'db', 'couchbase', 'mutateIn');
    if (span) {
        span.setServiceTarget('couchbase', process.env.COUCHBASE_CLUSTER_BUCKET);
    }
    
    // Build mutateIn specs for each key to update
    const specs = [];
    
    // Always update updatedAt timestamp
    specs.push(couchbase.MutateInSpec.upsert('updatedAt', new Date()));
    
    // Add specs for each key to update
    keysToUpdate.forEach(keyPath => {
      // Get value from quote object using dot notation or direct key
      const value = keyPath.split('.').reduce((obj, k) => obj?.[k], quote);
      
      if (value !== undefined) {
        specs.push(couchbase.MutateInSpec.upsert(keyPath, value));
      } else {
        // logger.warn(`upsertQuotePartial: Key "${keyPath}" not found in quote object for ${key}`);
      }
    });

    if (specs.length === 0) {
      // logger.warn(`upsertQuotePartial: No valid keys to update for ${key}`);
      return false;
    }

    // Perform partial update
    const options = {};
    if (expiration !== undefined) {
      options.expiry = Number(expiration);
    }
    
    await collection.mutateIn(key, specs, options);
    return true;
    
  } catch (e) { 
    logError(e, `${key} - Error in upsertQuotePartial`, xHeaderToken);
    return false;
  } finally {
    if (span) span.end();
  }
};