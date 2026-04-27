const {
  OrderItem,
  Creditmemo,
  CreditmemoItem,
  CreditmemoComment
} = require('../models/seqModels/archiveIndex');
const { getProductsBySKU } = require('../services/misc.service');
const { sanitiseImageUrl } = require('../utils');
const { getStoreConfigs } = require('../utils/config');
const { getKSATime } = require('./moment');

const getItems = ({ memoItems, itemIds, getProductDetailsFromMulin }) => {
  const items = [];
  memoItems.forEach(el => {
    if (itemIds.includes(el.order_item_id)) {
      const itemObject = el;
      for (const key in getProductDetailsFromMulin) {
        const item = getProductDetailsFromMulin[key];
        const variant = item.variants.find(variant => variant.sku === el.sku);
        if (variant) {
          const imageUrl = (item?.media_gallery || [])[0]?.value;
          itemObject.imageUrl = sanitiseImageUrl(imageUrl);
        }
      }
      items.push(itemObject);
    }
  });
  return items;
};

exports.getArchivedCreditMemos = async ({ orderId }) => {
  const creditMemos = await Creditmemo.findAll({
    where: { order_id: orderId },
    include: [{ model: CreditmemoItem }, { model: CreditmemoComment }]
    // raw: true
  });
  if (!creditMemos.length) return { error: 'Credit Memo(s) not found!' };

  const orderItems = await OrderItem.findAll({
    where: { order_id: orderId },
    raw: true
  });

  const parentItemIds = (orderItems || [])
    .filter(i => i.product_type === 'configurable')
    .map(el => el.item_id);

  const skus = (orderItems || [])
    .filter(i => i.product_type === 'simple')
    .map(el => el.sku);
  const getProductDetailsFromMulin = await getProductsBySKU({ skus });

  const memos = creditMemos?.map(memo => memo.dataValues);
  const response = [];
  for (const memo of memos) {
    const { CreditmemoItems, CreditmemoComments, ...rest } = memo;

    let refundedAmount = Number(memo.grand_total);
    if (Number(memo.amstorecredit_amount) > 0)
      refundedAmount += Number(memo.amstorecredit_amount);

    let formattedDate = undefined;
    if (memo.created_at) formattedDate = getKSATime(memo.created_at);
    const memoItems = CreditmemoItems?.map(item => item.dataValues);
    const memoComments = CreditmemoComments?.map(comment => comment.dataValues);
    const comments = [];
    memoComments.forEach(el => {
      comments.push({
        ...el,
        created_at: getKSATime(el.created_at)
      });
    });

    const items = getItems({
      typeReturn: memo.rma_number,
      memoItems,
      itemIds: parentItemIds,
      getProductDetailsFromMulin
    });

    let showTax = true;
    const configValue = getStoreConfigs({
      key: 'taxPercentage',
      storeId: memo?.store_id
    });
    if (configValue.length) {
      const taxPercentage = configValue[0].taxPercentage;
      if (!taxPercentage || taxPercentage === 0) showTax = false;
    }

    response.push({
      ...rest,
      refundedAmount: refundedAmount,
      items,
      comments,
      showTax,
      created_at: formattedDate
    });
  }

  return {
    error: false,
    response
  };
};
