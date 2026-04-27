const express = require('express');
const router = express.Router();

const configController = require('../controllers/base/config.controller');
// const AuthController = require('../controllers/core/auth.controller');

router.get('/health-check', (req, res) =>
  configController.healthCheck({ res, req })
);

module.exports = router;
