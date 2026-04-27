/**
 * Helper functions for Seller ASN (Advanced Shipping Notice) operations
 */

const { SellerAsn, SellerAsnDetails, SplitSalesOrder, SplitSalesOrderItem, SplitSellerOrder, SplitSellerOrderItem, SellerConfig } = require('../models/seqModels/index');
const { sequelize } = require('../models/seqModels/index');
const {
  // ORDER_SHIPPED_CODE,
  ORDER_PICKED_UP,
  ORDER_PACKED,
  ORDER_STATE_COMPLETE,
  ORDER_SHIPPED_STATUS_CODE,
  FAILED_DELIVERY,
  FAILED_DELIVERY_STATUS_CODE,
  ORDER_DELIVERED_CODE,
  ORDER_DELIVERED_STATUS_CODE,
  ORDER_RTO,
  ORDER_STATE_CANCELED,
  ORDER_RTO_STATUS_CODE,
  ORDER_RTO_INITIATED,
  ORDER_RTO_INITIATED_CODE,
  OUT_FOR_DELIVERY,
  STATUS_ID_CODE_MAP,
  SKIP_DUPLICATE_CHECK_REQ_STATUS,
  ORDER_LOST,
  ORDER_LOST_STATUS_CODE,
  ORDER_STATE_LOST,
  ORDER_CANCELLED_STATUS_CODE,
  ORDER_CANCELLED,
  ORDER_STATE_CANCELLED

} = require('../constants/order');

const logger = require('../config/logger');

/**
 * Extract seller_id from increment_id
 * Format: 7676545-G1-1200-1 → seller_id = 1200
 * @param {string} incrementId - The increment ID (e.g., "7676545-G1-1200-1")
 * @returns {string|null} - Extracted seller_id or null if invalid format
 */
const extractSellerIdFromIncrementId = (incrementId) => {
  if (!incrementId || typeof incrementId !== 'string') {
    logger.warn('[ASN] extractSellerIdFromIncrementId: Invalid increment_id provided');
    return null;
  }
  
  const parts = incrementId.split('-');
  // Format: orderId-countryCode-sellerId-sequence
  // Example: 7676545-G1-1200-1
  if (parts.length >= 3) {
    const sellerId = parts[2]; // Third part is seller_id
    logger.info(`[ASN] Extracted seller_id: ${sellerId} from increment_id: ${incrementId}`);
    return sellerId;
  }
  
  logger.warn(`[ASN] Could not extract seller_id from increment_id: ${incrementId}`);
  return null;
};


const getSellerConfigBySellerId = async (sellerId, warehouseId) => {
  try {
    if (!sellerId) {
      logger.warn('[ASN] getSellerConfigBySellerId: No seller_id provided');
      return null;
    }

    const whereClause = { SELLER_ID: sellerId };
    
    // If warehouse_id is provided, use it to find exact match
    if (warehouseId) {
      whereClause.styli_warehouse_id = warehouseId;
      logger.info(`[ASN] Looking up seller_config with seller_id: ${sellerId} AND styli_warehouse_id: ${warehouseId}`);
    } else {
      logger.info(`[ASN] Looking up seller_config with seller_id: ${sellerId} only (no warehouse_id provided)`);
    }

    const sellerConfig = await SellerConfig.findOne({
      where: whereClause
    });

    if (!sellerConfig) {
      logger.info(`[ASN] No seller_config found for seller_id: ${sellerId}, styli_warehouse_id: ${warehouseId || 'N/A'}`);
      return null;
    }

    logger.info(`[ASN] Found seller_config for seller_id: ${sellerId}, styli_warehouse_id: ${warehouseId || 'N/A'}`);
    return sellerConfig;
  } catch (error) {
    logger.error(`[ASN] Error fetching seller_config for seller_id ${sellerId}, warehouse_id ${warehouseId}:`, error.message);
    return null;
  }
};

/**
 * Check if seller has picked_up_by_styli flag enabled in basic_settings
 * @param {Object} sellerConfig - Seller config object
 * @returns {boolean} - True if picked_up_by_styli is enabled
 */
const isPickedUpByStyliEnabled = (sellerConfig) => {
  if (!sellerConfig || !sellerConfig.basic_settings) {
    logger.info('[ASN] No basic_settings found in seller_config');
    return false;
  }

  let basicSettings = sellerConfig.basic_settings;
  
  // Parse JSON if it's a string
  if (typeof basicSettings === 'string') {
    try {
      basicSettings = JSON.parse(basicSettings);
    } catch (e) {
      logger.error('[ASN] Failed to parse basic_settings JSON:', e.message);
      return false;
    }
  }

  const pickedUpByStyli = basicSettings?.picked_up_by_styli || basicSettings?.PICKED_UP_BY_STYLI;
  logger.info(`[ASN] picked_up_by_styli flag value: ${pickedUpByStyli} (type: ${typeof pickedUpByStyli})`);
  
  return pickedUpByStyli === true || pickedUpByStyli === 'true' || pickedUpByStyli === 1;
};

//To handle this function in sellse orders have the same skucode
const mergeOrderItemsAndTaxBreakupForms = (input) => {
  if (!input || !Array.isArray(input.orderItems)) throw new Error("Invalid input: orderItems must be an array");
  const mergedOrderItems = Object.values(input.orderItems.reduce((acc, i) => ((acc[i.channelSkuCode] ??= { ...i, quantity: 0 }).quantity += i.quantity || 0, acc), {}));
  return { ...input, orderItems: mergedOrderItems };
}



const handleSwitchCases = (
  switchCaseVars,
  notificationId,
  status,
  ndrStatusDesc,
  timestamp
) => {
  try {
    global.logInfo('in handleSwitchCases');
    switchCaseVars.timestamp = timestamp;
    switch (notificationId) {
      case 2:
        if (status.toLowerCase() === ORDER_PACKED || status.toLowerCase() === ORDER_PICKED_UP) {
          switchCaseVars.toUpdateState = ORDER_STATE_COMPLETE;
          switchCaseVars.toUpdateStatus = ORDER_SHIPPED_STATUS_CODE;
          switchCaseVars.orderComment = 'Order has been Shipped.';
          switchCaseVars.emailTemplate = 'shipped';
        }
        break;
      case OUT_FOR_DELIVERY:
        switchCaseVars.smsStatus = 'out_for_delivery';
        switchCaseVars.toUpdateState = ORDER_STATE_COMPLETE;
        switchCaseVars.toUpdateStatus = ORDER_SHIPPED_STATUS_CODE;
        switchCaseVars.orderComment = 'Order has been Shipped.';
        break;
      case FAILED_DELIVERY:
        try {
          switchCaseVars.clickpostMessage = JSON.stringify({
            type: 'inDelivered',
            value: ndrStatusDesc
          });
          if (switchCaseVars.clickpostMessage.length > 245) {
            console.log(`Trimming clickpostMessage`);
            switchCaseVars.clickpostMessage = JSON.stringify({
              type: 'inDelivered',
              value: ndrStatusDesc.substring(0, 212)
            });
          }
          switchCaseVars.toUpdateState = ORDER_STATE_COMPLETE;
          switchCaseVars.toUpdateStatus = ORDER_SHIPPED_STATUS_CODE;
          switchCaseVars.orderComment = `Order Failed Delivery : ${ndrStatusDesc}`;
          switchCaseVars.smsStatus = 'undelivered';
        } catch (error) {
          console.log(`Error processing FAILED_DELIVERY for order`, error);
        }
        break;
      case ORDER_DELIVERED_CODE:
        switchCaseVars.deliveredAt = timestamp ?? null;
        switchCaseVars.toUpdateState = ORDER_STATE_COMPLETE;
        switchCaseVars.toUpdateStatus = ORDER_DELIVERED_STATUS_CODE;
        switchCaseVars.orderComment = 'Order has been Delivered.';
        switchCaseVars.callFraudelent = true;
        break;
      case ORDER_RTO:
        switchCaseVars.toUpdateState = ORDER_STATE_CANCELED;
        switchCaseVars.toUpdateStatus = ORDER_RTO_STATUS_CODE;
        switchCaseVars.orderComment = `Order returned to warehouse with AWB : ${switchCaseVars.rtoAwb || switchCaseVars.waybill
          }`;
        switchCaseVars.pushToWms = true;
        break;
      case ORDER_RTO_INITIATED:
        switchCaseVars.toUpdateState = ORDER_STATE_CANCELED;
        switchCaseVars.toUpdateStatus = ORDER_RTO_INITIATED_CODE;
        switchCaseVars.orderComment = 'RTO is initiated for the order.';
        switchCaseVars.callFraudelent = true;
        break;
      case ORDER_LOST:
        switchCaseVars.toUpdateState = ORDER_STATE_LOST;
        switchCaseVars.toUpdateStatus = ORDER_LOST_STATUS_CODE;
        switchCaseVars.orderComment = 'Order is Lost';
        switchCaseVars.callFraudelent = false;
        break;
      case ORDER_CANCELLED:
        switchCaseVars.toUpdateState = ORDER_STATE_CANCELLED;
        switchCaseVars.toUpdateStatus = ORDER_CANCELLED_STATUS_CODE;
        switchCaseVars.orderComment = 'Order is Cancelled';
        switchCaseVars.callFraudelent = false;
        break;
      default:
    }

    return switchCaseVars;
  } catch (e) {
    global.logError('Error in handleSwitchCases', e.message ? e.message : '');
    throw e;
  }
};

/**
 * Create Seller ASN record from shipment update data
 * Implements two-path flow:
 * - NEW SELLERS PATH: When SELLER_CENTRAL_ASN (Consul) = true AND picked_up_by_styli (seller_config) = true
 *   → Each seller maintains their own ASN (lookup by seller_id)
 * - DEFAULT PATH: When flags are undefined or false
 *   → Sellers share the same open ASN (old behavior)
 * 
 * @param {Object} shipmentData - Shipment update data from API
 * @returns {Promise<Object>} - Created ASN record and details
 */
const createSellerAsnRecord = async (shipmentData) => {
  try {
    const {
      waybill,
      additional = {},
      status: reqStatus
    } = shipmentData;

    const {
      latest_status = {},
      is_rvp: isReturn
    } = additional;

    const {
      reference_number: increment_id,
      timestamp
    } = latest_status;

    logger.info(`[ASN] ═══════════════════════════════════════════════════════════════`);
    logger.info(`[ASN] STEP 1: WEBHOOK RECEIVED - Starting ASN creation for increment_id: ${increment_id}`);
    logger.info(`[ASN] Shipment Data: waybill=${waybill}, isReturn=${isReturn}, reqStatus=${reqStatus}`);

    // Skip if this is a return shipment
    if (isReturn) {
      logger.info(`[ASN] SKIP: Return shipment detected for increment_id: ${increment_id}`);
      return {
        success: false,
        message: 'Return shipments not processed for ASN'
      };
    }

    // STEP 3: EXTRACT SELLER_ID from increment_id
    const sellerId = extractSellerIdFromIncrementId(increment_id);
    logger.info(`[ASN] STEP 3: EXTRACT SELLER_ID - Extracted seller_id: ${sellerId} from increment_id: ${increment_id}`);

    // Get split sales order to extract order details
    const splitSellerOrder = await SplitSellerOrder.findOne({
      where: { increment_id }
    });

    if (!splitSellerOrder) {
      logger.warn(`[ASN] Split order not found for increment_id: ${increment_id}`);
      return {
        success: false,
        message: 'Seller order not found'
      };
    }

    // Added a check to prevent duplicate increment_id on seller ASN details table
    const incrementIdExists = await SellerAsnDetails.findOne({
      where: { increment_id }
    });
    if (incrementIdExists) {
      logger.info(`[ASN] SKIP: Increment_id ${increment_id} already exists in seller_asn_details`);
      return {
        success: false,
        message: 'Increment_id already exists in the database'
      };
    }

    // ═══════════════════════════════════════════════════════════════
    // STEP 4: CHECK FLAG 1 - seller_central_asn_enhancement (from Consul)
    // ═══════════════════════════════════════════════════════════════
    const sellerCentralAsnFlag = global?.baseConfig?.seller_central_asn_enhancement;
    logger.info(`[ASN] STEP 4: CHECK FLAG 1 - seller_central_asn_enhancement`);
    logger.info(`[ASN] Source: global.baseConfig.seller_central_asn_enhancement`);
    logger.info(`[ASN] Value: ${sellerCentralAsnFlag} (type: ${typeof sellerCentralAsnFlag})`);

    // Determine if we should use new sellers path or default path
    let useNewSellersPath = false;
    let sellerConfig = null;

    if (sellerCentralAsnFlag === undefined) {
      // Flag 1 UNDEFINED → DEFAULT FLOW (existing sellers behavior)
      logger.info(`[ASN] Flag 1 UNDEFINED → Using DEFAULT FLOW (existing sellers path)`);
      useNewSellersPath = false;
    } else if (sellerCentralAsnFlag === false || sellerCentralAsnFlag === 'false') {
      // Flag 1 FALSE → DEFAULT FLOW (don't use new sellers path, use shared ASN)
      logger.info(`[ASN] Flag 1 FALSE → Using DEFAULT FLOW (shared ASN, not seller-specific)`);
      useNewSellersPath = false;  // ✅ Use default path, don't skip
    } else if (sellerCentralAsnFlag === true || sellerCentralAsnFlag === 'true') {
      // Flag 1 TRUE → Check Flag 2
      logger.info(`[ASN] Flag 1 is TRUE → Proceeding to check Flag 2 (picked_up_by_styli)`);

      // ═══════════════════════════════════════════════════════════════
      // STEP 5: CHECK FLAG 2 - PICKED_UP_BY_STYLI (from seller_config)
      // ═══════════════════════════════════════════════════════════════
      const warehouseId = splitSellerOrder.warehouse_id;
      logger.info(`[ASN] STEP 5: CHECK FLAG 2 - PICKED_UP_BY_STYLI`);
      logger.info(`[ASN] Source: seller_config table (basic_settings) for seller_id: ${sellerId}, styli_warehouse_id: ${warehouseId}`);

      sellerConfig = await getSellerConfigBySellerId(sellerId, warehouseId);
      
      if (!sellerConfig) {
        logger.info(`[ASN] No seller_config found for seller_id: ${sellerId}, styli_warehouse_id: ${warehouseId} → Using DEFAULT FLOW`);
        useNewSellersPath = false;  // ✅ Use default path if no config found
      } else {
        const pickedUpByStyli = isPickedUpByStyliEnabled(sellerConfig);
        logger.info(`[ASN] picked_up_by_styli value: ${pickedUpByStyli}`);

        if (!pickedUpByStyli) {
          // Flag 2 FALSE → Also use DEFAULT FLOW (not skip)
          logger.info(`[ASN] Flag 2 (picked_up_by_styli) is FALSE → Using DEFAULT FLOW (shared ASN)`);
          useNewSellersPath = false;  // ✅ Use default path, don't skip
        } else {
          // Both flags are TRUE → Use NEW SELLERS PATH
          logger.info(`[ASN] ✅ Both flags TRUE → Using NEW SELLERS PATH (seller-specific ASN)`);
          useNewSellersPath = true;
        }
      }
    }

    // ═══════════════════════════════════════════════════════════════
    // STEP 6: CHECK OPEN ASN
    // ═══════════════════════════════════════════════════════════════
    const startTime = new Date();
    let asnId = null;
    let asnDetails = null;
    let openAsnRecord = null;

    if (useNewSellersPath) {
      // NEW SELLERS PATH: Check open ASN by seller_id
      logger.info(`[ASN] STEP 6: CHECK OPEN ASN (🆕 BY SELLER_ID)`);
      logger.info(`[ASN] 🆕 NEW LOGIC: Each seller has their own ASN!`);
      logger.info(`[ASN] Query: SELECT * FROM seller_asn WHERE status = 'open' AND seller_id = '${sellerId}'`);

      openAsnRecord = await SellerAsn.findOne({
        where: {
          status: 'open',
          seller_id: sellerId
        }
      });

      if (openAsnRecord) {
        logger.info(`[ASN] ✅ FOUND open ASN for seller_id ${sellerId}: ASN ID = ${openAsnRecord.id}`);
        asnId = openAsnRecord.id;
        asnDetails = openAsnRecord;
      } else {
        logger.info(`[ASN] ❌ NOT FOUND open ASN for seller_id ${sellerId} → Creating NEW ASN`);
        
        const asnData = {
          status: 'open',
          startTime,
          endTime: null,
          is_seller_central: 1,
          seller_id: sellerId // 🆕 NEW: Store seller_id for seller-specific tracking
        };

        const createdAsn = await SellerAsn.create(asnData);
        asnId = createdAsn.id;
        
        // Generate asn_number in format: ASN00001635 (padded to 8 digits)
        const asnNumber = `ASN${String(asnId).padStart(8, '0')}`;
        await createdAsn.update({ asn_number: asnNumber });
        
        asnDetails = createdAsn;
        logger.info(`[ASN] ✨ Created NEW ASN for seller_id ${sellerId}: ASN ID = ${asnId}, asn_number = ${asnNumber}`);
      }
    } else {
      // DEFAULT PATH: Check any open ASN (old behavior - shared ASN)
      logger.info(`[ASN] STEP 6-DEFAULT: CHECK OPEN ASN (OLD LOGIC)`);
      logger.info(`[ASN] ⚠️ NO seller_id check - ANY open ASN can be used`);
      logger.info(`[ASN] Query: SELECT * FROM seller_asn WHERE status = 'open'`);

      openAsnRecord = await SellerAsn.findOne({
        where: {
          status: 'open'
        }
      });

      if (openAsnRecord) {
        logger.info(`[ASN] ✅ FOUND open ASN (shared): ASN ID = ${openAsnRecord.id}`);
        asnId = openAsnRecord.id;
        asnDetails = openAsnRecord;
      } else {
        logger.info(`[ASN] ❌ NOT FOUND any open ASN → Creating NEW ASN (without seller_id)`);
        
        const asnData = {
          status: 'open',
          startTime,
          endTime: null
          // Note: No seller_id for default path (backward compatible)
        };

        const createdAsn = await SellerAsn.create(asnData);
        asnId = createdAsn.id;
        
        // Generate asn_number in format: ASN00001635 (padded to 8 digits)
        const asnNumber = `ASN${String(asnId).padStart(8, '0')}`;
        await createdAsn.update({ asn_number: asnNumber });
        
        asnDetails = createdAsn;
        logger.info(`[ASN] ✨ Created NEW ASN (shared): ASN ID = ${asnId}, asn_number = ${asnNumber}`);
      }
    }

    logger.info(`[ASN] Using ASN ID: ${asnId} (Path: ${useNewSellersPath ? 'NEW SELLERS' : 'DEFAULT'})`)

    // ═══════════════════════════════════════════════════════════════
    // STEP 7: CREATE ASN DETAILS
    // ═══════════════════════════════════════════════════════════════
    logger.info(`[ASN] STEP 7: CREATE ASN DETAILS (Path: ${useNewSellersPath ? 'NEW SELLERS' : 'DEFAULT'})`);

    const splitSellerOrderQuery = `
    SELECT * 
    FROM split_seller_order_item 
    WHERE seller_order_id = :seller_order_id AND product_type = :product_type
  `;

  const splitSellerOrderItems = await sequelize.query(splitSellerOrderQuery, {
    replacements: { seller_order_id: splitSellerOrder.entity_id, product_type: 'configurable' },
    type: sequelize.QueryTypes.SELECT
  });

  if (!splitSellerOrderItems || splitSellerOrderItems.length === 0) {
    logger.warn(`[ASN] No record found in split_seller_order_item for increment_id: ${increment_id}`);
    return {
      status: false,
      errorMsg: `No data found for order ${splitSellerOrder.main_order_id}`
    };
  }

  logger.info(`[ASN] Found ${splitSellerOrderItems.length} split_seller_order_item records`);

  const salesOrderItemsQuery = `
  SELECT * 
  FROM sales_order_item 
  WHERE item_id = :item_id
`;
  const salesOrderItems = await sequelize.query(salesOrderItemsQuery, {
    replacements: { item_id: splitSellerOrderItems?.[0]?.sales_order_item_id },
    type: sequelize.QueryTypes.SELECT
  });

  logger.info(`[ASN] Found ${salesOrderItems?.length || 0} sales_order_item records`);

  let isOrderQuantityMatch = false

  const sellerOrderItemQuantity = salesOrderItems?.[0]?.qty_ordered;
  const splitSellerOrderItemQuantity = splitSellerOrderItems?.[0]?.qty_ordered;

  if(sellerOrderItemQuantity === splitSellerOrderItemQuantity){
    isOrderQuantityMatch = true;
  }

  // Helper function to divide string values and format back to string with 3 decimal places
  const divideAndFormat = (value, divisor) => {
    if (!value || !divisor) return value;
    const result = parseFloat(value) / parseFloat(divisor);
    return result.toFixed(3);
  };

    // Create seller details records for each item
    const sellerDetailsRecords = [];
    
    for (const item of salesOrderItems) {
      const sellerDetailData = {
        seller_id: splitSellerOrderItems?.[0]?.seller_id,
        order_id: splitSellerOrder.main_order_id,
        split_order_id: splitSellerOrder.entity_id,
        waybill, // Store waybill in details since removed from main ASN table
        increment_id, // Store increment_id in details since removed from main ASN table
        seller_name: splitSellerOrderItems?.[0]?.seller_name,
        shipment_type: item.shipment_type,
        warehouse_location_id: item.warehouse_location_id,
        store_id: item.store_id,
        product_id: item.product_id,
        product_type: item.product_type,
        sku: item.sku,
        name: item.name,
        description: item.description,
        item_size: item.item_size,
        item_brand_name: item.item_brand_name,
        item_img_url: item.item_img_url,
        hsn_code: item.hsn_code,
        weight: item.weight,
        price: item.price,
        base_price: item.base_price,
        qty_ordered: splitSellerOrderItems?.[0]?.qty_ordered,
        qty_shipped: splitSellerOrderItems?.[0]?.qty_shipped,
        qty_invoiced: isOrderQuantityMatch ? item.qty_invoiced : divideAndFormat(item.qty_invoiced, splitSellerOrderItemQuantity),
        row_total: isOrderQuantityMatch ? item.row_total : divideAndFormat(item.row_total, splitSellerOrderItemQuantity),
        base_row_total: isOrderQuantityMatch ? item.base_row_total : divideAndFormat(item.base_row_total, splitSellerOrderItemQuantity),
        seller_asn_id: asnId,
        tax_percent: isOrderQuantityMatch ? item.tax_percent : divideAndFormat(item.tax_percent, splitSellerOrderItemQuantity),
        tax_amount: isOrderQuantityMatch ? item.tax_amount : divideAndFormat(item.tax_amount, splitSellerOrderItemQuantity)
      };
      if(item.product_type === "configurable"){
        const createdDetail = await SellerAsnDetails.create(sellerDetailData);
        sellerDetailsRecords.push(createdDetail);
        logger.info(`[ASN] Created ASN detail for SKU: ${item.sku}, seller_id: ${sellerDetailData.seller_id}`);
      }
    }

    logger.info(`[ASN] ═══════════════════════════════════════════════════════════════`);
    logger.info(`[ASN] ✅ ASN CREATION COMPLETED SUCCESSFULLY`);
    logger.info(`[ASN] Summary:`);
    logger.info(`[ASN]   - ASN ID: ${asnId}`);
    logger.info(`[ASN]   - increment_id: ${increment_id}`);
    logger.info(`[ASN]   - seller_id: ${sellerId}`);
    logger.info(`[ASN]   - Path: ${useNewSellersPath ? 'NEW SELLERS (seller-specific ASN)' : 'DEFAULT (shared ASN)'}`);
    logger.info(`[ASN]   - Details Count: ${sellerDetailsRecords.length}`);
    logger.info(`[ASN] ═══════════════════════════════════════════════════════════════`);

    return {
      success: true,
      message: 'ASN and seller details created successfully',
      asnId: asnId,
      detailsCount: sellerDetailsRecords.length,
      asn: asnDetails,
      details: sellerDetailsRecords,
      path: useNewSellersPath ? 'new_sellers' : 'default',
      sellerId: sellerId
    };

  } catch (error) {
    logger.error(`[ASN] ❌ ERROR creating ASN record:`, error.message);
    logger.error(`[ASN] Stack trace:`, error.stack);
    return {
      success: false,
      message: 'Error creating ASN record',
      error: error.message
    };
  }
};

/**
 * Update ASN record and associated seller details status
 * @param {number} asnId - ASN ID
 * @param {string} newStatus - New shipment status
 * @param {number} timestamp - Status update timestamp
 * @param {Object} additionalData - Additional update data
 * @returns {Promise<Object>} - Update result
 */
const updateSellerAsnStatus = async (asnId, newStatus, timestamp, additionalData = {}) => {
  try {
    const asn = await SellerAsn.findOne({
      where: {
        id: asnId
      }
    });

    if (!asn) {
      return {
        success: false,
        message: 'ASN record not found'
      };
    }

    const updateData = {
      status: newStatus
    };

    // If status indicates completion, set endTime
    const completionStatuses = ['delivered', 'completed', 'order_cancelled', 'rto', 'order_lost', 'closed', 'inward_completed'];
    if (completionStatuses.includes(newStatus)) {
      let endTime = new Date(); // Default to today's date/time
      
      if (timestamp) {
        // Handle both Unix timestamp (seconds) and milliseconds
        const parsedTimestamp = parseInt(timestamp);
        if (!isNaN(parsedTimestamp) && parsedTimestamp > 0) {
          // If timestamp is less than year 2000 in milliseconds, assume it's in seconds
          const timestampMs = parsedTimestamp < 946684800000 ? parsedTimestamp * 1000 : parsedTimestamp;
          const convertedDate = new Date(timestampMs);
          
          // Validate the converted date
          if (!isNaN(convertedDate.getTime())) {
            endTime = convertedDate;
            console.log(`Using parsed endTime: ${endTime.toISOString()} (from ${timestamp})`);
          } else {
            console.log(`Invalid timestamp conversion for endTime ${timestamp}, using today's date: ${endTime.toISOString()}`);
          }
        } else {
          console.log(`Invalid timestamp value for endTime: ${timestamp}, using today's date: ${endTime.toISOString()}`);
        }
      } else {
        console.log(`No timestamp provided for completion, using today's date for endTime: ${endTime.toISOString()}`);
      }
      
      updateData.endTime = endTime;
    }

    // Add any additional data
    Object.assign(updateData, additionalData);

    await asn.update(updateData);

    // Update associated seller details if needed
    if (additionalData.updateDetails) {
      await SellerAsnDetails.update(
        { 
          status: newStatus,
          ...additionalData.detailsUpdate 
        },
        { 
          where: { seller_asn_id: asn.id } 
        }
      );
    }

    console.log(`Updated ASN ${asn.id} status to ${newStatus}`);

    return {
      success: true,
      message: 'ASN status updated successfully',
      asnId: asn.id,
      asn: asn
    };

  } catch (error) {
    console.error('Error updating ASN status:', error);
    return {
      success: false,
      message: 'Error updating ASN status',
      error: error.message
    };
  }
};

/**
 * Push closed packages to Increff Create Inward Order API
 * @param {Object} asnData - ASN data with seller details
 * @returns {Promise<Object>} - Increff API response
 */
const pushToIncreffInwardAPI = async (asnData) => {
  try {
    const axios = require('axios');
    
    // Get ASN with details
    const asnWithDetails = await SellerAsn.findOne({
      where: { id: asnData.asnId },
      include: [{
        model: SellerAsnDetails,
        as: 'SellerAsnDetails'
      }]
    });

    if (!asnWithDetails || !asnWithDetails.SellerAsnDetails || asnWithDetails.SellerAsnDetails.length === 0) {
      return {
        success: false,
        message: 'ASN or seller details not found'
      };
    }

    const sellerDataValues = asnWithDetails?.SellerAsnDetails?.map(item => item.dataValues);

    // Build orderCode: 3 cases - (1) is_seller_central flag, (2) Seller Central via seller_id+asn_number on ASN, (3) Standard asnId
    let orderCode;
    const isSellerCentral = asnWithDetails?.seller_id && asnWithDetails?.asn_number;

    if (asnWithDetails?.is_seller_central === 1) {
      const rawName = asnWithDetails?.SellerAsnDetails?.[0]?.seller_name || asnWithDetails?.SellerAsnDetails?.[0]?.seller_id;
      const sellerFirstName = rawName?.split(' ')?.[0]?.toLowerCase();
      const sellerId = asnWithDetails?.SellerAsnDetails?.[0]?.seller_id;
      orderCode = `${sellerFirstName}-${sellerId}-${asnWithDetails?.asn_number}`;
      logger.info(`Seller Central Order Code to push asn ${asnData?.asnId} to Increff: ${orderCode}`);
    } else if (isSellerCentral) {
      const rawName = asnWithDetails?.SellerAsnDetails?.[0]?.seller_name || asnWithDetails?.seller_id;
      const sellerFirstName = rawName?.split(' ')?.[0]?.toLowerCase();
      orderCode = `${sellerFirstName}-${asnWithDetails.seller_id}-${asnWithDetails.asn_number}`;
      console.log(`[Increff Push] Seller Central ASN - orderCode: ${orderCode}`);
    } else {
      orderCode = asnData?.asnId?.toString();
      console.log(`[Increff Push] Standard ASN - orderCode: ${orderCode}`);
    }

    console.log(`[Increff Push] Building payload for ASN ID: ${asnData?.asnId}`);
    console.log(`[Increff Push] seller_id: ${asnWithDetails?.seller_id || 'NULL'}`);
    console.log(`[Increff Push] asn_number: ${asnWithDetails?.asn_number || 'NULL'}`);
    console.log(`[Increff Push] orderCode: ${orderCode}`);
    console.log(`[Increff Push] Items count: ${sellerDataValues?.length || 0}`);

    let inwardOrderPayload = {
      "orderTime": asnWithDetails?.startTime,
      "orderType": "PO",
      "orderCode": orderCode,
      "locationCode": global?.baseConfig?.pushToIncreffLocationCode || "110",
      "partnerCode": global?.baseConfig?.pushToIncreffPartnerCode || "110",
      "partnerLocationCode": global?.baseConfig?.pushToIncreffPartnerLocationCode || "110",
      orderItems: sellerDataValues.map(detail => ({
        "channelSkuCode": detail.sku,
        "quantity": Number(detail.qty_ordered),
        "sellingPricePerUnit": Number(detail.price),
        "orderItemCode": detail.sku
      }))
    };

    inwardOrderPayload = mergeOrderItemsAndTaxBreakupForms(inwardOrderPayload) || inwardOrderPayload;
    
    const increffApiUrl = global?.baseConfig?.pushToIncreffUrl;
    const headers = {
      'Content-Type': 'application/json',
      authUsername: global?.baseConfig?.pushToIncreffAuthUsername,
      authPassword: global?.baseConfig?.pushToIncreffAuthPassword
    };

    console.log(`[Increff Push] API URL: ${increffApiUrl}`);
    console.log(`[Increff Push] Payload:`, JSON.stringify(inwardOrderPayload, null, 2));

    const response = await axios.post(increffApiUrl, inwardOrderPayload, { headers });

    console.log(`Increff API response for:`, response?.data);

    return {
      success: true,
      message: 'Successfully pushed to Increff Inward API',
      increffResponse: response.data,
      asnId: asnData.asnId
    };

  } catch (error) {
    console.error('Error pushing to Increff Inward API:', error.response?.data || error.message);
    return {
      success: false,
      message: 'Failed to push to Increff Inward API',
      error: error.response?.data || error.message
    };
  }
};

/**
 * Get open ASN records older than specified time threshold
 * @param {number} timeThresholdMinutes - Time threshold in minutes
 * @returns {Promise<Object>} - Open ASN records with details
 */
const getOpenAsnRecordsOlderThan = async (timeThresholdMinutes) => {
  try {
    const thresholdTime = new Date();
thresholdTime.setMinutes(thresholdTime.getMinutes() - timeThresholdMinutes);

console.log(`Fetching ASN records that have exceeded ${timeThresholdMinutes} minutes threshold (before ${thresholdTime.toISOString()})`);

const openAsnRecords = await SellerAsn.findAll({
  where: {
    [require('sequelize').Op.or]: [
      { status: 'open' },
      { wms_status: 0 }
    ],
    startTime: {
      [require('sequelize').Op.lte]: thresholdTime
    }
  },
  include: [{
    model: SellerAsnDetails,
    as: 'SellerAsnDetails'
  }],
  order: [['startTime', 'ASC']] // Oldest first
});

    console.log(`Found ${openAsnRecords.length} open ASN records older than threshold`);

    return {
      success: true,
      count: openAsnRecords.length,
      records: openAsnRecords
    };

  } catch (error) {
    console.error('Error fetching open ASN records:', error);
    return {
      success: false,
      message: 'Error fetching open ASN records',
      error: error.message,
      records: []
    };
  }
};

/**
 * Get ASN record with details
 * @param {number} asnId - ASN ID
 * @returns {Promise<Object>} - ASN record with details
 */
const getSellerAsnDetails = async (asnId) => {
  try {
    const asn = await SellerAsn.findOne({
      where: { id: asnId },
      include: [{
        model: SellerAsnDetails,
        as: 'SellerAsnDetails'
      }]
    });

    if (!asn) {
      return {
        success: false,
        message: 'ASN not found'
      };
    }

    return {
      success: true,
      data: asn
    };

  } catch (error) {
    console.error('Error getting ASN details:', error);
    return {
      success: false,
      message: 'Error retrieving ASN details',
      error: error.message
    };
  }
};


const updateSplitSellerOrderStatus = async ({
  increment_id,
  timestamp,
  notificationId,
  waybill,
  ndr_status_description,
  rtoAwb,
  reqStatus
}) => {
  const { sequelize } = require('../models/seqModels/index');
  
  try {
    // Validation: Check if incrementId is provided
    if (!increment_id) {
      console.log('updateSplitSellerOrderStatus: increment_id is required');
      return {
        success: false,
        message: 'increment_id is required'
      };
    }

    // Validation: Check if status is provided
    if (!reqStatus) {
      console.log('updateSplitSellerOrderStatus: status is required');
      return {
        success: false,
        message: 'status is required'
      };
    }

    // Validation: Check if notificationId is provided
    if (!notificationId) {
      console.log('updateSplitSellerOrderStatus: notificationId is required');
      return {
        success: false,
        message: 'notificationId is required'
      };
    }

    // Validation: Check if waybill/track_number is provided
    if (!waybill) {
      console.log('updateSplitSellerOrderStatus: waybill/track_number is required');
      return {
        success: false,
        message: 'waybill/track_number is required'
      };
    }

    console.log('updateSplitSellerOrderStatus: Checking records for:', {
      increment_id,
      waybill,
      notificationId,
      reqStatus
    });

    const switchCaseVars = {
      toUpdateStatus: '',
      rtoAwb,
      waybill,
      toUpdateState: '',
      smsStatus: '',
      orderComment: '',
      clickpostMessage: '',
      pushToWms: false,
      callFraudelent: false,
      emailTemplate: ''
    };

    handleSwitchCases(
      switchCaseVars,
      notificationId,
      reqStatus,
      ndr_status_description,
      timestamp
    );

    // Step 1: Check split_seller_order table
    const splitSellerOrderQuery = `
      SELECT * 
      FROM split_seller_order 
      WHERE increment_id = :increment_id
    `;

    const splitSellerOrderResult = await sequelize.query(splitSellerOrderQuery, {
      replacements: { increment_id },
      type: sequelize.QueryTypes.SELECT
    });

    if (!splitSellerOrderResult || splitSellerOrderResult.length === 0) {
      console.log(`No record found in split_seller_order for increment_id: ${increment_id}`);
      return {
        status: false,
        errorMsg: `No data found for order ${increment_id}`
      };
    }

    const sellerOrder = splitSellerOrderResult[0];
    const order_id = sellerOrder.main_order_id || sellerOrder.split_order_id;
    const seller_id = sellerOrder.entity_id;

    console.log('Found split_seller_order:', {
      order_id,
      seller_id,
      increment_id
    });

    // Step 2: Check split_seller_shipment_track table
    const shipmentTrackQuery = `
      SELECT * 
      FROM split_seller_shipment_track 
      WHERE order_id = :order_id 
        AND seller_id = :seller_id 
        AND track_number = :track_number
    `;

    const shipmentTrackResult = await sequelize.query(shipmentTrackQuery, {
      replacements: {
        order_id: order_id.toString(),
        seller_id: seller_id.toString(),
        track_number: waybill
      },
      type: sequelize.QueryTypes.SELECT
    });

    if (!shipmentTrackResult || shipmentTrackResult.length === 0) {
      console.log(`No record found in split_seller_shipment_track for order_id: ${order_id}, seller_id: ${seller_id}, track_number: ${waybill}`);
      return {
        status: false,
        errorMsg: `No data found for AWB Number ${waybill}`
      };
    }

    console.log('Found split_seller_shipment_track record');

    // Step 4: Update status in split_seller_order table
    const updateSellerOrderQuery = `
      UPDATE split_seller_order 
      SET status = :status,
          updated_at = NOW()
      WHERE increment_id = :increment_id
    `;

    await sequelize.query(updateSellerOrderQuery, {
      replacements: {
        status: switchCaseVars.toUpdateStatus,
        increment_id
      },
      type: sequelize.QueryTypes.UPDATE
    });

    return {
      status: true,
      statusMsg: 'Shipment Update Successfull',
    };

  } catch (error) {
    console.error('Error in updateSplitSellerOrderStatus:', error);
    return {
      success: false,
      message: 'Error updating split seller order status',
      error: error.message
    };
  }
};



const bulkUpdateAsnStatusToClosed = async (asnIds) => {
  try {
    await SellerAsn.update({ status: 'closed', endTime: new Date() ,wms_status: 1}, { where: { id: asnIds } });
    return {
      success: true,
      message: 'Successfully updated ASN status to closed',
      asnIds: asnIds
    };
  } catch (error) {
    console.error('Error in bulkUpdateAsnStatusToClosed:', error);
    return {
      success: false,
      message: 'Error updating ASN status to closed',
      error: error.message,
      asnIds: asnIds
    };
  }
};

/**
 * Get open ASN records with separate thresholds for Standard (seller_id NULL) and Seller Central (seller_id NOT NULL) ASNs.
 * Standard ASNs use asnTimeThresholdMinutes, Seller Central ASNs use SELLER_CENTRAL_ASN_CLOSE_MINUTES.
 */
const getOpenAsnRecordsWithThresholds = async (standardThresholdMinutes, sellerCentralThresholdMinutes) => {
  try {
    const Op = require('sequelize').Op;
    const now = new Date();
    
    const standardThresholdTime = new Date(now.getTime() - standardThresholdMinutes * 60 * 1000);
    const sellerCentralThresholdTime = new Date(now.getTime() - sellerCentralThresholdMinutes * 60 * 1000);

    logger.info(`[ASN Close] Fetching open ASNs - Standard threshold: ${standardThresholdMinutes}min (before ${standardThresholdTime.toISOString()}), Seller Central threshold: ${sellerCentralThresholdMinutes}min (before ${sellerCentralThresholdTime.toISOString()})`);

    const standardAsnRecords = await SellerAsn.findAll({
      where: {
        [Op.or]: [{ status: 'open' }, { wms_status: 0 }],
        seller_id: { [Op.is]: null },
        startTime: { [Op.lte]: standardThresholdTime }
      },
      include: [{ model: SellerAsnDetails, as: 'SellerAsnDetails' }],
      order: [['startTime', 'ASC']]
    });

    const sellerCentralAsnRecords = await SellerAsn.findAll({
      where: {
        [Op.or]: [{ status: 'open' }, { wms_status: 0 }],
        seller_id: { [Op.not]: null },
        startTime: { [Op.lte]: sellerCentralThresholdTime }
      },
      include: [{ model: SellerAsnDetails, as: 'SellerAsnDetails' }],
      order: [['startTime', 'ASC']]
    });

    logger.info(`[ASN Close] Found ${standardAsnRecords.length} Standard ASNs (seller_id=NULL) and ${sellerCentralAsnRecords.length} Seller Central ASNs (seller_id NOT NULL) eligible for close`);

    return {
      success: true,
      standardRecords: standardAsnRecords,
      sellerCentralRecords: sellerCentralAsnRecords,
      totalCount: standardAsnRecords.length + sellerCentralAsnRecords.length
    };

  } catch (error) {
    logger.error('[ASN Close] Error fetching open ASN records:', error.message);
    return { success: false, message: error.message, standardRecords: [], sellerCentralRecords: [] };
  }
};

/**
 * Publish Seller Central ASN close notification to the same PubSub topic used for delivery status updates.
 * Uses type: 'asn_close' to differentiate from delivery status updates (type: 'update').
 * Enhanced payload includes additional fields from OMS seller_asn_details for Luna seller_asn_item table.
 */
/**
 * Publish "received_by_styli" status to Seller Central via Pub/Sub
 * Called when DELIVERED status is received for picked_up_by_styli sellers
 * @param {Object} deliveryData - Delivery data containing order details
 * @returns {Object} - Result with success status and messageId
 */
const publishReceivedByStyliStatus = async (deliveryData) => {
  try {
    const { PubSubService } = require('../pubsub/services');
    const SELLER_CENTRAL_ORDER_TOPIC = process.env.SELLER_CENTRAL_ORDER_TOPIC || 'seller-central-create-order-production';

    const { increment_id, sellerId } = deliveryData;

    logger.info(`[ReceivedByStyli] Building payload for increment_id: ${increment_id}, sellerId: ${sellerId}`);

    // Get seller order (specify attributes to avoid selecting non-existent columns like owner_seller_id)
    const sellerOrder = await SplitSellerOrder.findOne({
      where: { increment_id },
      attributes: ['entity_id', 'increment_id', 'seller_id']
    });

    if (!sellerOrder) {
      logger.error(`[ReceivedByStyli] Seller order not found for increment_id: ${increment_id}`);
      return { success: false, error: 'Seller order not found' };
    }

    // Get seller order items (SKUs) - only configurable type to avoid duplicates (same as Java)
    const sellerOrderItems = await SplitSellerOrderItem.findAll({
      where: { 
        seller_order_id: sellerOrder.entity_id,
        product_type: 'configurable'
      },
      attributes: ['sku']
    });

    logger.info(`[ReceivedByStyli] Found ${sellerOrderItems?.length || 0} items for seller_order: ${increment_id}`);

    // Build payload matching Java structure
    const payload = sellerOrderItems.map(item => ({
      sellerOrderId: increment_id,
      sku: item.sku,
      status: 'received',
      sellerId: sellerId
    }));

    if (payload.length === 0) {
      logger.info(`[ReceivedByStyli] No items found for ${increment_id}`);
      return { success: false, error: 'No items found' };
    }

    const pubsubMessage = {
      type: 'update',
      payload: payload
    };

    logger.info(`[ReceivedByStyli] Publishing to topic: ${SELLER_CENTRAL_ORDER_TOPIC}`);
    logger.info(`[ReceivedByStyli] Payload: ${JSON.stringify(pubsubMessage)}`);

    const messageId = await PubSubService.publishMessage(SELLER_CENTRAL_ORDER_TOPIC, pubsubMessage);
    logger.info(`[ReceivedByStyli] Successfully published for ${increment_id}, messageId: ${messageId}`);

    return { success: true, messageId };
  } catch (error) {
    logger.error(`[ReceivedByStyli] Failed to publish for ${deliveryData?.increment_id}:`, error.message);
    return { success: false, error: error.message };
  }
};

const publishSellerCentralAsnClose = async (asnRecord) => {
  try {
    const { PubSubService } = require('../pubsub/services');
    const SELLER_CENTRAL_ORDER_TOPIC = process.env.SELLER_CENTRAL_ORDER_TOPIC || 'seller-central-create-order-production';

    // Build enhanced payload with additional fields from OMS seller_asn_details
    const payload = (asnRecord.SellerAsnDetails || []).map(detail => {
      // Extract main_order_id and split_order_id from increment_id
      // Format: "003365821-L1-1304-1" → main="003365821", split="003365821-L1"
      let mainOrderId = null;
      let splitOrderId = null;
      
      if (detail.increment_id) {
        const parts = detail.increment_id.split('-');
        if (parts.length >= 2) {
          mainOrderId = parts[0];                    // "003365821"
          splitOrderId = `${parts[0]}-${parts[1]}`;  // "003365821-L1"
        }
      }

      return {
        sellerOrderId: detail.increment_id,
        sku: detail.sku,
        status: 'shipped',
        asnId: asnRecord.id,
        sellerId: asnRecord.seller_id || detail.seller_id,
        waybill: detail.waybill,
        qtyOrdered: detail.qty_ordered,
        
        // FIXED: Use extracted increment_ids instead of entity_ids
        orderId: mainOrderId,                          // → main_order_id (was entity_id)
        splitOrderId: splitOrderId,                    // → split_order_id (was entity_id)
        warehouseId: detail.warehouse_location_id,     // → warehouse_id
        productName: detail.name,                      // → product_name
        size: detail.item_size,                        // → size
        brandName: detail.item_brand_name,             // → (optional)
        imageUrl: detail.item_img_url,                 // → image_url
        qtyShipped: detail.qty_shipped,                // → qty_shipped
        pickedUpByStyli: 1   // → pickup_by_styli = 1 (Styli picks up from seller warehouse)
      };
    });

    // Build orderCode: {first_word_of_seller_name}-{seller_id}-{asn_number}
    const rawName = asnRecord.SellerAsnDetails?.[0]?.seller_name || asnRecord.seller_id;
    const sellerFirstName = rawName?.split(' ')?.[0]?.toLowerCase();
    const orderCode = `${sellerFirstName}-${asnRecord.seller_id}-${asnRecord.asn_number}`;

    const pubsubMessage = {
      type: 'asn_close',
      closedAt: new Date().toISOString(),
      asnId: asnRecord.id,
      sellerId: asnRecord.seller_id,
      orderCode: orderCode,  // SS-{seller_id}-{asn_number} format for Luna to store as asn_number
      pickedUpByStyli: 1,  // Flag to indicate Styli picks up from seller warehouse
      payload: payload
    };

    logger.info(`[ASN Close] Publishing Seller Central ASN ${asnRecord.id} to topic: ${SELLER_CENTRAL_ORDER_TOPIC}`);
    logger.info(`[ASN Close] orderCode: ${orderCode}`);
    logger.info(`[ASN Close] Payload items count: ${payload.length}`);
    
    const messageId = await PubSubService.publishMessage(SELLER_CENTRAL_ORDER_TOPIC, pubsubMessage);
    logger.info(`[ASN Close] Successfully published ASN ${asnRecord.id} to Seller Central, messageId: ${messageId}`);

    return { success: true, messageId };
  } catch (error) {
    logger.error(`[ASN Close] Failed to publish ASN ${asnRecord.id} to Seller Central:`, error.message);
    return { success: false, error: error.message };
  }
};




module.exports = {
  createSellerAsnRecord,
  updateSellerAsnStatus,
  pushToIncreffInwardAPI,
  getSellerAsnDetails,
  getOpenAsnRecordsOlderThan,
  getOpenAsnRecordsWithThresholds,
  publishSellerCentralAsnClose,
  publishReceivedByStyliStatus,
  updateSplitSellerOrderStatus,
  bulkUpdateAsnStatusToClosed,
  extractSellerIdFromIncrementId,
  getSellerConfigBySellerId,
  isPickedUpByStyliEnabled
};
