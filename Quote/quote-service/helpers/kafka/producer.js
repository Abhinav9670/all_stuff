const { logError } = require('../utils');
const kafka = require('./init');
const producer = kafka.producer();
const keysToSkipQuote = ['quoteItem', 'city','quoteAddress','quotePayment','priceDropData','metadata'];
const keysToSkipQuoteItem = ['enrich','metadata'];

const startKafkaProducer = async () => {
  try{
 const response =  await producer.connect();
  }catch(e){
    global.logError(e)
  }
};

const produceQuote = async ({ quote,xHeaderToken }) => {
  try{
  const { quoteMsg, itemMsgs } = prepareMessages(quote,xHeaderToken);
  const response = await producer.send({
    topic: `${process.env.KAFKA_TOPIC_QUOTE}`,
    messages: [
      quoteMsg
    ],
  });
  const itemResponse = await producer.send({
    topic: `${process.env.KAFKA_TOPIC_QUOTE_ITEMS}`,
    messages:itemMsgs,
  });
  }catch(e){
    logError(e,{"kafka producer error": e.message})
  }
};

const prepareMessages = (quote,xHeaderToken) => {
  const quoteItems = quote?.quoteItem;
  const quoteMessage = Object.keys(quote).reduce((quoteMsg, key) => {
    if (!keysToSkipQuote.includes(key)) {
      quoteMsg[key] =
      quote[key] && typeof quote[key] === 'object' && key !== "updatedAt"
          ? JSON.stringify(quote[key])
          : quote[key];
    }
    quoteMsg.updatedEpoc = Date.parse(quote.updatedAt);
    return quoteMsg;
  }, {});

  const itemMsgs = quoteItems.map(quoteItem => {
   const itemMsg =   Object.keys(quoteItem).reduce((quoteItemMsg, key) => {
      if (!keysToSkipQuoteItem.includes(key)){
      quoteItemMsg[key] =
      quoteItem[key] && typeof quoteItem[key] === 'object'
          ? JSON.stringify(quoteItem[key])
          : `${quoteItem[key]}`;
      }
      return quoteItemMsg;
    }, {});
    itemMsg.itemId = `${quote.id}_${quoteItem.sku}`;
    itemMsg.customerId = quote.customerId;
    itemMsg.updatedAt = quote.updatedAt;
    itemMsg.isActive = quote.isActive;
    itemMsg.customerEmail = quote.customerEmail || xHeaderToken;
    itemMsg.quoteId = quote.id;
    itemMsg.updatedEpoc = Date.parse(quote.updatedAt);
    return {id:`${quote.id}_${quoteItem.sku}`,value:JSON.stringify(itemMsg)}
  });

  return { quoteMsg:{key:`${quote.id}`,value:JSON.stringify(quoteMessage)},itemMsgs };
};

module.exports = { startKafkaProducer, producer, produceQuote };
