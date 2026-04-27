const {
  RMA_REFUND_ENDPOINT,
  RMA_TABBY_REFUND_ENDPOINT,
  REFUND_LIST_ENDPOINT,
  REFUND_STATUS_UPDATE_ENDPINIT,
  CREATE_COD_RTO_CREDITMEMO,
  LOCK_UNLOCK_SHUKRAN_POINTS
} = require('../constants/javaEndpoints');
const { AUTH_INTERNAL_HEADER_BEARER_TOKEN } = process.env;
const internalAuthToken = AUTH_INTERNAL_HEADER_BEARER_TOKEN?.split(',')?.[0];
const axios = require('axios');
const { logInfo } = require('../utils');
const { queueBasedDropOff } = require('../kafka/producer/queuebased.dropoff');
const { SubSalesOrder, SplitSalesOrder, SplitSalesOrderItem, sequelize, QueryTypes } = require('../models/seqModels/index');
const {updateShukranLedger}= require('../utils/easApi');
const { isSplitOrderPattern } = require('../utils/splitOrderUtils');
const { ORDER_RTO_STATUS_CODE } = require('../constants/order');

exports.callRefund = async ({ returnIncrementId, orderId }) => {
  await queueBasedDropOff(returnIncrementId, orderId);
  return {};
};

exports.callRefundold = async ({ returnIncrementId, orderId }) => {
  const body = { returnIncrementId, orderId };

  try {
    logInfo(
      `Java api refund call  RMA IncrementId :  ${returnIncrementId} , orderId: ${orderId}`,
      body
    );

    const response = await axios.post(RMA_REFUND_ENDPOINT, body, {
      headers: {
        'authorization-token': internalAuthToken
      }
    });
    const { data } = response;
    const { status, statusCode, statusMsg, sendSms, refundUrl } = data;
    logInfo(
      `Java api refund call RESPONSE RMA IncrementId :  ${returnIncrementId} , orderId: ${orderId}`,
      data
    );
    if (!status || ![200, '200'].includes(statusCode)) {
      return {
        status: false,
        errorMsg: `response from java API: ${statusMsg}`
      };
    }
    return { status: true, sendSms, refundUrl };
  } catch (e) {
    global.logError(e);
    return {
      status: false,
      errorMsg: `${returnIncrementId || orderId} Error from java API: ${
        e?.response?.data?.message || e.message
      }`
    };
  }
};

exports.tabbyRefund = async ({ orderId }) => {
  const body = { orderId };

  try {
    logInfo(`Java api Tabby refund call  orderId: ${orderId}`, body);

    const response = await axios.post(RMA_TABBY_REFUND_ENDPOINT, body, {
      headers: {
        'authorization-token': internalAuthToken
      }
    });
    const { data } = response;
    const { status, statusCode, statusMsg } = data;
    logInfo(`Java api tabby refund call RESPONSE , orderId: ${orderId}`, data);
    if (!status || ![200, '200'].includes(statusCode)) {
      return {
        status: false,
        errorMsg: `response from tabby java API: ${statusMsg}`
      };
    }
    return { status: true };
  } catch (e) {
    global.logError(e);
    return {
      status: false,
      errorMsg: `${orderId} Error from tabby java API: ${
        e?.response?.data?.message || e.message
      }`
    };
  }
};

exports.hasExistingRefund = async ({ orderId, incrementId, waybill }) => {
  const body = {
    incrementIds: [incrementId],
    status: [],
    offset: 0,
    pageSize: 1,
    awb: waybill,
    orderId
  };

  try {
    logInfo(`Checking existing refund for incrementId: ${incrementId}, awb: ${waybill}`, body);
    const response = await axios.post(REFUND_LIST_ENDPOINT, body, {
      headers: {
        'authorization-token': internalAuthToken
      }
    });
    const refunds =
      response?.data?.data ||
      response?.data?.refunds ||
      response?.data?.list ||
      [];
    return Array.isArray(refunds) && refunds.length > 0;
  } catch (e) {
    global.logError?.(e) || console.error(e);
    return false;
  }
};

exports.callRtoRefund = async ({ orderId, incrementId, waybill, paymentMethod }) => {
  const body = {
    incrementIds: [incrementId],
    status: [],
    offset: 0,
    pageSize: 0,
    awb: waybill,
    paymentMethod,
    idempotencyKey: `${incrementId}:${waybill}:rto`
  };

  try {
    logInfo(
      `RTO refund trigger for incrementId : ${incrementId} , orderId: ${orderId}, awb: ${waybill}`,
      body
    );

    const response = await axios.post(REFUND_STATUS_UPDATE_ENDPINIT, body, {
      headers: {
        'authorization-token': internalAuthToken
      }
    });

    const { data } = response || {};
    const status = data?.status ?? response?.status;
    if (status && [200, '200', true, 'true'].includes(status)) {
      return { status: true, refundUrl: data?.refundUrl };
    }

    return {
      status: false,
      errorMsg: data?.statusMsg || 'Failed to trigger RTO refund'
    };
  } catch (e) {
    global.logError?.(e) || console.error(e);
    return {
      status: false,
      errorMsg: `${incrementId || orderId} Error from RTO refund API: ${
        e?.response?.data?.message || e.message
      }`
    };
  }
};

exports.createCodRtoCreditMemo = async entityId => {
  const response = await axios.post(CREATE_COD_RTO_CREDITMEMO, {
    orderId: entityId
  });
  return response;
};

exports.callShukranLockAndUnlock = async (orderId, incrementId) => {
  try {
    let subSalesOrder = await SubSalesOrder.findOne({
      where: { order_id: orderId },
      attributes: [
        'id',
        'total_shukran_coins_burned',
        'customer_profile_id',
        'quote_id',
        'shukran_locked'
      ]
    });
    if (!subSalesOrder) {
      throw new Error('Order not found');
    }

    subSalesOrder = JSON.parse(JSON.stringify(subSalesOrder));
    
    // Fetch baseConfig and check feature flags
    const { baseConfig = {} } = global;
    const shukranEnable = baseConfig?.shukranEnable ?? false;
    const shukranOnShipmentLevel = baseConfig?.shukranOnShipmentLevel ?? false;
    
    // Check if this is a split order
    const isSplitOrder = incrementId && isSplitOrderPattern(incrementId);
    let pointsToUnlock = subSalesOrder.total_shukran_coins_burned;
    
    // If split order and both feature flags enabled, calculate Shukran points only for RTO shipment items
    if (isSplitOrder && shukranEnable && shukranOnShipmentLevel) {
      console.log(`[callShukranLockAndUnlock] Split order detected with feature flags enabled, calculating Shukran points for RTO shipment only`, {
        orderId,
        incrementId,
        shukranEnable,
        shukranOnShipmentLevel
      });
      
      try {
        // Step 1: Find split_sales_order record with status = 'rto' for this order_id
        const rtoSplitOrder = await SplitSalesOrder.findOne({
          where: {
            order_id: orderId,
            status: ORDER_RTO_STATUS_CODE
          },
          attributes: ['entity_id', 'increment_id', 'status']
        });
        
        if (rtoSplitOrder && rtoSplitOrder.entity_id) {
          console.log(`[callShukranLockAndUnlock] Found RTO split order`, {
            orderId,
            entity_id: rtoSplitOrder.entity_id,
            increment_id: rtoSplitOrder.increment_id,
            status: rtoSplitOrder.status
          });
          
          // Step 2: Query split_sales_order_item for items in this RTO shipment
          const rtoItemsQuery = `
            SELECT SUM(shukran_coins_burned) as total_burned_points
            FROM split_sales_order_item
            WHERE split_order_id = :splitOrderId
              AND product_type = 'simple'
          `;
          
          const rtoItemsResult = await sequelize.query(rtoItemsQuery, {
            replacements: { splitOrderId: rtoSplitOrder.entity_id },
            type: QueryTypes.SELECT
          });
          
          const calculatedPoints = rtoItemsResult?.[0]?.total_burned_points 
            ? parseFloat(rtoItemsResult[0].total_burned_points) 
            : 0;
          
          console.log(`[callShukranLockAndUnlock] Calculated Shukran points for RTO shipment`, {
            orderId,
            splitOrderEntityId: rtoSplitOrder.entity_id,
            calculatedPoints: calculatedPoints,
            originalTotalBurned: subSalesOrder.total_shukran_coins_burned
          });
          
          if (calculatedPoints > 0) {
            pointsToUnlock = calculatedPoints;
          } else {
            console.log(`[callShukranLockAndUnlock] No Shukran points found for RTO shipment items, using original total`, {
              orderId,
              splitOrderEntityId: rtoSplitOrder.entity_id
            });
          }
        } else {
          console.log(`[callShukranLockAndUnlock] No RTO split order found for order_id, using original total`, {
            orderId,
            incrementId
          });
        }
      } catch (splitOrderError) {
        console.error(`[callShukranLockAndUnlock] Error calculating split order Shukran points, using original total`, {
          orderId,
          incrementId,
          error: splitOrderError.message
        });
        global.logError('Error calculating split order Shukran points', splitOrderError);
        // Fallback to original total_shukran_coins_burned
      }
    } else if (isSplitOrder && (!shukranEnable || !shukranOnShipmentLevel)) {
      console.log(`[callShukranLockAndUnlock] Split order detected but feature flags disabled, using original total_shukran_coins_burned`, {
        orderId,
        incrementId,
        shukranEnable,
        shukranOnShipmentLevel
      });
    }
    
    const isShukranCoinsAvailable = pointsToUnlock && Number(pointsToUnlock) > 0;
    const isShukranLocked = subSalesOrder.shukran_locked === "0" || subSalesOrder.shukran_locked === 0;
    
    if (
      isShukranCoinsAvailable &&
      isShukranLocked
    ) {
      console.log(`[callShukranLockAndUnlock] Unlocking Shukran points`, {
        orderId,
        incrementId,
        isSplitOrder,
        pointsToUnlock: pointsToUnlock,
        originalTotalBurned: subSalesOrder.total_shukran_coins_burned
      });
      
      const response = await axios.post(
        LOCK_UNLOCK_SHUKRAN_POINTS,
        {
          profileId: subSalesOrder.customer_profile_id,
          points: '' + pointsToUnlock,
          cartId: subSalesOrder.quote_id,
          isLock: false
        },
        {
          headers: {
            'authorization-token': internalAuthToken
          }
        }
      );
      if (response.status) {
        await SubSalesOrder.update(
          { shukran_locked: 1 },
          { where: { id: subSalesOrder.id } }
        );
        await updateShukranLedger(
          incrementId,
          pointsToUnlock,
          true, 
          'Refunded Shukran Burned Points For Rto'
        );
      }
    } else {
      console.log(`[callShukranLockAndUnlock] Skipping unlock - conditions not met`, {
        orderId,
        incrementId,
        isShukranCoinsAvailable,
        isShukranLocked,
        pointsToUnlock
      });
    }
    return true;
  } catch (e) {
    global.logError(e);
  }
};