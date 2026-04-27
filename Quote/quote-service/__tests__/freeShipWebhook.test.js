const app = require('./app');
const request = require('supertest');
const constant = require('./dummy/mockPayload/constant.json');
const payload = require('./dummy/mockPayload/requestPayload.json');

describe('add freeshipping condition POST/rest/quote/webhook/free-shipping', () => {
      it('should return a 500 status code', async () => {
        const reqData = JSON.parse(JSON.stringify(payload.freeshipPayload));
          reqData.minOrderValue = -20;
        const { body, statusCode } = await request(app).post(
          '/rest/quote/webhook/free-shipping'
        ).set({authorization: process.env.AUTH_EXTERNAL_HEADER_BEARER_TOKEN}).send(reqData);
        expect(statusCode).toBe(200);
        expect(body.statusCode).toBe(500);
      });
      it('should return a 500 status code', async () => {
        const reqData = JSON.parse(JSON.stringify(payload.freeshipPayload));
          reqData.country = "sa_en";
        const { body, statusCode } = await request(app).post(
          '/rest/quote/webhook/free-shipping'
        ).set({authorization: process.env.AUTH_EXTERNAL_HEADER_BEARER_TOKEN}).send(reqData);
        expect(statusCode).toBe(200);
        expect(body.status).toBe(false);
        expect(body.statusCode).toBe(500);
      });
      it('should return a 200 status ', async () => {
        const { body, statusCode } = await request(app).post(
          '/rest/quote/webhook/free-shipping'
        ).set({authorization: process.env.AUTH_EXTERNAL_HEADER_BEARER_TOKEN}).send(payload.freeshipPayload);
        expect(statusCode).toBe(200);
        expect(body.status).toBe(true);
        expect(body.statusCode).toBe(200);
      });
  });
  