const mongoUtil = require('../utils/mongoInit');
const MONGO_MAX_NODE_COUNT = process.env.MONGO_NODE_COUNT;
const { sequelize } = require('../models/seqModels/index');

exports.checkMongoHealth = async () => {
  let mongoHealth = true;
  let activeNodes = MONGO_MAX_NODE_COUNT;
  try {
    const db = mongoUtil.getDb();
    const isMasterRes = await db.command({ isMaster: 1 });
    console.log(
      JSON.stringify(isMasterRes),
      '[checkMongoV2] - Response of isMaster() from Mongo health check.'
    );

    if (isMasterRes.hosts.length < MONGO_MAX_NODE_COUNT) {
      mongoHealth = false;
      activeNodes = isMasterRes.hosts.length;
    }
  } catch (e) {
    mongoHealth = false;
    activeNodes = 0;
    console.log(e.message, '[checkMongoV2] - IsMaster Error');
  }
  return { mongoHealth, activeNodes };
};

exports.checkMysqlHealth = async () => {
  let sqlHealth = false;
  try {
    await sequelize.authenticate();
    sqlHealth = true;
  } catch (err) {
    console.error('Unable to connect to the My sql:', err);
  }
  return sqlHealth;
};
