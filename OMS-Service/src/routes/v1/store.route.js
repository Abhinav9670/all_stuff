const express = require('express');
// const auth = require('../../middlewares/auth');
const storeController = require('../../controllers/store.controller');
const authValidate = require('../../middlewares/authValidate');

const router = express.Router();


// router.route('/list').get(auth('/list'), storeController.storeList);
// router.route('/website-list').get(auth('/website-list'), storeController.websiteList);

router.route('/list').get(authValidate(), storeController.storeList);
router.route('/website-list').get(authValidate(), storeController.websiteList);
router.route('/create-store').post(authValidate(), storeController.createStore);
router.route('/create-website').post(authValidate(), storeController.createWebsite);
router.route('/update-store').post(authValidate(), storeController.updateStoreDetail);
router.route('/detail').post(authValidate(), storeController.getStore);
router
  .route('/warehouse-location-list')
  .get(storeController.getWarehouseLocation);

module.exports = router;
