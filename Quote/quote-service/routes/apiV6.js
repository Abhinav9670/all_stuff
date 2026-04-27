const express = require('express');
const router = express.Router({ mergeParams: true });
const { pool } = require('../helpers/mysqlPool');
const cache = require('memory-cache');

const quote_controller = require('../controllers/v6/quoteController');
const shukranController = require('../controllers/v6/shukranController');

router.post("/get/basic", (req, res) =>
  quote_controller.getQuote({ req, res, pool, resetNotifs: false })
);
router.post("/get/basic2", (req, res) =>
  quote_controller.getQuoteBasicData({ req, res, pool, resetNotifs: false })
);
router.post('/get/totals', (req, res) => quote_controller.getQuoteTotals({ req, res, pool }));
router.post('/get', (req, res) => {
  const baseConfig = cache.get("baseConfig") || {};
  const enablev6getoptimization = baseConfig?.enablev6getoptimization || false;
  // console.log(`Global Config enablev6getoptimization : ${enablev6getoptimization}, JSON.stringify(req?.body) : ${JSON.stringify(req?.body)} X-Header-Token : ${req?.headers["x-header-token"]}`);
  if(!enablev6getoptimization === true || req?.body?.optimized === true){
    return quote_controller.getQuoteOptimized({ req, res, pool });
  } else {
    return quote_controller.getQuote({ req, res, pool });
  }
});
router.post('/update', (req, res) => quote_controller.updateQuotewithShukrandata({ req, res, pool }));

router.post('/verify/preferred_payment', (req, res) => quote_controller.verifyPreferredPayment({ req, res }));
router.post('/retry-payment/replica', (req, res) =>
  quote_controller.retryPaymentFailedReplica({ req, res, pool })
);

router.post('/remove-message', (req, res) => quote_controller.removeWishlistMessageFromBag({ req, res }));

router.post('/getShukranPointBalance', (req, res) => 
  shukranController.getShukranPointBalance({ req, res, pool })
);
router.post('/save', (req, res) =>
  quote_controller.addToQuote({ req, res })
);

module.exports = router;
