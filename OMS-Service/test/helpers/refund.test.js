/* eslint-disable max-lines-per-function */
/* eslint-disable no-undef */
const refundHelper = require('../../src/helpers/refund');

jest.mock('axios', () => ({ post: jest.fn() }));
jest.mock('../../src/utils', () => ({ logInfo: jest.fn() }));
jest.mock('../../src/kafka/producer/queuebased.dropoff', () => ({ queueBasedDropOff: jest.fn() }));
jest.mock('../../src/models/seqModels/index', () => ({ SubSalesOrder: { findOne: jest.fn(), update: jest.fn() } }));
jest.mock('../../src/utils/easApi', () => ({ updateShukranLedger: jest.fn() }));

describe('refund helper', () => {
  beforeAll(() => {
    process.env.AUTH_INTERNAL_HEADER_BEARER_TOKEN = 'token';
    global.baseConfig = { extrenalApis: { fraudulentOrderUpdate: 'http://fraud/api' } };
    global.logError = jest.fn();
  });
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should call queueBasedDropOff in callRefund', async () => {
    const { queueBasedDropOff } = require('../../src/kafka/producer/queuebased.dropoff');
    await refundHelper.callRefund({ returnIncrementId: 'R1', orderId: 'O1' });
    expect(queueBasedDropOff).toHaveBeenCalledWith('R1', 'O1');
  });

  it('should handle callRefundold success', async () => {
    require('axios').post.mockResolvedValue({ data: { status: true, statusCode: 200, statusMsg: 'ok', sendSms: true, refundUrl: 'url' } });
    const result = await refundHelper.callRefundold({ returnIncrementId: 'R2', orderId: 'O2' });
    expect(result).toEqual({ status: true, sendSms: true, refundUrl: 'url' });
  });

  it('should handle callRefundold error response', async () => {
    require('axios').post.mockResolvedValue({ data: { status: false, statusCode: 400, statusMsg: 'fail' } });
    const result = await refundHelper.callRefundold({ returnIncrementId: 'R3', orderId: 'O3' });
    expect(result.status).toBe(false);
    expect(result.errorMsg).toContain('response from java API');
  });

  it('should handle callRefundold axios error', async () => {
    require('axios').post.mockRejectedValue(new Error('fail'));
    const result = await refundHelper.callRefundold({ returnIncrementId: 'R4', orderId: 'O4' });
    expect(global.logError).toHaveBeenCalled();
    expect(result.status).toBe(false);
    expect(result.errorMsg).toContain('Error from java API');
  });

  it('should handle tabbyRefund success', async () => {
    require('axios').post.mockResolvedValue({ data: { status: true, statusCode: 200, statusMsg: 'ok' } });
    const result = await refundHelper.tabbyRefund({ orderId: 'O5' });
    expect(result).toEqual({ status: true });
  });

  it('should handle tabbyRefund error response', async () => {
    require('axios').post.mockResolvedValue({ data: { status: false, statusCode: 400, statusMsg: 'fail' } });
    const result = await refundHelper.tabbyRefund({ orderId: 'O6' });
    expect(result.status).toBe(false);
    expect(result.errorMsg).toContain('response from tabby java API');
  });

  it('should handle tabbyRefund axios error', async () => {
    require('axios').post.mockRejectedValue(new Error('fail'));
    const result = await refundHelper.tabbyRefund({ orderId: 'O7' });
    expect(global.logError).toHaveBeenCalled();
    expect(result.status).toBe(false);
    expect(result.errorMsg).toContain('Error from tabby java API');
  });

  it('should call axios.post in createCodRtoCreditMemo', async () => {
    require('axios').post.mockResolvedValue({ data: 'ok' });
    const result = await refundHelper.createCodRtoCreditMemo('E1');
    expect(result.data).toBe('ok');
  });

  it('should handle callShukranLockAndUnlock with shukran coins and locked', async () => {
    const { SubSalesOrder } = require('../../src/models/seqModels/index');
    SubSalesOrder.findOne.mockResolvedValue({
      id: 1,
      total_shukran_coins_burned: 10,
      customer_profile_id: 'P1',
      quote_id: 'Q1',
      shukran_locked: 0
    });
    require('axios').post.mockResolvedValue({ status: true });
    await refundHelper.callShukranLockAndUnlock('O8', 'I8');
    expect(SubSalesOrder.update).toHaveBeenCalledWith({ shukran_locked: 1 }, { where: { id: 1 } });
  });

  it('should handle callShukranLockAndUnlock with no subSalesOrder', async () => {
    const { SubSalesOrder } = require('../../src/models/seqModels/index');
    SubSalesOrder.findOne.mockResolvedValue(null);
    await expect(refundHelper.callShukranLockAndUnlock('O9', 'I9')).resolves.toBeUndefined();
  });

  it('should handle callShukranLockAndUnlock error', async () => {
    const { SubSalesOrder } = require('../../src/models/seqModels/index');
    SubSalesOrder.findOne.mockRejectedValue(new Error('fail'));
    await refundHelper.callShukranLockAndUnlock('O10', 'I10');
    expect(global.logError).toHaveBeenCalled();
  });
});
