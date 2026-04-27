/**
 * ASN Creation Monitor Alert
 *
 * Purpose: Monitor the seller_asn table with two checks (monitoring only — does not block any flow).
 * 1. No ASN created in the last timeWindowHours (e.g. 6h) → alert.
 * 2. Open ASNs past close threshold (asnTimeThresholdMinutes / SELLER_CENTRAL_ASN_CLOSE_MINUTES) still not pushed → alert.
 *
 * Consul: global.baseConfig.asn_creation_monitor (timeWindowHours, email);
 *         global.baseConfig.asnTimeThresholdMinutes, SELLER_CENTRAL_ASN_CLOSE_MINUTES for thresholds.
 */

const { SellerAsn } = require('../../models/seqModels/index');
const { Op } = require('sequelize');
const moment = require('moment');
const { sendSgEmail } = require('../../services/email.service');
const logger = require('../../config/logger');
const { getOpenAsnRecordsWithThresholds } = require('../sellerAsnHelper');

const DEFAULT_TIME_WINDOW_HOURS = 12;

/**
 * Get ASN creation monitor config from Consul (global.baseConfig.asn_creation_monitor).
 * @returns {Object} { timeWindowHours, email: { enabled, recipients } }
 */
const getAsnMonitorConfig = () => {
  const monitor = global?.baseConfig?.asn_creation_monitor || {};
  const timeWindowHours =
    typeof monitor.timeWindowHours === 'number' && monitor.timeWindowHours > 0
      ? monitor.timeWindowHours
      : DEFAULT_TIME_WINDOW_HOURS;
  const email = monitor.email || {};
  const enabled = email.enabled === true || email.enabled === 'true';
  const recipients = Array.isArray(email.recipients) ? email.recipients : [];
  return { timeWindowHours, email: { enabled, recipients } };
};

/**
 * Count ASNs with startTime within the last timeWindowHours and fetch latest ASN for context.
 * Uses startTime column (seller_asn table) for the time window.
 * @param {number} timeWindowHours
 * @returns {Promise<{ count: number, timeWindowHours: number, timeBoundary: Date, latestAsn: Object|null }>}
 */
const countRecentAsns = async (timeWindowHours) => {
  const timeBoundary = moment().subtract(timeWindowHours, 'hours').toDate();

  const count = await SellerAsn.count({
    where: { startTime: { [Op.gte]: timeBoundary } }
  });

  const latestAsn = await SellerAsn.findOne({
    attributes: ['id', 'asn_number', 'startTime', 'status'],
    order: [['startTime', 'DESC']]
  });

  return {
    count,
    timeWindowHours,
    timeBoundary,
    latestAsn: latestAsn ? latestAsn.get({ plain: true }) : null
  };
};

/**
 * Get open ASNs that have passed their close threshold (still open, not pushed).
 * Uses Consul: asnTimeThresholdMinutes (standard), SELLER_CENTRAL_ASN_CLOSE_MINUTES (seller central).
 * @returns {Promise<{ totalCount: number, standardCount: number, sellerCentralCount: number, standardThreshold: number, sellerCentralThreshold: number, asnNumbers: string[] }>}
 */
const getStaleOpenAsns = async () => {
  const consulConfig = global?.baseConfig || {};
  const standardThreshold = consulConfig.asnTimeThresholdMinutes || 60;
  const sellerCentralThreshold = consulConfig.SELLER_CENTRAL_ASN_CLOSE_MINUTES || 60;

  const result = await getOpenAsnRecordsWithThresholds(standardThreshold, sellerCentralThreshold);
  if (!result.success) {
    logger.warn('[AsnCreationMonitor] getOpenAsnRecordsWithThresholds failed:', result.message);
    return {
      totalCount: 0,
      standardCount: 0,
      sellerCentralCount: 0,
      standardThreshold,
      sellerCentralThreshold,
      asnNumbers: []
    };
  }

  const standardRecords = result.standardRecords || [];
  const sellerCentralRecords = result.sellerCentralRecords || [];
  const allRecords = [...standardRecords, ...sellerCentralRecords];
  const asnNumbers = allRecords.map((r) => r.asn_number).filter(Boolean);

  return {
    totalCount: result.totalCount || 0,
    standardCount: standardRecords.length,
    sellerCentralCount: sellerCentralRecords.length,
    standardThreshold,
    sellerCentralThreshold,
    asnNumbers
  };
};

/**
 * Build HTML email content for the ASN creation monitor alert.
 * Supports two conditions: no recent ASN creation, and open ASNs past threshold.
 * @param {Object} monitorResult - result from countRecentAsns
 * @param {Object} config - from getAsnMonitorConfig
 * @param {Object} [staleResult] - result from getStaleOpenAsns (when open ASNs past threshold)
 * @returns {{ subject: string, html: string }}
 */
const buildEmailContent = (monitorResult, config, staleResult = null) => {
  const { count, timeWindowHours } = monitorResult;
  const noCreation = count === 0;
  const hasStaleOpen = staleResult && staleResult.totalCount > 0;
  const checkedAt = moment().utc().format('YYYY-MM-DD HH:mm:ss');

  const subjectParts = [];
  if (noCreation) subjectParts.push(`No ASN in last ${timeWindowHours}h`);
  if (hasStaleOpen) subjectParts.push(`${staleResult.totalCount} open ASN(s) past threshold`);
  const subject = `[OMS] ASN Creation Monitor — ${subjectParts.join('; ')}`;

  let alertsHtml = '';
  if (noCreation) {
    alertsHtml += `
      <div style="background: #f9fafb; border: 1px solid #e5e7eb; border-left: 4px solid #f59e0b; border-radius: 6px; padding: 20px 24px; margin-bottom: 16px;">
        <p style="font-size: 15px; font-weight: 600; margin: 0 0 8px; color: #374151;">No ASN was created in the last ${timeWindowHours} hour(s).</p>
      </div>`;
  }
  if (hasStaleOpen) {
    const asnList = staleResult.asnNumbers.length > 0 ? `<p style="font-size: 13px; margin: 8px 0 0; color: #6b7280;">ASN(s): ${staleResult.asnNumbers.slice(0, 20).join(', ')}${staleResult.asnNumbers.length > 20 ? '…' : ''}</p>` : '';
    alertsHtml += `
      <div style="background: #f9fafb; border: 1px solid #e5e7eb; border-left: 4px solid #f59e0b; border-radius: 6px; padding: 20px 24px;">
        <p style="font-size: 15px; font-weight: 600; margin: 0; color: #374151;">${staleResult.totalCount} open ASN(s) past close threshold (not pushed).</p>
        ${asnList}
      </div>`;
  }

  const html = `
    <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; max-width: 520px; margin: 0 auto; padding: 24px; color: #1a1a1a;">
      <div style="font-size: 11px; font-weight: 600; letter-spacing: 0.5px; color: #6b7280; margin-bottom: 12px; text-transform: uppercase;">Monitoring Alert</div>
      <h1 style="font-size: 20px; font-weight: 600; margin: 0 0 6px; color: #111;">ASN Creation Monitor</h1>
      <p style="font-size: 14px; line-height: 1.5; color: #4b5563; margin: 0 0 24px;">No action required unless you suspect an issue.</p>
      ${alertsHtml}
      <p style="font-size: 13px; margin: 0; color: #6b7280;">Checked at (UTC): ${checkedAt}</p>
    </div>
  `;

  return { subject, html };
};

/**
 * Main entry: run both checks (no recent ASN, open ASNs past threshold) and send email if either condition is met.
 * @param {Object} options - optional { fromEmail, fromName } (defaults from global.baseConfig.emailConfig)
 * @returns {Promise<{ alertSent: boolean, count: number, staleOpenCount: number, message: string }>}
 */
const processAsnCreationMonitor = async ({ fromEmail, fromName } = {}) => {
  const startTime = Date.now();
  const emailConfig = global?.baseConfig?.emailConfig || {};
  const from = {
    email: fromEmail || emailConfig.fromEmail,
    name: fromName || emailConfig.fromName
  };

  const config = getAsnMonitorConfig();
  logger.info(
    `[AsnCreationMonitor] Running checks: timeWindowHours=${config.timeWindowHours}, email.enabled=${config.email.enabled}`
  );

  const [monitorResult, staleResult] = await Promise.all([
    countRecentAsns(config.timeWindowHours),
    getStaleOpenAsns()
  ]);

  const noRecentAsn = monitorResult.count === 0;
  const hasStaleOpen = staleResult.totalCount > 0;
  const alertNeeded = noRecentAsn || hasStaleOpen;

  if (!alertNeeded) {
    logger.info(
      `[AsnCreationMonitor] OK: ${monitorResult.count} ASN(s) in last ${config.timeWindowHours}h, 0 stale open ASNs. No alert.`
    );
    return {
      alertSent: false,
      count: monitorResult.count,
      staleOpenCount: 0,
      message: `No alert: ${monitorResult.count} ASN(s) created in last ${config.timeWindowHours}h, 0 open ASNs past threshold.`
    };
  }

  if (!config.email.enabled || !config.email.recipients.length) {
    logger.warn(
      '[AsnCreationMonitor] Alert condition met but email disabled or no recipients. Skipping send.'
    );
    return {
      alertSent: false,
      count: monitorResult.count,
      staleOpenCount: staleResult.totalCount,
      message: `Alert condition met (noCreation=${noRecentAsn}, staleOpen=${staleResult.totalCount}); email not sent (disabled or no recipients).`
    };
  }

  const { subject, html } = buildEmailContent(monitorResult, config, staleResult);
  const emailSent = await sendSgEmail({
    to: config.email.recipients.join(','),
    from,
    subject,
    html
  });

  const duration = Date.now() - startTime;
  logger.info(
    `[AsnCreationMonitor] Alert email ${emailSent ? 'sent' : 'failed'} to ${config.email.recipients.join(', ')} (${duration}ms)`
  );

  return {
    alertSent: emailSent,
    count: monitorResult.count,
    staleOpenCount: staleResult.totalCount,
    message: emailSent
      ? `Alert sent: noCreation=${noRecentAsn}, staleOpen=${staleResult.totalCount}.`
      : 'Alert email send failed.'
  };
};

module.exports = {
  processAsnCreationMonitor,
  getAsnMonitorConfig,
  countRecentAsns,
  getStaleOpenAsns,
  buildEmailContent
};
