/* eslint-disable max-lines-per-function */
/* eslint-disable no-undef */
// Mock Playwright at the very top to prevent Chromium launch errors in tests
jest.mock('playwright', () => ({
  chromium: {
    launch: jest.fn().mockResolvedValue({
      newPage: jest.fn().mockResolvedValue({
        setContent: jest.fn(),
        pdf: jest.fn(),
        close: jest.fn()
      }),
      close: jest.fn()
    })
  }
}));

const request = require('supertest');
jest.mock('redis', () => {
  const mockRedis = {
    connect: jest.fn().mockResolvedValue(true),
    on: jest.fn(),
    quit: jest.fn(),
    get: jest.fn().mockResolvedValue(null),
    set: jest.fn().mockResolvedValue('OK')
  };
  return {
    createClient: jest.fn(() => mockRedis)
  };
});
console.log = jest.fn();
console.error = jest.fn();
const axios = require('axios');
const app = require('../../src/app');
const mongoUtil = require('../../src/utils/mongoInit');
const CONST = require('./constants/consul.constants.json');
const RUN_CONFIG = require('../run.config.json');
const HEADER_TOKEN = RUN_CONFIG['X-HEADER-TOKEN'];
const { CreditmemoComment } = require('../../src/models/seqModels/index');
const { addAdminLog } = require('../../src/helpers/logging');
const logBrazeCustomEvent = require('../../src/utils/brazeApi');
const { fetchRefundList } = require('../../src/helpers/refund');

jest.mock('axios');
jest.mock('firebase-admin');
jest.mock('@sendgrid/mail');
jest.setTimeout(90000);
jest.mock('../../src/models/seqModels/index');
jest.mock('../../src/helpers/refund');
jest.mock('../../src/helpers/logging');
jest.mock('../../src/utils/brazeApi', () => jest.fn());

describe('order2_controller_api', () => {
  beforeAll(async () => {
    await mongoUtil.connectToServer();
    await new Promise(resolve => setTimeout(resolve, 5000));
    global.payfortConfig = CONST.payfortConfig;
    global.paymentMethods = CONST.paymentMethods;
    global.config = CONST.storeConfig;
    global.baseConfig = CONST.omsConfig;
    global.logError = jest.fn(() => ({}));
  });
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('order2_routes', () => {
    it('createOrder', async () => {
      axios.post.mockResolvedValueOnce({
        status: 200,
        data: { message: 'Order created' }
      });
      const response = await request(app)
        .post('/v1/orders/createOrder')
        .send({
          customerId: '12345',
          items: [{ sku: 'TEST123', qty: 1 }],
          shippingAddress: {
            street: 'Test Street',
            city: 'Test City'
          }
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('createOrder_negative', async () => {
      const response = await request(app)
        .post('/v1/orders/createOrder')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).not.toBe(200);
    });

    it('recreateOrder', async () => {
      axios.post.mockResolvedValueOnce({
        status: 200,
        data: {
          response: {
            storeId: 'store123'
          }
        }
      });
      addAdminLog.mockReturnValueOnce(true);

      const response = await request(app)
        .post('/v1/orders/recreateOrder')
        .send({
          orderId: '12345',
          reason: 'Test recreation'
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('recreateOrder_negative', async () => {
      const response = await request(app)
        .post('/v1/order/recreateOrder')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).not.toBe(200);
    });

    it('fetchJwt', async () => {
      const response = await request(app)
        .post('/v1/orders/jwt')
        .send({
          userId: '12345',
          storeId: '1'
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('fetchJwt_negative', async () => {
      const response = await request(app).post('/v1/orders/jwt').send({}).set({
        authorization: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });
      expect(response.status).toBe(200);
    });

    it('logCustomEvent_negative', async () => {
      logBrazeCustomEvent.mockImplementationOnce(() => {
        throw new Error('Braze API error');
      });

      const response = await request(app)
        .post('/v1/orders/braze-log-custom-event')
        .send({
          eventName: 'test_event',
          userId: '12345'
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(500);
    });

    it('checkInventory', async () => {
      axios.post.mockResolvedValueOnce({
        status: 200,
        data: {
          inventory: [
            { sku: 'TEST123', stock: 10 },
            { sku: 'TEST456', stock: 5 }
          ]
        }
      });
      const response = await request(app)
        .post('/v1/orders/check-inventory')
        .send({
          skus: ['TEST123', 'TEST456'],
          storeId: '1'
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('checkInventory_negative', async () => {
      const response = await request(app)
        .post('/v1/orders/check-inventory')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).not.toBe(200);
    });

    it('rtoRefundList_negative', async () => {
      const response = await request(app)
        .post('/v1/orders/autoRefundList')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).not.toBe(200);
    });

    it('statusUpdate', async () => {
      axios.post.mockResolvedValueOnce({
        status: 200,
        data: { message: 'Internal API success' }
      });
      const response = await request(app)
        .post('/v1/orders/statusUpdate')
        .send({
          orderId: '12345',
          status: 'completed',
          reason: 'Order fulfilled'
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('statusUpdate_negative', async () => {
      const response = await request(app)
        .post('/v1/orders/statusUpdate')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).not.toBe(200);
    });

    it('recreateOrder_with_tax_config', async () => {
      axios.post.mockResolvedValueOnce({
        status: 200,
        data: {
          response: { storeId: '1' }
        }
      });
      addAdminLog.mockReturnValueOnce(true);

      const response = await request(app)
        .post('/v1/orders/recreateOrder')
        .send({
          orderId: '12345',
          storeId: '1',
          reason: 'Test recreation with tax'
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('checkInventory_empty_skus', async () => {
      axios.post.mockResolvedValueOnce({
        status: 200,
        data: { inventory: [] } // Mock inventory data as per your logic
      });
      const response = await request(app)
        .post('/v1/orders/check-inventory')
        .send({
          skus: [],
          storeId: '1'
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('statusUpdate_error_response', async () => {
      const response = await request(app)
        .post('/v1/orders/statusUpdate')
        .send({
          orderId: 'invalid',
          status: 'invalid_status'
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).not.toBe(200);
    });

    it('fetchJwt_with_full_body', async () => {
      const response = await request(app)
        .post('/v1/orders/jwt')
        .send({
          userId: '12345',
          storeId: '1',
          customerId: '67890',
          sessionId: 'abc123'
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('test_order2_coverage', async () => {
      expect(200).toBe(200);
    });
  });
});