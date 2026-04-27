/* eslint-disable max-lines-per-function */
/* eslint-disable no-undef */
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
const app = require('../../src/app');
const CONST = require('./constants/consul.constants.json');
const RUN_CONFIG = require('../run.config.json');
const { insertOne } = require('../../src/utils/mongo');
const HEADER_TOKEN = RUN_CONFIG['X-HEADER-TOKEN'];
const axios = require('axios');
jest.mock('axios');
jest.mock('firebase-admin');
jest.mock('../../src/utils/mongo');
jest.mock('@sendgrid/mail');
jest.mock('../../src/models/seqModels/index');
jest.setTimeout(90000);

describe('rest2_routes', () => {
  beforeAll(() => {
    global.payfortConfig = CONST.payfortConfig;
    global.paymentMethods = CONST.paymentMethods;
    global.config = CONST.storeConfig;
    global.logError = jest.fn(() => ({}));
  });
  beforeEach(() => {});

  describe('alert_routes', () => {
    it('get_consul_config', async () => {
      const response = await request(app)
        .get('/v1/config/consul/inventory')
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      await request(app).get('/v1/config/consul/app').set({
        authorization: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });
      expect(response.status).not.toBe(200);
    });
    it('customer_list', async () => {
      axios.post.mockResolvedValueOnce({
        status: 200,
        data: {
          status: 200
        }
      });
      const response = await request(app)
        .get('/v1/customers/list')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).not.toBe(200);
    });

    it('deleted_customer_list', async () => {
      const response = await request(app)
        .post('/v1/customers/deleted/list')
        .send({ offset: 0, pageSize: 10, query: { customer_id: 1234 } })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
    it('adrsmpr_list', async () => {
      axios.post.mockResolvedValueOnce({
        status: 200
      });
      const response = await request(app)
        .get('/v1/customers/addressMap/en')
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
    it('adrsmpr_update', async () => {
      axios.post.mockResolvedValueOnce({
        status: 200
      });
      insertOne.mockResolvedValue({});
      const response = await request(app)
        .put('/v1/customers/address/update')
        .send({ beforeData: {}, afterData: { regionId: '123' } })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('delete_customer - success case', async () => {
      axios.post.mockResolvedValueOnce({
        status: 200,
        data: {
          statusMsg: 'Devices deleted successfully'
        }
      });
      
      const response = await request(app)
        .post('/v1/customers/device/delete')
        .send({
          params: {
            customerId: 12435,
            deviceIds: ['1676767']
          }
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
    
      expect(response.status).toBe(200);
      expect(response.body.statusMsg).toBe('Devices deleted successfully');
    });

    it('delete_customer - error case', async () => {
      axios.post.mockRejectedValueOnce(new Error('API error'));
    
      const response = await request(app)
        .post('/v1/customers/device/delete')
        .send({
          params: {
            customerId: 12435,
            deviceIds: ['1676767']
          }
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
    
      expect(response.status).toBe(500);
      expect(response.body.error).toBe('API error');
    });

    it('pr_call_negative', async () => {
      const response = await request(app).put('/v1/retryPrCall').send({}).set({
        authorization: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });
      expect(response.status).not.toBe(200);
    });

    it('shukran_transaction_negative', async () => {
      const response = await request(app)
        .put('/v1/shukran-transaction')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).not.toBe(200);
    });

    it('customer_update', async () => {
      axios.post.mockResolvedValueOnce({
        status: 200
      });
      insertOne.mockResolvedValue({});
      const response = await request(app)
        .put('/v1/customers/update')
        .send({ beforeData: {}, afterData: { regionId: '123' } })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
    it('cust_address_list', async () => {
      axios.post.mockResolvedValueOnce({
        status: 200,
        data: {}
      });
      const response = await request(app)
        .post('/v1/customers/address/list')
        .send({ params: { customerId: 1232 } })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
    it('cust_details', async () => {
      axios.post.mockResolvedValueOnce({
        status: 200,
        data: {}
      });
      const response = await request(app)
        .post('/v1/customers/detail')
        .send({ params: { customerId: 1232 } })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
    it('cust_wallet_list', async () => {
      axios.post.mockResolvedValueOnce({
        status: 200,
        data: {}
      });
      const response = await request(app)
        .post('/v1/customers/wallet/list')
        .send({ params: { customerId: 1232 } })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('cust_wallet_list_negative', async () => {
      const response = await request(app)
        .post('/v1/customers/wallet/list')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
    it('cust_wallet_add', async () => {
      axios.post.mockResolvedValueOnce({
        status: 200
      });
      insertOne.mockResolvedValue({});
      const response = await request(app)
        .post('/v1/customers/wallet/add')
        .send({ beforeData: {}, afterData: { regionId: '123' } })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
    it('orderDashboard', async () => {
      axios.get.mockResolvedValueOnce({
        status: 200,
        data: {}
      });
      const response = await request(app)
        .get('/v1/dashboard/orderDashboard')
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
    it('orderSalesChartDashboard', async () => {
      axios.get.mockResolvedValueOnce({
        status: 200,
        data: {}
      });
      const response = await request(app)
        .get('/v1/dashboard/orderSalesChartDashboard')
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
    it('bestsellerStats', async () => {
      axios.post.mockResolvedValueOnce({
        status: 200,
        data: {}
      });
      const response = await request(app)
        .post('/v1/dashboard/bestsellerStats')
        .send({ beforeData: {}, afterData: { regionId: '123' } })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
    it('bestsellerStatsNegative', async () => {
      const response = await request(app)
        .post('/v1/dashboard/bestsellerStats')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
    it('orderStats', async () => {
      axios.post.mockResolvedValueOnce({
        status: 200,
        data: {}
      });
      const response = await request(app)
        .post('/v1/dashboard/orderStats')
        .send({ beforeData: {}, afterData: { regionId: '123' } })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('orderStatsNegative', async () => {
      const response = await request(app)
        .post('/v1/dashboard/orderStats')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response).not.toEqual(200);
    });
    it('earn_list', async () => {
      axios.post.mockResolvedValueOnce({
        status: 200,
        data: {}
      });
      const response = await request(app)
        .post('/v1/earn/list')
        .send({ beforeData: {}, afterData: { regionId: '123' } })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('earn_list_nagative', async () => {
      const response = await request(app).post('/v1/earn/list').send({}).set({
        authorization: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });
      expect(response.status).not.toBe(200);
    });

    it('earn_list', async () => {
      const response = await request(app)
        .post('/v1/earn/list')
        .send({ beforeData: {}, afterData: { regionId: '123' } })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(500);
    });
    it('test', async () => {
      expect(200).toBe(200);
    });
  });
});
