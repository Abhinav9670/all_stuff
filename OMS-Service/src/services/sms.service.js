const httpStatus = require('http-status');
const { forEach, map } = require('lodash');
const { STORE_LANG_MAP } = require('../constants');
const Handlebars = require('handlebars');
const { sendSgEmail } = require('./email.service');
const templateMap = getTemplate();
const fs = require('fs');
const mongoUtil = require('../utils/mongoInit');
const {
  getCustomersDataWithFailedProxyOrders2
} = require('../helpers/orderOps');

const { replaceAllString, logInfo } = require('../utils');
const moment = require('moment');
const { sendKaleyraSMS } = require('./misc.service');
const ApiError = require('../utils/ApiError');
const { frontendURLBasedOnStoreId } = require('../utils/config');
const {
  getQuoteIdsNotPaymentFailedInDuration
} = require('../helpers/smsHelper');

// const isLessThan24HourAgo = date => {
//   const twentyFourHrInMs = 24 * 60 * 60 * 1000;
//   const twentyFourHoursAgo = Date.now() - twentyFourHrInMs;
//   return date > twentyFourHoursAgo;
// };

const nonProxyTemplate = () => {
  return (
    global.baseConfig?.smsConfig?.failureSMSJourney?.templates
      ?.nonProxyOrdersFailed || {}
  );
};

const proxyTemplate = () => {
  return (
    global.baseConfig?.smsConfig?.failureSMSJourney?.templates
      ?.proxyOrdersFailed || {}
  );
};

const getEmailData = () => {
  return (
    global.baseConfig?.smsConfig?.failureBNPLPaymentEmail || {}
  );
};

const sendfailedOrderSMS = async () => {
  const db = mongoUtil.getDb();

  try {
    const startOffset =
      global.baseConfig?.smsConfig?.failureSMSJourney?.smsRestrictionInHrs ||
      24;
    logInfo('smsRestrictionInHrs', startOffset);
    const startDate = moment().subtract(startOffset, 'hours').toDate();

    const lastSentSMSRecords = await db
      .collection('sms_list')
      .find({ createdAt: { $gte: startDate } })
      .toArray();

    const lastSentSMSEmailIds = map(lastSentSMSRecords, el => el.email);
     
    const proxyOrdersTemplate = proxyTemplate();
   
    const {
      finalArrayForNonProxyUsers,
      finalArrayForProxyUsers
    } = await getCustomersDataWithFailedProxyOrders2();

    // Now you may wonder why this mess after all the mess done earlier
    // We found live issues when lets say a tabby failed order is 40 min behind
    // but it has been converted to success order 20min earlier.
    // But existing logic chose to ignore anything in the last 30mins hence sms was sent.
    // As a patch, final arrays will be matched against all non failed quoteIds in the last 30mins.
    // Last 30 mins coz these are ignored in existing logic
    const finalFiterForQuoteIds = await getQuoteIdsNotPaymentFailedInDuration();

    const { smsCurrentRecipients, bulkUpdateArray } = sendSMSNonProxyOrders({
      finalArrayForNonProxyUsers,
      lastSentSMSEmailIds,
      finalFiterForQuoteIds
    });

    forEach(finalArrayForProxyUsers, el => {
      const lang = STORE_LANG_MAP[el.storeId];
      const template = proxyOrdersTemplate[`${lang}`];
      if (
        el.phone &&
        template &&
        !lastSentSMSEmailIds.includes(el.email) &&
        !smsCurrentRecipients.includes(el.email) &&
        !finalFiterForQuoteIds.includes(String(el.quoteId))
      ) {
        const sms = replaceAllString(template, getReplaceMap(el));
        logInfo('smsProxy', sms);
        sendKaleyraSMS({ msg: sms, phone: el.phone });
        bulkUpdateArray.push({
          insertOne: { document: { ...el, createdAt: new Date() } }
        });
        smsCurrentRecipients.push(el.email);
      }
    });

    logInfo('bulkUpdateArray', bulkUpdateArray);
    if (bulkUpdateArray.length)
      db.collection('sms_list').bulkWrite(bulkUpdateArray);
  } catch (e) {
    logInfo('Some error occured in service for sms journey');
    global.logError(e);
  }
};

const fetchSMSList = async reqObj => {
  try {
    const db = mongoUtil.getDb();
    const { filters, query, pageSize = 10, offset = 0 } = reqObj;
    const { fromDate, toDate, mode } = filters;

    try {
      let findObject = {};
      if (query) {
        findObject = {
          ...findObject,
          email: { $regex: query, $options: 'i' }
        };
      }
      if (mode)
        findObject = {
          ...findObject,
          mode
        };
      if (fromDate)
        findObject = {
          ...findObject,
          createdAt: {
            ...(findObject?.['createdAt'] || {}),
            $gte: new Date(fromDate)
          }
        };
      if (toDate)
        findObject = {
          ...findObject,
          createdAt: {
            ...(findObject?.['createdAt'] || {}),
            $lte: new Date(toDate)
          }
        };
      return await db
        .collection('sms_list')
        .aggregate([
          { $match: findObject },
          { $sort: { created_at: -1 } },
          {
            $facet: {
              totalData: [{ $skip: offset * pageSize }, { $limit: pageSize }],
              totalCount: [
                {
                  $group: {
                    _id: null,
                    count: { $sum: 1 }
                  }
                }
              ]
            }
          }
        ])
        .toArray();
    } catch (e) {
      global.logError(e);
      console.log([`Exception while fetching SMS List - ${e.message}`]);
      throw new ApiError(
        httpStatus.INTERNAL_SERVER_ERROR,
        'Bank Transfers read failed'
      );
    }
  } catch (e) {
    logInfo('Some error occured in service for sms journey');
    global.logError(e);
  }
};

const getReplaceMap = el => {
  const websiteIdentifier = frontendURLBasedOnStoreId[el.storeId] || 'sa/ar';

  return {
    '{name}': el.name,
    '{ordernumber}': el.incrementId,
    '{amount}': el.currency + ' ' + el.amount,
    '{link}': 'https://stylishop.com/' + websiteIdentifier + '/checkout/cart'
  };
};

/**
 *
 * @param {*} param0
 * @return {string}
 */
 function prepareHtml({ template, data }) {
  const templatePath = templateMap[template];

  const html = fs.readFileSync(templatePath, 'utf8');
  const htmlTemplate = Handlebars.compile(html);
  return htmlTemplate(data);
}

/**
 *
 * @param {*} param0
 * @return {string}
 */
function getTemplate() {
  return {
    bnplFailedOrders_en: './src/templates/bnplFailedOrdersEn.html',
    bnplFailedOrders_ar: './src/templates/bnplFailedOrdersAr.html'
  };
}

const sendSMSNonProxyOrders = ({
  finalArrayForNonProxyUsers,
  lastSentSMSEmailIds,
  finalFiterForQuoteIds
}) => {
  const smsCurrentRecipients = [];
  const bulkUpdateArray = [];
  const emailConfig = global?.baseConfig?.emailConfig;
  const { fromEmail, fromName } = emailConfig;
  const emailData = getEmailData();
  const nonProxyOrdersTemplate = nonProxyTemplate();

  forEach(finalArrayForNonProxyUsers, el => {
    const lang = STORE_LANG_MAP[el.storeId];
    const template = nonProxyOrdersTemplate[`${lang}`];
    if (
      el.phone &&
      template &&
      !lastSentSMSEmailIds.includes(el.email) &&
      !smsCurrentRecipients.includes(el.email) &&
      !finalFiterForQuoteIds.includes(String(el.quoteId))
    ) {
      const sms = replaceAllString(template, getReplaceMap(el));
      logInfo('smsNonProxy', sms);
      sendKaleyraSMS({ msg: sms, phone: el.phone });
      bulkUpdateArray.push({
        insertOne: { document: { ...el, createdAt: new Date() } }
      });
      smsCurrentRecipients.push(el.email);

      try {
        if (
          el.email &&
          el.mode &&
          (el.mode.includes('tabby') || el.mode.includes('tamara'))
        ) {
          const bnplEmailTemplate = `bnplFailedOrders_${lang}`;
          logInfo('emailProxy', el.email);

          const html = prepareHtml({
            template: bnplEmailTemplate,
            data: {
              name: el.name,
              paymentType: emailData[`${el.mode}`][`${lang}`],
              orderId: el.incrementId,
              country: emailData[`${el.storeId}`][`${lang}`]
            }
          });
          logInfo('html prepared ', el.email);
          const emailSubject = emailData['emailSubject'][`${lang}`];

          sendSgEmail({
            to: el.email,
            from: { email: fromEmail, name: fromName },
            subject: emailSubject.replace('<orderId>', el.incrementId),
            html
          });
          logInfo('email sent ', el.email);
        }
      } catch (e) {
        global.logError(`Email Error : ${el.email}`, e);
      }
    }
  });
  return { smsCurrentRecipients, bulkUpdateArray };
};

module.exports = { sendfailedOrderSMS, fetchSMSList };
