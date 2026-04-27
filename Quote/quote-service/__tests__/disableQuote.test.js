const app = require('./app');
const request = require('supertest');
const constant = require('./dummy/mockPayload/constant.json');
const payload = require('./dummy/mockPayload/requestPayload.json');
const axios = require('axios');
const { collection } = require('../config/couchbase.js');
const response = require('./dummy/mockPayload/externalResponse.json');

describe('disable quote /rest/quote/auth/v5/disable', () => {
  afterAll(() => {
    jest.resetAllMocks();
  });
  describe('quote not there', () => {
    it('should return a 202 status', async () => {
    const reqData = JSON.parse(JSON.stringify(payload.disablePayload));
      delete reqData.customerId;
      delete reqData.quoteId;
      const { body, statusCode } = await request(app).post(
        '/rest/quote/auth/v5/disable'
      ).set(constant.commonHeaders).send(reqData);
      expect(statusCode).toBe(200);
      expect(body.statusCode).toBe('202');
    });
  });
  describe('quote available', () => {
    it('should return a 200 status and response', async () => {
      await collection.upsert(`quote_1681477874120535`, constant.quoteForDisable);
      const { body, statusCode } = await request(app).post(
        '/rest/quote/auth/v5/disable'
      ).set(constant.commonHeaders).send(payload.disablePayload);
      expect(statusCode).toBe(200);
      expect(body.status).toBe(true);
      expect(body.statusCode).toBe('200');
    });
  });
});
