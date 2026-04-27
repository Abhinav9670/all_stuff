const httpStatus = require('http-status');
const catchAsync = require('../../utils/catchAsync');
const usersService = require('../../services/roles/users.service');

const users = catchAsync(async (req, res) => {
  // const ability = req.ability;

  // if (ability.cannot('read', 'user')) {
  //   console.log('No permissions to read users.');
  //   res
  //     .status(httpStatus.UNAUTHORIZED)
  //     .send('User not allowed to fetch users.');
  // }

  const response = await usersService.getUsers({});
  res.status(httpStatus.OK).json({
    status: true,
    statusCode: '200',
    statusMsg: 'Users fetched successfully',
    response
  });
});

const user = catchAsync(async (req, res) => {

  const result = await usersService.saveUser({
    user: req.body
  });
  let statusCode = '200';
  let statusMsg = 'User created successfully';
  if (result?.status === 'error') {
    statusCode = '201';
    statusMsg = result?.result?.message || 'Something went wrong!';
  }

  res.status(httpStatus.OK).json({
    status: true,
    statusCode,
    statusMsg
  });
});

const deleteUser = catchAsync(async (req, res) => {
  const { id } = req.params;

  await usersService.deleteUser({ id });
  res.status(httpStatus.OK).json({
    status: true,
    statusCode: '200',
    statusMsg: 'User deleted successfully'
  });
});

module.exports = {
  users,
  user,
  deleteUser
};
