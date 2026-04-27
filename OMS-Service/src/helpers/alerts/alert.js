const { Order, sequelize } = require('../../models/seqModels/index');
const { Op, QueryTypes } = require('sequelize');
const isEmpty = require('lodash.isempty');

const moment = require('moment');
const { sendSgEmail } = require('../../services/email.service');

const RETURN_STATUS = [
  { id: 4, label: 'Auto approved' },
  { id: 15, label: 'Picked up' },
  { id: 19, label: 'Dropped Off' },
  { id: 23, label: 'Under Verification' },
  { id: 25, label: 'Verification failed' }
];

exports.wmsNull = async ({ storeCountryMap, fromEmail, fromName }) => {
  const { count, interval, receiverEmails } =
    global?.baseConfig?.alertConfig?.wmsNull || {};
  const orderMap = {};
  let emailText = '';
  if (interval) {
    const orderResponse = await Order.findAll({
      attributes: ['increment_id', 'store_id', 'wms_status'],
      where: {
        created_at: {
          [Op.gte]: moment().subtract(1440, 'minutes').toDate(),
          [Op.lte]: moment().subtract(Number(interval), 'minutes').toDate()
        },
        wms_status: null,
        status: {
          [Op.notIn]: [
            'payment_failed',
            'closed',
            'payment_hold',
            'pending_payment'
          ]
        }
      }
    });

    orderResponse.forEach(row => {
      const orderCountry = storeCountryMap[Number(row.store_id)];
      if (!orderMap[orderCountry]) {
        orderMap[orderCountry] = [];
      }
      orderMap[orderCountry].push(row.increment_id);
    });

    if (!isEmpty(orderMap)) {
      emailText = '<h2>WMS NULL </h2> <br>';
      for (const country in count) {
        if (orderMap?.[country]?.length >= Number(count[country])) {
          emailText =
            emailText +
            `<b>${country}</b> : ${orderMap?.[country]?.join(',')}` +
            '<br>';
        }
      }
    }
  }

  if (!isEmpty(emailText)) {
    await sendSgEmail({
      to: receiverEmails,
      from: { email: fromEmail, name: fromName },
      subject: 'WMS Status Null Alerts',
      html: emailText
    });
  }
};

exports.fraudCheck = async () => {
  const { fromEmail, fromName } = global?.baseConfig?.emailConfig || {};
  const { receiverEmails, intervalHrs } =
    global?.baseConfig?.alertConfig?.fraudcheck || {};
  let emailText = '';
  const orders = await sequelize.query(
    `SELECT
          status,
          COUNT(*) AS count
      FROM
          amasty_rma_request
      WHERE
          status IN (23, 15, 19, 4, 25)
          AND modified_at >= NOW() - INTERVAL ? HOUR
      GROUP BY status
      ORDER BY status;`,
    {
      replacements: [intervalHrs],
      type: QueryTypes.SELECT
    }
  );
  if (!isEmpty(orders)) {
    emailText = '<h2>Return order analysis in last 24 hrs. </h2> <br>';
    for (const order in RETURN_STATUS) {
      const status = RETURN_STATUS[order];
      const detail = orders?.filter(ord => ord.status === status.id)[0];
      emailText =
        emailText + `<b>${status.label}</b> : ${detail?.count || 0}` + '<br>';
    }
  }

  if (!isEmpty(emailText)) {
    await sendSgEmail({
      to: receiverEmails,
      from: { email: fromEmail, name: fromName },
      subject: 'Return Order Alerts',
      html: emailText
    });
  }
};
