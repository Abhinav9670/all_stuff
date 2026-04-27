const httpStatus = require('http-status');
const catchAsync = require('../../utils/catchAsync');
const teamsService = require('../../services/roles/teams.service');

const teams = catchAsync(async (req, res) => {
  // const ability = req.ability;

  // if (ability.cannot('read', 'Teams')) {
  //   console.log('No permissions to read teams.');
  //   res
  //     .status(httpStatus.UNAUTHORIZED)
  //     .send('User not allowed to fetch teams.');
  // }

  const response = await teamsService.getTeams();
  res.status(httpStatus.OK).json({
    status: true,
    statusCode: '200',
    statusMsg: 'Teams fetched successfully',
    response
  });
});

const team = catchAsync(async (req, res) => {
  // const ability = req.ability;

  // if (ability.cannot('update', 'Teams')) {
  //   console.log('No permissions to update teams.');
  //   res
  //     .status(httpStatus.UNAUTHORIZED)
  //     .send('User not allowed to update teams.');
  // }

  await teamsService.saveTeam({
    team: req.body
  });
  res.status(httpStatus.OK).json({
    status: true,
    statusCode: '200',
    statusMsg: 'Team created successfully'
  });
});

const deleteTeam = catchAsync(async (req, res) => {
  // const ability = req.ability;

  // if (ability.cannot('delete', 'Teams')) {
  //   console.log('No permissions to delete teams.');
  //   res
  //     .status(httpStatus.UNAUTHORIZED)
  //     .send('User not allowed to delete teams.');
  // }

  const { id } = req.params;

  await teamsService.deleteTeam({ id });
  res.status(httpStatus.OK).json({
    status: true,
    statusCode: '200',
    statusMsg: 'Team deleted successfully'
  });
});

module.exports = {
  teams,
  team,
  deleteTeam
};
