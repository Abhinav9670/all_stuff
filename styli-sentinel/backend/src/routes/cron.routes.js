const express = require('express');
const router = express.Router();

const cronController = require('../controllers/cron/cron.controller');

router.get('/epsilon/generate/token', (req, res) => cronController.generateEpsilonToken({ res, req }));

module.exports = router;
