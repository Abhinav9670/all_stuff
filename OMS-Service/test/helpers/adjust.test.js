const adjust = require('../../src/helpers/adjust');

jest.mock('../../src/utils', () => ({
  logInfo: jest.fn(),
}));

global.baseConfig = { extrenalApis: { adjust: { delivered_event_token: 'token' } } };
global.logError = jest.fn();

describe('adjust.js', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    global.baseConfig = { extrenalApis: { adjust: { delivered_event_token: 'token' } } };
  });

  it('should call logInfo and logAdjustEvent for valid orderData', async () => {
    const orderData = {
      entity_id: 1,
      customer_id: 2,
      increment_id: 'OID',
      shippingAddress: { country_id: 'AE' },
      source: 'web',
      subSales: { device_id: 'dev123' }
    };
    await adjust.adjustDeliveredEvent({ orderData });
    const { logInfo } = require('../../src/utils');
    expect(logInfo).toHaveBeenCalledWith(
      expect.stringContaining('Adjust Request  Entity for Delivery'),
    );
    expect(logInfo).toHaveBeenCalledWith(
      expect.stringContaining('Adjust Request  Entity for Delivery Ended'),
    );
  });

  it('should not call logAdjustEvent if entity_id or customer_id missing', async () => {
    const orderData = {
      entity_id: null,
      customer_id: null,
      increment_id: 'OID',
      shippingAddress: { country_id: 'AE' },
      source: 'web',
      subSales: { device_id: 'dev123' }
    };
    await adjust.adjustDeliveredEvent({ orderData });
    const { logInfo } = require('../../src/utils');
    expect(logInfo).toHaveBeenCalledWith(
      expect.stringContaining('Adjust Request  Entity for Delivery'),
    );
    // Should not call 'Adjust Request  Entity for Delivery Ended'
    expect(logInfo).not.toHaveBeenCalledWith(
      expect.stringContaining('Adjust Request  Entity for Delivery Ended'),
    );
  });

  it('should handle error in adjustDeliveredEvent', async () => {
    const orderData = undefined;
    await adjust.adjustDeliveredEvent({ orderData });
    expect(global.logError).toHaveBeenCalled();
  });
});
