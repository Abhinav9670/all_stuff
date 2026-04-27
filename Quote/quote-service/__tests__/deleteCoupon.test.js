const app = require('./app');
const request = require('supertest');
const constant = require('./dummy/mockPayload/constant.json');
const { collection } = require('../config/couchbase.js');


describe('delete coupon from quote DELETE/rest/quote/auth/v5/coupon', () => {
  describe('invalid quoteId in req', () => {
    it('should return a 202 status', async () => {
      const { body, statusCode } = await request(app).delete(
        '/rest/quote/auth/v5/coupon'
      ).set(constant.commonHeaders).send({
        "storeId": 1
      });
      expect(statusCode).toBe(200);
      expect(body.statusCode).toBe('202');
      expect(body.status).toBe(false);
    });
  });

  describe('quote available and coupon code applied', () => {
    it('should return a 200 status', async () => {
      await collection.upsert(`quote_1681477874120535`, constant.quoteForDelete, { expiry: Number(process.env.COUCHBASE_QUOTE_DOC_EXPIRY) });
      const { body, statusCode } = await request(app).delete(
        '/rest/quote/auth/v5/coupon'
      ).set(constant.commonHeaders).send({
        "quoteId": constant.quoteForDelete.id,
        "storeId": 1
      });
      expect(statusCode).toBe(200);
      expect(body.status).toBe(true);
      expect(body.statusCode).toBe('200');
    });
  });
});
