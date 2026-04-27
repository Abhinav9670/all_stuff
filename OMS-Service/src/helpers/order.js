/* eslint-disable max-lines */
const {
  Order,
  Shipment,
  OrderAddress,
  OrderComment,
  OrderItem,
  OrderPayment,
  OrderGrid,
  Creditmemo,
  OrderStatus,
  OrderStatusState,
  sequelize,
  SubSalesOrder,
  SubSalesOrderItem,
  RmaRequest,
  SplitSalesOrder
} = require('../models/seqModels/index');
const {
  Order:OrderArchive,
  OrderAddress:OrderAddressArchive,
  OrderPayment:OrderPaymentArchive,
  SubSalesOrder:SubSalesOrderArchive,
  OrderItem : OrderItemArchive
} = require('../models/seqModels/archiveIndex');
const { QueryTypes, where, Op, literal } = require('sequelize');
const smsObj = require('./sms')
const {
  STATUS_HISTORY_MAP,
  ORDER_SHIPPED_STATUS_CODE,
  WMS_RTO_PUSH_PENDING_STATUS,
  ORDER_DELIVERED_STATUS_CODE,
  FAILED_DELIVERY
} = require('../constants/order');
const { promiseAll, getUtcTIme } = require('../utils');
const emailOBJ = require('./email')
const { logBrazeCustomEventForDelivery , logBrazeCustomEventForShipped, logBrazecustomEventForFailedDelivery } = require('./braze');
const { updateStatusHistory } = require('./utilities');
const { earnEventForDeliverySuccess } = require('./eas/earnIntegration');
const { eventForDelivery } = require('./cleverTap');
const { adjustDeliveredEvent } = require('./adjust');
const { sendMessage } = require('./forwardShipment');
const getWpMessage = require('../utils/getWhatsappMsg');
const moment = require('moment');
const { PubSubService } = require('../pubsub/services');
const logger = require('../config/logger');

// Seller Central PubSub topic for order status updates (configurable per environment)
const SELLER_CENTRAL_ORDER_TOPIC = process.env.SELLER_CENTRAL_ORDER_TOPIC || 'seller-central-create-order-production';
// Import split order utilities
const {
  isSplitOrderPattern,
  buildSplitAwareIncludeArray,
  buildSplitSalesOrderIncludeArray,
  processSplitOrderData,
  processSplitSalesOrderData
} = require('../utils/splitOrderUtils');

const buildIncludeArray = (inclSubSales) => {
  const include = [
    { model: Shipment, as: 'Shipments' },
    { model: OrderAddress, as: 'OrderAddresses' },
    { model: OrderItem, as: 'OrderItems' },
    { model: Creditmemo, as: 'Creditmemos' },
    { model: OrderPayment, as: 'OrderPayments' }
  ];

  if (inclSubSales) {
    include.push({ model: SubSalesOrder, as: 'SubSalesOrders' });
  }

  return include;
};

const processOrderItems = (items) => {
  const simpleItems = items?.filter(i => i.product_type === 'simple');
  const configItems = items?.filter(i => i.product_type === 'configurable');
  return { simpleItems, configItems };
};

const findShippingAddress = (orderAddresses) => {
  return orderAddresses?.reduce((adrs, address) => {
    if (address.dataValues?.address_type === 'shipping') {
      adrs = address.dataValues;
    }
    return adrs;
  }, {});
};

exports.getOrder = async ({ incrementId, entityId, inclSubSales }) => {
  console.log(" WMS RTO Push : getOrder: incrementId ", incrementId);
  console.log(" WMS RTO Push : getOrder: entityId ", entityId);
  
  const where = incrementId
    ? { increment_id: incrementId }
    : { entity_id: entityId };

  // Check if this is a split order based on increment_id pattern
  const isSplitOrder = incrementId && isSplitOrderPattern(incrementId);
  
  let orderData;
  
  if (isSplitOrder) {
    // Use SplitSalesOrder table for split orders
    const { SplitSalesOrder } = require('../models/seqModels/index');
    const include = buildSplitSalesOrderIncludeArray(inclSubSales);
    
    console.log("Split order detected, querying SplitSalesOrder table for increment_id:", incrementId);
    orderData = await SplitSalesOrder.findOne({ where, include });
  } else {
    // Use regular Order table for normal orders
    const include = [
      { model: Shipment, as: 'Shipments' },
      { model: OrderAddress, as: 'OrderAddresses' },
      { model: OrderItem, as: 'OrderItems' },
      { model: Creditmemo, as: 'Creditmemos' },
      { model: OrderPayment, as: 'OrderPayments' }
    ];

    if (inclSubSales) {
      include.push({ model: SubSalesOrder, as: 'SubSalesOrders' });
    }
    
    orderData = await Order.findOne({ where, include });
  }
  
  try {
    const orderInfo = orderData?.dataValues;
    
    if (isSplitOrder) {
      // Process split sales order data using the utility function
      const processedData = processSplitSalesOrderData(orderInfo);
      
      // IMPORTANT: For split orders, use order_id from SplitSalesOrder as entity_id
      // This ensures status history and other operations reference the correct parent record
      if (processedData && orderInfo?.order_id) {
        // Store the original split_sales_order.entity_id for seller order queries (e.g., 50609)
        processedData.split_entity_id = orderInfo.entity_id;
        // Use order_id as entity_id for status history operations (e.g., 9353729)
        processedData.entity_id = orderInfo.order_id;
      }
      
      return processedData;
    } else {
      // Process regular order data (existing logic)
      const shipments = orderInfo?.Shipments?.map(
        shipment => shipment.dataValues
      );
      const items = orderInfo?.OrderItems?.map(item => item.dataValues);
      const paymentInformation = orderInfo?.OrderPayments?.map(item => item.dataValues);
      const simpleItems = items?.filter(i => i.product_type === 'simple');
      const configItems = items?.filter(i => i.product_type === 'configurable');
      const creditMemo = orderInfo?.Creditmemos?.pop();

      const shippingAddress = orderInfo?.OrderAddresses?.reduce(
        (adrs, address) => {
          if (address.dataValues?.address_type === 'shipping') {
            adrs = address.dataValues;
          }
          return adrs;
        },
        {}
      );

      orderInfo.shipmentData = shipments;
      orderInfo.paymentInformation = paymentInformation
      orderInfo.shippingAddress = shippingAddress;
      orderInfo.simpleItems = simpleItems;
      orderInfo.configItems = configItems;
      orderInfo.creditMemo = creditMemo?.dataValues;
      orderInfo.subSales = orderInfo?.SubSalesOrders?.[0]?.dataValues;

      delete orderInfo.Creditmemos;
      delete orderInfo.SubSalesOrders;
      
      return orderInfo;
    }
  } catch (e) {
    global.logError(`getOrder Error  :  incrementId : ${incrementId}, entityId : ${entityId} , orderData : ${JSON.stringify(
      orderData || {}
    )}, ${e.message ? JSON.stringify(e.message) : ''}, ${e}`
    );
  }
};
/**
 * Check if an order is a seller central order based on seller_id and warehouse_id
 * Uses Consul configuration to determine seller central status
 * @param {string} sellerId - The seller ID
 * @param {string} warehouseId - The warehouse ID
 * @returns {boolean} - True if seller central order, false otherwise
 */
const isSellerCentralOrder = (sellerId, warehouseId) => {
  try {
    logger.info(`[SellerCentral] Checking seller central status - sellerId: ${sellerId}, warehouseId: ${warehouseId}`);
    
    const sellerInventoryMapping = global.javaOrderServiceConfig?.seller_inventory_mapping;
    
    if (!sellerInventoryMapping || !Array.isArray(sellerInventoryMapping)) {
      logger.info('[SellerCentral] seller_inventory_mapping not found in Consul config or is not an array');
      return false;
    }
    
    logger.info(`[SellerCentral] Found ${sellerInventoryMapping.length} seller inventory mappings in Consul`);
    
    const matchingMapping = sellerInventoryMapping.find(mapping => {
      const sellerIdMatch = String(mapping.SELLER_ID) === String(sellerId);
      const warehouseIdMatch = String(mapping.warehouse_id) === String(warehouseId);
      return sellerIdMatch && warehouseIdMatch;
    });
    
    if (matchingMapping) {
      const isSellerCentral = matchingMapping.PUSH_TO_SELLER_CENTRAL === true || 
                              matchingMapping.PUSH_TO_SELLER_CENTRAL === 'true';
      logger.info(`[SellerCentral] Matching mapping found - PUSH_TO_SELLER_CENTRAL: ${matchingMapping.PUSH_TO_SELLER_CENTRAL}, isSellerCentral: ${isSellerCentral}`);
      return isSellerCentral;
    }
    
    logger.info(`[SellerCentral] No matching mapping found for sellerId: ${sellerId}, warehouseId: ${warehouseId}`);
    return false;
  } catch (error) {
    logger.error(`[SellerCentral] Error checking seller central status - sellerId: ${sellerId}, warehouseId: ${warehouseId}, error: ${error.message}`);
    return false;
  }
};

/**
 * Publish seller central delivered status update to PubSub
 * This is called asynchronously after the DB update for delivered orders
 * Uses existing orderData and pre-fetched sellerOrders (no DB queries inside this function)
 *
 * Seller / warehouse resolution (per line item):
 * - Primary: seller_id and warehouse from orderData.simpleItems (split_sales_order_item / sales flow).
 * - Fallback: if either is missing, match sku against sellerOrderItems — rows loaded only for
 *   product_type = 'simple' on split_seller_order_item (avoids duplicate configurable rows for same sku).
 *
 * @param {string} incrementId - The order increment ID
 * @param {string} status - The order status (e.g., 'delivered')
 * @param {object} orderData - The order data already fetched (contains simpleItems with seller_id, warehouse_location_id)
 * @param {Array} sellerOrders - Pre-fetched seller orders from split_seller_order table
 * @param {Array} [sellerOrderItems=[]] - Pre-fetched split_seller_order_item rows (simple only), used only when item ids are empty
 */
const publishSellerCentralDeliveredStatus = async (incrementId, status, orderData, sellerOrders, sellerOrderItems = []) => {
  try {
    logger.info(`[SellerCentral] Step 1: Starting seller central status check for order: ${incrementId}, status: ${status}`);
    
    // Step 2: Get simple items from existing orderData (NO QUERY NEEDED!)
    const simpleItems = orderData?.simpleItems || [];
    console.log(" WMS RTO Push : publishSellerCentralDeliveredStatus: simpleItems ", simpleItems);
    logger.info(`[SellerCentral] Step 2: Found ${simpleItems.length} simple items in orderData for order: ${incrementId}`);
    
    if (simpleItems.length === 0) {
      logger.info(`[SellerCentral] No simple items found in orderData for order: ${incrementId} - skipping PubSub publish`);
      return;
    }

    const isEmptySellerId = (v) => v == null || v === '';
    const isEmptyWarehouseId = (v) => v == null || v === '';

    /**
     * Resolves seller_id and warehouse for Seller Central checks.
     * Falls back to split_seller_order_item (simple rows) only when the corresponding field is empty on the item.
     */
    const effectiveSellerAndWarehouse = (item) => {
      let sellerId = item.seller_id;
      let warehouseId = item.warehouse_location_id || item.warehouse_id;
      const needFallback =
        sellerOrderItems.length > 0 &&
        (isEmptySellerId(sellerId) || isEmptyWarehouseId(warehouseId));

      if (!needFallback) {
        return { sellerId, warehouseId };
      }

      const soi = sellerOrderItems.find((row) => String(row.sku) === String(item.sku));
      if (!soi) {
        logger.info(
          `[SellerCentral] Fallback skipped for sku ${item.sku} — no simple split_seller_order_item row matched`
        );
        return { sellerId, warehouseId };
      }

      if (isEmptySellerId(sellerId)) {
        sellerId = soi.seller_id;
        logger.info(
          `[SellerCentral] Fallback seller_id from split_seller_order_item (simple) for sku ${item.sku}: ${sellerId}`
        );
      }
      if (isEmptyWarehouseId(warehouseId)) {
        warehouseId = soi.warehouse_id;
        logger.info(
          `[SellerCentral] Fallback warehouse_id from split_seller_order_item (simple) for sku ${item.sku}: ${warehouseId}`
        );
      }

      return { sellerId, warehouseId };
    };
    
    // Step 3: Check which items are seller central using existing data (warehouse_location_id from orderData)
    logger.info(`[SellerCentral] Step 3: Checking seller central status for each item using Consul config (PUSH_TO_SELLER_CENTRAL flag)`);
    if (sellerOrderItems.length > 0) {
      logger.info(
        `[SellerCentral] Step 3: ${sellerOrderItems.length} simple split_seller_order_item row(s) available as fallback for empty seller_id / warehouse`
      );
    }
    
    const sellerCentralItems = simpleItems.filter(item => {
      const { sellerId, warehouseId } = effectiveSellerAndWarehouse(item);
      const isSellerCentral = isSellerCentralOrder(sellerId, warehouseId);
      logger.info(`[SellerCentral] Item check - sku: ${item.sku}, seller_id: ${sellerId}, warehouse_id: ${warehouseId}, isSellerCentral: ${isSellerCentral}`);
      return isSellerCentral;
    });
    
    logger.info(`[SellerCentral] Step 3 Result: ${sellerCentralItems.length} seller central items out of ${simpleItems.length} total items for order: ${incrementId}`);
    
    if (sellerCentralItems.length === 0) {
      logger.info(`[SellerCentral] No seller central items found for order: ${incrementId} - skipping PubSub publish`);
      return;
    }
    
    // Step 4: Use pre-fetched seller orders (NO DB QUERY - data already fetched in preparePromise)
    logger.info(`[SellerCentral] Step 4: Using pre-fetched seller orders: ${sellerOrders?.length || 0} records`);
    
    if (!sellerOrders || sellerOrders.length === 0) {
      logger.info(`[SellerCentral] No seller orders found - skipping PubSub publish`);
      return;
    }
    
    // Step 5: Build the payload - map seller central items with their seller order increment_id
    logger.info(`[SellerCentral] Step 5: Building payload for PubSub message`);
    
    const payload = [];
    for (const sellerOrder of sellerOrders) {
      // Check if this seller order is seller central
      const isSellerCentral = isSellerCentralOrder(sellerOrder.seller_id, sellerOrder.warehouse_id);
      
      if (isSellerCentral) {
        // Find matching SKUs for this seller order
        const matchingSkus = sellerCentralItems
          .filter(item => {
            const { sellerId } = effectiveSellerAndWarehouse(item);
            return String(sellerId) === String(sellerOrder.seller_id);
          })
          .map(item => item.sku);
        
        logger.info(`[SellerCentral] Seller order: ${sellerOrder.increment_id}, matching SKUs: ${matchingSkus.join(', ')}`);
        
        for (const sku of matchingSkus) {
          payload.push({
            sellerOrderId: sellerOrder.increment_id,
            sku: sku,
            status: status
          });
        }
      }
    }
    
    logger.info(`[SellerCentral] Step 5 Result: Built payload with ${payload.length} items`);
    
    if (payload.length === 0) {
      logger.info(`[SellerCentral] No payload items to publish for order: ${incrementId} - skipping PubSub publish`);
      return;
    }
    
    // Step 6: Prepare and publish to PubSub
    const pubsubMessage = {
      type: 'update',
      payload: payload
    };
    
    logger.info(`[SellerCentral] Step 6: Preparing to publish to topic: ${SELLER_CENTRAL_ORDER_TOPIC}`);
    logger.info(`[SellerCentral] PubSub message payload: ${JSON.stringify(pubsubMessage)}`);
    
    // Publish to PubSub asynchronously (fire and forget)
    PubSubService.publishMessage(SELLER_CENTRAL_ORDER_TOPIC, pubsubMessage)
      .then(messageId => {
        logger.info(`[SellerCentral] Step 7: SUCCESS - Published message to PubSub - messageId: ${messageId}, order: ${incrementId}, topic: ${SELLER_CENTRAL_ORDER_TOPIC}`);
      })
      .catch(error => {
        logger.error(`[SellerCentral] Step 7: FAILED - Error publishing to PubSub - order: ${incrementId}, topic: ${SELLER_CENTRAL_ORDER_TOPIC}, error: ${error.message}`);
      });
    
  } catch (error) {
    logger.error(`[SellerCentral] Error in publishSellerCentralDeliveredStatus - order: ${incrementId}, error: ${error.message}, stack: ${error.stack}`);
  }
};

const preparePromise = (promiseArray, { updateObj, incrementId, orderData }) => {
  if (updateObj.status) {
    const isSplitOrder = incrementId && isSplitOrderPattern(incrementId);
    
    if (isSplitOrder) {
      // For split orders, update SplitSalesOrder table
      const { SplitSalesOrder } = require('../models/seqModels/index');
      const { checkAndUpdateMainOrderStatus } = require('../utils/splitOrderUtils');
      
      const splitOrderUpdatePromise = SplitSalesOrder.update(updateObj, {
        where: { increment_id: incrementId }
      }).then(async (result) => {
        // CRITICAL: This API (/v1/rest/shipment-update) should ONLY update split_sales_order
        // It should NOT update seller orders (split_seller_order)
        // Seller orders should ONLY be updated via /v1/rest/seller/shipment-update API
        console.log(`[preparePromise] Updated split_sales_order ${incrementId} to status ${updateObj.status}`);
        // Sync main order status based on the "slowest" split status (processing/packed/shipped/delivered)
        await checkAndUpdateMainOrderStatus(incrementId);
        return result;
      });
      
      promiseArray.push(splitOrderUpdatePromise);
      
      // Also update OrderGrid if it exists (it might reference the original order)
      promiseArray.push(
        OrderGrid.update(
          { status: updateObj.status },
          {
            where: { increment_id: incrementId }
          }
        ).catch(err => {
          // Ignore if OrderGrid doesn't have the split order record
          console.log('OrderGrid update failed for split order (expected):', err.message);
        })
      );
    } else {
      // For regular orders, update Order table
      promiseArray.push(
        Order.update(updateObj, {
          where: { increment_id: incrementId }
        })
      );
      promiseArray.push(
        OrderGrid.update(
          { status: updateObj.status },
          {
            where: { increment_id: incrementId }
          }
        )
      );
    }
    
    // For delivered status, trigger async seller central PubSub notification
    if (updateObj.status === ORDER_DELIVERED_STATUS_CODE) {
      logger.info(`[SellerCentral] Triggering async seller central PubSub notification for delivered order: ${incrementId}`);
      
      // Fetch seller orders NOW (while DB connection is active) - before async call
      const isSplit = incrementId && isSplitOrderPattern(incrementId);
      // For split orders: use split_entity_id (split_sales_order.entity_id = 50609)
      // For regular orders: use entity_id (sales_order.entity_id)
      const orderId = isSplit ? orderData?.split_entity_id : orderData?.entity_id;
      
      logger.info(`[SellerCentral] Pre-fetching seller orders - isSplit: ${isSplit}, orderId: ${orderId}`);
      
      const sellerOrderQuery = isSplit 
        ? `SELECT increment_id, seller_id, warehouse_id, entity_id FROM split_seller_order WHERE split_order_id = ?`
        : `SELECT increment_id, seller_id, warehouse_id, entity_id FROM split_seller_order WHERE main_order_id = ?`;
      
      // Query runs synchronously here while DB connection is active
      sequelize.query(sellerOrderQuery, {
        replacements: [orderId],
        type: QueryTypes.SELECT
      }).then(async (sellerOrders) => {
        logger.info(`[SellerCentral] Pre-fetched ${sellerOrders?.length || 0} seller orders for order: ${incrementId}`);

        // Load simple line rows only — used as fallback when orderData.simpleItems lack seller_id / warehouse (e.g. main sales items).
        // Excluding configurable rows avoids two rows per sku when both exist on the same seller_order_id.
        let sellerOrderItems = [];
        if (sellerOrders?.length) {
          const sellerOrderEntityIds = sellerOrders.map((so) => so.entity_id).filter((id) => id != null);
          if (sellerOrderEntityIds.length > 0) {
            const placeholders = sellerOrderEntityIds.map(() => '?').join(',');
            const soiQuery = `
              SELECT sku, seller_id, warehouse_id, seller_order_id, product_type
              FROM split_seller_order_item
              WHERE seller_order_id IN (${placeholders})
                AND LOWER(TRIM(COALESCE(product_type, ''))) = 'simple'
            `;
            try {
              sellerOrderItems = await sequelize.query(soiQuery, {
                replacements: sellerOrderEntityIds,
                type: QueryTypes.SELECT,
              });
              logger.info(
                `[SellerCentral] Pre-fetched ${sellerOrderItems.length} split_seller_order_item row(s) (product_type=simple only) for fallback — order: ${incrementId}, seller_order_id IN (${sellerOrderEntityIds.join(', ')})`
              );
            } catch (soiErr) {
              logger.error(
                `[SellerCentral] Failed to pre-fetch split_seller_order_item for fallback — order: ${incrementId}, error: ${soiErr.message}`
              );
            }
          }
        }
        
        // STEP: Update split_seller_order status to 'delivered' for seller central orders BEFORE pub-sub
        if (sellerOrders && sellerOrders.length > 0) {
          const sellerCentralOrderIds = [];
          
          for (const sellerOrder of sellerOrders) {
            // Check if this seller order is seller central
            const isSellerCentral = isSellerCentralOrder(sellerOrder.seller_id, sellerOrder.warehouse_id);
            
            if (isSellerCentral) {
              sellerCentralOrderIds.push(sellerOrder.entity_id);
              logger.info(`[SellerCentral] Seller order ${sellerOrder.increment_id} (entity_id: ${sellerOrder.entity_id}) is seller central - will update status`);
            }
          }
          
          // Update split_seller_order status for all seller central orders
          if (sellerCentralOrderIds.length > 0) {
            try {
              const { SplitSellerOrder } = require('../models/seqModels/index');
              await SplitSellerOrder.update(
                { status: updateObj.status },
                { where: { entity_id: sellerCentralOrderIds } }
              );
              logger.info(`[SellerCentral] Updated split_seller_order status to '${updateObj.status}' for entity_ids: ${sellerCentralOrderIds.join(', ')}`);
            } catch (updateErr) {
              logger.error(`[SellerCentral] Error updating split_seller_order status for order: ${incrementId}, error: ${updateErr.message}`);
            }
          }
        }
        
        // Async publish: uses orderData + sellerOrders; sellerOrderItems only fills missing seller/warehouse on items
        publishSellerCentralDeliveredStatus(incrementId, updateObj.status, orderData, sellerOrders, sellerOrderItems);
      }).catch(err => {
        logger.error(`[SellerCentral] Error pre-fetching seller orders for order: ${incrementId}, error: ${err.message}`);
      });
    }
  }
};

const checkUndeliveredStatus = async (entityId) => {
  const orders = await sequelize.query(
    `SELECT count(1) as cnt FROM sales_order_status_history sh INNER JOIN sales_order sa ON sh.parent_id = sa.entity_id
      WHERE sa.entity_id = ? and sh.comment like 'Order Failed Delivery%'`,
    {
      replacements: [entityId],
      type: QueryTypes.SELECT
    }
  );
  console.log("OMS checkStatusArray response:::::", JSON.stringify(orders));
  if (orders?.[0]?.cnt && orders?.[0]?.cnt > 0) {
   return {undeliveredStatus:false,count:orders?.[0]?.cnt};
  }
   return {undeliveredStatus:true,count:0};
}

const handleShippedStatus = (orderData, promiseArray, incrementId) => {
  // Detect if this is a split order based on increment_id
  const isSplit = incrementId && isSplitOrderPattern(incrementId);
  
  // Get the items from the processed order data (simpleItems + configItems)
  const allItems = [...(orderData?.simpleItems || []), ...(orderData?.configItems || [])];
  
  if (isSplit) {
    // For split orders, we need to determine if this came from SplitSalesOrder or regular Order with split models
    // Check if we have SplitSalesOrderItems (from SplitSalesOrder table) or SplitSellerOrderItems (from Order table)
    const hasSplitSalesItems = orderData?.simpleItems?.some(item => item.split_order_id !== undefined);
    
    if (hasSplitSalesItems) {
      // Update SplitSalesOrderItem for split sales order flow
      const { SplitSalesOrderItem } = require('../models/seqModels/index');
      allItems?.forEach(itemData => {
        promiseArray.push(
          SplitSalesOrderItem.update(
            { qty_shipped: itemData?.qty_ordered },
            { where: { item_id: itemData.item_id } }
          )
        );
      });
    } else {
      // Update SplitSellerOrderItem for regular order with split seller items
      const { SplitSellerOrderItem } = require('../models/seqModels/index');
      allItems?.forEach(itemData => {
        promiseArray.push(
          SplitSellerOrderItem.update(
            { qty_shipped: itemData?.qty_ordered },
            { where: { item_id: itemData.item_id } }
          )
        );
      });
    }
  } else {
    // For regular orders, update OrderItem
    allItems?.forEach(itemData => {
      promiseArray.push(
        OrderItem.update(
          { qty_shipped: itemData?.qty_ordered },
          { where: { item_id: itemData.item_id } }
        )
      );
    });
  }
  
  promiseArray.push(logBrazeCustomEventForShipped({ orderData }));
};

const handleDeliveredStatus = (orderData, promiseArray) => {
  promiseArray.push(logBrazeCustomEventForDelivery({ orderData }));
  adjustDeliveredEvent({ orderData });
  promiseArray.push(earnEventForDeliverySuccess({ orderData }));
};

const handleOrderComment = ({ params, entityId, updateObj, formattedTime, reqStatus, promiseArray }) => {
  if (params.orderComment) {
    promiseArray.push(
      OrderComment.create({
        parent_id: entityId,
        comment: params.orderComment,
        status: updateObj.status,
        entity_name: 'order',
        created_at: formattedTime,
        final_status: reqStatus
      })
    );
  }
};

const handleStatusHistory = (updateObj, entityId, formattedTime, promiseArray) => {
  const toUpdateDate = STATUS_HISTORY_MAP[updateObj.status];
  if (toUpdateDate) {
    const updateStatusObj = { [toUpdateDate]: formattedTime };
    promiseArray.push(updateStatusHistory(entityId, updateStatusObj));
  }
};

const handleSMS = (smsStatus, updateObj, orderData, promiseArray,incrementId) => {
  let finalSms = smsStatus;
  if (updateObj?.smsStatus) {
    finalSms = updateObj.smsStatus;
  }
  promiseArray.push(
    smsObj.sendSMS({
      smsType: finalSms || updateObj.status,
      orderData,
      incrementIdData:incrementId
    })
  );
  return finalSms;
};

const getRequiredStore = (storesData, storeId) => {
  return storesData.find((item) =>
    item.storeId === storeId || item.storeId === storeId.toString() || parseInt(item.storeId) === storeId
  );
};

const getIsUndelivered = (featureBasedFlag, finalSms, undeliveredStatus) => {
  return featureBasedFlag.unDeliveredWAMsg && undeliveredStatus && finalSms === "undelivered";
};

const handleWhatsAppMessage = async (finalSms, orderData, incrementId) => {
  const {undeliveredStatus} = await checkUndeliveredStatus(orderData.entity_id);
  const { baseConfig = {}, config = {} } = global;
  const { featureBasedFlag = {}, whatsappConfig = {} } = baseConfig;
  const isUndelivered = getIsUndelivered(featureBasedFlag, finalSms, undeliveredStatus);

  if (isUndelivered) {
    const { shippingAddress: { telephone, firstname = "" } } = orderData || {};
    const storesData = config.environments?.[0]?.stores || [];
    const storeId = orderData.store_id;
    const requiredStore = getRequiredStore(storesData, storeId);
    const { storeLanguage = "en_US" } = requiredStore || {};
    const reqLanguage = storeLanguage.split("_")[0];
    const templateName = whatsappConfig[`failedDeliveryTemplateName_${reqLanguage}`];
    console.log(`FD WA MSG::: storesData: ${JSON.stringify(storesData)}, storeId: ${storeId}, store: ${storeLanguage}, requiredStore: ${requiredStore}, templateName: ${templateName}`)
    const whatsAppMsg = getWpMessage({
      template: templateName,
      fromPhone: whatsappConfig.fromPhoneNumber,
      phone: telephone.replace(" ", ""),
      is_arabic: reqLanguage === 'ar' ? 1 : 0,
      var_length: 2,
      media_url: "",
      dynamic_path: "",
      var_1: firstname,
      var_2: incrementId,
    });
    console.log("OMS final whatsapp msg ::::::", JSON.stringify(whatsAppMsg))
    await sendMessage(whatsAppMsg);
  }
};

const handleWmsPush = (pushToWms, params, entityId, updateObj, incrementId) => {
  if (pushToWms) {
    // Detect if this is a split order based on increment_id
    const isSplit = incrementId && isSplitOrderPattern(incrementId);
    
    if (isSplit) {
      // For split orders, we need to determine the correct table to update
      // Try to update both SplitSubSalesOrder and SplitSubSellerOrder as fallback
      const { SplitSubSalesOrder, SplitSubSellerOrder } = require('../models/seqModels/index');
      
      // Update SplitSubSalesOrder (for split sales order flow)
      SplitSubSalesOrder.update(
        { extra_1: params.rtoAwb },
        { where: { order_id: entityId } }
      ).catch(() => {
        // If that fails, try SplitSubSellerOrder (for regular order with split seller items)
        SplitSubSellerOrder.update(
          { extra_1: params.rtoAwb },
          { where: { order_id: entityId } }
        );
      });
    } else {
      // For regular orders, update SubSalesOrder
      SubSalesOrder.update(
        { extra_1: params.rtoAwb },
        { where: { order_id: entityId } }
      );
    }
    updateObj.wms_status = WMS_RTO_PUSH_PENDING_STATUS;
  }
};

exports.updateOrder = async params => {
  const {
    incrementId,
    entityId,
    smsStatus,
    orderData,
    pushToWms,
    updateObj,
    emailTemplate,
    reqStatus,
    notificationId
  } = params;

  try {
    global.logInfo('update order start ', incrementId);
    const promiseArray = [];
    const formattedTime = getUtcTIme(params.timestamp);

    if (notificationId === FAILED_DELIVERY || notificationId==='undelivered') {
      const { customer_id, entity_id, OrderItems, simpleItems } = orderData;
      const { undeliveredStatus, count } = await checkUndeliveredStatus(entity_id);
      logBrazecustomEventForFailedDelivery({ data: { increment_id: incrementId, entity_id: entity_id, customer_id: customer_id, orderedItems: OrderItems || simpleItems, count } });
    }

    if (updateObj?.status === ORDER_SHIPPED_STATUS_CODE) {
      handleShippedStatus(orderData, promiseArray, incrementId);
    }

    if (updateObj?.status === ORDER_DELIVERED_STATUS_CODE) {
      handleDeliveredStatus(orderData, promiseArray);
    }

    handleOrderComment({ params, entityId, updateObj, formattedTime, reqStatus, promiseArray });
    handleStatusHistory(updateObj, entityId, formattedTime, promiseArray);

    const finalSms = handleSMS(smsStatus, updateObj, orderData, promiseArray, incrementId);
    console.log("order data:::::", JSON.stringify(orderData))
    await handleWhatsAppMessage(finalSms, orderData, incrementId);

    handleWmsPush(pushToWms, params, entityId, updateObj, incrementId);

    if (emailTemplate) {
      promiseArray.push(emailOBJ.sendEmail({ type: emailTemplate, orderId: entityId, incrementIdData:incrementId  }));
    }

    preparePromise(promiseArray, { updateObj, incrementId, orderData });
    global.logInfo('update order end successfully ', incrementId);
    return await promiseAll(promiseArray);
  } catch (e) {
    global.logInfo(`update order ended with the error , ${incrementId}, ${e.message ? JSON.stringify(e.message) : ''}, ${e}`);
    throw new Error(e);
  }
};

exports.updateOrderStateStatus = async ({ orderId, state, status }) => {
  let success = true;
  let errorMsg = '';
  if (status) {
    const salesOrderUpdate = { status };
    const salesGridUpdate = { status };
    if (state) {
      salesGridUpdate.state = state;
    }
    const promiseArray = [];
    promiseArray.push(
      Order.update(salesOrderUpdate, {
        where: { entity_id: orderId }
      })
    );

    promiseArray.push(
      OrderGrid.update(
        { status },
        {
          where: { entity_id: orderId }
        }
      )
    );

    await Promise.allSettled(promiseArray)
      .then(values => {
        values.forEach(value => {
          if (value.status === 'rejected') {
            success = false;
            errorMsg = value.reason;
            global.logError(value.reason);
          }
        });
        return values;
      })
      .catch(error => {
        global.logError(error);
      });
    return { errorMsg, success };
  }
};

exports.paymentMethodsLabels = {
  cashondelivery: { en: 'Cash on delivery', ar: 'الدفع عند الإستلام' },
  apple_pay: { en: 'Apple Pay', ar: 'أبل باي' },
  md_payfort_cc_vault: {
    en: 'Saved Card Payfort',
    ar: 'بطاقاتي البنكية'
  },
  md_payfort: { en: 'Card Payment', ar: 'ادفع بواسطة البطاقات البنكية' },
  free: { en: 'Styli Credit', ar: 'رصيد ستايلي' },
  tabby_installments: { en: 'Tabby - Installments', ar: 'تابي - تقسيط' },
  tabby_paylater: { en: 'Tabby - Pay Later', ar: 'تابي - أدفع لاحقًا' }
};

exports.getTotalItemCount = async ({ orderId }) => {
  const orderItemData = await OrderItem.findAll({
    where: { order_id: orderId, product_type: 'simple' }
  });
  return orderItemData.reduce((totalCount, item) => {
    totalCount =
      totalCount +
      Number(item.qty_ordered || 0) -
      (Number(item.qty_canceled || 0) + Number(item.qty_refunded || 0));
    return totalCount;
  }, 0);
};

exports.getOrderItems = async ({ orderId, useArchive = false }) => {
  const orderItems = useArchive ?  await OrderItemArchive.findAll({
    where: { order_id: orderId },
    raw: true
  }) : await OrderItem.findAll({
    where: { order_id: orderId },
    raw: true
  }) 
  if (process.env?.REGION?.toUpperCase() === 'IN') {
    return (orderItems || [])
      .filter(i => i.product_type === 'simple')
      .map(el => el.item_id);
  }
  return (orderItems || [])
    .filter(i => i.product_type === 'configurable')
    .map(el => el.item_id);
};

exports.getOrderData = async ({ orderId, includeSubOrder = false , useArchive=false}) => {
  const include = [];
  include.push({
    model: useArchive ?  OrderAddressArchive : OrderAddress,
    as: 'OrderAddresses',
    where: { address_type: 'shipping' }
  });
  include.push({ model: useArchive ?  OrderPaymentArchive : OrderPayment});
  if (includeSubOrder) include.push({ model: useArchive ?  SubSalesOrderArchive : SubSalesOrder });
  const orderData = useArchive ? await OrderArchive.findOne({
    where: { entity_id: orderId },
    include,
    raw: true
  }) : await Order.findOne({
    where: { entity_id: orderId },
    include,
    raw: true
  });
  return orderData;
};

exports.getOrderStatus = async () => {
  return await sequelize.query(
    'SELECT soss.*,sos.label FROM sales_order_status_state soss LEFT JOIN sales_order_status sos ON (sos.status=soss.status)',
    {
      type: QueryTypes.SELECT
    }
  );
};

exports.getOrderStatusDetails = async statusCode => {
  return await sequelize.query(
    'SELECT soss.*,sos.label FROM sales_order_status_state soss LEFT JOIN sales_order_status sos ON (sos.status=soss.status) WHERE soss.status = ? LIMIT 1',
    {
      replacements: [statusCode],
      type: QueryTypes.SELECT
    }
  );
};
exports.updateOrderStatusDetail = async updateObj => {
  const {
    statusCode,
    isDefault,
    label,
    visibleOnFront,
    step,
    colorState
  } = updateObj;
  await OrderStatus.update(
    {
      label: label
    },
    {
      where: { status: statusCode }
    }
  );
  await OrderStatusState.update(
    {
      is_default: isDefault,
      visible_on_front: visibleOnFront,
      step: step,
      color_state: colorState
    },
    {
      where: { status: statusCode }
    }
  );
  return true;
};

/**
 * Validate input parameters for shipment SKU validation
 * @param {string} orderCode - The order increment ID
 * @param {Array} packboxDetailsList - Array of packbox details
 * @returns {Object|null} - Error object if invalid, null if valid
 */
const validateShipmentInputs = (orderCode, packboxDetailsList) => {
  if (!orderCode) {
    return { isValid: false, error: 'Order code is required' };
  }

  const condition = !packboxDetailsList || !Array.isArray(packboxDetailsList) || packboxDetailsList.length === 0;

  if (condition) {
    return { isValid: false, error: 'Packbox details list is required and must not be empty' };
  }
  return null;
};

/**
 * Extract SKUs from split order data
 * @param {Object} orderData - Order data object
 * @returns {Array} - Array of SKU strings
 */
const extractSkusFromSplitOrder = (orderData) => {
  const simpleItems = orderData.simpleItems || [];
  const configItems = orderData.configItems || [];
  return [...simpleItems, ...configItems].map(item => item.sku).filter(Boolean);
};

/**
 * Extract SKUs from regular order data
 * @param {Object} orderData - Order data object
 * @returns {Array} - Array of SKU strings
 */
const extractSkusFromRegularOrder = (orderData) => {
  const simpleItems = orderData.simpleItems || [];
  const configItems = orderData.configItems || [];
  
  if (simpleItems.length > 0 || configItems.length > 0) {
    return [...simpleItems, ...configItems].map(item => item.sku).filter(Boolean);
  }
  
  const orderItems = orderData.OrderItems || [];
  return orderItems.map(item => {
    return item.dataValues ? item.dataValues.sku : item.sku;
  }).filter(Boolean);
};

/**
 * Extract SKUs from order data (handles both split and regular orders)
 * @param {string} orderCode - The order increment ID
 * @param {Object} orderData - Order data object
 * @returns {Array} - Array of normalized SKU strings
 */
const extractSkusFromOrder = (orderCode, orderData) => {
  const isSplitOrder = isSplitOrderPattern(orderCode);
  const orderSkus = isSplitOrder 
    ? extractSkusFromSplitOrder(orderData)
    : extractSkusFromRegularOrder(orderData);
  
  return orderSkus.map(sku => String(sku));
};

/**
 * Extract SKUs from packbox details list
 * @param {Array} packboxDetailsList - Array of packbox details
 * @returns {Array} - Array of SKU strings
 */
const extractSkusFromPackboxDetails = (packboxDetailsList) => {
  const requestedSkus = [];
  
  packboxDetailsList.forEach(packbox => {
    if (!packbox.skuQuantityDataList || !Array.isArray(packbox.skuQuantityDataList)) {
      return;
    }
    
    packbox.skuQuantityDataList.forEach(skuData => {
      if (skuData.globalSkuId) {
        requestedSkus.push(String(skuData.globalSkuId));
      }
      if (skuData.clientSkuId) {
        requestedSkus.push(String(skuData.clientSkuId));
      }
    });
  });
  
  return [...new Set(requestedSkus)]; // Deduplicate
};

/**
 * Validate that requested SKUs exist in order SKUs
 * @param {Array} requestedSkus - Array of requested SKU strings
 * @param {Array} orderSkus - Array of order SKU strings
 * @param {string} orderCode - The order increment ID
 * @returns {Object|null} - Error object if invalid, null if valid
 */
const validateSkusMatch = (requestedSkus, orderSkus, orderCode) => {
  const invalidSkus = requestedSkus.filter(sku => !orderSkus.includes(sku));
  
  if (invalidSkus.length > 0) {
    return {
      isValid: false,
      error: `Invalid SKU(s) found: ${invalidSkus.join(', ')}. These SKUs do not exist in order ${orderCode}`,
      invalidSkus
    };
  }
  
  return null;
};

/**
 * Validate SKUs in shipment creation request against order items
 * @param {string} orderCode - The order increment ID
 * @param {Array} packboxDetailsList - Array of packbox details containing SKU information
 * @returns {Object} - Validation result with isValid flag and error message if invalid
 */
exports.validateShipmentSkus = async (orderCode, packboxDetailsList) => {
  try {
    // Validate inputs
    const inputError = validateShipmentInputs(orderCode, packboxDetailsList);
    if (inputError) return inputError;

    // Get order with items
    const orderData = await exports.getOrder({ incrementId: orderCode, inclSubSales: false });
    if (!orderData) {
      return { isValid: false, error: `Order not found for orderCode: ${orderCode}` };
    }

    // Extract SKUs from order
    const orderSkus = extractSkusFromOrder(orderCode, orderData);
    if (orderSkus.length === 0) {
      return { isValid: false, error: `No SKUs found in order: ${orderCode}` };
    }

    // Extract SKUs from packbox details
    const requestedSkus = extractSkusFromPackboxDetails(packboxDetailsList);
    if (requestedSkus.length === 0) {
      return { isValid: false, error: 'No SKUs found in packbox details list' };
    }

    // Validate SKUs match
    const validationError = validateSkusMatch(requestedSkus, orderSkus, orderCode);
    if (validationError) return validationError;

    return { isValid: true };
  } catch (error) {
    global.logError(`Error validating shipment SKUs for order ${orderCode}:`, error);
    return { isValid: false, error: `Error validating SKUs: ${error.message}` };
  }
};

exports.getSubSalesOrderItem = async (orderId, itemId) => {
  const subSalesOrderItem = await SubSalesOrderItem.findOne({
    where: {
      parent_order_id: orderId,
      main_item_id: itemId,
      is_gift_voucher: true
    },
    attributes: ['sub_item_id', 'discount']
  });
  if (subSalesOrderItem) {
    return JSON.parse(JSON.stringify(subSalesOrderItem));
  } else {
    return null;
  }
};

exports.updateSubsalesOrderData = async (payload, data) => {
  // For split orders, data.subSales is from split_sub_sales_order (different table).
  // We must update sub_sales_order by parent order_id (data.entity_id).
  const isSplitOrder = Object.prototype.hasOwnProperty.call(data, 'split_entity_id');
  let whereClause;

  if (isSplitOrder) {
    if (data.entity_id == null || data.entity_id === undefined) {
      console.log('updateSubsalesOrderData: split order but data.entity_id missing', { entity_id: data.entity_id, split_entity_id: data.split_entity_id });
      return;
    }
    whereClause = { order_id: data.entity_id };
  } else {
    if (!data.subSales?.id) {
      console.log('updateSubsalesOrderData: regular order but data.subSales.id missing', { subSales: data.subSales });
      return;
    }
    whereClause = { id: data.subSales.id };
  }

  const subSalesData = await SubSalesOrder.findOne({
    where: whereClause,
    raw: true
  });

  if (subSalesData && !['1', 1].includes(subSalesData.shukran_pr_successful)) {
    await SubSalesOrder.update(
      {
        shukran_pr_successful: 1,
        pr_updated_at: moment.utc().format(),
        shukran_pr_transaction_net_total: payload.TransactionNetTotal
      },
      { where: whereClause }
    );
  }
};

exports.findFailedPrOrders = async (prCallFailedTimeInHours) => {
  const twelveHoursAgo = new Date(Date.now() - prCallFailedTimeInHours * 60 * 60 * 1000);
  try {
    const failedPrOrdersList = await getFailedSubSalesOrders(twelveHoursAgo);

    if (failedPrOrdersList.length === 0) {
      console.log(`No failed SubSalesOrders found in the last ${prCallFailedTimeInHours} hours.`);
      return [];
    }

    const failedOrderIds = failedPrOrdersList.map(subSalesOrder => subSalesOrder.order_id);
    const failedPrIncrementIds = await getPROrderIncrementIds(failedOrderIds);
    if (failedPrIncrementIds.length === 0) {
      console.log(`No failed SalesOrders found in the last ${prCallFailedTimeInHours} hours, with order status delivered.`);
      return [];
    }

    return failedPrIncrementIds;
  } catch (error) {
    console.error("Error fetching failed PR orders:", error);
    return [];
  }
};

const getFailedSubSalesOrders = async twelveHoursAgo => {
  console.log(`In getFailedSubSalesOrders : twelveHoursAgo ${twelveHoursAgo}`);
  const formattedDate = moment(twelveHoursAgo).format("YYYY-MM-DD HH:mm:ss");
  console.log(`In getFailedSubSalesOrders : twelveHoursAgo ${formattedDate}`);
  const subQuery = {
    [Op.in]: sequelize.literal(`
      (SELECT parent_id FROM sales_order_status_history WHERE status = 'delivered' AND created_at > '${formattedDate}')
    `)
  };
  return await SubSalesOrder.findAll({
    where: {
      order_id: subQuery,
      shukran_linked: true,
      shukran_card_number: {
        [Op.and]: [{ [Op.ne]: null }, { [Op.ne]: '' }]
      },
      customer_profile_id: {
        [Op.and]: [{ [Op.ne]: null }, { [Op.ne]: '' }]
      },
      [Op.or]: [{ shukran_pr_successful: 0 }, { shukran_pr_successful: null }]
    },
    attributes: ['order_id']
  });
};


exports.findFailedRTOrders = async (rtCallFailedTimeInHours) => {
  const twelveHoursAgo = new Date(Date.now() - rtCallFailedTimeInHours * 60 * 60 * 1000);
  try {
    const failedRTOrdersList = await getFailedRmaRequest(twelveHoursAgo);

    if (failedRTOrdersList.length === 0) {
      console.log(`No failed RmaRequest found in the last ${rtCallFailedTimeInHours} hours.`);
      return [];
    }

    const failedOrderIds = failedRTOrdersList.map(rmaRequest => rmaRequest.order_id);
    console.log('In findFailedRTOrders : failedOrderIds: ', failedOrderIds);
    const RTSubSalesOrderIds = await getRTSubSalesOrderIds(failedOrderIds);
    console.log('In findFailedRTOrders : RTSubSalesOrderIds: ', RTSubSalesOrderIds);
    const failedRTIncrementIds = await getRTOrderIncrementIds(RTSubSalesOrderIds);

    return failedRTIncrementIds;
  } catch (error) {
    console.error("Error fetching failed RT orders:", error);
    return [];
  }
};

const getFailedRmaRequest = async (twelveHoursAgo) => {
  return await RmaRequest.findAll({
    where: {
      [Op.or]: [
        { shukran_rt_successful: 0 },
        { shukran_rt_successful: null }
      ],
      modified_at: { [Op.gte]: twelveHoursAgo },
      status: { [Op.in]: [7, 27, 43, 15, 19] }
    },
    attributes: ["order_id"]
  });
};

const getPROrderIncrementIds = async (failedOrderIds) => {
  if (failedOrderIds.length === 0) {
    console.log("No failed order IDs to process.");
    return [];
  }
  const orderList = await Order.findAll({
    where: {
      entity_id: { [Op.in]: failedOrderIds },
      status: 'delivered'
    },
    attributes: ["increment_id"]
  });

  return orderList.map(order => order.increment_id);
};

const getRTSubSalesOrderIds = async (failedOrderIds) => {
  if (!Array.isArray(failedOrderIds)) {
    console.error("In getRTSubSalesOrderIds: failed Order Ids must be an array.");
    return [];
  }
  if (failedOrderIds.length === 0) {
    console.log("In getRTSubSalesOrderIds: No failed order IDs to process to filter bases on sub sales order.");
    return [];
  }

  const orderIds = await SubSalesOrder.findAll({
    where: {
      order_id: { [Op.in]: failedOrderIds },
      shukran_linked: true,
      shukran_pr_successful: 1,
      total_shukran_coins_earned: { [Op.gt]: 0 },
      [Op.and]: [
        { shukran_card_number: { [Op.ne]: null, [Op.ne]: "" } },
        { customer_profile_id: { [Op.ne]: null, [Op.ne]: "" } }
      ]
    },
    attributes: ["order_id"]
  });
  return orderIds.map(subSalesOrder => subSalesOrder.order_id);
}

const getRTOrderIncrementIds = async (failedOrderIds) => {
  try {
    if (!Array.isArray(failedOrderIds)) {
      console.error("Invalid input: failedOrderIds must be an array.");
      return [];
    }
    if (failedOrderIds.length === 0) {
      console.log("No failed order IDs to process.");
      return [];
    }
    const orderList = await Order.findAll({
      where: {
        entity_id: { [Op.in]: failedOrderIds }
      },
      attributes: ["increment_id"]
    });

    return orderList.map(order => order.increment_id);
  } catch (error) {
    // Handle any runtime errors
    console.error("Error while fetching order increment IDs:", error.message);
    return [];
  }
};