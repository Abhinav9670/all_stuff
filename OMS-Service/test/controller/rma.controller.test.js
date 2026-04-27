/* eslint-disable no-unused-vars */
/* eslint-disable max-lines-per-function */
/* eslint-disable no-undef */

// Mock dotenv-expand to prevent recursion
jest.mock('dotenv-expand', () => jest.fn((env) => env));

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

// Mock index.js to prevent server from starting
jest.mock('../../src/index', () => ({
  // Export nothing to prevent server startup
}));

const app = require('../../src/app');

const { insertOne } = require('../../src/utils/mongo');
const { RmaRequest, OrderItem } = require('../../src/models/seqModels/index');
const CONST = require('./constants/consul.constants.json');
const RUN_CONFIG = require('../run.config.json');
const HEADER_TOKEN = RUN_CONFIG['X-HEADER-TOKEN'];
const axios = require('axios');
jest.mock('axios');
jest.mock('firebase-admin');
jest.mock('../../src/utils/mongo');
jest.mock('../../src/models/seqModels/index');
jest.setTimeout(90000);

describe('rma_api', () => {
  beforeAll(() => {
    global.payfortConfig = CONST.payfortConfig;
    global.paymentMethods = CONST.paymentMethods;
    global.config = CONST.storeConfig;
    global.logError = jest.fn(() => ({}));
  });
  beforeEach(() => { });

  describe('rest_routes', () => {
    it('rma_comments', async () => {
      const response = await request(app)
        .post('/v1/rma/comments')
        .send({
          rmaIncrementId: 'R00001',
          archived: false,
          orderId: '300001232'
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
    it('rma_comments_negative', async () => {
      const response = await request(app)
        .post('/v1/rma/comments')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
    it('rma_rma_update', async () => {
      const response = await request(app)
        .post('/v1/rma/update/rma-status-details')
        .send({
          rmaIncrementId: 'R00001',
          archived: false,
          orderId: '300001232'
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('rma_rma_update_negative', async () => {
      const response = await request(app)
        .post('/v1/rma/update/rma-status-details')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('cancel_reason_list', async () => {
      const response = await request(app)
        .get('/v1/rma/cancel-reason-list')

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('rma_status_detail', async () => {
      const response = await request(app).get('/v1/rma/rma-status-details/1');

      expect(response.status).toBe(200);
    });

    it('rma_status_detail_negative', async () => {
      const response = await request(app).get('/v1/rma/rma-status-details');

      expect(response.status).not.toBe(200);
    });

    it('create_tracking', async () => {
      const response = await request(app)
        .post('/v1/rma/create/tracking/1')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).not.toBe(200);
    });
    it('rma_status_list', async () => {
      const response = await request(app)
        .get('/v1/rma/rma-status-list')

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('rma_rma_update_status', async () => {
      const response = await request(app)
        .get('/v1/rma/rma-status-details/12')
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
    it('rma_list', async () => {
      RmaRequest.findAndCountAll.mockResolvedValue({ count: 10, rows: [] });
      const response = await request(app)
        .post('/v1/rma/list')
        .send({
          offset: 1,
          pageSize: 10,
          filters: { store_id: 1 },
          query: '',
          archived: false
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
    it('rma_detail', async () => {
      RmaRequest.findOne.mockResolvedValue({
        dataValues: { RmaRequestItems: [] }
      });
      OrderItem.findAll.mockResolvedValue([]);
      const response = await request(app)
        .post('/v1/rma/detail')
        .send({
          requestId: '636357',
          archived: false
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
    it('rma_update', async () => {
      axios.post.mockResolvedValueOnce({ status: 200, data: {} });
      const response = await request(app)
        .post('/v1/rma/update')
        .send({
          requestId: '636357',
          archived: false
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('rma_update_negative', async () => {
      const response = await request(app)
        .post('/v1/rma/update')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).not.toBe(200);
    });
    it('rma_init', async () => {
      axios.post
        .mockResolvedValueOnce({
          status: 200,
          data: {
            statusCode: '200',
            response: { products: [{ qty: 3, qtyReturned: 1, qtyCanceled: 1 }] }
          }
        })
        .mockResolvedValueOnce({
          status: 200,
          data: {
            statusCode: '200',
            statusMsg: 'success',
            response: { dropOff: false, storeId: 3 }
          }
        });
      const response = await request(app)
        .post('/v1/rma/init')
        .send({
          requestId: '636357',
          archived: false
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
    it('rma_init_negative', async () => {
      const response = await request(app)
        .post('/v1/rma/init')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).not.toBe(200);
    });
    it('rma_create', async () => {
      axios.post.mockResolvedValueOnce({
        status: 200,
        data: { statusCode: 200, response: {} }
      });
      insertOne.mockResolvedValue({});
      const response = await request(app)
        .post('/v1/rma/create')
        .send({
          incrementIds: '636357',
          awbNumbers: ''
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('rma_create_negative', async () => {
      const response = await request(app)
        .post('/v1/rma/create')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).not.toBe(200);
    });
    it('rma_recreate', async () => {
      RmaRequest.findAll
        .mockResolvedValueOnce([
          {
            dataValues: [{ status_id: 1, title: 'picked_up' }]
          }
        ])
        .mockResolvedValueOnce([
          {
            dataValues: {
              RmaTrackings: [{ dataValues: { tracking_number: '1231313' } }],
              status: 'picked_up',
              rma_inc_id: 1234
            }
          }
        ]);
      const response = await request(app)
        .post('/v1/rma/recreate')
        .send({
          incrementIds: '121314,112313',
          awbNumbers: 'F234324,R43423'
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(203);
    });

    it('rma_recreate_negative', async () => {
      const response = await request(app)
        .post('/v1/rma/recreate')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).not.toBe(203);
    });
    it('test', async () => {
      expect(200).toBe(200);
    });
    it('return-reason-list', async () => {
      const response = await request(app)
        .get('/v1/rma/return-reason-list')
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
  });
});