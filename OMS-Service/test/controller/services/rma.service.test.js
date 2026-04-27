/* eslint-disable no-unused-vars */
/* eslint-disable max-lines */
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
const app = require('../../../src/app');
const {
  getArchivedRmaDetail,
  getArchivedRmaRequests
} = require('../../../src/helpers/archivedRma');
const { RmaRequest } = require('../../../src/models/seqModels/archiveIndex');
const SAMPLE_REQ = require('../constants/order.request.json');
jest.mock('axios');
jest.mock('firebase-admin');
jest.mock('../../../src/models/seqModels/archiveIndex');
const axios = require('axios');
jest.mock('axios');
jest.setTimeout(80000);

describe('services_methods', () => {
  beforeAll(() => {
    global.logError = jest.fn(() => ({}));
    global.baseConfig = {
      configs: { trackingBaseUrl: 'http://test.com' },
      wmsRtoPush: {
        interval: 10,
        batchSize: 10
      }
    };
  });
  beforeEach(() => {});

  describe('services', () => {
    it('getArchivedRmaDetail', async () => {
      RmaRequest.findOne.mockReturnValueOnce({
        dataValues: {
          rma_inc_id: 'R12344',
          status: 'shipped',
          RmaRequestItems: [
            { order_item_id: 12345, dataValues: { order_item_id: 12345 } }
          ]
        }
      });
      RmaRequest.findAll.mockReturnValueOnce([
        {
          item_id: 12345,
          sku: 12345
        }
      ]);
      axios.post.mockResolvedValueOnce({
        data: {
          response: [
            {
              variants: [{ sku: 12345 }],
              media_gallery: [{ value: 'http://test.com' }]
            }
          ]
        }
      });
      const result = await getArchivedRmaDetail({
        requestId: '1232',
        rmaIncrementId: 'R12129'
      });
      expect(result?.archived).toBe(1);
    });
    it('getArchivedRmaRequests', async () => {
      RmaRequest.findAndCountAll.mockReturnValueOnce({
        count: 12,
        rows: [{ dataValues: {} }]
      });
      const result = await getArchivedRmaRequests({
        filters: { customer_name: 'chandan' },
        query: 'R21230,R2332423'
      });
      expect(result?.count).toBe(12);
    });
  });
});
