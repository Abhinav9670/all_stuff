/* eslint-disable max-lines-per-function */
/* eslint-disable no-undef */
const { getArchivedOrders } = require('../../src/helpers/orderOpsArchive');

jest.mock('../../src/models/seqModels/archiveIndex', () => ({ Order: { findAll: jest.fn() } }));

describe('orderOpsArchive helper', () => {
  beforeAll(() => {
    global.logError = jest.fn();
  });
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should return orders from archive', async () => {
    const { Order } = require('../../src/models/seqModels/archiveIndex');
    Order.findAll.mockResolvedValue([{ id: 1, name: 'order1' }]);
    const result = await getArchivedOrders({ where: { id: 1 } });
    expect(result).toEqual([{ id: 1, name: 'order1' }]);
  });

  it('should handle error and call logError', async () => {
    const { Order } = require('../../src/models/seqModels/archiveIndex');
    Order.findAll.mockRejectedValue(new Error('fail'));
    const result = await getArchivedOrders({ where: { id: 2 } });
    expect(global.logError).toHaveBeenCalled();
    expect(result).toEqual([]);
  });
});
