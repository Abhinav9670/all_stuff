const emailObj = require('../../helpers/email');
const { processSMS } = require('../../helpers/processSms');

const processOrderSuccess = async (pubSubMessage) => {
  const { data, id } = pubSubMessage;
  try {
    console.log(`[PUB/SUB] Processing Order Success Message ID: ${id}`);

    const dataFromPubSub = JSON.parse(data.toString());
    console.log(`[PUB/SUB] Order Success Message Data: ${JSON.stringify(dataFromPubSub)}`);

    let orderId = dataFromPubSub?.orderid;
    if (typeof dataFromPubSub === 'string') {
      orderId = JSON.parse(dataFromPubSub)?.orderid;
    }

    console.log(`[PUB/SUB] Extracted Order ID: ${orderId}`);

    if (!orderId) {
      throw new Error('Order ID not found in the Pub/Sub message.');
    }
    const { SplitSalesOrder, SplitSalesOrderItem } = require('../../models/seqModels');
    const dataVal = await SplitSalesOrder.findAll({
      where: { order_id: orderId },
      attributes: ['increment_id', 'entity_id'],
      include: [
        {
          model: SplitSalesOrderItem,
          as: 'SplitSalesOrderItems',
          attributes: ['estimated_delivery_date'],
          where: {
            product_type: 'simple'
          },
          required: true
        }
      ]
    });
    const splitOrderData = {};
    (dataVal ?? []).slice(0, 2).forEach((order, index) => {
      splitOrderData[`increment${index + 1}`] = order.increment_id;
      splitOrderData[`est${index + 1}`] =
        order.SplitSalesOrderItems?.[0]?.estimated_delivery_date ?? null;
    })

    emailObj.sendEmail({
      type: 'orderConfirm',
      orderId,
    });

    let successTemplate = dataVal.length === 0 ? 'order_place_success' : 'order_place_success_split_order'

    processSMS({
      type: 'order',
      template: successTemplate,
      entityId: orderId,
      updateCleverTap: true,
      splitOrderData
    });

    pubSubMessage.ack();
    console.log(`[PUB/SUB] Order Success Message ID: ${id} processed and acknowledged.`);
  } catch (error) {
    console.error(`[PUB/SUB] Error processing Order Success Message ID: ${id}`, error);
    global.logError(
      error.message.substring(0, 1000),
      `[PUB/SUB] ~~~Error processing Pub/Sub message ID: ${id} for Order Success~~~`
    );
    pubSubMessage.nack();
    console.log(`[PUB/SUB] Order Success Message ID: ${id} negatively acknowledged.`);
  }
};

module.exports = {
  processOrderSuccess,
};