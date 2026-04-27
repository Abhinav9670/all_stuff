/**
 * Stuck Order Alert Module - SELLER CENTRAL ORDERS ONLY
 * 
 * Purpose: Identify SELLER CENTRAL orders that are "stuck" (not in terminated status) 
 *          and send email alerts to tech support.
 * 
 * How Seller Central Orders are Identified:
 * - Uses Consul config: global.javaOrderServiceConfig.seller_inventory_mapping
 * - Filters warehouses where PUSH_TO_SELLER_CENTRAL: true
 * - Only orders with warehouse_location_id matching these warehouses are checked
 * 
 * Business Logic:
 * - Terminated statuses: closed, shipped, delivered (hardcoded)
 * - Time window: Orders created between (current_time - max_age_hours) and (current_time - min_age_minutes)
 * - SLA check: Compare order age with maxShippedSLA + buffer time
 * - Send email alert with order details in tabular format
 * 
 * Consul Config Path: global.javaOrderServiceConfig.order_details.stuck_order_alert
 */

const { sequelize } = require('../../models/seqModels/index');
const { QueryTypes } = require('sequelize');
const moment = require('moment');
const isEmpty = require('lodash.isempty');
const { sendSgEmail } = require('../../services/email.service');
const logger = require('../../config/logger');

// ============================================================================
// CONFIGURATION HELPERS
// ============================================================================

/**
 * Get stuck order alert configuration from Consul with defaults
 * All time values and settings are configurable via Consul for flexibility
 * 
 * @returns {Object} Configuration object with defaults applied
 */
// Hardcoded terminated statuses - orders with these statuses are considered "done"
const TERMINATED_STATUSES = ['closed', 'shipped', 'delivered'];

/**
 * Get seller central warehouse IDs from Consul configuration
 * Only warehouses with PUSH_TO_SELLER_CENTRAL: true are considered seller central
 * 
 * @returns {Array<string>} Array of seller central warehouse IDs
 */
const getSellerCentralWarehouseIds = () => {
  logger.info('[StuckOrderAlert] Fetching seller central warehouse IDs from Consul');
  
  const sellerInventoryMapping = global.javaOrderServiceConfig?.seller_inventory_mapping;
  
  if (!sellerInventoryMapping || !Array.isArray(sellerInventoryMapping)) {
    logger.warn('[StuckOrderAlert] seller_inventory_mapping not found in Consul or is not an array');
    return [];
  }
  
  // Filter only mappings where PUSH_TO_SELLER_CENTRAL is true
  const sellerCentralWarehouses = sellerInventoryMapping
    .filter(mapping => {
      const isSellerCentral = mapping.PUSH_TO_SELLER_CENTRAL === true || 
                              mapping.PUSH_TO_SELLER_CENTRAL === 'true';
      return isSellerCentral;
    })
    .map(mapping => String(mapping.warehouse_id));
  
  // Remove duplicates
  const uniqueWarehouses = [...new Set(sellerCentralWarehouses)];
  
  logger.info(`[StuckOrderAlert] Found ${uniqueWarehouses.length} seller central warehouses: ${uniqueWarehouses.join(', ')}`);
  
  return uniqueWarehouses;
};

const getStuckOrderConfig = () => {
  const config = global.javaOrderServiceConfig?.order_details?.stuck_order_alert;
  
  logger.info('[StuckOrderAlert] Loading configuration from Consul');
  logger.info(`[StuckOrderAlert] Raw config: ${JSON.stringify(config || 'NOT_FOUND')}`);
  
  const finalConfig = {
    // Time window configuration (in hours/minutes) - from Consul
    maxAgeHours: config?.max_age_hours ?? 12,           // Don't check orders older than this
    minAgeMinutes: config?.min_age_minutes ?? 5,        // Don't check orders newer than this
    
    // SLA configuration - from Consul
    slaBufferHours: config?.sla_buffer_hours ?? 1,      // Buffer time added to SLA check
    terminatedStatuses: TERMINATED_STATUSES,
    
    // Email configuration - from Consul
    email: {
      subject: config?.email?.subject ?? '🚨 Stuck Orders Alert - {{count}} orders need attention',
      recipients: config?.email?.recipients ?? [],
      cc: config?.email?.cc ?? []
    }
  };
  
  logger.info(`[StuckOrderAlert] Final config applied: ${JSON.stringify(finalConfig)}`);
  
  return finalConfig;
};

// ============================================================================
// DATABASE QUERIES
// ============================================================================

/**
 * Query seller central orders from database
 * 
 * Uses split_seller_order table directly (simpler, no joins needed)
 * Includes timelines JSON for SLA checking
 * 
 * Query Logic:
 * 1. Get seller central warehouse IDs from Consul (PUSH_TO_SELLER_CENTRAL: true)
 * 2. Orders created within time window (max_age_hours to min_age_minutes)
 * 3. Orders NOT in terminated statuses (closed, shipped, delivered)
 * 4. Orders with warehouse_id matching seller central warehouses
 * 
 * @param {Object} config - Configuration object
 * @returns {Promise<Array>} Array of seller central order objects with timelines
 */
const querySellerCentralOrders = async (config) => {
  logger.info('[StuckOrderAlert] Step 1: Getting seller central warehouse IDs from Consul');
  
  // Get seller central warehouse IDs from Consul
  const sellerCentralWarehouses = getSellerCentralWarehouseIds();
  
  if (sellerCentralWarehouses.length === 0) {
    logger.warn('[StuckOrderAlert] No seller central warehouses configured in Consul - no orders to check');
    return [];
  }
  
  logger.info(`[StuckOrderAlert] Step 2: Building database query for ${sellerCentralWarehouses.length} seller central warehouses`);
  
  // Calculate time boundaries
  const fromTime = moment().subtract(config.maxAgeHours, 'hours').format('YYYY-MM-DD HH:mm:ss');
  const toTime = moment().subtract(config.minAgeMinutes, 'minutes').format('YYYY-MM-DD HH:mm:ss');
  
  logger.info(`[StuckOrderAlert] Time window: FROM ${fromTime} TO ${toTime}`);
  logger.info(`[StuckOrderAlert] Excluding statuses: ${config.terminatedStatuses.join(', ')}`);
  logger.info(`[StuckOrderAlert] Filtering by warehouse IDs: ${sellerCentralWarehouses.join(', ')}`);
  
  // Build placeholders for SQL
  const statusPlaceholders = config.terminatedStatuses.map(() => '?').join(',');
  const warehousePlaceholders = sellerCentralWarehouses.map(() => '?').join(',');
  
  // Query directly from split_seller_order table - includes timelines for SLA checking
  // Join with split_seller_order_item to get seller_name
  const query = `
    SELECT 
      sso.increment_id AS order_id,
      sso.created_at,
      sso.status AS current_status,
      sso.seller_id,
      sso.warehouse_id,
      sso.timelines,
      ssoi.seller_name,
      TIMESTAMPDIFF(HOUR, sso.created_at, NOW()) AS hours_since_created
    FROM split_seller_order sso
    LEFT JOIN split_seller_order_item ssoi ON sso.entity_id = ssoi.seller_order_id
    WHERE sso.created_at >= ?
      AND sso.created_at <= ?
      AND sso.status NOT IN (${statusPlaceholders})
      AND sso.warehouse_id IN (${warehousePlaceholders})
    GROUP BY sso.entity_id
    ORDER BY sso.created_at ASC
  `;
  
  logger.info('[StuckOrderAlert] Step 3: Executing database query');
  
  try {
    const replacements = [
      fromTime, 
      toTime, 
      ...config.terminatedStatuses,
      ...sellerCentralWarehouses
    ];
    
    const orders = await sequelize.query(query, {
      replacements,
      type: QueryTypes.SELECT
    });
    
    logger.info(`[StuckOrderAlert] Step 4: Query completed - Found ${orders.length} seller central orders in time window`);
    
    return orders;
  } catch (error) {
    logger.error(`[StuckOrderAlert] Database query failed: ${error.message}`);
    throw error;
  }
};

/**
 * Filter orders that have exceeded their maxShipSla + buffer time
 * 
 * SLA Logic:
 * - Get maxShipSla from timelines JSON (fallback to shipSla for backwards compatibility)
 * - Current time > maxShipSla + buffer_time (from Consul, default 1 hour)
 * - If exceeded → Order is stuck and needs alert
 * 
 * @param {Array} orders - Array of orders with timelines
 * @param {Object} config - Configuration object with slaBufferHours
 * @returns {Array} Filtered orders that exceeded SLA
 */
const filterOrdersByShipSla = (orders, config) => {
  logger.info(`[StuckOrderAlert] Step 5: Filtering orders by maxShipSla + buffer time (${config.slaBufferHours} hours)`);
  
  const currentTime = moment();
  const stuckOrders = [];
  
  for (const order of orders) {
    try {
      // Parse timelines JSON if it's a string
      let timelines = order.timelines;
      if (typeof timelines === 'string') {
        timelines = JSON.parse(timelines);
      }
      
      // Get maxShipSla from timelines (fallback to shipSla for backwards compatibility)
      const maxShippedSLA = timelines?.maxShipSla || timelines?.shipSla;
      
      if (!maxShippedSLA) {
        logger.info(`[StuckOrderAlert] Order ${order.order_id}: No maxShipSla found in timelines - skipping`);
        continue;
      }
      
      // Parse maxShippedSLA datetime and add buffer
      const slaTime = moment(maxShippedSLA);
      const slaWithBuffer = slaTime.clone().add(config.slaBufferHours, 'hours');
      
      logger.info(`[StuckOrderAlert] Order ${order.order_id}: maxShipSla=${maxShippedSLA}, slaWithBuffer=${slaWithBuffer.format('YYYY-MM-DD HH:mm:ss')}, currentTime=${currentTime.format('YYYY-MM-DD HH:mm:ss')}`);
      
      // Check if current time has exceeded maxShippedSLA + buffer
      if (currentTime.isAfter(slaWithBuffer)) {
        logger.info(`[StuckOrderAlert] Order ${order.order_id}: EXCEEDED SLA - Adding to alert list`);
        
        // Add SLA info to order for email display
        order.ship_sla = maxShippedSLA;
        order.sla_with_buffer = slaWithBuffer.format('YYYY-MM-DD HH:mm:ss');
        order.exceeded_by_hours = currentTime.diff(slaWithBuffer, 'hours', true).toFixed(1);
        
        stuckOrders.push(order);
      } else {
        logger.info(`[StuckOrderAlert] Order ${order.order_id}: Within SLA - NOT adding to alert`);
      }
    } catch (error) {
      logger.error(`[StuckOrderAlert] Error processing order ${order.order_id}: ${error.message}`);
      // Skip this order if there's an error parsing timelines
    }
  }
  
  logger.info(`[StuckOrderAlert] Step 6: SLA filter complete - ${stuckOrders.length} orders exceeded SLA out of ${orders.length}`);
  
  return stuckOrders;
};

// ============================================================================
// EMAIL BUILDING
// ============================================================================

/**
 * Build HTML email content with order details in tabular format
 * 
 * @param {Array} orders - Array of stuck orders
 * @param {Object} config - Configuration object
 * @returns {Object} { subject, html } for email
 */
const buildEmailContent = (orders, config) => {
  logger.info('[StuckOrderAlert] Step 6: Building email content');
  
  const alertTime = moment().format('MMMM DD, YYYY [at] h:mm A');
  const fromTime = moment().subtract(config.maxAgeHours, 'hours').format('MMM DD, h:mm A');
  const toTime = moment().subtract(config.minAgeMinutes, 'minutes').format('MMM DD, h:mm A');
  
  // Build table rows
  const tableRows = orders.map((order, index) => {
    const createdAt = moment(order.created_at).format('MMM DD, YYYY h:mm A');
    const shipSlaFormatted = order.ship_sla ? moment(order.ship_sla).format('MMM DD, h:mm A') : 'N/A';
    const statusColor = getStatusColor(order.current_status);
    
    return `
      <tr style="background-color: ${index % 2 === 0 ? '#ffffff' : '#f9f9f9'};">
        <td style="padding: 12px; border: 1px solid #ddd;">${order.order_id}</td>
        <td style="padding: 12px; border: 1px solid #ddd;">${createdAt}</td>
        <td style="padding: 12px; border: 1px solid #ddd;">
          <span style="color: ${statusColor}; font-weight: bold;">${order.current_status}</span>
        </td>
        <td style="padding: 12px; border: 1px solid #ddd;">${order.seller_name || 'N/A'}</td>
        <td style="padding: 12px; border: 1px solid #ddd;">${order.seller_id || 'N/A'}</td>
        <td style="padding: 12px; border: 1px solid #ddd;">${order.warehouse_id || 'N/A'}</td>
        <td style="padding: 12px; border: 1px solid #ddd;">${shipSlaFormatted}</td>
        <td style="padding: 12px; border: 1px solid #ddd; color: #f44336; font-weight: bold;">${order.exceeded_by_hours || 0}h</td>
      </tr>
    `;
  }).join('');
  
  // Build complete HTML
  const html = `
    <!DOCTYPE html>
    <html>
    <head>
      <style>
        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
        .container { max-width: 900px; margin: 0 auto; padding: 20px; }
        .header { background-color: #f44336; color: white; padding: 20px; border-radius: 5px 5px 0 0; }
        .content { background-color: #fff; padding: 20px; border: 1px solid #ddd; }
        .summary { background-color: #fff3cd; padding: 15px; border-radius: 5px; margin-bottom: 20px; }
        table { width: 100%; border-collapse: collapse; margin-top: 20px; }
        th { background-color: #f44336; color: white; padding: 12px; text-align: left; }
        .footer { background-color: #f5f5f5; padding: 15px; text-align: center; font-size: 12px; color: #666; }
      </style>
    </head>
    <body>
      <div class="container">
        <div class="header">
          <h2 style="margin: 0;">⚠️ Stuck Seller Central Orders Alert</h2>
          <p style="margin: 5px 0 0 0;">Seller Central orders requiring immediate attention</p>
        </div>
        
        <div class="content">
          <div class="summary">
            <strong>📊 Alert Summary</strong><br>
            <strong>Total Orders Exceeded SLA:</strong> ${orders.length}<br>
            <strong>Alert Generated:</strong> ${alertTime}<br>
          </div>
          
          <p>The following <strong>${orders.length} Seller Central orders</strong> have exceeded their <strong>maxShipSla</strong>.</p>
          <table>
            <thead>
              <tr>
                <th>Order ID</th>
                <th>Created At</th>
                <th>Current Status</th>
                <th>Seller Name</th>
                <th>Seller ID</th>
                <th>Warehouse ID</th>
                <th>Max Shipped SLA</th>
                <th>Exceeded By</th>
              </tr>
            </thead>
            <tbody>
              ${tableRows}
            </tbody>
          </table>
          
          <div style="margin-top: 20px; padding: 15px; background-color: #e3f2fd; border-radius: 5px;">
            <strong>💡 Next Steps:</strong>
            <ul style="margin: 10px 0 0 0;">
              <li>Review each order's status and identify blockers</li>
              <li>Check for payment issues, inventory problems, or system errors</li>
              <li>Escalate critical orders to respective teams</li>
            </ul>
          </div>
        </div>
        
        <div class="footer">
          <p>This is an automated alert from OMS. Please do not reply to this email.</p>
        </div>
      </div>
    </body>
    </html>
  `;
  
  // Replace {{count}} placeholder in subject
  const subject = config.email.subject.replace('{{count}}', orders.length);
  
  logger.info(`[StuckOrderAlert] Step 7: Email content built - Subject: ${subject}`);
  
  return { subject, html };
};

/**
 * Get color for status display in email
 * @param {string} status - Order status
 * @returns {string} Color code
 */
const getStatusColor = (status) => {
  const colorMap = {
    'pending': '#f44336',           // Red
    'pending_payment': '#ff9800',   // Orange
    'payment_hold': '#ff9800',      // Orange
    'processing': '#2196f3',        // Blue
    'ready_to_ship': '#4caf50',     // Green
    'holded': '#9c27b0',            // Purple
    'default': '#666666'            // Gray
  };
  
  return colorMap[status?.toLowerCase()] || colorMap.default;
};

// ============================================================================
// MAIN ALERT FUNCTION
// ============================================================================

/**
 * Main function to process stuck order alerts
 * Called by the cron job via API endpoint
 * 
 * Flow:
 * 1. Load configuration from Consul
 * 2. Query stuck orders from database
 * 3. (Optional) Filter by SLA
 * 4. Build email content
 * 5. Send email alert
 * 6. Return summary
 * 
 * @param {Object} options - Optional overrides for testing
 * @param {string} options.fromEmail - Sender email
 * @param {string} options.fromName - Sender name
 * @returns {Promise<Object>} Result summary
 */
const processStuckOrderAlert = async ({ fromEmail, fromName } = {}) => {
  const startTime = Date.now();
  
  logger.info('[StuckOrderAlert] STARTING STUCK ORDER ALERT PROCESSING');
  logger.info(`[StuckOrderAlert] Execution started at: ${moment().format('YYYY-MM-DD HH:mm:ss')}`);
  logger.info('='.repeat(60));
  
  try {
    // Step 1: Load configuration
    const config = getStuckOrderConfig();
    logger.info(`[StuckOrderAlert] SLA buffer time from config: ${config.slaBufferHours} hours`);
    
    // Step 2: Query seller central orders in time window
    const sellerCentralOrders = await querySellerCentralOrders(config);
    
    if (isEmpty(sellerCentralOrders)) {
      logger.info('[StuckOrderAlert] No seller central orders found in time window - no email will be sent');
      logger.info('[StuckOrderAlert] PROCESSING COMPLETE ✓');
      return {
        success: true,
        message: 'No seller central orders found in time window',
        ordersFound: 0,
        ordersExceededSla: 0,
        emailSent: false,
        executionTimeMs: Date.now() - startTime
      };
    }
    
    logger.info(`[StuckOrderAlert] Found ${sellerCentralOrders.length} seller central orders in time window`);
    
    // Step 3: Filter orders that exceeded shipSla + buffer time
    const filteredOrders = filterOrdersByShipSla(sellerCentralOrders, config);
    
    if (isEmpty(filteredOrders)) {
      logger.info('[StuckOrderAlert] No orders exceeded SLA - no email will be sent');
      logger.info('[StuckOrderAlert] PROCESSING COMPLETE ✓');
      return {
        success: true,
        message: 'No orders exceeded SLA',
        ordersFound: sellerCentralOrders.length,
        ordersExceededSla: 0,
        emailSent: false,
        executionTimeMs: Date.now() - startTime
      };
    }
    
    logger.info(`[StuckOrderAlert] ${filteredOrders.length} orders exceeded SLA and will be alerted`);
    
    // Step 5: Check if recipients are configured
    if (isEmpty(config.email.recipients)) {
      logger.warn('[StuckOrderAlert] No email recipients configured - cannot send alert');
      return {
        success: false,
        message: 'No email recipients configured',
        ordersFound: filteredOrders.length,
        emailSent: false,
        executionTimeMs: Date.now() - startTime
      };
    }
    
    // Step 6: Build email content
    const { subject, html } = buildEmailContent(filteredOrders, config);
    
    // Step 7: Send email
    logger.info(`[StuckOrderAlert] Step 8: Sending email to ${config.email.recipients.join(', ')}`);
    
    const emailSent = await sendSgEmail({
      to: config.email.recipients.join(','),
      from: { email: fromEmail, name: fromName },
      subject,
      html
    });
    
    if (emailSent) {
      logger.info('[StuckOrderAlert] Step 9: Email sent successfully ✓');
    } else {
      logger.error('[StuckOrderAlert] Step 9: Email sending FAILED');
    }
    
    // Step 8: Log completion
    const executionTime = Date.now() - startTime;
    logger.info('='.repeat(60));
    logger.info('[StuckOrderAlert] PROCESSING COMPLETE');
    logger.info(`[StuckOrderAlert] Orders found: ${filteredOrders.length}`);
    logger.info(`[StuckOrderAlert] Email sent: ${emailSent}`);
    logger.info(`[StuckOrderAlert] Execution time: ${executionTime}ms`);
    logger.info('='.repeat(60));
    
    return {
      success: true,
      message: emailSent ? 'Alert email sent successfully' : 'Email sending failed',
      ordersFound: filteredOrders.length,
      emailSent,
      recipients: config.email.recipients,
      executionTimeMs: executionTime
    };
    
  } catch (error) {
    const executionTime = Date.now() - startTime;
    logger.error('='.repeat(60));
    logger.error('[StuckOrderAlert] PROCESSING FAILED');
    logger.error(`[StuckOrderAlert] Error: ${error.message}`);
    logger.error(`[StuckOrderAlert] Stack: ${error.stack}`);
    logger.error('='.repeat(60));
    
    return {
      success: false,
      message: error.message,
      ordersFound: 0,
      emailSent: false,
      executionTimeMs: executionTime
    };
  }
};

// ============================================================================
// EXPORTS
// ============================================================================

module.exports = {
  processStuckOrderAlert,
  getStuckOrderConfig,              // Exported for testing
  getSellerCentralWarehouseIds,     // Exported for testing
  querySellerCentralOrders,         // Exported for testing
  filterOrdersByShipSla,            // Exported for testing
  buildEmailContent                 // Exported for testing
};

