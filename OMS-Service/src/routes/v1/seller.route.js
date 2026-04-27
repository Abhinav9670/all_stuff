const express = require('express');
const router = express.Router();
const sellerController = require('../../controllers/seller.controller');
const authValidate = require('../../middlewares/authValidate');
const internalRestAuth = require('../../middlewares/restInternalAuth');
const setupMulter = require('../../middlewares/multerSetup');

const upload = setupMulter();

const authWithInternalFallback = (req, res, next) => {
  if (req.headers['authorization-token']) {
    return internalRestAuth(req, res, err => {
      if (err) {
        return authValidate()(req, res, next);
      }
      return next();
    });
  }
  return authValidate()(req, res, next);
};

router
  .route('/commission-details')
  .get(authWithInternalFallback, sellerController.getCommissionDetailsBySellerId)
  .post(authWithInternalFallback, upload.single('file'), sellerController.upsertCommissionDetails);

module.exports = router;

