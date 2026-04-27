const express = require('express');
// const auth = require('../../middlewares/auth');
const rmaController = require('../../controllers/rma.controller');
const rma2Controller = require('../../controllers/rma2.controller');
const authValidate = require('../../middlewares/authValidate');
const fs = require('fs');
const router = express.Router();
const setupMulter = require('../../middlewares/multerSetup');
const upload = setupMulter()	

router.route('/comments').post(authValidate(), rma2Controller.comments);
router.route('/list').post(authValidate(), rmaController.rmaList);
router.route('/detail').post(authValidate(), rmaController.rmaDetail);
router.route('/update').post(authValidate(), rmaController.rmaUpdate);
router.route('/init').post(authValidate(), rmaController.rmaInit);
router.route('/create').post(authValidate(), rmaController.rmaCreate);
router
  .route('/remove/tracking')
  .post(authValidate(), rmaController.rmaRemoveTracking);
router
  .route('/create/tracking/:requestId')
  .get(authValidate(5), rmaController.rmaCreateTracking);

router.route('/recreate').post(authValidate(), upload.single('file'), rmaController.rmaRecreate);

// router
//   .route('/cancel-reason-list')
//   .get(auth('/cancel-reason-list'), rmaController.cancelReasonList);

// router
//   .route('/return-reason-list')
//   .get(auth('/return-reason-list'), rmaController.returnReasonList);

router
  .route('/cancel-reason-list')
  .get(authValidate(), rma2Controller.cancelReasonList);
router
  .route('/return-reason-list')
  .get(authValidate(), rma2Controller.returnReasonList);
router
  .route('/rma-status-list')
  .get(authValidate(), rma2Controller.rmaStatusList);
router
  .route('/rma-status-details/:statusId')
  .get(rma2Controller.rmaStatusDetails);
router
  .route('/update/rma-status-details/')
  .post(authValidate(), rma2Controller.updateRMAStatusDetails);

module.exports = router;
