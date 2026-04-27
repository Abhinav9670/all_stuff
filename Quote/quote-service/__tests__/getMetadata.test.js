const app = require('./app');
const request = require('supertest');
const constant = require('./dummy/mockPayload/constant.json');

describe('get metadata GET/rest/quote/auth/v5/view/metadata', () => {
  describe('quote available', () => {
    it('should return a 200 status and get metadata', async () => {
      const { body, statusCode } = await request(app).get(
        '/rest/quote/auth/v5/view/metadata'
      ).set(constant.commonHeaders).query({ quoteId: "1684234744639834" });
      expect(statusCode).toBe(200);
      expect(body.statusCode).toBe('200');
      expect(body.status).toBe(true);
      expect(typeof body.response === 'object').toBe(true);
    });
  });
});
