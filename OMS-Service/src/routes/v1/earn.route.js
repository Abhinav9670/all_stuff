const express = require('express');
// const auth = require('../../middlewares/auth');
const earnController = require('../../controllers/earn.controller');
const authValidate = require('../../middlewares/authValidate');

const router = express.Router();
router.route('/list').post(authValidate(), earnController.getLedger);

module.exports = router;
