const { Order } = require('../models/seqModels/archiveIndex');

exports.getArchivedOrders = async ({ where }) => {
  let orders = [];
  try {
    orders = await Order.findAll({ where, raw: true });
  } catch (e) {
    global.logError('Error fetching archive orderList', e);
  }
  return orders;
};
