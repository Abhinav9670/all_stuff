const { DeleteCustomerConsumer } = require('./deleteCustomer')
const { priceDropConsumer } = require('./priceDropSkus')

const kafkaConsumers = async () => {
    DeleteCustomerConsumer();
    priceDropConsumer();
};

module.exports = { kafkaConsumers };

