const express = require('express');
const paymentRoutes = require('./payment.routes');
const payfortRoutes = require('./payfort.routes');

const router = express.Router();

/**
 * GET v1/status
 */
router.get('/status', (_req, res) => res.send('OK'));

/**
 * GET v1/docs
 */
router.use('/docs', express.static('docs'));
router.use('/payment', paymentRoutes);
router.use('/payfort', payfortRoutes);

/**
 * route for backward compatilibity
 */
router.use('/payment/v2', payfortRoutes);

router.use('/rest', paymentRoutes);



module.exports = router;
