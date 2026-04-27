
require('dotenv-expand')(require('dotenv').config({
    path: require('find-config')(`.env.${process.env.NODE_ENV || 'development.local'}`)
}));

const mysql = require('../config/mySqlConnection');
const { collection } = require('../config/couchbase.js');

const importStoreCredit = async () => {
    // console.log("import store credit inititalized");
    try {
        const quoteCollection = await collection();
        const storeCreditQuery = "select * from amasty_store_credit";
        const response = await mysql.query(storeCreditQuery);
        let [data] = response;
        data = JSON.parse(JSON.stringify(data));
        // console.log(`importing ${data.length} customer store credit`);
        const errorStoreCreditIds = [];
        let upsertCtr = 0;
        const apm = global?.apm;
        const couchbaseClusterBucket = process.env.COUCHBASE_CLUSTER_BUCKET;
        for (const index in data) {
            const creditData = data[index];
            let span;
            try {
                const key = `store_credit_${creditData.customer_id}`;
                span = apm?.startSpan('CB: Upsert importStoreCredit', 'db', 'couchbase', 'upsert');
                if (span) {
                    span.setServiceTarget('couchbase', couchbaseClusterBucket);
                }
                const upsertResponse = await quoteCollection.upsert(key, creditData);
                if (upsertResponse) {
                    upsertCtr++
                }
            } catch (e) {
                // console.error('Error upserting store credit', e);
                errorStoreCreditIds.push(creditData.store_credit_id);
            } finally {
                if (span) span.end();
            }
        }
        console.log(`upserted ${upsertCtr} customer store credit`);
    } catch (e) {
        // console.error('Error import Store Credits ', e.message);
    }
    console.log("import store credit completed");

}

importStoreCredit();