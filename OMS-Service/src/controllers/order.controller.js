const catchAsync = require('../utils/catchAsync');
const httpStatus = require('http-status');
const axios = require('axios');
const {
  ORDER_LIST_ENDPOINT,
  ORDER_DETAIL_ENDPOINT,
  ORDER_INVOICE_ENDPOINT,
  ORDER_SHIPMENT_ENDPOINT,
  OTS_ORDER_DETAILS_ENDPOINT
  // ARCHIVE_ORDER_LIST_ENDPOINT,
  // ARCHIVE_ORDER_DETAIL_ENDPOINT,
  // ARCHIVE_ORDER_INVOICE_ENDPOINT,
  // ARCHIVE_ORDER_SHIPMENT_ENDPOINT
} = require('../constants/javaEndpoints');

// const { OrderComment } = require('../models/seqModels/index');
const { getCreditMemos } = require('../helpers/creditMemo');
const { getStoreConfigs } = require('../utils/config');
const { getOrderStatus, getOrderStatusDetails } = require('../helpers/order');
const { getArchivedCreditMemos } = require('../helpers/archiveCreditMemo');
const { getLifetimeOrders, encryptAWB } = require('../helpers/utilities');
const { AUTH_INTERNAL_HEADER_BEARER_TOKEN } = process.env;
const internalAuthToken = AUTH_INTERNAL_HEADER_BEARER_TOKEN?.split(',')?.[0];

const statusPriority = [
  'processing',
  'packed',
  'shipped',
  'delivered',
  'closed',
  'canceled',
  'undelivered',
  'payment_failed',
  'pending_payment',
  'payment_canceled',
  'rto',
  'refunded',
  'rto_initiated',
  'payment_hold',
  'order_lost',
  'order_cancelled'
];
const lifetimeOrders = catchAsync(async (req, res) => {
  const { body } = req;
  const { customerId, customerEmail, websiteId } = body;
  const r = await getLifetimeOrders({ customerId, customerEmail, websiteId });
  if (r.error)
    return res.status(500).json({
      status: false,
      statusMsg: 'Error fetching lifetime orders',
      response: r
    });
  return res.status(200).json({
    status: true,
    statusMsg: 'Success fetching lifetime orders',
    response: r.response,
    responseList: r.responseList
  });
});

const orders = catchAsync(async (req, res) => {
  try {
    const { body } = req;
    if (body.ordersSource === 'archive') body.useArchive = true;
    // let url = ORDER_LIST_ENDPOINT;
    // if (body.ordersSource === 'archive') url = ARCHIVE_ORDER_LIST_ENDPOINT;
    const { filters } = body;
    const appVersionFromClient = filters?.appVersion
      ? filters?.appVersion?.split(',')
      : [];
    body.filters = {
      ...filters,
      appVersion: appVersionFromClient.map(el => {
        return el.trim();
      })
    };
    const response = await axios.post(ORDER_LIST_ENDPOINT, body, {
      headers: {
        Authorization: req.headers?.authorization || ''
      }
    });
    const { status, data } = response;
    res.status(status).json(data);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

const order = catchAsync(async (req, res) => {
  try {
    const { config } = global;
    const { environments } = config;
    const { stores } = environments[0];

    // const trackingBaseUrl = global?.baseConfig?.configs?.trackingBaseUrl;

    const { body } = req;
    body.fetchStoreCreditBalance = true;
    if (body.archived) body.useArchive = true;

    // let url = ORDER_DETAIL_ENDPOINT;
    // if (body.archived) url = ARCHIVE_ORDER_DETAIL_ENDPOINT;

    console.log("ORDER_DETAIL_ENDPOINT", ORDER_DETAIL_ENDPOINT);

    const response = await axios.post(ORDER_DETAIL_ENDPOINT, body, {
      headers: {
        Authorization: req.headers?.authorization || '',
        'authorization-token': internalAuthToken
      }
    });
    const { status, data } = response;
    if (data?.response) {
      const storeId = data?.response?.storeId;

      const storeDataAarray = stores.filter(function (store) {
        return store.storeId == storeId;
      });
      const storeLanguage = storeDataAarray.map(function (data) {
        return data.storeLanguage;
      });
      const trackingBaseUrl = `${global?.baseConfig?.configs?.trackingBaseUrl}/${storeLanguage}`;
      data.response.trackingBaseUrl = trackingBaseUrl;

      let showTax = true;
      const configValue = getStoreConfigs({
        key: 'taxPercentage',
        storeId: data?.response?.storeId
      });
      if (configValue.length) {
        const taxPercentage = configValue[0].taxPercentage;
        if (!taxPercentage || taxPercentage === 0) showTax = false;
      }
      data.response.showTax = showTax;

      if (data.response.splitOrder === true) {
        console.log("OTS_ORDER_DETAILS_ENDPOINT", OTS_ORDER_DETAILS_ENDPOINT);
        try {
          const splitOrderResponse = await axios.post(OTS_ORDER_DETAILS_ENDPOINT, {
            parentOrderId: data.response.orderId
          }, {
            headers: {
              Authorization: req.headers?.authorization || '',
              'authorization-token': internalAuthToken
            }
          });
       
          if (splitOrderResponse.data?.success && splitOrderResponse.data?.data) {
            const splitOrderData = splitOrderResponse.data.data;
            console.log("splitOrderData", splitOrderData);
            // Process products and add order-level history based on type
            if (data.response.products && Array.isArray(data.response.products)) {
              data.response.products.forEach(product => {
                if (product.type) {
                  // Check if the type matches any field in split order response
                  if (splitOrderData[product.type] && splitOrderData[product.type].statusMessage) {
                    // Create orderLevelHistory property for this product
                    product.orderLevelHistory = splitOrderData[product.type].statusMessage.filter(status => status.sellerOrderIncrementId === product.sellerOrderIncrementId);
                  }
                  
                  // Special handling for 'local' type - use express field
                  if (product.type === 'local' && splitOrderData.express && splitOrderData.express.statusMessage) {
                    product.orderLevelHistory = splitOrderData.express.statusMessage;
                  }
                }
              });
            }
            
          }
        } catch (splitOrderError) {
          console.error('Error fetching split order details:', splitOrderError.message);
        }

        if (Array.isArray(data.response.orderIds) && data.response.orderIds.length > 0) {
          // Find the highest priority status present
          let overallStatus = statusPriority[statusPriority.length - 1]; // default to last (lowest priority)
          for (const status of statusPriority) {
            if (data.response.orderIds.some(o => o.orderStatus === status)) {
              overallStatus = status;
              break;
            }
          }
          data.response.status = overallStatus;
          data.response.statusLabel = overallStatus;
        }
      }

   
    
    }

    const storesData = global.config?.environments?.[0]?.stores;
    if (
      data &&
      data.response &&
      data.response.totals &&
      data.response.totals.currency
    ) {
      data.response.totals.currencyConversionRate = storesData?.find(
        st => st.storeCurrency === data.response.totals.currency
      )?.currencyConversionRate;
    }

    res.status(status).json(data);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

const invoice = catchAsync(async (req, res) => {
  try {
    const { body } = req;
    if (body.archived) body.useArchive = true;

    // let url = ORDER_INVOICE_ENDPOINT;
    // if (body.archived) url = ARCHIVE_ORDER_INVOICE_ENDPOINT;

    const response = await axios.post(ORDER_INVOICE_ENDPOINT, body, {
      headers: {
        Authorization: req.headers?.authorization || '',
        'authorization-token': internalAuthToken
      }
    });
    const { status, data } = response;
    if (data?.response) {
      let showTax = true;
      const configValue = getStoreConfigs({
        key: 'taxPercentage',
        storeId: data?.response?.storeId
      });
      if (configValue.length) {
        const taxPercentage = configValue[0].taxPercentage;
        if (!taxPercentage || taxPercentage === 0) showTax = false;
      }
      data.response.showTax = showTax;
    }
    res.status(status).json(data);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

const shipment = catchAsync(async (req, res) => {
  try {
    const { body } = req;
    if (body.archived) body.useArchive = true;

    // let url = ORDER_SHIPMENT_ENDPOINT;
    // if (body.archived) url = ARCHIVE_ORDER_SHIPMENT_ENDPOINT;

    const response = await axios.post(ORDER_SHIPMENT_ENDPOINT, body, {
      headers: {
        Authorization: req.headers?.authorization || ''
      }
    });
    const { status, data } = response;
    res.status(status).json(data);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

const creditmemo = catchAsync(async (req, res) => {
  try {
    const { body } = req;
    const { orderId, archived } = body;
    if (!orderId) res.status(500).json({ error: 'Order ID not received!' });

    const { error, response } = archived
      ? await getArchivedCreditMemos({ orderId: orderId })
      : await getCreditMemos({ orderId: orderId });

    if (error) {
      return res.status(httpStatus.OK).json({
        status: false,
        statusCode: '201',
        statusMsg: error
      });
    }

    const payload = {
      status: true,
      statusCode: '200',
      statusMsg: 'Credit Memo(s) fetched!',
      response
    };
    return res.status(httpStatus.OK).json(payload);
  } catch (e) {
    return res.status(500).json({ error: e.message });
  }
});

const orderStatusList = catchAsync(async (req, res) => {
  try {
    const response = await getOrderStatus();
    res.status(httpStatus.OK).json({
      status: true,
      statusCode: '200',
      statusMsg: 'Success',
      response
    });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});
const orderStatusDetails = catchAsync(async (req, res) => {
  console.log("orderStatusDetails");
  try {
    const statusCode = req?.params?.statusCode;
    const response = await getOrderStatusDetails(statusCode);
    res.status(httpStatus.OK).json({
      status: true,
      statusCode: '200',
      statusMsg: 'Success',
      response
    });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

const getStoreConfig = (storeId) => {
  const appConfig = global.config;
  if (!appConfig || !appConfig.environments || !appConfig.environments[0] || !appConfig.environments[0].stores) {
    return { storeCurrency: 'SAR', storeId: storeId,fallbackCurrency: true }; // fallback
  }
  
  const stores = appConfig.environments[0].stores;
  const store = stores.find(s => s.storeId === storeId.toString());
  
  if (store) {
    return {
      storeCurrency: store.storeCurrency, // Dynamic currency from Consul config
      storeId: store.storeId,
      fallbackCurrency: false
    };
  }
  
  return { storeCurrency: 'SAR', storeId: storeId ,fallbackCurrency: true}; // fallback
};

/**
 * Helper function to check if an order is a seller central order
 * by looking up seller_id and warehouse_id in Consul's seller_inventory_mapping

 */
const isSellerCentralOrder = (sellerId, warehouseId) => {

  try {
    // Access Consul configuration from global object
    // The seller_inventory_mapping is an array in the Consul config
    const sellerInventoryMapping = global.javaOrderServiceConfig?.seller_inventory_mapping;
    

    
    // If seller_inventory_mapping doesn't exist, return false
    if (!sellerInventoryMapping || !Array.isArray(sellerInventoryMapping)) {
      console.log('[isSellerCentralOrder] seller_inventory_mapping not found in Consul config or is not an array');
      return false;
    }
    
    console.log(`[isSellerCentralOrder] seller_inventory_mapping has ${sellerInventoryMapping.length} entries`);
    console.log(`[isSellerCentralOrder] Available mappings:`, JSON.stringify(sellerInventoryMapping.slice(0, 5))); // Log first 5 entries
    
    // Loop through the seller_inventory_mapping array to find matching seller_id and warehouse_id
    const matchingMapping = sellerInventoryMapping.find(mapping => {
      // Check if SELLER_ID and warehouse_id match
      // Note: SELLER_ID in Consul might be a string, so we compare as strings
      const sellerIdMatch = String(mapping.SELLER_ID) === String(sellerId);
      const warehouseIdMatch = String(mapping.warehouse_id) === String(warehouseId);
      
      if (sellerIdMatch || warehouseIdMatch) {
        console.log(`[isSellerCentralOrder] Partial match found - mapping SELLER_ID=${mapping.SELLER_ID}, warehouse_id=${mapping.warehouse_id}, PUSH_TO_SELLER_CENTRAL=${mapping.PUSH_TO_SELLER_CENTRAL}`);
      }
      
      return sellerIdMatch && warehouseIdMatch;
    });
    
    // If we found a matching mapping, check if it's seller central
    // The field name might be IS_SELLER_CENTRAL or similar - adjust based on actual Consul structure
    if (matchingMapping) {
      console.log(`[isSellerCentralOrder] Full match found:`, JSON.stringify(matchingMapping));
      // Check for seller central indicator field
      // Common field names: IS_SELLER_CENTRAL, is_seller_central, SELLER_CENTRAL, etc.
      // Check for seller central indicator field: PUSH_TO_SELLER_CENTRAL
      const isSellerCentral = matchingMapping.PUSH_TO_SELLER_CENTRAL === true || 
                              matchingMapping.PUSH_TO_SELLER_CENTRAL === 'true';
      
      console.log(`[isSellerCentralOrder] PUSH_TO_SELLER_CENTRAL=${matchingMapping.PUSH_TO_SELLER_CENTRAL}, isSellerCentral=${isSellerCentral}`);
      return isSellerCentral;
    }
    
    // No matching mapping found, not a seller central order
    console.log(`[isSellerCentralOrder] No matching mapping found for seller_id=${sellerId}, warehouse_id=${warehouseId}`);
    return false;
  } catch (error) {
    console.error('Error checking seller central status:', error);
    // On error, default to false (not seller central)
    return false;
  }
};

/**
 * Helper function to format time difference as "Xh Ym Zs"
 * 
 * @param {number} milliseconds - Time difference in milliseconds
 * @returns {string} - Formatted time string like "1h 45m 30s"
 */
const formatTimeRemaining = (milliseconds) => {
  if (!milliseconds || milliseconds <= 0) {
    return '0s';
  }
  
  const totalSeconds = Math.floor(milliseconds / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  
  const parts = [];
  if (hours > 0) parts.push(`${hours}h`);
  if (minutes > 0) parts.push(`${minutes}m`);
  if (seconds > 0 || parts.length === 0) parts.push(`${seconds}s`);
  
  return parts.join(' ');
};

/**
 * Helper function to calculate time remaining for next status based on timelines
 * 
 * @param {string} currentStatus - Current order status
 * @param {object|string|null} timelines - Timelines object from database (JSON string or object)
 * @returns {string} - Time remaining as "Xh Ym Zs" or "N/A" if timelines are null
 */
// Convert DB timestamp (KSA local time) → JS Date
const parseAsKSA = (dateStr) => {
  if (!dateStr) return null;
  return new Date(dateStr.replace(" ", "T") + "+03:00");
};

const calculateTimeRemaining = (currentStatus, timelines) => {
  if (!timelines) {
    return 'N/A';
  }
  
  let timelinesObj = timelines;
  if (typeof timelines === 'string') {
    try {
      timelinesObj = JSON.parse(timelines);
    } catch (error) {
      console.error('Error parsing timelines JSON:', error);
      return 'N/A';
    }
  }

  if (!timelinesObj || typeof timelinesObj !== 'object') {
    return 'N/A';
  }

  const now = new Date();
  let slaDateTime = null;

  const statusLower = (currentStatus || '').toLowerCase();

  if (statusLower === 'processing' || statusLower === 'pending') {
    slaDateTime = parseAsKSA(timelinesObj.accSla);
  } else if (statusLower === 'accepted') {
    slaDateTime = parseAsKSA(timelinesObj.packSla);
  } else if (statusLower === 'packed') {
    slaDateTime = parseAsKSA(timelinesObj.shipSla);
  } else if (statusLower === 'shipped' || statusLower === 'delivered' || statusLower === 'closed') {
    return 'N/A';
  } else {
    slaDateTime = parseAsKSA(timelinesObj.accSla);
  }

  if (!slaDateTime || isNaN(slaDateTime.getTime())) {
    return 'N/A';
  }

  const timeDiff = slaDateTime.getTime() - now.getTime();

  if (timeDiff <= 0) {
    return '0s';
  }

  return formatTimeRemaining(timeDiff);
};


/**
 * Helper function to generate threshold_info for seller central orders
 * Calculates time remaining based on timelines from database
 * 
 * @param {object} orderData - Order data including status, timestamps, and timelines
 * @returns {object} - Threshold info object with status progression and time remaining
 */
const getThresholdInfo = (orderData) => {
  // Define status progression for seller central orders
  // All statuses are in lowercase to match database storage
  const statusProgression = [
    { status: 'processing', is_completed: false, is_current: false, is_next: false },
    { status: 'accepted', is_completed: false, is_current: false, is_next: false },
    { status: 'packed', is_completed: false, is_current: false, is_next: false },
    { status: 'shipped', is_completed: false, is_current: false, is_next: false },
    { status: 'delivered', is_completed: false, is_current: false, is_next: false },
    { status: 'closed', is_completed: false, is_current: false, is_next: false } // Add closed status as terminal state
  ];
  
  // Map current status to progression
  // Handle "assorted" as equivalent to "accepted" (accSla = Accepted SLA)
  // All statuses from database are lowercase
  let currentStatus = (orderData.seller_order_status || 'processing').toLowerCase();
  if (currentStatus === 'assorted') {
    currentStatus = 'accepted'; // Map assorted to accepted for status progression
  }
  const currentIndex = statusProgression.findIndex(s => s.status === currentStatus);
  
  // Calculate time remaining for next status based on timelines
  // For "closed" status, time remaining should be "N/A" (terminal state)
  const timeRemaining = currentStatus === 'closed' ? 'N/A' : calculateTimeRemaining(currentStatus, orderData.timelines);
  
  if (currentIndex >= 0) {
    // For "closed" status, don't mark previous statuses as completed
    // Closed is a cancellation/terminal state, so previous statuses remain incomplete
    if (currentStatus !== 'closed') {
      // Mark all previous statuses as completed (normal flow)
      for (let i = 0; i < currentIndex; i++) {
        statusProgression[i].is_completed = true;
        if (i === currentIndex - 1 && orderData.last_update) {
          statusProgression[i].completed_at = orderData.last_update;
        }
      }
    }
    
    // Mark current status
    statusProgression[currentIndex].is_completed = true;
    statusProgression[currentIndex].is_current = true;
    if (orderData.last_update) {
      statusProgression[currentIndex].completed_at = orderData.last_update;
    }
    
    // Mark next status if exists (but not for closed, as it's terminal)
    if (currentIndex + 1 < statusProgression.length && currentStatus !== 'closed') {
      statusProgression[currentIndex + 1].is_next = true;
      // Set time_remaining for the next status
      statusProgression[currentIndex + 1].time_remaining = timeRemaining;
    }
  } else {
    // If status not found in progression (edge case), still return structure
    // This handles any unexpected statuses
  }
  
  return {
    current_status: currentStatus,
    time_remaining_for_next_status: timeRemaining,
    status_progression: statusProgression
  };
};

/**
 * Deduplicates order results by keeping only the latest entry for each seller_order_id + sku combination
 * @param {Array} results - Array of order result rows from database query
 * @returns {Array} - Deduplicated array with only the latest entries
 */
const deduplicateOrderResults = (results) => {
  const uniqueResultsMap = new Map();
  
  results.forEach(row => {
    const uniqueKey = `${row.seller_order_id}_${row.sku}`;
    
    if (!uniqueResultsMap.has(uniqueKey)) {
      // First occurrence - store it
      uniqueResultsMap.set(uniqueKey, row);
      return;
    }
    
    // Duplicate found - compare timestamps and keep only the latest one
    const existing = uniqueResultsMap.get(uniqueKey);
    const existingTime = existing.last_update ? new Date(existing.last_update).getTime() : 0;
    const rowTime = row.last_update ? new Date(row.last_update).getTime() : 0;
    
    // Keep only the row with the latest timestamp, discard the older one
    if (rowTime > existingTime) {
      uniqueResultsMap.set(uniqueKey, row);
    }
  });
  
  return Array.from(uniqueResultsMap.values());
};

const sellerOrderDetails = catchAsync(async (req, res) => {
  console.log("sellerOrderDetails");
  try {
    const {isSplitOrder, orders } = req.body;
    console.log("isSplitOrder", isSplitOrder);
    const salesOrderItemTable = isSplitOrder === true ?  "split_sales_order_item" : "sales_order_item";
    const salesOrderTable = isSplitOrder === true ? "split_sales_order" : "sales_order";
    const salesOrderJoinColumn = isSplitOrder === true ? "order_id" : "entity_id";
    
    if (!orders || !Array.isArray(orders) || orders.length === 0) {
      return res.status(400).json({
        status: false,
        statusMsg: 'orders array is required with seller_order_id and sku combinations',
        response: null
      });
    }

    // Validate each order in the array
    for (const order of orders) {
      if (!order.seller_order_id || !order.sku) {
        return res.status(400).json({
          status: false,
          statusMsg: 'Each order must have seller_order_id and sku',
          response: null
        });
      }
    }

    
    const AWB_ENCRYPTION_SECRET = global.javaOrderServiceConfig?.order_details?.AWB_ENCRYPTION_SECRET;
    const AWB_ENCRYPTION_SALT = global.javaOrderServiceConfig?.order_details?.AWB_ENCRYPTION_SALT;
    const BETA_TRACK_URL = global.javaOrderServiceConfig?.order_details?.BETA_TRACK_URL;
    if (!AWB_ENCRYPTION_SECRET || !AWB_ENCRYPTION_SALT) {
      return res.status(500).json({
        status: false,
        statusMsg: 'AWB encryption keys not found in Consul configuration',
        response: null
      });
    }
    const { sequelize } = require('../models/seqModels/index');
    const axios = require('axios');
    
    // Build dynamic query for multiple combinations
    // UPDATED: Now including seller_id and warehouse_id from split_seller_order_item table
    // These fields are needed to identify seller central orders from Consul config
    const whereConditions = orders.map((_, index) => 
      `(so.entity_id = :seller_order_id_${index} AND soi.sku = :sku_${index})`
    ).join(' OR ');

    const query = `
      SELECT DISTINCT
          so.entity_id AS seller_order_id,
          so.increment_id as seller_order_increment_id,
          so.status,
          so.seller_central_acknowledgement,
          soi.seller_name,
          soi.store_id,
          so.main_order_id AS parent_order_id,
          so.shipment_mode,
          ssoi.name AS product_name,
          ssoi.sku,
          ssoi.item_size AS size,
          soi.qty_ordered AS quantity,
          ssoi.price AS amount,
          so.increment_id,
          sst.track_number,
          sst.title AS carrier_code,
          sst.description AS carrier_name,
          sss.increment_id AS shipment_increment_id,
          so.updated_at AS last_update,
          soi.seller_id,
          soi.warehouse_id,
          so.timelines,
          sso.status AS sales_order_status,
          sso.increment_id AS split_order_increment_id,
          sad.waybill AS seller_central_awb,
          so.cancellation_reason
      FROM split_seller_order so
      JOIN split_seller_order_item soi 
          ON so.entity_id = soi.seller_order_id
      JOIN ${salesOrderItemTable} ssoi
          ON soi.main_order_id = ssoi.order_id
         AND soi.sku = CONVERT(ssoi.sku USING utf8mb4) COLLATE utf8mb4_unicode_ci
      LEFT JOIN ${salesOrderTable} sso
          ON so.main_order_id = sso.${salesOrderJoinColumn}
         ${isSplitOrder === true ? "AND so.increment_id LIKE CONCAT(sso.increment_id, '-%')" : ""}
      LEFT JOIN split_seller_shipment_track sst
          ON so.entity_id = sst.seller_id
      LEFT JOIN split_seller_shipment sss
          ON so.entity_id = sss.seller_order_id
       LEFT JOIN seller_asn_details sad
          ON so.increment_id COLLATE utf8mb4_unicode_ci = sad.increment_id COLLATE utf8mb4_unicode_ci
         
      WHERE ${whereConditions}
    `;
    // Build replacements object
    const replacements = {};
    orders.forEach((order, index) => {
      replacements[`seller_order_id_${index}`] = order.seller_order_id;
      replacements[`sku_${index}`] = order.sku;
    });

    // Separate query to get item_id for each seller_order_id + sku combination
    const itemIdWhereConditions = orders.map((_, index) => 
      `(seller_order_id = :seller_order_id_${index} AND sku = :sku_${index})`
    ).join(' OR ');

    const itemIdQuery = `
      SELECT 
          seller_order_id,
          sku,
          item_id
      FROM split_seller_order_item 
      WHERE product_type = 'simple'
        AND (${itemIdWhereConditions})
    `;
    
    const itemIdResults = await sequelize.query(itemIdQuery, {
      replacements: replacements,
      type: sequelize.QueryTypes.SELECT
    });
    
    // Create a map for quick lookup
    const itemIdMap = {};
    itemIdResults.forEach(row => {
      const key = `${row.seller_order_id}_${row.sku}`;
      itemIdMap[key] = row.item_id;
    });

    const results = await sequelize.query(query, {
      replacements: replacements,
      type: sequelize.QueryTypes.SELECT
    });

    if (!results || results.length === 0) {
      return res.status(200).json({
        orders: []
      });
    }

    // Deduplicate results based on seller_order_id + sku combination
    // This handles cases where LEFT JOINs create duplicate rows
    // Strategy: Keep only the LATEST entry (based on last_update timestamp) and discard older duplicates
    const uniqueResults = deduplicateOrderResults(results);

    // Group results by parent_order_id and shipment_mode for efficient API calls
    const orderGroups = {};
    uniqueResults.forEach(row => {
      const key = `${row.parent_order_id}_${row.shipment_mode}`;
      if (!orderGroups[key]) {
        orderGroups[key] = {
          parent_order_id: row.parent_order_id,
          shipment_mode: row.shipment_mode,
          items: []
        };
      }
      orderGroups[key].items.push(row);
    });

    // Fetch order history for each group
    const orderHistoryPromises = Object.values(orderGroups).map(async (group) => {
      try {
        console.log(`Fetching history for parent_order_id: ${group.parent_order_id}, shipment_mode: ${group.shipment_mode}`);
        
        const historyResponse = await axios.post(
          OTS_ORDER_DETAILS_ENDPOINT,
          {
            parentOrderId: group.parent_order_id
          },
          {
            headers: {
              'Content-Type': 'application/json'
            }
          }
        );

        if (historyResponse.data && historyResponse.data.success) {
          return {
            parent_order_id: group.parent_order_id,
            shipment_mode: group.shipment_mode,
            historyData: historyResponse.data.data,
            items: group.items,
            api_status: 'success' 
          };
        }
        return {
          parent_order_id: group.parent_order_id,
          shipment_mode: group.shipment_mode,
          historyData: null,
          items: group.items,
          api_status: 'failed'
        };
      } catch (error) {
        console.error(`Error fetching history for parent_order_id ${group.parent_order_id}:`, error.message);
        return {
          parent_order_id: group.parent_order_id,
          shipment_mode: group.shipment_mode,
          historyData: null,
          items: group.items,
          api_status: 'failed'
        };
      }
    });

    const orderHistoryResults = await Promise.all(orderHistoryPromises);
    console.log("orderHistoryResults", orderHistoryResults);

    // Create a map for quick lookup of history data
    const historyMap = {};
    const StatusMap = {};
    orderHistoryResults.forEach(result => {
      historyMap[result.parent_order_id] = result.historyData;
      StatusMap[result.parent_order_id] = result.api_status;
    });

    // Fetch back order information
    // Step 1: Collect all unique seller_order_ids from results
    const sellerOrderIds = [...new Set(uniqueResults.map(row => row.seller_order_id))];
    const backOrderMap = {};
    const asnCodeMap = {};
    
    console.log("[#SFP-1178] sellerOrderIds", sellerOrderIds);
    if (sellerOrderIds.length === 0) {
      // No seller order IDs to process
    } else {
      try {
        // Step 2: Query seller_back_order_item to get back_order_ids for each seller order
        const backOrderItemQuery = `
          SELECT DISTINCT
            seller_order_id,
            back_order_id,
            asn_code
          FROM seller_back_order_item
          WHERE seller_order_id IN (:sellerOrderIds)
        `;

        const backOrderItems = await sequelize.query(backOrderItemQuery, {
          replacements: { sellerOrderIds },
          type: sequelize.QueryTypes.SELECT
        });

        console.log('[#SFP-1178] backOrderItems', backOrderItems);

        if (backOrderItems.length === 0) {
          // No back order items found
        } else {
          // Step 3: Extract all back_order_ids
          const backOrderIds = [
            ...new Set(backOrderItems.map(item => item.back_order_id))
          ];
          console.log('[#SFP-1178] backOrderIds', backOrderIds);

          // Step 4: Query seller_back_order to get increment_ids
          const backOrderQuery = `
            SELECT 
              entity_id,
              back_order_incrementid
            FROM seller_back_order
            WHERE entity_id IN (:backOrderIds)
          `;

          const backOrders = await sequelize.query(backOrderQuery, {
            replacements: { backOrderIds },
            type: sequelize.QueryTypes.SELECT
          });

          console.log('[#SFP-1178] backOrders', backOrders);

          // Step 5: Create a map of back_order_id to back_order_incrementid
          const backOrderIdToIncrementId = {};
          backOrders.forEach(bo => {
            backOrderIdToIncrementId[bo.entity_id] = bo.back_order_incrementid;
          });

          console.log(
            '[#SFP-1178] backOrderIdToIncrementId',
            backOrderIdToIncrementId
          );

          // Step 6: Group increment_ids and asn_codes by seller_order_id
          backOrderItems.forEach(item => {
            const incrementId = backOrderIdToIncrementId[item.back_order_id];
            if (incrementId) {
              backOrderMap[item.seller_order_id] = backOrderMap[item.seller_order_id] || [];
              backOrderMap[item.seller_order_id].push(incrementId);
            }

            // Group asn_codes by seller_order_id
            if (item.asn_code) {
              asnCodeMap[item.seller_order_id] = asnCodeMap[item.seller_order_id] || [];
              asnCodeMap[item.seller_order_id].push(item.asn_code);
            }
          });

          console.log('[#SFP-1178] backOrderMap', backOrderMap);
          console.log('[#SFP-1178] asnCodeMap', asnCodeMap);
        }
      } catch (error) {
        console.error('[#SFP-1178] Error fetching back order information:', error);
        // Continue without back order info - backward compatible
      }
    }

    // Helper function to get SKU history
    const getSkuHistory = (historyData, sku, sellerOrderIncrementId) => {
      if (!historyData) {
        return [];
      }
    
      let allSkuHistory = [];
    
      // Check global SKUs
      if (historyData.global && historyData.global.skus) {
        const globalSkuData = historyData.global.skus.find(s => s.sku === sku);
        if (globalSkuData && globalSkuData.statusMessage) {
          const globalHistory = globalSkuData.statusMessage
          .filter(status => !status.sellerOrderIncrementId || status.sellerOrderIncrementId === sellerOrderIncrementId)
          .map(status => ({
            timestamp: status.timestamp,
            status: status.message,
            statusId: status.statusId,
            source: 'global' // Add source to identify which one
          }));
          allSkuHistory = allSkuHistory.concat(globalHistory);
        }
      }
    
      // Check express SKUs
      if (historyData.express && historyData.express.skus) {
        const expressSkuData = historyData.express.skus.find(s => s.sku === sku);
        if (expressSkuData && expressSkuData.statusMessage) {
          const expressHistory = expressSkuData.statusMessage
          .filter(status => !status.sellerOrderIncrementId || status.sellerOrderIncrementId === sellerOrderIncrementId)
          .map(status => ({
            timestamp: status.timestamp,
            status: status.message,
            statusId: status.statusId,
            source: 'express' // Add source to identify which one
          }));
          allSkuHistory = allSkuHistory.concat(expressHistory);
        }
      }
    
      // Sort by timestamp to get chronological order
      return allSkuHistory.sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp));
    };
    
    // Process the results to match your expected response format
    const processedOrders = uniqueResults.map(row => {
      const historyData = historyMap[row.parent_order_id];
      const skuHistory = getSkuHistory(historyData, row.sku, row.seller_order_increment_id);
      console.log("skuHistory", skuHistory);
      const apiStatus = StatusMap[row.parent_order_id];
      const storeConfig = getStoreConfig(row.store_id);
      
      // Get back order increment_id(s) for this specific seller order
      const backOrderIncrementIds = backOrderMap[row.seller_order_id] || null;
      
      // Get asn_code(s) for this specific seller order
      const asnCodes = asnCodeMap[row.seller_order_id] || null;
      
      // STEP 1: Identify if this is a seller central order
      // Check Consul configuration using seller_id and warehouse_id from the database
      const isSellerCentral = isSellerCentralOrder(row.seller_id, row.warehouse_id);
      console.log(`Order ${row.seller_order_id}: seller_id=${row.seller_id}, warehouse_id=${row.warehouse_id}, isSellerCentral=${isSellerCentral}`);
      
      // STEP 1.5: Determine the actual status based on order type
      // - Non-seller central: Use split_seller_order.status (row.status)
      // - Seller central: Use split_sales_order.status (row.sales_order_status)
      let actualStatus = row.status; // Default to split_seller_order.status
      if (isSellerCentral) {
        
        // For seller central orders, use split_sales_order status
      
        // Special case: if status is 'processing' and acknowledged, treat as 'accepted'
        if (actualStatus && actualStatus.toLowerCase() === 'processing' && row.seller_central_acknowledgement === 1) {
          actualStatus = 'accepted';
        }
      }
      // For non-seller central orders, actualStatus remains as row.status (from split_seller_order)
      
      // STEP 2: Build base order data that's common to both types
      const baseOrderData = {
        seller_order_id: row.seller_order_id,
        seller_name: row.seller_name,
        seller_order_status: actualStatus,
        seller_order_increment_id: row.seller_order_increment_id,
        base_track_url: BETA_TRACK_URL || null,
        store_id: row.store_id,
        items_count: 1, // Will be populated from database later
        is_seller_central: isSellerCentral,
        styli_warehouse_id: row.warehouse_id || null,
        last_update: row.last_update,
        // Last mile status from split_sales_order - used to control cancel button visibility
        last_mile_status: row.sales_order_status || null,
        // Split order increment_id from split_sales_order table
        increment_id: row.split_order_increment_id || null,
        // Shipment mode (local/global) - used to determine AWB display logic
        shipment_mode: row.shipment_mode || null,
        // Cancellation reason from split_seller_order table
        cancellation_reason: row.cancellation_reason || null,
        // Back order increment IDs (array if multiple, null if none)
        back_order_increment_id: backOrderIncrementIds || null,
        // ASN codes (array if multiple, null if none)
        asn_code: asnCodes || null,
        order_items: [
          {
            product_name: row.product_name,
            sku: row.sku,
            size: row.size,
            quantity: row.quantity,
            total_price: {
              currency: storeConfig.storeCurrency,
              amount: row.amount,
              // For seller central orders, fallbackCurrency is not included
              ...(isSellerCentral ? {} : { fallbackCurrency: storeConfig.fallbackCurrency })
            },
            status: actualStatus
          }
        ],
        order_history: skuHistory
      };
      
      // STEP 3: Build response based on order type (seller central vs regular)
      if (isSellerCentral) {
        // SELLER CENTRAL ORDER RESPONSE STRUCTURE
        // Seller central orders have a different structure:
        // - No AWB/tracking info (awb_number, encrypted_awb_number, carrier, service)
        // - No shipment_increment_id
        // - No order_item_code
        // - No tracking_info
        // - No internal_ots_call_status
        // - Includes threshold_info with status progression
        return {
          ...baseOrderData,
          awb_number: row.seller_central_awb || null,
          // Add threshold_info for seller central orders
          // Pass timelines from database to calculate time_remaining_for_next_status
          threshold_info: getThresholdInfo({
            seller_order_status: actualStatus,
            last_update: row.last_update,
            timelines: row.timelines // Pass timelines from database query
          })
        };
      } else {
        // REGULAR ORDER (NON-SELLER CENTRAL) RESPONSE STRUCTURE
        // Regular orders include all tracking and shipment information
        let encryptedTrackNumber = null;
        if (row.track_number) {
          encryptedTrackNumber = encryptAWB(row.track_number, AWB_ENCRYPTION_SECRET, AWB_ENCRYPTION_SALT);
        }
        
        // Get item_id from the separate query
        const itemIdKey = `${row.seller_order_id}_${row.sku}`;
        const orderItemCode = itemIdMap[itemIdKey] || null;
        
        return {
          ...baseOrderData,
          shipment_increment_id: row.shipment_increment_id,
          order_item_code: orderItemCode,
          awb_number: row.track_number,
          encrypted_awb_number: encryptedTrackNumber,
      
          carrier: row.carrier_code,
          service: row.carrier_name,
          last_update: row.last_update,
          tracking_info: {
            awb_number: row.track_number,
            carrier: row.carrier_code,
            service: row.carrier_name,
        
          },
          internal_ots_call_status: apiStatus
        };
      }
    });

    
    return res.status(200).json({
      orders: processedOrders
    });

  } catch (error) {
    console.error('Error in sellerOrderDetails:', error);
    return res.status(500).json({
      status: false,
      statusMsg: 'Internal server error',
      response: error.message
    });
  }
});

module.exports = {
  lifetimeOrders,
  orders,
  order,
  invoice,
  shipment,
  creditmemo,
  orderStatusList,
  orderStatusDetails,
  sellerOrderDetails
};