const healthModel = require('../../models/health.model');
const moment = require('moment');

exports.healthCheck = async ({ res, req }) => {
  try {
    const currentTimeStampInUTC = moment.utc().toDate().toUTCString();
    const health = await healthModel.findOne({});
    if (health) {
      await healthModel.findByIdAndUpdate(health._id, {
        check: currentTimeStampInUTC
      });
    } else {
      await healthModel.create({
        check: currentTimeStampInUTC
      });
    }
    return res.status(200).send({ status: 'Success' });
  } catch (errror) {
    console.log(errror);
    return res.status(500).send({ status: 'failed', message: errror.message });
  }
};
