const app = require('./app');
const request = require('supertest');
const constant = require('./dummy/mockPayload/constant.json');
const axios = require('axios');
const payload = require('./dummy/mockPayload/requestPayload.json');

describe('credit apply to quote POST/rest/quote/auth/v5/storecredit', () => {
  describe('invalid customer id in req', () => {
    it('should return a 202 status', async () => {
    const reqData = JSON.parse(JSON.stringify(payload.applyCreditPayload));
      reqData.customerId="65346254";
      const { body, statusCode } = await request(app).post(
        '/rest/quote/auth/v5/storecredit'
      ).set(constant.commonHeaders).send(reqData);
      expect(statusCode).toBe(200);
      expect(body.statusCode).toBe('202');
      expect(body.status).toBe(false);
    });
  });
  describe('Req with invalid amount', () => {
    it('should return a 203 status and the count', async () => {
      const reqData = JSON.parse(JSON.stringify(payload.applyCreditPayload));
      reqData.amount= -50;
      const { body, statusCode } = await request(app).post(
        '/rest/quote/auth/v5/storecredit'
      ).set(constant.commonHeaders).send(reqData);
      expect(statusCode).toBe(200);
      expect(body.statusCode).toBe('203');
      expect(body.status).toBe(false);
    });
  });
  describe('valid req', () => {
    it('should return a 200 status ', async () => {
      const data ={data : {
        status: true,
        statusCode: "200",
        statusMsg: "Store credit fetched successfully!",
        response: {
          storeCredit: 100,
          customerId: null,
          storeId: null,
          message: null
        },
        error: null,
        customerId: null
      }};
      axios.post = async () => {
        return data;
      };
    const { body, statusCode } = await request(app).post(
      '/rest/quote/auth/v5/storecredit'
    ).set(constant.commonHeaders).send(payload.applyCreditPayload);
    expect(statusCode).toBe(200);
    expect(body.status).toBe(true);
    expect(body.statusCode).toBe('200');
    });
  });
});
