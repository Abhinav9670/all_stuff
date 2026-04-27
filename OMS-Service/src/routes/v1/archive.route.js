const express = require('express');
const router = express.Router();
const archiveController = require('../../controllers/archive.controller');
const internalRestAuth = require('../../middlewares/restInternalAuth');


router.route('/archive-orders').post(internalRestAuth,archiveController.archiveOrders);
router.route('/archive-setup').post(internalRestAuth,archiveController.archiveSetup);

module.exports = router;
