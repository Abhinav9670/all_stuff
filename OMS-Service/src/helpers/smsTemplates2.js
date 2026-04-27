/* eslint-disable max-lines */
/* eslint-disable max-lines-per-function */

exports.autoRefundTemplate = inputData => {
  const {
    smsType,
    firstname,
    lastname,
    incrementId,
    onlineRefundAmount
  } = inputData;
  let replaceMap;
  const returnData = inputData.returnData;
  const creditMemo = returnData?.creditMemo;
  const autoRefundData = returnData?.autoRefundResponse;
  const { requestedQty = 0, returnedQty = 0 } = autoRefundData || {};
  const amount = `${creditMemo?.order_currency_code} ${onlineRefundAmount}`;
  switch (smsType) {
    case 'autorefund_pp_less':
    case 'autorefund_cod_less':
      replaceMap = {
        '{{CName}}': `${firstname} ${lastname}`,
        '{{#amount_currency}}': amount,
        '{{#items}}': requestedQty,
        '{{#missing_items_count}}': requestedQty - returnedQty
      };
      break;
    case 'autorefund_pp_equal':
    case 'autorefund_cod_equal':
      replaceMap = {
        '{{CName}}': `${firstname} ${lastname}`,
        '{{#order_id}}': incrementId,
        '{{#return_id}}': returnData?.rmaData?.rma_inc_id,
        '{{#amount_currency}}': amount
      };
      break;
    case 'autorefund_pp_excess':
    case 'autorefund_cod_excess':
      replaceMap = {
        '{{CName}}': `${firstname} ${lastname}`,
        '{{#items}}': requestedQty,
        '{{#excess_items_count}}': returnedQty - requestedQty,
        '{{#amount_currency}}': amount
      };
      break;
    default:
  }
  return replaceMap;
};
