const utilities = require('../../src/helpers/utilities');

jest.mock('../../src/models/seqModels/index', () => ({
  StatusHistory: {
    findOne: jest.fn(),
    update: jest.fn(),
  },
  Order: {
    findAll: jest.fn(),
  },
  OrderPayment: {},
}));

jest.mock('../../src/utils/config', () => ({
  getWebsiteStoreMap: jest.fn(),
}));
jest.mock('../../src/helpers/orderOpsArchive', () => ({
  getArchivedOrders: jest.fn(),
}));
jest.mock('../../src/helpers/sequalizeFilters', () => ({
  setInMatch: jest.fn((where, key, val) => ({ ...where, [key]: val })),
  setExactMatch: jest.fn((where, key, val) => ({ ...where, [key]: val })),
}));

global.logInfo = jest.fn();
global.logError = jest.fn();

const { StatusHistory, Order } = require('../../src/models/seqModels/index');
const { getWebsiteStoreMap } = require('../../src/utils/config');
const { getArchivedOrders } = require('../../src/helpers/orderOpsArchive');
const sequalizeFilters = require('../../src/helpers/sequalizeFilters');

describe('utilities.js', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('updateStatusHistory', () => {
    it('should update status history and return true', async () => {
      StatusHistory.findOne.mockResolvedValue({ id: 1 });
      StatusHistory.update.mockResolvedValue([1]);
      const result = await utilities.updateStatusHistory(1, { status: 'updated' });
      expect(StatusHistory.findOne).toHaveBeenCalledWith({ where: { order_id: 1 } });
      expect(StatusHistory.update).toHaveBeenCalledWith({ status: 'updated' }, { where: { id: 1 } });
      expect(result).toBe(true);
    });

    it('should return true if no id found', async () => {
      StatusHistory.findOne.mockResolvedValue(null);
      const result = await utilities.updateStatusHistory(1, { status: 'updated' });
      expect(result).toBe(true);
    });

    it('should handle error and return false', async () => {
      StatusHistory.findOne.mockRejectedValue(new Error('fail'));
      const result = await utilities.updateStatusHistory(1, { status: 'updated' });
      expect(result).toBe(false);
      expect(global.logError).toHaveBeenCalled();
    });
  });

  describe('getLifetimeOrders', () => {
    it('should return error if no customerId and no customerEmail', async () => {
      const result = await utilities.getLifetimeOrders({});
      expect(result).toEqual({ error: 'Required parameters missing!' });
    });

    it('should return orders and itemized status', async () => {
      getWebsiteStoreMap.mockReturnValue({ 1: [1, 2] });
      sequalizeFilters.setInMatch.mockImplementation((where, key, val) => ({ ...where, [key]: val }));
      sequalizeFilters.setExactMatch.mockImplementation((where, key, val) => ({ ...where, [key]: val }));
      Order.findAll.mockResolvedValue([
        { status: 'complete', increment_id: 'OID1', created_at: '2024-01-01', retry_payment: false, OrderPayments: [{ dataValues: { method: 'md_payfort', additional_information: '{"card_number":"1234567890123456"}' } }] },
        { status: 'pending', increment_id: 'OID2', created_at: '2024-01-02', retry_payment: true, OrderPayments: [{ dataValues: { method: 'cod', additional_information: '' } }] },
      ]);
      getArchivedOrders.mockResolvedValue([
        { status: 'complete', increment_id: 'OID3', created_at: '2024-01-03', retry_payment: false, OrderPayments: [{ dataValues: { method: 'md_payfort', additional_information: '{"card_number":"9876543210987654"}' } }] },
      ]);
      const result = await utilities.getLifetimeOrders({ customerId: 1, websiteId: 1 });
      expect(result.response.total).toBe(3);
      expect(result.responseList.length).toBe(3);
      expect(result.response.itemized.complete).toBeGreaterThan(0);
      expect(result.response.itemized.pending).toBeGreaterThan(0);
    });

    it('should handle error and return error message', async () => {
      getWebsiteStoreMap.mockImplementation(() => { throw new Error('fail'); });
      const result = await utilities.getLifetimeOrders({ customerId: 1, websiteId: 1 });
      expect(result).toHaveProperty('error');
    });
  });
});
