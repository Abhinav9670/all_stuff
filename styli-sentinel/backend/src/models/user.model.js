const mongoose = require('mongoose');
const mongooseLogsPlugin = require('mongoose-activitylogs');

const userModel = new mongoose.Schema(
  {
    _id: { type: String, unique: true },
    name: String,
    userType: String,
    roles: Array,
    authorization: { type: Boolean, default: true }, // false disable authorization
    authentication: { type: Boolean, default: true }, // false disable authentication
    status: { type: Boolean, default: true }, // false disable check only service level check
    last_logged_in_time: Date,
    uuid: String,
    login_status: { type: Boolean },
  },
  { timestamps: true, collection: 'user' }
);

userModel.plugin(mongooseLogsPlugin, {
  schemaName: 'user',
  createAction: 'created',
  updateAction: 'updated',
  deleteAction: 'removed',
});
module.exports = mongoose.model('user', userModel);