const { Op } = require('sequelize');
const rmaFilters = require('../../src/helpers/rmaFilters');
const sequalizeFilters = require('../../src/helpers/sequalizeFilters');

jest.mock('../../src/helpers/sequalizeFilters', () => ({
  setNotMatch: jest.fn((where, key, val) => ({ ...where, [key]: { $not: val } })),
  setExactMatch: jest.fn((where, key, val) => ({ ...where, [key]: val })),
  setLikeMatch: jest.fn((where, key, val) => ({ ...where, [key]: { $like: val } })),
  setInMatch: jest.fn((where, key, val) => ({ ...where, [key]: { $in: val } })),
  setGTMatch: jest.fn((where, key, val) => ({ ...where, [key]: { $gt: val } })),
  setLTMatch: jest.fn((where, key, val) => ({ ...where, [key]: { $lt: val } })),
  setLTEMatch: jest.fn((where, key, val) => ({ ...where, [key]: { $lte: val } })),
  setGTEMatch: jest.fn((where, key, val) => ({ ...where, [key]: { $gte: val } })),
}));

describe('rmaFilters', () => {
  describe('applyQuery', () => {
    it('should apply query with multiple ids', () => {
      const where = { foo: 'bar' };
      const query = '1,2,3';
      const result = rmaFilters.applyQuery({ where, query });
      expect(result).toHaveProperty('foo', 'bar');
      expect(Object.prototype.hasOwnProperty.call(result, Op.or)).toBe(true);
      expect(Array.isArray(result[Op.or])).toBe(true);
    });

    it('should handle empty query', () => {
      const where = {};
      const query = '';
      const result = rmaFilters.applyQuery({ where, query });
      expect(Object.prototype.hasOwnProperty.call(result, Op.or)).toBe(true);
      expect(Array.isArray(result[Op.or])).toBe(true);
    });
  });

  describe('prepareFilters', () => {
    beforeEach(() => jest.clearAllMocks());

    it('should handle is_created_by_admin = admin', () => {
      rmaFilters.prepareFilters({ is_created_by_admin: 'admin' });
      expect(sequalizeFilters.setNotMatch).toHaveBeenCalled();
    });

    it('should handle is_created_by_admin != admin', () => {
      rmaFilters.prepareFilters({ is_created_by_admin: 'user' });
      expect(sequalizeFilters.setExactMatch).toHaveBeenCalled();
    });

    it('should handle orderEntityId', () => {
      rmaFilters.prepareFilters({ orderEntityId: 123 });
      expect(sequalizeFilters.setExactMatch).toHaveBeenCalled();
    });

    it('should handle customer_name', () => {
      rmaFilters.prepareFilters({ customer_name: 'John' });
      expect(sequalizeFilters.setLikeMatch).toHaveBeenCalled();
    });

    it('should handle store_id with non-empty array', () => {
      rmaFilters.prepareFilters({ store_id: [1, 2] });
      expect(sequalizeFilters.setInMatch).toHaveBeenCalled();
    });

    it('should handle fromDate', () => {
      rmaFilters.prepareFilters({ fromDate: '2024-01-01' });
      expect(sequalizeFilters.setGTMatch).toHaveBeenCalled();
    });

    it('should handle toDate', () => {
      rmaFilters.prepareFilters({ toDate: '2024-01-02' });
      expect(sequalizeFilters.setLTMatch).toHaveBeenCalled();
    });

    it('should handle fromId', () => {
      rmaFilters.prepareFilters({ fromId: 10 });
      expect(sequalizeFilters.setGTEMatch).toHaveBeenCalled();
    });

    it('should handle toId', () => {
      rmaFilters.prepareFilters({ toId: 20 });
      expect(sequalizeFilters.setLTEMatch).toHaveBeenCalled();
    });

    it('should handle status', () => {
      rmaFilters.prepareFilters({ status: 'open' });
      expect(sequalizeFilters.setExactMatch).toHaveBeenCalled();
    });

    it('should handle customer_id', () => {
      rmaFilters.prepareFilters({ customer_id: 5 });
      expect(sequalizeFilters.setExactMatch).toHaveBeenCalled();
    });

    it('should handle rma_inc_id', () => {
      rmaFilters.prepareFilters({ rma_inc_id: 'RMA123' });
      expect(sequalizeFilters.setExactMatch).toHaveBeenCalled();
    });

    it('should handle unknown filter (default case)', () => {
      // Should not throw or call any set*Match
      expect(() => rmaFilters.prepareFilters({ unknown: 'x' })).not.toThrow();
    });

    it('should return undefined for empty filters', () => {
      expect(rmaFilters.prepareFilters({})).toBeUndefined();
    });
  });
});
