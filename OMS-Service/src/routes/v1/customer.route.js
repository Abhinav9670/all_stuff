const express = require('express');
// const auth = require('../../middlewares/auth');
const customerController = require('../../controllers/customer.controller');
const authValidate = require('../../middlewares/authValidate');

const router = express.Router();

router.route('/list').post(authValidate(), customerController.customerList);
router
  .route('/deleted/list')
  .post(authValidate(), customerController.deletedCustomersList);
router
  .route('/addressMap/:countryLocale')
  .get(authValidate(4), customerController.addressMap);
router
  .route('/address/update')
  .put(authValidate(), customerController.addressUpdate);

router.route('/update').put(authValidate(), customerController.customerUpdate);
router
  .route('/address/list')
  .post(authValidate(), customerController.addressList);

router.route('/detail').post(authValidate(), customerController.customerDetail);

router.route('/device/delete').post(authValidate(), customerController.deleteCustomer);

router
  .route('/wallet/list')
  .post(authValidate(), customerController.customerWalletList);

router
  .route('/wallet/add')
  .post(authValidate(), customerController.customerWalletUpdate);

module.exports = router;
