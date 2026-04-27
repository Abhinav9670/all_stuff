const { add } = require("lodash");
const { upsertQuote } = require("../helpers/upsertQuote");
const _ = require('lodash');
const { getAddressMapper, logError, getStoreLanguage } = require('./utils');
// const { logInfo, logger } = require('./utils');
const cache = require('memory-cache');
const { REGION } = process.env;
const { fetchCityDetails, appendCityDetails } = require('./v6/appendCityDetails');
const defaultMessage = "Invalid Address!";

exports.formatAddress = ({ address = {}, addressId, email }) => {
  const formattedAddress = {
    "customerAddressId": addressId || '',
    "addressType": "shipping",
    "email": address.email || '',
    "defaultAddress": !!address.defaultAddress,
    "firstname": address.firstname || address.firstName || '',
    "lastname": address.lastname || address.lastName || '',
    "street": address.buildingNumber ? `${address.streetAddress ? `${address.streetAddress} ` : ''}${address.buildingNumber}` : address.streetAddress,
    "mobileNumber": address.mobileNumber || '',
    "city": address.city || '',
    "region": address.region || '',
    "regionId": address.region_id || address.regionId || '',
    "postcode": address.postcode || '',
    "countryId": address.country_id || address.country || '',
    "telephone": address.mobileNumber || '',
    "shippingMethod": "",
    "shippingDescription": "",
    "locationType": "",
    "nearestLandmark": address.nearest_landmark || '',
    "area": address.area || '',
    "buildingNumber": address.buildingNumber || '',
    "latitude": address.latitude || null,
    "longitude": address.longitude || null,
    "formattedAddress": address.formattedAddress || '',
    "unitNumber": address.unitNumber || '',
    "postalCode": address.postalCode || '',
    "shortAddress": address.shortAddress || '',
    "nationalId": address.nationalId || null
  }

  return formattedAddress;
}

exports.addAddress = async ({ storeId, collection, customerId, address, addressId, quote, xHeaderToken }) => {
  let response;
  let formattedAddress = {};
  let quoteAddress = [];
  const  { status, message } = await this.validateAddressData({ address, storeId, xHeaderToken });
  if (!status) {
    response = {
      status: false,
      statusCode: '212',
      statusMsg: message,
    };
    return response;
  }

  // logger.info(`addAddress: Processing address for quote ${quote?.id}: ${JSON.stringify(address)}`);
  if(!collection){
    // logger.error("addAddress: Couchbase collection missing in addAddress API");
  }

  formattedAddress = this.formatAddress({ address, addressId })
  quoteAddress = [formattedAddress];

  quote.quoteAddress = quoteAddress;
  quote.customerEmail = formattedAddress?.email || quote.customerEmail || xHeaderToken;
  const paymentMethod = quote.quotePayment?.method;
  // logger.info(`addAddress: Updating city mapper on address change for quote ${quote?.id}: ${JSON.stringify(quoteAddress)}`);
  await appendCityDetails({
    quote,
    xHeaderToken,
    paymentMethod,
  });
  const upsertResponse = await upsertQuote({ storeId, quote, collection, xHeaderToken });
  if (upsertResponse) {
    response = {
      status: true,
      statusCode: '200',
      statusMsg: "address update successfull!",
    };
  }
  return response;
}

exports.validateAddressReq = ({ storeId, quoteId, customerId, addressId, address }) => {
  if (_.isEmpty(address) || typeof address !== 'object' || !storeId) {
    return false
  }

  if(!customerId && !address.email){
    return false
  }
  
  return true
}

exports.validateAddressData = async ({ address, storeId, xHeaderToken }) => {
   const customerAuthConfigKey = process.env.CONSUL_CUSTOMER_AUTH_CONFIG;
   const customerAuthConfig = cache.get(customerAuthConfigKey);
  //  const baseConfig = cache.get('baseConfig');
   const valdationMessages = customerAuthConfig?.addressValidationMessage;
   const storeLanguage = getStoreLanguage(storeId);
  //  logInfo("REGION IN",REGION)
  if (REGION === 'IN') {
    return validateInAddressData(address, valdationMessages, storeLanguage);
  }
  try {
    if (!address.country)
      return {
        status: false,
        message:
          valdationMessages[`country-name-validation_${storeLanguage}`] ||
          defaultMessage,
      };
    if (!address.firstName)
      return {
        status: false,
        message:
          valdationMessages[`first_name_validation_${storeLanguage}`] ||
          defaultMessage,
      };

    const addressMapper = getAddressMapper(storeId);
    // const arStores = JSON.parse(process.env.AR_STORES)
    const regionData = addressMapper && address && address.regionId ? addressMapper[String(address.regionId)] : undefined;
    if (regionData) {
      let cityData = {}
      cityData = regionData.cities && Object.values(regionData.cities).find(city =>
        city.name_ar.trim() === address.city.trim() || city.name_en.trim() === address.city.trim()
      );


      let areaData = {};
      if (!_.isEmpty(cityData)) {
        areaData = cityData.area && Object.values(cityData.area).find(area =>
          area?.name_ar?.trim() === address?.area?.trim() || area?.name_en?.trim() === address?.area?.trim()
        );
      } else 
          return {
            status: false,
            message:
              valdationMessages[`city-validation_${storeLanguage}`] ||
              defaultMessage,
          };

      if(address.latitude && address.longitude) 
        return { status: true };
      if (!_.isEmpty(areaData)) return { status: true, message: "" };
      else
        return {
          status: false,
          message:
            valdationMessages[`area_validation_${storeLanguage}`] ||
            defaultMessage,
        };
    }

    return {
      status: false,
      message:
        valdationMessages[`region-validation_${storeLanguage}`] ||
        defaultMessage,
    };

  } catch (e) {
    logError(e, 'Error validating address mapper', xHeaderToken);
    return {
      status: false,
      message:
        valdationMessages[`code_validation_${storeLanguage}`] || defaultMessage,
    };
  }
}

const validateInAddressData = async (address, valdationMessages, storeLanguage) => {
  try {
    // logger.info(`validateInAddressData: Validating address area: ${JSON.stringify(address)}`);
    const response = await fetchCityDetails(address.area);
    if (!response || !response?.enabled) {
      return {
        status: false,
        message:
          valdationMessages[`region-validation_${storeLanguage}`] ||
          defaultMessage,
      };
    }
    const city = response.city;
    const area = response.name_en;
    if (_.toUpper(city.trim()) !== _.toUpper(address.city.trim())) {
      return {
        status: false,
        message:
          valdationMessages[`city-validation_${storeLanguage}`] ||
          defaultMessage,
      };
    }

    if (area.trim() !== address.area.trim()) {
      return {
        status: false,
        message:
          valdationMessages[`area_validation_${storeLanguage}`] ||
          defaultMessage,
      };
    }
    return { status: true, message: "" };
  } catch (error) {
    logError(error, `Error in finding Pincode details.`);
  }
   return {
     status: false,
     message:
       valdationMessages[`code_validation_${storeLanguage}`] || defaultMessage,
   };
};