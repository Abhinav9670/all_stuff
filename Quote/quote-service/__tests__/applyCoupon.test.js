const app = require('./app');
const request = require('supertest');
const constant = require('./dummy/mockPayload/constant.json');
const axios = require('axios');
const { collection } = require('../config/couchbase.js');
const payload = require('./dummy/mockPayload/requestPayload.json');
const response = require('./dummy/mockPayload/externalResponse.json');

describe('coupon apply to quote POST/rest/quote/auth/v5/coupon', () => {
  beforeAll(async () => {
  await collection.upsert(`quote_1684234744639835`, constant.quoteWithoutCoupon);
  });
  describe('invalid quoteId in req', () => {
    it('should return a 202 status', async () => {
    const reqData = JSON.parse(JSON.stringify(payload.applyCouponPayload));
      delete reqData.quoteId;
      const { body, statusCode } = await request(app).post(
        '/rest/quote/auth/v5/coupon'
      ).set(constant.commonHeaders).send(reqData);
      expect(statusCode).toBe(200);
      expect(body.statusCode).toBe('202');
      expect(body.status).toBe(false);
    });
  });
  describe('Coupon code not provided', () => {
    it('should return a 203 status and the count', async () => {
      const reqData = JSON.parse(JSON.stringify(payload.applyCouponPayload));
      delete reqData.coupon;
      const { body, statusCode } = await request(app).post(
        '/rest/quote/auth/v5/coupon'
      ).set(constant.commonHeaders).send(reqData);
      expect(statusCode).toBe(200);
      expect(body.statusCode).toBe('203');
      expect(body.status).toBe(false);
    });
  });
  describe('quote available and coupon code provided', () => {
    it('should return a 200 status and the apply coupon to the quote', async () => {
      axios.post = async () => {
        return response.promoResponseCouponApplied;
      };
      const { body, statusCode } = await request(app).post(
        '/rest/quote/auth/v5/coupon'
      ).set(constant.commonHeaders).send(payload.applyCouponPayload);
      expect(statusCode).toBe(200);
      expect(body.status).toBe(true);
      expect(body.statusCode).toBe('200');
    });
  });
});
