const fetchQuote = require('../helpers/fetchQuote');
const { addAddress, validateAddressReq } = require('../helpers/address');
const { logError } = require('../helpers/utils');
const { collection, initcluster } = require('../config/couchbase.js');
const { uuidCheckFailed, uuidError } = require('../helpers/validateToken');
// const { logger } = require('../helpers/logger');

exports.updateAddress = async ({ req, res }) => {
    const { headers, uuid, body } = req;
    const xHeaderToken = headers['x-header-token'] || '';
    let response;
    const logPrefix = `uupdateAddress:::${new Date().toISOString()}`;

    try {
        const quoteCollection = await collection();
        const cluster = await initcluster();

        if(!quoteCollection || !cluster){
            // logger.error(logPrefix, "couchbase connection missing in updateAddress:")
        }
        const { storeId, addressId, address } = body;
        const quoteId = String(body.quoteId || "");
        const customerId = String(body.customerId || "");
        address.lastName = address?.lastName?.trim() ||  ".";
        address.firstName = address?.firstName?.trim() ||  address.lastName;

        if (!validateAddressReq({ storeId, quoteId, customerId, addressId, address })) {
            res.status(200);
            res.json({ "status": false, "statusCode": "201", statusMsg: "Invalid request" })
            return '';
        }

        let quote;

        if (!customerId) {
            quote = await fetchQuote.fetchQuote({ identifier: String(quoteId || ''), storeId, collection:quoteCollection, cluster, type: "guest", res })
        } else {
            quote = await fetchQuote.fetchQuote({ identifier: String(customerId || ''), storeId, collection:quoteCollection, cluster, type: "customer", res });
        }

        if (!quote) {
            res.status(200);
            res.json({
                status: false,
                statusCode: "202",
                statusMsg: "quote not found!",
            });
            return;
        }

        if (uuidCheckFailed({ uuid, quote })) return uuidError(res);

        response = await addAddress({ collection:quoteCollection, storeId, customerId: String(customerId || ''), address, quote, addressId: String(addressId || ''), xHeaderToken });
        res.status(200);
        res.json(response);


    } catch (e) {
        logError(e, 'error updating address : ', xHeaderToken);
        res.status(500);
        res.json({
            status: false,
            statusCode: "500",
            statusMsg: e.message,
        });
    }



}