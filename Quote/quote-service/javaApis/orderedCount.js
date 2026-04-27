const { default: Axios } = require("axios");
const { couchbase, collection } = require("../config/couchbase");
const { logError, logInfo, getStoreConfig } = require("../helpers/utils");
const internalAuthToken =
  process?.env?.AUTH_INTERNAL_HEADER_BEARER_TOKEN?.split(",")?.[0];

exports.getOrderedCount = async ({
  customerId,
  xHeaderToken,
  storeId,
  token,
  pool,
  quote,
  optimized = false
}) => {
  let previousOrderCount = 0;
  const customerEmail = xHeaderToken || "";
  try {
    if (customerId && customerEmail) {
      //   logInfo(
      //     "process.env.GET_ORDERED_COUNT",
      //     `${process.env.GET_ORDERED_COUNT}`,
      //     xHeaderToken
      //   );
      if (storeId) {
        // const storeIds = getQueryStoreIds(storeId);

        // let query =
        //   "select count(*) as count from sales_order sa where sa.store_id in ('" +
        //   storeIds +
        //   "')and sa.customer_email = ? and sa.status in ('processing','shipped','delivered','pending','packed','refunded')";

        // const [rows] = await pool.query(query, [customerEmail]);
        // previousOrderCount = rows[0]?.count;
        const customerOrderList = await this.getCustomerOrderList({
          quote,
          customerEmail,
          xHeaderToken,
          optimized
        });
        previousOrderCount = customerOrderList?.filter(order =>
          [
            "processing",
            "shipped",
            "delivered",
            "pending",
            "packed",
            "refunded"
          ].includes(order.status)
        )?.length;
      }
    }
  } catch (e) {
    logError(e, "Error getOrderedCount  : ", xHeaderToken || customerEmail);
  }
  return previousOrderCount;
};

exports.getCustomerOrderList = async ({
  quote,
  customerEmail,
  xHeaderToken,
  optimized = false
}) => {
  try {
    const logPrefix = `getCustomerOrderList:::${new Date().toISOString()}`;
    let quoteCollection;
    if(!optimized){
      quoteCollection = await collection();
      if(!quoteCollection){
        // console.log(logPrefix,"Collection missing in getCustomerOrderList: ")
      }
    }
    if (quote.previousOrderList) {
      return quote.previousOrderList;
    } else {
      if(!customerEmail){
        return [];
      }
      const websiteCode = getStoreConfig(quote?.storeId, "websiteId");
      const reqObj = { customerEmail, websiteId: Number(websiteCode) };

      const response = await Axios.post(
        process.env.ORDER_LIST,
        reqObj,
        {
          headers: {
            "Content-Type": "application/json",
            "authorization-token": internalAuthToken
          },
        }
      );

      const previousOrderList = response?.data?.responseList || [];
      previousOrderList.reverse();
      if(!optimized && quoteCollection){
        const key = `quote_${quote?.id}`;
        const apm = global?.apm;
        let span;
        const couchbaseClusterBucket = process.env.COUCHBASE_CLUSTER_BUCKET;
        try {
          span = apm?.startSpan('CB: MutateIn previousOrderList', 'db', 'couchbase', 'mutateIn');
          if (span) {
            span.setServiceTarget('couchbase', couchbaseClusterBucket);
          }
          await quoteCollection.mutateIn(key, [
            couchbase.MutateInSpec.upsert("previousOrderList", previousOrderList)
          ]);
        } finally {
          if (span) span.end();
        }
      }
      return previousOrderList;
    }
  } catch (e) {
    if (e.response) {
      logError(
        e.response.data,
        `Axios error: ${e.response.status} - ${e.response.statusText}`,
        xHeaderToken
      );
    } else if (e.request) {
      logError(e.request, "No response received from ORDER_LIST API", xHeaderToken);
    } else {
      logError(e.message, "Error creating request:", xHeaderToken);
    }
  }
  return [];
};
