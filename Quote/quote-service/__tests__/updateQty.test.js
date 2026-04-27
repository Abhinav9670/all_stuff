const app = require('./app');
const request = require('supertest');
const constant = require('./dummy/mockPayload/constant.json');
const payload = require('./dummy/mockPayload/requestPayload.json');
const axios = require('axios');
const { collection } = require('../config/couchbase.js');
const response = require('./dummy/mockPayload/externalResponse.json');

describe('update quantity /rest/quote/auth/v5', () => {
  afterAll(() => {
    jest.resetAllMocks();
  });
  describe('quote not there', () => {
    it('should return a 202 status', async () => {
    const reqData = JSON.parse(JSON.stringify(payload.updateQtyPayload));
      delete reqData.customerId;
      delete reqData.quoteId;
      const { body, statusCode } = await request(app).put(
        '/rest/quote/auth/v5'
      ).set(constant.commonHeaders).send(reqData);
      expect(statusCode).toBe(200);
      expect(body.statusCode).toBe('202');
    });
  });
  describe('product sku not there in quote', () => {
    it('should return a 500 status', async () => {
    const reqData = JSON.parse(JSON.stringify(payload.updateQtyPayload));
      reqData.customerId = '3910691';
      const { body, statusCode } = await request(app).put(
        '/rest/quote/auth/v5'
      ).set(constant.commonHeaders).send(reqData);
      expect(statusCode).toBe(200);
      expect(body.statusCode).toBe('500');
    });
  });
  describe('quote available', () => {
    it('should return a 200 status and response', async () => {
      await collection.upsert(`quote_1684234744639834`, constant.addQuoteObj);
      const { body, statusCode } = await request(app).put(
        '/rest/quote/auth/v5'
      ).set(constant.commonHeaders).send(payload.updateQtyPayload);
      expect(statusCode).toBe(200);
      expect(body.status).toBe(true);
      expect(body.statusCode).toBe('200');
      expect(body.statusMsg).toBe("Success");
    });
  });
});
