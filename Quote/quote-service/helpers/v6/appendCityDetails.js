const _ = require('lodash');
const axios = require('axios');
const { logError, logInfo } = require('../utils');
const { COUCHBASE_CLUSTER_ADDRESS_BUCKET, FETCH_CITY_ENDPOINT } = process.env;
const mongoUtil = require('../../config/mongoInit');
const { getAppConfigKey } = require('../utils');

exports.fetchCityDetails = async cityId => {
  try{
    // logInfo(`FETCH_CITY_ENDPOINT::${FETCH_CITY_ENDPOINT}`);
    // logInfo(`City ID::${cityId}`)

  const cityResponse = await axios.get(`${FETCH_CITY_ENDPOINT}/${cityId}`, {
    headers: {
      'Content-Type': 'application/json'
    }
  });
  const { data: { response: cityData } } = cityResponse;
  // logInfo(`City Details API success: ${JSON.stringify(cityData)}`)
  return cityData;
}catch(error){
 // logInfo(`error in the fetch City details in addressmapper : ${error}`)
 return false;
}
};

exports.appendCityDetails = async ({
  quote,
  cluster,
  xHeaderToken,
  paymentMethod
}) => {
  const addressObject = _.filter(
    quote.quoteAddress || [],
    el => el.addressType == 'shipping'
  )[0];
  const db = mongoUtil.getDb();
  // console.log('adsrmprFromMongo',getBaseConfig('adsrmprFromMongo'));
  // const config = getBaseConfig('adsrmprFromMongo') || false;
  if (addressObject?.city) {
    try {
      if (!db) {
        // console.log(`MongoDB connection is not initialized in appendCityDetails ${JSON.stringify(addressObject)} for xHeaderToken ${xHeaderToken}`);
        return quote;
      }
      let addressMapperObj = null;

      if(process.env.REGION !== 'IN'){
        let addressMapperObjDb;
      //  if(config){
        const quoteQueryData = await db.collection("cities").findOne({$and:[{type_1:'search_city'},{region_id: addressObject.regionId},{$or:[{name_en:{ '$regex' : addressObject.city, $options: 'i' }},{name_ar:{ '$regex' : addressObject.city, $options: 'i' }}]}]});
        addressMapperObjDb = quoteQueryData;
      //  }else{
      //    const query = `select * from ${COUCHBASE_CLUSTER_ADDRESS_BUCKET} where type_1="search_city" AND region_id="${addressObject.regionId}" AND (name_en="${addressObject.city}" or name_ar="${addressObject.city}")`;
      //    const quoteQueryData = await cluster.query(query);
      //   if (span) span.end()
      //   addressMapperObjDb =
      //     quoteQueryData?.rows[0]?.[COUCHBASE_CLUSTER_ADDRESS_BUCKET];
      //  }
        if (addressMapperObjDb?.id)
          addressMapperObj = await this.fetchCityDetails(addressMapperObjDb.id);
      }else{
        addressMapperObj = await this.fetchCityDetails(addressObject?.area);
      }
    
      if (addressMapperObj) {
        let fastDelivery = false;
        let time = '';
        if (addressMapperObj.fast_delivery) {
          let fastDeliveryType;
          if(addressMapperObj?.fastDeliveryType){
            fastDeliveryType = addressMapperObj.fastDeliveryType;
          }
          else {
            let cityData = await db.collection('cities').findOne({_id:addressObject?.cityMapper?.id});
            fastDeliveryType = cityData?.fast_delivery_type || 'sdd';
          }
          let fastDeliveryData;
          // if(config){
            fastDeliveryData = await db.collection('fast_pay').findOne({type:'fast_pay'});
          // }else{
          // const fastDelQry = `select * from ${COUCHBASE_CLUSTER_ADDRESS_BUCKET} where type="fast_pay"`;
          // const fastDeliveryObj = await cluster.query(fastDelQry);
          // fastDeliveryData =
          //   fastDeliveryObj?.rows[0]?.[COUCHBASE_CLUSTER_ADDRESS_BUCKET];
          // }
          
          const { paymentData, selectedTime } = fastDeliveryData || {};
          if (paymentData) {
            let selectedTimeValue = Array.isArray(selectedTime) ?  selectedTime.find(item => fastDeliveryType in item)?.[fastDeliveryType] : selectedTime
            const filteredPayment = paymentData.find(
              p => p.code === paymentMethod
            );
            if (filteredPayment?.selected) {
              fastDelivery = true;
              time = selectedTimeValue;
            }
          }
        }

        addressMapperObj.fast_delivery = fastDelivery;
        addressMapperObj.threshold_time = time;
        quote.city = addressMapperObj;
        // logInfo(
        //   `cityDetails info for quote "${quote.id}"`,
        //   addressMapperObj,
        //   xHeaderToken
        // );
      }
    } catch (e) {
      logError(
        e,
        `${addressObject.city} - error getting city details by query`,
        xHeaderToken
      );
    }
  }

  return quote;

  //   if (quoteQueryData.rows && quoteQueryData.rows.length) {
  //     quote = quoteQueryData?.rows[0]?.quote;
  //   }
};

exports.fetchSLADetails = async (data) => {
  try{
    const { warehouseIds, cityId, countryId } = data;
    const addressMapperApi = getAppConfigKey('addressMapperApi') || 'https://adrsmpr.stylishop.com/api';
    // logInfo(`ADDRESS_MAPPER_API::${addressMapperApi}`);
    // logInfo(`warehouseId::${warehouseIds}`);
    // logInfo(`city_id::${cityId}`);
    // logInfo(`country_id::${countryId}`);

  const cityResponse = await axios.post(`${addressMapperApi}/sla/search-city`, {
    warehouse_ids: warehouseIds,
    city_id: cityId ? cityId.toString() : cityId,
    country: countryId
  }, {
    headers: {
      'Content-Type': 'application/json'
    }
  });
  return cityResponse;
}catch(error){
 logInfo(`error in the fetch City details in addressmapper : ${error}`)
}
};

exports.calculateHighestSLA = async ({ uniqueWarehouseIds, cityId, countryId = null }) => {
  let slaDetails = null;
  try {
    if (!uniqueWarehouseIds?.length && !cityId) return null;

    slaDetails = await this.fetchSLADetails({ warehouseIds: uniqueWarehouseIds, cityId, countryId });
    const data = slaDetails?.data?.response;

    // Extract only needed data before returning to allow garbage collection of full response
    const result = data?.reduce((max, item) =>
      new Date(item?.data?.estimated_date) > new Date(max?.data?.estimated_date || 0) ? item : max, null
    )?.data || null;
    
    // Explicitly nullify large response object to help GC
    slaDetails = null;
    return result;
  } catch (error) {
    // console.error("SLA calculation error:", error.message);
    // Ensure cleanup on error
    slaDetails = null;
    return null;
  }
};