const express = require('express');
const router = express.Router();
const appleController = require('../../controllers/applePay.controller');
const cardController = require('../../controllers/card.controller');
const { validateJwt } = require('../../middlewares/jwt');

router.post('/applepayValidate', async (req, res) => {
  const { body } = req;
  const response = await appleController.applePayValidate(body);
  res.send(response);
});

router.post('/card', validateJwt, (req, res) => {
  cardController.cardPayment(req, res);
});

router.post('/apple', validateJwt, (req, res) => {
  appleController.applePay(req, res);
});

router.all('/card/return', async (req, res) => {
  cardController.cardReturn(req, res);
});

router.post('/capturePayment', async (req, res) => {
  cardController.capturePayment(req, res);
});

module.exports = router;
