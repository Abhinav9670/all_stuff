const express = require('express');
const router = express.Router();

const smsController = require('../../controllers/sms.controller');
const internalRestAuth = require('../../middlewares/restInternalAuth');
// const auth = require('../../middlewares/auth');
const authValidate = require('../../middlewares/authValidate');

router
  .route('/failedOrderSMSPush')
  .get(internalRestAuth, smsController.sendFailedOrderSMS);
router
  .route('/fetchSMSList')
  .post(authValidate(), smsController.SMSList);

module.exports = router;
