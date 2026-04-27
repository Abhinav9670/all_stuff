const express = require('express');
const router = express.Router();

const userv2Controller = require('../controllers/admin/user-v2.controller');
const { userAddRequestValidator } = require('../validator/user.validate');
const { authServiceCheck } = require('../helper');

router.post('/user/add', authServiceCheck, (req, res) =>
    userv2Controller.userAddV2({ res, req })
);

router.put('/user/add', authServiceCheck, (req, res) =>
    userv2Controller.userUpdateV2({ res, req })
);

module.exports = router;