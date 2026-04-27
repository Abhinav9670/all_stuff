const { Op } = require('sequelize');
const isEmpty = require('lodash.isempty');
const {
  setNotMatch,
  setExactMatch,
  setLikeMatch,
  setInMatch,
  setGTMatch,
  setLTMatch,
  setLTEMatch,
  setGTEMatch
} = require('./sequalizeFilters');

exports.applyQuery = ({ where, query }) => {
  const idsArr = query?.split(',').filter(d => !isEmpty(d));
  return {
    ...where,
    [Op.or]: [
      {
        rma_inc_id: { [Op.in]: idsArr }
      },
      {
        '$Order.increment_id$': { [Op.in]: idsArr }
      }
    ]
  };
};

exports.prepareFilters = filters => {
  let where;
  Object.keys(filters || {}).forEach(el => {
    switch (el) {
      case 'is_created_by_admin': {
        if (filters.is_created_by_admin === 'admin') {
          where = setNotMatch(where, 'is_created_by_admin', '0');
        } else {
          where = setExactMatch(where, 'is_created_by_admin', '0');
        }
        break;
      }
      case 'orderEntityId': {
        where = setExactMatch(where, 'order_id', filters.orderEntityId);
        break;
      }
      case 'customer_name': {
        where = setLikeMatch(where, 'customer_name', filters.customer_name);
        break;
      }
      case 'store_id': {
        if (filters[el].length > 0)
          where = setInMatch(where, 'store_id', filters.store_id);

        break;
      }
      case 'fromDate': {
        where = setGTMatch(where, 'created_at', new Date(filters.fromDate));
        break;
      }
      case 'toDate': {
        where = setLTMatch(where, 'created_at', new Date(filters.toDate));
        break;
      }
      case 'fromId': {
        where = setGTEMatch(where, 'request_id', filters.fromId);
        break;
      }
      case 'toId': {
        where = setLTEMatch(where, 'request_id', filters.toId);
        break;
      }
      case 'status': {
        where = setExactMatch(where, 'status', filters.status);
        break;
      }
      case 'customer_id': {
        where = setExactMatch(where, 'customer_id', filters.customer_id);
        break;
      }
      case 'rma_inc_id': {
        where = setExactMatch(where, 'rma_inc_id', filters.rma_inc_id);
        break;
      }
      default:
        break;
    }
  });
  return where;
};
