/* eslint-disable max-lines-per-function */
/* eslint-disable no-undef */
const { updateFraudulent } = require('../../src/helpers/fraudulentUpdate');

jest.mock('axios', () => ({ post: jest.fn() }));
jest.mock('../../src/utils', () => ({ logInfo: jest.fn() }));

describe('fraudulentUpdate helper', () => {
  beforeAll(() => {
    global.baseConfig = { extrenalApis: { fraudulentOrderUpdate: 'http://fraud/api' } };
    global.logError = jest.fn();
  });
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should return status true for successful API call', async () => {
    require('axios').post.mockResolvedValue({ data: { status: true, statusCode: 200, statusMsg: 'ok' } });
    const result = await updateFraudulent({ customerId: 'C1', email: 'e@x.com' });
    expect(result).toEqual({ status: true });
  });

  it('should return errorMsg for failed API status', async () => {
    require('axios').post.mockResolvedValue({ data: { status: false, statusCode: 400, statusMsg: 'fail' } });
    const result = await updateFraudulent({ customerId: 'C2', email: 'e2@x.com' });
    expect(result.status).toBe(false);
    expect(result.errorMsg).toContain('response from Farudulent API');
  });

  it('should handle axios error and call logError', async () => {
    require('axios').post.mockRejectedValue(new Error('fail'));
    const result = await updateFraudulent({ customerId: 'C3', email: 'e3@x.com' });
    expect(global.logError).toHaveBeenCalled();
    expect(result.status).toBe(false);
    expect(result.errorMsg).toContain('Error from Farudulent API');
  });
});
