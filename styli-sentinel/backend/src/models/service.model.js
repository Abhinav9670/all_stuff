const mongoose = require('mongoose');
const mongooseLogsPlugin = require('mongoose-activitylogs');

const serviceModel = new mongoose.Schema(
  {
    _id: String, // domain name abc-qa.stylishop.com
    name: String, // abc service - GCC
    description: String,
    authorization: { type: Boolean, default: true }, // false disable authorization
    authentication: { type: Boolean, default: true }, // false disable authentication
    status: { type: Boolean, default: true }, // false disable check only service level check
    verifyotp: { type: Boolean, default: false }
  },
  { timestamps: true, collection: 'service' }
);

serviceModel.plugin(mongooseLogsPlugin, {
  schemaName: 'service',
  createAction: 'created',
  updateAction: 'updated',
  deleteAction: 'removed'
});
module.exports = mongoose.model('service', serviceModel);
