const express = require('express');
const alertController = require('../../controllers/alert.controller');
const internalRestAuth = require('../../middlewares/restInternalAuth');

const router = express.Router();

// router.route('/alerts').get(alertController.sendAlerts);

router
  .route('/processing')
  .get(internalRestAuth, alertController.sendProcessingAlerts);
router
  .route('/noorder')
  .get(internalRestAuth, alertController.sendNoOrderAlerts);
router
  .route('/lessorder')
  .get(internalRestAuth, alertController.sendLessOrderAlerts);
router
  .route('/awb-missing')
  .get(internalRestAuth, alertController.sendAwbMissingAlerts);
router
  .route('/pending-payment')
  .get(internalRestAuth, alertController.sendPendingPaymentAlerts);
router
  .route('/failed-payment')
  .get(internalRestAuth, alertController.sendPaymentFailedAlerts);
router.route('/wms-null').get(internalRestAuth, alertController.wmsNullAlerts);

router.route('/fraud-check').get(internalRestAuth, alertController.fraudCheck);

/**
 * Stuck Order Alert Endpoint
 * 
 * Purpose: Find orders stuck in non-terminated status and send email alerts
 * 
 * Usage: Configure as a cron job to hit this endpoint periodically
 * 
 * Example cron (every 30 minutes):
 * curl -X GET "https://oms-api-qa.stylishop.store/v1/alerts/stuck-orders" \
 *   -H "authorization-token: your-internal-token"
 * 
 * Consul Config: global.javaOrderServiceConfig.order_details.stuck_order_alert
 */
router
  .route('/stuck-orders')
  .get(internalRestAuth, alertController.sendStuckOrderAlerts);

/**
 * ASN Creation Monitor — no ASN in time window and/or open ASNs past threshold.
 * Consul: baseConfig.asn_creation_monitor
 */
router
  .route('/asn-creation-monitor')
  .get(internalRestAuth, alertController.sendAsnCreationMonitor);

module.exports = router;