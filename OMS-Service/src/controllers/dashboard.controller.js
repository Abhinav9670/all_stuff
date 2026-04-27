const axios = require('axios');
const httpStatus = require('http-status');
const catchAsync = require('../utils/catchAsync');
const dashboardService = require('../services/dashboard.service');
const {
  DASHBOARD_LAST_HOUR_ORDER,
  DASHBOARD_LAST_24_HOUR_ORDER_SALES_CHART
} = require('../constants/pythonEndpoints');

const orderStats = catchAsync(async (req, res) => {
  const response = await dashboardService.getOrderStats();
  res.status(httpStatus.OK).json({
    status: true,
    statusCode: '200',
    statusMsg: 'Order Stats fetched successfully',
    response
  });
});

const bestsellerStats = catchAsync(async (req, res) => {
  const response = await dashboardService.getBestsellerStats();
  res.status(httpStatus.OK).json({
    status: true,
    statusCode: '200',
    statusMsg: 'getBestsellerStats fetched successfully',
    response
  });
});

const orderDashboard = catchAsync(async (req, res) => {
  try {
    const response = await axios.get(DASHBOARD_LAST_HOUR_ORDER, {
      headers: {
        Authorization: req.headers?.authorization || ''
      }
    });
    const { status, data } = response;
    res.status(status).json(data);
  } catch (e) {
    global.logError(e.message);
    res.status(500).json({ error: e.message });
  }
});

const orderSalesChartDashboard = catchAsync(async (req, res) => {
  try {
    const response = await axios.get(DASHBOARD_LAST_24_HOUR_ORDER_SALES_CHART, {
      headers: {
        Authorization: req.headers?.authorization || ''
      }
    });
    const { status, data } = response;
    res.status(status).json(data);
  } catch (e) {
    global.logError(e.message);
    res.status(500).json({ error: e.message });
  }
});

module.exports = {
  orderStats,
  bestsellerStats,
  orderDashboard,
  orderSalesChartDashboard
};
