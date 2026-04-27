const express = require('express');
const router = express.Router({ mergeParams: true });
const { pool } = require('../helpers/mysqlPool');

const quote_controller = require('../controllers/v7/quoteController');

//Added for v7 version
router.post('/get', (req, res) => quote_controller.getQuote({ req, res, pool }));
router.post('/get/totals', (req, res) => quote_controller.getQuoteTotals({ req, res, pool }));



module.exports = router;