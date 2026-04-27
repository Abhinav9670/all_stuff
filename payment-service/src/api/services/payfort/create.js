const logger = require('../../../config/logger');
const { formatPayfortAmount } = require('../../utils');

exports.createPurchaseReq = ({ body, currency, ipAddress, useDeviceFingerPrint, orderDetails, isApple }) => {
  const {
    paymentData = {},
    paymentMethod = {},
    expiryDate = '',
    cardNumber,
    cvv,
    saveCard,
    customerId,
    source,
    quoteId,
    cardToken,
    selectedAddressId: addressId,
    language = 'en',
    device_fingerprint,
    order_description = 'msite',
    customerEmail,
    storeId,
    returnUrl = '',
    paymentRedirectHost,
    isClientTokenized
  } = body || {};

  const { payfort } = global.payfortConfig;
  const { paymentService = {} } = payfort || {};
  const { baseUrl } = paymentService;

  const finalReturnUrl = returnUrl ? returnUrl : `https://${baseUrl}/v1/payfort/card/return`;

  const [month, year] = expiryDate.split('/');

  const { data: apple_data, signature: apple_signature, header } = paymentData;
  const { displayName, network, type } = paymentMethod;

  // Starts: API-2648: Authorization capture for payments with Payfort
  let commandType = 'PURCHASE';
  global.loggerInfo('create.js - createPurchaseReq - orderDetails: ', JSON.stringify(orderDetails));
  global.loggerInfo('create.js - createPurchaseReq - orderDetails.payfortAuthorized: ', orderDetails.payfortAuthorized);
  if (orderDetails.payfortAuthorized != undefined && orderDetails.payfortAuthorized == '1') {
    commandType = 'AUTHORIZATION';
  }
  global.loggerInfo('commandType01: ', commandType);
  // Ends: API-2648: Authorization capture for payments with Payfort

  const purchaseData = {
    command: commandType,
    currency,
    order_description,
    customer_email: (customerEmail || '').replace(/\s/g, ''),
    customer_name: undefined,
    device_fingerprint: useDeviceFingerPrint && device_fingerprint,
    language,
    customer_ip: ipAddress,
    return_url: finalReturnUrl,
    merchant_reference: orderDetails?.incrementId,
    card_security_code: cvv,
    merchant_extra4: String(storeId)
  };

  if (cardToken !== undefined) {
    purchaseData.token_name = cardToken;
    purchaseData.storedCard = cardToken;
    if (isClientTokenized) {
      purchaseData.isClientTokenized = isClientTokenized;
      purchaseData.remember_me = saveCard ? 'YES' : 'NO';
    }
  } else {
    purchaseData.card_number = cardNumber;
    purchaseData.expiry_date = `${year}${month}`;
  }

  const rememberMe = saveCard ? 'YES' : 'NO';
  purchaseData.merchant_extra1 = isApple
    ? 'apple_pay_123'
    : `${rememberMe}_${customerId}_${addressId || ''}_${quoteId}_${orderDetails.incrementId}_${
        orderDetails.orderId
      }_${source}_${paymentRedirectHost}`;
  (purchaseData.amount = formatPayfortAmount({ storeId, price: orderDetails.amount })),
    (purchaseData.token_name = cardToken),
    (purchaseData.incrementId = orderDetails.incrementId);

  if (isApple) {
    delete purchaseData.customer_name;
    delete purchaseData.device_fingerprint;
    delete purchaseData.card_security_code;
    delete purchaseData.card_number;
    delete purchaseData.expiry_date;
    delete purchaseData.remember_me;
    delete purchaseData.customer_name;
    delete purchaseData.token_name;
    delete purchaseData.incrementId;

    purchaseData.appleData = {
      digital_wallet: 'APPLE_PAY',
      apple_data,
      apple_signature,
      apple_header: {
        apple_ephemeralPublicKey: header.ephemeralPublicKey,
        apple_publicKeyHash: header.publicKeyHash,
        apple_transactionId: header.transactionId
      },
      apple_paymentMethod: {
        apple_displayName: displayName,
        apple_network: network,
        apple_type: type
      }
    };
  }
  logger.info(`OrderId : ${orderDetails?.incrementId} ,Card Token :  ${cardToken}`);
  return purchaseData;
};

exports.getClient = ({ storeId = 3, isApple = false, returnUrl } = {}) => {
  const { payfort } = global.payfortConfig;
  const { paymentService = {} } = payfort || {};
  const { storeCountryMap = {}, websiteHost, apiDomain, purchaseUrl } = paymentService;

  const finalReturnUrl = returnUrl ? returnUrl : `https://${websiteHost}/payment/payment/v2/card/return`;

  const country = storeCountryMap[`${storeId}`];
  const countryCred = payfort[`${country}_credentials`];
  const isAppleOrCard = isApple ? 'APPLE' : 'CARD';
  const dynamicText = `${country.toUpperCase()}_${isAppleOrCard}`;

  const accessCode = `PAYFORT_TOKEN_${dynamicText}_ACCESS_CODE`;
  const requestPassphrase = `PAYFORT_TOKEN_${dynamicText}_REQ_PASSPHRASE`;
  const responsePassphrase = `PAYFORT_TOKEN_${dynamicText}_RES_PASSPHRASE`;
  const merchantIdentifier = `PAYFORT_TOKEN_${dynamicText}_MERCHANT_IDENTIFIER`;

  logger.info(
    `requestPassphrase : ${requestPassphrase} ,responsePassphrase:  ${responsePassphrase} , merchantIdentifier: ${merchantIdentifier} `
  );
  return {
    isApple,
    access_code: countryCred[accessCode],
    merchant_identifier: countryCred[merchantIdentifier],
    passphrase: countryCred[requestPassphrase],
    responsePassphrase: countryCred[responsePassphrase],
    purchaseUrl,
    returnUrl: isApple ? '' : finalReturnUrl,
    apiDomain
  };
};

exports.createCapturePaymentReq = ({ body }) => {
  const {
    command,
    access_code,
    merchant_identifier,
    merchant_reference,
    amount,
    currency,
    language,
    signature,
    fort_id,
    order_description
  } = body || {};

  const captureData = {
    command: command,
    access_code: access_code,
    merchant_identifier: merchant_identifier,
    merchant_reference: merchant_reference,
    amount: amount,
    currency: currency,
    language: language,
    signature: signature,
    fort_id: fort_id,
    order_description: order_description
  };
  return captureData;
};

exports.getClientForCapturePayment = ({ storeId = 3, isApple = false } = {}) => {
  const { payfort } = global.payfortConfig;
  const { paymentService = {} } = payfort || {};
  const { storeCountryMap = {}, apiDomain, purchaseUrl } = paymentService;

  const country = storeCountryMap[`${storeId}`];
  const countryCred = payfort[`${country}_credentials`];
  const isAppleOrCard = isApple ? 'APPLE' : 'CARD';
  const dynamicText = `${country.toUpperCase()}_${isAppleOrCard}`;

  const accessCode = `PAYFORT_TOKEN_${dynamicText}_ACCESS_CODE`;
  const requestPassphrase = `PAYFORT_TOKEN_${dynamicText}_REQ_PASSPHRASE`;
  const responsePassphrase = `PAYFORT_TOKEN_${dynamicText}_RES_PASSPHRASE`;
  const merchantIdentifier = `PAYFORT_TOKEN_${dynamicText}_MERCHANT_IDENTIFIER`;

  logger.info(
    `requestPassphrase : ${requestPassphrase} ,responsePassphrase:  ${responsePassphrase} , merchantIdentifier: ${merchantIdentifier} `
  );
  return {
    isApple,
    access_code: countryCred[accessCode],
    merchant_identifier: countryCred[merchantIdentifier],
    passphrase: countryCred[requestPassphrase],
    responsePassphrase: countryCred[responsePassphrase],
    purchaseUrl,
    apiDomain
  };
};
