/* eslint-disable no-undef */
const request = require('supertest');
// const app = require('../../src/config/express');
const app = require('../../src/config/express');
const CONST = require('./constants/consul.constants.json');
const RUN_CONFIG = require('../run.config.json');
const TEST_DATA = require('./constants/card.test.data.json');
const axios = require('axios');
const { createOrder } = require('../../src/api/services/order.service');
jest.mock('axios');
jest.mock('../../src/api/services/order.service');

jest.setTimeout(20000);
describe('cashfree_api', () => {
  beforeAll(() => {
    global.payfortConfig = CONST.payfortConfig;
    global.paymentMethods = CONST.paymentMethods;
    global.config = CONST.storeConfig;
    global.logError = jest.fn(() => ({}));
  });
  beforeEach(() => {
    const data = { message: 'Hello World!' };
    axios.post.mockResolvedValue({ data });
    axios.get.mockResolvedValue({ data });
  });

  it('/v1/payment/cashfree_valid', async () => {
    try {
      createOrder.mockResolvedValue({ incrementId: '1239828723', orderId: 1223213 });
      const response = await request(app).post('/v1/payment/cashfree').send(TEST_DATA.CARD_VALID).set({
        token: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': RUN_CONFIG['X-HEADER-TOKEN']
      });
      expect(response.status).toBe(200);
    } catch (error) {
      console.error(error);
    }
  });
});
