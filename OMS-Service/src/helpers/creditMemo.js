/* eslint-disable max-lines-per-function */
/* eslint-disable max-lines */
const {
  OrderItem,
  Creditmemo,
  CreditmemoItem,
  CreditmemoComment,
  RmaTracking,
  Invoice,
  CreditmemoItemTax,
  SplitSalesOrder,
  SplitSalesOrderItem
} = require('../models/seqModels/index');
const {
  OrderItem : OrderItemArchive,
  Creditmemo : CreditmemoArchive,
  CreditmemoItem : CreditmemoItemArchive,
  CreditmemoComment : CreditmemoCommentArchive,
  RmaTracking : RmaTrackingArchive,
  Invoice : InvoiceArchive,
  CreditmemoItemTax : CreditmemoItemTaxArchive
} = require('../models/seqModels/archiveIndex');
const { getNumericValue, sanitiseImageUrl } = require('../utils');
const moment = require('moment');
const { getProductsBySKU } = require('../services/misc.service');
const { getStoreConfigs } = require('../utils/config');
const {
  paymentMethodsLabels,
  getOrderData
} = require('./order');
const { getKSATime } = require('./moment');
const { copyOrderDataToCreditMemo } = require('./orderOps');
const { getCompanyAddress } = require('./invoice');
const { getCompanyEmail } = require('../utils/pdf');
const {
  earnCreditMemoResponse,
  shukranCreditMemoResponse,
  refundAmount
} = require('./eas/earnIntegration');
const { getGstInPan, getStateCode, getInTaxTypes } = require('./tax');
const { inTaxTypes } = require('../constants');
const {
  getZatcaItemDetails,
  getZatcaTotals,
  roundingTo2Decimal
} = require('./zatca');

/**
 * Split rows use a table PK as `item_id`; Magento credit memo lines use the original
 * `sales_order_item_id`. Align so memo `order_item_id` matches order line lookups.
 */
const canonicalOrderItemIdFromSplitRow = item => {
  const soId = item.sales_order_item_id;
  const n = Number(soId);
  if (soId != null && soId !== '' && Number.isFinite(n) && n > 0) {
    return n;
  }
  return Number(item.item_id);
};

const normalizeSplitSalesOrderItemRow = item => ({
  ...item,
  item_id: canonicalOrderItemIdFromSplitRow(item),
  product_type: item.product_type || 'simple'
});

/**
 * Main order lines + split lines can both reference the same Magento item_id; merge keeps one row
 * (prefer higher qty_refunded, then split row when tied).
 */
const dedupeOrderItemsByItemId = rows => {
  const byId = new Map();
  for (const r of rows || []) {
    const id = Number(r.item_id);
    if (Number.isNaN(id)) continue;
    const prev = byId.get(id);
    if (!prev) {
      byId.set(id, r);
      continue;
    }
    const score = row =>
      (parseFloat(row.qty_refunded) || 0) * 1000 +
      (parseFloat(row.qty_ordered) || 0) +
      (row.split_order_id != null && Number(row.split_order_id) > 0 ? 0.5 : 0);
    byId.set(id, score(r) >= score(prev) ? r : prev);
  }
  return [...byId.values()];
};

/** Coerce memo line PK for Set dedupe (mysql2 / Sequelize may mix string and number). */
const normalizeMemoLineEntityId = raw => {
  if (raw == null || raw === '') return null;
  const n = Number(raw);
  return Number.isFinite(n) ? n : null;
};

/** One row per memo line entity; also drop repeated order_item_id within the same credit memo (bad data / joins). */
const dedupeMemoLinesForCreditMemo = raw => {
  const seenEntity = new Set();
  const seenOrderItem = new Set();
  const out = [];
  for (const m of raw || []) {
    const eid = normalizeMemoLineEntityId(m?.entity_id);
    if (eid != null) {
      if (seenEntity.has(eid)) continue;
      seenEntity.add(eid);
    }
    const oi = m?.order_item_id;
    if (oi != null && oi !== '') {
      const n = Number(oi);
      if (!Number.isNaN(n) && seenOrderItem.has(n)) continue;
      if (!Number.isNaN(n)) seenOrderItem.add(n);
    }
    out.push(m);
  }
  return out;
};

/** Last line of defence: same logical memo row must not appear twice in API items. */
const dedupeMemoResponseItemsByEntityId = items => {
  const seen = new Set();
  const out = [];
  for (const r of items || []) {
    const eid = normalizeMemoLineEntityId(r?.entity_id);
    if (eid != null) {
      if (seen.has(eid)) continue;
      seen.add(eid);
    }
    out.push(r);
  }
  return out;
};

/**
 * Within a single credit memo, two lines for the same SKU are a split-order artefact
 * (main order item + split order item both landed a memo line for the same product).
 * Keep the row with the higher resolved qty; on a tie keep the first (lower entity_id = main order).
 */
const dedupeMemoItemsBySku = items => {
  const bySku = new Map();
  const noSku = [];
  for (const item of items || []) {
    const sku = item?.sku;
    if (!sku) {
      noSku.push(item);
      continue;
    }
    const existing = bySku.get(sku);
    if (!existing) {
      bySku.set(sku, item);
      continue;
    }
    const newQty = parseFloat(item.qty) || 0;
    const existingQty = parseFloat(existing.qty) || 0;
    if (newQty > existingQty) bySku.set(sku, item);
  }
  const kept = new Set(bySku.values());
  // Preserve original insertion order; items without a SKU always pass through
  return (items || []).filter(i => !i.sku || kept.has(i));
};

const memoLineMatchesParentIds = (memoOrderItemId, parentIds) =>
  parentIds.some(pid => Number(pid) === Number(memoOrderItemId));

/**
 * Normalize memo line qty: DB / mysql2 often returns DECIMAL as string "1.000";
 * Sequelize may also pass number or library Decimal-like objects.
 */
const parseMemoQty = el => {
  const raw = el?.qty;
  if (raw == null || raw === '') return 0;
  if (typeof raw === 'number') return Number.isFinite(raw) ? raw : 0;
  if (typeof raw === 'bigint') return Number(raw);
  const s =
    typeof raw === 'string'
      ? raw
      : typeof raw === 'object' &&
          raw !== null &&
          typeof raw.toString === 'function'
        ? raw.toString()
        : String(raw);
  const q = parseFloat(s.trim());
  return Number.isFinite(q) ? q : 0;
};

/** When qty and qty_refunded are 0 in DB but line amounts exist, infer qty = line_total / unit_price. */
const deriveQtyFromMemoAmounts = el => {
  const row =
    parseFloat(el.row_total_incl_tax) ||
    parseFloat(el.row_total) ||
    0;
  const unitPrice =
    parseFloat(el.price_incl_tax) ||
    parseFloat(el.price) ||
    parseFloat(el.base_price) ||
    0;
  if (!Number.isFinite(row) || row <= 0) return 0;
  // Line has a total but no usable unit price (sync / column gaps): assume one unit (common single-SKU refunds).
  if (!Number.isFinite(unitPrice) || unitPrice <= 0) return 1;
  const q = row / unitPrice;
  if (!Number.isFinite(q) || q <= 0) return 0;
  return roundingTo2Decimal(q);
};

/** Prefer split row when main + split share the same Magento item_id (duplicate rows after concat). */
const findOrderLineForMemoItem = (orderItems, orderItemId) => {
  const id = Number(orderItemId);
  const matches = (orderItems || []).filter(e => Number(e.item_id) === id);
  if (!matches.length) return undefined;
  if (matches.length === 1) return matches[0];
  return matches.reduce((best, row) => {
    const q = parseFloat(row.qty_refunded) || 0;
    const bq = parseFloat(best?.qty_refunded) || 0;
    return q > bq ? row : best;
  });
};

/** When memo qty is 0, try order line qty_refunded, then infer from row_total / unit price, then qty_ordered. */
const resolveMemoLineQty = (el, orderItem) => {
  const m = parseMemoQty(el);
  if (m > 0) return m;
  if (orderItem != null) {
    const qr = parseFloat(orderItem.qty_refunded);
    if (Number.isFinite(qr) && qr > 0) return qr;
  }
  const derived = deriveQtyFromMemoAmounts(el);
  if (derived > 0) return derived;
  if (orderItem != null) {
    const qo = parseFloat(orderItem.qty_ordered);
    if (Number.isFinite(qo) && qo > 0) return qo;
  }
  return m;
};

const getItems = async ({
  memoItems,
  itemIds,
  getProductDetailsFromMulin,
  orderItems,
  status,
  paymentMethod
}) => {
  const items = [];
  for (const el of memoItems) {
    if (!memoLineMatchesParentIds(el.order_item_id, itemIds)) continue;
    const orderItem = findOrderLineForMemoItem(
      orderItems,
      el.order_item_id
    );
    const itemObject = el;
    for (const key in getProductDetailsFromMulin) {
      const item = getProductDetailsFromMulin[key];
      const variant = item.variants.find(variant => variant.sku === el.sku);
      if (variant) {
        const imageUrl = (item?.media_gallery || [])[0]?.value;
        itemObject.imageUrl = sanitiseImageUrl(imageUrl);
      }
    }
    if (process.env?.REGION?.toUpperCase() === 'IN') {
      const creditMemoItemTax = await CreditmemoItemTax.findAll({
        where: { sales_creditmemo_item_id: el.entity_id }
      });
      itemObject.taxObjects = creditMemoItemTax;
    }

    const finalQty = resolveMemoLineQty(itemObject, orderItem);
    const zatca = getZatcaItemDetails({
      itemObject: { ...itemObject, qty: finalQty },
      orderItem,
      status,
      paymentMethod
    });
    items.push({
      ...itemObject,
      ...zatca,
      qty: finalQty
    });
  }
  return dedupeMemoItemsBySku(dedupeMemoResponseItemsByEntityId(items));
};

exports.getCreditMemos = async ({ orderId }) => {
  let useArchive = false;
  const order = await getOrderData({orderId, includeSubOrder: true, useArchive});
  const paymentMethod = order?.["OrderPayments.method"] || '';

  const creditMemos = await Creditmemo.findAll({
    where: { order_id: orderId },
    include: [{ model: CreditmemoItem }, { model: CreditmemoComment }]
  });
  if (!creditMemos.length) return { error: 'Credit Memo(s) not found!' };
  const mainOrderItems = await OrderItem.findAll({
    where: { order_id: orderId },
    raw: true
  });

  // For split orders, items live in split_sales_order_item; include them so credit memo items resolve
  let orderItems = (mainOrderItems || []).slice();
  const splitOrders = await SplitSalesOrder.findAll({
    where: { order_id: orderId },
    attributes: ['entity_id'],
    raw: true
  });
  if (splitOrders?.length) {
    const splitEntityIds = splitOrders.map(so => so.entity_id).filter(Boolean);
    const splitItems = await SplitSalesOrderItem.findAll({
      where: { split_order_id: splitEntityIds },
      raw: true
    });
    // Normalize to same shape as OrderItem (item_id, sku, product_type) for getItems/getParentIds
    const normalizedSplitItems = (splitItems || []).map(
      normalizeSplitSalesOrderItemRow
    );
    orderItems = orderItems.concat(normalizedSplitItems);
  }

  orderItems = dedupeOrderItemsByItemId(orderItems);

  const parentItemIds = getParentIds(orderItems);
  const skus = [
    ...new Set(
      (orderItems || [])
        .filter(i => i.product_type === 'simple')
        .map(el => el.sku)
        .filter(Boolean)
    )
  ];
  const getProductDetailsFromMulin = await getProductsBySKU({ skus });

  const memos = creditMemos?.map(memo => memo.dataValues);
  const response = [];
  for (const memo of memos) {
    if (process.env?.REGION?.toUpperCase() === 'IN') {
      memo.taxTitles = getInTaxTypes();
    }
    const { CreditmemoItems, CreditmemoComments, ...rest } = memo;

    const refundedAmount = refundAmount(memo);

    let formattedDate = undefined;
    if (memo.created_at) formattedDate = getKSATime(memo.created_at);
    const memoItemsRaw = CreditmemoItems?.map(item => item.dataValues) || [];
    const memoItems = dedupeMemoLinesForCreditMemo(memoItemsRaw);
    const memoComments = CreditmemoComments?.map(comment => comment.dataValues);
    const comments = [];
    memoComments.forEach(el => {
      comments.push({
        ...el,
        created_at: getKSATime(el.created_at)
      });
    });

    const memoOrderItemIds = (memoItems || [])
      .map(m => m.order_item_id)
      .filter(id => id != null && id !== '');
    const itemIdsForMemo = [
      ...new Set([...(parentItemIds || []), ...memoOrderItemIds])
    ];
    const items = await getItems({
      typeReturn: memo.rma_number,
      memoItems,
      itemIds: itemIdsForMemo,
      getProductDetailsFromMulin,
      orderItems,
      status: order?.status,
      paymentMethod
    });

    let showTax = true;
    const configValue = getStoreConfigs({
      key: 'taxPercentage',
      storeId: memo?.store_id
    });
    if (configValue.length) {
      const taxPercentage = configValue[0].taxPercentage;
      if (!taxPercentage || taxPercentage === 0) showTax = false;
    }
    const voucherAmount = items.reduce(
      (n, { voucher_amount }) => n + parseFloat(voucher_amount),
      0
    );
    const grand_total = Number(rest.grand_total) || 0;
    const amstorecredit_amount = Number(rest.amstorecredit_amount) || 0;
    const eas_value_in_currency = Number(rest.eas_value_in_currency) || 0;
    const currencyConversionRate =
      Number(
        getStoreConfigs({
          key: 'currencyConversionRate',
          storeId: memo?.store_id
        })?.[0]?.currencyConversionRate
      ) || 1;
    const zatcaTotals = getZatcaTotals(items);
    const roundingPayableAmount = roundingTo2Decimal(
      grand_total +
        amstorecredit_amount +
        eas_value_in_currency -
        zatcaTotals.zatcaTotalInclTax
    );
    const zatcaRefundedAmount = grand_total;
    const base_zatcaRefundedAmount = roundingTo2Decimal(
      zatcaRefundedAmount * currencyConversionRate
    );
    response.push({
      ...rest,
      voucherAmount: getNumericValue(voucherAmount),
      refundedAmount: refundedAmount,
      items,
      comments,
      showTax,
      created_at: formattedDate,
      ...zatcaTotals,
      roundingPayableAmount,
      zatcaRefundedAmount,
      base_zatcaRefundedAmount,
      currencyConversionRate
    });
  }

  return { error: false, response };
};

exports.getCreditMemo = async ({
  creditMemo,
  entity_id,
  includeOrder = false,
  includeSubOrder = false,
  includeRmaTracking = false,
  includeInvoice = false,
  useArchive = false
}) => {


  if (!creditMemo) {
    creditMemo = useArchive ? await CreditmemoArchive.findOne({
      where: { entity_id: entity_id },
      raw: true,
    }) : await Creditmemo.findOne({
      where: { entity_id: entity_id },
      raw: true,
    })
  
    if (!creditMemo) {
      return { error: "Credit Memo not found!" };
    }
  }

  // Include split order item ids so credit memo items resolve for split orders
  const parentItemIds = await getParentItemIdsIncludingSplit(creditMemo.order_id, useArchive);
  const creditMemoItems = useArchive ? await CreditmemoItemArchive.findAll({
    where: { parent_id: creditMemo.entity_id },
    raw: true
  }) : await CreditmemoItem.findAll({
    where: { parent_id: creditMemo.entity_id },
    raw: true
  });

  const filteredItems = await getFilteredItems({
    creditMemoItems,
    parentItemIds,
    useArchive
  });
  creditMemo.items = filteredItems;
  if (process.env?.REGION?.toUpperCase() === 'IN') {
    creditMemo.taxTitles = getInTaxTypes();
  }

  const creditMemoComments = useArchive ?  await CreditmemoCommentArchive.findAll({
    where: { parent_id: creditMemo.entity_id },
    raw: true
  }) : await CreditmemoComment.findAll({
    where: { parent_id: creditMemo.entity_id },
    raw: true
  });
  creditMemo.comments = creditMemoComments;
  creditMemo.voucherAmount = filteredItems.reduce(
    (n, { voucher_amount }) => n + parseFloat(voucher_amount),
    0
  );

  if (includeOrder) {
    const orderData = await getOrderData({
      orderId: creditMemo.order_id,
      includeSubOrder,
      useArchive
    });
    if (orderData) copyOrderDataToCreditMemo({ orderData, creditMemo });
  }
  if (includeRmaTracking && creditMemo.rma_number) {
    const rmaTracking = useArchive ?  await RmaTrackingArchive.findOne({
      where: { request_id: creditMemo.rma_number },
      raw: true
    }) : await RmaTracking.findOne({
      where: { request_id: creditMemo.rma_number },
      raw: true
    });
    if (rmaTracking) {
      creditMemo.rmaTrackingCode = rmaTracking.tracking_code;
      creditMemo.rmaTrackingNumber = rmaTracking.tracking_number;
    }
  }
  if (includeInvoice) {
    const invoice = useArchive ?  await InvoiceArchive.findOne({
      where: { order_id: creditMemo.order_id },
      raw: true
    }) : await Invoice.findOne({
      where: { order_id: creditMemo.order_id },
      raw: true
    });
    creditMemo.invoice = invoice;
  }
  return { error: false, response: creditMemo };
};

const prepareResponse = (response, currency, factor) => {
  const subtotal_incl_tax = Number(response.subtotal_incl_tax)
    ? `${getNumericValue(response.subtotal_incl_tax)}`
    : '0.00';
  const base_subtotal_incl_tax = getNumericValue(subtotal_incl_tax * factor);
  const subtotal = Number(response.subtotal)
    ? `${getNumericValue(response.subtotal)}`
    : '0.00';
  const base_subtotal = getNumericValue(subtotal * factor);
  const discount_amount = Number(response.discount_amount)
    ? `${getNumericValue(response.discount_amount)}`
    : undefined;
  const base_discount_amount = getNumericValue(discount_amount * factor);
  const adjustment =
    Number(response.adjustment) && !response.rma_number
      ? `${getNumericValue(-1 * response.adjustment)}`
      : undefined;
  const base_adjustment = getNumericValue(adjustment * factor);
  const shipping_amount = Number(response.shipping_amount)
    ? `${getNumericValue(response.shipping_amount)}`
    : undefined;
  const base_shipping_amount = getNumericValue(shipping_amount * factor);
  const tax_amount = Number(response.tax_amount)
    ? `${getNumericValue(response.tax_amount)}`
    : '0.00';
  const amstorecredit_amount = Number(response.amstorecredit_amount)
    ? `${getNumericValue(response.amstorecredit_amount)}`
    : undefined;
  const grand_total = Number(response.grand_total)
    ? `${getNumericValue(response.grand_total)}`
    : '0.00';
  const base_grand_total = getNumericValue(grand_total * factor);
  const cash_on_delivery_fee =
    Number(response.cash_on_delivery_fee) && !response.rma_number
      ? `${getNumericValue(response.cash_on_delivery_fee)}`
      : undefined;
  const base_cash_on_delivery_fee = getNumericValue(
    cash_on_delivery_fee * factor
  );
  const refunded = refundAmount(response);
  const base_refunded = getNumericValue(refunded * factor);
  const refundedAmount = Number(refunded)
    ? `${getNumericValue(refunded)}`
    : '0.00';
  const base_refundedAmount = getNumericValue(refundedAmount * factor);

  return {
    subtotal_incl_tax,
    base_subtotal_incl_tax,
    subtotal,
    base_subtotal,
    cash_on_delivery_fee,
    base_cash_on_delivery_fee,
    grand_total,
    base_grand_total,
    amstorecredit_amount,
    tax_amount,
    shipping_amount,
    base_shipping_amount,
    adjustment,
    base_adjustment,
    discount_amount,
    base_discount_amount,
    refundedAmount,
    base_refundedAmount,
    factor
  };
};

exports.prepareCreditMemo = async ({ response, showTax = true, factor, status, paymentData, useArchive = false }) => {
  const {
    paymentInformation = {},
    warehouseId,
    store_id: storeId,
    order_id: orderId
  } = response;

  const companyEmail = getCompanyEmail({ warehouseId, storeId });
  const { ccNumber, ccType, paymentMethod } = paymentInformation;
  const currency = response.order_currency_code;
  const formattedResponse = prepareResponse(response, currency, factor);
  const earnResponse = earnCreditMemoResponse(response, currency);
  const shukranResponse= shukranCreditMemoResponse(response,currency);
  response = { ...response, ...formattedResponse, ...earnResponse, ...shukranResponse };
  response.isUaeOrKsa = [1, 3, 7, 11].includes(storeId);
  response.isUae = [7, 11].includes(storeId);
  response.isKsa = [1, 3].includes(storeId);
  response.isExcludeKsa = [7, 11, 13, 12, 15, 17, 19, 21, 51, 23, 25].includes(storeId);

  let orderItems = useArchive ? await OrderItemArchive.findAll({
    where: { order_id: orderId },
    raw: true
  }) : await OrderItem.findAll({
    where: { order_id: orderId },
    raw: true
  });
  orderItems = orderItems || [];

  if (!useArchive) {
    const splitOrders = await SplitSalesOrder.findAll({
      where: { order_id: orderId },
      attributes: ['entity_id'],
      raw: true
    });
    if (splitOrders?.length) {
      const splitEntityIds = splitOrders.map(so => so.entity_id).filter(Boolean);
      const splitItems = await SplitSalesOrderItem.findAll({
        where: { split_order_id: splitEntityIds },
        raw: true
      });
      const normalizedSplitItems = (splitItems || []).map(
        normalizeSplitSalesOrderItemRow
      );
      orderItems = orderItems.concat(normalizedSplitItems);
    }
  }

  orderItems = dedupeOrderItemsByItemId(orderItems);

  if (!paymentMethod.includes('tabby') && ccType) response.ccType = ccType;
  if (!paymentMethod.includes('tabby') && ccNumber)
    response.ccNumber = ccNumber.slice(ccNumber.length - 6);
  if (paymentMethod.includes('cashfree') && ccNumber) response.ccNumber = '';

  response.paymentModeEn = paymentMethodsLabels[paymentMethod]?.en;
  response.paymentModeAr = paymentMethodsLabels[paymentMethod]?.ar;
  response.companyEmail = companyEmail;
  response.invoiceCreatedAt = response.orderCreatedAt
    ? getKSATime(response?.invoice?.created_at)
    : 'N/A';
  response.createdAt = response.created_at
    ? getKSATime(response.created_at)
    : 'N/A';
  response.orderIncrementId = response.orderIncrementId || 'N/A';
  response.incrementId = response.increment_id || 'N/A';
  const items = setResponseItem({
    response,
    items: response.items,
    showTax,
    orderItems,
    status, paymentMethod: paymentData
  });
  // Match getCreditMemos / getItems: PDF must not list duplicate SKU rows (split-order artefact).
  const displayItems = dedupeMemoItemsBySku(
    dedupeMemoResponseItemsByEntityId(items)
  );
  response.items = displayItems;

  const { shippingAddress = {} } = response;
  const addressObject = {
    ...shippingAddress,
    firstName: shippingAddress.firstname,
    lastName: shippingAddress.lastname,
    streetAddress: shippingAddress.street,
    mobileNumber: shippingAddress.telephone,
    country: shippingAddress.country_id
  };
  const carrierCodes = global?.baseConfig?.configs?.carrierCodes;
  const rmaTrackingCode = carrierCodes.find(
    cc => cc.code === String(response.rmaTrackingCode)
  )?.label;

  const { addressEn, addressAr } = getCompanyAddress(response.warehouseId);
  response.companyAddressEn = addressEn;
  response.companyAddressAr = addressAr;
  const grand_total = Number(response.grand_total);
  const amstorecredit_amount = response.amstorecredit_amount
    ? Number(response.amstorecredit_amount)
    : 0;

  const eas_value_in_currency = response.eas_value_in_currency
    ? Number(response.eas_value_in_currency)
    : 0;
  // Must use same rows as response.items; summing pre-dedupe items double-counts ZATCA totals.
  const zatcaTotals = getZatcaTotals(displayItems);
  const shukranValueInCurrency = shukranResponse.shukranValueInCurrency || 0;
  let roundingPayableAmount = roundingTo2Decimal(
    grand_total +
      amstorecredit_amount +
      eas_value_in_currency -
      zatcaTotals.zatcaTotalInclTax
  );
  if(roundingPayableAmount && Math.abs(roundingPayableAmount) > 0.03){
    roundingPayableAmount = (Math.abs(roundingPayableAmount)- shukranValueInCurrency)
  }
  const zatcaRefundedAmount = grand_total;
  const base_zatcaRefundedAmount = roundingTo2Decimal(zatcaRefundedAmount * Number(factor));
  response = {
    ...response,
    shippingAddress: addressObject,
    rmaTrackingCode,
    roundingPayableAmount,
    zatcaRefundedAmount,
    base_zatcaRefundedAmount,
    ...zatcaTotals
  };
  
  response.showTax = showTax;
  response.currencyConversionRate = factor;
  if (process.env?.REGION?.toUpperCase() === 'IN') {
    const { gstIN, panNo } = getGstInPan(response.warehouseId);
    response.gstIN = gstIN;
    response.panNo = panNo;
    response.shippingAddress.stateCode = getStateCode(
      response.shippingAddress.region
    );
    response.warehouseIdStateCode = global?.taxConfig?.warehouseIdStateCode;
  }
 

  return response;
};

const getParentIds = orderItems => {
  if (process.env?.REGION?.toUpperCase() === 'IN') {
    const ids = (orderItems || [])
      .filter(i => i.product_type === 'simple')
      .map(el => el.item_id);
    return ids;
  }
  const ids = (orderItems || [])
    .filter(i => i.product_type === 'configurable' || (i.product_type === 'simple' && !i.parent_item_id))
    .map(el => el.item_id);
  return ids;
};

/**
 * Get parent item ids for credit memo item filtering, including split order items.
 * For split orders, line items live in split_sales_order_item; without this, items would be empty.
 */
const getParentItemIdsIncludingSplit = async (orderId, useArchive = false) => {
  const mainOrderItems = useArchive
    ? await OrderItemArchive.findAll({ where: { order_id: orderId }, raw: true })
    : await OrderItem.findAll({ where: { order_id: orderId }, raw: true });
  if (useArchive) return getParentIds(mainOrderItems || []);

  const splitOrders = await SplitSalesOrder.findAll({
    where: { order_id: orderId },
    attributes: ['entity_id'],
    raw: true
  });
  if (!splitOrders?.length) return getParentIds(mainOrderItems || []);

  const splitEntityIds = splitOrders.map(so => so.entity_id).filter(Boolean);
  const splitItems = await SplitSalesOrderItem.findAll({
    where: { split_order_id: splitEntityIds },
    raw: true
  });
  const normalizedSplitItems = (splitItems || []).map(
    normalizeSplitSalesOrderItemRow
  );
  const merged = dedupeOrderItemsByItemId(
    (mainOrderItems || []).concat(normalizedSplitItems)
  );
  return getParentIds(merged);
};

const setResponseItem = ({ response, items, showTax, orderItems, status, paymentMethod }) => {
  return (items || []).map(el => {
    const orderItem = findOrderLineForMemoItem(orderItems, el.order_item_id);
    const memoQty = resolveMemoLineQty(el, orderItem);
    const itemForZatca = { ...el, qty: memoQty };
    return {
      ...el,
      showTax,
      isUaeOrKsa: response.isUaeOrKsa,
      subTotal: getNumericValue(Number(el.price_incl_tax)),
      price_incl_tax: `${getNumericValue(el.price_incl_tax)}`,
      qty: memoQty,
      tax_amount: Number(el.tax_amount)
        ? `${getNumericValue(el.tax_amount)}`
        : '0.00',
      row_total_incl_tax: Number(el.row_total_incl_tax)
        ? `${getNumericValue(
            el.row_total_incl_tax - Number(el.discount_amount)
          )}`
        : '0.00',
      voucher_amount: Number(el.voucher_amount)
        ? `${getNumericValue(el.voucher_amount)}`
        : '0.00',
      ...getZatcaItemDetails({
        itemObject: itemForZatca,
        orderItem,
        status,
        paymentMethod
      })
    };
  });
};

const getFilteredItems = async ({ creditMemoItems, parentItemIds, useArchive = false }) => {
  const memoIds = (creditMemoItems || [])
    .map(c => c.order_item_id)
    .filter(id => id != null && id !== '');
  const allowedIds = [...new Set([...(parentItemIds || []), ...memoIds])];
  const filteredItems = [];
  const dedupedMemo = dedupeMemoLinesForCreditMemo(creditMemoItems);
  dedupedMemo.forEach(el => {
    if (memoLineMatchesParentIds(el.order_item_id, allowedIds))
      filteredItems.push(el);
  });
  if (process.env?.REGION?.toUpperCase() === 'IN') {
    for (const cItem of filteredItems) {
      const creditMemoItemTax = useArchive ?  await CreditmemoItemTaxArchive.findAll({
        where: { sales_creditmemo_item_id: cItem.entity_id },
        raw: true
      }) : await CreditmemoItemTax.findAll({
        where: { sales_creditmemo_item_id: cItem.entity_id },
        raw: true
      });
      cItem.taxObjects = inTaxTypes.map(v => {
        const taxobj = creditMemoItemTax.find(t => t.tax_type == v);
        return {
          taxAmount: Number(taxobj.tax_amount)
            ? `${getNumericValue(taxobj.tax_amount)}`
            : '0.00',
          taxPercentage: Number(taxobj.tax_percentage)
            ? `${getNumericValue(taxobj.tax_percentage)}`
            : '0.00'
        };
      });
    }
  }
  return filteredItems;
};
