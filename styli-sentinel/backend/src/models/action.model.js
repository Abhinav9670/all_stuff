const mongoose = require('mongoose');
const mongooseLogsPlugin = require('mongoose-activitylogs');

const actionModel = new mongoose.Schema(
    {
        _id: {
            type: String,
        }, // Full url in format: POST-abc-qa.stylishop.com/api/v1/admin/xyz/edit
        name: String, // Edit abc-url xyz-domain - GCC
        domain: String, // abc-qa.stylishop.com
        url: String, // /api/v1/admin/xyz/edit
        httpMethod: String, // POST, GET, PUT, DELETE, OPTIONS, PATCH
        type: String, // R, W
        description: String,
        authorization: { type: Boolean, default: true }, // false disable authorization
        authentication: { type: Boolean, default: true }, // false disable authentication
        status: { type: Boolean, default: true }, // false disable check only service level check
    },
    { timestamps: true, collection: 'action' }
);

actionModel.plugin(mongooseLogsPlugin, {
    schemaName: 'action',
    createAction: 'created',
    updateAction: 'updated',
    deleteAction: 'removed',
});
module.exports = mongoose.model('action', actionModel);
