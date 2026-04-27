const httpStatus = require('http-status');

const catchAsync = require('../utils/catchAsync');

const { permittedFieldsOf } = require('@casl/ability/extra');
const { dummyData } = require('../constants/index');

const logActivity = require('../utils/logActivity');

const ping = catchAsync(async (req, res) => {
  res.status(httpStatus.OK).send('pong');
});


module.exports = {
  ping
};
