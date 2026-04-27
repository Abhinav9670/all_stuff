const express = require('express');
const router = express.Router();
const { pool } = require('../helpers/mysqlPool');
const routesV6 = require('./apiV6.js');
const routesV7 = require('./apiV7.js');

const quote_controller = require('../controllers/quoteController');
const address_controller = require('../controllers/addressController');
const misc_controller = require('../controllers/miscController');
const abandonedCartController = require('../controllers/abandonedCartController');
const heapSnapshotController = require('../controllers/heapSnapshotController');
const { validateExternalToken , validateInternalToken } = require('../helpers/validateToken');
const { validateCouponCharacters } = require('../helpers/utils.js');

router.get('/rest/quote', (req, res) =>
  res.send('This response is from quote service')
);
router.get('/rest/quote/health-check', (req, res) =>
  misc_controller.healthCheck({ res, req, pool })
);
router.get('/rest/quote/couch-health-check', (req, res) =>
  misc_controller.couchBaseHealthCheck({ res, req })
);
router.get('/rest/quote/customer-health-check', (req, res) =>
  misc_controller.customerHealthCheck({ res, req })
);
router.get('/rest/quote/address-health-check', (req, res) =>
  misc_controller.addressHealthCheck({ res, req })
);

// Public endpoint for capturing heap snapshots (no authentication required)
router.get('/rest/quote/heap-snapshot', (req, res) =>
  heapSnapshotController.captureHeapSnapshot(req, res)
);

router.post('/rest/quote/auth/v5/get/unprocessed', (req, res) =>
  quote_controller.getQuoteUnprocessed({ req, res })
);

router.post('/rest/quote/auth/v5/getNew', (req, res) =>
  quote_controller.getQuoteNew({ req, res })
);

router.post('/rest/quote/auth/v5', (req, res) =>
  quote_controller.addToQuote({ req, res })
);
router.delete('/rest/quote/auth/v5', (req, res) =>
  quote_controller.deleteItem({ req, res })
);
router.put('/rest/quote/auth/v5', (req, res) =>
  quote_controller.updateQty({ req, res })
);
router.put('/rest/quote/auth/v5/changesize', (req, res) =>
  quote_controller.changeSize({ req, res })
);
router.post('/rest/quote/auth/v5/address', (req, res) =>
  address_controller.updateAddress({ req, res })
);
router.post('/rest/quote/auth/v5/migrate', (req, res) =>
  quote_controller.migrateQuote({ req, res })
);
router.post('/rest/quote/auth/v5/view/count', (req, res) =>
  quote_controller.getCount({ req, res })
);
router.post('/rest/quote/auth/v5/disable', (req, res) =>
  quote_controller.disableQuote({ req, res })
);
router.post('/rest/quote/auth/v5/replica', (req, res) =>
  quote_controller.enableQuote({ req, res })
);
router.post('/rest/quote/auth/v5/coupon', validateCouponCharacters, (req, res) =>
  quote_controller.applyCoupon({ req, res })
);
router.delete('/rest/quote/auth/v5/coupon', (req, res) =>
  quote_controller.deleteCoupon({ req, res })
);
router.get('/rest/quote/auth/v5/view/metadata', (req, res) =>
  quote_controller.getMetadata({ req, res })
);
router.get('/rest/quote/auth/v5/view/validate', (req, res) =>
  quote_controller.validate({ req, res })
);
router.post('/rest/quote/auth/v5/storecredit', (req, res) =>
  quote_controller.applyStoreCredit({ req, res })
);
router.delete('/rest/quote/auth/v5/storecredit', (req, res) =>
  quote_controller.deleteStoreCredit({ req, res })
);
router.post('/rest/quote/auth/v5/payment', (req, res) =>
  quote_controller.payment({ req, res })
);
router.post('/rest/quote/auth/v5/view/config', (req, res) =>
  misc_controller.updateConfigValues({ req, res })
);

router.post('/rest/quote/auth/v5/savestorecredit', (req, res) =>
  quote_controller.saveStoreCreditFromOrderAPI({ req, res })
);

router.post('/rest/quote/save/preferred-payment', validateInternalToken ,(req, res) => 
  quote_controller.savePaymentMethodInQuote({ req, res })
);

// router.post('/rest/quote/auth/v5/migrate-quote', (req, res) =>
//   quote_controller.migrateOldQuote({ req, res, pool })
// );

router.post(
  "/rest/quote/webhook/price-drop",
  validateExternalToken,
  misc_controller.processPriceDrop
);

router.use('/rest/quote/auth/v6', routesV6);

router.post(
  "/rest/quote/webhook/free-shipping",
  validateExternalToken,
  misc_controller.processFreeShipping
);
router.post(
  "/rest/quote/abandon-cart",
  // validateExternalToken,
  abandonedCartController.processAbandonedCart
);
router.post(
  "/rest/quote/webhook/abandonCartDetail",
  validateExternalToken,
  abandonedCartController.handleAbandonCartDetails
);
router.use('/rest/quote/auth/v7', routesV7);
module.exports = router;
