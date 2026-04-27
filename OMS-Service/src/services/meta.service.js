const mongoose = require('mongoose');
const mongoUtil = require('../utils/mongoInit');
const saveKafkaErrorProducts = async ({
  productFromKafka = {},
  errorMessage = '',
  messageOffset = '',
  retryId
}) => {
  try {
    const db = mongoUtil.getDb();
    if (retryId) {
      return await db.collection('error_products').updateMany(
        { _id: mongoose.Types.ObjectId(retryId) },
        {
          $set: {
            productFromKafka,
            errorMessage,
            updated: new Date()
          }
        }
      );
    }
    await db.collection('error_products').insertOne({
      productFromKafka,
      errorMessage,
      messageOffset,
      created_at: new Date()
    });
  } catch (e) {
    global.logError(e);
  }
};

module.exports = {
  saveKafkaErrorProducts
};
