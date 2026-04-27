const express = require('express');
// const auth = require('../../middlewares/auth');
const orderController = require('../../controllers/order.controller');
const orderWriteController = require('../../controllers/orderWrite.controller');
const order2Controller = require('../../controllers/order2.controller');
const quoteController = require('../../controllers/quote.controller');
const pdfController = require('../../controllers/pdf.controller');
const bulkInvoiceController = require('../../controllers/bulk.invoice.controller');
const internalRestAuth = require('../../middlewares/restInternalAuth');
const authValidate = require('../../middlewares/authValidate');
const mobileValidate = require('../../middlewares/mobileValidate');
const router = express.Router();
router
  .route('/list/lifetime')
  .post(internalRestAuth, orderController.lifetimeOrders);
router.route('/list').post(authValidate(), orderController.orders);
router.route('/detail').post(authValidate(), orderController.order);
router.route('/invoice').post(authValidate(), orderController.invoice);
router.route('/shipment').post(authValidate(), orderController.shipment);
router.route('/creditmemo').post(authValidate(), orderController.creditmemo);
router
  .route('/creditmemo/update')
  .post(authValidate(), order2Controller.creditmemoUpdate);
// router.route('/creditmemo').post(orderController.creditmemo);
router.route('/quote/get').post(authValidate(), quoteController.getQuote);
router
  .route('/quote/setPayment')
  .post(authValidate(), quoteController.setPayment);

router.route('/quote/coupon').post(authValidate(), quoteController.applyCoupon);

router
  .route('/quote/coupon')
  .delete(authValidate(), quoteController.removeCoupon);

router
  .route('/quote/removeproduct')
  .post(authValidate(), quoteController.removeProduct);

router
  .route('/quote/setAddress')
  .post(authValidate(), quoteController.setQuoteAddress);

router.route('/createOrder').post(authValidate(), order2Controller.createOrder);

router
  .route('/recreateOrder')
  .post(authValidate(), order2Controller.recreateOrder);

router.route('/address').put(authValidate(), orderWriteController.address);
router.route('/status').post(authValidate(), orderWriteController.status);
router
  .route('/create-shipment')
  .put(authValidate(), orderWriteController.createShipment);

router
  .route('/create-seller-shipment')
  .post(orderWriteController.createSellerShipment);

router
  .route('/create-awb')
  .post(authValidate(), orderWriteController.createAwb);

router
  .route('/create-seller-awb')
  .post(authValidate(), orderWriteController.createSellerAwb);

router
  .route('/cancel-seller-order')
  .post(authValidate(), orderWriteController.cancelSellerOrder);

  router.route('/generatePDF').post(authValidate(),pdfController.generatePDF);
  router.route('/generatePDF/:orderId').get(mobileValidate(),pdfController.generatePDF);
router
  .route('/generateCreditMemoPDF/:entityId')
  .get(pdfController.generateCreditMemoPDF);
router
  .route('/generateCreditMemoPDF')
  .post(authValidate(),pdfController.generateCreditMemoPDF);

router
  .route('/generateSecondReturnInvoice/:rmaIncId')
  .get(pdfController.generateSecondReturnInvoice);

router
  .route('/emailCreditMemoPDF/:entityId')
  .get(pdfController.emailCreditMemoPDF);

router
  .route('/order-status-list')
  .get(authValidate(), orderController.orderStatusList);
router
  .route('/order-status-details/:statusCode')
  .get(authValidate(4), orderController.orderStatusDetails);
router
  .route('/update/order-status-details/')
  .post(authValidate(), orderWriteController.updateOrderStatusDetails);

router.route('/jwt').post(authValidate(), order2Controller.fetchJwt);
router
  .route('/braze-log-custom-event')
  .post(authValidate(), order2Controller.logCustomEvent);
router
  .route('/check-inventory')
  .post(authValidate(), order2Controller.checkInventory);

router
  .route('/autoRefundList')
  .post(authValidate(), order2Controller.rtoRefundList);

router
  .route('/statusUpdate')
  .post(authValidate(), order2Controller.statusUpdate);

router
  .route('/generate/invoice')
  .post(authValidate(), bulkInvoiceController.generateInvoice);

router
  .route('/generate/invoice/list')
  .post(authValidate(), bulkInvoiceController.findAllGeneratedInvoice);

router
  .route('/seller/details')
  .post(orderController.sellerOrderDetails);

module.exports = router;
