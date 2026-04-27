const app = require('./app');
const request = require('supertest');
const constant = require('./dummy/mockPayload/constant.json');
const axios = require('axios');
const payload = require('./dummy/mockPayload/requestPayload.json');
const response = require('./dummy/mockPayload/externalResponse.json');
const { collection } = require('../config/couchbase.js');

describe('change product size POST/rest/quote/auth/v5/changesize', () => {
  describe('Invalid quote id in req', () => {
    it('should return a 202 status', async () => {
    const reqData = JSON.parse(JSON.stringify(payload.changeSizePayload));
      reqData.customerId = '123';
      const { body, statusCode } = await request(app).put(
        '/rest/quote/auth/v5/changesize'
      ).set(constant.commonHeaders).send(reqData);
      expect(statusCode).toBe(200);
      expect(body.statusCode).toBe('202');
      expect(body.status).toBe(false);
    });
  });
  describe('Invalid product sku given', () => {
    it('should return a 500 status and product not found message', async () => {
      const reqData1 = JSON.parse(JSON.stringify(payload.changeSizePayload));
      reqData1.skuToDelete = '700272590106';
      const { body, statusCode } = await request(app).put(
        '/rest/quote/auth/v5/changesize'
      ).set(constant.commonHeaders).send(reqData1);
      expect(statusCode).toBe(200);
      expect(body.statusCode).toBe('202');
      expect(body.status).toBe(false);
    });
  });
  describe('valid Req to change size', () => {
    it('should return a 200 status and the quoteId', async () => {
      await collection.upsert(`quote_1684234744639834`, constant.addQuoteObj);
      axios.post = async (url) => {
        if(url.endsWith('/api/detail')){
          return response.vmResponseGetProduct;
        }
      };
      const { body, statusCode } = await request(app).put(
        '/rest/quote/auth/v5/changesize'
      ).set(constant.commonHeaders).send(payload.changeSizePayload);
      expect(statusCode).toBe(200);
      expect(body.status).toBe(true);
      expect(body.quoteId).not.toBe(null);
    });
  });
});
