const mongoUtil = require('../utils/mongoInit');

const saveInventoryLog = async message => {
  try {
    message.createdAt = new Date();
    const db = mongoUtil.getDb();
    await db.collection('inventorylogging').insertOne(message);
  } catch (error) {
    console.log('Error in saving inventory log. Error : ' + error);
  }
};

const findInventoryLogBySku = async (sku, inventory) => {
  const db = mongoUtil.getDb();
  try {
    const rs = await db
      .collection('inventorylogging')
      .find({ sku, inventory })
      .toArray();
    if (rs) {
      const logs = rs.map(r => {
        return {
          timestamp: r.timestamp,
          action: r,
          action_type: r.operation,
          user: r.modifiedBy,
          sku: r.sku
        };
      });
      logs.sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp));
      return logs;
    }
    return [];
  } catch (error) {
    console.log(`Error in feting inventory logs : ${error.message}`);
    return [];
  }
};

module.exports = { saveInventoryLog, findInventoryLogBySku };