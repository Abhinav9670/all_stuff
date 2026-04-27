const { getStoreLanguage } = require('../utils');
const _ = require('lodash');
const { upsertQuote } = require('../upsertQuote');

exports.pushNotification = ({
  quote,
  notifyData,
  screenName = 'bag',
  notifyId,
  textKey
}) => {
  const notificationObj = prepareNotifications({
    notifyData,
    notifyId,
    screenName,
    storeId: quote?.storeId,
    textKey
  });

  if (!quote?.notifications) quote.notifications = [];

  if (!_.isEmpty(notificationObj)) quote.notifications.push(notificationObj);
  return quote;
};

const prepareNotifications = ({
  notifyId,
  notifyData,
  screenName,
  storeId,
  textKey,
  type = "strip"
}) => {
  const storeLanguage = getStoreLanguage(storeId);
  const { bgColor, color } = notifyData;

  const message = notifyData?.[storeLanguage]?.[textKey];

  if(message){
    return {
        screen:screenName,
        name:notifyId,
        content:{
            message,
            bgColor: bgColor[textKey] || "#E4ECF1",
            color: color[textKey] || "#1C759B",
        },
        type
    };
  } 
};

exports.resetNotifications = async({quote,xHeaderToken,collection}) => {
   quote.notifications = [];
   await upsertQuote({ storeId:quote?.storeId, quote, collection, xHeaderToken });

   return quote
}
