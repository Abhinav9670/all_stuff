/* eslint-disable no-unused-vars */
/* eslint-disable max-lines-per-function */
/* eslint-disable no-undef */
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
const {
  RmaRequest,
  OrderItem,
  RmaTracking
} = require('../../src/models/seqModels/index');
const CONST = require('./constants/consul.constants.json');
const RUN_CONFIG = require('../run.config.json');
const HEADER_TOKEN = RUN_CONFIG['X-HEADER-TOKEN'];
const axios = require('axios');
jest.mock('axios');
jest.mock('firebase-admin');
jest.mock('../../src/utils/mongo');
jest.mock('../../src/models/seqModels/index');
jest.setTimeout(90000);

describe('rma_additional_tests', () => {
  beforeAll(() => {
    global.payfortConfig = CONST.payfortConfig;
    global.paymentMethods = CONST.paymentMethods;
    global.config = CONST.storeConfig;
    global.logError = jest.fn(() => ({}));
  });

  describe('rma_tracking_operations', () => {
    it('rma_remove_tracking', async () => {
      const response = await request(app)
        .post('/v1/rma/remove/tracking')
        .send({
          tracking_id: '12345'
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('rma_create_tracking', async () => {
      axios.get.mockResolvedValueOnce({
        status: 200,
        data: { statusCode: '200', response: {} }
      });
      const response = await request(app)
        .get('/v1/rma/create/tracking/12345')
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('rma_create_tracking_negative', async () => {
      axios.get.mockRejectedValueOnce(new Error('Failed to create AWB'));
      const response = await request(app)
        .get('/v1/rma/create/tracking/12345')
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(500);
    });
  });

  describe('rma_list_operations', () => {
    it('rma_list_invalid_page_size', async () => {
      const response = await request(app)
        .post('/v1/rma/list')
        .send({
          offset: 1,
          pageSize: 'invalid',
          filters: { store_id: 1 },
          query: '',
          archived: false
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(400);
    });
  });

  describe('rma_init_operations', () => {
    it('rma_init_with_req_obj', async () => {
      axios.post.mockResolvedValueOnce({
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
          orderId: '12345',
          reqObj: {
            orderId: '12345',
            customerId: '67890',
            storeId: '3',
            items: [
              {
                parentOrderItemId: '123',
                returnQuantity: 1,
                reasonId: 17
              }
            ]
          }
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
  });

  describe('rma_recreate_operations', () => {
    beforeEach(() => {
      // Reset baseConfig before each test
      global.baseConfig = undefined;
    });

    it('rma_recreate_with_oms_flag', async () => {
      global.baseConfig = {
        apiOptimization: {
          omsRmaRecreate: true
        }
      };
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
          incrementIds: '121314',
          awbNumbers: 'F234324'
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(203);
    });

    it('rma_recreate_without_oms_flag', async () => {
      global.baseConfig = {
        apiOptimization: {
          omsRmaRecreate: false
        }
      };
      const mockFile = {
        filename: 'test.csv',
        path: '/tmp/test.csv'
      };
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
        .field('incrementIds', '121314')
        .field('awbNumbers', 'F234324')
        .attach('file', Buffer.from('test'), mockFile.filename)
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(203);
    });

    it('rma_recreate_no_available_ids', async () => {
      RmaRequest.findAll.mockResolvedValue([]);
      const response = await request(app)
        .post('/v1/rma/recreate')
        .send({
          incrementIds: '121314',
          awbNumbers: 'F234324'
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(203);
      expect(response.body).toEqual({
        status: false,
        error: 'None of the RMA ids present in system '
      });
    });

    it('rma_recreate_db_error', async () => {
      global.baseConfig = {
        apiOptimization: {
          omsRmaRecreate: true
        }
      };
      RmaRequest.findAll.mockRejectedValueOnce(new Error('Database error'));
      const response = await request(app)
        .post('/v1/rma/recreate')
        .send({
          incrementIds: '121314',
          awbNumbers: 'F234324'
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(500);
    });

    it('rma_recreate_tracking_destroy_error', async () => {
      global.baseConfig = {
        apiOptimization: {
          omsRmaRecreate: true
        }
      };
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
      RmaTracking.destroy.mockRejectedValueOnce(
        new Error('Tracking delete error')
      );
      const response = await request(app)
        .post('/v1/rma/recreate')
        .send({
          incrementIds: '121314',
          awbNumbers: 'F234324'
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(203);
      expect(response.body.status).toBe(false);
      // expect(response.body.statusMsg).toMatch(/Tracking delete error/i);
    });

    it('rma_recreate_file_upload_error', async () => {
      global.baseConfig = {
        apiOptimization: {
          omsRmaRecreate: true
        }
      };
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
      const mockFile = {
        filename: 'test.csv',
        path: '/tmp/test.csv'
      };
      const response = await request(app)
        .post('/v1/rma/recreate')
        .field('incrementIds', '121314')
        .field('awbNumbers', 'F234324')
        .attach('file', Buffer.from('test'), mockFile.filename)
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(203);
    });
  });
});
