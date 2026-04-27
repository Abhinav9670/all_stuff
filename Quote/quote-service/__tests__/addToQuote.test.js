const app = require('./app');
const request = require('supertest');
const payload = require('./dummy/mockPayload/requestPayload.json');
const constant = require('./dummy/mockPayload/constant.json');
const response = require('./dummy/mockPayload/externalResponse.json');
const axios = require('axios');

describe('add to quote POST/rest/quote/auth/v5', () => {
  describe('addToQuoteProductsRequests length 0 in req', () => {
    it('should return a 201 status', async () => {
    const reqData = JSON.parse(JSON.stringify(payload.addToQuotePayload));
      reqData.addToQuoteProductsRequests = [];
      const { body, statusCode } = await request(app).post(
        '/rest/quote/auth/v5'
      ).set(constant.commonHeaders).send(reqData);
      expect(statusCode).toBe(200);
      expect(body.statusCode).toBe('201');
      expect(body.status).toBe(false);
    });
  });
  describe('quote not available', () => {
    it('should return a 200 status and add quote in couchbase', async () => {
      axios.post = async (url) => {
        if(url.endsWith('/api/detail')){
             return response.vmGetProduct;
        }
      };
      const { body, statusCode } = await request(app).post(
        '/rest/quote/auth/v5'
      ).set(constant.commonHeaders).send(payload.addToQuotePayload);
      expect(statusCode).toBe(200);
      expect(body.statusCode).toBe('200');
      expect(body.status).toBe(true);
      expect(body.quoteId).not.toBe(null);
    });
  });
  describe('quote available', () => {
    it('should return a 200 status and the quoteId', async () => {
      axios.post = async (url) => {
        if(url.endsWith('/api/detail')){
             return response.vmGetProduct;
        }
      };
      const { body, statusCode } = await request(app).post(
        '/rest/quote/auth/v5'
      ).set(constant.commonHeaders).send(payload.addToQuotePayload);
      expect(statusCode).toBe(200);
      expect(body.status).toBe(true);
      expect(body.quoteId).not.toBe(null);
    });
  });
});
