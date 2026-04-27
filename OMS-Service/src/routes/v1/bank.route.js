const express = require('express');
const router = express.Router();
const bankController = require('../../controllers/bank.controller');
// const auth = require('../../middlewares/auth');
const authValidate = require('../../middlewares/authValidate');
const internalRestAuth = require('../../middlewares/restInternalAuth');
const setupMulter = require('../../middlewares/multerSetup');
const upload = setupMulter()	


router.route('/list').post(authValidate(), bankController.fetchBankTransfers);
router
  .route('/create')
  .post(internalRestAuth, bankController.createBankTransfer);
router
  .route('/upload/list')
  .post(authValidate(), bankController.getBankTransfersHistory);
router
  .route('/status/purge')
  .get(internalRestAuth, bankController.purgeStatusUpdateData);
router
  .route('/process')
  .post(authValidate(), upload.single('file'), bankController.processBankTransfers);

module.exports = router;
