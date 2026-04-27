const {
  RmaRequest,
  RmaRequestItem,
  RmaTracking,
  Order,
  SubSalesOrder,
  OrderItem,
  OrderAddress,
  sequelize,
  RmaStatus,
  OrderCancelReason,
  RmaReason,
  Creditmemo,
  OrderComment
} = require('../models/seqModels/index');
const { QueryTypes } = require('sequelize');
const { getProductsBySKU } = require('../services/misc.service');
const { sanitiseImageUrl } = require('../utils');
const { prepareFilters, applyQuery } = require('./rmaFilters');
const { getKSATime } = require('./moment');
const crypto = require('crypto');
const { getStoreConfigs } = require('../utils/config');
const moment = require('moment');
const { Op } = sequelize.Sequelize;

// Import split order utilities
const { isSplitOrderPattern } = require('../utils/splitOrderUtils');

const getRmaDetail = async ({ requestId, rmaIncrementId }) => {
  let encryptedTrackNumber;
  const trackingBaseUrl = global?.baseConfig?.configs?.trackingBaseUrl;

  const where = requestId
  ? {
      [Op.or]: [
        { request_id: Number(requestId) },  // Try as number
        { request_id: String(requestId) }   // Try as string
      ]
    }
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

  if (rma?.RmaTrackings?.[0]?.tracking_number) {
    console.log('AWB', rma.RmaTrackings[0].tracking_number);
    encryptedTrackNumber = encryptAWB(rma.RmaTrackings[0].tracking_number); // Assign the result to a variable
  }
  const orderItemIds = rma.RmaRequestItems.map(el => el.order_item_id) || [];
  
  // Detect if this is a split order based on order increment_id
  const orderIncrementId = rma.Order?.increment_id;
  const isSplit = orderIncrementId && isSplitOrderPattern(orderIncrementId.toString());
  
  let items;
  if (isSplit) {
    // For split orders, use SplitSellerOrderItem
    const { SplitSellerOrderItem } = require('../models/seqModels/index');
    items = await SplitSellerOrderItem.findAll({
      where: {
        item_id: orderItemIds
      },
      raw: true
    });
  } else {
    // For regular orders, use OrderItem
    items = await OrderItem.findAll({
      where: {
        item_id: orderItemIds
      },
      raw: true
    });
  }

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
  rma.archived = 0;

  if (rma.shipping_label && !rma.shipping_label.startsWith('https://'))
    rma.shipping_label = rma.shipping_label.replace('http://', 'https://');
  rma.encryptedTrackNumber = encryptedTrackNumber;
  return rma;
};

const getRmaStatus = async () => {
  const response = await RmaStatus.findAll();
  return response?.map(status => status.dataValues);
};

const getRmaStatusDetails = async statusId => {
  return await RmaStatus.findOne({
    where: { status_id: statusId }
  });
};

const updateRmaStatusDetails = async updateObj => {
  const { statusId, isEnabled, title, statusCode, color, priority } = updateObj;
  return await RmaStatus.update(
    {
      is_enabled: isEnabled,
      title: title,
      status_code: statusCode,
      color: color,
      priority: priority
    },
    {
      where: { status_id: statusId }
    }
  );
};

const getRmaReasons = async () => {
  const response = await RmaReason.findAll();
  return response?.map(reason => reason?.dataValues);
};

const getRmaShipmentDetail = async ({ rmaIncrementId, omsShortCheckV2Enable = false }) => {
  const queryResponse = await sequelize.query(
    'SELECT  sop.method,so.increment_id as order_inc_id,so.status as order_status,`request_id`, `order_id`, rr.`store_id`,`return_type`,rr.is_short_pickedup, rr.is_fraud_pickedup, rr.`created_at`, `modified_at`, rr.`status` as `status_id`, rr.`customer_id`, rr.`customer_name`, rr.`rma_inc_id`,rs.title as `status_title`,rs.status_code, rma_payment_method FROM `amasty_rma_request` AS `rr` left join amasty_rma_status as rs on rr.status = rs.status_id left join sales_order as so on rr.order_id = so.entity_id left join sales_order_payment as sop on sop.parent_id = rr.order_id WHERE `rr`.`rma_inc_id` = ? LIMIT 1',
    {
      replacements: [rmaIncrementId],
      type: QueryTypes.SELECT
    }
  );

  const rmaData = queryResponse?.[0];
  if (!rmaData) {
    return {
      status: false,
      data: {}
    };
  }

  let promiseArray = [];
  if(omsShortCheckV2Enable){
    promiseArray = [
      OrderAddress.findOne({
        where: { parent_id: rmaData.order_id, address_type: 'shipping' },
        raw: true
      }),
      RmaRequestItem.findAll({
        where: { request_id: rmaData.request_id },
        raw: true
      })
    ];
    
  } else {
    promiseArray = [
      getRmaStatus(),
      OrderAddress.findOne({
        where: { parent_id: rmaData.order_id, address_type: 'shipping' }
      }),
      RmaRequestItem.findAll({
        where: { request_id: rmaData.request_id }
      }),
      RmaTracking.findOne({
        where: { request_id: rmaData.request_id }
      }),
      Creditmemo.findOne({
        where: { rma_number: rmaData.request_id }
      })
    ];    
  }
  return await Promise.allSettled(promiseArray).then(async values => {
    values.forEach(value => {
      // console.log({ v: value?.value });
      if (value.status === 'rejected') {
        global.logError(value.reason);
        return { status: false, msg: value.reason };
      }
    });

    const rmaItems = (omsShortCheckV2Enable ? values[1]?.value : values[2]?.value?.map(item => item.dataValues));

    const orderItemIds = rmaItems.map(el => el.order_item_id) || [];
    
    // Detect if this is a split order based on order increment_id
    const isSplit = rmaData.order_inc_id && isSplitOrderPattern(rmaData.order_inc_id.toString());
    
    let items;
    if (isSplit) {
      // For split orders, use SplitSellerOrderItem
      const { SplitSellerOrderItem } = require('../models/seqModels/index');
      items = await SplitSellerOrderItem.findAll({
        where: {
          item_id: orderItemIds
        },
        raw: true
      });
    } else {
      // For regular orders, use OrderItem
      items = await OrderItem.findAll({
        where: {
          item_id: orderItemIds
        },
        raw: true
      });
    }
    
    const itemsMap = {};
    items.forEach(el => {
      itemsMap[el.item_id] = el;
    });

    rmaItems.forEach(el => {
      const itemObject = itemsMap[el.order_item_id];
      el.OrderItem = itemObject;
      el.name = itemObject.name;
    });

    let retObj = {};
    if(omsShortCheckV2Enable){
      retObj = {
        rmaData,
        address: values[0]?.value,
        rmaItems
      };
    } else {
      retObj = {
        rmaData,
        rmaStatusData: values[0].value,
        address: values[1]?.value?.dataValues,
        rmaItems,
        rmaTracking: values[3]?.value?.dataValues,
        creditMemo: values[4]?.value?.dataValues
      };
    }

    return {
      status: true,
      data: retObj
    };
  });
};

const getRmaRequests = async ({
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
    order: [['created_at', 'DESC'],["request_id",'DESC']],
    distinct: true
  };

  const include = { model: Order, required: !!filters.order_id};
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
      object.archived = 0;
      return object;
    })
  };
};

const removeTracking = async ({ tracking_id }) => {
  await RmaTracking.destroy({
    where: {
      tracking_id: tracking_id
    }
  });
};

const isShortPickUp = remark => {
  // let result = false;
  const shortPickupEnabled = global?.baseConfig?.configs?.shortPickupEnabled;
  const shortPickupCodes = global?.baseConfig?.configs?.shortPickupCodes || '';
  const shortPickupCodArr = shortPickupCodes.split(',');
  // console.log({ shortPickupCodes, shortPickupCodArr, shortPickupEnabled });
  let flag = false;
  if (shortPickupCodes && shortPickupEnabled) {
    for (const code of shortPickupCodArr) {
      if (remark.includes(code)) {
        flag = true;
        break;
      }
    }
  }
  return flag;
};

const getCancelReasons = async () => {
  const response = await OrderCancelReason.findAll();
  // console.log(response);
  return response?.map(status => status.dataValues);
};

const getReturnReasons = async () => {
  const response = await RmaReason.findAll();
  return response?.map(status => status.dataValues);
};

function encryptAWB(waybill) {
  try {
    const SECRET_KEY =
      global.javaOrderServiceConfig?.order_details?.AWB_ENCRYPTION_SECRET;
    const SALT =
      global.javaOrderServiceConfig?.order_details?.AWB_ENCRYPTION_SALT;
    const iterations = 10000;
    const keyLength = 16; // 128 bits key length (16 bytes)

    const key = crypto.pbkdf2Sync(
      SECRET_KEY,
      SALT,
      iterations,
      keyLength,
      'sha256'
    );
    const cipher = crypto.createCipheriv('aes-128-ecb', key, '');

    let encryptedAWB = cipher.update(waybill, 'utf8', 'base64');
    encryptedAWB += cipher.final('base64');
    console.log('encryptedAWB: ', encryptedAWB);
    // URL-encode the encrypted value
    const encodedAWB = encodeURIComponent(encryptedAWB);
    return encodedAWB;
  } catch (error) {
    console.error('Exception occurred in encrypting AWB: ', error);
    return null;
  }
}

const getRmaData = async rmaId => {
  try {
    const rmaData = await RmaRequest.findOne({
      where: { rma_inc_id: rmaId },
      attributes: ['request_id', 'zatca_details', 'zatca_qr_code']
    });
    return JSON.parse(JSON.stringify(rmaData));
  } catch (err) {
    throw err;
  }
};

const saveShukranEarnedPointsInDb = async (
  orderIncrementId,
  totalPointsEarned,
  transactionNetTotal
) => {
  global.logInfo('saveShukranEarnedPointsInDb');

  const orderData = await returnOrderData(orderIncrementId);
  global.logInfo('saveShukranEarnedPointsInDb2', orderData?JSON.stringify(orderData):'');
  if (orderData) {
    const storeData = await getStoreConfigs({
      key: 'shukranPointConversion',
      storeId: orderData.store_id
    });
    const baseStoreData = await getStoreConfigs({
      key: 'shukranPointConversion',
      storeId: 1
    });
    await SubSalesOrder.update(
      {
        total_shukran_coins_earned: parseFloat(totalPointsEarned),
        total_shukran_earned_value_in_base_currency:
          baseStoreData && baseStoreData.length > 0
            ? parseFloat(
                (
                  parseFloat(totalPointsEarned) *
                  parseFloat(baseStoreData[0].shukranPointConversion)
                ).toFixed(2)
              )
            : parseFloat((parseFloat(totalPointsEarned) * 0.05).toFixed(2)),
        total_shukran_earned_value_in_currency:
          storeData && storeData.length > 0
            ? parseFloat(
                (
                  parseFloat(totalPointsEarned) *
                  parseFloat(storeData[0].shukranPointConversion)
                ).toFixed(2)
              )
            : parseFloat((parseFloat(totalPointsEarned) * 0.05).toFixed(2)),
        shukran_pr_transaction_net_total: transactionNetTotal,
        shukran_pr_successful: 1,
        pr_updated_at: moment.utc().format()
      },
      { where: { order_id: orderData.entity_id } }
    );
  }
};

const saveShukranEarnedPointsInOrderHistory = async (
  orderIncrementId,
  totalPointsEarned
) => {
  global.logInfo('saveShukranEarnedPointsInOrderHistory');
  const orderData = await returnOrderData(orderIncrementId);
  if (orderData) {
    await OrderComment.create({
      parent_id: orderData.entity_id,
      status: orderData.status,
      created_at: moment.utc().format('YYYY-MM-DD HH:mm:ss'),
      entity_name: "order",
      comment: `Shukran Points Earned: ${totalPointsEarned}`
    });
  }
};

const saveShukranPrSuccessfulInDb = async orderIncrementId => {
  global.logInfo('saveShukranPrSuccessfulInDb');

  const orderData = await returnOrderData(orderIncrementId);
  if (orderData) {
    await SubSalesOrder.update(
      {
        shukran_pr_successful: 1,
        pr_updated_at: moment.utc().format()
      },
      { where: { order_id: orderData.entity_id } }
    );
  }
};

const saveShukranPrTimingInDb = async orderIncrementId => {
  global.logInfo('saveShukranPrSuccessfulInDb');

  const orderData = await returnOrderData(orderIncrementId);
  if (orderData) {
    await SubSalesOrder.update(
      {
        pr_updated_at: moment.utc().format()
      },
      { where: { order_id: orderData.entity_id } }
    );
  }
};

const returnOrderData = async orderIncrementId => {
  let incrementId = orderIncrementId;
  if (orderIncrementId.includes(global.config.shukranEnrollmentCommonCode)) {
    incrementId = orderIncrementId.replace(
      global.config.shukranEnrollmentCommonCode,
      ''
    );
  }
  let orderData = await Order.findOne({
    where: { increment_id: incrementId },
    attributes: ['entity_id', 'store_id', 'status']
  });
  return JSON.parse(JSON.stringify(orderData));
};

module.exports = {
  getRmaStatus,
  getRmaRequests,
  getRmaShipmentDetail,
  getRmaDetail,
  isShortPickUp,
  getCancelReasons,
  getReturnReasons,
  removeTracking,
  getRmaReasons,
  getRmaStatusDetails,
  updateRmaStatusDetails,
  getRmaData,
  saveShukranEarnedPointsInDb,
  saveShukranPrSuccessfulInDb,
  saveShukranPrTimingInDb,
  saveShukranEarnedPointsInOrderHistory
};
