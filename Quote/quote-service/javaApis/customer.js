const axios = require('axios');
const { logError, logInfo } = require('../helpers/utils');

exports.getCustomer = async ({ token, customerId }) => {
    try {
        // logInfo('process.env.GET_CUSTOMER', `${process.env.GET_CUSTOMER}${customerId}`);

        const response = await axios.get(`${process.env.GET_CUSTOMER}${customerId}`, {
            headers: {
                token,
                'Content-Type': 'application/json'
            }
        });
        const customer = response?.data?.response?.customer;
        const defaultAddress = response?.data?.response?.defaultAddress;

        // logInfo('customer response', customer);
        // logInfo('defaultAddress response', defaultAddress);

        return { customer, defaultAddress };
    } catch (e) {
        logError(e, 'Error fetching customer : ');
        // console.log('Error fetching customer : ',e.message);
    }
}

exports.getCustomerStoreCredit = async ({ customer, pool, xHeaderToken }) => {
    try {
        return await pool.query("select store_credit from amasty_store_credit where customer_id = ?", [customer]);
    } catch (e) {
        logError(e, 'Error getting store credit for customer : ', xHeaderToken);
        return undefined;
    }
}
