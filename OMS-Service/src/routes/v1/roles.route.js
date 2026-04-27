const express = require('express');
// const auth = require('../../middlewares/auth');
const rolesController = require('../../controllers/roles/roles-management.controller');
const teamsController = require('../../controllers/roles/teams.controller');
const usersController = require('../../controllers/roles/users.controller');
const authValidate = require('../../middlewares/authValidate');

const router = express.Router();

router.route('/groups').post(authValidate(), rolesController.permissionGroups);
router.route('/group').post(authValidate(), rolesController.permissionGroup);
router
  .route('/group/:id')
  .delete(authValidate(4), rolesController.deletePermissionGroup);

router.route('/teams').post(authValidate(), teamsController.teams);
router.route('/team').post(authValidate(), teamsController.team);
router.route('/team/:id').delete(authValidate(4), teamsController.deleteTeam);

router.route('/users').post(authValidate(), usersController.users);
router.route('/user').post(authValidate(), usersController.user);
router.route('/user/:id').delete(authValidate(4), usersController.deleteUser);

module.exports = router;
