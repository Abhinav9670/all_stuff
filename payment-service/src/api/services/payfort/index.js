const axios = require('axios');
const logger = require('../../../config/logger');
const { isEmpty } = require('lodash');
const { FAILURE } = require('./constants');
const { generateSignature } = require('./utilityReport');
const { stringifyError } = require('../../utils/log');

const isSuccessStatus = (status) => ['20', '14', '15', '19', '02'].includes(status);
const validateResponse = (response = {}) => {
  const error = {};
  const success = {};
  const body = response?.data;

  if (isEmpty(body)) {
    error.status = FAILURE;
    error.message = 'Unable to process the payment';
  } else if (isSuccessStatus(body.status)) {
    success.message = 'Payment successful.';
    success.redirectUrl = body['3ds_url'];
    success.payfort = body;
  } else {
    error.status = FAILURE;
    error.message = body.response_message || 'Unable to process the payment';
    error.payfort = body;
  }

  return { error, success };
};

const buildSignatureData = (client, data) => {
  const signatureData = {
    access_code: client.access_code,
    amount: data.amount,
    command: data.command,
    currency: data.currency,
    customer_ip: data.customer_ip,
    language: data.language,
    merchant_identifier: client.merchant_identifier,
    merchant_reference: data.merchant_reference,
    merchant_extra1: data.merchant_extra1,
    order_description: data.order_description,
    merchant_extra4: `${data.merchant_extra4}`,
  };

  if (data.customer_email) {
    signatureData.customer_email = data.customer_email;
  }

  if (data.remember_me) {
    signatureData.remember_me = data.remember_me;
  }

  if (data.storedCard && !data.isClientTokenized && data.card_security_code) {
    signatureData.card_security_code = data.card_security_code;
  }

  if (data.device_fingerprint) {
    signatureData.device_fingerprint = data.device_fingerprint;
  }

  if (client.isApple && data.appleData) {
    signatureData.apple_data = data.appleData.apple_data;
    signatureData.apple_header = data.appleData.apple_header;
    signatureData.apple_paymentMethod = data.appleData.apple_paymentMethod;
    signatureData.apple_signature = data.appleData.apple_signature;
    signatureData.digital_wallet = 'APPLE_PAY';
  } else {
    signatureData.token_name = data.token_name;
  }

  if (data.return_url) {
    signatureData.return_url = data.return_url;
  }

  return signatureData;
};

const buildJsonRequest = (client, data) => {
  const jsonRequest = {
    access_code: client.access_code,
    amount: data.amount,
    command: data.command,
    currency: data.currency,
    customer_ip: data.customer_ip,
    language: data.language,
    merchant_identifier: client.merchant_identifier,
    merchant_reference: data.merchant_reference,
    return_url: data.return_url,
    signature: data.signature,
    token_name: data.token_name,
    merchant_extra1: data.merchant_extra1,
    order_description: data.order_description,
    merchant_extra4: data.merchant_extra4,
  };

  if (data.customer_email) {
    jsonRequest.customer_email = data.customer_email;
  }

  if (data.storedCard && !data.isClientTokenized) {
    jsonRequest.card_security_code = data.card_security_code;
  }

  if (data.device_fingerprint) {
    jsonRequest.device_fingerprint = data.device_fingerprint;
  }

  if (data.remember_me) {
    jsonRequest.remember_me = data.remember_me;
  }

  if (client.isApple && data.appleData) {
    Object.assign(jsonRequest, data.appleData);
  }

  return jsonRequest;
};

const purchase = async (client, data, apmTransaction) => {
  try {
    data.access_code = client.access_code;
    data.merchant_identifier = client.merchant_identifier;

    const signatureData = buildSignatureData(client, data);
    logger.info('Signature Data:', JSON.stringify(signatureData));

    const apmSpanSignature = apmTransaction.startSpan('generateSignatureSpan');
    data.signature = generateSignature(client.passphrase, signatureData);
    apmSpanSignature.end();

    const jsonRequest = buildJsonRequest(client, data);
    logger.info('Purchase Request:', JSON.stringify(jsonRequest));

    const apmSpanPayfort = apmTransaction.startSpan('payfortSpan');
    try {
      const startTime = new Date();
      const httpResponse = await axios.post(client.purchaseUrl, jsonRequest);
      const endTime = new Date();
      logger.info(`Payfort Purchase API Time: ${data.merchant_reference}: ${endTime - startTime}ms`);

      const { error, success } = validateResponse(httpResponse);
      logger.info(`Payfort Response - Error: ${JSON.stringify(error)}, Success: ${JSON.stringify(success)}`);
      apmSpanPayfort.end();

      if (!isEmpty(error)) {
        const errorMessage = JSON.stringify(error, ['message', 'arguments', 'type', 'name', 'stack']);
        logger.info(`Error at validateResponse: ${stringifyError(error)}, ErrorMessage: ${errorMessage}`);
        return { error };
      } else {
        return { error: {}, success };
      }
    } catch (error) {
      apmSpanPayfort.end();
      logger.info(`Error at purchase: ${stringifyError(error)}`);
      return { error: Object.assign({}, error, { payfort: error }) };
    }
  } catch (error) {
    logger.info(`Purchase Exception: ${stringifyError(error)}`);
    return { error };
  }
};

const purchaseReturn = async ({ payfortQuery, client }) => {
  return new Promise((resolve) => {
    const signatureValues = { ...payfortQuery };
    delete signatureValues.signature;
    const responsePassphrase = generateSignature(client.responsePassphrase, signatureValues);
    const { payfort } = global.payfortConfig;

    const successStatusArr =
      payfortQuery.command === 'AUTHORIZATION'
        ? payfort['PAYFORT_AUTHORIZATION_SUCCESS_STATUS']
        : payfort['PAYFORT_PURCHASE_SUCCESS_STATUS'];

    if (responsePassphrase === payfortQuery.signature) {
      if (
        successStatusArr.includes(payfortQuery.status) ||
        ['15', '19'].includes(payfortQuery.status)
      ) {
        resolve({ success: payfortQuery });
      } else {
        resolve({ error: payfortQuery });
      }
    } else {
      logger.info('Purchase Return - Signature Failure');
      resolve({
        error: {
          status: FAILURE,
          message: "Something went wrong. Aren't you?"
        }
      });
    }
  });
};

const capturePayment = async (client, data) => {
  try {
    data.access_code = client.access_code;
    data.merchant_identifier = client.merchant_identifier;

    const captureData = {
      access_code: client.access_code,
      amount: data.amount,
      command: data.command,
      currency: data.currency,
      fort_id: data.fortId,
      language: data.language,
      merchant_identifier: client.merchant_identifier,
      merchant_reference: data.merchant_reference,
      order_description: data.order_description,
    };

    logger.info('Capture Payment - Capture Data:', JSON.stringify(captureData));
    data.signature = generateSignature(client.passphrase, captureData);
    logger.info('Capture Payment - Signature:', data.signature);

    const jsonRequest = {
      ...captureData,
      signature: data.signature,
    };

    logger.info('Capture Payment - JSON Request:', jsonRequest);

    try {
      const startTime = new Date();
      const httpResponse = await axios.post(client.purchaseUrl, jsonRequest);
      const endTime = new Date();
      logger.info(`Payfort Capture API Time: ${data.merchant_reference}: ${endTime - startTime}ms`);

      const { error, success } = validateResponse(httpResponse);
      if (!isEmpty(error)) {
        logger.info(`Error at capturePayment: ${stringifyError(error)}`);
        return { error };
      } else {
        return { error: {}, success };
      }
    } catch (error) {
      return { error: Object.assign({}, error, { payfort: error }) };
    }
  } catch (e) {
    logger.info('Capture Payment Exception:', e.message);
    return { error: e };
  }
};

module.exports = {
  purchase,
  purchaseReturn,
  capturePayment,
};
