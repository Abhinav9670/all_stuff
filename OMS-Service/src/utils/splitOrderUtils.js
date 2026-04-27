/**
 * Utility functions for split order handling
 */

const normalizeStatus = (s) => (s || '').toString().trim().toLowerCase();

/** Statuses that mean the order/split/seller order is cancelled; excluded from "all delivered" and slowest-status logic */
const CANCELLED_STATUSES = new Set([
  'order_cancelled',
  'order cancelled',
  'canceled',
  'cancel',
  'cancelled',
  'seller_cancelled',
  'seller cancelled'
]);

const isCancelledStatus = (status) => {
  const normalized = normalizeStatus(status);
  if (!normalized) return false;
  return CANCELLED_STATUSES.has(normalized) || normalized.includes('cancel');
};

/** Terminal/closed seller order statuses that don't need to be "delivered" for main order to go delivered */
const TERMINAL_SELLER_STATUSES = new Set(['closed', 'complete', 'canceled', 'cancelled', 'order_cancelled', 'order cancelled']);

const isTerminalOrClosedSellerStatus = (status) => {
  const normalized = normalizeStatus(status);
  if (!normalized) return false;
  return TERMINAL_SELLER_STATUSES.has(normalized) || isCancelledStatus(status);
};

/**
 * Derive "slowest" split status based on DB-configured status progression.
 * Uses `sales_order_status_state.step` (already used across OMS) instead of a static rank table.
 *
 * Lower `step` => earlier / slower status.
 */
const deriveSlowestStatus = async ({ sequelize, QueryTypes, splitOrders }) => {
  if (!splitOrders || splitOrders.length === 0) return 'processing';

  const statuses = Array.from(
    new Set(splitOrders.map((so) => normalizeStatus(so?.status)).filter(Boolean))
  );

  if (statuses.length === 0) return 'processing';

  // Pull the smallest step for each status (some DBs may have multiple rows per status+state).
  const rows = await sequelize.query(
    `SELECT status, MIN(step) AS step
     FROM sales_order_status_state
     WHERE status IN (:statuses)
     GROUP BY status`,
    {
      replacements: { statuses },
      type: QueryTypes.SELECT
    }
  );

  const stepByStatus = new Map(
    (rows || []).map((r) => [normalizeStatus(r.status), Number(r.step)])
  );

  const getStep = (status) => {
    const key = normalizeStatus(status);
    // If a status is unknown to `sales_order_status_state`, treat it as earliest (slowest).
    const step = stepByStatus.get(key);
    return Number.isFinite(step) ? step : -Infinity;
  };

  const firstStatus = normalizeStatus(splitOrders[0]?.status) || 'processing';
  return splitOrders.reduce((slowest, so) => {
    const next = normalizeStatus(so?.status) || 'processing';
    return getStep(next) < getStep(slowest) ? next : slowest;
  }, firstStatus);
};

const fetchSellerOrdersForMainOrder = async ({ sequelize, QueryTypes, mainOrderId }) => {
  const defaultWarehouseId = global?.baseConfig?.defaultWarehouseId || "110";
  console.log('defaultWarehouseId', defaultWarehouseId);
  return await sequelize.query(
    `SELECT entity_id, increment_id, status, shipment_mode, split_order_id, main_order_id
     FROM split_seller_order
     WHERE (main_order_id = :mainOrderId
        OR split_order_id IN (SELECT entity_id FROM split_sales_order WHERE order_id = :mainOrderId))
        AND (warehouse_id IS NULL OR warehouse_id != :defaultWarehouseId)
     ORDER BY shipment_mode, entity_id`,
    {
      replacements: { mainOrderId, defaultWarehouseId },
      type: QueryTypes.SELECT
    }
  );
};

/**
 * Check if increment_id matches split order pattern
 * Patterns: 000365475-L-1, 000365475-L1, 000365475-G-1, 000365475-G1
 * @param {string} incrementId - The order increment ID
 * @returns {boolean} - True if it matches split order pattern
 */
const isSplitOrderPattern = (incrementId) => {
  if (!incrementId || typeof incrementId !== 'string') {
    return false;
  }
  
  // Regex pattern to match: XXXXXX-L-X, XXXXXX-LX, XXXXXX-G-X, XXXXXX-GX
  // where XXXXXX is any number sequence and X is any number
  const splitPattern = /^\d+-[LG]-?\d+$/;
  
  return splitPattern.test(incrementId);
};

const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms));

const splitOrderDataFetch = async (orderId) => {
  const { SplitSalesOrder, SplitSalesOrderItem } = require('../models/seqModels');
  let dataVal = [];

  for (let attempt = 1; attempt <= 3; attempt++) {

    dataVal = await SplitSalesOrder.findAll({
      where: { order_id: orderId },
      attributes: ['increment_id', 'entity_id'],
      include: [
        {
          model: SplitSalesOrderItem,
          as: 'SplitSalesOrderItems',
          attributes: ['estimated_delivery_date'],
          where: { product_type: 'simple' },
          required: true
        }
      ]
    });

    if (dataVal && dataVal.length > 0) {
      break; // data found, so break the loop
    }

    console.log(`Attempt ${attempt} failed. Waiting 4 seconds...`);
    await delay(4000); // ⏳ 4 sec wait before next attempt
  }
  let splitOrderValues = {};
  (dataVal ?? []).slice(0, 2).forEach((order, index) => {
    splitOrderValues[`increment${index + 1}`] = order.increment_id;

    const estDate = order.SplitSalesOrderItems?.[0]?.estimated_delivery_date;
    splitOrderValues[`est${index + 1}`] = estDate
      ? new Date(estDate).toDateString()
      : null;
  });
  return {splitOrderValues, dataVal};
};

const isGlobal = (incrementId) => {
  if (!incrementId || typeof incrementId !== 'string') {
    return false;
  }
  
  // Regex pattern to match: XXXXXX-L-X, XXXXXX-LX, XXXXXX-G-X, XXXXXX-GX
  // where XXXXXX is any number sequence and X is any number
  const splitPattern = /^\d+-[LG]-?\d+$/;
  const globalSplitPattern = /^\d+-G-?\d+$/;
  
  return splitPattern.test(incrementId) && globalSplitPattern.test(incrementId);
};

const extractBaseIncrementId = (incrementId) => {
  if (!incrementId || typeof incrementId !== 'string') {
    return '';
  }

  const match = incrementId.match(/^(\d+)-[LG]-?\d+$/);
  return match ? match[1] : '';
};

/**
 * Get the appropriate models based on increment_id pattern
 * @param {string} incrementId - The order increment ID
 * @returns {object} - Object containing the models to use
 */
const getModelsForOrder = (incrementId) => {
  const {
    OrderItem,
    OrderPayment,
    SubSalesOrder,
    SplitSellerOrderItem,
    SplitSellerOrderPayment,
    SplitSubSellerOrder
  } = require('../models/seqModels/index');

  const isSplitOrder = isSplitOrderPattern(incrementId);

  if (isSplitOrder) {
    return {
      OrderItem: SplitSellerOrderItem,
      OrderPayment: SplitSellerOrderPayment,
      SubSalesOrder: SplitSubSellerOrder,
      isSplitOrder: true,
      itemAlias: 'SplitSellerOrderItems',
      paymentAlias: 'SplitSellerOrderPayments',
      subSalesAlias: 'SplitSubSellerOrders'
    };
  } else {
    return {
      OrderItem,
      OrderPayment,
      SubSalesOrder,
      isSplitOrder: false,
      itemAlias: 'OrderItems',
      paymentAlias: 'OrderPayments',
      subSalesAlias: 'SubSalesOrders'
    };
  }
};

/**
 * Build include array for SplitSalesOrder queries
 * @param {boolean} inclSubSales - Whether to include sub sales orders
 * @returns {array} - Array of include objects for Sequelize query
 */
const buildSplitSalesOrderIncludeArray = (inclSubSales = false) => {
  const {
    Shipment,
    OrderAddress,
    Creditmemo,
    SplitSalesOrderItem,
    SplitSalesOrderPayment,
    SplitSubSalesOrder
  } = require('../models/seqModels/index');
  
  const include = [
    { model: Shipment, as: 'Shipments' },
    { model: OrderAddress, as: 'OrderAddresses' },
    { model: SplitSalesOrderItem, as: 'SplitSalesOrderItems' },
    { model: Creditmemo, as: 'Creditmemos' },
    { model: SplitSalesOrderPayment, as: 'SplitSalesOrderPayments' }
  ];

  if (inclSubSales) {
    include.push({
      model: SplitSubSalesOrder,
      as: 'SplitSubSalesOrders'
    });
  }

  return include;
};

/**
 * Build include array with appropriate models based on increment_id
 * @param {string} incrementId - The order increment ID
 * @param {boolean} inclSubSales - Whether to include sub sales orders
 * @returns {array} - Array of include objects for Sequelize query
 */
const buildSplitAwareIncludeArray = (incrementId, inclSubSales = false) => {
  const {
    Shipment,
    OrderAddress,
    Creditmemo
  } = require('../models/seqModels/index');

  const models = getModelsForOrder(incrementId);
  
  const include = [
    { model: Shipment, as: 'Shipments' },
    { model: OrderAddress, as: 'OrderAddresses' },
    { model: models.OrderItem, as: models.itemAlias },
    { model: Creditmemo, as: 'Creditmemos' },
    { model: models.OrderPayment, as: models.paymentAlias }
  ];

  if (inclSubSales) {
    include.push({
      model: models.SubSalesOrder,
      as: models.subSalesAlias
    });
  }

  return include;
};

/**
 * Process split sales order data
 * @param {object} orderInfo - The raw order data from database
 * @returns {object} - Processed order data
 */
const processSplitSalesOrderData = (orderInfo) => {
  if (!orderInfo) return orderInfo;

  const shipments = orderInfo?.Shipments?.map(
    shipment => shipment.dataValues
  );

  // Use split sales order aliases
  const items = orderInfo?.SplitSalesOrderItems?.map(item => item.dataValues);
  const paymentInformation = orderInfo?.SplitSalesOrderPayments?.map(item => item.dataValues);
  const subSales = orderInfo?.SplitSubSalesOrders?.[0]?.dataValues;

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
  orderInfo.paymentInformation = paymentInformation;
  orderInfo.shippingAddress = shippingAddress;
  orderInfo.simpleItems = simpleItems;
  orderInfo.configItems = configItems;
  orderInfo.creditMemo = creditMemo?.dataValues;
  orderInfo.subSales = subSales;

  // Clean up the original associations
  delete orderInfo.Creditmemos;
  delete orderInfo.SplitSubSalesOrders;
  delete orderInfo.SplitSalesOrderItems;
  delete orderInfo.SplitSalesOrderPayments;

  return orderInfo;
};

/**
 * Process order data based on whether it's a split order or regular order
 * @param {object} orderInfo - The raw order data from database
 * @param {boolean} isSplitOrder - Whether this is a split order
 * @returns {object} - Processed order data
 */
const processSplitOrderData = (orderInfo, isSplitOrder) => {
  if (!orderInfo) return orderInfo;

  const shipments = orderInfo?.Shipments?.map(
    shipment => shipment.dataValues
  );

  let items, paymentInformation, subSales;

  if (isSplitOrder) {
    // Use split order aliases
    items = orderInfo?.SplitSellerOrderItems?.map(item => item.dataValues);
    paymentInformation = orderInfo?.SplitSellerOrderPayments?.map(item => item.dataValues);
    subSales = orderInfo?.SplitSubSellerOrders?.[0]?.dataValues;
  } else {
    // Use regular order aliases
    items = orderInfo?.OrderItems?.map(item => item.dataValues);
    paymentInformation = orderInfo?.OrderPayments?.map(item => item.dataValues);
    subSales = orderInfo?.SubSalesOrders?.[0]?.dataValues;
  }

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
  orderInfo.paymentInformation = paymentInformation;
  orderInfo.shippingAddress = shippingAddress;
  orderInfo.simpleItems = simpleItems;
  orderInfo.configItems = configItems;
  orderInfo.creditMemo = creditMemo?.dataValues;
  orderInfo.subSales = subSales;

  // Clean up the original associations
  delete orderInfo.Creditmemos;
  delete orderInfo.SubSalesOrders;
  delete orderInfo.SplitSubSellerOrders;
  delete orderInfo.OrderItems;
  delete orderInfo.SplitSellerOrderItems;
  delete orderInfo.OrderPayments;
  delete orderInfo.SplitSellerOrderPayments;
  delete orderInfo.SplitSalesOrderItems;
  delete orderInfo.SplitSalesOrderPayments;

  return orderInfo;
};

/**
 * Sync main order status based on split orders.
 *
 * Business rule (per OMS expectations):
 * - Main order status should reflect the "slowest" (least advanced) split order status.
 *   Example: local=delivered, global=packed => main should be packed.
 *
 * Safety rule:
 * - Only set main order to "delivered" when BOTH:
 *   1) all split orders are delivered, AND
 *   2) all seller orders are delivered (prevents cross-mode seller updates via DB cascades).
 *
 * @param {string} splitIncrementId - The split order increment ID that was just updated
 * @returns {Promise<boolean>} - True if main order was updated, false otherwise
 */
const checkAndUpdateMainOrderStatus = async (splitIncrementId) => {
  try {
    const { ORDER_DELIVERED_STATUS_CODE, ORDER_STATE_COMPLETE } = require('../constants/order');
    const { SplitSalesOrder, Order, OrderGrid, sequelize, QueryTypes } = require('../models/seqModels/index');
    const currentSplitOrder = await SplitSalesOrder.findOne({
      where: { increment_id: splitIncrementId }
    });
    
    if (!currentSplitOrder) {
      console.log(`Split order not found: ${splitIncrementId}`);
      return false;
    }
    
    const mainOrderId = currentSplitOrder.order_id;
    
    // Load all split orders for the parent, then compute the least advanced status.
    const allSplitOrders = await SplitSalesOrder.findAll({
      where: { order_id: mainOrderId },
      raw: true
    });

    if (!allSplitOrders || allSplitOrders.length === 0) {
      console.log(`[checkAndUpdateMainOrderStatus] No split orders found for main order ${mainOrderId}`);
      return false;
    }

    // Exclude cancelled split orders so main can go to delivered when other splits are delivered
    const activeSplitOrders = allSplitOrders.filter((so) => !isCancelledStatus(so?.status));
    const splitOrdersForStatus = activeSplitOrders.length > 0 ? activeSplitOrders : allSplitOrders;

    const derivedStatus = await deriveSlowestStatus({ sequelize, QueryTypes, splitOrders: splitOrdersForStatus });

    console.log(
      `[checkAndUpdateMainOrderStatus] Main order ${mainOrderId} split statuses:`,
      allSplitOrders.map((so) => ({ increment_id: so.increment_id, status: so.status, shipment_mode: so.shipment_mode }))
    );
    console.log(`[checkAndUpdateMainOrderStatus] Derived (slowest) main status: "${derivedStatus}"`);

    // If derived status is delivered, ensure non-terminal seller orders (not cancelled/closed) are delivered before updating main order.
    if (derivedStatus === normalizeStatus(ORDER_DELIVERED_STATUS_CODE)) {
      const sellerOrders = await fetchSellerOrdersForMainOrder({ sequelize, QueryTypes, mainOrderId });
      // Exclude terminal/closed seller orders (closed, cancelled, complete) – they don't block main from delivered
      const activeSellerOrders = (sellerOrders || []).filter((so) => !isTerminalOrClosedSellerStatus(so?.status));

      const allSellerDelivered =
        activeSellerOrders.length === 0 ||
        activeSellerOrders.every((so) => normalizeStatus(so.status) === normalizeStatus(ORDER_DELIVERED_STATUS_CODE));

      if (!allSellerDelivered) {
        console.log(
          `[checkAndUpdateMainOrderStatus] NOT updating main order ${mainOrderId} to delivered because some seller orders are not delivered:`,
          activeSellerOrders
            .filter((so) => normalizeStatus(so.status) !== normalizeStatus(ORDER_DELIVERED_STATUS_CODE))
            .map((so) => ({ increment_id: so.increment_id, status: so.status, shipment_mode: so.shipment_mode }))
        );
        return false;
      }
    }
    const mainOrder = await Order.findOne({
      where: { entity_id: mainOrderId },
      attributes: ['entity_id', 'status', 'state'],
      raw: true
    });

    const currentMainStatus = normalizeStatus(mainOrder?.status);
    if (currentMainStatus === derivedStatus) {
      console.log(`[checkAndUpdateMainOrderStatus] Main order ${mainOrderId} already at status "${derivedStatus}" - no update needed`);
      return false;
    }

    const nextState =
      derivedStatus === normalizeStatus(ORDER_DELIVERED_STATUS_CODE)
        ? ORDER_STATE_COMPLETE
        : (mainOrder?.state || 'processing');

    const updatePayload = { status: derivedStatus, state: nextState };

    const updateResult = await Order.update(updatePayload, {
      where: { entity_id: mainOrderId }
    });

    // Keep grid in sync if present.
    try {
      await OrderGrid.update({ status: derivedStatus }, { where: { entity_id: mainOrderId } });
    } catch (e) {
      // Ignore if OrderGrid doesn't have the record
    }

    console.log(
      `[checkAndUpdateMainOrderStatus] Updated main order ${mainOrderId}: ${mainOrder?.status} -> ${derivedStatus} (state: ${mainOrder?.state} -> ${nextState})`
    );

    return updateResult?.[0] > 0;
  } catch (error) {
    console.error('[checkAndUpdateMainOrderStatus] Error:', error);
    return false;
  }
};

module.exports = {
  isSplitOrderPattern,
  getModelsForOrder,
  buildSplitAwareIncludeArray,
  buildSplitSalesOrderIncludeArray,
  processSplitOrderData,
  processSplitSalesOrderData,
  checkAndUpdateMainOrderStatus,
  splitOrderDataFetch,
  isGlobal,
  extractBaseIncrementId
}; 