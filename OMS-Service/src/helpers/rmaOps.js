const { OrderComment } = require('../models/seqModels/index');
const { Op } = require('sequelize');

const getRmaComments = async (rmaIncrementId, orderId) => {
  let comments = [];
  try {
    comments = await OrderComment.findAll({
      where: {
        parent_id: orderId,
        comment: { [Op.like]: `%${rmaIncrementId}%` }
      }
    });
  } catch (e) {
    global.logError(e, 'could not fetch comments for RMA: ' + rmaIncrementId);
  }
  return comments;
};

module.exports = {
  getRmaComments
};
