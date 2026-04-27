require('dotenv-expand')(require('dotenv').config({
    path: require('find-config')(`.env.${process.env.NODE_ENV || 'development.local'}`)
}));

const mysql = require('../config/mySqlConnection');
const { collection } = require('../config/couchbase.js');
const batchSize = Number(process.env.IMPORT_BATCH_SIZE || 100);
const entityVarcharMap = { 221: 'nearest_landmark', 222: 'area' };

const importAddresses = async ({ startFromPage }) => {
    const errorUpsertAddressKeys = [];
    // console.log('intializing import address');
    try {
        const quoteCollection = await collection();
        const addressCountQuery = "select count(entity_id) as address_count from customer_address_entity";
        const addressCount = await mysql.query(addressCountQuery);
        let data = JSON.parse(JSON.stringify(addressCount));
        const count = data[0][0].address_count;
        const maxPageNum = Math.floor(count / batchSize);

        // console.log('total Address count', count);
        // console.log('total number of pages', maxPageNum);
        const couchbaseClusterAddressBucket = process.env.COUCHBASE_CLUSTER_ADDRESS_BUCKET;

        for (let i = startFromPage || 0; i <= maxPageNum; i++) {
            try {
                // console.log(`######### page number :  ${i}  #############`)
                const query = `SELECT * FROM customer_address_entity  limit ${i * batchSize},${batchSize}`;
                // const query = `SELECT * FROM customer_address_entity  where parent_id=391`;
                const addressResponse = await mysql.query(query);
                const varcharDataObj = {};
                const finalData = {};
                const customerIds = [];
                let [addressRowData] = addressResponse;
                addressRowData = JSON.parse(JSON.stringify(addressRowData));
                const addressIds = addressRowData.map(addressRow => {
                    customerIds.push(addressRow.parent_id);
                    return addressRow.entity_id;
                });

                const uniqueCustomerIds = [... new Set(customerIds)];
                // console.log('addressRowData', addressRowData)
                // console.log('address import customer count : ', uniqueCustomerIds.length);


                const varcharQuery = `SELECT * FROM customer_address_entity_varchar WHERE entity_id in ('${addressIds.join("','")}')`;
                const varcharResponse = await mysql.query(varcharQuery);
                let [varcharData] = varcharResponse;
                varcharData = JSON.parse(JSON.stringify(varcharData));

                for (const index in varcharData) {
                    const varcharRowData = varcharData[index];

                    if (!varcharDataObj[varcharRowData.entity_id]) {
                        varcharDataObj[varcharRowData.entity_id] = { nearest_landmark: '', area: '' };
                    }

                    if (varcharRowData.attribute_id === 221) {
                        varcharDataObj[varcharRowData.entity_id].nearest_landmark = varcharRowData.value;
                    } else if (varcharRowData.attribute_id === 222) {
                        varcharDataObj[varcharRowData.entity_id].area = varcharRowData.value;
                    }
                }

                for (const index in addressRowData) {
                    const addressRow = addressRowData[index];
                    if (!finalData[addressRow.parent_id]) {
                        finalData[addressRow.parent_id] = [];
                    }
                    const finalAddressData = { ...addressRow, ...varcharDataObj[addressRow.entity_id] };
                    finalData[addressRow.parent_id].push(finalAddressData);
                }

                // console.log('finalData', finalData)

                let upsertCtr = 0;
                const apm = global?.apm;
                for (const index in finalData) {
                    const aData = finalData[index];
                    let span;
                    try {
                        const key = `address_${index}`;
                        // console.log('key',key);
                        span = apm?.startSpan('CB: Upsert importAddress', 'db', 'couchbase', 'upsert');
                        if (span) {
                            span.setServiceTarget('couchbase', couchbaseClusterAddressBucket);
                        }
                        const upsertResponse = await quoteCollection.upsert(key, aData);
                        if (upsertResponse) {
                            upsertCtr++;
                        }
                    } catch (e) {
                        // console.log('error upserting ', e)
                        errorUpsertAddressKeys.push(key);
                    } finally {
                        if (span) span.end();
                    }
                }
                // console.log('upsert customer address count : ', upsertCtr);

            } catch (e) {
                // console.error('Error while fetching data of customer address ', e);
            }
        }

        if (errorUpsertAddressKeys.length) {
            // console.log('########## Error upsert following address #########');
            // console.log(JSON.stringify(errorUpsertAddressKeys));
        }
    } catch (e) {
        // console.error('Error fetching address from mysql ', e);
    }
    // console.log('importing Address completed');
}


// console.log(process.env.start_from_page);
const startFromPage = Number(process.env.start_from_page);

importAddresses({ startFromPage });