const express = require('express');
const restController = require('../../controllers/rest.controller');
// const auth = require('../../middlewares/auth');
const authValidate = require('../../middlewares/authValidate');
const externalRestAuth = require('../../middlewares/restExternalAuth');
const internalRestAuth = require('../../middlewares/restInternalAuth');
const fs = require('fs');
const router = express.Router();
const setupMulter = require('../../middlewares/multerSetup');
const upload = setupMulter();

router
  .route('/shipment-update')
  .post(internalRestAuth, restController.shipmentUpdate);

router.route('/retryPrCall').post(internalRestAuth, restController.retryPrCall);

router.route('/handle-split-order-cancel').post(internalRestAuth, restController.handleShukranRtoCancelled);

router.route('/failedRTOrders').post(internalRestAuth, restController.findFailedRTOrders);
router.route('/failedPrOrders').post(internalRestAuth, restController.retryFailedPrOrders);
router.route('/revertFailedOrderPrCalls').post(internalRestAuth, restController.revertFailedOrderPrCalls);

router
  .route('/shukran-transaction')
  .post(internalRestAuth, restController.shukranTransaction);

router
  .route('/short-check-v2')
  .put(externalRestAuth, restController.shortPickupUpdate);
router.route('/ordersms').post(internalRestAuth, restController.orderSms);
router
  .route('/return-push')
  .post(internalRestAuth, restController.returnPushToWms);

router
  .route('/upload-file')
  .post(authValidate(), upload.single('file'), restController.uploadFile);
router
  .route('/fetch-uploads')
  .post(authValidate(), restController.fetchUploads);

router
  .route('/rto-push-warehouse')
  .get(internalRestAuth, restController.rtoPushWms);

router
  .route('/push-to-increff')
  .post(internalRestAuth, restController.pushToIncreff);

router
  .route('/seller/shipment-update')
  .post(internalRestAuth, restController.sellerShipmentUpdate);

module.exports = router;
