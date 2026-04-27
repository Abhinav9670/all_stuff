/* eslint-disable no-undef */
const request = require('supertest');
// const app = require('../../src/config/express');
const app = require('../../src/config/express');
const CONST = require('./constants/consul.constants.json');
const RUN_CONFIG = require('../run.config.json');
const TEST_DATA = require('./constants/card.test.data.json');
const axios = require('axios');
// const { createOrder } = require('../../src/api/services/order.service');
jest.mock('../../src/api/services/order.service');
jest.mock('axios');

jest.setTimeout(10000);
describe('card_api', () => {
  beforeAll(() => {
    global.payfortConfig = CONST.payfortConfig;
    global.paymentMethods = CONST.paymentMethods;
    global.config = CONST.storeConfig;
    global.logError = jest.fn(() => ({}));
  });
  beforeEach(() => {});

  // it('payment/payfort/card_valid', async () => {
  //   createOrder.mockResolvedValue({ incrementId: '1239828723', orderId: 1223213 });
  //   const response = await request(app).post('/payment/payfort/card').send(TEST_DATA.CARD_VALID).set({
  //     token: RUN_CONFIG.JWT_TOKEN,
  //     'X-Header-Token': RUN_CONFIG['X-HEADER-TOKEN']
  //   });
  //   expect(response.status).toBe(500);
  // });

  describe('apple_pay', () => {
    it('payment/payfort/apple_valid', async () => {
      const response = await request(app).post('/payment/payfort/apple').send(TEST_DATA.CARD_VALID).set({
        token: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': RUN_CONFIG['X-HEADER-TOKEN']
      });
      expect(response.status).toBe(500);
    });
  });

  describe('card_return', () => {
    it('payment/payfort/card/return_valid', async () => {
      const response = await request(app).post('/payment/payfort/card/return').send(TEST_DATA.CARD_VALID).set({
        token: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': RUN_CONFIG['X-HEADER-TOKEN']
      });
      expect(response.status).toBe(302);
    });
  });
  describe('applepayValidate', () => {
    it('applepayValidate_valid', async () => {
      const data = { message: 'Hello World!' };
      axios.post.mockResolvedValueOnce({ data });
      const response = await request(app).post('/payment/payfort/applepayValidate').send(TEST_DATA.CARD_VALID).set({
        token: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': RUN_CONFIG['X-HEADER-TOKEN']
      });
      expect(response.status).toBe(200);
    });
  });
});
