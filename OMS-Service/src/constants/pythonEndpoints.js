const { PYTHON_BASE_URL } = process.env;

exports.DASHBOARD_LAST_HOUR_ORDER = `${PYTHON_BASE_URL}/api/v1/oms_order_dashboard`;
exports.DASHBOARD_LAST_24_HOUR_ORDER_SALES_CHART = `${PYTHON_BASE_URL}/api/v1/oms_chart`;
