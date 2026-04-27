const express = require('express');
const router = express.Router();
// const auth = require('../../middlewares/auth');

const configController = require('../../controllers/config.controller');
const authValidate = require('../../middlewares/authValidate');
const internalRestAuth = require('../../middlewares/restInternalAuth');
const authWithInternalFallback = (req, res, next) => {
  // Check if internal token is present
  if (req.headers['authorization-token']) {
    return internalRestAuth(req, res, (err) => {
      if (err) {
        // If internal auth fails, try JWT
        return authValidate()(req, res, next);
      }
      next();
    });
  }
  // No internal token, use JWT
  return authValidate()(req, res, next);
};

router
  .route('/consul/:type')
  .get(authValidate(4), configController.getConsulData);

router
  .route('/consul/save')
  .post(authValidate(), configController.saveConsulData);

router
  .route('/seller-detail/save')
  .post(authWithInternalFallback, configController.saveSellerDetail);

router
  .route('/seller-detail/list')
  .post(authValidate(), configController.getSellerDetailConfigList);

router
  .route('/seller-detail/update/:sellerId')
  .put(authWithInternalFallback, configController.updateSellerDetail);

router
  .route('/seller-detail/update-by-warehouse')
  .post(authWithInternalFallback, configController.updateSellerDetailByWarehouse);

router
  .route('/seller-detail/get/:sellerId')
  .get(authValidate(), configController.getSellerDetail);

router
  .route('/seller-detail/get-by-warehouse')
  .post(authWithInternalFallback, configController.getSellerDetailByWarehouse);

router
  .route('/seller-detail/check-exists')
  .get(authValidate(), configController.checkSellerDetailExists);

// Migration API: Migrate seller_inventory_mapping from Consul to DB
// POST /v1/config/migrate-seller-inventory
// Body: { dryRun: true/false } - defaults to true (dry run) for safety
router
  .route('/migrate-seller-inventory')
  .post( authWithInternalFallback, configController.migrateSellerInventoryToDb);

// router.route('/consul/save').post(configController.saveConsulData);

module.exports = router;
