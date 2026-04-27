const express = require('express');
// const auth = require('../../middlewares/auth');
const router = express.Router();
const loggingController = require('../../controllers/logging.controller');
const authValidate = require('../../middlewares/authValidate');

router
  .route('/inventory/:inventory/:sku')
  .get(authValidate(4), loggingController.findInventoryLogs);
router
  .route('/adminlogs')
  .post(authValidate(), loggingController.findAdminLogs);

module.exports = router;
