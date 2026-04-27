const mongoose = require('mongoose');
const mongooseLogsPlugin = require('mongoose-activitylogs');

const roleModel = new mongoose.Schema(
  {
    name: String,
    description: String,
    service: Array,
    action: Array,
    status: { type: Boolean, default: true }, // false disable the role
  },
  { timestamps: true, collection: 'role' }
);

roleModel.plugin(mongooseLogsPlugin, {
  schemaName: 'role',
  createAction: 'created',
  updateAction: 'updated',
  deleteAction: 'removed',
});
module.exports = mongoose.model('role', roleModel);