const express = require('express');
// const auth = require('../../middlewares/auth');
const dashboardController = require('../../controllers/dashboard.controller');
const authValidate = require('../../middlewares/authValidate');
const router = express.Router();

router.route('/orderStats').post(authValidate(), dashboardController.orderStats);
router
  .route('/bestsellerStats')
  .post(authValidate(), dashboardController.bestsellerStats);

router.route('/orderDashboard').get(authValidate(), dashboardController.orderDashboard);
router
  .route('/orderSalesChartDashboard')
  .get(authValidate(), dashboardController.orderSalesChartDashboard);

module.exports = router;
