const cache = require('memory-cache');
const { serializeError } = require('serialize-error');
const { createLogger, format, transports } = require('winston');
const cryptoRandomInt = require('crypto-random-int');
const toDecimals = require('round-to-decimal');
const _ = require('lodash');
const ValidationError = require("./errors/ValidationError");

exports.getNewQuoteId = async () => {
  const randomInt = await cryptoRandomInt(1, 1000);

  const randomId = String(new Date().getTime()) + randomInt;

  return randomId;
};

exports.getCurrency = ({ storeId }) => {
  const currencyMap = JSON.parse(process.env.CURRENCY_MAP);
  const currency = currencyMap[String(storeId)] || 'SAR';
  return currency;
};

exports.mappedObject = ({ dataArray, mapKey }) => {
  const mappedObj = {};
  for (const index in dataArray) {
    const obj = dataArray[index];
    mappedObj[obj[mapKey]] = obj;
  }
  return mappedObj;
};

exports.getPrice = ({ priceObj }) => {
  if (priceObj.default_original_formated == undefined) {
    return Number(priceObj.default);
  } else {
    const originalDefaultPrice = priceObj.default_original_formated.replace(
      /[^\d.-]/g,
      ''
    );
    return Number(
      originalDefaultPrice == 0 ? priceObj.default : originalDefaultPrice
    );
  }
};

exports.getSpecialPrice = ({ priceObj, price }) => {
  const specialPrice =
    priceObj.default_original_formated == undefined
      ? null
      : Number(priceObj.default);
  return Math.round(price * 100) == Math.round(specialPrice * 100)
    ? null
    : specialPrice;
};

exports.getChildPrice = ({ childProductObj }) => {
  return Number(childProductObj?.pr || 0);
};

exports.getChildSpecialPrice = ({ childProductObj, price }) => {
  const specialPrice =
    childProductObj?.sp === undefined ? null : Number(childProductObj?.sp);
  return Math.round(price * 100) == Math.round(specialPrice * 100)
    ? null
    : specialPrice;
};

exports.formatPriceOld = (price, locale = 'en-SA', options = {}) => {
  if (price === undefined) return null;
  return Number(Number(price).toFixed(2));
};

exports.formatPrice = price => {
  const priceStr = String(price || 0);
  let finalPrice;
  if (this.getAppConfigKey('quoteRoundOff')) {
    finalPrice = toDecimals(Number(priceStr), 2);
  } else {
    const priceArr = priceStr.split('.');
    const priceNumber = Number(priceArr[0]);
    const priceDecimal = priceArr[1] || '0';
    let precision = 2;
    let decimalNumb = 0;
    decimalNumb = priceDecimal.substr(0, precision).padEnd(precision, '0');
    finalPrice = Number(`${priceNumber}.${decimalNumb}`);
  }

  return finalPrice;
};

exports.getAppConfigKey = key => {
  const appCofig = this.getAppConfig();
  return appCofig && appCofig[key];
};

exports.getAppConfig = () => {
  const configCacheKey = process.env.CONSUL_KEY;
  const existingCache = cache.get(configCacheKey);
  return existingCache;
};

exports.getStoreConfig = (storeId, key = null) => {
  const appCofig = this.getAppConfig();
  const storeConfig =
    appCofig?.environments[0]?.stores?.filter(storeData => {
      return String(storeData.storeId) === String(storeId);
    })[0] || {};

  if (Array.isArray(key)) {
    // Return an object with all requested keys
    return key.reduce((result, item) => {
      result[item] = storeConfig[item] ?? '';
      return result;
    }, {});
  } else if (key) {
    // Handle single key case for backward compatibility
    return storeConfig[key] ?? '';
  } else {
    // Return entire config if no keys specified
    return storeConfig;
  }
};

exports.getAddressMapper = (storeId) => {
  const addressMapperKeySA = process.env.CONSUL_ADDRESS_MAPPER_KEY_SA;
  const addressMapperKeyKW = process.env.CONSUL_ADDRESS_MAPPER_KEY_KW;
  const addressMapperKeyAE = process.env.CONSUL_ADDRESS_MAPPER_KEY_AE;
  const addressMapperKeyBH = process.env.CONSUL_ADDRESS_MAPPER_KEY_BH;
  const addressMapperKeyQA = process.env.CONSUL_ADDRESS_MAPPER_KEY_QA;
  const addressMapperKeyOM = process.env.CONSUL_ADDRESS_MAPPER_KEY_OM;
  let addressMap;
  let addressMapperCacheKey = "";
  const formattedStoreId = Number(storeId);
  if (formattedStoreId === 1 || formattedStoreId === 3) {
    addressMapperCacheKey = addressMapperKeySA;
  } else if (formattedStoreId === 7 || formattedStoreId === 11) {
    addressMapperCacheKey = addressMapperKeyAE;
  } else if (formattedStoreId === 12 || formattedStoreId === 13) {
    addressMapperCacheKey = addressMapperKeyKW;
  } else if (formattedStoreId === 15 || formattedStoreId === 17) {
    addressMapperCacheKey = addressMapperKeyQA;
  } else if (formattedStoreId === 19 || formattedStoreId === 21) {
    addressMapperCacheKey = addressMapperKeyBH;
  } else if (formattedStoreId === 23 || formattedStoreId === 25) {
    addressMapperCacheKey = addressMapperKeyOM;
  } else if (formattedStoreId === 51) {
    addressMapperCacheKey = process.env.GCP_ADDRESS_MAPPER_KEY_IN;
  }
  if (addressMapperCacheKey) {
    addressMap = cache.get(addressMapperCacheKey);
  }

  return addressMap;
};

function simpleStringify(object) {
  try {
    const simpleObject = {};
    for (let prop in object) {
      if (!object.hasOwnProperty(prop)) {
        continue;
      }
      if (typeof object[prop] == 'object') {
        simpleObject[prop] = object[prop];
        // continue;
      }
      if (typeof object[prop] == 'function') {
        continue;
      }
      simpleObject[prop] = object[prop];
    }
    return JSON.stringify(simpleObject); // returns cleaned up JSON
  } catch (e) {
    return {};
  }
}

exports.logInfo = (param1, param2, xHeaderToken = '') => {
  // const message = param2 || param1;
  // const payload = {
  //   key: typeof param1 === 'object' ? simpleStringify(param1) : param1,
  //   message: typeof message === 'object' ? simpleStringify(message) : message,
  //   request: {
  //     headers: {
  //       'x-header-token': xHeaderToken
  //     }
  //   }
  // };
  // logger.info(`logInfo: ${JSON.stringify(payload)}`);
};

exports.logError = (e, msg = '', additionalData = '') => {
  const payload = {
    custom: {
      key: e.message,
      stack:e?.stack,
      message: typeof msg === 'object' ? simpleStringify(msg) : msg,
      additionalData:
        typeof additionalData === 'object'
          ? simpleStringify(additionalData)
          : additionalData
    }
  };
  if (global.logError) {
    global.logError(e, payload);
  }
  // console.error(JSON.stringify(payload));
};

exports.logErrorString = (e, msg = '', additionalData = '') => {
  const payload = {
    logtype: `error`,
    key: e?.message,
    additionalData: additionalData,
    message: msg,
    error_stack: serializeError(e),
  };
  if (global.logError) {
    global.logError(e, payload);
  }
  // let msgString;
  // try {
  //   msgString = JSON.stringify(payload);
  // } catch (error) {
  //   msgString = payload;
  // }
  // this.logger.error(msgString);
};

// const logger = require('./logger');
// exports.logger = logger; 

exports.sanitiseImageUrl = input => {
  const appCofig = this.getAppConfig();
  const baseurl = appCofig?.environments[0]?.baseurl;
  if (!input) return '';
  if (input.indexOf('/pub/') > -1) {
    let arr = input.split('/pub/');
    if (arr.length > 1) arr.shift();
    input = arr.join();
    return baseurl + '/' + input;
  } else if (input.startsWith('//')) {
    return 'https:' + input;
  } else {
    return input;
  }
};

exports.getQueryStoreIds = storeId => {
  const formattedStoreId = Number(storeId);
  let storeIds;
  switch (formattedStoreId) {
    case 7:
    case 11:
      storeIds = [7, 11].join("','");
      break;
    case 12:
    case 13:
      storeIds = [12, 13].join("','");
      break;
    case 15:
    case 17:
      storeIds = [15, 17].join("','");
      break;
    case 19:
    case 21:
      storeIds = [19, 21].join("','");
      break;
    case 23:
    case 25:
      storeIds = [23, 25].join("','");
      break;
    default:
      storeIds = [1, 3].join("','");
      break;
  }
  return storeIds;
};

/**
 * Retrive Payment Options at Store wise
 * @param {*} storeId
 * @returns
 */
exports.paymentMethodsFromConfig = storeId => {
  const configCacheKey = process.env.CONSUL_KEY;
  const configCacheResponse = cache.get(configCacheKey);
  const paymentMethods = {
   "enablePaymentOptimizations": true,
   "sendApplePayKey": true,
   "usePaymentService": true,
   "tamaraEligibilityCheck": false,
   "splitOrderAppVersion": "5.2.1000",
   "splitOrderWebEnabled": true,
   "credentials": {
      "httpTimeoutInMs": 2000,
      "ae": {
         "merchantCode": "uae_new",
         "oldMerchantCode": "uae",
         "apiUrl": "https://api.tabby.ai/api/v2/checkout",
         "apiPublicKey": "pk_test_a0e5339b-3fbd-4bee-9a69-d83301f77084",
         "successUrl": "https://qa.stylifashion.com/ae/LANG/checkout/payment/confirmation",
         "cancelUrl": "https://qa.stylifashion.com/ae/LANG/checkout/payment/replica",
         "failureUrl": "https://qa.stylifashion.com/ae/LANG/checkout/payment/replica",
         "appSuccessUrl": "styliapp://applinks/ae/checkout/payment/confirmation",
         "appCancelUrl": "styliapp://applinks/sa/checkout/payment/replica?cancel=cancel",
         "appFailureUrl": "styliapp://applinks/ae/checkout/payment/replica",
         "tamaraApiUrl": "https://api-sandbox.tamara.co/checkout",
         "tamaraToken": "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJhY2NvdW50SWQiOiJjMTQyMzBiMC1hMTgyLTRhZGYtYTFiYS1lOTRlZWM1MGFlMTciLCJ0eXBlIjoibWVyY2hhbnQiLCJzYWx0IjoiZWMwZTE1YWUwNmNjMTUzMWUzMDAzMmZiNGM0ZjMzZGQiLCJpYXQiOjE2MzY5NTk5ODUsImlzcyI6IlRhbWFyYSJ9.U00w9Btq_6mBZUdvBYbKugWcsckczJ-Gq7j1xW-5lHw6C-bmF_FcGFyTbFIez4VK6g-A3yl-_7zhOqPFWigFvA-vNSerVD2Tut82Cv5or5KE8TmWF3zL4XCjaYL0ikZd9-ctVArB9SuNynDZ5XZyMIAkjaMe_B_z56AKL8WR8f1bJ--9kOhA9_-seRprRGatzgw7sKxRg_Uhq7cs_bK4ryAvU2dU8fsZZsTIrNomOXNC_WsnL_LZ2qYNq_TJgAidkCcXXKVeJE-uL0APeygxBrdCW4B2uifvbafsNPHq_6_aSgbCGvfJYEZogHg5ACqYpr8KTAsdqbBPuqpW11Dp6Q",
         "tamaraNotificationUrl": "https://qa-api.stylifashion.com/rest/order/tamara/webhook",
         "tamarapublickey": "f072f1b2-129d-4084-a67d-78e6a337eba4",
         "methods": [
            "free",
            "apple_pay",
            "md_payfort_cc_vault",
            "md_payfort",
            "tabby_installments",
            "tamara_installments_3",
            "cashondelivery",
            "shukran_payment"
         ],
         "loyaltyLevels": [
            {
               "minOrder": 0,
               "maxOrder": 10,
               "level": 1
            },
            {
               "minOrder": 10,
               "maxOrder": 50,
               "level": 2
            },
            {
               "minOrder": 50,
               "maxOrder": 100,
               "level": 3
            }
         ]
      },
      "sa": {
         "merchantCode": "ksa",
         "oldMerchantCode": "ksa",
         "apiUrl": "https://api.tabby.ai/api/v2/checkout",
         "apiPublicKey": "pk_test_a0e5339b-3fbd-4bee-9a69-d83301f77084",
         "successUrl": "https://qa.stylifashion.com/sa/LANG/checkout/payment/confirmation",
         "cancelUrl": "https://qa.stylifashion.com/sa/LANG/checkout/payment/replica",
         "failureUrl": "https://qa.stylifashion.com/sa/LANG/checkout/payment/replica",
         "appSuccessUrl": "styliapp://applinks/sa/checkout/payment/confirmation",
         "appCancelUrl": "styliapp://applinks/sa/checkout/payment/replica?cancel=cancel",
         "appFailureUrl": "styliapp://applinks/sa/checkout/payment/replica",
         "tamaraApiUrl": "https://api-sandbox.tamara.co/checkout",
         "tamaraToken": "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJhY2NvdW50SWQiOiJjMTQyMzBiMC1hMTgyLTRhZGYtYTFiYS1lOTRlZWM1MGFlMTciLCJ0eXBlIjoibWVyY2hhbnQiLCJzYWx0IjoiZWMwZTE1YWUwNmNjMTUzMWUzMDAzMmZiNGM0ZjMzZGQiLCJpYXQiOjE2MzY5NTk5ODUsImlzcyI6IlRhbWFyYSJ9.U00w9Btq_6mBZUdvBYbKugWcsckczJ-Gq7j1xW-5lHw6C-bmF_FcGFyTbFIez4VK6g-A3yl-_7zhOqPFWigFvA-vNSerVD2Tut82Cv5or5KE8TmWF3zL4XCjaYL0ikZd9-ctVArB9SuNynDZ5XZyMIAkjaMe_B_z56AKL8WR8f1bJ--9kOhA9_-seRprRGatzgw7sKxRg_Uhq7cs_bK4ryAvU2dU8fsZZsTIrNomOXNC_WsnL_LZ2qYNq_TJgAidkCcXXKVeJE-uL0APeygxBrdCW4B2uifvbafsNPHq_6_aSgbCGvfJYEZogHg5ACqYpr8KTAsdqbBPuqpW11Dp6Q",
         "tamaraNotificationUrl": "https://qa-api.stylifashion.com/rest/order/tamara/webhook",
         "tamarapublickey": "f072f1b2-129d-4084-a67d-78e6a337eba4",
         "methods": [
            "free",
            "apple_pay",
            "md_payfort_cc_vault",
            "md_payfort",
            "cashondelivery",
            "tabby_installments",
            "tamara_installments_3",
            "shukran_payment"
         ],
         "loyaltyLevels": [
            {
               "minOrder": 0,
               "maxOrder": 10,
               "level": 1
            },
            {
               "minOrder": 10,
               "maxOrder": 50,
               "level": 2
            },
            {
               "minOrder": 50,
               "maxOrder": 100,
               "level": 3
            }
         ]
      },
      "kw": {
         "merchantCode": "kwt",
         "apiUrl": "https://api.tabby.ai/api/v2/checkout",
         "apiPublicKey": "pk_test_a0e5339b-3fbd-4bee-9a69-d83301f77084",
         "successUrl": "https://qa.stylifashion.com/kw/LANG/checkout/payment/confirmation",
         "cancelUrl": "https://qa.stylifashion.com/kw/LANG/checkout/payment/replica",
         "failureUrl": "https://qa.stylifashion.com/kw/LANG/checkout/payment/replica",
         "appSuccessUrl": "styliapp://applinks/sa/checkout/payment/confirmation",
         "appCancelUrl": "styliapp://applinks/sa/checkout/payment/replica",
         "appFailureUrl": "styliapp://applinks/sa/checkout/payment/replica",
         "methods": [
            "apple_pay",
            "free",
            "md_payfort_cc_vault",
            "md_payfort",
            "cashondelivery",
            "tamara_installments_3",
            "shukran_payment",
            "tabby_installments"
         ],
         "loyaltyLevels": [
            {
               "minOrder": 0,
               "maxOrder": 10,
               "level": 1
            },
            {
               "minOrder": 10,
               "maxOrder": 50,
               "level": 2
            },
            {
               "minOrder": 50,
               "maxOrder": 100,
               "level": 3
            }
         ]
      },
      "bh": {
         "merchantCode": "bhr",
         "apiUrl": "https://api.tabby.ai/api/v2/checkout",
         "apiPublicKey": "pk_test_a0e5339b-3fbd-4bee-9a69-d83301f77084",
         "successUrl": "https://qa.stylifashion.com/bh/LANG/checkout/payment/confirmation",
         "cancelUrl": "https://qa.stylifashion.com/bh/LANG/checkout/payment/replica",
         "failureUrl": "https://qa.stylifashion.com/bh/LANG/checkout/payment/replica",
         "appSuccessUrl": "styliapp://applinks/sa/checkout/payment/confirmation",
         "appCancelUrl": "styliapp://applinks/sa/checkout/payment/replica",
         "appFailureUrl": "styliapp://applinks/sa/checkout/payment/replica",
         "methods": [
            "apple_pay",
            "free",
            "md_payfort_cc_vault",
            "md_payfort",
            "cashondelivery",
            "shukran_payment",
            "tamara_installments_3",
            "shukran_payment"
         ],
         "loyaltyLevels": [
            {
               "minOrder": 0,
               "maxOrder": 10,
               "level": 1
            },
            {
               "minOrder": 10,
               "maxOrder": 50,
               "level": 2
            },
            {
               "minOrder": 50,
               "maxOrder": 100,
               "level": 3
            }
         ]
      },
      "qa": {
         "merchantCode": "qa",
         "apiUrl": "https://api.tabby.ai/api/v2/checkout",
         "apiPublicKey": "pk_test_a0e5339b-3fbd-4bee-9a69-d83301f77084",
         "successUrl": "https://qa.stylifashion.com/qa/LANG/checkout/payment/confirmation",
         "cancelUrl": "https://qa.stylifashion.com/qa/LANG/checkout/payment/replica",
         "failureUrl": "https://qa.stylifashion.com/qa/LANG/checkout/payment/replica",
         "appSuccessUrl": "styliapp://applinks/qa/checkout/payment/confirmation",
         "appCancelUrl": "styliapp://applinks/qa/checkout/payment/replica?cancel=cancel",
         "appFailureUrl": "styliapp://applinks/qa/checkout/payment/replica",
         "methods": [
            "free",
            "apple_pay",
            "md_payfort_cc_vault",
            "md_payfort",
            "cashondelivery"
         ],
         "loyaltyLevels": [
            {
               "minOrder": 0,
               "maxOrder": 10,
               "level": 1
            },
            {
               "minOrder": 10,
               "maxOrder": 50,
               "level": 2
            },
            {
               "minOrder": 50,
               "maxOrder": 100,
               "level": 3
            }
         ]
      }
   },
   "CODRestriction": {
      "sa": {
         "codRestrictionEnabled": false,
         "codRestrictionMin": 50,
         "codRestrictionMax": 500
      },
      "in": {
         "codRestrictionEnabled": true,
         "codRestrictionMin": 1000,
         "codRestrictionMax": 5000
      }
   }
}
  const stores = [
                {
                    "storeId": "1",
                    "orderSplitFlag": false,
                    "shipmentMode": [
                        "local",
                        "global"
                    ],
                    "shippingConfig": {
                        "local": {
                            "localShipmentAmount": 9,
                            "localShipmentThreshold": 115,
                            "shipmentMapping": "express"
                        },
                        "global": {
                            "globalShipmentAmount": 12,
                            "globalShipmentThreshold": 140,
                            "shipmentMapping": "global"
                        },
                        "minimumShippingCharge": 12,
                        "consolidatedOrderFreeShippingThreshold": 250
                    },
                    "defaultWarehouseId": "110",
                    "defaultFulfillmentMode": "express",
                    "isSLAEnabled": true,
                    "defaultDeliveryModelForThreshold": "express",
                    "enableDeliveryModelForThresholdCalc": false,
                    "enableNewFreeShippingConfig": true,
                    "searchSource": "elastic",
                    "isShukranEnable": true,
                    "shukranWelcomeBonous": 0,
                    "invoiceTerritory": "Kingdom of Saudi Arabia",
                    "shukranQP": 250,
                    "shukranQPMessage": "Fantastic! It’s a qualifying purchase, You’re one step closer to {TIER_NAME} tier",
                    "shukranProfileMessage": "{QP} purchases before {DATE} to {ACTIVITY} {TIER_NAME} Tier",
                    "shukranProfileDetailsMessage": "{QP} purchases to {ACTIVITY} {TIER_NAME}",
                    "shukranStoreCode": "SA",
                    "shukranCurrencyCode": "SAU",
                    "shukranConfig": {
                        "sale": {
                            "conversion_factor": 0.08696
                        },
                        "non_sale": {
                            "conversion_factor": 0.17391
                        }
                    },
                    "shukranPointConversion": 0.05,
                    "styliCashPointConversion": 0.9,
                    "refundDeduction": 12,
                    "isSecondRefund": true,
                    "storeCode": "en",
                    "storeLanguage": "en_US",
                    "storeCurrency": "SAR",
                    "shipmentChargesThreshold": 120,
                    "shipmentCharges": 10,
                    "codCharges": 10,
                    "taxPercentage": 15,
                    "websiteId": 1,
                    "websiteIdentifier": "sa/en",
                    "storeName": "Saudi Arabia",
                    "websiteCode": "sa",
                    "countryCode": "+966",
                    "currencyConversionRate": 1,
                    "termsAndUse": "https://stylishop.com/en/legal/user-agreement",
                    "privecyPolicy": "https://stylishop.com/en/legal/privacy",
                    "helpCentreAndFaq": "https://stylishop.com/en/helpcentre",
                    "contract": "https://stylishop.com/en/contact",
                    "customDutiesPercentage": 0,
                    "importFeePercentage": 0,
                    "minimumDutiesAmount": 0,
                    "flagUrl": "https://bucket.stylishop.com/flags/flag_sa.png",
                    "quoteProductMaxAddedQty": 3,
                    "importMaxFeePercentage": 0,
                    "catalogCurrencyConversionRate": 1,
                    "decimalPricing": false,
                    "phoneNumberValidation": {
                        "maxLength": 10,
                        "actualLength": 9,
                        "lableHintNumber": "501234567",
                        "validation": [
                            {
                                "zeroInitialIndex": true,
                                "regex": [
                                    "(^(?:[0])?([0-9]{10})$)"
                                ]
                            },
                            {
                                "zeroInitialIndex": false,
                                "regex": [
                                    "(^([1-9]){1}([0-9]{8})$)"
                                ]
                            }
                        ]
                    },
                    "warehouseId": 110,
                    "mapperTable": "sa_inventory",
                    "rmaapplicableThreshold": 0,
                    "showAddLowValueAmount": 50,
                    "minimumFirstOrderValue": 150,
                    "holdOrder": true,
                    "isPayfortAuthorized": true,
                    "enableApplepayholdOrder": true
                },
                {
                    "storeId": "3",
                    "searchSource": "elastic",
                    "orderSplitFlag": true,
                    "shipmentMode": [
                        "local",
                        "global"
                    ],
                    "shippingConfig": {
                        "local": {
                            "localShipmentAmount": 10,
                            "localShipmentThreshold": 150,
                            "shipmentMapping": "express"
                        },
                        "global": {
                            "globalShipmentAmount": 15,
                            "globalShipmentThreshold": 200,
                            "shipmentMapping": "global"
                        },
                        "minimumShippingCharge": 12,
                        "consolidatedOrderFreeShippingThreshold": 250
                    },
                    "defaultWarehouseId": "110",
                    "defaultFulfillmentMode": "express",
                    "defaultDeliveryModelForThreshold": "express",
                    "enableDeliveryModelForThresholdCalc": false,
                    "enableNewFreeShippingConfig": true,
                    "isShukranEnable": true,
                    "shukranQP": 250,
                    "invoiceTerritory": "Kingdom of Saudi Arabia",
                    "shukranWelcomeBonous": 0,
                    "shukranQPMessage": "Fantastic! It’s a qualifying purchase, You’re one step closer to {TIER_NAME} tier",
                    "shukranProfileMessage": "{QP} purchases before {DATE} to {ACTIVITY} {TIER_NAME} Tier",
                    "shukranProfileDetailsMessage": "{QP} purchases to {ACTIVITY} {TIER_NAME}",
                    "shukranStoreCode": "SA",
                    "shukranCurrencyCode": "SAU",
                    "shukranConfig": {
                        "sale": {
                            "conversion_factor": 0.08696
                        },
                        "non_sale": {
                            "conversion_factor": 0.17391
                        }
                    },
                    "shukranPointConversion": 0.05,
                    "styliCashPointConversion": 1,
                    "refundDeduction": 12,
                    "isSecondRefund": true,
                    "storeCode": "ar",
                    "storeLanguage": "ar_SA",
                    "storeCurrency": "SAR",
                    "shipmentChargesThreshold": 120,
                    "shipmentCharges": 12,
                    "codCharges": 10,
                    "taxPercentage": 15,
                    "websiteId": 1,
                    "websiteIdentifier": "sa/ar",
                    "storeName": "السعودية",
                    "websiteCode": "sa",
                    "countryCode": "+966",
                    "currencyConversionRate": 1,
                    "termsAndUse": "https://stylishop.com/ar/legal/user-agreement",
                    "privecyPolicy": "https://stylishop.com/ar/legal/privacy",
                    "helpCentreAndFaq": "https://stylishop.com/ar/helpcentre",
                    "contract": "https://stylishop.com/ar/contact",
                    "customDutiesPercentage": 0,
                    "importFeePercentage": 0,
                    "minimumDutiesAmount": 0,
                    "flagUrl": "https://bucket.stylishop.com/flags/flag_sa.png",
                    "quoteProductMaxAddedQty": 3,
                    "importMaxFeePercentage": 0,
                    "catalogCurrencyConversionRate": 1,
                    "decimalPricing": false,
                    "phoneNumberValidation": {
                        "maxLength": 10,
                        "actualLength": 9,
                        "lableHintNumber": "501234567",
                        "validation": [
                            {
                                "zeroInitialIndex": true,
                                "regex": [
                                    "(^(?:[0])?([0-9]{10})$)"
                                ]
                            },
                            {
                                "zeroInitialIndex": false,
                                "regex": [
                                    "(^([1-9]){1}([0-9]{8})$)"
                                ]
                            }
                        ]
                    },
                    "warehouseId": 110,
                    "mapperTable": "sa_inventory",
                    "rmaapplicableThreshold": 0,
                    "showAddLowValueAmount": 50,
                    "minimumFirstOrderValue": 150,
                    "holdOrder": true,
                    "isPayfortAuthorized": true,
                    "enableApplepayholdOrder": true
                },
                {
                    "storeId": "7",
                    "searchSource": "elastic",
                    "orderSplitFlag": true,
                    "shipmentMode": [
                        "local",
                        "global"
                    ],
                    "shippingConfig": {
                        "local": {
                            "localShipmentAmount": 10,
                            "localShipmentThreshold": 150,
                            "shipmentMapping": "express"
                        },
                        "global": {
                            "globalShipmentAmount": 15,
                            "globalShipmentThreshold": 200,
                            "shipmentMapping": "global"
                        },
                        "minimumShippingCharge": 12,
                        "consolidatedOrderFreeShippingThreshold": 250
                    },
                    "defaultWarehouseId": "110",
                    "defaultFulfillmentMode": "express",
                    "defaultDeliveryModelForThreshold": "express",
                    "enableDeliveryModelForThresholdCalc": false,
                    "enableNewFreeShippingConfig": true,
                    "shukranConfig": {
                        "sale": {
                            "conversion_factor": 0.1
                        },
                        "non_sale": {
                            "conversion_factor": 0.2
                        }
                    },
                    "shukranPointConversion": 0.05,
                    "styliCashPointConversion": 0.5,
                    "shukranWelcomeBonous": 0,
                    "isShukranEnable": true,
                    "invoiceTerritory": "United Arab Emirates",
                    "shukranQP": 250,
                    "shukranQPMessage": "Fantastic! It’s a qualifying purchase, You’re one step closer to {TIER_NAME} tier",
                    "shukranProfileMessage": "{QP} purchases before {DATE} to {ACTIVITY} {TIER_NAME} Tier",
                    "shukranProfileDetailsMessage": "{QP} purchases to {ACTIVITY} {TIER_NAME}",
                    "shukranStoreCode": "AE",
                    "shukranCurrencyCode": "ARE",
                    "refundDeduction": 12,
                    "isSecondRefund": true,
                    "storeCode": "ae_en",
                    "storeLanguage": "en_US",
                    "storeCurrency": "AED",
                    "shipmentChargesThreshold": 99,
                    "shipmentCharges": 12,
                    "codCharges": 10,
                    "taxPercentage": 0,
                    "websiteId": 3,
                    "websiteIdentifier": "ae/en",
                    "storeName": "United Arab Emirates",
                    "websiteCode": "ae",
                    "countryCode": "+971",
                    "currencyConversionRate": 1.02,
                    "termsAndUse": "https://stylishop.com/ae_en/legal/user-agreement",
                    "privecyPolicy": "https://stylishop.com/ae_en/legal/privacy",
                    "helpCentreAndFaq": "https://stylishop.com/ae_en/helpcentre",
                    "contract": "https://stylishop.com/ae_en/contact",
                    "customDutiesPercentage": 5,
                    "importFeePercentage": 0,
                    "minimumDutiesAmount": 1000,
                    "flagUrl": "https://bucket.stylishop.com/flags/flag_ae.png",
                    "quoteProductMaxAddedQty": 3,
                    "importMaxFeePercentage": 0,
                    "catalogCurrencyConversionRate": 0.98,
                    "decimalPricing": false,
                    "phoneNumberValidation": {
                        "maxLength": 10,
                        "actualLength": 9,
                        "lableHintNumber": "501234567",
                        "validation": [
                            {
                                "zeroInitialIndex": true,
                                "regex": [
                                    "(^(?:[0])?([0-9]{10})$)"
                                ]
                            },
                            {
                                "zeroInitialIndex": false,
                                "regex": [
                                    "(^([1-9]){1}([0-9]{8})$)"
                                ]
                            }
                        ]
                    },
                    "warehouseId": 110,
                    "mapperTable": "sa_inventory",
                    "rmaapplicableThreshold": 0,
                    "showAddLowValueAmount": 50,
                    "minimumFirstOrderValue": 150,
                    "holdOrder": true,
                    "isPayfortAuthorized": true,
                    "enableApplepayholdOrder": true
                },
                {
                    "storeId": "11",
                    "searchSource": "elastic",
                    "orderSplitFlag": true,
                    "shipmentMode": [
                        "local",
                        "global"
                    ],
                    "shippingConfig": {
                        "local": {
                            "localShipmentAmount": 10,
                            "localShipmentThreshold": 150,
                            "shipmentMapping": "express"
                        },
                        "global": {
                            "globalShipmentAmount": 15,
                            "globalShipmentThreshold": 200,
                            "shipmentMapping": "global"
                        },
                        "minimumShippingCharge": 12,
                        "consolidatedOrderFreeShippingThreshold": 250
                    },
                    "defaultWarehouseId": "110",
                    "defaultFulfillmentMode": "express",
                    "defaultDeliveryModelForThreshold": "express",
                    "enableDeliveryModelForThresholdCalc": false,
                    "enableNewFreeShippingConfig": true,
                    "shukranConfig": {
                        "sale": {
                            "conversion_factor": 0.1
                        },
                        "non_sale": {
                            "conversion_factor": 0.2
                        }
                    },
                    "isShukranEnable": true,
                    "invoiceTerritory": "United Arab Emirates",
                    "shukranQP": 250,
                    "shukranWelcomeBonous": 0,
                    "shukranQPMessage": "Fantastic! It’s a qualifying purchase, You’re one step closer to {TIER_NAME} tier",
                    "shukranProfileMessage": "{QP} purchases before {DATE} to {ACTIVITY} {TIER_NAME} Tier",
                    "shukranProfileDetailsMessage": "{QP} purchases to {ACTIVITY} {TIER_NAME}",
                    "shukranPointConversion": 0.05,
                    "styliCashPointConversion": 0.5,
                    "shukranStoreCode": "AE",
                    "shukranCurrencyCode": "ARE",
                    "refundDeduction": 12,
                    "isSecondRefund": true,
                    "storeCode": "ae_ar",
                    "storeLanguage": "ar_SA",
                    "storeCurrency": "AED",
                    "shipmentChargesThreshold": 160,
                    "shipmentCharges": 12,
                    "codCharges": 10,
                    "taxPercentage": 0,
                    "websiteId": 3,
                    "websiteIdentifier": "ae/ar",
                    "storeName": "الإمارات",
                    "websiteCode": "ae",
                    "countryCode": "+971",
                    "currencyConversionRate": 1.02,
                    "termsAndUse": "https://stylishop.com/ae_ar/legal/user-agreement",
                    "privecyPolicy": "https://stylishop.com/ae_ar/legal/privacy",
                    "helpCentreAndFaq": "https://stylishop.com/ae_ar/helpcentre",
                    "contract": "https://stylishop.com/ae_ar/contact",
                    "customDutiesPercentage": 5,
                    "importFeePercentage": 0,
                    "minimumDutiesAmount": 1000,
                    "flagUrl": "https://bucket.stylishop.com/flags/flag_ae.png",
                    "quoteProductMaxAddedQty": 3,
                    "importMaxFeePercentage": 0,
                    "catalogCurrencyConversionRate": 0.98,
                    "decimalPricing": false,
                    "phoneNumberValidation": {
                        "maxLength": 10,
                        "actualLength": 9,
                        "lableHintNumber": "501234567",
                        "validation": [
                            {
                                "zeroInitialIndex": true,
                                "regex": [
                                    "(^(?:[0])?([0-9]{10})$)"
                                ]
                            },
                            {
                                "zeroInitialIndex": false,
                                "regex": [
                                    "(^([1-9]){1}([0-9]{8})$)"
                                ]
                            }
                        ]
                    },
                    "warehouseId": 110,
                    "mapperTable": "sa_inventory",
                    "rmaapplicableThreshold": 0,
                    "showAddLowValueAmount": 50,
                    "minimumFirstOrderValue": 150,
                    "holdOrder": true,
                    "isPayfortAuthorized": true,
                    "enableApplepayholdOrder": true
                },
                {
                    "storeId": "12",
                    "searchSource": "elastic",
                    "orderSplitFlag": true,
                    "shipmentMode": [
                        "local",
                        "global"
                    ],
                    "shippingConfig": {
                        "local": {
                            "localShipmentAmount": 10,
                            "localShipmentThreshold": 150,
                            "shipmentMapping": "express"
                        },
                        "global": {
                            "globalShipmentAmount": 15,
                            "globalShipmentThreshold": 200,
                            "shipmentMapping": "global"
                        },
                        "minimumShippingCharge": 12,
                        "consolidatedOrderFreeShippingThreshold": 250
                    },
                    "defaultWarehouseId": "110",
                    "defaultFulfillmentMode": "express",
                    "defaultDeliveryModelForThreshold": "express",
                    "enableDeliveryModelForThresholdCalc": false,
                    "enableNewFreeShippingConfig": true,
                    "shukranConfig": {
                        "sale": {
                            "conversion_factor": 1.25
                        },
                        "non_sale": {
                            "conversion_factor": 2.5
                        }
                    },
                    "isShukranEnable": true,
                    "invoiceTerritory": "Kuwait",
                    "shukranQP": 25,
                    "shukranStoreCode": "KW",
                    "shukranWelcomeBonous": 0,
                    "shukranQPMessage": "Fantastic! It’s a qualifying purchase, You’re one step closer to {TIER_NAME} tier",
                    "shukranProfileMessage": "{QP} purchases before {DATE} to {ACTIVITY} {TIER_NAME} Tier",
                    "shukranProfileDetailsMessage": "{QP} purchases to {ACTIVITY} {TIER_NAME}",
                    "shukranPointConversion": 0.004,
                    "styliCashPointConversion": 0.75,
                    "shukranCurrencyCode": "KWT",
                    "refundDeduction": 0,
                    "isSecondRefund": false,
                    "storeCode": "kw_en",
                    "storeLanguage": "en_US",
                    "storeCurrency": "KWD",
                    "shipmentChargesThreshold": 20,
                    "shipmentCharges": 1.5,
                    "codCharges": 1.5,
                    "taxPercentage": 0,
                    "websiteId": 4,
                    "websiteIdentifier": "kw/en",
                    "storeName": "Kuwait",
                    "websiteCode": "kw",
                    "countryCode": "+965",
                    "currencyConversionRate": 12.2,
                    "termsAndUse": "https://stylishop.com/kw_en/legal/user-agreement",
                    "privecyPolicy": "https://stylishop.com/kw_en/legal/privacy",
                    "helpCentreAndFaq": "https://stylishop.com/kw_en/helpcentre",
                    "contract": "https://stylishop.com/kw_en/contact",
                    "customDutiesPercentage": 0,
                    "importFeePercentage": 2.5,
                    "minimumDutiesAmount": 100,
                    "flagUrl": "https://bucket.stylishop.com/flags/flag_kw.png",
                    "quoteProductMaxAddedQty": 3,
                    "importMaxFeePercentage": 7.5,
                    "catalogCurrencyConversionRate": 0.0816,
                    "decimalPricing": true,
                    "phoneNumberValidation": {
                        "maxLength": 9,
                        "actualLength": 8,
                        "lableHintNumber": "50123456",
                        "validation": [
                            {
                                "zeroInitialIndex": true,
                                "regex": [
                                    "(^(?:[0])?([0-9]{9})$)"
                                ]
                            },
                            {
                                "zeroInitialIndex": false,
                                "regex": [
                                    "(^([1-9]){1}([0-9]{7})$)"
                                ]
                            }
                        ]
                    },
                    "warehouseId": 110,
                    "mapperTable": "sa_inventory",
                    "rmaapplicableThreshold": 0,
                    "holdOrder": true,
                    "isPayfortAuthorized": false,
                    "enableApplepayholdOrder": true
                },
                {
                    "storeId": "13",
                    "searchSource": "elastic",
                    "orderSplitFlag": true,
                    "shipmentMode": [
                        "local",
                        "global"
                    ],
                    "shippingConfig": {
                        "local": {
                            "localShipmentAmount": 10,
                            "localShipmentThreshold": 150,
                            "shipmentMapping": "express"
                        },
                        "global": {
                            "globalShipmentAmount": 15,
                            "globalShipmentThreshold": 200,
                            "shipmentMapping": "global"
                        },
                        "minimumShippingCharge": 12,
                        "consolidatedOrderFreeShippingThreshold": 250
                    },
                    "defaultWarehouseId": "110",
                    "defaultFulfillmentMode": "express",
                    "defaultDeliveryModelForThreshold": "express",
                    "enableDeliveryModelForThresholdCalc": false,
                    "enableNewFreeShippingConfig": true,
                    "shukranConfig": {
                        "sale": {
                            "conversion_factor": 1.25
                        },
                        "non_sale": {
                            "conversion_factor": 2.5
                        }
                    },
                    "isShukranEnable": true,
                    "shukranQP": 25,
                    "invoiceTerritory": "Kuwait",
                    "shukranStoreCode": "KW",
                    "shukranWelcomeBonous": 0,
                    "shukranQPMessage": "Fantastic! It’s a qualifying purchase, You’re one step closer to {TIER_NAME} tier",
                    "shukranProfileMessage": "{QP} purchases before {DATE} to {ACTIVITY} {TIER_NAME} Tier",
                    "shukranProfileDetailsMessage": "{QP} purchases to {ACTIVITY} {TIER_NAME}",
                    "shukranPointConversion": 0.004,
                    "styliCashPointConversion": 0.75,
                    "shukranCurrencyCode": "KWT",
                    "refundDeduction": 0,
                    "isSecondRefund": false,
                    "storeCode": "kw_ar",
                    "storeLanguage": "ar_SA",
                    "storeCurrency": "KWD",
                    "shipmentChargesThreshold": 20,
                    "shipmentCharges": 1.5,
                    "codCharges": 1.5,
                    "taxPercentage": 0,
                    "websiteId": 4,
                    "websiteIdentifier": "kw/ar",
                    "storeName": "الكويت‎",
                    "websiteCode": "kw",
                    "countryCode": "+965",
                    "currencyConversionRate": 12.2,
                    "termsAndUse": "https://stylishop.com/kw_ar/legal/user-agreement",
                    "privecyPolicy": "https://stylishop.com/kw_ar/legal/privacy",
                    "helpCentreAndFaq": "https://stylishop.com/kw_ar/helpcentre",
                    "contract": "https://stylishop.com/kw_ar/contact",
                    "customDutiesPercentage": 0,
                    "importFeePercentage": 2.5,
                    "minimumDutiesAmount": 100,
                    "flagUrl": "https://bucket.stylishop.com/flags/flag_kw.png",
                    "quoteProductMaxAddedQty": 3,
                    "importMaxFeePercentage": 7.5,
                    "catalogCurrencyConversionRate": 0.0816,
                    "decimalPricing": true,
                    "phoneNumberValidation": {
                        "maxLength": 9,
                        "actualLength": 8,
                        "lableHintNumber": "50123456",
                        "validation": [
                            {
                                "zeroInitialIndex": true,
                                "regex": [
                                    "(^(?:[0])?([0-9]{9})$)"
                                ]
                            },
                            {
                                "zeroInitialIndex": false,
                                "regex": [
                                    "(^([1-9]){1}([0-9]{7})$)"
                                ]
                            }
                        ]
                    },
                    "warehouseId": 110,
                    "mapperTable": "sa_inventory",
                    "rmaapplicableThreshold": 0,
                    "holdOrder": true,
                    "isPayfortAuthorized": false,
                    "enableApplepayholdOrder": true
                },
                {
                    "storeId": "15",
                    "searchSource": "elastic",
                    "orderSplitFlag": true,
                    "shipmentMode": [
                        "local",
                        "global"
                    ],
                    "shippingConfig": {
                        "local": {
                            "localShipmentAmount": 10,
                            "localShipmentThreshold": 150,
                            "shipmentMapping": "express"
                        },
                        "global": {
                            "globalShipmentAmount": 15,
                            "globalShipmentThreshold": 200,
                            "shipmentMapping": "global"
                        },
                        "minimumShippingCharge": 12,
                        "consolidatedOrderFreeShippingThreshold": 250
                    },
                    "defaultWarehouseId": "110",
                    "defaultFulfillmentMode": "express",
                    "defaultDeliveryModelForThreshold": "express",
                    "enableDeliveryModelForThresholdCalc": false,
                    "enableNewFreeShippingConfig": true,
                    "refundDeduction": 0,
                    "isSecondRefund": false,
                    "storeCode": "qa_en",
                    "storeLanguage": "en_US",
                    "storeCurrency": "QAR",
                    "shipmentCharges": 3,
                    "codCharges": 15,
                    "taxPercentage": 0,
                    "websiteId": 5,
                    "websiteIdentifier": "qa/en",
                    "storeName": "Qatar",
                    "websiteCode": "qa",
                    "countryCode": "+974",
                    "currencyConversionRate": 1.03,
                    "termsAndUse": "https://stylishop.com/qa_en/legal/user-agreement",
                    "privecyPolicy": "https://stylishop.com/qa_en/legal/privacy",
                    "helpCentreAndFaq": "https://stylishop.com/qa_en/helpcentre",
                    "contract": "https://stylishop.com/qa_en/contact",
                    "customDutiesPercentage": 0,
                    "importFeePercentage": 2.5,
                    "minimumDutiesAmount": 3000,
                    "flagUrl": "https://bucket.stylishop.com/flags/flag_qa.png",
                    "quoteProductMaxAddedQty": 3,
                    "importMaxFeePercentage": 6,
                    "catalogCurrencyConversionRate": 1.03,
                    "decimalPricing": false,
                    "phoneNumberValidation": {
                        "maxLength": 9,
                        "actualLength": 8,
                        "lableHintNumber": "50123456",
                        "validation": [
                            {
                                "zeroInitialIndex": true,
                                "regex": [
                                    "(^(?:[0])?([0-9]{9})$)"
                                ]
                            },
                            {
                                "zeroInitialIndex": false,
                                "regex": [
                                    "(^([1-9]){1}([0-9]{7})$)"
                                ]
                            }
                        ]
                    },
                    "warehouseId": 110,
                    "mapperTable": "sa_inventory",
                    "rmaapplicableThreshold": 0,
                    "holdOrder": false,
                    "isPayfortAuthorized": false,
                    "enableApplepayholdOrder": false,
                    "shipmentChargesThreshold": 190,
                    "styliCashPointConversion": 0.5
                },
                {
                    "storeId": "17",
                    "searchSource": "elastic",
                    "orderSplitFlag": true,
                    "shipmentMode": [
                        "local",
                        "global"
                    ],
                    "shippingConfig": {
                        "local": {
                            "localShipmentAmount": 10,
                            "localShipmentThreshold": 150,
                            "shipmentMapping": "express"
                        },
                        "global": {
                            "globalShipmentAmount": 15,
                            "globalShipmentThreshold": 200,
                            "shipmentMapping": "global"
                        },
                        "minimumShippingCharge": 12,
                        "consolidatedOrderFreeShippingThreshold": 250
                    },
                    "defaultWarehouseId": "110",
                    "defaultFulfillmentMode": "express",
                    "defaultDeliveryModelForThreshold": "express",
                    "enableDeliveryModelForThresholdCalc": false,
                    "enableNewFreeShippingConfig": true,
                    "refundDeduction": 0,
                    "isSecondRefund": false,
                    "storeCode": "qa_ar",
                    "storeLanguage": "ar_SA",
                    "storeCurrency": "QAR",
                    "shipmentCharges": 3,
                    "codCharges": 15,
                    "taxPercentage": 0,
                    "websiteId": 5,
                    "websiteIdentifier": "qa/ar",
                    "storeName": "دولة قطر",
                    "websiteCode": "qa",
                    "countryCode": "+974",
                    "currencyConversionRate": 1.03,
                    "termsAndUse": "https://stylishop.com/qa_ar/legal/user-agreement",
                    "privecyPolicy": "https://stylishop.com/qa_ar/legal/privacy",
                    "helpCentreAndFaq": "https://stylishop.com/qa_ar/helpcentre",
                    "contract": "https://stylishop.com/kw_ar/contact",
                    "customDutiesPercentage": 0,
                    "importFeePercentage": 2.5,
                    "minimumDutiesAmount": 3000,
                    "flagUrl": "https://bucket.stylishop.com/flags/flag_qa.png",
                    "quoteProductMaxAddedQty": 3,
                    "importMaxFeePercentage": 6,
                    "catalogCurrencyConversionRate": 1.03,
                    "decimalPricing": false,
                    "phoneNumberValidation": {
                        "maxLength": 9,
                        "actualLength": 8,
                        "lableHintNumber": "50123456",
                        "validation": [
                            {
                                "zeroInitialIndex": true,
                                "regex": [
                                    "(^(?:[0])?([0-9]{9})$)"
                                ]
                            },
                            {
                                "zeroInitialIndex": false,
                                "regex": [
                                    "(^([1-9]){1}([0-9]{7})$)"
                                ]
                            }
                        ]
                    },
                    "warehouseId": 110,
                    "mapperTable": "sa_inventory",
                    "rmaapplicableThreshold": 0,
                    "holdOrder": false,
                    "isPayfortAuthorized": false,
                    "enableApplepayholdOrder": false,
                    "shipmentChargesThreshold": 190,
                    "styliCashPointConversion": 0.5
                },
                {
                    "storeId": "19",
                    "searchSource": "elastic",
                    "orderSplitFlag": true,
                    "shipmentMode": [
                        "local",
                        "global"
                    ],
                    "shippingConfig": {
                        "local": {
                            "localShipmentAmount": 10,
                            "localShipmentThreshold": 150,
                            "shipmentMapping": "express"
                        },
                        "global": {
                            "globalShipmentAmount": 15,
                            "globalShipmentThreshold": 200,
                            "shipmentMapping": "global"
                        },
                        "minimumShippingCharge": 12,
                        "consolidatedOrderFreeShippingThreshold": 250
                    },
                    "defaultWarehouseId": "110",
                    "defaultFulfillmentMode": "express",
                    "defaultDeliveryModelForThreshold": "express",
                    "enableDeliveryModelForThresholdCalc": false,
                    "enableNewFreeShippingConfig": true,
                    "isShukranEnable": true,
                    "shukranQP": 23,
                    "shukranStoreCode": "BH",
                    "invoiceTerritory": "Bahrain",
                    "shukranWelcomeBonous": 0,
                    "shukranQPMessage": "Fantastic! It’s a qualifying purchase, You’re one step closer to {TIER_NAME} tier",
                    "shukranProfileMessage": "{QP} purchases before {DATE} to {ACTIVITY} {TIER_NAME} Tier",
                    "shukranProfileDetailsMessage": "{QP} purchases to {ACTIVITY} {TIER_NAME}",
                    "shukranPointConversion": 0.005,
                    "styliCashPointConversion": 0.65,
                    "shukranCurrencyCode": "BHR",
                    "shukranConfig": {
                        "sale": {
                            "conversion_factor": 1
                        },
                        "non_sale": {
                            "conversion_factor": 2
                        }
                    },
                    "refundDeduction": 0,
                    "isSecondRefund": false,
                    "storeCode": "bh_en",
                    "storeLanguage": "en_US",
                    "storeCurrency": "BHD",
                    "shipmentChargesThreshold": 20,
                    "shipmentCharges": 1.2,
                    "codCharges": 1.2,
                    "taxPercentage": 0,
                    "websiteId": 7,
                    "websiteIdentifier": "bh/en",
                    "storeName": "Bahrain",
                    "websiteCode": "bh",
                    "countryCode": "+973",
                    "currencyConversionRate": 10,
                    "termsAndUse": "https://stylishop.com/bh_en/legal/user-agreement",
                    "privecyPolicy": "https://stylishop.com/bh_en/legal/privacy",
                    "helpCentreAndFaq": "https://stylishop.com/bh_en/helpcentre",
                    "contract": "https://stylishop.com/bh_en/contact",
                    "customDutiesPercentage": 0,
                    "importFeePercentage": 2,
                    "minimumDutiesAmount": 300,
                    "flagUrl": "https://bucket.stylishop.com/flags/flag_bh.png",
                    "quoteProductMaxAddedQty": 3,
                    "importMaxFeePercentage": 6,
                    "catalogCurrencyConversionRate": 0.1,
                    "decimalPricing": true,
                    "phoneNumberValidation": {
                        "maxLength": 9,
                        "actualLength": 8,
                        "lableHintNumber": "50123456",
                        "validation": [
                            {
                                "zeroInitialIndex": true,
                                "regex": [
                                    "(^(?:[0])?([0-9]{9})$)"
                                ]
                            },
                            {
                                "zeroInitialIndex": false,
                                "regex": [
                                    "(^([1-9]){1}([0-9]{7})$)"
                                ]
                            }
                        ]
                    },
                    "warehouseId": 110,
                    "mapperTable": "sa_inventory",
                    "rmaapplicableThreshold": 0,
                    "holdOrder": false,
                    "isPayfortAuthorized": false,
                    "enableApplepayholdOrder": false
                },
                {
                    "storeId": "21",
                    "searchSource": "elastic",
                    "orderSplitFlag": true,
                    "shipmentMode": [
                        "local",
                        "global"
                    ],
                    "shippingConfig": {
                        "local": {
                            "localShipmentAmount": 10,
                            "localShipmentThreshold": 150,
                            "shipmentMapping": "express"
                        },
                        "global": {
                            "globalShipmentAmount": 15,
                            "globalShipmentThreshold": 200,
                            "shipmentMapping": "global"
                        },
                        "minimumShippingCharge": 12,
                        "consolidatedOrderFreeShippingThreshold": 250
                    },
                    "defaultWarehouseId": "110",
                    "defaultFulfillmentMode": "express",
                    "defaultDeliveryModelForThreshold": "express",
                    "enableDeliveryModelForThresholdCalc": false,
                    "enableNewFreeShippingConfig": true,
                    "shukranConfig": {
                        "sale": {
                            "conversion_factor": 1
                        },
                        "non_sale": {
                            "conversion_factor": 2
                        }
                    },
                    "isShukranEnable": true,
                    "invoiceTerritory": "Bahrain",
                    "shukranQP": 23,
                    "shukranWelcomeBonous": 0,
                    "shukranQPMessage": "Fantastic! It’s a qualifying purchase, You’re one step closer to {TIER_NAME} tier",
                    "shukranProfileMessage": "{QP} purchases before {DATE} to {ACTIVITY} {TIER_NAME} Tier",
                    "shukranProfileDetailsMessage": "{QP} purchases to {ACTIVITY} {TIER_NAME}",
                    "shukranPointConversion": 0.005,
                    "styliCashPointConversion": 0.65,
                    "shukranStoreCode": "BH",
                    "shukranCurrencyCode": "BHR",
                    "refundDeduction": 0,
                    "isSecondRefund": false,
                    "storeCode": "bh_ar",
                    "storeLanguage": "ar_SA",
                    "storeCurrency": "BHD",
                    "shipmentChargesThreshold": 20,
                    "shipmentCharges": 1.2,
                    "codCharges": 1.2,
                    "taxPercentage": 0,
                    "websiteId": 7,
                    "websiteIdentifier": "bh/ar",
                    "storeName": "البحرين",
                    "websiteCode": "bh",
                    "countryCode": "+973",
                    "currencyConversionRate": 10,
                    "termsAndUse": "https://stylishop.com/bh_ar/legal/user-agreement",
                    "privecyPolicy": "https://stylishop.com/bh_ar/legal/privacy",
                    "helpCentreAndFaq": "https://stylishop.com/bh_ar/helpcentre",
                    "contract": "https://stylishop.com/bh_ar/contact",
                    "customDutiesPercentage": 0,
                    "importFeePercentage": 2,
                    "minimumDutiesAmount": 300,
                    "flagUrl": "https://bucket.stylishop.com/flags/flag_bh.png",
                    "quoteProductMaxAddedQty": 3,
                    "importMaxFeePercentage": 6,
                    "catalogCurrencyConversionRate": 0.1,
                    "decimalPricing": true,
                    "phoneNumberValidation": {
                        "maxLength": 9,
                        "actualLength": 8,
                        "lableHintNumber": "50123456",
                        "validation": [
                            {
                                "zeroInitialIndex": true,
                                "regex": [
                                    "(^(?:[0])?([0-9]{9})$)"
                                ]
                            },
                            {
                                "zeroInitialIndex": false,
                                "regex": [
                                    "(^([1-9]){1}([0-9]{7})$)"
                                ]
                            }
                        ]
                    },
                    "warehouseId": 110,
                    "mapperTable": "sa_inventory",
                    "rmaapplicableThreshold": 0,
                    "holdOrder": false,
                    "isPayfortAuthorized": false,
                    "enableApplepayholdOrder": false
                }
            ];
  const store = _.find(stores, s => s.storeId == storeId);
  if (store) {
    const paymentConfig = paymentMethods.credentials[`${store.websiteCode}`];
    return paymentConfig?.methods;
  } else {
    return [];
  }
};

/**
 * @param {*} key
 * @returns
 */
exports.getBaseConfig = key => {
  const baseConfig = cache.get('baseConfig');
  return baseConfig?.[key];
};

exports.getArrayRandomValue = array => {
  const randomElement = array?.[Math.floor(Math.random() * array.length)];
  return randomElement;
};

exports.getStoreLanguage = (storeId) => {
  const language = this.getStoreConfig(storeId, 'storeLanguage');
  return language?.split('_')?.[0];
}

exports.getTaxIndia = key => {
  const taxIn = cache.get('taxIn');
  return taxIn?.[key];
};


exports.getCODRestrication = key => {
  const paymentMethods = cache.get('paymentMethods');
  return paymentMethods?.CODRestriction?.[key] || {};
};

exports.findStore = (payload) => {
  const config = this.getAppConfig();
  const store = config?.environments[0]?.stores?.find(
    (st) =>
       _.toLower(st.websiteCode) === _.toLower(payload?.country) && st.storeCode.includes(payload?.language)
  );
  if (!store) {
    throw new ValidationError(
      `Store not found for provided request. Country : ${payload?.country}, Lang : ${payload?.language}`
    );
  }
  return store;
};

exports.getShippingKey = (customerId,storeId) =>{
  return 'quote_'+ customerId + '_' +storeId;
}

exports.getAdminStoreConfig = (storeId, key = null) => {
  const adminBaseConfig = cache.get("adminBaseConfig");
  const storeConfig =
  adminBaseConfig?.configs?.filter(storeData => {
      return String(storeData.storeId) === String(storeId);
    })[0] || {};

  if (key) {
    return storeConfig[key] || '';
  } else {
    return storeConfig;
  }
};

/**
 * Retrieves the Shukran point conversion rate for a given storeId.
 * @param {string|number} storeId - The ID of the store.
 * @returns {number} - The Shukran point conversion rate for the given storeId.
 *                      Returns 0 if the storeId is not found.
 */
exports.getShukranPointConversion = storeId => {
  // Map of default Shukran point conversion rates by storeId
  const defaultShukranPointConversion = {
    "1": 0.05,
    "3": 0.05,
    "7": 0.05,
    "11": 0.05,
    "12": 0.004,
    "13": 0.004,
    "19": 0.005,
    "21": 0.005,
    "23": 0.005,
    "25": 0.005
  };
  // Convert storeId to a string to avoid type mismatches
  const storeKey = String(storeId);
  // Return the conversion rate or a default value (0.05) if not found
  return defaultShukranPointConversion[storeKey] || 0.05;
};

exports.bnblCalculation = ({baseConfig, responseObject, quote, retryPayment}) => {
  const {isBNPLEnable = false, bnplInstalmentCount = 4 } = baseConfig || {};
  if(isBNPLEnable && !retryPayment){
    responseObject['bnplConfig'] = {};
    const {grandTotal = 0, availablePaymentMethods} = responseObject;
    const instalMentAmount  = grandTotal/bnplInstalmentCount;
    const payPerInstallMent = Math.round(instalMentAmount * 100) / 100;
    const installMentConfig = {"installments_count":bnplInstalmentCount, "pay_per_installment":payPerInstallMent};
    if(availablePaymentMethods.filter(payment => payment.includes('tabby')).length){
      responseObject['bnplConfig']['tabby_installments'] = installMentConfig;
    }
    if(availablePaymentMethods.filter(payment => payment.includes('tamara')).length){
      responseObject['bnplConfig']['tamara_installments'] = installMentConfig;
    }
  }
}

exports.deleteQuoteKey = async (redisClient, quoteId, customerId) => {
  const enableQuoteCache = cache.get("baseConfig")?.enableQuoteCache || false;
  const key = !customerId ? `guest_${quoteId}` : `customer_${customerId}`;

  if (!enableQuoteCache) return;
  
  // logger.info(`Executed redis cache flush function with quoteId: ${quoteId} or customerId: ${customerId}`);
  
  await redisClient.del(key);
};

exports.validateCouponCharacters = (req, res, next) => {
  const isContainSpecialCharacter = /[^a-zA-Z0-9\u0600-\u06FF]/.test(req.body?.coupon);

  if (isContainSpecialCharacter) {
    const baseConfig = cache.get('baseConfig');
    const getCouponErrorMessage = baseConfig?.getCouponErrorMessage || {};
    const statusMsg = getCouponErrorMessage[req.body?.storeId] || "Invalid Coupon Code";

    return res.status(200).json({
      status: false,
      statusCode: '300',
      statusMsg
    });
  }
  next();
};