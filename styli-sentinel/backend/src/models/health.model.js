const Mongoose = require('mongoose');

module.exports = Mongoose.model(
  'Health',
  new Mongoose.Schema(
    {
      check: Date
    },
    { collection: 'health' }
  )
);
