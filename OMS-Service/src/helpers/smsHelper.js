const { logInfo, getNumericValue } = require('../utils');
const { setNotMatch, setGTEMatch } = require('./sequalizeFilters');
const {
  Order,
  ProxyOrder,
  SubSalesOrder
} = require('../models/seqModels/index');
const moment = require('moment');
const { uniq, map, forEach } = require('lodash');

exports.handleLogs = ({
  proxyQuoteIds,
  quoteIdsFromSubSalesOrder,
  quoteIdsWhoQuitAfterTabbyTamara,
  quoteOrderMapFromSubSalesOrder,
  orderStatusMap,
  finalArrayForNonProxyUsers,
  finalArrayForProxyUsers
}) => {
  //   Logs (Keep below lines commented and use while debugging)
  logInfo('proxyQuoteIds', proxyQuoteIds);
  logInfo('proxyQuoteIds count', proxyQuoteIds.length);
  // logInfo('quoteIdsFromSubSalesOrder', quoteIdsFromSubSalesOrder);
  // logInfo('quoteIdsFromSubSalesOrder count', quoteIdsFromSubSalesOrder.length);
  logInfo('quoteIdsWhoQuitAfterTabbyTamara', quoteIdsWhoQuitAfterTabbyTamara);
  logInfo(
    'quoteIdsWhoQuitAfterTabbyTamara count',
    quoteIdsWhoQuitAfterTabbyTamara.length
  );
  // logInfo('quoteOrderMapFromSubSalesOrder', quoteOrderMapFromSubSalesOrder);
  // logInfo('quoteOrderMapFromSubSalesOrder count', quoteOrderMapFromSubSalesOrder.length);
  logInfo('finalArrayForNonProxyUsers', finalArrayForNonProxyUsers);
  logInfo(
    'finalArrayForNonProxyUsers count',
    finalArrayForNonProxyUsers.length
  );
  logInfo('finalArrayForProxyUsers', finalArrayForProxyUsers);
  logInfo('finalArrayForProxyUsers count', finalArrayForProxyUsers.length);
};

exports.pushProxyEntry = (el, finalArrayForProxyUsers) => {
  const proxyQuoteObject = JSON.parse(el.quote);
  if (proxyQuoteObject) {
    finalArrayForProxyUsers.push({
      email: getEmailFromProxyObject(proxyQuoteObject),
      name: proxyQuoteObject.shippingAddress?.firstname,
      status: 'payment_failed',
      phone: proxyQuoteObject.shippingAddress?.mobileNumber,
      storeId: Number(proxyQuoteObject.storeId),
      amount: getNumericValue(
        Number(proxyQuoteObject.grandTotal || 0) +
          Number(proxyQuoteObject.storeCreditApplied || 0)
      ),
      currency: proxyQuoteObject.currency,
      mode: proxyQuoteObject.selectedPaymentMethod,
      failedAt: el.updated_at,
      quoteId: el.quote_id
    });
  }
};

const getEmailFromProxyObject = proxyQuoteObject => {
  return (
    proxyQuoteObject.customerEmail || proxyQuoteObject.shippingAddress?.email
  );
};

exports.getQuoteIdsNotPaymentFailedInDuration = async () => {
  let finalFiterForQuoteIds = [];

  try {
    const startOffset =
      global.baseConfig?.smsConfig?.failureSMSJourney?.endOffsetInMinutes || 30;
    const startDate = moment().subtract(startOffset, 'minutes').toDate();
    let where = setNotMatch({}, 'status', 'payment_failed');
    where = setGTEMatch(where, 'created_at', startDate);

    // Proxy Orders
    const proxyOrders = await ProxyOrder.findAll({ where, raw: true });
    finalFiterForQuoteIds = map(proxyOrders, el => String(el.quote_id));

    // Non-proxy Orders
    const orders = await Order.findAll({
      where,
      include: [{ model: SubSalesOrder, raw: true }]
    });

    forEach(orders, el => {
      const { SubSalesOrders } = el;
      const sso = SubSalesOrders?.[0]?.dataValues;
      finalFiterForQuoteIds.push(String(sso.external_quote_id));
    });
    return uniq(finalFiterForQuoteIds);
  } catch (e) {
    logInfo('Error at getQuoteIdsNotPaymentFailedInDuration' + e.message);
    global.logError(e);
    return [];
  }
};
