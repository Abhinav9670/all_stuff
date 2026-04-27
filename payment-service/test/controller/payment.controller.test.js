/* eslint-disable no-undef */
const request = require('supertest');
const dotenv = require('dotenv');
dotenv.config({ path: '.env.test.local' });
require('../../src/index');
const app = require('../../src/config/express');
const CONST = require('./constants/consul.constants.json');
const RUN_CONFIG = require('../run.config.json');
const TEST_DATA = require('./constants/payment.test.data.json');
const axios = require('axios');
jest.mock('axios');
jest.setTimeout(90000);
describe('BNPL_payment', () => {
  beforeAll(() => {
    global.payfortConfig = CONST.payfortConfig;
    global.paymentMethods = CONST.paymentMethods;
    global.config = CONST.storeConfig;
    global.logError = jest.fn(() => ({}));
  });
  beforeEach(() => {
    const data = { message: 'Hello World!' };
    axios.post.mockResolvedValue({ data });
    axios.get.mockResolvedValue({ data });
  });

  it('/v1/status', async () => {
    const respon = await request(app).get('/v1/status');
    expect(respon.status).toBe(200);
  });

  it('payment/config_valid', async () => {
    const response = await request(app)
      .post('/v1/payment/configs')
      .send({
        countryCode: 'sa'
      })
      .set({
        token: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': RUN_CONFIG['X-HEADER-TOKEN']
      });
    expect(response.status).toBe(200);
  });

  it('payment/config_invlid_country', async () => {
    global.logError = jest.fn(() => ({}));
    global.payfortConfig = CONST.payfortConfig;
    const response = await request(app)
      .post('/v1/payment/configs')
      .send({
        countryCode: 'xyz'
      })
      .set({
        token: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': RUN_CONFIG['X-HEADER-TOKEN']
      });
    expect(response.status).toBe(400);
  });

  it('payment/session/clear_valid', async () => {
    const response = await request(app)
      .post('/v1/payment/session/clear')
      .send({
        id: '1654510364128228',
        bnplAmount: '99.53',
        paymentMethod: 'tamara_installments_3'
      })
      .set({
        token: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': RUN_CONFIG['X-HEADER-TOKEN']
      });
    expect(response.status).toBe(200);
  });

  it('payment/options_invalid', async () => {
    const response = await request(app).post('/v1/payment/options').send(TEST_DATA.OPTIONS_INVALID).set({
      token: RUN_CONFIG.JWT_TOKEN,
      'X-Header-Token': RUN_CONFIG['X-HEADER-TOKEN'],
      'authorization-token': 'hfdfe457bdfe83r8bc73yr456c'
    });
    expect(response.status).toBe(206);
  });
  it('options_tabby', async () => {
    const response = await request(app).post('/v1/payment/options').send(TEST_DATA.OPTIONS_VALID_TABBY).set({
      token: RUN_CONFIG.JWT_TOKEN,
      'X-Header-Token': RUN_CONFIG['X-HEADER-TOKEN'],
      'authorization-token': 'hfdfe457bdfe83r8bc73yr456c'
    });
    expect(response.status).toBe(200);
  });
  it('payment/options_valid_tamara', async () => {
    const data = { status: 200 };
    axios.post.mockResolvedValue({ data });
    const response = await request(app).post('/v1/payment/options').send(TEST_DATA.OPTIONS_VALID).set({
      token: RUN_CONFIG.JWT_TOKEN,
      'X-Header-Token': RUN_CONFIG['X-HEADER-TOKEN'],
      'authorization-token': 'hfdfe457bdfe83r8bc73yr456c'
    });
    expect(response.status).toBe(200);
  });
});
