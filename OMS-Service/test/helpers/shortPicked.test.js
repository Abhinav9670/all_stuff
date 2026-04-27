const shortPicked = require('../../src/helpers/shortPicked');
const rma = require('../../src/helpers/rma');
const rmaUpdateOps = require('../../src/helpers/rmaUpdateOps');
const { RMA_UNDER_VERIFICATION, RMA_ITEM_VERIFICATION_FAILED, RMA_ITEM_VERIFICATION_PASSED } = require('../../src/constants/order');

jest.mock('../../src/helpers/rma', () => ({
  getRmaStatus: jest.fn(),
}));
jest.mock('../../src/helpers/rmaUpdateOps', () => ({
  updateRmaStatus: jest.fn(),
}));

describe('shortPicked', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    global.javaOrderServiceConfig = {
      order_details: {
        QC_PASS_REASON: ['REASON1', 'REASON2'],
        SHORT_PICK_PASS_CHECK: true,
      }
    };
  });

  describe('updateRmaActualQty', () => {
    it('should return error if not valid', async () => {
      const reqItems = [{ itemCode: '1_abc', channelSkuCode: 'sku', qcStatus: 'FAIL', qcReason: 'X' }];
      const rmaItems = [{ item_status: 1, qty: 1, OrderItem: { item_id: 2 } }];
      const result = await shortPicked.updateRmaActualQty({
        reqItems,
        rmaItems,
        rmaIncrementId: 'RMA1',
        requestId: 1,
        isFraudPickedUp: false,
        isPaymentAutoRefunded: true
      });
      expect(result.status).toBe(false);
      expect(result.errorMsg).toMatch(/doesn't match/);
    });

    it('should return error if qty mismatch', async () => {
      // Two reqItems with same itemCode root, so reqItemQty will be 2, existingQty is 1
      const reqItems = [
        { itemCode: '1_abc', channelSkuCode: 'sku', qcStatus: 'PASS', qcReason: 'X' },
        { itemCode: '1_def', channelSkuCode: 'sku', qcStatus: 'PASS', qcReason: 'X' }
      ];
      const rmaItems = [{ item_status: 1, qty: 1, OrderItem: { item_id: 1 } }];
      rma.getRmaStatus.mockResolvedValue([
        { status_code: RMA_ITEM_VERIFICATION_FAILED, status_id: 2 },
        { status_code: RMA_ITEM_VERIFICATION_PASSED, status_id: 1 }
      ]);
      const result = await shortPicked.updateRmaActualQty({
        reqItems,
        rmaItems,
        rmaIncrementId: 'RMA2',
        requestId: 1,
        isFraudPickedUp: false,
        isPaymentAutoRefunded: true
      });
      expect(result.status).toBe(false);
      expect(result.errorMsg).toMatch(/Qty Mismatched Error/);
    });

    it('should return error if updateRmaStatus returns falsy', async () => {
      const reqItems = [{ itemCode: '1_abc', channelSkuCode: 'sku', qcStatus: 'PASS', qcReason: 'X' }];
      const rmaItems = [{ item_status: 1, qty: 1, OrderItem: { item_id: 1 } }];
      rma.getRmaStatus.mockResolvedValue([
        { status_code: 'RMA_ITEM_VERIFICATION_FAILED', status_id: 2 },
        { status_code: 'RMA_ITEM_VERIFICATION_PASSED', status_id: 1 }
      ]);
      rmaUpdateOps.updateRmaStatus.mockResolvedValue(false);
      const result = await shortPicked.updateRmaActualQty({
        reqItems,
        rmaItems,
        rmaIncrementId: 'RMA3',
        requestId: 1,
        isFraudPickedUp: false,
        isPaymentAutoRefunded: true
      });
      expect(result.status).toBe(false);
      expect(result.errorMsg).toMatch(/Error upadating RMA Stataus/);
    });

    it('should return success and correct counts', async () => {
      const reqItems = [{ itemCode: '1_abc', channelSkuCode: 'sku', qcStatus: 'PASS', qcReason: 'X' }];
      const rmaItems = [{ item_status: 1, qty: 1, OrderItem: { item_id: 1 } }];
      rma.getRmaStatus.mockResolvedValue([
        { status_code: 'RMA_ITEM_VERIFICATION_FAILED', status_id: 2 },
        { status_code: 'RMA_ITEM_VERIFICATION_PASSED', status_id: 1 }
      ]);
      rmaUpdateOps.updateRmaStatus.mockResolvedValue(true);
      const result = await shortPicked.updateRmaActualQty({
        reqItems,
        rmaItems,
        rmaIncrementId: 'RMA4',
        requestId: 1,
        isFraudPickedUp: false,
        isPaymentAutoRefunded: true
      });
      expect(result.status).toBe(true);
      expect(result.totalRmaCount).toBeDefined();
      expect(result.totalReturnedQty).toBeDefined();
    });
  });

  describe('rollbackToUnderVerification', () => {
    it('should call updateRmaStatus with correct params', async () => {
      rma.getRmaStatus.mockResolvedValue([
        { status_code: RMA_UNDER_VERIFICATION, status_id: 5 }
      ]);
      const returnData = {
        rmaData: { request_id: 1 },
        rmaItems: [{ item_status: 1, order_item_id: 10 }]
      };
      await shortPicked.rollbackToUnderVerification({ returnData, orderId: 2 });
      expect(rmaUpdateOps.updateRmaStatus).toHaveBeenCalledWith(expect.objectContaining({
        rmaId: 1,
        statusId: 5,
        orderId: 2,
        isShortPickup: true
      }));
    });
  });
});
