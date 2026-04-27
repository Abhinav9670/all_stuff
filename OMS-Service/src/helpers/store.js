const { Store, StoreWebsite } = require('../models/seqModels/index');
const moment = require('moment');
const Sequelize = require('sequelize');
const { IBAN_COUNTRY_MAP, STORE_LANG_MAP } = require('../constants');

const getStores = async () => {
  const response = await Store.findAll();
  return response
    ?.map(status => status.dataValues)
    ?.filter(e => e.store_id !== 0);
};

const getStoreDetails = async store_id => {
  return await Store.findOne({
    where: { store_id: store_id },
    raw: true
  });
};

const getStoresWebsite = async () => {
  const response = await StoreWebsite.findAll({
    where: {
      website_id: {
        [Sequelize.Op.not]: 0
      }
    }
  });
  return response?.map(status => status.dataValues);
};

const getStoreLink = (storeId) => {
  const country = IBAN_COUNTRY_MAP[Number(storeId)].toLowerCase();
  const language = STORE_LANG_MAP[Number(storeId)];
  const MAIN_WEBSITE_HOST_NAME = process.env.MAIN_WEBSITE_HOST_NAME;
  return `${MAIN_WEBSITE_HOST_NAME}/${country}/${language}`;
}
const getWebsiteLink = (storeId) => {
  const country = IBAN_COUNTRY_MAP[Number(storeId)].toLowerCase();
  const language = STORE_LANG_MAP[Number(storeId)];
  const MAIN_WEBSITE_HOST_NAME = process.env.MAIN_WEBSITE_HOST_NAME;
  return `${MAIN_WEBSITE_HOST_NAME}/${country}/${language}`;
}
const getDayWithSuffix = (day) => {
  const j = day % 10,
        k = day % 100;
  if (j === 1 && k !== 11) {
    return 'st';
  }
  if (j === 2 && k !== 12) {
    return 'nd';
  }
  if (j === 3 && k !== 13) {
    return 'rd';
  }
  return 'th';
};

// Function to format the date with the day and superscript suffix
const formatDateWithSuperscript = (date) => {
  const day = moment(date).date(); // Get the day of the month
  const suffix = getDayWithSuffix(day); // Get the appropriate suffix
  const monthYear = moment(date).format('MMM YYYY'); // Get the month and year

  // Return the formatted day with <sup> for the suffix
  return `${day}<sup>${suffix}</sup> ${monthYear}`;
};

// Format estimated delivery date with superscript
const getEstDelivery = date => {
  return date ? formatDateWithSuperscript(date) : '';
};

const getProductUrl = (storeId, products) => {
  const country = IBAN_COUNTRY_MAP[Number(storeId)].toLowerCase();
  const language = STORE_LANG_MAP[Number(storeId)];
  const MAIN_WEBSITE_HOST_NAME = process.env.MAIN_WEBSITE_HOST_NAME;

  return products.map(product => {
    const formattedName = product.name.replace(/\s+/g, '-'); // replace spaces with hyphens
    return `${MAIN_WEBSITE_HOST_NAME}/${country}/${language}/product-${formattedName}-${product.sku}`;
  });
};
const getTrackUrl = (storeId, orderId) => {
  const country = IBAN_COUNTRY_MAP[Number(storeId)].toLowerCase();
  const language = STORE_LANG_MAP[Number(storeId)];
  const MAIN_WEBSITE_HOST_NAME = process.env.MAIN_WEBSITE_HOST_NAME;
  return `${MAIN_WEBSITE_HOST_NAME}/${country}/${language}/account/orderview/${orderId}`;
}

const createNewWebsite = async req => {
  const website = {
    code: req.options.website_code,
    name: req.options.website_name,
    default_group_id: 0,
    sort_order: req.options.sort_order
  };
  const website_details = await StoreWebsite.create(website);
  return website_details?.website_id;
};

const createNewStore = async req => {
  const store = {
    code: req.options.code,
    website_id: req.options.website_id,
    group_id: req.options.website_id,
    name: req.options.name,
    is_external: req.options.is_external,
    warehouse_location_code: req.options.warehouse_location_code,
    warehouse_inventory_table: req.options.warehouse_inventory_table,
    currency: req.options.currency,
    currency_conversion_rate: req.options.currency_conversion_rate
  };
  return await Store.create(store);
};

const getNewStore = (response, store) => {
  if (store === '') {
    store = response;
  }
  return {
    storeId: response?.store_id?.toString(),
    storeCode: '',
    storeLanguage: '',
    storeCurrency: store?.currency?.toString(),
    shipmentChargesThreshold: '',
    shipmentCharges: '',
    codCharges: '',
    taxPercentage: '',
    websiteId: store?.website_id?.toString(),
    websiteIdentifier: '',
    storeName: '',
    websiteCode: store?.website_code?.toString(),
    countryCode: '',
    currencyConversionRate: store?.currency_conversion_rate?.toString(),
    termsAndUse: '',
    privecyPolicy: '',
    helpCentreAndFaq: '',
    contract: '',
    customDutiesPercentage: '',
    importFeePercentage: '',
    minimumDutiesAmount: '',
    flagUrl: '',
    quoteProductMaxAddedQty: '',
    importMaxFeePercentage: '',
    catalogCurrencyConversionRate: '',
    decimalPricing: '',
    phoneNumberValidation: {
      maxLength: '',
      actualLength: '',
      lableHintNumber: '',
      validation: [
        {
          zeroInitialIndex: '',
          regex: ''
        },
        {
          zeroInitialIndex: '',
          regex: ''
        }
      ]
    },
    rmaapplicableThreshold: '',
    warehouseId: store?.warehouse_location_code?.toString(),
    mapperTable: store?.warehouse_inventory_table?.toString()
  };
};

const updateStoreDetails = async updateObj => {
  return await Store.update(
    {
      code: updateObj.code,
      website_id: updateObj.website_id,
      group_id: updateObj.website_id,
      name: updateObj.name,
      is_external: updateObj.is_external,
      warehouse_location_code: updateObj.warehouse_location_code,
      warehouse_inventory_table: updateObj.warehouse_inventory_table,
      currency: updateObj.currency,
      currency_conversion_rate: updateObj.currency_conversion_rate
    },
    {
      where: { store_id: updateObj.store_id }
    }
  );
};

module.exports = {
  getStores,
  getStoreDetails,
  getStoresWebsite,
  createNewWebsite,
  createNewStore,
  getNewStore,
  updateStoreDetails,
  getStoreLink,
  getWebsiteLink,
  getTrackUrl,
  getProductUrl,
  getEstDelivery
};
