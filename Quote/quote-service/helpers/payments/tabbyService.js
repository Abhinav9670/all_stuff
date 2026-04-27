const axios = require("axios");
const _ = require("lodash");
const mysql = require("../../config/mySqlConnection");
const { logError, logInfo } = require("../utils");
const moment = require("moment");
const paymentUtils = require("./paymentUtils");
const { getCustomerOrderList } = require("../../javaApis/orderedCount");
const { getCustomerInfo } = require("../customer");
const COLOR_CODE = "#545454";

/**
 * Crate Tabby Session to Initiate Tabby Payment Transaction
 */
exports.createSession = async (quote, responseObject, store, paymentConfig) => {
  let config;
  try {
  const totalAmount = responseObject.grandTotal;
  if(totalAmount === '0')
    return;
  const sessionObj = await buildSessionPayload(
    quote,
    responseObject,
    store,
    paymentConfig
  );
   config = {
    method: "post",
    url: paymentConfig.apiUrl,
    headers: {
      Authorization: `Bearer ${paymentConfig.apiPublicKey}`,
      "Content-Type": "application/json"
    },
    data: sessionObj
  };
  return axios(config)
    .then(res => {
      // logInfo(
      //   `### Tabby Create Session for Quote : ${quote.id}`,
      //   `### Request : ${JSON.stringify(
      //     config
      //   )}, ### Response : ${JSON.stringify(res.data)}`
      // );
      return extractResponse(res.data, quote);
    })
    .catch(error => {
      logError(
        error,
        `### Error in Creating Tabby get payment for Quote: ${
          quote.id
        } ### Request : ${JSON.stringify(
          config
        )} ### Response : ${JSON.stringify(error?.response?.data)}`
      );
    });
  }catch(error){
    logError(
      error,
      `### Error in Creating Tabby Session for Quote: ${quote.id} ### Request: ${
        config ? JSON.stringify(config) : 'Session payload not built / config undefined'
      } ### Response: ${JSON.stringify(error?.response?.data || {})}`
    );
  }
};
/**
 * Capture required information from Tabby Response and store Tabby Payment ID in quote.
 * @param {*} response
 * @param {*} quote
 * @returns
 */
const extractResponse = async (response, quote) => {
  const tabbyPaymentOps = {};
  const configs = response.configuration;
  const payment = response.payment;
  const availProducts = configs?.available_products;
  if (!_.isEmpty(availProducts)) {
    const installments = availProducts?.installments?.[0];
    const payLater = availProducts?.pay_later?.[0];
    if (installments) {
      tabbyPaymentOps.installments = {
        web_url: installments.web_url,
        pay_per_installment: installments.pay_per_installment,
        installments_count: installments.installments_count,
        amount_to_pay: installments.amount_to_pay,
        color_code: COLOR_CODE,
        installments: findInstallmentDates(installments)
      };
    }
    if (payLater) {
      tabbyPaymentOps.paylater = {
        web_url: payLater.web_url,
        amount_to_pay: payLater.amount_to_pay,
        installments_count: payLater.installments_count,
        pay_per_installment: payLater.pay_per_installment,
        color_code: COLOR_CODE,
        installments: findInstallmentDates(payLater)
      };
    }
  }
  quote.tabbyPaymentId = payment.id;
  return tabbyPaymentOps;
};

const buildSessionPayload = async (
  quote,
  responseObject,
  store,
  paymentConfig
) => {
  const sessionObj = {};
  sessionObj.lang = paymentUtils.getStoreLanguageFromStore(store);
  const xSource = _.lowerCase(responseObject.xSource);
  const clientVersionNumber =
    responseObject.xClientVersion?.split(".").join("") || 10000;
  if (xSource !== "msite" && xSource !== "oldmsite" && Number(clientVersionNumber) <= 337) {
    sessionObj.merchant_code = paymentConfig.oldMerchantCode;
  } else {
    sessionObj.merchant_code = paymentConfig.merchantCode;
  }
  const payment = {};
  const totalAmount = responseObject.grandTotal;
  payment.amount =
    totalAmount === "0" ? responseObject.estimatedTotal : totalAmount;
  payment.currency = quote.storeCurrencyCode;
  payment.description = "";
  const quoteAddrs = quote.quoteAddress[0];
  payment.buyer = {
    phone: quoteAddrs?.mobileNumber,
    email: quote.customerEmail,
    name: quoteAddrs?.firstname + " " + quoteAddrs?.lastname
    // dob: "2006-01-02",
  };
  const registeredSince = await getRegisteredSince(quote?.customerId);
  const totalOrders = await findOrdersByCustomer(quote);
  payment.buyer_history = {
    registered_since: registeredSince,
    loyalty_level: checkLoyalty(totalOrders, paymentConfig),
    wishlist_count: 0
  };
  const items = orderItems(quote);
  payment.order = {
    reference_id: _.toString(quote?.id),
    items: items
  };
  payment.order_history = [];
  payment.shipping_address = {};
  sessionObj.merchant_urls = {
    success: paymentUtils.buildUrl(
      "success",
      responseObject,
      store,
      paymentConfig
    ),
    cancel: paymentUtils.buildUrl(
      "cancel",
      responseObject,
      store,
      paymentConfig
    ),
    failure: paymentUtils.buildUrl(
      "failure",
      responseObject,
      store,
      paymentConfig
    )
  };
  sessionObj.payment = payment;
  return sessionObj;
};

const orderItems = quote => {
  return quote.quoteItem?.map(item => {
    return {
      title: item.name,
      description: "",
      quantity: item.qty,
      unit_price: item.priceInclTax,
      discount_amount: "",
      reference_id: item.sku,
      image_url: "",
      product_url: "",
      gender: item.gender,
      category: "",
      color: "",
      product_material: "",
      size_type: "",
      size: "",
      brand: item.brandName
    };
  });
};

// const merchantCode = (responseObject, paymentConfig) =>{
//   if (responseObject.xSource !== 'msite') {
//     const clientVersionNumber = responseObject.xClientVersion?.split('.').join('') || 10000;
//     if (Number(clientVersionNumber) < 306) {
//       return paymentConfig.oldMerchantCode;
//     }
//   }
//   return paymentConfig.merchantCode;
// }

/**
 * Extract loyaltyLevel from config
 * @param {*} totalOrder
 * @param {*} paymentConfig
 * @returns
 */
const checkLoyalty = (totalOrder, paymentConfig) => {
  const loyaltyLevel = _.find(
    paymentConfig.loyaltyLevels,
    lev =>
      _.toNumber(lev.minOrder) <= _.toNumber(totalOrder) &&
      _.toNumber(lev.maxOrder) >= _.toNumber(totalOrder)
  );
  return loyaltyLevel ? loyaltyLevel.level : 0;
};

const getRegisteredSince = async (customerId) => {
  let registeredOn = moment().utc().format("YYYY-MM-DDTHH:mm:ss.SSSZ");
  const customerInfo = await getCustomerInfo(customerId);
  try {
    if (customerId && customerInfo) {
      registeredOn = moment(customerInfo?.createdAt).toISOString();
    }
  } catch (error) {
    logError(
      error,
      `Error In getting Customer Registered Since: ${customerId}`,
      `Customer Fetch failed`
    );
  }
  return registeredOn;
};

exports.getRegisteredSince = getRegisteredSince;

// const getRegisteredSince = async customerId => {
//   let registeredOn = moment().utc().format("YYYY-MM-DDTHH:mm:ss.SSSZ");
//   const findCustomer = `select created_at from new_customer_entity where entity_id = ${customerId}`;
//   try {
//     if (customerId) {
//       const response = await mysql.query(findCustomer);
//       const data = JSON.parse(JSON.stringify(response));
//       if (data[0][0]) {
//         registeredOn = data[0][0].created_at;
//       }
//     }
//   } catch (error) {
//     logError(
//       error,
//       `Error In getting Customer Registered Since: ${customerId}`,
//       `Query : ${findCustomer}`
//     );
//   }
//   return registeredOn;
// };

const findOrdersByCustomer = async quote => {
  const { customerEmail } = quote;
  try {
    const customerOrderList = await getCustomerOrderList({
      quote,
      customerEmail
    });

    const previousOrderCount = customerOrderList?.filter(order =>
      ["processing", "shipped", "delivered"].includes(order.status)
    )?.length;

    return previousOrderCount;
  } catch (error) {
    logError(
      error,
      `Error In getting Tabby Total Orders for Customer : ${customerEmail}`
      // `Query : ${findOrdersQuery}`
    );
  }
  return 0;
};

const findInstallmentDates = data => {
  return data?.installments?.map(val => {
    const fmtDate = moment(val.due_date).format("DD MMM YY");
    val["due_date"] = fmtDate;
    return val;
  });
};
