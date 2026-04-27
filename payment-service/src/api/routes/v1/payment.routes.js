const express = require('express');
const router = express.Router();
const controller = require('../../controllers/payment.controller');
const { validateJwt } = require('../../middlewares/jwt');
const cfController = require('../../controllers/cashfree.controller');
const { validateInternalAuth } = require('../../middlewares/internalAuth');

router.post('/options', validateInternalAuth, controller.paymentOptions);
router.post('/session/clear', controller.clearSession);
router.post('/configs', validateJwt, controller.configs);
router.post('/payfortlogs', controller.payFortLogs);

router.post('/cashfree', validateJwt, (req, res) => {
  cfController.cfPayment(req, res);
});

router.all('/cashfree/return', (req, res) => {
  cfController.cfReturn(req, res);
});

router.get('/health-check', (req, res) => controller.healthCheck({ res, req }));

module.exports = router;
