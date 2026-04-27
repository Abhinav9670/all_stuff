/* eslint-disable max-lines-per-function */
/* eslint-disable no-undef */
const request = require('supertest');
jest.mock('redis', () => {
  const mockRedis = {
    connect: jest.fn().mockResolvedValue(true),
    on: jest.fn(),
    quit: jest.fn(),
    get: jest.fn().mockResolvedValue(null),
    set: jest.fn().mockResolvedValue('OK')
  };
  return {
    createClient: jest.fn(() => mockRedis)
  };
});
console.log = jest.fn();
console.error = jest.fn();
const app = require('../../src/app');
const mongoUtil = require('../../src/utils/mongoInit');
const RUN_CONFIG = require('../run.config.json');
const HEADER_TOKEN = RUN_CONFIG['X-HEADER-TOKEN'];
const axios = require('axios');

jest.mock('axios');
jest.setTimeout(90000);

describe('quote_routes', () => {
  beforeAll(async () => {
    await mongoUtil.connectToServer();
    global.logError = jest.fn(() => ({}));
  });

  it('getQuote', async () => {
    axios.post.mockResolvedValueOnce({
      status: 200,
      data: { success: true }
    });

    const response = await request(app)
      .post('/v1/orders/quote/get')
      .send({ quoteId: '123' })
      .set({
        authorization: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });

    expect(response.status).toBe(200);
  });

  it('setPayment', async () => {
    axios.post.mockResolvedValueOnce({
      status: 200,
      data: { paymentStatus: 'success' }
    });

    const response = await request(app)
      .post('/v1/orders/quote/setPayment')
      .send({ method: 'card' })
      .set({
        authorization: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });

    expect(response.status).toBe(200);
  });

  it('applyCoupon', async () => {
    axios.post.mockResolvedValueOnce({
      status: 200,
      data: { couponApplied: true }
    });

    const response = await request(app)
      .post('/v1/orders/quote/coupon')
      .send({ code: 'SAVE10' })
      .set({
        authorization: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });

    expect(response.status).toBe(200);
  });

  it('removeCoupon', async () => {
    axios.delete.mockResolvedValueOnce({
      status: 200,
      data: { couponRemoved: true }
    });

    const response = await request(app)
      .delete('/v1/orders/quote/coupon')
      .send({ code: 'SAVE10' })
      .set({
        authorization: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });

    expect(response.status).toBe(200);
  });

  it('setQuoteAddress', async () => {
    axios.post.mockResolvedValueOnce({
      status: 200,
      data: { addressSet: true }
    });

    const response = await request(app)
      .post('/v1/orders/quote/setAddress')
      .send({ address: '123 Street' })
      .set({
        authorization: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });

    expect(response.status).toBe(200);
  });

  it('removeProduct', async () => {
    axios.delete.mockResolvedValueOnce({
      status: 200,
      data: { productRemoved: true }
    });

    // Use POST and correct route casing to match Express router
    const response = await request(app)
      .post('/v1/orders/quote/removeproduct')
      .send({ productId: 'prod-123' })
      .set({
        authorization: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });

    expect(response.status).toBe(200);
    expect(response.body).toHaveProperty('productRemoved', true);
  });
});