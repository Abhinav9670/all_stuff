const express = require('express');
const adminRoutes = require('./admin.routes');
const baseRoutes = require('./base.routes');
const authRoutes = require('./auth.routes');
const cronRoutes = require('./cron.routes');
const adminRoutesV2 = require('./admin-v2.routes');

const router = express.Router();

router.get('/', (req, res) => {
    res.status(200).send('OK SENTINEL');
});
router.use('/api/v1/admin', adminRoutes);
router.use('/api/v1/auth', authRoutes);
router.use('/api/v1', baseRoutes);
router.use('/api/v2/admin', adminRoutesV2);
router.use('/api/v1/cron', cronRoutes);

module.exports = router;
