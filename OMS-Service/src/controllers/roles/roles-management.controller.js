const httpStatus = require('http-status');
const catchAsync = require('../../utils/catchAsync');
const rolesManagementService = require('../../services/roles/roles-management.service');

const permissionGroups = catchAsync(async (req, res) => {
  // const ability = req.ability;

  // if (ability.cannot('read', 'role')) {
  //   console.log('No permissions to read permission groups.');
  //   res
  //     .status(httpStatus.UNAUTHORIZED)
  //     .send('user not allowed to fetch permission groups.');
  // }

  const response = await rolesManagementService.getPermissionGroups();
  res.status(httpStatus.OK).json({
    status: true,
    statusCode: '200',
    statusMsg: 'Permission Groups fetched successfully',
    response
  });
});

const permissionGroup = catchAsync(async (req, res) => {
  // const ability = req.ability;

  // if (ability.cannot('update', 'role')) {
  //   console.log('No permissions to update permission groups.');
  //   res
  //     .status(httpStatus.UNAUTHORIZED)
  //     .send('User not allowed to update permission groups.');
  // }

  await rolesManagementService.savePermissionGroup({
    group: req.body
  });
  res.status(httpStatus.OK).json({
    status: true,
    statusCode: '200',
    statusMsg: 'Permission Group created successfully'
  });
});

const deletePermissionGroup = catchAsync(async (req, res) => {
  // const ability = req.ability;

  // if (ability.cannot('delete', 'role')) {
  //   console.log('No permissions to delete permission groups.');
  //   res
  //     .status(httpStatus.UNAUTHORIZED)
  //     .send('User not allowed to delete permission groups.');
  // }

  const { id } = req.params;

  await rolesManagementService.deletePermissionGroup({ id });
  res.status(httpStatus.OK).json({
    status: true,
    statusCode: '200',
    statusMsg: 'Permission Group deleted successfully'
  });
});

module.exports = {
  permissionGroup,
  permissionGroups,
  deletePermissionGroup
};
