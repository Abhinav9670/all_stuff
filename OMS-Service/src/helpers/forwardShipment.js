/* eslint-disable max-lines-per-function */
const orderObj = require('./order');
const Models = require('../models/seqModels/index');
const sequelize = require('sequelize');
const { QueryTypes } = require('sequelize');
const { getCurrentTimestamp } = require('./utilities');
const {
  // ORDER_SHIPPED_CODE,
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
  ORDER_STATE_CANCELLED,
  ORDER_TRACK_HISTORY,
  ORDER_STATE_CLOSED,
  CASHONDELIVERY

} = require('../constants/order');

const {
  // ORDER_SHIPPED_CODE,
  OUT_FOR_DELIVERY_SHIPMENT_CODE,
  OUT_FOR_DELIVERY_SHIPMENT_STATUS_CODE,
  OUT_FOR_DELIVERY_SHIPMENT_STATUS_MESSAGE,
  RTO_DELIVERED_SHIPMENT_CODE,
  RTO_DELIVERED_SHIPMENT_STATUS_CODE,
  RTO_DELIVERED_SHIPMENT_STATUS_MESSAGE,
  DELIVERED_SHIPMENT_CODE,
  DELIVERED_SHIPMENT_STATUS_CODE,
  DELIVERED_SHIPMENT_STATUS_MESSAGE,
  REFUNDED_SHIPMENT_CODE,
  REFUNDED_SHIPMENT_STATUS_CODE,
  REFUNDED_SHIPMENT_STATUS_MESSAGE,
  RTO_INITIATED_SHIPMENT_CODE,
  RTO_INITIATED_SHIPMENT_STATUS_CODE,
  RTO_INITIATED_SHIPMENT_STATUS_MESSAGE,
  PICKED_UP_SHIPMENT_CODE,
  PICKED_UP_SHIPMENT_STATUS_CODE,
  PICKED_UP_SHIPMENT_STATUS_MESSAGE,
  RECEIVED_AT_WAREHOUSE_SHIPMENT_CODE,
  RECEIVED_AT_WAREHOUSE_SHIPMENT_STATUS_CODE,
  RECEIVED_AT_WAREHOUSE_SHIPMENT_STATUS_MESSAGE
} = require('../constants/orderStatusCode');

const { updateFraudulent } = require('./fraudulentUpdate');
const {
  createCodRtoCreditMemo,
  callShukranLockAndUnlock,
  callRtoRefund,
  hasExistingRefund
} = require('./refund');
const axios = require('axios');
const { shukranTransactionCreate } = require('../shukran/action');
const { PubSubService } = require('../pubsub/services');
const { isSplitOrderPattern } = require('../utils/splitOrderUtils');
const { SHIPPED, UNDELIVERED, RTO_INITIATED, LOST_DAMAGED_FORWARD_COD, LOST_DAMAGED_FORWARD_PREPAID } = require('../constants/smsTemplateConstants');

function getOrderDetailsByStatus(status) {
  switch (status) {
    case OUT_FOR_DELIVERY_SHIPMENT_STATUS_CODE:
      return {
        code: OUT_FOR_DELIVERY_SHIPMENT_CODE,
        message: OUT_FOR_DELIVERY_SHIPMENT_STATUS_MESSAGE
      };

    case DELIVERED_SHIPMENT_STATUS_CODE:
      return {
        code: DELIVERED_SHIPMENT_CODE,
        message: DELIVERED_SHIPMENT_STATUS_MESSAGE
      };

    case RTO_DELIVERED_SHIPMENT_STATUS_CODE:
      return {
        code: RTO_DELIVERED_SHIPMENT_CODE,
        message: RTO_DELIVERED_SHIPMENT_STATUS_MESSAGE
      };

    case RTO_INITIATED_SHIPMENT_STATUS_CODE:
      return {
        code: RTO_INITIATED_SHIPMENT_CODE,
        message: RTO_INITIATED_SHIPMENT_STATUS_MESSAGE
      };

    case REFUNDED_SHIPMENT_STATUS_CODE:
      return {
        code: REFUNDED_SHIPMENT_CODE,
        message: REFUNDED_SHIPMENT_STATUS_MESSAGE
      };

    case PICKED_UP_SHIPMENT_STATUS_CODE:
      return {
        code: PICKED_UP_SHIPMENT_CODE,
        message: PICKED_UP_SHIPMENT_STATUS_MESSAGE
      };

    case RECEIVED_AT_WAREHOUSE_SHIPMENT_STATUS_CODE:
      return {
        code: RECEIVED_AT_WAREHOUSE_SHIPMENT_CODE,
        message: RECEIVED_AT_WAREHOUSE_SHIPMENT_STATUS_MESSAGE
      };

    default:
      return null; // or throw an error if invalid

  }
}


const handleSwitchCases = (
  switchCaseVars,
  notificationId,
  status,
  ndrStatusDesc,
  timestamp,
  paymentMethod
) => {
  try {
    global.logInfo('in handleSwitchCases');
    switchCaseVars.timestamp = timestamp;
    switch (notificationId) {
      case 2:
        if (status === ORDER_PACKED) {
          switchCaseVars.toUpdateState = ORDER_STATE_COMPLETE;
          switchCaseVars.toUpdateStatus = ORDER_SHIPPED_STATUS_CODE;
          switchCaseVars.orderComment = 'Order has been Shipped.';
          switchCaseVars.emailTemplate = SHIPPED;
        }
        // FBS Flag for PICKED_UP
        switchCaseVars.checkFBS = true;
        switchCaseVars.fbsStatus = 'shipped';
        break;
      case OUT_FOR_DELIVERY:
        switchCaseVars.smsStatus = OUT_FOR_DELIVERY;
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
          switchCaseVars.toUpdateStatus = FAILED_DELIVERY_STATUS_CODE;
          switchCaseVars.orderComment = `Order Failed Delivery : ${ndrStatusDesc}`;
          switchCaseVars.smsStatus = UNDELIVERED;
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
        // FBS Flag for DELIVERED
        switchCaseVars.checkFBS = true;
        switchCaseVars.fbsStatus = 'delivered';
        break;
      case ORDER_RTO:
        switchCaseVars.toUpdateState = ORDER_STATE_CANCELED;
        switchCaseVars.toUpdateStatus = ORDER_RTO_STATUS_CODE;
        switchCaseVars.orderComment = `Order returned to warehouse with AWB : ${switchCaseVars.rtoAwb || switchCaseVars.waybill
          }`;
        switchCaseVars.pushToWms = true;
        switchCaseVars.triggerRtoRefund = true;
        // FBS Flag for RTO
       
        break;
      case ORDER_RTO_INITIATED:
        switchCaseVars.toUpdateState = ORDER_STATE_CANCELED;
        switchCaseVars.toUpdateStatus = ORDER_RTO_INITIATED_CODE;
        switchCaseVars.orderComment = 'RTO is initiated for the order.';
        switchCaseVars.callFraudelent = true;
        switchCaseVars.triggerRtoRefund = true;
          // FBS Flag for RTO
          switchCaseVars.checkFBS = true;
          switchCaseVars.fbsStatus = 'rto';
            switchCaseVars.smsStatus = RTO_INITIATED;
        break;
      case ORDER_LOST:
        switchCaseVars.toUpdateState = ORDER_STATE_LOST;
        switchCaseVars.toUpdateStatus = ORDER_LOST_STATUS_CODE;
        switchCaseVars.orderComment = 'Order is Lost';
        switchCaseVars.callFraudelent = false;
        switchCaseVars.smsStatus = paymentMethod === CASHONDELIVERY ? LOST_DAMAGED_FORWARD_COD : LOST_DAMAGED_FORWARD_PREPAID;
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

exports.sendMessage = async reqData => {
  try {
    const endpoint = process.env.FRESH_CHAT_WHATSAPP;
    const token = process.env.FRESH_CHAT_WHATSAPP_TOKEN;
    console.log('freshchat call info', endpoint, token);
    if (process.env.NODE_ENV !== 'test') {
      const res = await axios.post(endpoint, reqData.requestBody, {
        headers: { Authorization: `Bearer ${token}` }
      });
      console.log('OMS Whatsapp message response::::', JSON.stringify(res));
    }
  } catch (e) {
    console.log('OMS api error freshchat::::', JSON.stringify(e));
  }
};

/**
 * Calculate returned items and their Shukran coins for return status 15 or 19
 * @param {Object} params - Parameters object
 * @param {string} params.requestId - RMA request_id
 * @param {string} params.otherShipmentIncrementId - Other shipment increment_id
 * @param {number} params.returnStatus - Return status code
 * @param {string} params.rmaIncId - RMA increment_id
 * @returns {Promise<Object>} - Object containing orderItemIds and totalReturnedShukranCoins
 */
const calculateReturnedItemsShukranCoins = async ({ requestId, otherShipmentIncrementId, returnStatus, rmaIncId }) => {
  const logPrefix = '[shukranSplitOrderHandling]';
  console.log(`${logPrefix} calculateReturnedItemsShukranCoins: Starting calculation`, {
    requestId,
    otherShipmentIncrementId,
    returnStatus,
    rmaIncId
  });
  
  try {
    // Step 15.1: Get order_item_ids from amasty_rma_request_item
    console.log(`${logPrefix} calculateReturnedItemsShukranCoins: Step 15.1 - Querying amasty_rma_request_item for order_item_ids`, {
      request_id: requestId
    });
    
    let rmaRequestItemResult;
    try {
      const rmaRequestItemQuery = `
        SELECT order_item_id
        FROM amasty_rma_request_item 
        WHERE request_id = :requestId
      `;
      
      rmaRequestItemResult = await Models.sequelize.query(rmaRequestItemQuery, {
        replacements: { requestId },
        type: QueryTypes.SELECT
      });
      
      console.log(`${logPrefix} calculateReturnedItemsShukranCoins: Step 15.1 - RMA request item query result`, {
        request_id: requestId,
        resultCount: rmaRequestItemResult?.length || 0,
        order_item_ids: rmaRequestItemResult?.map(r => r.order_item_id) || []
      });
    } catch (error) {
      console.error(`${logPrefix} calculateReturnedItemsShukranCoins: Step 15.1 - Error querying amasty_rma_request_item`, {
        request_id: requestId,
        error: error.message,
        stack: error.stack
      });
      global.logError('Error in calculateReturnedItemsShukranCoins Step 15.1', error);
      return { orderItemIds: [], totalReturnedShukranCoins: 0 };
    }
    
    if (!rmaRequestItemResult || rmaRequestItemResult.length === 0) {
      console.log(`${logPrefix} calculateReturnedItemsShukranCoins: No RMA request items found`, {
        request_id: requestId
      });
      return { orderItemIds: [], totalReturnedShukranCoins: 0 };
    }
    
    const orderItemIds = rmaRequestItemResult.map(r => r.order_item_id).filter(id => id != null && id !== undefined);
    console.log(`${logPrefix} calculateReturnedItemsShukranCoins: Step 15.2 - Extracted order_item_ids`, {
      order_item_ids: orderItemIds,
      count: orderItemIds.length
    });
    
    // Validate orderItemIds before querying
    if (!orderItemIds || orderItemIds.length === 0) {
      console.log(`${logPrefix} calculateReturnedItemsShukranCoins: No valid order_item_ids found after filtering`, {
        request_id: requestId
      });
      return { orderItemIds: [], totalReturnedShukranCoins: 0 };
    }
    
    // Step 15.2: Get shukran_coins_earned from sales_order_item for these order_item_ids
    console.log(`${logPrefix} calculateReturnedItemsShukranCoins: Step 15.2 - Querying sales_order_item for shukran_coins_earned`, {
      order_item_ids: orderItemIds,
      orderItemIdsCount: orderItemIds.length
    });
    
    let salesOrderItemResult;
    let childItemIds = []; // Declare outside try block for use in Step 15.3
    try {
      // Add timeout and validation
      if (orderItemIds.length > 1000) {
        console.warn(`${logPrefix} calculateReturnedItemsShukranCoins: Large number of order_item_ids (${orderItemIds.length}), this may cause performance issues`);
      }
      
      // IMPORTANT: Match amasty_rma_request_item.order_item_id against parent_item_id in sales_order_item
      // This finds all child items that have the returned item as their parent
      const salesOrderItemQuery = `
        SELECT item_id, shukran_coins_earned, product_type, parent_item_id
        FROM sales_order_item 
        WHERE parent_item_id IN (:orderItemIds)
          AND product_type = 'simple'
      `;
      
      console.log(`${logPrefix} calculateReturnedItemsShukranCoins: Step 15.2 - Executing query with ${orderItemIds.length} order_item_ids (matching against parent_item_id in sales_order_item)`);
      
      salesOrderItemResult = await Models.sequelize.query(salesOrderItemQuery, {
        replacements: { orderItemIds },
        type: QueryTypes.SELECT
      });
      
      console.log(`${logPrefix} calculateReturnedItemsShukranCoins: Step 15.2 - Query completed successfully`);
      
      // Extract the item_ids of child items found (these are the items to exclude)
      childItemIds = salesOrderItemResult?.map(item => item.item_id).filter(id => id != null && id !== undefined).map(id => Number(id)).filter(id => !isNaN(id)) || [];
      
      console.log(`${logPrefix} calculateReturnedItemsShukranCoins: Step 15.2 - Sales order item query result`, {
        parent_order_item_ids_from_rma: orderItemIds,
        child_item_ids_found: childItemIds,
        resultCount: salesOrderItemResult?.length || 0,
        items: salesOrderItemResult?.map(item => ({
          item_id: item.item_id,
          parent_item_id: item.parent_item_id,
          shukran_coins_earned: item.shukran_coins_earned,
          product_type: item.product_type
        })) || []
      });
    } catch (error) {
      console.error(`${logPrefix} calculateReturnedItemsShukranCoins: Step 15.2 - Error querying sales_order_item`, {
        order_item_ids: orderItemIds,
        orderItemIdsCount: orderItemIds.length,
        error: error.message,
        stack: error.stack
      });
      global.logError('Error in calculateReturnedItemsShukranCoins Step 15.2', error);
      // Return empty result if query fails
      return { orderItemIds: [], totalReturnedShukranCoins: 0 };
    }
    
    // Step 15.3: Sum all shukran_coins_earned
    let totalReturnedShukranCoins = 0;
    try {
      if (salesOrderItemResult && salesOrderItemResult.length > 0) {
        totalReturnedShukranCoins = salesOrderItemResult.reduce((sum, item) => {
          const coins = parseFloat(item.shukran_coins_earned) || 0;
          return sum + coins;
        }, 0);
      }
      
      // Use child item IDs (found items) instead of parent order_item_ids from RMA
      // childItemIds was extracted in Step 15.2
      const finalOrderItemIds = childItemIds.length > 0 ? childItemIds : [];
      
      console.log(`${logPrefix} calculateReturnedItemsShukranCoins: Step 15.3 - Total returned shukran coins calculated`, {
        totalReturnedShukranCoins: totalReturnedShukranCoins,
        returnedItemsCount: salesOrderItemResult?.length || 0,
        parent_order_item_ids_from_rma: orderItemIds,
        child_item_ids_to_exclude: finalOrderItemIds
      });
      
      console.log(`${logPrefix} calculateReturnedItemsShukranCoins: Total shukran coins to be deducted for returned items: ${totalReturnedShukranCoins}`);
      
      return {
        orderItemIds: finalOrderItemIds,
        totalReturnedShukranCoins
      };
    } catch (error) {
      console.error(`${logPrefix} calculateReturnedItemsShukranCoins: Step 15.3 - Error calculating total returned shukran coins`, {
        error: error.message,
        stack: error.stack
      });
      global.logError('Error in calculateReturnedItemsShukranCoins Step 15.3', error);
      // Return empty result if calculation fails
      return { orderItemIds: [], totalReturnedShukranCoins: 0 };
    }
  } catch (error) {
    console.error(`${logPrefix} calculateReturnedItemsShukranCoins: Unexpected error in function`, {
      requestId,
      otherShipmentIncrementId,
      returnStatus,
      rmaIncId,
      error: error.message,
      stack: error.stack
    });
    global.logError('Unexpected error in calculateReturnedItemsShukranCoins', error);
    // Return empty result on any unexpected error
    return { orderItemIds: [], totalReturnedShukranCoins: 0 };
  }
};

/**
 * Get parent order increment_id from sales_order table
 * @param {Object} params - Parameters object
 * @param {number} params.orderId - order_id from split_sales_order
 * @param {number} params.fallbackOrderId - Fallback order_id to try if first query fails
 * @returns {Promise<string|null>} - Parent increment_id or null if not found
 */
const getParentOrderIncrementId = async ({ orderId, fallbackOrderId = null }) => {
  const logPrefix = '[shukranSplitOrderHandling]';
  console.log(`${logPrefix} getParentOrderIncrementId: Fetching parent order increment_id`, {
    orderId,
    fallbackOrderId
  });
  
  const parentOrderQuery = `
    SELECT increment_id, entity_id
    FROM sales_order 
    WHERE entity_id = :orderId
    LIMIT 1
  `;
  
  let parentOrderResult = await Models.sequelize.query(parentOrderQuery, {
    replacements: { orderId },
    type: QueryTypes.SELECT
  });
  
  console.log(`${logPrefix} getParentOrderIncrementId: Parent order query result (first attempt)`, {
    order_id: orderId,
    resultCount: parentOrderResult?.length || 0,
    result: parentOrderResult
  });
  
  if (!parentOrderResult || parentOrderResult.length === 0) {
    if (fallbackOrderId) {
      console.log(`${logPrefix} getParentOrderIncrementId: Trying fallback order_id`, {
        originalOrderId: orderId,
        fallbackOrderId
      });
      
      parentOrderResult = await Models.sequelize.query(parentOrderQuery, {
        replacements: { orderId: fallbackOrderId },
        type: QueryTypes.SELECT
      });
      
      console.log(`${logPrefix} getParentOrderIncrementId: Parent order query result (fallback attempt)`, {
        order_id: fallbackOrderId,
        resultCount: parentOrderResult?.length || 0,
        result: parentOrderResult
      });
    }
    
    if (!parentOrderResult || parentOrderResult.length === 0) {
      console.log(`${logPrefix} getParentOrderIncrementId: No parent order found in sales_order`, {
        orderId,
        fallbackOrderId
      });
      return null;
    }
  }
  
  const parentIncrementId = parentOrderResult[0].increment_id;
  console.log(`${logPrefix} getParentOrderIncrementId: Parent increment_id retrieved`, {
    orderId,
    fallbackOrderId,
    parentIncrementId
  });
  
  return parentIncrementId;
};

/**
 * Handle Shukran PR call for split orders
 * @param {Object} params - Parameters object
 * @param {string} params.incrementId - Current shipment increment_id
 * @param {boolean} params.shukranOnShipmentLevel - Feature flag value
 * @param {Object} params.resStatus - Response status
 * @param {Object} params.resError - Response error
 * @returns {Promise<Object>} - Status and error message
 */
const handleShukranSplitOrderLogic = async ({ incrementId, shukranOnShipmentLevel, resStatus, resError }) => {
  const logPrefix = '[shukranSplitOrderHandling]';
  
  console.log(`${logPrefix} handleShukranSplitOrderLogic: Starting split order handling`, {
    incrementId,
    shukranOnShipmentLevel
  });
  
  // Only called when isSplitOrder && shukranOnShipmentLevel (checked in updateFwdShipment)
  console.log(`${logPrefix} Step 4: Handling split order (Shukran on shipment level)`, { incrementId });
  
  // Fetch all split shipments for the parent order
  console.log(`${logPrefix} Step 6: Fetching all split shipments for parent order`, { currentIncrementId: incrementId });
  const allSplitShipments = await fetchAllSplitShipmentsForParentOrder(incrementId);
  console.log(`${logPrefix} Step 6: Fetch operation completed`, { 
    currentIncrementId: incrementId, 
    totalShipments: allSplitShipments?.length || 0,
    shipments: allSplitShipments 
  });
  console.log(`${logPrefix} All split shipments fetched`, { 
    currentIncrementId: incrementId, 
    totalShipments: allSplitShipments?.length || 0,
    shipments: allSplitShipments 
  });
  
  console.log(`${logPrefix} Step 7: Checking shipment count`, { 
    currentIncrementId: incrementId,
    shipmentCount: allSplitShipments?.length || 0
  });
  
  if (!allSplitShipments || allSplitShipments.length <= 1) {
    console.log(`${logPrefix} Step 8: Only one shipment found for split order (or no shipments)`, { 
      currentIncrementId: incrementId, 
      shipmentCount: allSplitShipments?.length || 0 
    });
    console.log(`${logPrefix} Only one shipment found for split order (or no shipments) - proceeding with normal Shukran PR call`, { 
      currentIncrementId: incrementId, 
      shipmentCount: allSplitShipments?.length || 0 
    });
    // Only one shipment - proceed with normal Shukran PR call
    console.log(`${logPrefix} Step 8: Proceeding with normal Shukran PR call for single shipment split order`, { incrementId });
    console.log(`${logPrefix} ========== ENTERING shukranTransactionCreate FUNCTION ========== incrementId: ${incrementId}`, { incrementId, reason: 'Single shipment split order' });
    await shukranTransactionCreate({ incrementId });
    console.log(`${logPrefix} Step 8: Shukran PR call completed for single shipment split order`, { incrementId });
    return { status: resStatus, errorMsg: resError };
  }
  
  console.log(`${logPrefix} Step 8: Multiple shipments found for split order`, { 
    currentIncrementId: incrementId, 
    shipmentCount: allSplitShipments.length,
    shipmentIncrementIds: allSplitShipments.map(s => s.increment_id)
  });
  console.log(`${logPrefix} Multiple shipments found for split order`, { 
    currentIncrementId: incrementId, 
    shipmentCount: allSplitShipments.length,
    shipmentIncrementIds: allSplitShipments.map(s => s.increment_id)
  });
  
  // Identify current shipment (L1 or G1) and the other shipment
  console.log(`${logPrefix} Step 9: Identifying current shipment and other shipment`, { currentIncrementId: incrementId });
  const currentShipment = allSplitShipments.find(s => s.increment_id === incrementId);
  const otherShipment = allSplitShipments.find(s => s.increment_id !== incrementId);
  
  console.log(`${logPrefix} Step 9: Shipment identification result`, {
    currentIncrementId: incrementId,
    currentShipment: currentShipment ? { increment_id: currentShipment.increment_id, status: currentShipment.status } : null,
    otherShipment: otherShipment ? { increment_id: otherShipment.increment_id, status: otherShipment.status } : null
  });
  
  if (!currentShipment || !otherShipment) {
    console.log(`${logPrefix} Step 9: Could not identify both shipments, proceeding with normal flow`, {
      currentIncrementId: incrementId
    });
    console.log(`${logPrefix} ========== ENTERING shukranTransactionCreate FUNCTION ========== incrementId: ${incrementId}`, { incrementId, reason: 'Could not identify both shipments' });
    await shukranTransactionCreate({ incrementId });
    return { status: resStatus, errorMsg: resError };
  }
  
  const otherShipmentStatus = otherShipment.status;
  const terminalStatuses = [ORDER_RTO_STATUS_CODE, ORDER_DELIVERED_STATUS_CODE, ORDER_CANCELLED_STATUS_CODE , ORDER_STATE_CLOSED];
  
  console.log(`${logPrefix} Step 10: Checking if other shipment is in terminal state`, {
    currentIncrementId: incrementId,
    otherShipmentIncrementId: otherShipment.increment_id,
    otherShipmentStatus: otherShipmentStatus,
    terminalStatuses: terminalStatuses
  });
  
  const isOtherShipmentTerminal = terminalStatuses.includes(otherShipmentStatus);
  console.log(`${logPrefix} Step 10: Other shipment terminal status check result`, {
    currentIncrementId: incrementId,
    otherShipmentIncrementId: otherShipment.increment_id,
    otherShipmentStatus: otherShipmentStatus,
    isTerminal: isOtherShipmentTerminal
  });
  
  // If other shipment is not in terminal state, do nothing
  if (!isOtherShipmentTerminal) {
    console.log(`${logPrefix} Step 11: Other shipment is not in terminal state - doing nothing`, {
      currentIncrementId: incrementId,
      otherShipmentIncrementId: otherShipment.increment_id,
      otherShipmentStatus: otherShipmentStatus
    });
    console.log(`${logPrefix} The other shipment ${otherShipment.increment_id} associated with this order is not in terminal state - Shukran points will be credited once the other shipment is also delivered`);
    return { status: resStatus, errorMsg: resError };
  }
  
  console.log(`${logPrefix} Step 11: Other shipment is in terminal state, checking terminal state type`, {
    currentIncrementId: incrementId,
    otherShipmentIncrementId: otherShipment.increment_id,
    otherShipmentStatus: otherShipmentStatus
  });
  
  // Check if other shipment is RTO or Cancelled
  if (otherShipmentStatus === ORDER_RTO_STATUS_CODE || otherShipmentStatus === ORDER_CANCELLED_STATUS_CODE) {
    console.log(`${logPrefix} Step 12: Other shipment is RTO or Cancelled - creating PR call for current shipment only`, {
      currentIncrementId: incrementId,
      otherShipmentIncrementId: otherShipment.increment_id,
      otherShipmentStatus: otherShipmentStatus
    });
    // Create PR call for L1 (current shipment) only
    console.log(`${logPrefix} ========== ENTERING shukranTransactionCreate FUNCTION ========== incrementId: ${incrementId}`, { incrementId, reason: 'Other shipment RTO/Cancelled - current shipment only' });
    await shukranTransactionCreate({ incrementId });
    console.log(`${logPrefix} Step 12: PR call completed for current shipment only`, { incrementId });
    return { status: resStatus, errorMsg: resError };
  }
  
  // If other shipment is Delivered, check for return
  if (otherShipmentStatus === ORDER_DELIVERED_STATUS_CODE) {
    console.log(`${logPrefix} Step 12: Other shipment is Delivered - checking for return`, {
      currentIncrementId: incrementId,
      otherShipmentIncrementId: otherShipment.increment_id,
      otherShipmentOrderId: otherShipment.order_id
    });
    
    // Check if other shipment has a return
    const otherShipmentReturn = await checkReturnForOrderId(otherShipment.order_id);
    console.log(`${logPrefix} Step 13: Return check result for other shipment`, {
      currentIncrementId: incrementId,
      otherShipmentIncrementId: otherShipment.increment_id,
      otherShipmentOrderId: otherShipment.order_id,
      returnFound: otherShipmentReturn !== null,
      returnData: otherShipmentReturn
    });
    
    if (!otherShipmentReturn) {
      console.log(`${logPrefix} Step 14: No return found for other shipment - creating PR call for entire order`, {
        currentIncrementId: incrementId,
        otherShipmentIncrementId: otherShipment.increment_id,
        otherShipmentOrderId: otherShipment.order_id
      });
      
      // Get parent order increment_id
      const parentIncrementId = await getParentOrderIncrementId({
        orderId: otherShipment.order_id,
        fallbackOrderId: currentShipment.order_id
      });
      
      if (!parentIncrementId) {
        console.log(`${logPrefix} Step 14: No parent order found in sales_order, cannot proceed with entire order PR call`, {
          currentIncrementId: incrementId,
          otherShipmentOrderId: otherShipment.order_id,
          currentShipmentOrderId: currentShipment.order_id
        });
        return { status: resStatus, errorMsg: resError };
      }
      
      console.log(`${logPrefix} Step 14: Parent increment_id retrieved from sales_order`, {
        currentIncrementId: incrementId,
        order_id: otherShipment.order_id,
        parentIncrementId: parentIncrementId
      });

      // Check if parent order already has PR call done
      const parentOrderData = await orderObj.getOrder({ incrementId: parentIncrementId, inclSubSales: true });
      const isParentPrAlreadyDone = await isPrAlreadyDone(parentOrderData?.subSales?.shukran_pr_successful, 'delivered');
      
      // Also check if any split shipments already have PR call done (to prevent duplicates)
      let anySplitShipmentHasPrCall = false;
      for (const shipment of allSplitShipments) {
        try {
          const shipmentOrderData = await orderObj.getOrder({ incrementId: shipment.increment_id, inclSubSales: true });
          const shipmentPrDone = await isPrAlreadyDone(shipmentOrderData?.subSales?.shukran_pr_successful, 'delivered');
          if (shipmentPrDone) {
            anySplitShipmentHasPrCall = true;
            console.log(`${logPrefix} Step 14: Found split shipment with PR call already done`, {
              shipmentIncrementId: shipment.increment_id,
              parentIncrementId: parentIncrementId
            });
            break;
          }
        } catch (err) {
          console.error(`${logPrefix} Step 14: Error checking split shipment PR status`, {
            shipmentIncrementId: shipment.increment_id,
            error: err.message
          });
        }
      }
      
      console.log(`${logPrefix} Step 14: Checking if parent order or any split shipment already has PR call done`, {
        parentIncrementId: parentIncrementId,
        isParentPrAlreadyDone: isParentPrAlreadyDone,
        anySplitShipmentHasPrCall: anySplitShipmentHasPrCall,
        parentShukranPrSuccessful: parentOrderData?.subSales?.shukran_pr_successful
      });
      
      if (isParentPrAlreadyDone || anySplitShipmentHasPrCall) {
        console.log(`${logPrefix} Step 14: Parent order or split shipment already has PR call done - skipping duplicate call`, {
          parentIncrementId: parentIncrementId,
          currentIncrementId: incrementId,
          isParentPrAlreadyDone: isParentPrAlreadyDone,
          anySplitShipmentHasPrCall: anySplitShipmentHasPrCall
        });
        return { status: resStatus, errorMsg: resError };
      }
      
      // Create PR call for entire order (parent increment_id from sales_order)
      console.log(`${logPrefix} ========== ENTERING shukranTransactionCreate FUNCTION ========== incrementId: ${parentIncrementId}`, { incrementId: parentIncrementId, reason: 'No return - entire order' });
      await shukranTransactionCreate({ incrementId: parentIncrementId });
      console.log(`${logPrefix} ========== EXITED shukranTransactionCreate FUNCTION ==========`, { incrementId: parentIncrementId });
      console.log(`${logPrefix} Step 14: PR call completed for entire order`, { parentIncrementId });
      return { status: resStatus, errorMsg: resError };
    }
    
    // Return exists, check status
    const returnStatus = Number(otherShipmentReturn.status);
    console.log(`${logPrefix} Step 14: Return found, checking return status`, {
      currentIncrementId: incrementId,
      otherShipmentIncrementId: otherShipment.increment_id,
      returnStatus: returnStatus,
      rma_inc_id: otherShipmentReturn.rma_inc_id
    });
    
    if (returnStatus === 15 || returnStatus === 19) {
      console.log(`${logPrefix} Step 15: Return status is 15 or 19 - calculating returned items shukran coins`, {
        currentIncrementId: incrementId,
        otherShipmentIncrementId: otherShipment.increment_id,
        returnStatus: returnStatus,
        rma_inc_id: otherShipmentReturn.rma_inc_id,
        request_id: otherShipmentReturn.request_id
      });
      console.log(`${logPrefix} This shipment ${otherShipment.increment_id} has a return and return increment id = ${otherShipmentReturn.rma_inc_id}`);
      
      // Calculate returned items and their Shukran coins
      const { orderItemIds, totalReturnedShukranCoins } = await calculateReturnedItemsShukranCoins({
        requestId: otherShipmentReturn.request_id,
        otherShipmentIncrementId: otherShipment.increment_id,
        returnStatus: returnStatus,
        rmaIncId: otherShipmentReturn.rma_inc_id
      });
      
      if (!orderItemIds || orderItemIds.length === 0) {
        console.log(`${logPrefix} Step 15.1: No RMA request items found, proceeding with normal PR call`, {
          request_id: otherShipmentReturn.request_id
        });
        // No returned items found, proceed with normal PR call
        const parentIncrementId = await getParentOrderIncrementId({
          orderId: otherShipment.order_id,
          fallbackOrderId: currentShipment.order_id
        });
        
        if (parentIncrementId) {
          // Check if parent order already has PR call done
          const parentOrderDataNoItems = await orderObj.getOrder({ incrementId: parentIncrementId, inclSubSales: true });
          const isParentPrAlreadyDoneNoItems = await isPrAlreadyDone(parentOrderDataNoItems?.subSales?.shukran_pr_successful, 'delivered');
          
          // Also check if any split shipments already have PR call done (to prevent duplicates)
          let anySplitShipmentHasPrCallNoItems = false;
          for (const shipment of allSplitShipments) {
            try {
              const shipmentOrderData = await orderObj.getOrder({ incrementId: shipment.increment_id, inclSubSales: true });
              const shipmentPrDone = await isPrAlreadyDone(shipmentOrderData?.subSales?.shukran_pr_successful, 'delivered');
              if (shipmentPrDone) {
                anySplitShipmentHasPrCallNoItems = true;
                console.log(`${logPrefix} Step 15.1: Found split shipment with PR call already done (no items found)`, {
                  shipmentIncrementId: shipment.increment_id,
                  parentIncrementId: parentIncrementId
                });
                break;
              }
            } catch (err) {
              console.error(`${logPrefix} Step 15.1: Error checking split shipment PR status`, {
                shipmentIncrementId: shipment.increment_id,
                error: err.message
              });
            }
          }
          
          console.log(`${logPrefix} Step 15.1: Checking if parent order or any split shipment already has PR call done (no items found)`, {
            parentIncrementId: parentIncrementId,
            isParentPrAlreadyDone: isParentPrAlreadyDoneNoItems,
            anySplitShipmentHasPrCall: anySplitShipmentHasPrCallNoItems,
            parentShukranPrSuccessful: parentOrderDataNoItems?.subSales?.shukran_pr_successful
          });
          
          if (!isParentPrAlreadyDoneNoItems && !anySplitShipmentHasPrCallNoItems) {
            console.log(`${logPrefix} ========== ENTERING shukranTransactionCreate FUNCTION ========== incrementId: ${parentIncrementId}`, { incrementId: parentIncrementId, reason: 'Return status 15/19 but no items found' });
            await shukranTransactionCreate({ incrementId: parentIncrementId });
            console.log(`${logPrefix} ========== EXITED shukranTransactionCreate FUNCTION ==========`, { incrementId: parentIncrementId });
          } else {
            console.log(`${logPrefix} Step 15.1: Parent order or split shipment already has PR call done - skipping duplicate call`, {
              parentIncrementId: parentIncrementId,
              currentIncrementId: incrementId,
              isParentPrAlreadyDone: isParentPrAlreadyDoneNoItems,
              anySplitShipmentHasPrCall: anySplitShipmentHasPrCallNoItems
            });
          }
        }
        return { status: resStatus, errorMsg: resError };
      }
      
      // Get parent order increment_id
      const parentIncrementId = await getParentOrderIncrementId({
        orderId: otherShipment.order_id,
        fallbackOrderId: currentShipment.order_id
      });
      
      if (!parentIncrementId) {
        console.log(`${logPrefix} Step 15: No parent order found in sales_order, cannot proceed with entire order PR call`, {
          currentIncrementId: incrementId,
          order_id: otherShipment.order_id
        });
        return { status: resStatus, errorMsg: resError };
      }
      
      console.log(`${logPrefix} Step 15: Parent increment_id retrieved from sales_order`, {
        currentIncrementId: incrementId,
        order_id: otherShipment.order_id,
        parentIncrementId: parentIncrementId,
        totalReturnedShukranCoins: totalReturnedShukranCoins
      });

      // Check if parent order already has PR call done
      const parentOrderDataWithReturn = await orderObj.getOrder({ incrementId: parentIncrementId, inclSubSales: true });
      const isParentPrAlreadyDoneWithReturn = await isPrAlreadyDone(parentOrderDataWithReturn?.subSales?.shukran_pr_successful, 'delivered');
      
      // Also check if any split shipments already have PR call done (to prevent duplicates)
      let anySplitShipmentHasPrCallWithReturn = false;
      for (const shipment of allSplitShipments) {
        try {
          const shipmentOrderData = await orderObj.getOrder({ incrementId: shipment.increment_id, inclSubSales: true });
          const shipmentPrDone = await isPrAlreadyDone(shipmentOrderData?.subSales?.shukran_pr_successful, 'delivered');
          if (shipmentPrDone) {
            anySplitShipmentHasPrCallWithReturn = true;
            console.log(`${logPrefix} Step 15: Found split shipment with PR call already done (with returned items)`, {
              shipmentIncrementId: shipment.increment_id,
              parentIncrementId: parentIncrementId
            });
            break;
          }
        } catch (err) {
          console.error(`${logPrefix} Step 15: Error checking split shipment PR status`, {
            shipmentIncrementId: shipment.increment_id,
            error: err.message
          });
        }
      }
      
      console.log(`${logPrefix} Step 15: Checking if parent order or any split shipment already has PR call done (with returned items)`, {
        parentIncrementId: parentIncrementId,
        isParentPrAlreadyDone: isParentPrAlreadyDoneWithReturn,
        anySplitShipmentHasPrCall: anySplitShipmentHasPrCallWithReturn,
        parentShukranPrSuccessful: parentOrderDataWithReturn?.subSales?.shukran_pr_successful
      });
      
      if (isParentPrAlreadyDoneWithReturn || anySplitShipmentHasPrCallWithReturn) {
        console.log(`${logPrefix} Step 15: Parent order or split shipment already has PR call done - skipping duplicate call`, {
          parentIncrementId: parentIncrementId,
          currentIncrementId: incrementId,
          excludedOrderItemIds: orderItemIds,
          totalReturnedShukranCoins: totalReturnedShukranCoins,
          isParentPrAlreadyDone: isParentPrAlreadyDoneWithReturn,
          anySplitShipmentHasPrCall: anySplitShipmentHasPrCallWithReturn
        });
        return { status: resStatus, errorMsg: resError };
      }
      
      // Pass excluded order_item_ids to shukranTransactionCreate only if feature flag is enabled
      // These are the returned items that should not earn Shukran points
      const itemsToExclude = shukranOnShipmentLevel ? orderItemIds : [];
      
      console.log(`${logPrefix} Step 15: Feature flag check before passing excluded order_item_ids`, {
        parentIncrementId,
        shukranOnShipmentLevel,
        excludedOrderItemIdsProvided: orderItemIds.length,
        itemsToExcludeCount: itemsToExclude.length,
        totalReturnedShukranCoins: totalReturnedShukranCoins
      });
      
      console.log(`${logPrefix} Step 15: Passing excluded order_item_ids to shukranTransactionCreate`, {
        parentIncrementId,
        excludedOrderItemIds: itemsToExclude,
        excludedCount: itemsToExclude.length,
        totalReturnedShukranCoins: totalReturnedShukranCoins,
        shukranOnShipmentLevel
      });
      console.log(`${logPrefix} ========== ENTERING shukranTransactionCreate FUNCTION ========== incrementId: ${parentIncrementId}`, { 
        incrementId: parentIncrementId, 
        reason: 'Return status 15/19 - entire order with returned items excluded',
        excludedOrderItemIds: itemsToExclude,
        totalReturnedShukranCoins: totalReturnedShukranCoins,
        shukranOnShipmentLevel
      });
      await shukranTransactionCreate({ 
        incrementId: parentIncrementId,
        excludedOrderItemIds: itemsToExclude
      });
      console.log(`${logPrefix} ========== EXITED shukranTransactionCreate FUNCTION ==========`, { 
        incrementId: parentIncrementId, 
        excludedOrderItemIds: orderItemIds,
        totalReturnedShukranCoins: totalReturnedShukranCoins
      });
      console.log(`${logPrefix} Step 15: PR call completed for entire order with returned items excluded`, { 
        parentIncrementId, 
        excludedOrderItemIds: orderItemIds,
        totalReturnedShukranCoins
      });
      return { status: resStatus, errorMsg: resError };
    } else {
      console.log(`${logPrefix} Step 15: Return status is not 15 or 19 - creating PR call for entire order`, {
        currentIncrementId: incrementId,
        otherShipmentIncrementId: otherShipment.increment_id,
        returnStatus: returnStatus,
        rma_inc_id: otherShipmentReturn.rma_inc_id,
        otherShipmentOrderId: otherShipment.order_id
      });
      
      // Get parent order increment_id
      const parentIncrementId = await getParentOrderIncrementId({
        orderId: otherShipment.order_id,
        fallbackOrderId: currentShipment.order_id
      });
      
      if (!parentIncrementId) {
        console.log(`${logPrefix} Step 15: No parent order found, cannot proceed with entire order PR call`, {
          currentIncrementId: incrementId,
          order_id: otherShipment.order_id
        });
        return { status: resStatus, errorMsg: resError };
      }
      
      console.log(`${logPrefix} Step 15: Parent increment_id retrieved from sales_order`, {
        currentIncrementId: incrementId,
        order_id: otherShipment.order_id,
        parentIncrementId: parentIncrementId
      });

      // Check if parent order already has PR call done
      const parentOrderDataForNonReturn = await orderObj.getOrder({ incrementId: parentIncrementId, inclSubSales: true });
      const isParentPrAlreadyDoneForNonReturn = await isPrAlreadyDone(parentOrderDataForNonReturn?.subSales?.shukran_pr_successful, 'delivered');
      
      // Also check if any split shipments already have PR call done (to prevent duplicates)
      let anySplitShipmentHasPrCallForNonReturn = false;
      for (const shipment of allSplitShipments) {
        try {
          const shipmentOrderData = await orderObj.getOrder({ incrementId: shipment.increment_id, inclSubSales: true });
          const shipmentPrDone = await isPrAlreadyDone(shipmentOrderData?.subSales?.shukran_pr_successful, 'delivered');
          if (shipmentPrDone) {
            anySplitShipmentHasPrCallForNonReturn = true;
            console.log(`${logPrefix} Step 15: Found split shipment with PR call already done (return status not 15/19)`, {
              shipmentIncrementId: shipment.increment_id,
              parentIncrementId: parentIncrementId
            });
            break;
          }
        } catch (err) {
          console.error(`${logPrefix} Step 15: Error checking split shipment PR status`, {
            shipmentIncrementId: shipment.increment_id,
            error: err.message
          });
        }
      }
      
      console.log(`${logPrefix} Step 15: Checking if parent order or any split shipment already has PR call done (return status not 15/19)`, {
        parentIncrementId: parentIncrementId,
        isParentPrAlreadyDone: isParentPrAlreadyDoneForNonReturn,
        anySplitShipmentHasPrCall: anySplitShipmentHasPrCallForNonReturn,
        parentShukranPrSuccessful: parentOrderDataForNonReturn?.subSales?.shukran_pr_successful
      });
      
      if (isParentPrAlreadyDoneForNonReturn || anySplitShipmentHasPrCallForNonReturn) {
        console.log(`${logPrefix} Step 15: Parent order or split shipment already has PR call done - skipping duplicate call`, {
          parentIncrementId: parentIncrementId,
          currentIncrementId: incrementId,
          isParentPrAlreadyDone: isParentPrAlreadyDoneForNonReturn,
          anySplitShipmentHasPrCall: anySplitShipmentHasPrCallForNonReturn
        });
        return { status: resStatus, errorMsg: resError };
      }

      // Create PR call for entire order (parent increment_id from sales_order)
      console.log(`${logPrefix} ========== ENTERING shukranTransactionCreate FUNCTION ========== incrementId: ${parentIncrementId}`, { incrementId: parentIncrementId, reason: 'Return status not 15/19 - entire order' });
      await shukranTransactionCreate({ incrementId: parentIncrementId });
      console.log(`${logPrefix} ========== EXITED shukranTransactionCreate FUNCTION ==========`, { incrementId: parentIncrementId });
      console.log(`${logPrefix} Step 15: PR call completed for entire order`, { parentIncrementId });
      return { status: resStatus, errorMsg: resError };
    }
  }
  
  // Fallback - should not reach here
  console.log(`${logPrefix} Step 16: Unexpected terminal state - proceeding with normal flow`, {
    currentIncrementId: incrementId,
    otherShipmentStatus: otherShipmentStatus
  });
  console.log(`${logPrefix} ========== ENTERING shukranTransactionCreate FUNCTION ========== incrementId: ${incrementId}`, { incrementId, reason: 'Unexpected terminal state - fallback' });
  await shukranTransactionCreate({ incrementId });
  console.log(`${logPrefix} Step 16: PR call completed`, { incrementId });
  
  return { status: resStatus, errorMsg: resError };
};

/**
 * Handle Shukran PR call for RTO/Cancelled shipments when other shipment is Delivered
 * This function is self-contained and only requires incrementId
 * @param {string} incrementId - Current shipment increment_id (RTO/Cancelled)
 * @returns {Promise<Object>} - Status and error message
 */
const handleShukranRtoCancelledLogic = async (incrementId) => {
  const logPrefix = '[shukranSplitOrderHandling]';
  
  console.log(`${logPrefix} handleShukranRtoCancelledLogic: Starting RTO/Cancelled handling`, {
    incrementId
  });
  
  try {
    // Fetch baseConfig and check if Shukran is enabled
    const { baseConfig = {} } = global;
    const shukranEnable = baseConfig?.shukranEnable ?? false;
    const shukranOnShipmentLevel = baseConfig?.shukranOnShipmentLevel ?? false;
    
    console.log(`${logPrefix} handleShukranRtoCancelledLogic: Feature flags check`, {
      incrementId,
      shukranEnable,
      shukranOnShipmentLevel
    });
    
    if (!shukranEnable) {
      console.log(`${logPrefix} handleShukranRtoCancelledLogic: Shukran is not enabled - no action needed`, { incrementId });
      return { status: false, errorMsg: 'Shukran is not enabled' };
    }
    
    // Check if this is a split order
    console.log(`${logPrefix} RTO/Cancelled Step 1: Checking if order is a split order`, { incrementId });
    const isSplitOrder = isSplitOrderPattern(incrementId);
    console.log(`${logPrefix} RTO/Cancelled Step 1: Split order check result`, { incrementId, isSplitOrder });
    
    if (!shukranOnShipmentLevel || !isSplitOrder) {
      console.log(`${logPrefix} RTO/Cancelled Step 1: The order is not a split order or feature flag disabled - no action needed`, { incrementId });
      return { status: false, errorMsg: 'Not a split order or feature flag disabled' };
    }
  
  console.log(`${logPrefix} RTO/Cancelled Step 2: The order is a split order, fetching all split shipments`, { incrementId });
  
  // Fetch all split shipments for the parent order
  const allSplitShipments = await fetchAllSplitShipmentsForParentOrder(incrementId);
  console.log(`${logPrefix} RTO/Cancelled Step 2: Fetch operation completed`, { 
    currentIncrementId: incrementId, 
    totalShipments: allSplitShipments?.length || 0,
    shipments: allSplitShipments 
  });
  
    if (!allSplitShipments || allSplitShipments.length <= 1) {
      console.log(`${logPrefix} RTO/Cancelled Step 2: Only one shipment found for split order - no action needed`, { 
        currentIncrementId: incrementId, 
        shipmentCount: allSplitShipments?.length || 0 
      });
      return { status: false, errorMsg: 'Only one shipment found for split order' };
    }
    
    console.log(`${logPrefix} RTO/Cancelled Step 3: Multiple shipments found, identifying current and other shipment`, { 
      currentIncrementId: incrementId, 
      shipmentCount: allSplitShipments.length
    });
    
    // Identify current shipment (RTO/Cancelled) and the other shipment
    const currentShipment = allSplitShipments.find(s => s.increment_id === incrementId);
    const otherShipment = allSplitShipments.find(s => s.increment_id !== incrementId);
    
    console.log(`${logPrefix} RTO/Cancelled Step 3: Shipment identification result`, {
      currentIncrementId: incrementId,
      currentShipment: currentShipment ? { increment_id: currentShipment.increment_id, status: currentShipment.status } : null,
      otherShipment: otherShipment ? { increment_id: otherShipment.increment_id, status: otherShipment.status } : null
    });
    
    if (!currentShipment || !otherShipment) {
      console.log(`${logPrefix} RTO/Cancelled Step 3: Could not identify both shipments - no action needed`, { 
        currentIncrementId: incrementId 
      });
      return { status: false, errorMsg: 'Could not identify both shipments' };
    }
    
    const otherShipmentStatus = otherShipment.status;
    
    console.log(`${logPrefix} RTO/Cancelled Step 4: Checking if other shipment is Delivered`, {
      currentIncrementId: incrementId,
      otherShipmentIncrementId: otherShipment.increment_id,
      otherShipmentStatus: otherShipmentStatus
    });
    
    // Check if other shipment is Delivered
    if (otherShipmentStatus !== ORDER_DELIVERED_STATUS_CODE) {
      console.log(`${logPrefix} RTO/Cancelled Step 4: Other shipment is not Delivered - no action needed`, {
        currentIncrementId: incrementId,
        otherShipmentIncrementId: otherShipment.increment_id,
        otherShipmentStatus: otherShipmentStatus
      });
      return { status: false, errorMsg: 'Other shipment is not Delivered' };
    }
  
  console.log(`${logPrefix} RTO/Cancelled Step 4: Other shipment is Delivered - checking for return`, {
    currentIncrementId: incrementId,
    otherShipmentIncrementId: otherShipment.increment_id,
    otherShipmentOrderId: otherShipment.order_id
  });
  
  // Check if other (delivered) shipment has a return
  const otherShipmentReturn = await checkReturnForOrderId(otherShipment.order_id);
  console.log(`${logPrefix} RTO/Cancelled Step 5: Return check result for other (delivered) shipment`, {
    currentIncrementId: incrementId,
    otherShipmentIncrementId: otherShipment.increment_id,
    otherShipmentOrderId: otherShipment.order_id,
    returnFound: otherShipmentReturn !== null,
    returnData: otherShipmentReturn
  });
  
  if (!otherShipmentReturn) {
    console.log(`${logPrefix} RTO/Cancelled Step 6: No return found for other (delivered) shipment - creating PR call for delivered shipment`, {
      currentIncrementId: incrementId,
      otherShipmentIncrementId: otherShipment.increment_id,
      otherShipmentOrderId: otherShipment.order_id
    });

    // Check if delivered shipment already has PR call done
    const deliveredShipmentOrderData = await orderObj.getOrder({ incrementId: otherShipment.increment_id, inclSubSales: true });
    const isDeliveredShipmentPrAlreadyDone = await isPrAlreadyDone(deliveredShipmentOrderData?.subSales?.shukran_pr_successful, 'delivered');
    
    console.log(`${logPrefix} RTO/Cancelled Step 6: Checking if delivered shipment already has PR call done`, {
      deliveredShipmentIncrementId: otherShipment.increment_id,
      isDeliveredShipmentPrAlreadyDone: isDeliveredShipmentPrAlreadyDone,
      deliveredShipmentShukranPrSuccessful: deliveredShipmentOrderData?.subSales?.shukran_pr_successful
    });
    
    if (isDeliveredShipmentPrAlreadyDone) {
      console.log(`${logPrefix} RTO/Cancelled Step 6: Delivered shipment already has PR call done - skipping duplicate call`, {
        deliveredShipmentIncrementId: otherShipment.increment_id,
        currentIncrementId: incrementId
      });
      return { status: true, errorMsg: null, message: 'PR call already done for delivered shipment' };
    }
    
    // No return - do PR call for delivered shipment
    console.log(`${logPrefix} ========== ENTERING shukranTransactionCreate FUNCTION ========== incrementId: ${otherShipment.increment_id}`, { 
      incrementId: otherShipment.increment_id, 
      reason: 'RTO/Cancelled - other shipment delivered, no return' 
    });
    await shukranTransactionCreate({ incrementId: otherShipment.increment_id });
    console.log(`${logPrefix} ========== EXITED shukranTransactionCreate FUNCTION ==========`, { 
      incrementId: otherShipment.increment_id 
    });
      console.log(`${logPrefix} RTO/Cancelled Step 6: PR call completed for delivered shipment (no return)`, { 
        deliveredShipmentIncrementId: otherShipment.increment_id 
      });
      return { status: true, errorMsg: null, message: 'PR call completed for delivered shipment (no return)' };
    }
    
    // Return exists, check status
    const returnStatus = Number(otherShipmentReturn.status);
    console.log(`${logPrefix} RTO/Cancelled Step 6: Return found, checking return status`, {
      currentIncrementId: incrementId,
      otherShipmentIncrementId: otherShipment.increment_id,
      returnStatus: returnStatus,
      rma_inc_id: otherShipmentReturn.rma_inc_id
    });
    
    if (returnStatus === 15 || returnStatus === 19) {
    console.log(`${logPrefix} RTO/Cancelled Step 7: Return status is 15 or 19 - calculating returned items shukran coins`, {
      currentIncrementId: incrementId,
      otherShipmentIncrementId: otherShipment.increment_id,
      returnStatus: returnStatus,
      rma_inc_id: otherShipmentReturn.rma_inc_id,
      request_id: otherShipmentReturn.request_id
    });
    console.log(`${logPrefix} RTO/Cancelled: Other (delivered) shipment ${otherShipment.increment_id} has a return and return increment id = ${otherShipmentReturn.rma_inc_id}`);
    
    // Calculate returned items and their Shukran coins
    const { orderItemIds, totalReturnedShukranCoins } = await calculateReturnedItemsShukranCoins({
      requestId: otherShipmentReturn.request_id,
      otherShipmentIncrementId: otherShipment.increment_id,
      returnStatus: returnStatus,
      rmaIncId: otherShipmentReturn.rma_inc_id
    });
    
    if (!orderItemIds || orderItemIds.length === 0) {
      console.log(`${logPrefix} RTO/Cancelled Step 7: No RMA request items found, proceeding with normal PR call for delivered shipment`, {
        request_id: otherShipmentReturn.request_id
      });
      // No returned items found, proceed with normal PR call for delivered shipment
      console.log(`${logPrefix} ========== ENTERING shukranTransactionCreate FUNCTION ========== incrementId: ${otherShipment.increment_id}`, { 
        incrementId: otherShipment.increment_id, 
        reason: 'RTO/Cancelled - other shipment delivered, return status 15/19 but no items found' 
      });
      await shukranTransactionCreate({ incrementId: otherShipment.increment_id });
      console.log(`${logPrefix} ========== EXITED shukranTransactionCreate FUNCTION ==========`, { 
        incrementId: otherShipment.increment_id 
      });
      return { status: true, errorMsg: null, message: 'PR call completed for delivered shipment (return status 15/19 but no items found)' };
    }
    
    console.log(`${logPrefix} RTO/Cancelled Step 7: Calculated returned items and shukran coins`, {
      otherShipmentIncrementId: otherShipment.increment_id,
      totalReturnedShukranCoins: totalReturnedShukranCoins,
      returnedItemsCount: orderItemIds.length,
      order_item_ids: orderItemIds
    });
    
    // Pass excluded order_item_ids to shukranTransactionCreate only if feature flag is enabled
    const itemsToExclude = shukranOnShipmentLevel ? orderItemIds : [];
    
    console.log(`${logPrefix} RTO/Cancelled Step 7: Feature flag check before passing excluded order_item_ids`, {
      deliveredShipmentIncrementId: otherShipment.increment_id,
      shukranOnShipmentLevel,
      excludedOrderItemIdsProvided: orderItemIds.length,
      itemsToExcludeCount: itemsToExclude.length,
      totalReturnedShukranCoins: totalReturnedShukranCoins
    });
    
    console.log(`${logPrefix} RTO/Cancelled Step 7: Passing excluded order_item_ids to shukranTransactionCreate for delivered shipment`, {
      deliveredShipmentIncrementId: otherShipment.increment_id,
      excludedOrderItemIds: itemsToExclude,
      excludedCount: itemsToExclude.length,
      totalReturnedShukranCoins: totalReturnedShukranCoins,
      shukranOnShipmentLevel
    });
    // Check if delivered shipment already has PR call done
    const deliveredShipmentOrderDataWithItems = await orderObj.getOrder({ incrementId: otherShipment.increment_id, inclSubSales: true });
    const isDeliveredShipmentPrAlreadyDoneWithItems = await isPrAlreadyDone(deliveredShipmentOrderDataWithItems?.subSales?.shukran_pr_successful, 'delivered');
    
    console.log(`${logPrefix} RTO/Cancelled Step 7: Checking if delivered shipment already has PR call done (with excluded items)`, {
      deliveredShipmentIncrementId: otherShipment.increment_id,
      isDeliveredShipmentPrAlreadyDone: isDeliveredShipmentPrAlreadyDoneWithItems,
      deliveredShipmentShukranPrSuccessful: deliveredShipmentOrderDataWithItems?.subSales?.shukran_pr_successful
    });
    
    if (isDeliveredShipmentPrAlreadyDoneWithItems) {
      console.log(`${logPrefix} RTO/Cancelled Step 7: Delivered shipment already has PR call done - skipping duplicate call`, {
        deliveredShipmentIncrementId: otherShipment.increment_id,
        currentIncrementId: incrementId,
        excludedOrderItemIds: itemsToExclude,
        totalReturnedShukranCoins: totalReturnedShukranCoins
      });
      return { status: true, errorMsg: null, message: 'PR call already done for delivered shipment' };
    }
    
    console.log(`${logPrefix} ========== ENTERING shukranTransactionCreate FUNCTION ========== incrementId: ${otherShipment.increment_id}`, { 
      incrementId: otherShipment.increment_id, 
      reason: 'RTO/Cancelled - other shipment delivered, return status 15/19 with returned items excluded',
      excludedOrderItemIds: itemsToExclude,
      totalReturnedShukranCoins: totalReturnedShukranCoins,
      shukranOnShipmentLevel
    });
    await shukranTransactionCreate({ 
      incrementId: otherShipment.increment_id,
      excludedOrderItemIds: itemsToExclude
    });
    console.log(`${logPrefix} ========== EXITED shukranTransactionCreate FUNCTION ==========`, { 
      incrementId: otherShipment.increment_id, 
      excludedOrderItemIds: orderItemIds,
      totalReturnedShukranCoins: totalReturnedShukranCoins
    });
      console.log(`${logPrefix} RTO/Cancelled Step 7: PR call completed for delivered shipment with returned items excluded`, { 
        deliveredShipmentIncrementId: otherShipment.increment_id, 
        excludedOrderItemIds: orderItemIds,
        totalReturnedShukranCoins
      });
      return { status: true, errorMsg: null, message: 'PR call completed for delivered shipment with returned items excluded' };
    } else {
      console.log(`${logPrefix} RTO/Cancelled Step 7: Return status is not 15 or 19 - creating PR call for delivered shipment`, {
        currentIncrementId: incrementId,
        otherShipmentIncrementId: otherShipment.increment_id,
        returnStatus: returnStatus,
        rma_inc_id: otherShipmentReturn.rma_inc_id
      });

      // Check if delivered shipment already has PR call done
      const deliveredShipmentOrderDataNot1519 = await orderObj.getOrder({ incrementId: otherShipment.increment_id, inclSubSales: true });
      const isDeliveredShipmentPrAlreadyDoneNot1519 = await isPrAlreadyDone(deliveredShipmentOrderDataNot1519?.subSales?.shukran_pr_successful, 'delivered');
      
      console.log(`${logPrefix} RTO/Cancelled Step 7: Checking if delivered shipment already has PR call done (return status not 15/19)`, {
        deliveredShipmentIncrementId: otherShipment.increment_id,
        isDeliveredShipmentPrAlreadyDone: isDeliveredShipmentPrAlreadyDoneNot1519,
        deliveredShipmentShukranPrSuccessful: deliveredShipmentOrderDataNot1519?.subSales?.shukran_pr_successful
      });
      
      if (isDeliveredShipmentPrAlreadyDoneNot1519) {
        console.log(`${logPrefix} RTO/Cancelled Step 7: Delivered shipment already has PR call done - skipping duplicate call`, {
          deliveredShipmentIncrementId: otherShipment.increment_id,
          currentIncrementId: incrementId
        });
        return { status: true, errorMsg: null, message: 'PR call already done for delivered shipment' };
      }

      // Return status is not 15/19 - do PR call for delivered shipment
      console.log(`${logPrefix} ========== ENTERING shukranTransactionCreate FUNCTION ========== incrementId: ${otherShipment.increment_id}`, { 
        incrementId: otherShipment.increment_id, 
        reason: 'RTO/Cancelled - other shipment delivered, return status not 15/19'
      });
      await shukranTransactionCreate({ incrementId: otherShipment.increment_id });
      console.log(`${logPrefix} ========== EXITED shukranTransactionCreate FUNCTION ==========`, { 
        incrementId: otherShipment.increment_id 
      });
      console.log(`${logPrefix} RTO/Cancelled Step 7: PR call completed for delivered shipment`, { 
        deliveredShipmentIncrementId: otherShipment.increment_id 
      });
      return { status: true, errorMsg: null, message: 'PR call completed for delivered shipment' };
    }
  } catch (error) {
    console.error(`${logPrefix} handleShukranRtoCancelledLogic: Error processing RTO/Cancelled logic`, {
      incrementId,
      error: error.message,
      stack: error.stack
    });
    global.logError('Error in handleShukranRtoCancelledLogic', error);
    return { status: false, errorMsg: error.message || 'Error processing RTO/Cancelled logic' };
  }
};

exports.updateFwdShipment = async ({
  increment_id: incrementId,
  timestamp,
  notificationId,
  waybill,
  ndr_status_description: ndrStatusDesc,
  rtoAwb,
  reqStatus,
  cp_id
}) => {
  const logPrefix = '[shukranSplitOrderHandling]';
  try {
    let errorMsg;
    let updateObj = {};
    const orderData = await this.getOrderFwdShipment(incrementId);
    if (!orderData) {
      throw new Error(`Data not found for order ${incrementId}`);
    }
    if (!orderData.status) {
      throw new Error(`Order status is not found for order ${incrementId}`);
    }
    const {
      status,
      // entity_id: entityId,
      shipmentData,
      store_id: storeId,
      // customer_id: customerId,
      shippingAddress,
    } = orderData;

    const paymentMethod = orderData.paymentInformation?.[0]?.method;
    const isDelivered = notificationId === 5 || notificationId === '5';
    if (isDuplicateReq({ notificationId, status, reqStatus })) {
      return { status: false, errorMsg: 'order in same state' };
    }

    const { baseConfig = {} } = global
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
    emailTemplate: '',
    triggerRtoRefund: false
    };

    const shipmentObj = shipmentData?.find(ship => {
      global.logInfo(ship);
      return ship.track_number === waybill;
    });

    if (!shipmentObj) {
      errorMsg = `Data not found for order ${incrementId} with AWB ${waybill}`;
      return { status: false, errorMsg };
    }

    if (!errorMsg) {
      handleSwitchCases(
        switchCaseVars,
        notificationId,
        status,
        ndrStatusDesc,
        timestamp,
        paymentMethod
      );
      
      const excludedMessageHeadings = global.baseConfig?.smsConfig?.excludedMessageHeadings || [];
      const SMSExcludedCourierPartners = global.baseConfig?.smsConfig?.SMSExcludedCourierPartners || [];
      if (excludedMessageHeadings.includes(switchCaseVars.smsStatus) ||
        SMSExcludedCourierPartners.includes(String(cp_id))) {
        switchCaseVars.smsStatus = '';
      }

      // ========== FBS (Fulfilled by Styli) Pub/Sub Processing ==========
      // FBS = seller_id is '0001' (Styli) AND owner_seller_id has value
      if (switchCaseVars.checkFBS) {
        try {
          const { SplitSellerOrder, SplitSellerOrderItem } = Models;
          const logger = require('../config/logger');
          const SELLER_CENTRAL_ORDER_TOPIC = process.env.SELLER_CENTRAL_ORDER_TOPIC || 'seller-central-create-order-production';
          
          logger.info(`[FBS] Checking FBS for increment_id: ${incrementId}, fbsStatus: ${switchCaseVars.fbsStatus}`);
          
          // Get seller orders for this order
          const sellerOrders = await SplitSellerOrder.findAll({
            where: { main_order_id: orderData.entity_id },
            attributes: ['increment_id', 'seller_id', 'owner_seller_id', 'entity_id']
          });
          
          logger.info(`[FBS] Found ${sellerOrders?.length || 0} seller orders for main_order_id: ${orderData.entity_id}`);
          
          const payload = [];
          
          for (const sellerOrder of sellerOrders) {
            // FBS Check: seller_id = '0001' AND owner_seller_id IS NOT NULL
            if (sellerOrder.seller_id === '0001' && sellerOrder.owner_seller_id) {
              logger.info(`[FBS] FBS order detected! increment_id: ${sellerOrder.increment_id}, seller_id: 0001, owner_seller_id: ${sellerOrder.owner_seller_id}`);
              
              // Get SKUs for this seller order (same as existing seller central logic)
              const sellerOrderItems = await SplitSellerOrderItem.findAll({
                where: { seller_order_id: sellerOrder.entity_id, product_type: 'configurable' },
                attributes: ['sku']
              });
              
              logger.info(`[FBS] Found ${sellerOrderItems?.length || 0} items for seller_order: ${sellerOrder.increment_id}`);
              
              for (const item of sellerOrderItems) {
                payload.push({
                  sellerOrderId: sellerOrder.increment_id,
                  sku: item.sku,
                  status: switchCaseVars.fbsStatus,  // 'picked_up' / 'delivered' / 'rto'
                  ownerSellerId: sellerOrder.owner_seller_id  // Additional field for FBS
                });
              }
            }
          }
          
          if (payload.length > 0) {
            const pubsubMessage = {
              type: 'update',  // Same as existing seller central
              payload: payload
            };
            
            logger.info(`[FBS] Publishing to topic: ${SELLER_CENTRAL_ORDER_TOPIC}`);
            logger.info(`[FBS] Payload: ${JSON.stringify(pubsubMessage)}`);
            
            await PubSubService.publishMessage(SELLER_CENTRAL_ORDER_TOPIC, pubsubMessage);
            
            logger.info(`[FBS] Pub/Sub sent successfully - ${payload.length} items with status: ${switchCaseVars.fbsStatus}`);
          } else {
            logger.info(`[FBS] No FBS items found for order: ${incrementId}`);
          }
        } catch (fbsError) {
          console.error(`[FBS] Error in FBS Pub/Sub for ${incrementId}:`, fbsError.message);
        }
      }
      // ========== END FBS ==========
      
     
      updateObj = {
        state: switchCaseVars.toUpdateState,
        status: switchCaseVars.toUpdateStatus,
        smsStatus: switchCaseVars.smsStatus
      };
      updateObj.clickpost_message = switchCaseVars.clickpostMessage || '';
      if (switchCaseVars.deliveredAt) {
        updateObj.delivered_at = switchCaseVars.deliveredAt || '';
      }
    }
    const updateOrderRes = await orderObj.updateOrder({
      updateObj,
      incrementId,
      entityId: orderData.entity_id,
      storeId,
      orderData,
      waybill,
      rtoAwb,
      notificationId,
      reqStatus,
      ...switchCaseVars
    });
    const { success: resStatus, errorMsg: resError } = updateOrderRes;

    const isPrepaid = orderData.paymentInformation?.[0]?.method !== CASHONDELIVERY;
    const courierCode = shipmentObj?.carrier_code;
    const rtoRefundConfig = { enabled: false, ...(global?.baseConfig?.rtoAutoRefund || {}) };
    const courierAllowed = !Array.isArray(rtoRefundConfig.couriers) || rtoRefundConfig.couriers.includes(courierCode);

    console.log('Rto-Trigger ', incrementId, ' ', isPrepaid, ' ', courierCode, ' ', rtoRefundConfig, ' ', courierAllowed, ' ', orderData?.paymentInformation[0]?.method, ' ', switchCaseVars.triggerRtoRefund);

    if (
      // resStatus &&
      switchCaseVars.triggerRtoRefund &&
      rtoRefundConfig.enabled &&
      isPrepaid &&
      courierAllowed
    ) {
      let alreadyRefunded = false;
      try {
        alreadyRefunded = await hasExistingRefund({
          incrementId,
          waybill,
          orderId: orderData.entity_id
        });
      } catch (err) {
        global.logError?.('Error checking existing RTO refund', err) || console.error('Error checking existing RTO refund', err);
      }

      if (!alreadyRefunded) {
        const refundResp = await callRtoRefund({
          orderId: orderData.entity_id,
          incrementId,
          waybill,
          paymentMethod: orderData.paymentInformation?.[0]?.method
        });
        if (!refundResp?.status) {
          return { status: false, errorMsg: refundResp?.errorMsg || 'RTO refund failed' };
        }
      }
    }

    console.log('Step 1: Checking if Shukran PR call is already done', { incrementId });
    const isPrCallAlreadyDone = await isPrAlreadyDone(orderData.subSales.shukran_pr_successful, status);
    console.log('Step 1: PR call status check result', { 
      incrementId, 
      isPrCallAlreadyDone, 
      shukran_pr_successful: orderData.subSales.shukran_pr_successful,
      currentStatus: status 
    });
    
    console.log('Step 2: Checking conditions for Shukran PR call', { 
      incrementId,
      isPrCallAlreadyDone,
      resStatus,
      shukranEnable: baseConfig?.shukranEnable,
      isDelivered,
      paymentMethod: orderData?.paymentInformation[0]?.method
    });
    console.log('shukran pr data to be processed ', incrementId, ' ', isPrCallAlreadyDone, ' ', resStatus, ' ', baseConfig?.shukranEnable, ' ', isDelivered, ' ', orderData?.paymentInformation[0]?.method);
    
    if (!isPrCallAlreadyDone && resStatus && baseConfig?.shukranEnable && isDelivered) {
      console.log('Step 3: All conditions met, proceeding with Shukran flow', { incrementId });

      const shukranOnShipmentLevel = baseConfig?.shukranOnShipmentLevel ?? false;
      const isSplitOrder = isSplitOrderPattern(incrementId);
      console.log(`${logPrefix} Step 3: Feature flag and split order check`, {
        incrementId,
        shukranOnShipmentLevel,
        isSplitOrder
      });

      if (isSplitOrder && shukranOnShipmentLevel) {
        // Split order – handle Shukran split order logic
        return await handleShukranSplitOrderLogic({
          incrementId,
          shukranOnShipmentLevel,
          resStatus,
          resError
        });
      }
      // Normal flow – non-split order (or split order with feature flag off)
      console.log(`${logPrefix} Step 3: Normal flow – proceeding with Shukran PR call for non-split order`, { incrementId });
      await shukranTransactionCreate({ incrementId });
      return { status: resStatus, errorMsg: resError };
    } else {
      console.log('Step 3: Conditions not met for Shukran PR call, skipping', { 
        incrementId,
        isPrCallAlreadyDone,
        resStatus,
        shukranEnable: baseConfig?.shukranEnable,
        isDelivered
      });
    }
    
    // Handle RTO and Cancelled cases for split orders
    const isRto = notificationId === ORDER_RTO || notificationId === String(ORDER_RTO);
    const isCancelled = notificationId === ORDER_CANCELLED || notificationId === String(ORDER_CANCELLED);
    const isRtoOrCancelled = isRto || isCancelled;
    
    if (isRtoOrCancelled && resStatus && baseConfig?.shukranEnable) {
      console.log(`${logPrefix} RTO/Cancelled: Checking conditions for Shukran PR call`, {
        incrementId,
        notificationId,
        isRto,
        isCancelled,
        resStatus,
        shukranEnable: baseConfig?.shukranEnable
      });
      
      // Handle Shukran RTO/Cancelled split order logic
      // Function now handles all checks internally (shukranEnable, shukranOnShipmentLevel, etc.)
      await handleShukranRtoCancelledLogic(incrementId);
    }
    
    if (
      resStatus &&
      orderData.entity_id &&
      orderData.paymentInformation &&
      orderData.paymentInformation.length > 0 &&
      orderData.paymentInformation[0].method &&
      orderData.paymentInformation[0].method === 'cashondelivery' &&
      notificationId &&
      notificationId === 12
    ) {
      await callShukranLockAndUnlock(orderData.entity_id, orderData.increment_id);
      await createCodRtoCreditMemo(orderData.entity_id);
    }
    if (switchCaseVars.callFraudelent) {
      updateFraudulent({
        customerId: orderData.customer_id,
        email: shippingAddress?.email
      });
    }
    return { status: resStatus, errorMsg: resError };
  } catch (e) {
    return { status: false, errorMsg: e.message ? `updateFwdShipment error ${e.message}` : 'updateFwdShipment error' };
  }
};

exports.getShipmentIncId = async orderId => {
  const updateQuery = `SELECT increment_id FROM sales_shipment where order_id="${orderId}"`;

  const queryResponse = await Models.sequelize.query(updateQuery, {
    type: sequelize.QueryTypes.SELECT
  });
  return queryResponse?.[0]?.increment_id;
};

exports.getOrderFwdShipment = async (incrementId) => {
try {
    return await orderObj.getOrder({ incrementId, inclSubSales: true });
  } catch (e) {
    console.error('Error in getOrderFwdShipment', e);
    return null;
  }
};

exports.handleShukranRtoCancelledLogic = handleShukranRtoCancelledLogic;

const isDuplicateReq = ({ notificationId, status, reqStatus }) => {
  if (
    status === STATUS_ID_CODE_MAP[notificationId] &&
    !SKIP_DUPLICATE_CHECK_REQ_STATUS.includes(reqStatus)
  ) {
    return true;
  }
  return false;
};

/**
 * Check for return (RMA) requests for a given order_id
 * @param {number} orderId - The order_id from split_sales_order
 * @returns {Promise<Object|null>} - Return request data if found, null otherwise
 */
const checkReturnForOrderId = async (orderId) => {
  const logPrefix = '[shukranSplitOrderHandling]';
  console.log(`${logPrefix} ========== ENTERING checkReturnForOrderId FUNCTION ==========`, { orderId });
  try {
    console.log(`${logPrefix} checkReturnForOrderId: Starting to check for returns`, { orderId });
    
    // Query amasty_rma_request table for returns with this order_id
    console.log(`${logPrefix} checkReturnForOrderId: Querying amasty_rma_request table`, { orderId });
    const returnQuery = `
      SELECT request_id, order_id, status, rma_inc_id
      FROM amasty_rma_request 
      WHERE order_id = :orderId
      LIMIT 1
    `;
    
    const returnResult = await Models.sequelize.query(returnQuery, {
      replacements: { orderId },
      type: QueryTypes.SELECT
    });
    
    console.log(`${logPrefix} checkReturnForOrderId: Query result`, { 
      orderId, 
      resultCount: returnResult?.length || 0,
      result: returnResult 
    });
    
    if (!returnResult || returnResult.length === 0) {
      console.log(`${logPrefix} checkReturnForOrderId: No return found for order_id`, { orderId });
      return null;
    }
    
    const returnData = returnResult[0];
    const returnStatus = returnData.status;
    console.log(`${logPrefix} checkReturnForOrderId: Return found`, { 
      orderId, 
      returnStatus, 
      rma_inc_id: returnData.rma_inc_id,
      request_id: returnData.request_id
    });
    
    // Return the return data regardless of status - status will be checked in main logic
    console.log(`${logPrefix} checkReturnForOrderId: Returning return data`, { 
      orderId, 
      returnStatus, 
      rma_inc_id: returnData.rma_inc_id 
    });
    return returnData;
  } catch (error) {
    console.error(`${logPrefix} checkReturnForOrderId: Error checking for returns`, { 
      orderId, 
      error: error.message,
      stack: error.stack 
    });
    global.logError('Error in checkReturnForOrderId', error);
    return null;
  }
};

/**
 * Fetch all split shipments for a parent order
 * @param {string} currentIncrementId - The current split shipment increment_id
 * @returns {Promise<Array>} - Array of all split shipments for the parent order
 */
const fetchAllSplitShipmentsForParentOrder = async (currentIncrementId) => {
  const logPrefix = '[shukranSplitOrderHandling]';
  console.log(`${logPrefix} ========== ENTERING fetchAllSplitShipmentsForParentOrder FUNCTION ==========`, { currentIncrementId });
  try {
    console.log(`${logPrefix} fetchAllSplitShipmentsForParentOrder: Starting to fetch split shipments`, { currentIncrementId });
    
    // Step 1: Find the current split order record to get order_id
    console.log(`${logPrefix} fetchAllSplitShipmentsForParentOrder: Step 1 - Querying split_sales_order for current increment_id`, { currentIncrementId });
    const currentSplitOrderQuery = `
      SELECT order_id, entity_id, increment_id, status 
      FROM split_sales_order 
      WHERE increment_id = :currentIncrementId 
      LIMIT 1
    `;
    
    const currentSplitOrderResult = await Models.sequelize.query(currentSplitOrderQuery, {
      replacements: { currentIncrementId },
      type: QueryTypes.SELECT
    });
    
    console.log(`${logPrefix} fetchAllSplitShipmentsForParentOrder: Step 1 - Current split order query result`, { 
      currentIncrementId, 
      resultCount: currentSplitOrderResult?.length || 0,
      result: currentSplitOrderResult 
    });
    
    if (!currentSplitOrderResult || currentSplitOrderResult.length === 0) {
      console.log(`${logPrefix} fetchAllSplitShipmentsForParentOrder: No split order found for current increment_id`, { currentIncrementId });
      return [];
    }
    
    const parentOrderId = currentSplitOrderResult[0].order_id;
    console.log(`${logPrefix} fetchAllSplitShipmentsForParentOrder: Step 2 - Found parent order_id`, { 
      currentIncrementId, 
      parentOrderId 
    });
    
    // Step 2: Find all split shipments for this parent order_id
    console.log(`${logPrefix} fetchAllSplitShipmentsForParentOrder: Step 3 - Querying split_sales_order for all shipments with parent order_id`, { parentOrderId });
    const allSplitShipmentsQuery = `
      SELECT entity_id, increment_id, order_id, status, shipment_mode
      FROM split_sales_order 
      WHERE order_id = :parentOrderId
      ORDER BY increment_id
    `;
    
    const allSplitShipmentsResult = await Models.sequelize.query(allSplitShipmentsQuery, {
      replacements: { parentOrderId },
      type: QueryTypes.SELECT
    });
    
    console.log(`${logPrefix} fetchAllSplitShipmentsForParentOrder: Step 3 - All split shipments query result`, { 
      parentOrderId, 
      shipmentCount: allSplitShipmentsResult?.length || 0,
      shipments: allSplitShipmentsResult 
    });
    
    if (!allSplitShipmentsResult || allSplitShipmentsResult.length === 0) {
      console.log(`${logPrefix} fetchAllSplitShipmentsForParentOrder: No split shipments found for parent order_id`, { parentOrderId });
      return [];
    }
    
    console.log(`${logPrefix} fetchAllSplitShipmentsForParentOrder: Successfully fetched all split shipments`, { 
      currentIncrementId,
      parentOrderId,
      totalShipments: allSplitShipmentsResult.length,
      shipments: allSplitShipmentsResult.map(s => ({ 
        increment_id: s.increment_id, 
        status: s.status,
        shipment_mode: s.shipment_mode 
      }))
    });
    
    return allSplitShipmentsResult;
  } catch (error) {
    console.error(`${logPrefix} fetchAllSplitShipmentsForParentOrder: Error fetching split shipments`, { 
      currentIncrementId, 
      error: error.message,
      stack: error.stack 
    });
    global.logError('Error in fetchAllSplitShipmentsForParentOrder', error);
    return [];
  }
};


exports.updateShipment = async (incrementId,status) => {
  try {
    console.log("incrementId", incrementId);
    console.log("status", status);
    const orderType = incrementId.includes('-') ? 'split' : 'parent';
    let orderData;
    if (orderType === 'split') {
      const queryResponse = await Models.sequelize.query(
        'select customer_email, customer_id, order_id, quote_id,status from split_sales_order where increment_id = ?',
        {
          replacements: [incrementId],
          type: QueryTypes.SELECT
        }
      );
      orderData = queryResponse[0];
    } else {
      const queryResponse = await Models.sequelize.query(
        'SELECT customer_email, customer_id, quote_id,entity_id,status from sales_order where increment_id = ?',
        {
          replacements: [incrementId],
          type: QueryTypes.SELECT
        }
      );
      orderData = queryResponse[0];
    }

    if (!orderData) {
      console.error(`No data found for order ${incrementId}`);
      return null;
    }
console.log("orderData", orderData);
    const orderDetails = getOrderDetailsByStatus(status);
    const body = [{
      "splitOrderId": orderType === 'split' ? incrementId : null,
      "parentOrderId": orderType === 'split' ? orderData?.order_id : orderData.entity_id,
      "incrementId": incrementId,
      "customerId": orderData?.customer_id,
      "quoteId": orderData?.quote_id,
      "customerEmail": orderData?.customer_email,
      "statusMessage": [
        {
          statusId: orderDetails?.code || null,
          message: orderDetails?.message || orderData?.status,
          timestamp: getCurrentTimestamp()
        }]
    }];

    
    console.log('body for forward shipment', body);
    const meta = await PubSubService.publishMessage(ORDER_TRACK_HISTORY, body);
    console.log("meta name something else", meta);
    return meta;
  } catch (e) {
    console.error('Error in updateShipment', e);
    return null;
  }
}

const isPrAlreadyDone = async (shukranPrSuccessful, status) => {
  let isDone = false;
  if (shukranPrSuccessful && shukranPrSuccessful === 1 && status === 'delivered') {
    isDone = true;
  }
  return isDone;
}