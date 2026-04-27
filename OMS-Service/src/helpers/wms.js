const randomNatural = require('random-natural');
const moment = require('moment');
const axios = require('axios');
const { isEmpty } = require('lodash');
// const { getStoreConfigs } = require('../utils/config');
const { logInfo, promiseAll, stringifyError } = require('../utils');
const { getRmaReasons } = require('./rma');
const { sequelize, Order, OrderPayment } = require('../models/seqModels/index');
const { QueryTypes, Op } = require('sequelize');
const {
  WMS_RTO_PUSH_PENDING_STATUS,
  WMS_RTO_PUSH_SUCCESS_STATUS
} = require('../constants/order');
const orderObj = require('./order');
// const { tabbyRefund } = require('./refund');
const { RETURN_CANCEL_ENDPOINT } = require('../constants/javaEndpoints');
const { AUTH_INTERNAL_HEADER_BEARER_TOKEN } = process.env;
const internalAuthToken = AUTH_INTERNAL_HEADER_BEARER_TOKEN?.split(',')?.[0];

const getHeaders = locationCode => {
  const wmsCredentials = global.javaOrderServiceConfig?.inventory_mapping.find(
    item => `${item.warehouse_id}` === `${locationCode}`
  );
  return {
    authusername: wmsCredentials?.WMS_WAREHOUSE_HEADER_USER_NAME,
    authpassword: wmsCredentials?.WMS_WAREHOUSE_HEADER_PASSWORD,
    'content-type': 'application/json'
  };
};

const getForwardOrderRequest = async ({ orderData, rtoAwb }) => {
  const {
    shipmentData,
    simpleItems: items,
    increment_id: incId,
    // store_id: storeId,
    entity_id
  } = orderData;
  // const warehouseId = getStoreConfigs({ key: 'warehouseId', storeId })?.[0]
  //   ?.warehouseId;

  const warehouseId = await this.fetchWarehouseId(entity_id);

  const { track_number: waybill, title } = shipmentData?.[0] || {};
  const formattedItems = [];
  //   console.log('WMS:', { orderData, shipmentData });

  items.forEach(item => {
    for (
      let i = 0;
      i < Number(item.qty_ordered) - Number(item.qty_canceled || 0);
      i++
    ) {
      formattedItems.push({
        channelSkuCode: item.sku,
        reason: 'Order RTO',
        itemCode: `${item.item_id}_${randomNatural({ min: 100, max: 50000 })}`
      });
    }
  });

  return {
    forwardOrderCode: incId,
    orderItems: formattedItems,
    orderType: 'RETURN_TO_ORIGIN',
    returnOrderCode: incId,
    awbNumber: rtoAwb || waybill,
    transporter: title,
    // returnOrderTime: `${moment().format('Y-MM-DDTHH:MM:ss')}.000000Z`, // 2021-07-27T08:11:02.000000Z
    returnOrderTime: `${moment().format('Y-MM-DDTHH:MM:ss.SSSZ')}`, // 2021-09-09T12:09:25.825+04:00
    locationCode: `${warehouseId}` // static warehouse value
  };
};

const getReturnOrderRequest = async ({ rmaDetail = {} }) => {
  const { Order, rmaRequestItems, rma_inc_id, RmaTrackings, carrierCodes } =
    rmaDetail;
  const orderData = Order?.dataValues;
  const { dataValues: trackingData } = RmaTrackings?.[0] || {};
  const { increment_id, entity_id } = orderData;

  if (!trackingData) return {};

  const warehouseId = await this.fetchWarehouseId(entity_id);
  const rmaReasons = await getRmaReasons();
  const reasonMap = rmaReasons.reduce(
    (map, r) => ({ ...map, [r.reason_id]: r.title }),
    {}
  );

  let qt = 0;
  const formattedItems = rmaRequestItems.flatMap(rmaItem => {
    const { OrderItem: item, reason_id, qty, request_item_id } = rmaItem;
    qt = rmaItem.qty;
    const channelSkuCode = item.sku;
    const reason = reasonMap[`${reason_id}`];
    const reqId = request_item_id;

    return Array.from({ length: qty }, (_, i) => {
      const itemCode = `${item.item_id}_${reqId}${i + 1}`;
      console.log(
        `channelSkuCode: ${channelSkuCode}, qty: ${qty}, itemCode: ${itemCode}`
      );
      return { channelSkuCode, reason, itemCode };
    });
  });
  return {
    forwardOrderCode: increment_id,
    orderItems: formattedItems,
    orderType: 'CUSTOMER_RETURN',
    returnOrderCode: rma_inc_id,
    awbNumber: trackingData.tracking_number,
    transporter: carrierCodes.find(
      c => `${c.code}` === `${trackingData.tracking_code}`
    )?.label,
    returnOrderTime: `${moment().format('Y-MM-DDTHH:MM:ss.SSSZ')}`,
    locationCode: `${warehouseId}`, // static warehouse value,
    quantity: qt
  };
};

const handleReturnOrderCancellation = async ({ request, rmaDetail, forwardOrderCode, locationCode }) => {
  if (global.javaOrderServiceConfig?.order_details?.RECREATEPUSHTOWMS && rmaDetail?.rma_inc_id) {
    const formattedItems = request.orderItems;
    const filteredId = extractFilteredId(rmaDetail?.rma_inc_id);
    if (
      global.javaOrderServiceConfig?.order_details?.RETCANPUSHTOWMS &&
      filteredId
    ) {
      console.log(`Found recreated return order`, filteredId);
      try {
        const qt = request.quantity;
        await pushReturnCancelToWms(
          filteredId,
          formattedItems,
          forwardOrderCode,
          locationCode,
          qt
        );
      } catch (error) {
        const stringifiedError = stringifyError(error);
        console.error(
          `Error in cancelling return order: ${stringifiedError}`
        );
      }
    }
  }
};

const formatRequestBody = ({ request }) => {
  let formattedRequest;
  if (request.hasOwnProperty('quantity')) {
    const { quantity, ...rest } = request;
    formattedRequest = rest;
  } else {
    formattedRequest = { ...request };
  }
  return formattedRequest;
};

const handleWmsResponse = async ({ wmsResponse, isReturnFlow, orderData }) => {
  const { status } = wmsResponse;
  if (status == 200 && !isReturnFlow) {
    await Order.update(
      { wms_status: WMS_RTO_PUSH_SUCCESS_STATUS },
      { where: { entity_id: orderData.entity_id } }
    );
  }
};

exports.pushOrderToWms = async ({
  orderData,
  isReturnFlow,
  rtoAwb,
  rmaDetail
}) => {
  const request = isReturnFlow
    ? await getReturnOrderRequest({ rmaDetail })
    : await getForwardOrderRequest({ orderData, rtoAwb });
  if (isEmpty(request)) return false;

  const { forwardOrderCode, locationCode } = request;

  const wmsEndpoint =
    global.javaOrderServiceConfig?.order_details?.WMS_SERVICE_BASE_URL;

  const finalEndpoint = `${wmsEndpoint}/return/return-orders`;
  logInfo(
    `WMS Endpoint for ${
      rmaDetail?.rma_inc_id || forwardOrderCode
    }: ${finalEndpoint}`
  );
  logInfo(
    isReturnFlow
      ? `WMS request Return order ${rmaDetail?.rma_inc_id}`
      : `WMS request forward order ${forwardOrderCode}`,
    request
  );
  console.log(
    `RETCANPUSHTOWMS flag is set to `,
    global.javaOrderServiceConfig?.order_details?.RETCANPUSHTOWMS
  );
  console.log(
    `RECREATEPUSHTOWMS flag is set to `,
    global.javaOrderServiceConfig?.order_details?.RECREATEPUSHTOWMS
  );

  try {
    await handleReturnOrderCancellation({ request, rmaDetail, forwardOrderCode, locationCode });
    
    const formattedRequest = formatRequestBody({ request });
    logInfo(
      `WMS Request body for order ${
        isReturnFlow ? rmaDetail?.rma_inc_id : forwardOrderCode
      }`,
      { requestBody: JSON.stringify(formattedRequest) }
    );
    const headers = getHeaders(locationCode);
    const wmsResponse = await axios.post(finalEndpoint, formattedRequest, {
      headers
    });
    const { status, statusText } = wmsResponse;
    logInfo(
      `WMS Response for order ${
        isReturnFlow ? rmaDetail?.rma_inc_id : forwardOrderCode
      }`,
      { status, statusText }
    );

    await handleWmsResponse({ wmsResponse, isReturnFlow, orderData });
    return true;
  } catch (error) {
    const stringifiedError = stringifyError(error);
    console.log(`Error in wmsResponse : ${stringifiedError}`);
  }
};

exports.fetchWarehouseId = async orderEntityId => {
  const selectQuery = `SELECT warehouse_id FROM sub_sales_order where order_id="${orderEntityId}"`;

  const queryResponse = await sequelize.query(selectQuery, {
    // replacements: [rmaIncrementId],
    type: QueryTypes.SELECT
  });
  const warehouseId = queryResponse?.[0]?.warehouse_id;
  // console.log({ queryResponse, warehouseId });
  return warehouseId || 110;
};

const sendRtoOrder = async entityId => {
  const orderData = await orderObj.getOrder({ entityId, inclSubSales: true });
  console.log(' WMS RTO Push : Send RTO Order: ', JSON.stringify(orderData));
  const { subSales } = orderData;
  this.pushOrderToWms({
    orderData,
    isReturnFlow: false,
    rtoAwb: subSales.extra_1
  });
};

exports.pushRtoOrders = async () => {
  const { interval, batchSize } = global?.baseConfig?.wmsRtoPush || {};
  const orderResponse = await Order.findAll({
    attributes: ['entity_id'],
    where: {
      updated_at: {
        [Op.gte]: moment().subtract(Number(interval), 'minutes').toDate(),
        [Op.lte]: moment().toDate()
      },
      wms_status: WMS_RTO_PUSH_PENDING_STATUS
    },
    include: [{ model: OrderPayment }],
    limit: batchSize
  });
  const promiseArr = [];
  console.log(
    ' WMS RTO Push : orderResponse in pushRtoOrders ',
    JSON.stringify(orderResponse)
  );
  orderResponse.forEach(order => {
    console.log(
      ' WMS RTO Push : Orders to be processed: ',
      order.dataValues.entity_id
    );
    promiseArr.push(sendRtoOrder(order?.dataValues?.entity_id));
  });

  if (promiseArr.length) await promiseAll(promiseArr);
};

const pushReturnCancelToWms = async (
  filteredId,
  formattedItems,
  forwardOrderCode,
  locationCode
) => {
  console.log('Cancel Return Id :', filteredId);
  const modifiedItems = formattedItems.map(item => {
    return {
      channelSkuCode: item.channelSkuCode,
      returnOrderItemCode: item.itemCode,
      reason: item.reason
    };
  });

  const payload = {
    returnOrderCode: filteredId || '',
    orderCode: forwardOrderCode || '',
    locationCode: locationCode || '',
    returnOrderItems: modifiedItems || []
  };
  console.log('Payload:', JSON.stringify(payload));
  try {
    const response = await axios.post(RETURN_CANCEL_ENDPOINT, payload, {
      headers: {
        'authorization-token': internalAuthToken
      }
    });
    console.log('Response of pushReturnCancelToWms', response.data);
  } catch (error) {
    const stringifiedError = stringifyError(error);
    console.log(`Error in processing: ${stringifiedError}`);
  }
};
function extractFilteredId(rmaIncId) {
  const splitParts = rmaIncId.split('_');

  if (splitParts.length === 2) {
    return splitParts[0];
  } else if (splitParts.length > 2) {
    return splitParts.slice(0, 2).join('_');
  } else {
    return null;
  }
}
