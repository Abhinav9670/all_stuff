const { sequelize } = require('../models/seqModels/index');
const { QueryTypes } = require('sequelize');
const { default: axios } = require('axios');
const { FIND_FRAUD_CUSTOMER } = require('../constants/custMgmtEndpoints');
const { promiseAll } = require('../utils');
const { updateRmaStatus } = require('./rmaUpdateOps');

const isFraudCustomer = async orderId => {
  try {
    const customerInfo = await findCustomerInfo(orderId);
    return isFraud(customerInfo);
  } catch (error) {
    console.error(`Error in checking fraud customer : ${error}`);
  }
  return false;
};

const findCustomerInfo = async orderId => {
  try {
    const queryResponse = await sequelize.query(
      'SELECT customer_email, customer_id from sales_order where entity_id = ?',
      {
        replacements: [orderId],
        type: QueryTypes.SELECT
      }
    );
    const response = queryResponse[0];
    return response ? response : null;
  } catch (error) {
    console.error(`Error In Find Customer Details : ${error}`);
  }
  return null;
};

const isFraud = async customerInfo => {
  try {
    const payload = JSON.stringify({
      email: customerInfo?.customer_email,
      customer_id: customerInfo?.customer_id
    });
    const response = await axios.post(FIND_FRAUD_CUSTOMER, payload, {
      headers: {
        'Content-Type': 'application/json'
      }
    });
    const fraudCust = response?.data?.response;
    return fraudCust ? true : false;
  } catch (error) {
    console.error(`Error In Fetching Fraud Customer Details : ${error}`);
  }
  return false;
};

const setFraudPickedUp = async ({
  rmaId,
  rmaVerificationStatusId,
  rmaItems,
  returnType,
  rmaStatusData,
  timestamp,
  orderId
}) => {
  const promiseArr = [
    updateRmaStatus({
      rmaItems,
      rmaId,
      statusId: rmaVerificationStatusId,
      rmaStatusData,
      returnType,
      isFraudPickup: true,
      timestamp,
      orderId
    })
  ];

  const { success, errorMsg } = await promiseAll(promiseArr);

  return { status: success, msg: success ? 'success' : errorMsg };
};

module.exports = {
  isFraudCustomer,
  setFraudPickedUp
};
