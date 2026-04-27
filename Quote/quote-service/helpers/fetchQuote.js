const COUCHBASE_BUCKET = process.env.COUCHBASE_CLUSTER_BUCKET;
// const { logger } = require('./utils');
// Pre-computed store mappings - initialized once at module load
const storeMap = {
    1: [1, 3],
    3: [1, 3],
    7: [7, 11],
    11: [7, 11],
    12: [12, 13],
    13: [12, 13],
    15: [17, 15],
    17: [17, 15],
    19: [19, 21],
    21: [19, 21],
    23: [23, 25],
    25: [23, 25],
    51: [51]
};

// Pre-computed store filter map for O(1) lookup - initialized once at module load
const storeFilterMap = {
    1: '(storeId=1 or storeId=3)',
    3: '(storeId=1 or storeId=3)',
    7: '(storeId=7 or storeId=11)',
    11: '(storeId=7 or storeId=11)',
    12: '(storeId=12 or storeId=13)',
    13: '(storeId=12 or storeId=13)',
    15: '(storeId=15 or storeId=17)',
    17: '(storeId=15 or storeId=17)',
    19: '(storeId=19 or storeId=21)',
    21: '(storeId=19 or storeId=21)',
    23: '(storeId=23 or storeId=25)',
    25: '(storeId=23 or storeId=25)',
    51: '(storeId=51)'
};

exports.fetchQuote = async function ({ identifier, storeId, collection, cluster, type, res, xHeaderToken = '', retryPaymentReplica = false }) {
    let storeFilter;
    let quote = null;
    const logPrefix = `fetchQuote:::${new Date().toISOString()}`;
    const apm = global?.apm;

    const formattedStoreId = Number(storeId);
    storeFilter = storeFilterMap[formattedStoreId];


    if(!collection || !cluster){
        // logger.error(`${logPrefix} Couchbase connection is missing in fetch Quote function:`);
        return null
    }

    if (type == "customer" && identifier) {
        let quoteQueryData;
        let span;
        try {
            const sanitizedIdentifier = String(identifier).replace(/"/g, '\\"');
            const query = `SELECT * FROM \`${COUCHBASE_BUCKET}\` WHERE customerId="${sanitizedIdentifier}" AND isActive=1 AND ${storeFilter} LIMIT 1`;
            span = apm?.startSpan('CB: Query fetch Quote', 'db', 'couchbase', 'query');
            if (span) {
                span.setServiceTarget('couchbase', COUCHBASE_BUCKET);
            }
            quoteQueryData = await cluster.query(query);
            if (quoteQueryData?.rows?.length) {
                // logger.info(`${logPrefix} Quote found for customer: ${identifier}`);
            }
        } catch (e) {
            // logger.error(`${logPrefix} Error getting quote by query for ${identifier}: ${e.message}`);
        } finally {
            if (span) span.end();
        }
        
        if (quoteQueryData?.rows?.length) {
            quote = quoteQueryData.rows[0][COUCHBASE_BUCKET];
        }
    } else if (type == "guest" && Boolean(identifier)) {
        let quoteData
        const key = `quote_${identifier}`;
        let kvSpan;
        try {
            kvSpan = apm?.startSpan('CB: KV fetch Quote', 'db', 'couchbase', 'get');
            if (kvSpan) {
                kvSpan.setServiceTarget('couchbase', COUCHBASE_BUCKET);
            }
            quoteData = await collection.get(key);
            if ((quoteData?.content?.isActive == 1 &&
                storeMap[formattedStoreId].indexOf(Number(quoteData?.content?.storeId)) > -1) || retryPaymentReplica
            )
                quote = quoteData?.content
        } catch (e) {
            // logger.error(`${logPrefix} Error getting quote by key from actual node ${key}: ${e.message}`);
            let replicaSpan;
            try {
                replicaSpan = apm?.startSpan('CB: KV Replica fetch Quote', 'db', 'couchbase', 'get');
                if (replicaSpan) {
                    replicaSpan.setServiceTarget('couchbase', COUCHBASE_BUCKET);
                }
                const replicaData = await collection.getAnyReplica(key);
                if (replicaData?.content?.isActive == 1 &&
                    storeMap[formattedStoreId].indexOf(Number(replicaData?.content?.storeId)) > -1
                )
                    quote = replicaData?.content
            } catch (e) {
                // logger.error(`${logPrefix} Error getting quote by key from replica node ${key}: ${e.message}`);
            } finally {
                if (replicaSpan) replicaSpan.end();
            }
        } finally {
            if (kvSpan) kvSpan.end();
        }
    }
    return quote;

};
