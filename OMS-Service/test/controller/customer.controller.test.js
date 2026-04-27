/* eslint-disable max-lines-per-function */
/* eslint-disable no-undef */
const request = require('supertest');
const moment = require('moment-timezone');
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
const RUN_CONFIG = require('../run.config.json');
const axios = require('axios');
const { AmastyStoreCredit, AmastyStoreCreditHistory } = require('../../src/models/seqModels/index');

jest.mock('axios');
jest.mock('../../src/models/seqModels/index');
jest.mock('../../src/helpers/logging');
jest.mock('../../src/helpers/customer');
jest.mock('../../src/shukran/action');

const HEADER_TOKEN = RUN_CONFIG['X-HEADER-TOKEN'];

describe('customer_controller', () => {
  beforeAll(() => {
    global.logError = jest.fn(() => ({}));
    global.logInfo = jest.fn(() => ({}));
    global.baseConfig = {
      apiOptimization: {
        omsWalletListEnable: true
      }
    };
    global.config = {
      environments: [{
        stores: [{
          storeId: 1,
          storeCode: 'test',
          storeCurrency: 'AED',
          currencyConversionRate: 1
        }]
      }]
    };
  });

  describe('customer_list', () => {
    it('should return customer list successfully', async () => {
      axios.post.mockResolvedValueOnce({ status: 200, data: { customers: [] } });
      const response = await request(app)
        .post('/v1/customers/list')
        .send({ params: {} })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('should handle error in customer list', async () => {
      axios.post.mockRejectedValueOnce(new Error('Failed to fetch'));
      const response = await request(app)
        .post('/v1/customers/list')
        .send({ params: {} })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(500);
    });
  });

  describe('customer_detail', () => {
    it('should return customer details successfully', async () => {
      axios.post.mockResolvedValueOnce({
        status: 200,
        data: {
          response: {
            customer: {
              shukranLinkFlag: false
            }
          }
        }
      });
      const response = await request(app)
        .post('/v1/customers/detail')
        .send({ params: { customerId: '123' } })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('should handle error in customer detail', async () => {
      axios.post.mockRejectedValueOnce(new Error('Failed to fetch'));
      const response = await request(app)
        .post('/v1/customers/detail')
        .send({ params: { customerId: '123' } })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(500);
    });
  });

  describe('address_list', () => {
    it('should return address list successfully', async () => {
      axios.post.mockResolvedValueOnce({ status: 200, data: { addresses: [] } });
      const response = await request(app)
        .post('/v1/customers/address/list')
        .send({ params: { customerId: '123' } })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
  });

  describe('address_update', () => {
    it('should update address successfully', async () => {
      axios.put.mockResolvedValueOnce({ status: 200, data: { success: true } });
      const response = await request(app)
        .put('/v1/customers/address/update')
        .send({
          beforeData: { id: '1' },
          afterData: { id: '1', regionId: 123 },
          email: 'test@test.com'
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
  });

  describe('customer_update', () => {
    it('should update customer successfully', async () => {
      axios.put.mockResolvedValueOnce({ status: 200, data: { success: true } });
      const response = await request(app)
        .put('/v1/customers/update')
        .send({
          beforeData: { id: '1' },
          afterData: { id: '1' },
          email: 'test@test.com'
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
  });

  describe('customer_wallet', () => {
    it('should return wallet list with OMS optimization enabled', async () => {
      AmastyStoreCredit.findOne.mockResolvedValueOnce({
        returnable_amount: 100,
        store_credit: 200
      });
      AmastyStoreCreditHistory.findAll.mockResolvedValueOnce([{
        action_data: '["123"]',
        created_at: '2024-01-01',
        difference: 100,
        store_credit_balance: 200,
        is_deduct: 0,
        action: 1,
        message: 'test'
      }]);

      const response = await request(app)
        .post('/v1/customers/wallet/list')
        .send({ customerId: '123', storeId: 1 })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('should handle wallet update successfully', async () => {
      axios.post.mockResolvedValueOnce({ status: 200, data: { success: true } });
      const response = await request(app)
        .post('/v1/customers/wallet/add')
        .send({
          customerId: '123',
          amount: 100
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
  });

  describe('deleted_customers', () => {
    it('should return deleted customers list', async () => {
      const response = await request(app)
        .post('/v1/customers/deleted/list')
        .send({
          offset: 0,
          pageSize: 10,
          query: ''
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('should handle invalid page size', async () => {
      const response = await request(app)
        .post('/v1/customers/deleted/list')
        .send({
          offset: 0,
          pageSize: 'invalid',
          query: ''
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(400);
    });
  });

  describe('delete_customer', () => {
    it('should delete customer device successfully', async () => {
      axios.post.mockResolvedValueOnce({
        status: 200,
        data: { statusMsg: 'Success' }
      });
      const response = await request(app)
        .post('/v1/customers/device/delete')
        .send({
          params: {
            customerId: '123',
            deviceIds: ['device1']
          }
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('should handle error in customer device deletion', async () => {
      axios.post.mockRejectedValueOnce(new Error('Failed to delete'));
      const response = await request(app)
        .post('/v1/customers/device/delete')
        .send({
          params: {
            customerId: '123',
            deviceIds: ['device1']
          }
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(500);
    });
  });
});