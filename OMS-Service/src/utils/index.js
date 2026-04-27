const moment = require('moment');

exports.replaceAllString = (str, mapObj) => {
  const re = new RegExp(Object.keys(mapObj).join('|'), 'gi');
  return str.replace(re, function (matched) {
    const val = mapObj[matched];
    return val != null ? val : '';
  });
};

exports.getNumericValue = value => {
  if (!value) return 0;
  return parseFloat(value).toFixed(2);
};

exports.getPercentage = (b,a) => {
  if (b === 0) return '0.00%';  // Handle division by zero
  const percentage = (a / b) * 100;
  let percentVal = percentage.toFixed(0);
  return 100-percentVal;
};

exports.logInfo = (key, message) => {
  try {
    const payload = {
      key: typeof key === 'object' ? JSON.stringify(key) : key,
      message: typeof message === 'object' ? JSON.stringify(message) : message
      // request: {
      //   headers: {
      //     'x-header-token': xHeaderToken
      //   }
      // }
    };
    console.log(JSON.stringify(payload));
  } catch (e) {
    console.error('error info  Logging ', e.message);
  }
};

exports.sanitiseImageUrl = input => {
  const appConfig = global.config;
  const baseurl = appConfig?.environments[0]?.baseurl;
  if (!input) return '';
  if (input.indexOf('/pub/') > -1) {
    const arr = input.split('/pub/');
    if (arr.length > 1) arr.shift();
    input = arr.join();
    return baseurl + '/' + input;
  } else if (input.startsWith('//')) {
    return 'https:' + input;
  } else {
    return input;
  }
};

exports.promiseAll = async promiseArray => {
  let success = true;
  let errorMsg = '';
  const output = [];
  await Promise.allSettled(promiseArray)
    .then(values => {
      values.forEach(value => {
        if (value.status === 'rejected') {
          success = false;
          errorMsg = value.reason;
          global.logError(value.reason);
        } else {
          output.push(value?.value);
        }
      });
      return values;
    })
    .catch(error => {
      global.logError(error);
      success = false;
      errorMsg = error.message;
    });

  return { success, errorMsg, output };
};

exports.getUtcTIme = timestamp => {
  return timestamp ? moment.utc(timestamp).format('YYYY-MM-DD HH:mm:ss') : '';
};

exports.getJwtHeaders = clientHeaders => {
  return {
    Token: clientHeaders?.token,
    'x-header-token': clientHeaders?.['x-header-token']
  };
};

exports.getErrorResponse = e => {
  const errorMsg = e?.response?.data?.statusMsg || e.message;
  const statusCode = e?.response?.status || 500;
  return { errorMsg, statusCode };
};

const getTLVForValue = (tagNum, tagValue) => {
  // console.log(Buffer.from('شركة ﻻندمارك العربية', 'utf8'));

  const utf8Encode = new TextEncoder();
  const byteArray = utf8Encode.encode(tagValue);
  // console.log({ byteArrayLength: byteArray.length });
  // console.log({ length: tagValue.length });

  const tagbuf = Buffer.from([tagNum], 'utf8');
  const tagValueLenBuf = Buffer.from([byteArray.length], 'utf8');
  const tagValueBuf = Buffer.from(tagValue, 'utf8');
  const BufsArray = [tagbuf, tagValueLenBuf, tagValueBuf];
  return Buffer.concat(BufsArray);
};

exports.getInvoiceTLVString = (updatedAt, invoicedAmount, taxAmount) => {
  if (!updatedAt || !invoicedAmount || !taxAmount) return undefined;
  const sellerNameBuf = getTLVForValue(
    '1',
    'شركة ريتيل كارت للتجارة شركة شخص واحد'
  );
  const vatRegistrationNameBuf = getTLVForValue('2', '310415422600003');
  // const timestampBuf = getTLVForValue('3', moment(updatedAt).toISOString());
  const timestampBuf = getTLVForValue('3', updatedAt);
  const invoiceTotalBuf = getTLVForValue(
    '4',
    this.getNumericValue(invoicedAmount)
  );
  const vatBuf = getTLVForValue('5', this.getNumericValue(taxAmount));

  // console.log({ sellerNameBuf, vatRegistrationNameBuf });

  const tagsBufsArray = [
    sellerNameBuf,
    vatRegistrationNameBuf,
    timestampBuf,
    invoiceTotalBuf,
    vatBuf
  ];
  const qrCodebuf = Buffer.concat(tagsBufsArray);
  // console.log('debug', qrCodebuf.toString('base64'));
  return qrCodebuf.toString('base64');
};

exports.getQrCodeStr = ({ storeId, date, invoicedAmount, taxAmount }) => {
  let qrCodeStr;
  let qrCodeFlag = false;
  if ([1, 3, '1', '3', 7, 11, '7', '11'].includes(storeId)) {
    if (global?.baseConfig?.configs?.ksaQR) qrCodeFlag = true;
  } else if (global?.baseConfig?.configs?.nonKsaQR) qrCodeFlag = true;

  if (qrCodeFlag)
    qrCodeStr = this.getInvoiceTLVString(date, invoicedAmount, taxAmount);

  return qrCodeStr;
};

exports.errObj = (code, msg) => {
  return {
    status: false,
    statusCode: code,
    statusMsg: msg
  };
};

exports.sleepInMilliSeconds = ms => {
  return new Promise(resolve => {
    setTimeout(resolve, ms);
  });
};

exports.stringifyError = err => {
  return JSON.stringify(err, ['message', 'arguments', 'type', 'name', 'stack']);
};

exports.maskPhoneNumber = async phoneNumber => {
  let newPhoneNumber = phoneNumber.replace(' ', '').trim();
  let resultPhoneNumber = newPhoneNumber.replace(newPhoneNumber.substring(2, 7), '*****');
  console.log("resultPhoneNumber", resultPhoneNumber);
  return resultPhoneNumber;
};
