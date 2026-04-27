const { logInfo, logError } = require('../helpers/utils');
const kafka = require('../helpers/kafka/initMulinKafka');
const axios = require('axios');
const moment = require("moment");
const { initcluster } = require('../config/couchbase.js');

// console.log(
//   `price drop consumer topic : ${process.env.KAFKA_PRICE_DROP_TOPIC}`
// );
// console.log(`Kafka group ID : ${process.env.KAFKA_GROUP_ID}`);

const priceDropConsumer = async () => {
  try {
    const consumer = kafka.consumer({
      groupId: `${process.env.KAFKA_GROUP_ID}_price_drop`,
    });
    consumer.on('consumer.connect', () => console.log('consumer kafka connected'));
    consumer.on('consumer.disconnect', () => console.log('consumer kafka disconnect'));
    consumer.on('consumer.crash', event => console.log('consumer kafka crash', JSON.stringify(event?.payload?.error)));
    consumer.on('consumer.stop', () => console.log('consumer kafka stop'));
    consumer.on('consumer.network.request_timeout', () => console.log('consumer kafka network timeout'));
    consumer.on('consumer.heartbeat', e => console.log(`heartbeat at ${e.timestamp}`));

    await consumer.connect();
    logInfo('connect done');
    await consumer.subscribe({
      topic: process.env.KAFKA_PRICE_DROP_TOPIC,
      fromBeginning: true,
    });
    logInfo('subscribe done');
    await consumer.run({
      eachBatchAutoResolve: false,
      eachBatch: async ({

        batch,
        resolveOffset,
        heartbeat,
        isRunning,
        isStale,
      }) => {
        for (const message of batch.messages) {
          if (!isRunning() || isStale()) {
            break;
          }
          try {
            const dataFromKafkaData = message.value.toString();
            // logInfo('dataFromKafka price drop notification', dataFromKafkaData);
            const dataFromKafka = JSON.parse(dataFromKafkaData);
            if (dataFromKafka?.skus) {
              const rawData = dataFromKafka.skus;
              customerStatusUpdate(rawData);
            }

          } catch (e) {
            // logInfo('error in parse data', e);
            logError(e, "Error processing kafka message delete customer consumer");
          }
          resolveOffset(message.offset);
          await heartbeat();
        }
      },
    });
  } catch (e) {
    // logInfo('kafka connect  data error', e);
    logError('error during parsing price drop data:', e);
  }
};

const customerStatusUpdate = async (dataFromKafka) => {
  try {
    let results;
    const priceDropMap = new Map(Object.entries(dataFromKafka));
    try {
      const cluster = await initcluster();
      const apm = global?.apm;
      const skus = [];
      priceDropMap.forEach(function (val, key) {
        skus.push(key);
      });
      const lastSevenDays = String(moment.utc(Date.now()).subtract(7, 'd').format("YYYY-MM-DD"));
      // console.log(`skus: ${JSON.stringify(skus)}, lastSevenDays: ${lastSevenDays}`);
      const chunkSize = 70;
      for (let i = 0; i < skus.length; i += chunkSize) {
        const chunk = skus.slice(i, i + chunkSize);
        const query = `SELECT quoteItem , customerId , customerEmail ,storeId , storeCurrencyCode,websiteId FROM ${process.env.COUCHBASE_CLUSTER_BUCKET} WHERE customerIsGuest=0 and isActive=1 and updatedAt >
      "${lastSevenDays}" and ANY v IN skus SATISFIES v IN ${JSON.stringify(chunk)} END and ANY y IN websiteId SATISFIES y IN [1,3,4,5,7,9] END`;

        // console.log('drop price query:', query);
        let span;
        try {
          span = apm?.startSpan('CB: Query priceDropSkus', 'db', 'couchbase', 'query');
          if (span) {
            span.setServiceTarget('couchbase', process.env.COUCHBASE_CLUSTER_BUCKET);
          }
          results = await cluster.query(query);
        } finally {
          if (span) span.end();
        }
        // console.log('drop price results:', results);
        if (results) {
          const data = results;
          await prepareEventData({ data, priceDropMap });
        }
      }


    } catch (e) {
      logError(e, 'error getting drop price quote by query');
    }


  } catch (e) {
    logError(e);
  }
};

const logBrazeCustomEvent = async ({ eventArr, customAttrArr }) => {
  const { BRAZE_BASE_URL, BRAZE_AUTH_TOKEN } = process.env;
  let rsp = false;
  const events = eventArr;
  const attributes = customAttrArr;
  // logInfo("events::", JSON.stringify(events));
  // logInfo("attributes::", JSON.stringify(attributes));
  // logInfo("braze Key", { BRAZE_BASE_URL, BRAZE_AUTH_TOKEN });
  try {
    if (BRAZE_BASE_URL) {
      const response = await axios.post(
        BRAZE_BASE_URL,
        { events, attributes },
        {
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${BRAZE_AUTH_TOKEN}` || ""
          }
        }
      );
      rsp = true;
      // logInfo("Braze response ", response?.data);
    }
  } catch (e) {
    rsp = e.message
    logError(e, "braze error during price drop notification");
  }
  return rsp;
};

const prepareEventData = async ({ data, priceDropMap }) => {
  try {
    const results = data;
    let customAttrArr = [];
    let eventArr = [];
    if (results.rows.length > 0) {
      results.rows.forEach(quoteData => {
        let data = quoteData;
        (data.quoteItem || []).forEach(item => {
          if (priceDropMap.has(item.sku)) {
            const priceData = priceDropMap.get(item.sku);
            const name = item.name;
            let reducedPrice = 0;
            let oldValuePrice = 0;
            let priceDiff = 0;
            const imageurl = item.imgUrl;
            const customer_id = data.customerId;
            let priceDropPercent = 0;
            const priceincltax = item.priceInclTax;
            const storeCurrency = data.storeCurrencyCode;

            if (priceData.length > 0) {
              priceData.forEach(priceVal => {
                for (let key in priceVal) {
                  let websiteId = 0;
                  if (data.websiteId && data.websiteId.length > 0) {
                    websiteId = data.websiteId[0];
                  }
                  const oldNewValObject = priceVal[key];
                  const priceMap = new Map(Object.entries(oldNewValObject));
                  // console.log('priceMap:', priceMap);
                  priceMap.forEach(function (priceVal, priceKey) {
                    if (key.toLowerCase() === 'oldValue'.toLowerCase() && websiteId == priceKey) {
                      oldValuePrice = priceincltax;
                    }
                    if (key.toLowerCase() === 'newValue'.toLowerCase() && websiteId == priceKey) {
                      reducedPrice = priceVal;
                      priceDiff = oldValuePrice - reducedPrice;
                      if (priceDiff > 0) {
                        priceDropPercent = (oldValuePrice - reducedPrice) / oldValuePrice * 100;
                      }
                      const properties = {
                        product_name: name,
                        image: imageurl,
                        user_id: customer_id,
                        price_diff: Number(priceDiff),
                        drop_percent: Math.round(priceDropPercent),
                        current_price: Number((reducedPrice).toFixed(2)),
                        old_price: Number(priceincltax),
                        storeCurrency
                      };

                      const eventObject = {
                        external_id: customer_id,
                        name: "price_drop",
                        time: moment.utc(Date.now()).format("YYYY-MM-DD HH:mm:ss"),
                        properties: properties
                      };

                      const customAttrObj = {
                        external_id: customer_id,
                        pd_image: imageurl
                      };
                        /**75 is brnaze limit per request **/
                      if (eventArr.length < 70) {
                        customAttrArr.push(customAttrObj);
                        eventArr.push(eventObject);
                      } else {
                        customAttrArr.push(customAttrObj);
                        eventArr.push(eventObject);
                        logBrazeCustomEvent({ eventArr, customAttrArr });
                        customAttrArr = [];
                        eventArr = [];
                      }

                    }
                  });

                }
              })
            }

          }
        });

      });
      if (customAttrArr.length > 0 && eventArr.length > 0) {
        const eventResponse = logBrazeCustomEvent({ eventArr, customAttrArr });
        // console.log('eventResponse:', eventResponse);
      }
    }
  } catch (e) {
    logError(e, "price drop error during price drop seup json");
  }
};


module.exports = { priceDropConsumer, customerStatusUpdate };
