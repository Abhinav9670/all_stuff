const reverseShipment = require('../../src/helpers/reverseShipment');

jest.mock('../../src/helpers/rma', () => ({
  getRmaShipmentDetail: jest.fn(),
  isShortPickUp: jest.fn(() => false),
}));
jest.mock('../../src/helpers/rmaUpdateOps', () => ({
  setShortPickedup: jest.fn(),
  updateRmaStatus: jest.fn(),
}));
jest.mock('../../src/helpers/reverseShipment2', () => ({
  updateNormalPickup: jest.fn(),
  updateNormalPickupForNoAutoRefund: jest.fn(),
}));
jest.mock('../../src/helpers/fraudCustomerRma', () => ({
  isFraudCustomer: jest.fn(() => false),
  setFraudPickedUp: jest.fn(),
}));
jest.mock('../../src/helpers/sms', () => ({
  sendSMS: jest.fn(() => ({})),
}));
jest.mock('../../src/helpers/archivedRma', () => ({
  getCityData: jest.fn(),
}));
jest.mock('../../src/utils', () => ({
  logInfo: jest.fn(),
}));

const rma = require('../../src/helpers/rma');
const rmaUpdateOps = require('../../src/helpers/rmaUpdateOps');
const reverseShipment2 = require('../../src/helpers/reverseShipment2');
const fraudCustomerRma = require('../../src/helpers/fraudCustomerRma');
const smsObj = require('../../src/helpers/sms');
const archivedRma = require('../../src/helpers/archivedRma');
const utils = require('../../src/utils');

describe('reverseShipment.updateRevShipment', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should return error if getRmaShipmentDetail fails', async () => {
    rma.getRmaShipmentDetail.mockResolvedValue({ status: false, msg: 'error' });
    const result = await reverseShipment.updateRevShipment({ rmaIncrementId: 'R1' });
    expect(result).toEqual({ status: false, rmaErrorMsg: 'error' });
  });

  it('should return error if no rmaData', async () => {
    rma.getRmaShipmentDetail.mockResolvedValue({ status: true, data: {} });
    const result = await reverseShipment.updateRevShipment({ rmaIncrementId: 'R2' });
    expect(result).toEqual({ status: false, errorMsg: expect.stringContaining('No Return Request found') });
  });

  it('should return error if rma is cancelled (status_id 12)', async () => {
    rma.getRmaShipmentDetail.mockResolvedValue({ status: true, data: { rmaData: { status_id: 12 } } });
    const result = await reverseShipment.updateRevShipment({ rmaIncrementId: 'R3' });
    expect(result).toEqual({ status: false, errorMsg: 'Return have been cancelled' });
  });

  it('should handle notificationId 2 (normal pickup, success path)', async () => {
    rma.getRmaShipmentDetail.mockResolvedValue({
      status: true,
      data: {
        rmaData: { status_id: 1, status_title: 'title', status_code: 1, request_id: 'RID', return_type: 'type', order_status: 1, order_id: 'OID', rma_inc_id: 'INC', method: 'COD', rma_payment_method: 'COD' },
        rmaStatusData: [{ status_code: 1, status_id: 1 }],
        rmaItems: [{}],
        address: { country: 'UAE', regionId: '1', city: 'DXB' }
      }
    });
    archivedRma.getCityData.mockResolvedValue({ is_payment_auto_refunded: true });
    reverseShipment2.updateNormalPickup.mockResolvedValue({ status: true, smsStatus: 'picked_up', refundUrl: 'url', autoRefundResponse: {} });
    smsObj.sendSMS.mockResolvedValue({});
    utils.logInfo.mockImplementation(() => {});
    const result = await reverseShipment.updateRevShipment({ rmaIncrementId: 'R4', notificationId: 2 });
    expect(result.status).toBe(true);
    expect(smsObj.sendSMS).toHaveBeenCalled();
  });

  it('should handle notificationId 2 (updateNormalPickup returns error)', async () => {
    rma.getRmaShipmentDetail.mockResolvedValue({
      status: true,
      data: {
        rmaData: { status_id: 1, status_title: 'title', status_code: 1, request_id: 'RID', return_type: 'type', order_status: 1, order_id: 'OID', rma_inc_id: 'INC', method: 'COD', rma_payment_method: 'COD' },
        rmaStatusData: [{ status_code: 1, status_id: 1 }],
        rmaItems: [{}],
        address: { country: 'UAE', regionId: '1', city: 'DXB' }
      }
    });
    archivedRma.getCityData.mockResolvedValue({ is_payment_auto_refunded: true });
    reverseShipment2.updateNormalPickup.mockResolvedValue({ status: false, errorMsg: 'fail' });
    const result = await reverseShipment.updateRevShipment({ rmaIncrementId: 'R5', notificationId: 2 });
    expect(result.status).toBe(false);
    expect(result.errorMsg).toBe('fail');
  });

  it('should handle notificationId 2 (isShortPickUp path)', async () => {
    rma.isShortPickUp.mockReturnValue(true);
    rma.getRmaShipmentDetail.mockResolvedValue({
      status: true,
      data: {
        rmaData: { status_id: 1, status_title: 'title', status_code: 1, request_id: 'RID', return_type: 'type', order_status: 1, order_id: 'OID', rma_inc_id: 'INC', method: 'COD', rma_payment_method: 'COD' },
        rmaStatusData: [{ status_code: 1, status_id: 1 }],
        rmaItems: [{}],
        address: { country: 'UAE', regionId: '1', city: 'DXB' }
      }
    });
    rmaUpdateOps.setShortPickedup.mockResolvedValue({ status: true });
    reverseShipment2.updateNormalPickup.mockResolvedValue({ status: true });
    smsObj.sendSMS.mockResolvedValue({});
    const result = await reverseShipment.updateRevShipment({ rmaIncrementId: 'R6', notificationId: 2 });
    expect(result.status).toBe(true);
  });

  it('should handle notificationId 2 (isFraudCustomer path)', async () => {
    rma.isShortPickUp.mockReturnValue(false);
    fraudCustomerRma.isFraudCustomer.mockResolvedValue(true);
    rma.getRmaShipmentDetail.mockResolvedValue({
      status: true,
      data: {
        rmaData: { status_id: 1, status_title: 'title', status_code: 1, request_id: 'RID', return_type: 'type', order_status: 1, order_id: 'OID', rma_inc_id: 'INC', method: 'COD', rma_payment_method: 'COD' },
        rmaStatusData: [{ status_code: 1, status_id: 1 }],
        rmaItems: [{}],
        address: { country: 'UAE', regionId: '1', city: 'DXB' }
      }
    });
    fraudCustomerRma.setFraudPickedUp.mockResolvedValue({ status: true });
    smsObj.sendSMS.mockResolvedValue({});
    const result = await reverseShipment.updateRevShipment({ rmaIncrementId: 'R7', notificationId: 2 });
    expect(result.status).toBe(true);
  });

  it('should handle notificationId 2 (isShortPickUp returns false, setShortPickedup returns error)', async () => {
    rma.isShortPickUp.mockReturnValue(true);
    rma.getRmaShipmentDetail.mockResolvedValue({
      status: true,
      data: {
        rmaData: { status_id: 1, status_title: 'title', status_code: 1, request_id: 'RID', return_type: 'type', order_status: 1, order_id: 'OID', rma_inc_id: 'INC', method: 'COD', rma_payment_method: 'COD' },
        rmaStatusData: [{ status_code: 1, status_id: 1 }],
        rmaItems: [{}],
        address: { country: 'UAE', regionId: '1', city: 'DXB' }
      }
    });
    rmaUpdateOps.setShortPickedup.mockResolvedValue({ status: false, msg: 'short fail' });
    const result = await reverseShipment.updateRevShipment({ rmaIncrementId: 'R8', notificationId: 2 });
    expect(result.status).toBe(false);
    expect(result.errorMsg).toBe('short fail');
  });
});
