const app = require('./app');
const request = require('supertest');
const constant = require('./dummy/mockPayload/constant.json');

describe('check quote is valid or not GET/rest/quote/auth/v5/view/validate', () => {
  // describe('send invalid qoute id ', () => {
  //   it('should return a 202 status ', async () => {
  //     const { body, statusCode } = await request(app).get(
  //       '/rest/quote/auth/v5/view/validate'
  //     ).set(constant.commonHeaders).query({ quoteId: "1684234744639844" });
  //     expect(statusCode).toBe(200);
  //     expect(body.statusCode).toBe('202');
  //     expect(body.status).toBe(false);
  //   });
  // });
  describe('valid qoute id ', () => {
    it('should return a 200 status and the count', async () => {
      const { body, statusCode } = await request(app).get(
        '/rest/quote/auth/v5/view/validate'
      ).set(constant.commonHeaders).query({ quoteId: "1684234744639834" });
      expect(statusCode).toBe(200);
      expect(body.statusCode).toBe('200');
      expect(body.status).toBe(true);
      expect(typeof body.response === 'object').toBe(true);
    });
  });
});
