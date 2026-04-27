const {
  JAVA_BASE_URL,
  INVENTORY_BASE_URL,
  JAVA_CUSTOMER_BASE_URL,
  OTS_BASE_URL
} = process.env;
// const { ARCHIVE_JAVA_BASE_URL } = process.env;

exports.CUSTOMER_LIST_ENDPOINT = `${JAVA_CUSTOMER_BASE_URL}/rest/customer/oms/list`;
exports.CUSTOMER_DETAIL_ENDPOINT = `${JAVA_CUSTOMER_BASE_URL}/rest/customer/oms/details`;
exports.CUSTOMER_DEVICE_DELETE_ENDPOINT = `${JAVA_CUSTOMER_BASE_URL}/rest/customer/oms/delete/customer/login/history`;
exports.CUSTOMER_UPDATE_ENDPOINT = `${JAVA_CUSTOMER_BASE_URL}/rest/customer/oms/update`;

exports.ADDRESS_LIST_ENDPOINT = `${JAVA_CUSTOMER_BASE_URL}/rest/customer/oms/address/list`;
exports.ADDRESS_UPDATE_ENDPOINT = `${JAVA_CUSTOMER_BASE_URL}/rest/customer/oms/address/update`;

exports.WALLET_LIST_ENDPOINT = `${JAVA_BASE_URL}/rest/order/oms/storecredit/history`;
exports.WALLET_ADD_ENDPOINT = `${JAVA_BASE_URL}/rest/order/oms/storecredit/add`;

exports.CREATE_JWT = `${JAVA_CUSTOMER_BASE_URL}/rest/customer/token/create`;
exports.CREATE_JWT_V2 = `${JAVA_CUSTOMER_BASE_URL}/rest/customer/v2/token/create`;

exports.ORDER_CREATE_ENDPOINT = `${JAVA_BASE_URL}/rest/order/auth/v2/create`;
exports.ORDER_RECREATE_ENDPOINT = `${JAVA_BASE_URL}/rest/order/oms/recreate`;
exports.ORDER_LIST_ENDPOINT = `${JAVA_BASE_URL}/rest/order/oms/list`;
exports.ORDER_PRVIOUS_LIST_ENDPOINT = `${JAVA_BASE_URL}/rest/order/firstorder`;

exports.ORDER_DETAIL_ENDPOINT = `${JAVA_BASE_URL}/rest/order/oms/details`;
exports.ORDER_INVOICE_ENDPOINT = `${JAVA_BASE_URL}/rest/order/oms/invoice/details`;
exports.ORDER_SHIPMENT_ENDPOINT = `${JAVA_BASE_URL}/rest/order/oms/shipping/details`;
exports.ORDER_ADDRESS_UPDATE_ENDPOINT = `${JAVA_BASE_URL}/rest/order/oms/address/update`;
exports.ORDER_STATUS_UPDATE_ENDPOINT = `${JAVA_BASE_URL}/rest/order/oms/status/update`;
exports.ORDER_SHIPMENT_CREATE_ENDPOINT = `${JAVA_BASE_URL}/rest/order/oms/shipment/create`;
exports.ORDER_SELLER_SHIPMENT_CREATE_ENDPOINT = `${JAVA_BASE_URL}/rest/order/seller/shipment/create`;
exports.ORDER_AWB_CREATE_ENDPOINT = `${JAVA_BASE_URL}/rest/order/oms/get-shipment-v3`;
exports.ORDER_SELLER_AWB_CREATE_ENDPOINT = `${JAVA_BASE_URL}/rest/order/oms/seller/get-shipment-v3`;
exports.ORDER_SELLER_CANCEL_ENDPOINT = `${JAVA_BASE_URL}/rest/order/seller/cancel`;

exports.RMA_UPDATE_ENDPOINT = `${JAVA_BASE_URL}/rest/order/oms/rma/update`;

exports.RMA_REFUND_ENDPOINT = `${JAVA_BASE_URL}/rest/order/oms/return/refund`;
exports.RMA_TABBY_REFUND_ENDPOINT = `${JAVA_BASE_URL}/rest/order/tabby/refund`;

exports.RMA_INIT_ENDPOINT = `${JAVA_BASE_URL}/rest/order/oms/rma/init`;
exports.RMA_CREATE_ENDPOINT = `${JAVA_BASE_URL}/rest/order/oms/rma`;
exports.RMA_CREATE_AWB_ENDPOINT = `${JAVA_BASE_URL}/rest/order/oms/return/get-awb/requestid`;

// exports.ARCHIVE_ORDER_LIST_ENDPOINT = `${ARCHIVE_JAVA_BASE_URL}/rest/archive/order/oms/list`;
// exports.ARCHIVE_ORDER_DETAIL_ENDPOINT = `${ARCHIVE_JAVA_BASE_URL}/rest/archive/order/oms/details`;
// exports.ARCHIVE_ORDER_INVOICE_ENDPOINT = `${ARCHIVE_JAVA_BASE_URL}/rest/archive/invoice/oms/details`;
// exports.ARCHIVE_ORDER_SHIPMENT_ENDPOINT = `${ARCHIVE_JAVA_BASE_URL}/rest/archive/shipping/oms/details`;
exports.SKU_INVENTORY_ENDPOINT = `${INVENTORY_BASE_URL}/api/inventory/storefront/atp`;
exports.REFUND_LIST_ENDPOINT = `${JAVA_BASE_URL}/rest/order/refund/list`;
exports.REFUND_STATUS_UPDATE_ENDPINIT = `${JAVA_BASE_URL}/rest/order/refund/bulkrefund`;
exports.RETURN_CANCEL_ENDPOINT = `${JAVA_BASE_URL}/rest/order/oms/rma/pushReturnCancelToWms`;
exports.CREATE_COD_RTO_CREDITMEMO = `${JAVA_BASE_URL}/rest/order/oms/cod/rto/zatca`;
exports.LOCK_UNLOCK_SHUKRAN_POINTS=`${JAVA_BASE_URL}/rest/order/oms/shukran/lockAndUnlock`;

// OTS API endpoints
exports.OTS_ORDER_DETAILS_ENDPOINT = `${OTS_BASE_URL}/v1/orderDetailsOMS`;
