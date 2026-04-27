const { customerCollection } = require("../../config/couchbase");
const { processRules } = require("../ruleEngine");
const moment = require("moment");
const {
  logError,
  getQueryStoreIds,
  logInfo,
  getBaseConfig
} = require("../utils");
// const logger = require('../utils');
const { COUCHBASE_CLUSTER_CUSTOMER_BUCKET } = process.env;
const FRAUD_CUSTOMER_KEY = "fraud_customers";
const _ = require("lodash");
const { getCustomerOrderList } = require("../../javaApis/orderedCount");
const mongoInit = require("../../config/mongoInit");

exports.checkFraud = async ({
  quote,
  cluster,
  customerCollection,
  xHeaderToken,
  pool,
}) => {
  try {
    const apm = global?.apm;
    const {
      customerId,
      quoteAddress,
      storeId,
      returnOrderCount,
      customerEmail = "",
    } = quote;

    const fetchDataFromMongo = getBaseConfig("fetchDataFromMongo");
    const useOptimizedMongoQuery = getBaseConfig("useOptimizedMongoQuery");
    const db = mongoInit.getCustomerMGDB();

    const isFraudException = await checkFraudException({
      customerId,
      email: quoteAddress?.email || customerEmail,
      cluster,
      fetchDataFromMongo,
      db,
      useOptimizedMongoQuery
    });

    if (!isFraudException) {
      quote.isWhitelistedCustomer = false;
      const finalEmail = quoteAddress?.email || customerEmail || xHeaderToken;

      let rtoCount = returnOrderCount;
      let enabledFRules = [];

      if (fetchDataFromMongo) {
        const fRule = await db
          .collection("rules")
          .find({ type: "fRules", enabled: true })
          .toArray();
        enabledFRules = fRule;
      } else {
        let span;
        try {
          span = apm?.startSpan('CB: Query fRules fetch CustomerMg', 'db', 'couchbase', 'query');
          if (span) {
            span.setServiceTarget('couchbase', COUCHBASE_CLUSTER_CUSTOMER_BUCKET);
          }
          const fRulesQuery = `select * from ${COUCHBASE_CLUSTER_CUSTOMER_BUCKET} where type="fRules" AND enabled = true`;
          const fRulesData = await cluster.query(fRulesQuery);
          if (fRulesData && fRulesData?.rows?.length) {
            enabledFRules = fRulesData?.rows?.flatMap(
              (row) => row[COUCHBASE_CLUSTER_CUSTOMER_BUCKET]
            );
          }
        } finally {
          if (span) span.end();
        }
      }

      if (
        enabledFRules.length &&
        (returnOrderCount === undefined || !customerId)
      ) {
        rtoCount = await getRtoOrderCount({
          customerId,
          storeId,
          email: finalEmail,
          pool,
          xHeaderToken,
          quote,
        });
        quote.returnOrderCount = rtoCount;
      }

      const toPrcessData = { rtoCount };
      const { ruleResponse, ruleOutput } = processRules({
        rulesData: enabledFRules,
        data: toPrcessData,
      });
      // logInfo(
      //   "fraud Data",
      //   {
      //     enabledFRules,
      //     toPrcessData,
      //     ruleResponse,
      //     ruleOutput,
      //     email: finalEmail,
      //     storeId,
      //     customerId,
      //   },
      //   xHeaderToken
      // );

      quote.appliedFraudRules = ruleResponse;
      quote.appliedFRulesOutput = ruleOutput;
      if (ruleResponse.length)
        handleRuleAppliedToQuote(
          customerCollection,
          quote,
          ruleResponse,
          finalEmail,
          xHeaderToken,
          apm,
          db,
          fetchDataFromMongo
        );
    } else {
      quote.isWhitelistedCustomer = true;
    }
    return quote;
  } catch (e) {
    logError(e, "error check Fraud  : ", xHeaderToken);
  }
};

const handleRuleAppliedToQuote = async (
  customerCollection,
  quote,
  ruleResponse,
  finalEmail,
  xHeaderToken,
  apm,
  db,
  fetchDataFromMongo
) => {
  const key = `fQuote_${quote.id}`;
  // logger.info(`checkFraud: Processing quote ID ${key}`);
  let fQuote = undefined;
  try {
    let fQuoteDate;
    if(fetchDataFromMongo){
      fQuoteDate = await db.collection('customer').find({id:key}).toArray();
    }else{
      let span;
      try {
        span = apm?.startSpan('CB: KV fQuote CustomerMg', 'db', 'couchbase', 'get');
        if (span) {
          span.setServiceTarget('couchbase', COUCHBASE_CLUSTER_CUSTOMER_BUCKET);
        }
        fQuoteDate = await customerCollection.get(key);
      } finally {
        if (span) span.end();
      }
    }


    fQuote = fetchDataFromMongo?fQuoteDate: fQuoteDate?.content;
  } catch (e) {
    logError(
      e,
      `${key} - error getting fQuote by key from acutal node`,
      xHeaderToken
    );
    try {
      let replicaFQuoteDate;
      if(fetchDataFromMongo){
        replicaFQuoteDate = await db.collection("customer").find({id:key}).toArray();
      }else{
        let span;
        try {
          span = apm?.startSpan('CB: KV fQuote CustomerMg Replica', 'db', 'couchbase', 'get');
          if (span) {
            span.setServiceTarget('couchbase', COUCHBASE_CLUSTER_CUSTOMER_BUCKET);
          }
          replicaFQuoteDate = await customerCollection.getAnyReplica(key);
        } finally {
          if (span) span.end();
        }
      }
    
      fQuote = fetchDataFromMongo?replicaFQuoteDate:replicaFQuoteDate?.content;
    } catch (ex) {
      logError(
        ex,
        `${key} - error getting fQuote by key from replica node`,
        xHeaderToken
      );
    }
  }
  // logger.info(`checkFraud: fQuote from DB for ${key}, length: ${fQuote?.length}, data: ${JSON.stringify(fQuote)}`);
  if (!fQuote?.length) {
    const object = {
      id: key,
      customerId: quote.customerId,
      customerEmail: finalEmail,
      markedOn: String(moment.utc().format()),
      rules: ruleResponse,
      type: "fQuote",
    };
    // logger.info(`checkFraud: Inserting quote in DB - ${JSON.stringify(object)}`);
    try {
      if (fetchDataFromMongo) {
        db.collection("customer").updateOne(
          { id: key },
          { $set: { ...object } },
          { upsert: true }
        );
      } else {
        let span;
        try {
          span = apm?.startSpan('CB: Upsert fQuote CustomerMg', 'db', 'couchbase', 'upsert');      
          if (span) {
            span.setServiceTarget('couchbase', COUCHBASE_CLUSTER_CUSTOMER_BUCKET);
          }
          customerCollection.upsert(key, object);
        } finally {
          if (span) span.end();
        }
      }
      // logInfo("fraud quote marked", { object }, xHeaderToken);
    } catch (e) {
      logError(e, `${key} - Error upserting fQuote`, xHeaderToken);
    }
  }
};

const getRtoOrderCount = async ({
  customerId,
  storeId,
  email,
  xHeaderToken,
  quote,
}) => {
  let rtoOrderCount = 0;
  const undelivered = [
    "rto_initiated",
    "rto",
    "shipped",
    "packed",
    "processing",
  ];
  const toSkip = ["payment_failed", "closed"];

  if (email) {
    const resData = await getCustomerOrderList({
      quote,
      customerEmail: email,
    });

    let isReturnInitiated = true;
    for (let i = 0; i < resData.length; i++) {
      const rData = resData[i];

      if (toSkip.includes(rData.status)) continue;
      if (!undelivered.includes(rData.status)) isReturnInitiated = false;
      if (undelivered.includes(rData.status) && isReturnInitiated) {
        rtoOrderCount++;
      }
    }

    // logInfo(
    //   "fraud rtoCountData",
    //   { rtoOrderCount, email, storeId, customerId },
    //   xHeaderToken
    // );
  }

  return rtoOrderCount;
};

const checkFraudException = async ({
  customerId,
  email,
  cluster,
  fetchDataFromMongo,
  db,
  useOptimizedMongoQuery
}) => {
  let result = false;
  let exceptionList
  if (fetchDataFromMongo) {
    const start = performance.now();
    if (useOptimizedMongoQuery) {
      exceptionList = await db
      .collection("customer")
      .find({
        $or: [
          { email: email },
          { customerId: customerId }
        ],
        type: "eList",
        isActive: true
      }).toArray();
    }else{
     exceptionList = await db
      .collection("customer")
      .find({
        $and: [
          {
            $or: [{ email: email }, { customerId: customerId }],
          },
          {
            type: "eList",
            isActive: true,
          },
        ],
      })
      .toArray();
    }
    const end = performance.now(); 
    // console.log(`[FraudException] Mongo Query Time: ${(end - start).toFixed(2)} ms | customerId=${customerId} email=${email}`);
    result = exceptionList?.length || 0;
  } else {
    const apm = global?.apm;
    let span;
    try {
      span = apm?.startSpan('CB: Query eList CustomerMg', 'db', 'couchbase', 'query');
      if (span) {
        span.setServiceTarget('couchbase', COUCHBASE_CLUSTER_CUSTOMER_BUCKET);
      }
      const selectQuery = `select * from ${COUCHBASE_CLUSTER_CUSTOMER_BUCKET} where type="eList" and isActive = true and (email="${
        email ?? ""
      }" or customerId="${customerId ?? ""}");`;
      const eListData = await cluster.query(selectQuery);
      let eList = [];
      if (eListData && eListData.rows && eListData.rows.length) {
        eList = eListData?.rows?.flatMap(
          (row) => row[COUCHBASE_CLUSTER_CUSTOMER_BUCKET]
        );
      }
      result = !!eList.length;
    } finally {
      if (span) span.end();
    }
  }
  return result;
};

/**
 * Validate If the customer email and phone is marked as Fraud
 * @param {*} email
 * @param {*} phone
 * @returns
 */
exports.checkFraudCustomer = async (email, phone) => {
  try {
    const fetchDataFromMongo = getBaseConfig("fetchDataFromMongo");
    const db = mongoInit.getCustomerMGDB();
    let fraudCust;
    if (fetchDataFromMongo) {
      fraudCust = await db
        .collection("fraud_customers")
        .find({
          $or: [{ email: email }, { phone: phone }],
        })
        .toArray();
    } else {
      const apm = global?.apm;
      let span;
      try {
        span = apm?.startSpan('CB: KV fraud_customers CustomerMg', 'db', 'couchbase', 'get');
        if (span) {
          span.setServiceTarget('couchbase', COUCHBASE_CLUSTER_CUSTOMER_BUCKET);
        }
        const custCollection = await customerCollection();
        const custObj = await custCollection.get(FRAUD_CUSTOMER_KEY);
        const values = Object.values(custObj?.content);
        fraudCust = values.filter(
          (cust) =>
            _.toUpper(cust.email) === _.toUpper(email) || cust.phone === phone
        );
      } finally {
        if (span) span.end();
      }
    }
    return fetchDataFromMongo && fraudCust
      ? fraudCust[0]
      : fraudCust
      ? fraudCust[0]
      : null;
  } catch (error) {
    return null;
  }
};
