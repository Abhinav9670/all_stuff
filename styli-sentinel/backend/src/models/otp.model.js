const mongoose = require('mongoose');
const mongooseLogsPlugin = require('mongoose-activitylogs');

const otpModel = new mongoose.Schema(
  {
    _id: String,
    otp: String,
    userData: {
      type: mongoose.Schema.Types.Mixed,
      default: {}
    },
    domain: String
  },
  { timestamps: true, collection: 'login_otp' }
);

otpModel.plugin(mongooseLogsPlugin, {
  schemaName: 'login_otp',
  createAction: 'created',
  updateAction: 'updated',
  deleteAction: 'removed',
});
module.exports = mongoose.model('login_otp', otpModel);