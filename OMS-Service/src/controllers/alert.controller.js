const catchAsync = require('../utils/catchAsync');
const {
  processAlerts,
  alertProcessingOrder,
  alertNoOrder,
  alertAwbMissing,
  pendingOrHoldPayment,
  paymentFailed,
  alertLessOrderInAnHour
} = require('../helpers/alerts/index');
const { getStoreCountryMap } = require('../utils/config');
const { wmsNull, fraudCheck } = require('../helpers/alerts/alert');
const { processStuckOrderAlert } = require('../helpers/alerts/stuckOrderAlert');
const { processAsnCreationMonitor } = require('../helpers/alerts/asnCreationMonitor');
const logger = require('../config/logger');

exports.sendAlerts = catchAsync(async (req, res) => {
  try {
    processAlerts();
  } catch (e) {
    global.logError(e.message);
    res.status(500).json({ error: e.message });
  }
});

exports.sendProcessingAlerts = catchAsync(async (req, res) => {
  try {
    const storeCountryMap = getStoreCountryMap();
    const { fromEmail, fromName } = global?.baseConfig?.emailConfig || {};
    await alertProcessingOrder({ storeCountryMap, fromEmail, fromName });
    res.status(200).json({ status: true });
  } catch (e) {
    global.logError(e.message);
    res.status(500).json({ error: e.message });
  }
});

exports.sendNoOrderAlerts = catchAsync(async (req, res) => {
  try {
    const storeCountryMap = getStoreCountryMap();
    const { fromEmail, fromName } = global?.baseConfig?.emailConfig || {};
    await alertNoOrder({ storeCountryMap, fromEmail, fromName });
    res.status(200).json({ status: true });
  } catch (e) {
    global.logError(e.message);
    res.status(500).json({ error: e.message });
  }
});

exports.sendLessOrderAlerts = catchAsync(async (req, res) => {
  try {
    const { fromEmail, fromName } = global?.baseConfig?.emailConfig || {};
    await alertLessOrderInAnHour({ fromEmail, fromName });
    res.status(200).json({ status: true });
  } catch (e) {
    global.logError(e.message);
    res.status(500).json({ error: e.message });
  }
});

exports.sendAwbMissingAlerts = catchAsync(async (req, res) => {
  try {
    const storeCountryMap = getStoreCountryMap();
    const { fromEmail, fromName } = global?.baseConfig?.emailConfig || {};
    await alertAwbMissing({ storeCountryMap, fromEmail, fromName });
    res.status(200).json({ status: true });
  } catch (e) {
    global.logError(e.message);
    res.status(500).json({ error: e.message });
  }
});

exports.sendPendingPaymentAlerts = catchAsync(async (req, res) => {
  try {
    const storeCountryMap = getStoreCountryMap();
    const { fromEmail, fromName } = global?.baseConfig?.emailConfig || {};
    await pendingOrHoldPayment({ storeCountryMap, fromEmail, fromName });
    res.status(200).json({ status: true });
  } catch (e) {
    global.logError(e.message);
    res.status(500).json({ error: e.message });
  }
});

exports.sendPaymentFailedAlerts = catchAsync(async (req, res) => {
  try {
    const storeCountryMap = getStoreCountryMap();
    const { fromEmail, fromName } = global?.baseConfig?.emailConfig || {};
    await paymentFailed({ storeCountryMap, fromEmail, fromName });
    res.status(200).json({ status: true });
  } catch (e) {
    global.logError(e.message);
    res.status(500).json({ error: e.message });
  }
});

exports.wmsNullAlerts = catchAsync(async (req, res) => {
  try {
    const storeCountryMap = getStoreCountryMap();
    const { fromEmail, fromName } = global?.baseConfig?.emailConfig || {};
    await wmsNull({ storeCountryMap, fromEmail, fromName });
    res.status(200).json({ status: true });
  } catch (e) {
    global.logError(e.message);
    res.status(500).json({ error: e.message });
  }
});

exports.fraudCheck = catchAsync(async (req, res) => {
  try {
    await fraudCheck();
    res.status(200).json({ status: true });
  } catch (e) {
    global.logError(e.message);
    res.status(500).json({ error: e.message });
  }
});

/**
 * Stuck Order Alert API
 * 
 * Purpose: Find orders that are "stuck" (not in terminated status) and send email alerts
 * 
 * This endpoint is designed to be called by a cron job at regular intervals (e.g., every 30 minutes)
 * 
 * Consul Configuration Required:
 * Path: java/order-service/credentials_{env} -> order_details.stuck_order_alert
 * 
 * 
 * 
 * @route GET /v1/alerts/stuck-orders
 * @access Internal (requires authorization-token header)
 */
exports.sendStuckOrderAlerts = catchAsync(async (req, res) => {
  try {
    logger.info('[StuckOrderAlert] API endpoint called');
    
    // Get email config from baseConfig (same pattern as other alerts)
    const { fromEmail, fromName } = global?.baseConfig?.emailConfig || {};
    
    logger.info(`[StuckOrderAlert] Using sender: ${fromName} <${fromEmail}>`);
    
    // Process the stuck order alert
    const result = await processStuckOrderAlert({ fromEmail, fromName });
    
    logger.info(`[StuckOrderAlert] API response: ${JSON.stringify(result)}`);
    
    // Return appropriate status based on result
    if (result.success) {
      res.status(200).json({
        status: true,
        message: result.message,
        data: {
          ordersFound: result.ordersFound,
          emailSent: result.emailSent,
          recipients: result.recipients || [],
          executionTimeMs: result.executionTimeMs
        }
      });
    } else {
      res.status(200).json({
        status: false,
        message: result.message,
        data: {
          ordersFound: result.ordersFound,
          emailSent: result.emailSent,
          executionTimeMs: result.executionTimeMs
        }
      });
    }
  } catch (e) {
    logger.error(`[StuckOrderAlert] API error: ${e.message}`);
    global.logError(e.message);
    res.status(500).json({ 
      status: false,
      error: e.message 
    });
  }
});

/**
 * ASN Creation Monitor — no ASN in time window and/or open ASNs past threshold.
 * Consul: baseConfig.asn_creation_monitor, asnTimeThresholdMinutes, SELLER_CENTRAL_ASN_CLOSE_MINUTES.
 */
exports.sendAsnCreationMonitor = catchAsync(async (req, res) => {
  try {
    const { fromEmail, fromName } = global?.baseConfig?.emailConfig || {};
    const result = await processAsnCreationMonitor({ fromEmail, fromName });
    res.status(200).json({
      status: true,
      alertSent: result.alertSent,
      count: result.count,
      staleOpenCount: result.staleOpenCount ?? 0,
      message: result.message
    });
  } catch (e) {
    logger.error(`[AsnCreationMonitor] API error: ${e.message}`);
    global.logError(e.message);
    res.status(500).json({ status: false, error: e.message });
  }
});