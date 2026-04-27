const axios = require('axios');
const { logError, logInfo, getStoreConfig } = require('../helpers/utils');

exports.getStoreCreditBalance = async ({ customerId, storeId, token, xHeaderToken }) => {

    if (!customerId) return null;

    // logInfo('process.env.STORE_CREDIT_URL', `${process.env.STORE_CREDIT_URL}`, xHeaderToken);
    try {
        const response = await axios.post(process.env.STORE_CREDIT_URL, { customerId, storeId }, {
            headers: {
                token,
                'Content-Type': 'application/json'
            }
        });
        const storeCrediit = response?.data?.response?.storeCredit;
        // logInfo('store credit fetch successful ', storeCrediit, xHeaderToken);
        return storeCrediit;
    } catch (e) {
        logError(e, 'Error store credit fetch : ', xHeaderToken);
        return null;
    }

}
