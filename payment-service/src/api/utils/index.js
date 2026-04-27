exports.getIpAddress = req => {
  const ipv6Regex =
    /^((?:[0-9A-Fa-f]{1,4}))((?::[0-9A-Fa-f]{1,4}))*::((?:[0-9A-Fa-f]{1,4}))((?::[0-9A-Fa-f]{1,4}))*|((?:[0-9A-Fa-f]{1,4}))((?::[0-9A-Fa-f]{1,4})){7}$/;
  let ip = req?.headers['x-forwarded-for'] || req?.connection?.remoteAddress || '';
  ip = ip.split(',');
  if (ip.length > 1) {
    ip = ip[1];
  } else {
    ip = ip[0];
  }

  if (ip.includes(':') && !ipv6Regex.test(ip)) {
    return '::1';
  }
  return ip ? ip.trim() : '::1';
};

exports.getCurrencyAndPrecision = storeId => {
  let currency = 'SAR';
  let storeUrl = 'sa';
  switch (storeId) {
    case 7:
    case '7':
    case 11:
    case '11':
      currency = 'AED';
      storeUrl = 'ae';
      break;
    case 12:
    case '12':
    case 13:
    case '13':
      currency = 'KWD';
      storeUrl = 'kw';
      break;
    case '15':
    case '17':
    case 15:
    case 17:
      currency = 'QAR';
      storeUrl = 'qa';
      break;
    case '19':
    case '21':
    case 19:
    case 21:
      currency = 'BHD';
      storeUrl = 'bh';
      break;
    case 51:
      currency = 'INR';
      storeUrl = 'in';
      break;
    default:
      break;
  }
  return {
    storeUrl,
    currency
  };
};

exports.formatPayfortAmount = ({ storeId, price }) => {
  const priceStr = String(price);
  const priceArr = priceStr.split('.');
  const priceNumber = Number(priceArr[0]);
  const priceDecimal = priceArr[1] || '0';

  console.log('priceDecimal', priceDecimal);
  console.log('priceNumber', priceNumber);
  let precision = 2;
  let decimalNumb = 0;

  switch (storeId) {
    case 12:
    case '12':
    case 13:
    case '13':
    case '19':
    case 19:
    case '21':
    case 21:
      precision = 3;
      break;
    default:
      break;
  }
  decimalNumb = priceDecimal.substr(0, precision).padEnd(precision, '0');
  console.log('decimalNumb', decimalNumb);
  return Number(`${priceNumber}${decimalNumb}`);
};

exports.countryCodeMap = code => {
  let anotherCode = '';
  switch (code) {
    case 'sa': {
      anotherCode = 'ksa';
      break;
    }
    case 'ae': {
      anotherCode = 'uae';
      break;
    }
    case 'kw': {
      anotherCode = 'kwt';
      break;
    }
    case 'qa': {
      anotherCode = 'qat';
      break;
    }
    case 'bh': {
      anotherCode = 'bah';
      break;
    }
    case 'in': {
      anotherCode = 'ind';
      break;
    }
    default:
      break;
  }
  return anotherCode;
};

exports.getConfig = (config, countryUC, identifier, type) => {
  let value = '';
  switch (identifier) {
    case 'merchantIdentifier': {
      value = config[`PAYFORT_TOKEN_${countryUC}_${type}_MERCHANT_IDENTIFIER`] || '';
      break;
    }
    case 'accessCode': {
      value = config[`PAYFORT_TOKEN_${countryUC}_${type}_ACCESS_CODE`] || '';
      break;
    }
    case 'requestSHA': {
      value =
        config[`PAYFORT_TOKEN_${countryUC}_${type}_REQ_PASSPHRASE`] ||
        config[`PAYFORT_TOKEN_${countryUC}_${type}_REQ_PASSPHARASE`] ||
        '';
      break;
    }
    case 'responseSHA': {
      value =
        config[`PAYFORT_TOKEN_${countryUC}_${type}_RES_PASSPHRASE`] ||
        config[`PAYFORT_TOKEN_${countryUC}_${type}_RES_PASSPHARASE`] ||
        '';
      break;
    }
    default:
      break;
  }
  return value;
};
exports.CASH_ON_DELIVERY = 'cashondelivery';
