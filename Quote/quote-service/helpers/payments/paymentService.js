const cache = require('memory-cache');
const _ = require('lodash');
const tabbyService = require('./tabbyService');
const tamaraService = require('./tamaraService');
const { checkFraudCustomer } = require('../v6/checkFraud');
const {
  logError,
  getStoreConfig,
  getBaseConfig,
  logInfo,
  getCODRestrication
} = require('../utils');
// const logger = require('../logger');

const { couchbase } = require('../../config/couchbase');
const axios = require('axios');
const moment = require('moment');

const PAYMENT_SERVICE_URL = process.env.PAYMENT_SERVICE_URL;

const TABBY_INSTALLMENTS = 'tabby_installments';
const TABBY_PAYLATER = 'tabby_paylater';
const TABBY = 'tabby';
const TAMARA = 'tamara';
const TAMARA_INSTALLMENTS_3 = 'tamara_installments_3';
const TAMARA_INSTALLMENTS_6 = 'tamara_installments_6';
const CASH_ON_DELIVERY = 'cashondelivery';
const { AUTH_INTERNAL_HEADER_BEARER_TOKEN } = process.env;
const internalAuthToken = AUTH_INTERNAL_HEADER_BEARER_TOKEN?.split(",")?.[0];

/**
 * Returns applicable payment method for provided customer and country specific applicable payment options.
 * @param {*} quote
 * @param {*} responseObject
 */
exports.paymentMethods = async (quote, responseObject, isFirstCall = false) => {
  const baseConfig = cache.get('baseConfig');
  const bnplAsync = baseConfig?.bnplAsync;
  const selectedPaymentMethod = quote?.quotePayment?.method;
  const qPaymentMethods = quote.availablePaymentMethods || [];
  const paymentsConfig = quote.paymentsConfig || {};
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
  quote.bnplSessionAmount = responseObject.grandTotal;
  const bnplPromiseArr = [];

  const excludeFirstCall = getExcludeFirstCall({ paymentsConfig });

  const store = _.find(stores, (s) => s.storeId == quote.storeId);
  if (store) {
    const paymentConfig = paymentMethods.credentials[`${store.websiteCode}`];
    const methods = paymentConfig?.methods;
    if (isFirstCall) {
      responseObject.availablePaymentMethods = methods;
    } else {
      responseObject.availablePaymentMethods = qPaymentMethods;
    }
    if (paymentMethods.tamaraEligibilityCheck)
      await checkTamaraEligibility(
        responseObject,
        paymentConfig,
        quote,
        store,
        paymentsConfig
      );
    const availableMethods = await restrictFraudCustomerPayments(
      quote,
      methods,
      responseObject
    );
    const tabbyOps = availableMethods?.find((m) => _.includes(m, 'tabby'));
    if (
      tabbyOps &&
      tabbyOps.length > 0 &&
      ( selectedPaymentMethod?.includes('tabby') ||
        (isFirstCall && !excludeFirstCall?.tabby))
    ) {
      if (bnplAsync) {
        bnplPromiseArr.push(
          tabbyPaymentDetails({ quote, responseObject, store, paymentConfig })
        );
      } else {
        const tabbyConfig = await tabbyPaymentDetails({
          quote,
          responseObject,
          store,
          paymentConfig,
        });
        Object.assign(paymentsConfig, tabbyConfig);
      }
    }

    const tamaraOps = availableMethods?.find((m) => _.includes(m, 'tamara'));
    if (
  tamaraOps &&
  tamaraOps.length > 0 &&
  ( selectedPaymentMethod?.includes('tamara') ||
    (isFirstCall && !excludeFirstCall?.tamara))
)
{
      if (bnplAsync) {
        bnplPromiseArr.push(
          tamaraPaymentDetails({
            quote,
            responseObject,
            store,
            paymentConfig,
            paymentsConfig,
          })
        );
      } else {
        const tamaraConfig = await tamaraPaymentDetails({
          quote,
          responseObject,
          store,
          paymentConfig,
          paymentsConfig,
        });
        Object.assign(paymentsConfig, tamaraConfig);
      }
    }

    if (bnplAsync) {
      await Promise.allSettled(bnplPromiseArr).then((results) => {
        results.forEach((res) => {
          Object.assign(paymentsConfig, res?.value || {});
        });
      });
    }
  }
  if (isFirstCall) {
    quote.availablePaymentMethods = responseObject.availablePaymentMethods;
  }

  const websiteCode = getStoreConfig(quote.storeId, "websiteCode");
  const CODRestriction = getCODRestrication(websiteCode);
  if (CODRestriction?.codRestrictionEnabled) {
    const grandTotal = (Number(responseObject.grandTotal) || 0);
    if (Number(CODRestriction?.codRestrictionMin) > 0) {
      if (grandTotal < Number(CODRestriction?.codRestrictionMin)) {
        return resetPaymentMethods(responseObject, CASH_ON_DELIVERY);
      }
    }
    if (Number(CODRestriction?.codRestrictionMax) > 0) {
      if (grandTotal > Number(CODRestriction?.codRestrictionMax)) {
        return resetPaymentMethods(responseObject, CASH_ON_DELIVERY);
      }
    }
  }
  responseObject.paymentsConfig = paymentsConfig;
  quote.paymentsConfig = paymentsConfig;
  removePaymentIdForOtherPaymentType(quote);
};

/**
 * Returns if any additional Information required for any payment method
 * @param {*} paymentsConfig
 */
const tabbyPaymentDetails = async ({
  quote,
  responseObject,
  store,
  paymentConfig,
}) => {
  const methods = responseObject.availablePaymentMethods;
  let paymentsConfig = {};
  const tabby = await tabbyService.createSession(
    quote,
    responseObject,
    store,
    paymentConfig
  );
  if (tabby) {
    const { installments, paylater } = tabby;
    const tabby_installments = methods?.find((m) =>
      _.includes(m, TABBY_INSTALLMENTS)
    );
    const tabby_paylater = methods?.find((m) => _.includes(m, TABBY_PAYLATER));

    if (tabby_installments && installments)
      paymentsConfig[TABBY_INSTALLMENTS] = installments;
    else resetPaymentMethods(responseObject, TABBY_INSTALLMENTS);

    if (tabby_paylater && paylater) paymentsConfig[TABBY_PAYLATER] = paylater;
    else resetPaymentMethods(responseObject, TABBY_PAYLATER);
  } else {
    resetPaymentMethods(responseObject, TABBY);
  }

  return paymentsConfig;
};

const tamaraPaymentDetails = async ({
  quote,
  responseObject,
  store,
  paymentConfig,
  paymentsConfig,
}) => {
  const methods = responseObject.availablePaymentMethods;

  let tamaraEligibility = {};
  let paymentsConfigSession = {};

  const paymentMethods = cache.get('paymentMethods');
  if (!paymentMethods.tamaraEligibilityCheck) {
    const tamraPaymentOps = await tamaraService.getPayments(
      quote,
      responseObject,
      store,
      paymentConfig
    );
    const { installment_3, installment_6 } = tamraPaymentOps;
    const tamara_installments_3 = methods?.find((m) =>
      _.includes(m, TAMARA_INSTALLMENTS_3)
    );
    if (installment_3 && tamara_installments_3)
      tamaraEligibility[TAMARA_INSTALLMENTS_3] = installment_3;
    else resetPaymentMethods(responseObject, TAMARA_INSTALLMENTS_3);

    const tamara_installments_6 = methods?.find((m) =>
      _.includes(m, TAMARA_INSTALLMENTS_6)
    );
    if (tamara_installments_6 && installment_6)
      tamaraEligibility[TAMARA_INSTALLMENTS_6] = installment_6;
    else resetPaymentMethods(responseObject, TAMARA_INSTALLMENTS_6);
  } else {
    // logInfo(`create session check 0 ${JSON.stringify(paymentsConfig)}`);

    const tamara_installments_3 = methods?.find((m) =>
      _.includes(m, TAMARA_INSTALLMENTS_3)
    );

    if (tamara_installments_3)
      tamaraEligibility.tamara_installments_3 =
        paymentsConfig.tamara_installments_3;

    const tamara_installments_6 = methods?.find((m) =>
      _.includes(m, TAMARA_INSTALLMENTS_6)
    );

    if (tamara_installments_6)
      tamaraEligibility.tamara_installments_6 =
        paymentsConfig.tamara_installments_6;
  }

  if (_.includes(responseObject.selectedPaymentMethod, 'tamara')) {
    // logInfo(`create session check 1 ${JSON.stringify(tamaraEligibility)}`);

    paymentsConfigSession = await tamaraService.createSession(
      quote,
      responseObject,
      store,
      paymentConfig
    );
    // logInfo(`create session check 2 ${JSON.stringify(paymentsConfigSession)}`);
    if (!paymentsConfig || !paymentsConfig[`${quote.quotePayment.method}`])
      resetPaymentMethods(responseObject, `${quote.quotePayment.method}`);
  }
  logInfo(`create session check 2a `);
  try {
    if (
      tamaraEligibility.tamara_installments_3 &&
      paymentsConfigSession.tamara_installments_3
    ) {
      tamaraEligibility.tamara_installments_3.web_url =
        paymentsConfigSession.tamara_installments_3.web_url;
      tamaraEligibility.tamara_installments_3.color_code =
        paymentsConfigSession.tamara_installments_3.color_code;
    }
    if (
      tamaraEligibility.tamara_installments_6 &&
      paymentsConfigSession.tamara_installments_6
    ) {
      tamaraEligibility.tamara_installments_6.color_code =
        paymentsConfigSession.tamara_installments_6.color_code;
    }
    logInfo(`create session check object assigning success 3`);
  } catch (error) {
    logInfo(`create session check object assigning error 3 ${error}`);
  }
  return tamaraEligibility;
};

const resetPaymentMethods = (responseObject, option) => {
  const payments = responseObject?.availablePaymentMethods?.filter(
    (m) => !_.includes(m, option)
  );
  responseObject.availablePaymentMethods = payments;
};

const removePaymentIdForOtherPaymentType = (quote) => {
  if (
    !(
      _.includes(quote?.quotePayment?.method, TABBY) ||
      _.includes(quote?.quotePayment?.method, TAMARA)
    )
  )
    quote.tabbyPaymentId = null;
};

const restrictFraudCustomerPayments = async (
  quote,
  methods,
  responseObject
) => {
  const quoteAddrs = quote.quoteAddress[0];
  const fraudCust = await checkFraudCustomer(
    quote.customerEmail,
    quoteAddrs?.mobileNumber
  );
  if (
    fraudCust &&
    fraudCust?.blocked_payments?.length > 0 &&
    fraudCust?.forward
  ) {
    fraudCust.blocked_payments.forEach((paymt) => {
      methods
        .filter((p) => _.includes(p, paymt))
        .forEach((payment) => {
          resetPaymentMethods(responseObject, payment);
        });
    });
  }
  return responseObject.availablePaymentMethods;
};

exports.bnplBagCall = async ({
  quote,
  collection,
  responseObject,
  xHeaderToken,
}) => {
  const apm = global?.apm;
  let span;
  try {
    const enabled = getBaseConfig('bnplBagPage');
    if (enabled) {
      const { storeId, paymentsConfig = {} } = quote;
      const store = getStoreConfig(storeId);
      const paymentMethods = cache.get('paymentMethods');
      const paymentConfig = paymentMethods.credentials[`${store.websiteCode}`];
      const bnplPromiseArr = [];
      bnplPromiseArr.push(
        tabbyPaymentDetails({ quote, responseObject, store, paymentConfig })
      );
      bnplPromiseArr.push(
        tamaraPaymentDetails({
          quote,
          responseObject,
          store,
          paymentConfig,
        })
      );

      await Promise.allSettled(bnplPromiseArr).then((results) => {
        results.forEach((res) => {
          Object.assign(paymentsConfig, res?.value || {});
        });
      });

      span = apm?.startSpan('CB: MutateIn bnplBagCall', 'db', 'couchbase', 'mutateIn');
      if (span) {
        span.setServiceTarget('couchbase', process.env.COUCHBASE_CLUSTER_BUCKET);
      }
      await collection.mutateIn(`quote_${quote?.id}`, [
        couchbase.MutateInSpec.upsert('paymentsConfig', paymentsConfig),
      ]);
    }
  } catch (e) {
    logError(e, 'Error bnpl Bag bag page async call');
  } finally {
    if (span) span.end();
  }
};

const getExcludeFirstCall = ({ paymentsConfig = {} }) => {
  const bnplBagEnabled = getBaseConfig('bnplBagPage');
  let excludeFirstCall = { tabby: false, tamara: false };
  if (bnplBagEnabled) {
    for (const index in paymentsConfig) {
      if (index.includes('tabby')) {
        excludeFirstCall.tabby = true;
      }
      if (index.includes('tamara')) {
        excludeFirstCall.tamara = true;
      }
    }
  }
  return excludeFirstCall;
};

/**
 * Returns available payment methods and config from payment service
 * @param {*} quote
 * @param {*} responseObject
 * @param {*} headers
 */
exports.paymentMethodsV2 = async (quote, responseObject, headers,xClientVersion,xSource,xHeaderToken) => {
  try {
    // logInfo("payment methods headers: " + JSON.stringify(headers));
    const bnplAmount =
      responseObject.grandTotal === '0'
        ? responseObject.estimatedTotal
        : responseObject.grandTotal;
    quote.bnplSessionAmount = bnplAmount;
    const quoteAddress = quote.quoteAddress[0];
    const payload = {
      id: quote.id,
      storeId: quote.storeId,
      customerId: quote.customerId,
      storeCurrencyCode: quote.storeCurrencyCode,
      subtotal: Number(bnplAmount),
      coinInCurrency: quote?.coinDiscountData?.storeCoinValue,
      donationAmount: quote?.donationAmount,
      customerEmail: quote.customerEmail,
      discountData: quote.discountData,
      paymentMethod: responseObject.selectedPaymentMethod,
      retryPayment: quote.retryPayment,
      quoteAddress: {
        firstname: quoteAddress.firstname,
        lastname: quoteAddress.lastname,
        mobileNumber: quoteAddress.mobileNumber,
        buildingNumber: quoteAddress.buildingNumber,
        region: quoteAddress.region,
        area: quoteAddress.area,
        city: quoteAddress.city,
        countryId: quoteAddress.countryId,
      },
      quoteItem: orderItems(quote),
      codCharges: responseObject?.codCharges
    };
    // headers = { ...headers, "authorization-token": internalAuthToken };
    const config = {
      method: 'post',
      url: `${PAYMENT_SERVICE_URL}/v1/payment/options`,
      headers: {
        token : headers?.token,
        "authorization-token": internalAuthToken,
        "x-source": xSource || headers["x-source"] || headers["X-Source"] || responseObject.xSource || "",
        "x-client-version": xClientVersion || headers["x-client-version"] || headers["X-Client-Version"] || responseObject.xClientVersion || "",
        "x-header-token": xHeaderToken || headers["x-header-token"] || headers["X-Header-Token"] || responseObject.xHeaderToken || "",
      },
      data: payload,
    };
    // logInfo(`### Payment Service call Request : ${JSON.stringify(config)}`);
    let response = {};
    try{
      response = await axios(config);
    }catch(err){
      // logger.error(`paymentMethods: Axios call error for payment request - Config: ${JSON.stringify(config)}, Error: ${err?.message}`);
      logError(err, 'Error in axios call');
    }
    if (response.status === 200) {
      const res = response.data.response;
      if (res.paymentsConfig['payment_id'])
        quote.tabbyPaymentId = res.paymentsConfig['payment_id'];
      else quote.tabbyPaymentId = null;
      responseObject.availablePaymentMethods = res.availablePaymentMethods;
      responseObject.paymentsConfig = res.paymentsConfig;
    }
    // logInfo(`### Payment Service response :  ${response?.status} ' Body : ' ${JSON.stringify(response?.data)}`)
  } catch (e) {
    // logger.error(`paymentMethods: Payment service call error - ${e?.message}`);
    // logError(e, 'Error in payment service call');
  }
};

const orderItems = (quote) => {
  return quote.quoteItem?.map((item) => {
    return {
      name: item.name,
      type: item.productType,
      qty: item.qty,
      imgUrl: item.imgUrl,
      sku: item.sku,
      priceInclTax: item.priceInclTax,
    };
  });
};

/**
 * Clear BNPL session in payment service
 * @param {*} quote
 * @param {*} headers
 */
exports.clearSesssion = async (quote, headers) => {
  try {
    const payload = {
      id: quote.id,
      bnplAmount: quote.bnplSessionAmount,
      paymentMethod: quote.quotePayment?.method,
    };

    const config = {
      method: 'post',
      url: `${PAYMENT_SERVICE_URL}/v1/payment/session/clear`,
      headers: {
        token : headers?.token,
        "authorization-token": internalAuthToken
      },
      data: payload,
    };
    // logInfo(`### Session clear request: ${JSON.stringify(config)}`);
    const response = await axios(config);
    if (response.status === 200) {
      logInfo(`BNPL session cleared for quote : ${quote.id}`);
    } else {
      logInfo(
        `BNPL session didn't cleared for quote : ${quote.id}, Status : ${response.status}`
      );
    }
  } catch (e) {
    logError(e, 'Error in payment service call');
  }
};

const checkTamaraEligibility = async (
  responseObject,
  paymentConfig,
  quote,
  store,
  paymentsConfig
) => {
  try {
    const quoteAddr = quote.quoteAddress[0];
    const currency = store.storeCurrency;
    const country = store.websiteCode;
    let amount =
      Number(responseObject.grandTotal) -
      Number(responseObject.codCharges || 0);

    if (amount === 0) amount = Number(responseObject.estimatedTotal || 0);

    const payload = {
      total_amount: {
        amount: String(amount || ''),
        currency: currency,
      },
      country_code: country?.toUpperCase(),
      items: [
        {
          total_amount: {
            amount: String(amount || ''),
            currency: currency,
          },
        },
      ],
      consumer: {
        phone_number: quoteAddr?.mobileNumber,
      },
      shipping_address: {
        country_code: country?.toUpperCase(),
      },
    };

    const config = {
      method: 'POST',
      url: `${paymentConfig.tamaraApiUrl}/credit-pre-check`,
      headers: {
        Authorization: `Bearer ${paymentConfig.tamaraToken}`,
        'Content-Type': 'application/json',
      },
      data: JSON.stringify(payload),
    };

    const response = await axios(config);
    if (response?.status === 200) {
      // logInfo(
      //   `Tamara eligibility check success : ${
      //     responseObject.quoteId
      //   } Response: ${JSON.stringify(response.data)}`
      // );

      const res = response.data;
      if (res?.[0]?.supported_instalments?.length) {
        const installments = res[0].supported_instalments;

        const tamara_installments_3 = installments.find((e) => {
          return e.instalments === 3;
        });
        if (!tamara_installments_3) {
          resetPaymentMethods(responseObject, TAMARA_INSTALLMENTS_3);
        } else {
          let installmentObj = {};
          installmentObj.installments_count = 3;
          installmentObj.amount_to_pay = amount.toString();
          await tamaraPaymentPlans(
            4,
            country,
            currency,
            amount,
            installmentObj,
            paymentConfig
          );
          paymentsConfig.tamara_installments_3 = installmentObj;
        }
        const tamara_installments_6 = installments.find((e) => {
          return e.instalments === 6;
        });
        if (!tamara_installments_6) {
          resetPaymentMethods(responseObject, TAMARA_INSTALLMENTS_6);
        } else {
          let installmentObj = {};
          installmentObj.installments_count = 6;
          installmentObj.amount_to_pay = amount.toString();
          await tamaraPaymentPlans(
            6,
            country,
            currency,
            amount,
            installmentObj,
            paymentConfig
          );
          paymentsConfig.tamara_installments_6 = installmentObj;
        }
      } else {
        resetPaymentMethods(responseObject, TAMARA_INSTALLMENTS_3);
        resetPaymentMethods(responseObject, TAMARA_INSTALLMENTS_6);
      }
    } else {
      logInfo(
        `Tamara eligibility check success : ${responseObject.quoteId}, Status : ${response.status}`
      );
    }
  } catch (e) {
    logError(e, 'Error in checking tamara eligibility');
  }
};

const tamaraPaymentPlans = async (
  installment,
  country,
  currency,
  amount,
  tamara_installments,
  paymentConfig
) => {
  const paymentPlanConfig = {
    method: 'GET',
    url: `${paymentConfig.tamaraApiUrl}/payment-plan`,
    headers: {
      Authorization: `Bearer ${paymentConfig.tamaraToken}`,
      'Content-Type': 'application/json',
    },
    params: {
      country: country?.toUpperCase(),
      currency: currency,
      payment_type: 'PAY_BY_INSTALMENTS',
      public_key: paymentConfig.tamarapublickey,
      order_value: String(amount || ''),
      instalments: installment,
    },
  };

  const paymentPlanResponse = await axios(paymentPlanConfig);
  if (paymentPlanResponse?.status == 200) {
    const installments = [];
    const paymentPlanData = paymentPlanResponse.data;
    paymentPlanData.repayments.forEach((repayment) => {
      let repaymentData = {};
      repaymentData.amount = repayment.amount.amount.toString();
      const fmtDate = moment(repayment.due_date).format('DD MMM YY');
      repaymentData.due_date = fmtDate;
      installments.push(repaymentData);
    });
    if (installments.length > 0) {
      tamara_installments.pay_per_installment = installments[0].amount;
    }

    tamara_installments.installments = installments;
  } else {
    logInfo(`Tamara payment plan failure :`);
  }
};
