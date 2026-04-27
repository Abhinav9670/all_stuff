const {
  Order,
  OrderAddress,
  OrderComment,
  OrderPayment,
  ProxyOrder,
  SubSalesOrder
} = require('../models/seqModels/index');
const { Op } = require('sequelize');
const { ORDER_REFUNDED_STATUS_CODE } = require('../constants/order');
const dot = require('dot-object');
const moment = require('moment');
const { setGTEMatch, setLTEMatch } = require('./sequalizeFilters');
const {
  map,
  difference,
  forEach,
  uniq,
  forOwn,
  find,
  last
} = require('lodash');
const { handleLogs, pushProxyEntry } = require('./smsHelper');
const { logInfo, getNumericValue } = require('../utils');
const { getStoreWebsiteIdMap } = require('../utils/config');
const { getLifetimeOrders } = require('./utilities');

exports.updatePreviousComment = async ({ orderId }) => {
  return await OrderComment.findOne({
    where: {
      parent_id: orderId,
      comment: { [Op.like]: '%We refunded%' }
    },
    order: [['entity_id', 'DESC']]
  }).then(function (record) {
    if (record) record.update({ status: ORDER_REFUNDED_STATUS_CODE });
  });
};

exports.copyOrderDataToCreditMemo = ({ orderData, creditMemo }) => {
  const orderObject = dot.object(orderData);
  creditMemo.orderCreatedAt = orderObject.createdAt;
  creditMemo.orderIncrementId = orderObject.increment_id;
  creditMemo.shippingAddress = orderObject.OrderAddresses;
  creditMemo.customerEmail = orderObject.customer_email;

  const orderpaymentObject = orderObject?.OrderPayments || {};
  const additionalInformationObject = JSON.parse(
    orderpaymentObject.additional_information || '{}'
  );
  const paymentInformation = {
    paymentMethod: orderpaymentObject.method,
    ccNumber: additionalInformationObject.card_number,
    ccType: additionalInformationObject.payment_option
  };
  creditMemo.paymentInformation = paymentInformation;

  const subOrderObject = orderObject?.SubSalesOrders || {};
  creditMemo.warehouseId = subOrderObject.warehouse_id;
};

const getProxyOrdersFilterObj = () => {
  const startOffset =
    global.baseConfig?.smsConfig?.failureSMSJourney?.startOffsetInHrs || 24;
  const endOffset =
    global.baseConfig?.smsConfig?.failureSMSJourney?.endOffsetInMinutes || 30;

  logInfo('getProxyOrdersFilterObj', { startOffset, endOffset });
  const startDate = moment().subtract(startOffset, 'hours').toDate();
  const endDate = moment().subtract(endOffset, 'minutes').toDate();
  let where = { status: 'payment_failed' };
  where = setGTEMatch(where, 'created_at', startDate);
  where = setLTEMatch(where, 'created_at', endDate);

  return { where, raw: true };
};

const getOrdersFilterObj = () => {
  const startOffset =
    global.baseConfig?.smsConfig?.failureSMSJourney?.startOffsetInHrs || 24;
  const endOffset =
    global.baseConfig?.smsConfig?.failureSMSJourney?.endOffsetInMinutes || 30;

  logInfo('getOrdersFilterObj', { startOffset, endOffset });
  const startDate = moment().subtract(startOffset, 'hours').toDate();
  const endDate = moment().subtract(endOffset, 'minutes').toDate();
  let filterSO = {};
  filterSO = setGTEMatch(filterSO, 'created_at', startDate);
  filterSO = setLTEMatch(filterSO, 'created_at', endDate);
  return {
    where: filterSO,
    include: [
      { model: SubSalesOrder, raw: true },
      { model: OrderAddress, where: { address_type: 'shipping' }, raw: true },
      { model: OrderPayment }
    ]
  };
};

exports.getCustomersDataWithFailedProxyOrders2 = async () => {
  try {
    const proxyOrders = await ProxyOrder.findAll(getProxyOrdersFilterObj());
    const proxyQuoteIds = uniq(map(proxyOrders, el => String(el.quote_id)));
    const orders = await Order.findAll(getOrdersFilterObj());
    let quoteIdsFromSubSalesOrder = [];
    const orderStatusMap = {};
    const quoteOrderMapFromSubSalesOrder = {};
    forEach(orders, el => {
      const { SubSalesOrders, OrderAddresses, OrderPayments, status } = el;
      const sso = SubSalesOrders?.[0]?.dataValues;
      const ao = OrderAddresses?.[0]?.dataValues;
      const paymentMethod = OrderPayments?.[0]?.dataValues?.method?.toLowerCase();
      quoteIdsFromSubSalesOrder.push(String(sso.external_quote_id));
      // console.log({ status, quoteId: sso.external_quote_id });
      orderStatusMap[el.entity_id] = {
        email: ao.email,
        name: ao.firstname,
        status,
        phone: ao.telephone,
        storeId: Number(el.store_id),
        amount: getNumericValue(
          Number(el.grand_total || 0) + Number(el.amstorecredit_amount || 0)
        ),
        currency: el.order_currency_code,
        incrementId: el.increment_id,
        mode: paymentMethod,
        failedAt: el.updated_at,
        quoteId: sso.external_quote_id
      };
      quoteOrderMapFromSubSalesOrder[`${sso.external_quote_id}`] = [
        ...(quoteOrderMapFromSubSalesOrder[`${sso.external_quote_id}`] || []),
        el.entity_id
      ];
    });
    quoteIdsFromSubSalesOrder = uniq(quoteIdsFromSubSalesOrder);

    const quoteIdsWhoQuitAfterTabbyTamara = difference(
      proxyQuoteIds,
      quoteIdsFromSubSalesOrder
    );

    const finalArrayForNonProxyUsers = [];
    forOwn(quoteOrderMapFromSubSalesOrder, (v, key) => {
      const orderOtherThanFailed = find(
        v,
        el => orderStatusMap[el]?.status != 'payment_failed'
      );
      if (!orderOtherThanFailed)
        finalArrayForNonProxyUsers.push(orderStatusMap[last(v)]);
    });

    const finalArrayForProxyUsers = [];
    forEach(proxyOrders, el => {
      if (quoteIdsWhoQuitAfterTabbyTamara.includes(el.quote_id)) {
        pushProxyEntry(el, finalArrayForProxyUsers);
      }
    });

    handleLogs({
      proxyQuoteIds,
      quoteIdsFromSubSalesOrder,
      quoteIdsWhoQuitAfterTabbyTamara,
      quoteOrderMapFromSubSalesOrder,
      orderStatusMap,
      finalArrayForNonProxyUsers,
      finalArrayForProxyUsers
    });

    return { finalArrayForNonProxyUsers, finalArrayForProxyUsers };
  } catch (e) {
    console.log(e.message);
    return {};
  }
};

const getCustomerOrderList = async ({ customerEmail, storeId }) => {
  try {
    const websiteId = getStoreWebsiteIdMap()?.[storeId];
    const r = await getLifetimeOrders({ customerEmail, websiteId });
    return r?.responseList;
  } catch (e) {
    global.logError(
      `Error fetching customer previous orderList ${JSON.stringify({
        customerEmail,
        storeId: Number(storeId)
      })}`,
      e
    );
  }
  return [];
};

exports.getFirstOrder = async ({ customerEmail, storeId }) => {
  const allOrders = await getCustomerOrderList({ customerEmail, storeId });
  allOrders.sort(function (left, right) {
    return moment.utc(left.createdAt).diff(moment.utc(right.createdAt));
  });

  return allOrders?.[0];
};
