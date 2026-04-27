const express = require('express');
const router = express.Router();
const miscController = require('../../controllers/misc.controller');
// const auth = require('../../middlewares/auth');
const authValidate = require('../../middlewares/authValidate');

// router.route('/processCategories').post(miscController.processCategories);
// router.route('/processEmailAlerts').post(miscController.processEmailAlerts);
router.route('/gerPermissionTargets').get(miscController.permissionTargets);
router.route('/health-check').get(miscController.healthCheck);
router
  .route('/download/file')
  .post(authValidate(), miscController.downloadFileFromGCP);

module.exports = router;
