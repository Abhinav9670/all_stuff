/* eslint-disable max-lines-per-function */
/* eslint-disable sonarjs/cognitive-complexity */
/* eslint-disable max-lines */
const {
  sequelize,
  Order,
  Shipment,
  RmaRequest,
  RmaTracking
} = require('../../models/seqModels/index');
const { QueryTypes } = require('sequelize');
const { Op } = require('sequelize');
const isEmpty = require('lodash.isempty');

const moment = require('moment');
const { getCountryStoreMap } = require('../../utils/config');
const { sendSgEmail } = require('../../services/email.service');
const { promiseAll } = require('../../utils');

const getEmailText = async ({
  interval,
  intervalEnd,
  count,
  storeCountryMap,
  operator,
  status
}) => {
  const orderMap = {};
  let emailText = '';

  const twentyFourHoursAgo = moment().subtract(24, 'hours').toDate();

  let where = {
    created_at: {
      [operator === '<' ? Op.lte : Op.gte]: moment()
        .subtract(Number(interval), 'minutes')
        .toDate()
    }
  };
  
  if (status.includes('pending_payment')) {
    where = {
      created_at: {
        [Op.lte]: moment().subtract(Number(intervalEnd), 'minutes').toDate(),
        [Op.gte]: moment().subtract(Number(interval), 'minutes').toDate(),
        [Op.gt]: twentyFourHoursAgo
      }
    };
  }
  
  if (status === 'processing') {
    where = {
      created_at: {
        [Op.lte]: moment().subtract(Number(interval), 'minutes').toDate(),
        [Op.gte]: '2021-03-30 23:59:00'
      }
    };
  }
  
  const orderResponse = await Order.findAll({
    attributes: ['increment_id', 'store_id'],
    where: { ...where, status }
  });
  orderResponse.forEach(row => {
    const orderCountry = storeCountryMap[Number(row.store_id)];
    if (!orderMap[orderCountry]) {
      orderMap[orderCountry] = [];
    }
    orderMap[orderCountry].push(row.increment_id);
  });

  for (const country in count) {
    if (orderMap?.[country]?.length >= Number(count[country])) {
      emailText = `${emailText}
            <b>${country}</b> : ${orderMap?.[country]?.join(',')} <br>`;
    }
  }
  const duration = moment.duration(Number(interval), 'minutes');
  return { orderMap, duration, emailText };
};

exports.pendingOrHoldPayment = async ({
  storeCountryMap,
  fromEmail,
  fromName
}) => {
  const { count, intervalStart, receiverEmails, intervalEnd } =
    global?.baseConfig?.alertConfig?.pendingOrHoldPayment || {};

  const { emailText, duration } = await getEmailText({
    interval: intervalStart,
    intervalEnd,
    count,
    storeCountryMap,
    operator: '>',
    status: ['pending_payment', 'payment_hold']
  });
  if (!isEmpty(emailText)) {
    await sendSgEmail({
      to: receiverEmails,
      from: { email: fromEmail, name: fromName },
      subject: `Pending Payment/Payment Hold  Alert for Last ${duration.hours()} hours and ${duration.minutes()} minutes.`,
      html: emailText
    });
  }
};

exports.paymentFailed = async ({ storeCountryMap, fromEmail, fromName }) => {
  const { count, interval, receiverEmails } =
    global?.baseConfig?.alertConfig?.paymentFailed || {};

  const { emailText, duration } = await getEmailText({
    interval,
    count,
    storeCountryMap,
    operator: '>',
    status: ['payment_failed', 'payment_canceled']
  });
  if (!isEmpty(emailText)) {
    await sendSgEmail({
      to: receiverEmails,
      from: { email: fromEmail, name: fromName },
      subject: `Payment Failed/Payment Canceled Alert for Last ${duration.hours()} hours and ${duration.minutes()} minutes.`,
      html: emailText
    });
    console.log({ count, receiverEmails, emailText });
  }
};

const forwardAwbMissing = async ({ interval, storeCountryMap }) => {
  const orderMap = {};
  if (interval) {
    const orderResponse = await Order.findAll({
      attributes: ['increment_id', 'store_id', 'status'],
      where: {
        created_at: {
          [Op.gte]: moment().subtract(Number(interval), 'minutes').toDate(),
          [Op.lte]: moment().toDate()
        },
        status: { [Op.notIn]: ['payment_failed', 'closed'] }
      },
      include: [{ model: Shipment, as: 'Shipments' }]
    });

    orderResponse.forEach(row => {
      const orderCountry = storeCountryMap[Number(row.store_id)];
      if (!orderMap[orderCountry]) {
        orderMap[orderCountry] = [];
      }
      if (row.Shipments.length === 0) {
        orderMap[orderCountry].push(row.increment_id);
      }
    });
  }

  return orderMap;
};

const returnAwbMissing = async ({ interval, storeCountryMap }) => {
  const returnOrderMap = {};
  if (interval) {
    const orderResponse = await RmaRequest.findAll({
      attributes: ['rma_inc_id', 'store_id'],
      where: {
        created_at: {
          [Op.gte]: moment().subtract(Number(interval), 'minutes').toDate(),
          [Op.lte]: moment().toDate()
        }
      },
      include: [{ model: RmaTracking, as: 'RmaTrackings' }]
    });

    orderResponse.forEach(row => {
      const orderCountry = storeCountryMap[Number(row.store_id)];
      if (!returnOrderMap[orderCountry]) {
        returnOrderMap[orderCountry] = [];
      }
      if (row.RmaTrackings.length === 0) {
        returnOrderMap[orderCountry].push(row.rma_inc_id);
      }
    });
  }
  return returnOrderMap;
};

exports.alertAwbMissing = async ({ storeCountryMap, fromEmail, fromName }) => {
  const config = global?.baseConfig?.alertConfig?.awbMissing;
  const { count, interval, receiverEmails , reversecount } = config;
  let emailText = '';
  const forwardOrders = await forwardAwbMissing({
    interval,
    storeCountryMap
  });
  const returnOrders = await returnAwbMissing({ interval, storeCountryMap });
  let emailSend = false;
  if (!isEmpty(forwardOrders)) {
    emailText = '<h2>Forward AWB Missing</h2> <br>';
    for (const country in count) {
      if (forwardOrders?.[country]?.length >= Number(count[country])) {
        emailText =
          emailText +
          `<b>${country}</b> : ${forwardOrders?.[country]?.join(',')}` +
          '<br>';
          emailSend = true;
      }
    }
  }

  if (!isEmpty(returnOrders)) {
    emailText = emailText + '<br><h2>Reverse AWB Missing</h2> <br>';
    for (const country in reversecount) {
      if (returnOrders?.[country]?.length >= Number(reversecount[country])) {
        emailText =
          emailText +
          `<b>${country}</b> : ${returnOrders?.[country]?.join(',')}` +
          '<br>';
          emailSend = true;
      }
    }
  }

  if (emailText && emailSend === true) {
    const duration = moment.duration(Number(interval), 'minutes');
    await sendSgEmail({
      to: receiverEmails,
      from: { email: fromEmail, name: fromName },
      subject: `AWB missing Alert for Last ${duration.hours()} hours and ${duration.minutes()} minutes.`,
      html: emailText
    });
  }
};

exports.alertNoOrder = async ({ storeCountryMap, fromEmail, fromName }) => {
  const config = global?.baseConfig?.alertConfig?.noOrder;
  const { interval, receiverEmails } = config;
  const countryStoreMap = getCountryStoreMap();

  const countPromise = [];
  for (const country in interval) {
    const minutes = Number(interval[country]);
    if (minutes > 0) {
      countPromise.push(
        Order.findAll({
          attributes: [
            [sequelize.fn('count', sequelize.col('increment_id')), 'cnt']
          ],
          where: {
            created_at: {
              [Op.gte]: moment().subtract(minutes, 'minutes').toDate()
            },
            store_id: countryStoreMap[country]
          }
        })
      );
    } else {
      delete interval[country];
    }
  }

  const countPromiseResp = await promiseAll(countPromise);

  const noOrderCountries = Object.keys(interval).reduce((resArr, cn, index) => {
    if (countPromiseResp?.output?.[index]?.[0]?.dataValues.cnt === 0)
      resArr.push(cn);
    return resArr;
  }, []);

  if (noOrderCountries?.length) {
    const emailText =
      'No orders received for following countries : <br>' +
      noOrderCountries.reduce((str, cn) => {
        const duration = moment.duration(Number(interval[cn]), 'minutes');
        str = `${str} ${cn} in ${duration.hours()} hours and ${duration.minutes()} minutes. <br> `;
        return str;
      }, '');

    await sendSgEmail({
      to: receiverEmails,
      from: { email: fromEmail, name: fromName },
      subject: 'No Order Alert',
      html: emailText
    });
  }
};

exports.alertProcessingOrder = async ({
  storeCountryMap,
  fromEmail,
  fromName
}) => {
  const { count, interval, receiverEmails } =
    global?.baseConfig?.alertConfig?.processingOrder || {};
  const { emailText, duration } = await getEmailText({
    interval,
    count,
    storeCountryMap,
    operator: '<',
    status: 'processing'
  });
  if (!isEmpty(emailText)) {
    await sendSgEmail({
      to: receiverEmails,
      from: { email: fromEmail, name: fromName },
      subject: `Processing Order Alert for Last ${duration.hours()} hours and ${duration.minutes()} minutes.`,
      html: emailText
    });
  }
};

exports.alertLessOrderInAnHour = async ({ fromEmail, fromName }) => {
  const config = global?.baseConfig?.alertConfig?.lessOrderForAnHour;
  const { minutes, receiverEmails, thresholds } = config;

  const interval = moment().subtract(minutes, 'minutes').toDate();

  const msiteOrderCount = await getMSiteOrderCount(interval);
  const iosOrderCount = await getIOSOrderCount(interval);
  const androidOrderCount = await getAndroidOrderCount(interval);

  console.log(
    'msiteOrderCount ',
    msiteOrderCount?.[0]?.count,
    'iosOrderCount',
    iosOrderCount?.[0]?.count,
    'androidOrderCount',
    androidOrderCount?.[0]?.count
  );

  if (
    iosOrderCount?.[0]?.count <= thresholds?.ios ||
    msiteOrderCount?.[0]?.count <= thresholds?.msite ||
    androidOrderCount?.[0]?.count <= thresholds?.android
  ) {
    const emailText = `Orders received from :-  <br>
 
            <b>iOS</b>          :   ${iosOrderCount?.[0]?.count} <br>
            <b>Android</b>      :   ${androidOrderCount?.[0]?.count} <br>
            <b>MSite</b>        :   ${msiteOrderCount?.[0]?.count} <br>
            
            <br>
            Min Thresholds are : <br>
            
            <b>iOS Threshold</b>          :   ${thresholds?.ios} <br>
            <b>Android Threshold</b>      :   ${thresholds?.android} <br>
            <b>MSite Threshold</b>        :   ${thresholds?.msite} <br>`;

    await sendSgEmail({
      to: receiverEmails,
      from: { email: fromEmail, name: fromName },
      subject: 'Orders In an Hour Alert',
      html: emailText
    });
  }
};

const getMSiteOrderCount = async interval => {
  return await sequelize.query(
    `SELECT count(increment_id) as count FROM sales_order so LEFT JOIN sub_sales_order sso ON (so.entity_id=sso.order_id) WHERE created_at >= ? AND lower(sso.client_source)=lower('msite')`,
    {
      replacements: [interval],
      type: QueryTypes.SELECT
    }
  );
};

const getIOSOrderCount = async interval => {
  return await sequelize.query(
    `SELECT count(increment_id) as count FROM sales_order so LEFT JOIN sub_sales_order sso ON (so.entity_id=sso.order_id) WHERE created_at >= ? AND lower(sso.client_source)=lower('ios')`,
    {
      replacements: [interval],
      type: QueryTypes.SELECT
    }
  );
};

const getAndroidOrderCount = async interval => {
  return await sequelize.query(
    `SELECT count(increment_id) as count FROM sales_order so LEFT JOIN sub_sales_order sso ON (so.entity_id=sso.order_id) WHERE created_at >= ? AND lower(sso.client_source)=lower('android')`,
    {
      replacements: [interval],
      type: QueryTypes.SELECT
    }
  );
};
