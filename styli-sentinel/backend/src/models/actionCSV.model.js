const Mongoose = require('mongoose');
const Schema = Mongoose.Schema;
const ActionSchema = new Schema(
    {
        action_name: String,
        action_service_name: String,
        action_valid: Boolean,
        action_url: String
    },
    { collection: 'Action' }
);

module.exports = Mongoose.model('Action', ActionSchema);