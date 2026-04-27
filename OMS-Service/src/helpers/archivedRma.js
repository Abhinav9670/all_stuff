const {
  RmaRequest,
  RmaRequestItem,
  RmaTracking,
  Order,
  OrderItem,
  OrderAddress,
  OrderComment
} = require('../models/seqModels/archiveIndex');
const { Op } = require('sequelize');
const { prepareFilters, applyQuery } = require('./rmaFilters');
const { getKSATime } = require('./moment');
const { getProductsBySKU } = require('../services/misc.service');
const { sanitiseImageUrl } = require('../utils');
const axios = require('axios');

const getArchivedRmaDetail = async ({ requestId, rmaIncrementId }) => {
  const trackingBaseUrl = global?.baseConfig?.configs?.trackingBaseUrl;

  const where = requestId
    ? { request_id: requestId }
    : { rma_inc_id: rmaIncrementId };
  const { dataValues: rma } = await RmaRequest.findOne({
    where,
    include: [
      { model: RmaRequestItem },
      { model: RmaTracking },
      {
        model: Order,
        include: [{ model: OrderAddress, where: { address_type: 'shipping' } }]
      }
    ]
  });

  const orderItemIds = rma.RmaRequestItems.map(el => el.order_item_id) || [];
  const items = await OrderItem.findAll({
    where: {
      item_id: orderItemIds
    },
    raw: true
  });

  const itemsMap = {};
  const skus = [];
  items.forEach(el => {
    itemsMap[el.item_id] = el;
    skus.push(el.sku);
  });

  const getProductDetailsFromMulin = await getProductsBySKU({ skus });

  const rmaRequestItems = rma?.RmaRequestItems?.map(item => item.dataValues);
  rmaRequestItems.forEach(el => {
    const itemObject = itemsMap[el.order_item_id];

    if (!el.item_status || el.item_status === 1) el.item_status = rma.status;

    for (const key in getProductDetailsFromMulin) {
      const item = getProductDetailsFromMulin[key];
      const variant = item.variants.find(el => el.sku === itemObject.sku);
      if (variant) {
        const imageUrl = (item?.media_gallery || [])[0]?.value;
        itemObject.imageUrl = sanitiseImageUrl(imageUrl);
      }
    }
    el.OrderItem = itemObject;
  });

  rma.rmaRequestItems = rmaRequestItems;

  const carrierCodes = global.baseConfig?.configs?.carrierCodes || [];
  rma.carrierCodes = carrierCodes;
  rma.trackingBaseUrl = trackingBaseUrl;
  if (rma.created_at) rma.created_at = getKSATime(rma.created_at);
  rma.archived = 1;
  return rma;
};

const getArchivedRmaRequests = async ({
  offset = 0,
  limit = 10,
  filters = {},
  query
}) => {
  //   Prepare "where" filters
  let where = prepareFilters(filters);
  where = query ? applyQuery({ where, query }) : where;

  const payload = {
    offset: offset * limit,
    limit,
    where,
    order: [['created_at', 'DESC']]
  };

  const include = { model: Order };
  if (filters.order_id) include.where = { increment_id: filters.order_id };
  payload.include = [include];

  const { count, rows: rmaRequestsData } = await RmaRequest.findAndCountAll(
    payload
  );
  return {
    count,
    hits: (rmaRequestsData || []).map(el => {
      const object = el.dataValues;
      object.order_id = object.Order?.entity_id;
      if (object.created_at) object.created_at = getKSATime(object.created_at);
      object.order_increment_id = object.Order?.increment_id;
      delete object.Order;
      object.archived = 1;
      return object;
    })
  };
};

const getArchivedRmaComments = async (rmaIncrementId, orderId) => {
  let comments = [];
  try {
    const result = await OrderComment.findAll({
      where: {
        parent_id: orderId,
        comment: { [Op.like]: `%${rmaIncrementId}%` }
      }
    });
    comments = result;
  } catch (e) {
    global.logError(e, 'could not fetch comments for RMA: ' + rmaIncrementId);
  }
  return comments;
};

const getCityData = async (countryId, regionId, city) => {
  try {
    const url = `${process.env.CITY_DATA_API_URL}/api/address/getCityDataFromProvinence`;
    let cityData = await axios.post(url, {
      countryId,
      regionId,
      city
    });

    if (cityData?.data?.status && cityData.data.response) {
      return cityData.data.response;
    } else {
      throw new Error('No city data');
    }
  } catch (err) {
    global.logError('getCityData:', err);
  }
};

module.exports = {
  getArchivedRmaRequests,
  getArchivedRmaDetail,
  getArchivedRmaComments,
  getCityData
};
