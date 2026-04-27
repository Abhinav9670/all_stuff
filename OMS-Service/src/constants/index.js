const { BUCKET_BASE_URL } = process.env;

const dummyData = {};
exports.dummyData = dummyData;

exports.ADDRESS_MAPPER_URL = BUCKET_BASE_URL;
exports.CATEGORY_WOMEN = 7;
exports.CATEGORY_MEN = 3;
exports.CATEGORY_BEAUTY = 11765;
exports.STORE_LANG_MAP = {
  1: 'en',
  3: 'ar',
  7: 'en',
  11: 'ar',
  13: 'ar',
  12: 'en',
  15: 'en',
  17: 'ar',
  19: 'en',
  21: 'ar',
  51: 'en',
  23: 'en',
  25: 'ar'
};

exports.STORE_TIME_ZONE_MAP = {
  1: 'Asia/Riyadh',
  3: 'Asia/Riyadh',
  7: 'Asia/Dubai',
  11: 'Asia/Dubai',
  13: 'Asia/Kuwait',
  12: 'Asia/Kuwait',
  15: 'Asia/Qatar',
  17: 'Asia/Qatar',
  19: 'Asia/Bahrain',
  21: 'Asia/Bahrain',
  51: 'Asia/Kolkata',
  23: 'Asia/Muscat',
  25: 'Asia/Muscat'
};

exports.IBAN_COUNTRY_MAP = {
  1: 'SA',
  3: 'SA',
  7: 'AE',
  11: 'AE',
  13: 'KW',
  12: 'KW',
  15: 'QA',
  17: 'QA',
  19: 'BH',
  21: 'BH',
  51: 'IN',
  23: 'OM',
  25: 'OM'
};

exports.SIZE_BEAUTY = 'size_beauty';
exports.SIZE_PHONE_CASES = 'size_phone_cases';

exports.CONTENT_TYPE = 'application/json';

exports.TOKEN =
  'KEY eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJndWVzdEBzdHlsaXNob3AuY29tIiwiY29kZSI6IjB6ZENWWlBxbGcyWUhKR0JWd1VQZCIsInJvbGUiOiJndWVzdCJ9.4De9WDTg_avRXEBdbB4gQ1MkxObT6beWJMDGQ1EWbQmNZfe8S6C5AlkyOd-Zzfv827xyZeG17tv4PBz7MjRG7w';

exports.ALLOWED_DOMAINS = [
  'https://oms-ui-dev.web.app',
  'https://oms-qa.stylifashion.com',
  'https://oms-ui-qa.web.app',
  'https://oms-qa.stylifashion.com',
  'https://omsprod.stylishop.store',
  'https://oms-ui-qa-in.web.app',
  'https://oms-ui-uat.web.app',
  'https://omsprod.web.app',
  'https://dev.stylifashion.com',
  'https://qa.stylifashion.com',
  'https://qa01.stylifashion.com',
  'https://stylishop.com',
  'https://styli-flutter-web-34dca.firebaseapp.com',
  'https://uat.stylifashion.com',
  // 'http://localhost:3000',
  'https://omsprod-in.web.app',
];

exports.SPLIT_ORDER_SMS_TYPE = [
  'rto_initiated',
  'order_cod_partial_unfulfilment',
  'order_prepaid_partial_unfulfilment',
  'order_cod_fully_unfulfilment',
  'order_prepaid_fully_unfulfilment',
  'order_prepaid_cancel',
  'order_cod_cancel',
  'order_tabby_paylater_cancel',
  'order_tabby_installment_cancel',
  'rto_refund_initiated'
]

exports.inTaxTypes = ['IGST', 'CGST', 'SGST'];
