/* eslint-disable max-lines-per-function */
/* eslint-disable no-undef */
const { eventForDelivery, updateCleverTapProfile } = require('../../src/helpers/cleverTap');

jest.mock('axios', () => ({ post: jest.fn() }));
jest.mock('moment', () => () => ({ unix: () => 1234567890, format: () => '2024-06-06 12:00:00' }));
jest.mock('lodash', () => ({ isEmpty: jest.fn(obj => !obj || Object.keys(obj).length === 0) }));
jest.mock('../../src/utils', () => ({ logInfo: jest.fn() }));
jest.mock('../../src/helpers/orderOps', () => ({ getFirstOrder: jest.fn() }));

const { logInfo } = require('../../src/utils');
const axios = require('axios');
const { getFirstOrder } = require('../../src/helpers/orderOps');

describe('cleverTap helper', () => {
  beforeAll(() => {
    global.baseConfig = { extrenalApis: { cleverTap: { eventPushUrl: 'url', accountId: 'id', passcode: 'pw' } } };
    global.logError = jest.fn();
  });
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should send event for delivery', async () => {
    axios.post.mockResolvedValue({ data: 'ok' });
    const orderData = {
      entity_id: 'E1',
      customer_id: 'C1',
      increment_id: 'I1',
      customer_email: 'test@x.com',
      shippingAddress: { country_id: 'SA' },
      source: 'web',
      subSales: { device_id: 'dev1' }
    };
    await eventForDelivery({ orderData });
    expect(axios.post).toHaveBeenCalled();
  });

  it('should not send event if entity_id or customer_id missing', async () => {
    const orderData = { entity_id: null, customer_id: null };
    await eventForDelivery({ orderData });
    expect(axios.post).not.toHaveBeenCalled();
  });

  it('should handle errors in eventForDelivery', async () => {
    axios.post.mockImplementationOnce(() => { throw new Error('fail'); });
    const orderData = {
      entity_id: 'E2',
      customer_id: 'C2',
      increment_id: 'I2',
      customer_email: 'fail@x.com',
      shippingAddress: { country_id: 'SA' },
      source: 'web',
      subSales: { device_id: 'dev2' }
    };
    await eventForDelivery({ orderData });
    expect(global.logError).toHaveBeenCalled();
  });

  it('should update cleverTap profile with data', async () => {
    getFirstOrder.mockResolvedValue({ createdAt: '2024-01-01' });
    axios.post.mockResolvedValue({ data: 'ok' });
    const orderData = {
      customer_id: 'C3',
      customer_email: 'profile@x.com',
      created_at: '2024-06-06',
      store_id: 1
    };
    await updateCleverTapProfile(orderData);
    expect(axios.post).toHaveBeenCalled();
  });

  it('should not update cleverTap profile if profile is empty', async () => {
    const { isEmpty } = require('lodash');
    isEmpty.mockReturnValue(true);
    await updateCleverTapProfile({});
    expect(axios.post).not.toHaveBeenCalled();
  });
});
