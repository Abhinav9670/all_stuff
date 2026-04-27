/* eslint-disable max-lines-per-function */
/* eslint-disable no-undef */
const { logBrazeCustomEventForDelivery } = require('../../src/helpers/braze');

jest.mock('../../src/utils/brazeApi', () => ({ logBrazeCustomEvent: jest.fn() }));
jest.mock('moment', () => Object.assign(() => ({ format: () => '2025-06-06 12:00:00' }), { utc: () => ({ format: () => '2025-06-06 12:00:00' }) }));

const { logBrazeCustomEvent } = require('../../src/utils/brazeApi');

describe('braze helper', () => {
  beforeAll(() => {
    global.logInfo = jest.fn();
    global.logError = jest.fn();
  });
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should call logBrazeCustomEvent with correct payload', async () => {
    const orderData = {
      orderData: {
        entity_id: 'E1',
        customer_id: 'C1',
        subSales: { total_shukran_coins_earned: 10, total_shukran_earned_value_in_currency: 5 },
        increment_id: 'INC1'
      }
    };
    await logBrazeCustomEventForDelivery(orderData);
    expect(global.logInfo).toHaveBeenCalled();
    expect(logBrazeCustomEvent).toHaveBeenCalledWith(expect.objectContaining({ events: [expect.any(Object)] }));
  });

  it('should not call logBrazeCustomEvent if entity_id or customer_id missing', async () => {
    await logBrazeCustomEventForDelivery({ orderData: { entity_id: null, customer_id: null } });
    expect(logBrazeCustomEvent).not.toHaveBeenCalled();
  });
});
