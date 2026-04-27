const archivedRma = require('../../src/helpers/archivedRma');

jest.mock('../../src/models/seqModels/archiveIndex', () => ({
  RmaRequest: {
    findOne: jest.fn(),
    findAndCountAll: jest.fn(),
  },
  RmaRequestItem: jest.fn(),
  RmaTracking: jest.fn(),
  Order: jest.fn(),
  OrderItem: {
    findAll: jest.fn(),
  },
  OrderAddress: jest.fn(),
  OrderComment: {
    findAll: jest.fn(),
  },
}));
jest.mock('axios', () => ({
  post: jest.fn(),
}));
jest.mock('../../src/helpers/rmaFilters', () => ({
  prepareFilters: jest.fn(() => ({})),
  applyQuery: jest.fn(({ where }) => where),
}));
jest.mock('../../src/helpers/moment', () => ({
  getKSATime: jest.fn(date => `ksa-${date}`),
}));
jest.mock('../../src/services/misc.service', () => ({
  getProductsBySKU: jest.fn(() => ({ sku1: { variants: [{ sku: 'sku1' }], media_gallery: [{ value: 'img.jpg' }] } })),
}));
jest.mock('../../src/utils', () => ({
  sanitiseImageUrl: jest.fn(url => `safe-${url}`),
}));

global.baseConfig = { configs: { carrierCodes: ['C1'], trackingBaseUrl: 'track-url' } };
global.logError = jest.fn();

describe('archivedRma', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    global.baseConfig = { configs: { carrierCodes: ['C1'], trackingBaseUrl: 'track-url' } };
  });

  it('should getArchivedRmaDetail', async () => {
    const { RmaRequest, OrderItem } = require('../../src/models/seqModels/archiveIndex');
    RmaRequest.findOne.mockResolvedValue({
      dataValues: {
        status: 1,
        RmaRequestItems: [{ order_item_id: 1, dataValues: { order_item_id: 1 } }],
        created_at: '2025-01-01',
        Order: { entity_id: 1, OrderAddress: [{}] },
      }
    });
    OrderItem.findAll.mockResolvedValue([{ item_id: 1, sku: 'sku1' }]);
    const result = await archivedRma.getArchivedRmaDetail({ requestId: 1 });
    expect(result).toHaveProperty('archived', 1);
    expect(result).toHaveProperty('carrierCodes');
    expect(result).toHaveProperty('trackingBaseUrl');
    expect(result).toHaveProperty('created_at');
    expect(result).toHaveProperty('rmaRequestItems');
  });

  it('should getArchivedRmaRequests', async () => {
    const { RmaRequest } = require('../../src/models/seqModels/archiveIndex');
    RmaRequest.findAndCountAll.mockResolvedValue({
      count: 1,
      rows: [{ dataValues: { created_at: '2025-01-01', Order: { entity_id: 1, increment_id: 'OID' } } }],
    });
    const result = await archivedRma.getArchivedRmaRequests({ offset: 0, limit: 1, filters: {}, query: '' });
    expect(result.count).toBe(1);
    expect(result.hits[0]).toHaveProperty('archived', 1);
    expect(result.hits[0]).toHaveProperty('order_id', 1);
    expect(result.hits[0]).toHaveProperty('order_increment_id', 'OID');
  });

  it('should getArchivedRmaComments', async () => {
    const { OrderComment } = require('../../src/models/seqModels/archiveIndex');
    OrderComment.findAll.mockResolvedValue([{ comment: 'test' }]);
    const result = await archivedRma.getArchivedRmaComments('RMA1', 1);
    expect(result).toEqual([{ comment: 'test' }]);
  });

  it('should handle error in getArchivedRmaComments', async () => {
    const { OrderComment } = require('../../src/models/seqModels/archiveIndex');
    OrderComment.findAll.mockRejectedValue(new Error('fail'));
    const result = await archivedRma.getArchivedRmaComments('RMA1', 1);
    expect(result).toEqual([]);
    expect(global.logError).toHaveBeenCalled();
  });

  it('should getCityData success', async () => {
    const axios = require('axios');
    axios.post.mockResolvedValue({ data: { status: true, response: { city: 'DXB' } } });
    process.env.CITY_DATA_API_URL = 'http://api';
    const result = await archivedRma.getCityData('AE', '1', 'DXB');
    expect(result).toEqual({ city: 'DXB' });
  });

  it('should handle error in getCityData', async () => {
    const axios = require('axios');
    axios.post.mockRejectedValue(new Error('fail'));
    process.env.CITY_DATA_API_URL = 'http://api';
    const result = await archivedRma.getCityData('AE', '1', 'DXB');
    expect(result).toBeUndefined();
    expect(global.logError).toHaveBeenCalled();
  });
});
