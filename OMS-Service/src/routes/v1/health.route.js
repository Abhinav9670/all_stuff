const express = require('express');
// const auth = require('../../middlewares/auth');
const healthController = require('../../controllers/health.controller');
const authValidate = require('../../middlewares/authValidate');

const router = express.Router();
router
  .route('/ping')
  .post(authValidate(), healthController.ping)
  .get(authValidate(), healthController.ping);

module.exports = router;
