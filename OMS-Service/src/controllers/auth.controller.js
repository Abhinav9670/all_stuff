const httpStatus = require('http-status');
const catchAsync = require('../utils/catchAsync');
const { authService } = require('../services');


const permissionList = catchAsync(async (req, res) => {
  const { email } = req.body;
  const data = await authService.getPermissionList({ email });

  const payload = {
    status: true,
    statusCode: '200',
    statusMsg: 'Permissions found!',
    response: data
  };
  res.status(httpStatus.OK).json(payload);
});

module.exports = {
  permissionList
};
