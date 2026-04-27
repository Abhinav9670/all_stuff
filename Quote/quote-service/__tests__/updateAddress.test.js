const app = require('./app');
const request = require('supertest');
const constant = require('./dummy/mockPayload/constant.json');
const payload = require('./dummy/mockPayload/requestPayload.json');

describe('update address POST/rest/quote/auth/v5/address', () => {
    describe('invalid req', () => {
        const reqData = JSON.parse(JSON.stringify(payload.addressPayload));
        delete reqData.customerId;
        it('should return a 200 status', async () => {
          const { body, statusCode } = await request(app).post(
            '/rest/quote/auth/v5/address'
          ).set(constant.commonHeaders).send(reqData);
          expect(statusCode).toBe(200);
          expect(body.status).toBe(false);
          expect(body.statusCode).toBe('201');
        });
      });
  describe('update addess with valid req', () => {
    it('should return a 200 status', async () => {
      const { body, statusCode } = await request(app).post(
        '/rest/quote/auth/v5/address'
      ).set(constant.commonHeaders).send(payload.addressPayload);
      expect(statusCode).toBe(200);
      expect(body.status).toBe(true);
      expect(body.statusCode).toBe('200');
    });
  });
});
