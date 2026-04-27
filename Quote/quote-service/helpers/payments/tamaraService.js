const axios = require('axios');
const _ = require('lodash');
const { logInfo, logError } = require('../utils');
const logger = require('../utils');
const paymentUtils = require('./paymentUtils');
// const TAMARA_INSTALLMENTS_3 = "tamara_installments_3";
// const TAMARA_INSTALLMENTS_6 = "tamara_installments_6";
const COLOR_CODE = '#545454';

exports.getPayments = async (quote, responseObject, store, paymentConfig) => {

  try {
  const currency = store.storeCurrency;
  const country = store.websiteCode;
  let amount =
    Number(responseObject.grandTotal) - Number(responseObject.codCharges || 0);

  if (amount === 0) amount = Number(responseObject.estimatedTotal || 0);

  if (amount <= 0) return {};

  const config = {
    method: 'get',
    url: `${
      paymentConfig.tamaraApiUrl
    }/payment-types?country=${country?.toUpperCase()}&currency=${currency}&order_value=${amount}`,
    headers: {
      Authorization: `Bearer ${paymentConfig.tamaraToken}`,
      'Content-Type': 'application/json',
    },
    timeout: paymentConfig.httpTimeout,
  };

  return axios(config)
    .then((res) => {
      // logInfo(
      //   `### Tamara Get Payment Options for Quote : ${quote.id}`,
      //   `### Request : ${JSON.stringify(
      //     config
      //   )}, ### Response : ${JSON.stringify(res.data)}`
      // );
      return extractResponse(res.data, responseObject);
    })
    .catch((error) => {
      logError(
        error,
        `### Error in Get Payment types for Quote: ${quote.id} 
        ### Request : ${JSON.stringify(config)} ### Response : ${JSON.stringify(
          error?.response?.data
        )}`
      );
      return {};
    });

  }catch(error){
    logError(
      error,
      `### Error in Creating Tamara Session for Quote: ${
        quote.id
      } ### Request : ${JSON.stringify(config)} ### Response : ${JSON.stringify(
        error
      )}`
    );
  }
};

exports.createSession = async (quote, responseObject, store, paymentConfig) => {
  const payload = await buildSessionPayload(
    quote,
    responseObject,
    store,
    paymentConfig
  );
  // logger.info(`createSession: Tamara payload for quote ${quote.id} - ${JSON.stringify(payload)}`);
  const config = {
    method: 'post',
    url: paymentConfig.tamaraApiUrl,
    headers: {
      Authorization: `Bearer ${paymentConfig.tamaraToken}`,
      'Content-Type': 'application/json',
    },
    data: payload,
    timeout: paymentConfig.httpTimeout,
  };

  try {
    const session = await axios(config);
    const sessionDataResponse = extractSessionResponse(
      session.data,
      quote,
      responseObject
    );
    return sessionDataResponse;
  } catch (error) {
    logError(
      error,
      `### Error in Creating Tamara Session for Quote: ${
        quote.id
      } ### Request : ${JSON.stringify(config)} ### Response : ${JSON.stringify(
        error
      )}`
    );
  }
};

const extractSessionResponse = (response, quote, responseObject) => {
  const installments =
    quote?.quotePayment?.method === 'tamara_installments_3' ? 4 : 6;
  const totalAmount =
    responseObject.grandTotal === '0'
      ? responseObject.estimatedTotal
      : responseObject.grandTotal;
  const pay_per_installment = _.toString(
    calculateInstallmentAmounts(totalAmount, installments)
  );

  const tamaraPaymentOps = {};
  if (!_.isEmpty(response)) {
    tamaraPaymentOps[`${quote.quotePayment.method}`] = {
      web_url: response.checkout_url,
      amount_to_pay: totalAmount,
      installments_count: installments,
      pay_per_installment: pay_per_installment,
      color_code: COLOR_CODE,
    };
  }
  quote.tabbyPaymentId = response?.order_id;
  if (!response?.order_id) return;
  return tamaraPaymentOps;
};

const calculateInstallmentAmounts = (totalAmount, installments) => {
  try {
    const amount = Number(totalAmount) / installments;
    return _.round(amount, 2);
  } catch (error) {
    return 0;
  }
};

const buildSessionPayload = async (
  quote,
  responseObject,
  store,
  paymentConfig
) => {
  const sessionObj = {};
  sessionObj.order_reference_id = _.toString(quote?.id);
  sessionObj.order_number = _.toString(quote?.id);
  sessionObj.description = 'Dresses/Accessories';
  const totalAmount = responseObject.grandTotal;
  sessionObj.total_amount = {
    amount: totalAmount === '0' ? responseObject.estimatedTotal : totalAmount,
    currency: store.storeCurrency,
  };
  sessionObj.instalments =
    quote?.quotePayment?.method === 'tamara_installments_3' ? 4 : 6;
  sessionObj.country_code = _.toUpper(store.websiteCode);
  sessionObj.payment_type = 'PAY_BY_INSTALMENTS';
  sessionObj.locale = store?.storeLanguage;
  sessionObj.items = orderItems(quote);
  const quoteAddrs = quote.quoteAddress[0];

  const lastName = quoteAddrs?.lastname?.trim() || '.';
  const firstName = quoteAddrs?.firstname?.trim() || lastName;

  sessionObj.consumer = {
    first_name: firstName,
    last_name: lastName,
    phone_number: quoteAddrs?.mobileNumber,
    email: quote.customerEmail,
  };
  sessionObj.shipping_address = {
    first_name: firstName,
    last_name: lastName,
    line1: quoteAddrs?.buildingNumber,
    line2: '',
    region: quoteAddrs?.region,
    postal_code: quoteAddrs?.area,
    city: quoteAddrs?.city,
    country_code: quoteAddrs?.countryId,
    phone_number: quoteAddrs?.mobileNumber,
  };
  sessionObj.billing_address = {
    first_name: firstName,
    last_name: lastName,
    line1: quoteAddrs?.buildingNumber,
    line2: '',
    region: quoteAddrs?.region,
    postal_code: quoteAddrs?.area,
    city: quoteAddrs?.city,
    country_code: quoteAddrs?.countryId,
    phone_number: quoteAddrs?.mobileNumber,
  };
  sessionObj.tax_amount = {
    amount: '00.00',
    currency: store.storeCurrency,
  };
  sessionObj.shipping_amount = {
    amount: '00.00',
    currency: store.storeCurrency,
  };

  try {
    if (responseObject.discountData?.length) {
      const totalDiscount = responseObject.discountData.reduce(
        (totalDis, item) => {
          return totalDis + Number(item.value);
        },
        0
      );

      sessionObj.discount = {
        name: 'STYLICOUPON',
        amount: {
          amount: String(totalDiscount) || '0',
          currency: store.storeCurrency,
        },
      };
    }
  } catch (e) {
    // logger.error(`buildSessionPayload: Tamara discount object exception - ${e.message}`);
  }
  sessionObj.merchant_url = {
    success: paymentUtils.buildUrl(
      'success',
      responseObject,
      store,
      paymentConfig
    ),
    cancel: paymentUtils.buildUrl(
      'cancel',
      responseObject,
      store,
      paymentConfig
    ),
    failure: paymentUtils.buildUrl(
      'failure',
      responseObject,
      store,
      paymentConfig
    ),
    notification: paymentConfig.tamaraNotificationUrl,
  };
  sessionObj.platform = 'Styli OMS';
  sessionObj.risk_assessment = {
    has_cod_failed: false,
  };
  return sessionObj;
};

const extractResponse = async (response, responseObject) => {
  const tabbyPaymentOps = {};
  const supportedInstalments = response[0]?.supported_instalments;
  const totalAmount =
    responseObject.grandTotal === '0'
      ? responseObject.estimatedTotal
      : responseObject.grandTotal;
  supportedInstalments?.forEach((res) => {
    switch (res?.instalments) {
      case 3:
        const payment_3 = buildPaymentRes(totalAmount, 3);
        tabbyPaymentOps['installment_3'] = payment_3;

        break;
      case 4:
        const payment_4 = buildPaymentRes(totalAmount, 4);
        tabbyPaymentOps['installment_3'] = payment_4;
        break;
      case 6:
        const payment_6 = buildPaymentRes(totalAmount, 6);
        tabbyPaymentOps['installment_6'] = payment_6;
        break;
      default:
        break;
    }
  });
  return tabbyPaymentOps;
};

const buildPaymentRes = (totalAmount, installments) => {
  const pay_per_installment = _.toString(
    calculateInstallmentAmounts(totalAmount, installments)
  );
  return {
    amount_to_pay: _.toString(totalAmount),
    installments_count: installments,
    pay_per_installment: _.toString(pay_per_installment),
  };
};

const orderItems = (quote) => {
  return quote.quoteItem?.map((item) => {
    return {
      name: item.name,
      type: item.productType,
      description: '',
      quantity: item.qty,
      total_amount: {
        amount: item.priceInclTax,
        currency: quote.storeCurrencyCode,
      },
      reference_id: quote.id,
      image_url: '',
      sku: item.sku,
    };
  });
};
