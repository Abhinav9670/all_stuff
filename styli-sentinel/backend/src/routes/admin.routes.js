const express = require('express');
const router = express.Router();

const manageRoutesController = require('../controllers/admin/manage-routes.controller');
const userController = require('../controllers/admin/user.controller');
const rolesController = require('../controllers/admin/roles.controller');

const { serviceAddRequestValidator, deleteRequestValidator } = require('../validator/service.validate');
const { serviceActionAddRequestValidator, bulkRequestRequestValidator } = require('../validator/action.validate');
const { roleAddRequestValidator, roleUpdateRequestValidator } = require('../validator/role.validate');
const { userAddRequestValidator, deleteuserValidator } = require('../validator/user.validate');
const { authServiceCheck } = require('../helper');

router.post('/service/add', serviceAddRequestValidator, authServiceCheck, (req, res) =>
  manageRoutesController.serviceAdd({ res, req })
);

router.put('/service/add', serviceAddRequestValidator, authServiceCheck, (req, res) =>
  manageRoutesController.serviceUpdate({ res, req })
);

router.post('/service', authServiceCheck, (req, res) => manageRoutesController.serviceList({ res, req }));

router.post('/service/update', authServiceCheck, (req, res) => manageRoutesController.serviceStatus({ res, req }));
router.post('/service/delete', deleteRequestValidator, authServiceCheck, (req, res) =>
  manageRoutesController.serviceRemove({ res, req })
);

router.post('/service/status', deleteRequestValidator, authServiceCheck, (req, res) =>
  manageRoutesController.serviceStatus({ res, req })
);

router.post('/action/add', serviceActionAddRequestValidator, authServiceCheck, (req, res) =>
  manageRoutesController.serviceActionAdd({ res, req })
);

router.put('/action/add', serviceActionAddRequestValidator, authServiceCheck, (req, res) =>
  manageRoutesController.serviceActionUpdate({ res, req })
);

router.post('/action', authServiceCheck, (req, res) => manageRoutesController.serviceActionList({ res, req }));

router.post('/action/delete', deleteRequestValidator, authServiceCheck, (req, res) =>
  manageRoutesController.serviceActionRemove({ res, req })
);

router.post('/action/delete', deleteRequestValidator, authServiceCheck, (req, res) =>
  manageRoutesController.serviceActionStatus({ res, req })
);

// bulk upload without file
router.post('/bulk-upload', bulkRequestRequestValidator, authServiceCheck, (req, res) =>
  manageRoutesController.bulkUpload({ res, req })
);

router.post('/user/add', userAddRequestValidator, authServiceCheck, (req, res) => userController.userAdd({ res, req }));

router.put('/user/add', userAddRequestValidator, authServiceCheck, (req, res) =>
  userController.userUpdate({ res, req })
);

router.post('/users', authServiceCheck, (req, res) => userController.usersList({ res, req }));

router.post('/user/delete', deleteuserValidator, authServiceCheck, (req, res) =>
  userController.userRemove({ res, req })
);
//roles

router.post('/roles/add', roleAddRequestValidator, authServiceCheck, (req, res) =>
  rolesController.roleAdd({ res, req })
);

router.put('/roles/add', roleUpdateRequestValidator, authServiceCheck, (req, res) =>
  rolesController.roleUpdate({ res, req })
);

router.post('/roles', authServiceCheck, (req, res) => rolesController.roleList({ res, req }));

router.post('/roles/delete', deleteRequestValidator, authServiceCheck, (req, res) =>
  rolesController.roleRemove({ res, req })
);

module.exports = router;
