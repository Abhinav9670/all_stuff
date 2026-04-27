const app = require('./app');
const request = require('supertest');
const constant = require('./dummy/mockPayload/constant.json');
const payload = require('./dummy/mockPayload/requestPayload.json');
const { collection } = require('../config/couchbase.js');

describe('Abandon cart /rest/quote/abandon-cart', () => {
  afterAll(() => {
    jest.resetAllMocks();
  });
  describe('quote available', () => {
    it('should return a 200 status and response', async () => {
      await collection.upsert(`quote_1681477874120535`, constant.quoteForDisable);
      const { body, statusCode } = await request(app).post(
        '/rest/quote/abandon-cart'
      ).set(constant.commonHeaders).send(payload.enablePayload);
      expect(statusCode).toBe(200);
      expect(body.status).toBe(true);
      expect(body.statusCode).toBe(200);
    });
  });
});
