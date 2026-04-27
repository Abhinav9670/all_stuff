const app = require('./app');
const request = require('supertest');
const constant = require('./dummy/mockPayload/constant.json');
const payload = require('./dummy/mockPayload/requestPayload.json');

describe('get count POST/rest/quote/auth/v5/view/count', () => {
  describe('no quote and no cutomer available in req', () => {
    it('should return a 202 status', async () => {
    const reqData = JSON.parse(JSON.stringify(payload.getCountPayload));
      reqData.customerId = '';
      const { body, statusCode } = await request(app).post(
        '/rest/quote/auth/v5/view/count'
      ).set(constant.commonHeaders).send(reqData);
      expect(statusCode).toBe(200);
      expect(body.statusCode).toBe('202');
    });
  });
  describe('quote available', () => {
    it('should return a 200 status and the count', async () => {
      const reqData = JSON.parse(JSON.stringify(payload.getCountPayload));
      reqData.quoteId = "1684234744639834";
      const { body, statusCode } = await request(app).post(
        '/rest/quote/auth/v5/view/count'
      ).set(constant.commonHeaders).send(reqData);
      expect(statusCode).toBe(200);
      expect(body.statusCode).toBe('200');
      expect(typeof body.response === 'object').toBe(true);
    });
  });
  describe('quote available but in req no quote id given', () => {
    it('should return a 200 status and the count', async () => {
      const { body, statusCode } = await request(app).post(
        '/rest/quote/auth/v5/view/count'
      ).set(constant.commonHeaders).send(payload.getCountPayload);
      expect(statusCode).toBe(200);
      expect(body.statusCode).toBe('200');
      expect(typeof body.response === 'object').toBe(true);
    });
  });
});
