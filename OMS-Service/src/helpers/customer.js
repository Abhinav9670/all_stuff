const { Op } = require('sequelize');
const axios = require('axios');
const { CUSTOMER_DETAIL_ENDPOINT } = require('../constants/javaEndpoints');
const { AUTH_INTERNAL_HEADER_BEARER_TOKEN } = process.env;
const internalAuthToken = AUTH_INTERNAL_HEADER_BEARER_TOKEN?.split(',')?.[0];

const { DeletedCustomers } = require('../models/seqModels/index');

const getDeletedCustomers = async ({ offset = 0, limit = 10, query }) => {
  try {
    const where = query ? applyQuery({ query }) : '';

    const payload = {
      offset: offset * limit,
      limit,
      where,
      order: [['requested_at', 'DESC']]
    };

    const { count, rows: requestData } = await DeletedCustomers.findAndCountAll(
      payload
    );

    return {
      count,
      hits: (requestData || []).map(el => {
        return el.dataValues;
      })
    };
  } catch (e) {
    global.logError(e);
  }
};

const applyQuery = ({ query }) => {
  return {
    [Op.or]: [
      {
        customer_id: { [Op.like]: `%${query}%` }
      },
      {
        email: { [Op.like]: `%${query}%` }
      }
    ]
  };
};

const getCustomerInfo = async customerId => {
  try {
    const reqBody = { customerId };
    const result = await axios.post(CUSTOMER_DETAIL_ENDPOINT, reqBody, {
      headers: {
        'Content-Type': 'application/json',
        'authorization-token': internalAuthToken
      }
    });
    return result?.data?.response?.customer;
  } catch (e) {
    global.logError(e);
    console.log(
      `Error fetching user info ${customerId} for reason ${e.message}`
    );
    return undefined;
  }
};

module.exports = {
  getDeletedCustomers,
  getCustomerInfo
};
