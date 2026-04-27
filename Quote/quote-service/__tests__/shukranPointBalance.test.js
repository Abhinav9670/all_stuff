const app = require('./app');
const request = require('supertest');
const constant = require('./dummy/mockPayload/constant.json');
const payload = require("./dummy/mockPayload/requestPayload.json");

describe('getShukranPointBalance', () => {
  it('should have a pointAmount property', async () => {    
    const { statusCode, body } = await request(app)
    .post("/rest/quote/auth/v6/getShukranPointBalance")
    .set(constant.commonHeaders)
    .send(payload.shukranPointBalancePayload);

    expect(statusCode).toBe(200);

    expect(body.status).toBe(true);
    expect(body.statusCode).toBe('200');

    expect(body).toHaveProperty('response');
  });
});
