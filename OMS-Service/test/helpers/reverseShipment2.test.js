/* eslint-disable max-lines-per-function */
/* eslint-disable no-undef */

// Mock Redis before any other imports
jest.mock('redis', () => ({
  createClient: jest.fn().mockReturnValue({
    connect: jest.fn().mockResolvedValue(undefined),
    on: jest.fn(),
    get: jest.fn(),
    set: jest.fn(),
    del: jest.fn()
  })
}));

// Mock config before requiring any other modules
jest.mock('../../src/config/config', () => ({
  MONGODB_URL: 'mongodb://localhost:27017/test',
  KAFKA_BROKERS: ['localhost:9092'],
  KAFKA_CLIENT_ID: 'test-client',
  KAFKA_GROUP_ID: 'test-group',
  REDIS_HOST: 'localhost',
  REDIS_PORT: 6379,
  REDIS_PASSWORD: '',
  NODE_ENV: 'test'
}));

// Mock environment variables
process.env.GLOBAL_REDIS_HOST = 'localhost';
process.env.GLOBAL_REDIS_PORT = '6379';
process.env.GLOBAL_REDIS_AUTH = '';
process.env.REGION = 'UAE';
process.env.NODE_ENV = 'test';

// Mock logger
jest.mock('../../src/config/logger', () => ({
  info: jest.fn(),
  error: jest.fn(),
  debug: jest.fn()
}));

// Mock Redis config
jest.mock('../../src/config/redis', () => ({
  createClient: jest.fn().mockReturnValue({
    connect: jest.fn().mockResolvedValue(undefined),
    on: jest.fn(),
    get: jest.fn(),
    set: jest.fn(),
    del: jest.fn()
  })
}));

const moment = require('moment');
const { updateNormalPickup, updateNormalPickupForNoAutoRefund, updateStatus } = require('../../src/helpers/reverseShipment2');
const { updateRmaStatus } = require('../../src/helpers/rmaUpdateOps');
const { updateStatusHistory } = require('../../src/helpers/utilities');
const { updatePreviousComment } = require('../../src/helpers/orderOps');
const { callRefund, callRefundold } = require('../../src/helpers/refund');
const { updateOrderStateStatus, getTotalItemCount } = require('../../src/helpers/order');
const { OrderComment } = require('../../src/models/seqModels/index');
const { publishMessage } = require('../../src/utils/pubsubconfig');

// Mock all dependencies
console.log = jest.fn();
console.error = jest.fn();

jest.mock('../../src/helpers/rmaUpdateOps');
jest.mock('../../src/helpers/utilities');
jest.mock('../../src/helpers/orderOps');
jest.mock('../../src/helpers/refund');
jest.mock('../../src/helpers/order');
jest.mock('../../src/models/seqModels/index', () => {
  const Sequelize = require('sequelize');
  return {
    OrderComment: {
      create: jest.fn()
    },
    sequelize: {
      Sequelize: Sequelize,
      query: jest.fn(),
      transaction: jest.fn(() => ({
        commit: jest.fn().mockResolvedValue(true),
        rollback: jest.fn().mockResolvedValue(true)
      }))
    }
  };
});
jest.mock('../../src/utils/pubsubconfig');
jest.mock('../../src/utils/mongoInit', () => ({
  getMongoConnection: jest.fn().mockResolvedValue({})
}));

const TEST_DATA = {
  BASE_PARAMS: {
    statusTitle: 'Processing',
    rmaStatusId: '1',
    orderId: 'ORD123',
    rma_inc_id: 'RMA123',
    paymentMethod: 'cod',
    rmaPaymentMethod: 'cod',
    rmaItems: [
      {
        item_status: '1',
        request_qty: 1,
        OrderItem: {
          item_id: '1',
          sku: 'SKU123'
        }
      }
    ],
    storeId: 'STORE123',
    orderIncrementId: 'ORD_INC_123',
    timestamp: '2024-03-20',
    rmaId: 'RMA123',
    returnType: 'refund',
    rmaStatusData: [
      { status_code: 'processing', status_id: '1' }
    ]
  },
  RETURNED_ITEMS: [
    {
      sku: 'SKU123',
      quantity: 1
    }
  ]
};

describe('reverse_shipment2', () => {
  beforeAll(() => {
    global.logError = jest.fn(() => ({}));
    global.logInfo = jest.fn(() => ({}));
    global.baseConfig = {
      configs: {
        javaUpdateRefund: false,
        cpPickupCheckEnabled: true,
        omsRefundPubSubEnabled: false
      }
    };
  });

  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('update_normal_pickup', () => {
    it('normal_pickup_success_valid', async () => {
      getTotalItemCount.mockResolvedValueOnce(1);
      updateRmaStatus.mockResolvedValueOnce(true);
      callRefundold.mockResolvedValueOnce({ refundUrl: 'http://refund.url' });

      const result = await updateNormalPickup(TEST_DATA.BASE_PARAMS);

      expect(result.status).toBe(true);
      expect(result.refundUrl).toBe('http://refund.url');
      expect(OrderComment.create).toHaveBeenCalled();
      expect(updateRmaStatus).toHaveBeenCalled();
    });

    it('cancelled_rma_valid', async () => {
      const params = {
        ...TEST_DATA.BASE_PARAMS,
        rmaStatusId: '12'
      };

      const result = await updateNormalPickup(params);

      expect(result.status).toBe(true);
      expect(OrderComment.create).toHaveBeenCalledWith(
        expect.objectContaining({
          comment: expect.stringContaining('Refund not triggerd')
        })
      );
    });

    it('auto_refund_qc_check_valid', async () => {
      getTotalItemCount.mockResolvedValueOnce(1);
      updateRmaStatus.mockResolvedValueOnce(true);
      callRefundold.mockResolvedValueOnce({ refundUrl: 'http://refund.url' });

      const params = {
        ...TEST_DATA.BASE_PARAMS,
        returnedItems: TEST_DATA.RETURNED_ITEMS
      };

      const result = await updateNormalPickup(params);

      expect(result.status).toBe(true);
      expect(result.autoRefundResponse).toBeDefined();
    });

    it('pubsub_enabled_valid', async () => {
      global.baseConfig.configs.omsRefundPubSubEnabled = true;
      process.env.OMS_REFUND_API_REARRANGEMENT_TOPIC = 'test-topic';

      getTotalItemCount.mockResolvedValueOnce(1);
      publishMessage.mockResolvedValueOnce(true);

      const result = await updateNormalPickup(TEST_DATA.BASE_PARAMS);

      expect(result.status).toBe(true);
      expect(publishMessage).toHaveBeenCalledWith(
        'test-topic',
        expect.objectContaining({
          returnIncrementId: TEST_DATA.BASE_PARAMS.rma_inc_id
        })
      );

      global.baseConfig.configs.omsRefundPubSubEnabled = false;
    });
  });

  describe('update_normal_pickup_for_no_auto_refund', () => {
    it('success_valid', async () => {
      updateRmaStatus.mockResolvedValueOnce(true);

      const params = {
        rmaId: TEST_DATA.BASE_PARAMS.rmaId,
        rmaVerificationStatusId: '2',
        rmaItems: TEST_DATA.BASE_PARAMS.rmaItems,
        returnType: TEST_DATA.BASE_PARAMS.returnType,
        rmaStatusData: TEST_DATA.BASE_PARAMS.rmaStatusData,
        timestamp: TEST_DATA.BASE_PARAMS.timestamp,
        orderId: TEST_DATA.BASE_PARAMS.orderId,
        isPaymentAutoRefunded: true
      };

      const result = await updateNormalPickupForNoAutoRefund(params);

      expect(result.status).toBe(true);
      expect(result.msg).toBe('success');
    });

    it('failure_valid', async () => {
      updateRmaStatus.mockRejectedValueOnce(new Error('Update failed'));

      const params = {
        rmaId: TEST_DATA.BASE_PARAMS.rmaId,
        rmaVerificationStatusId: '2',
        rmaItems: TEST_DATA.BASE_PARAMS.rmaItems,
        returnType: TEST_DATA.BASE_PARAMS.returnType,
        rmaStatusData: TEST_DATA.BASE_PARAMS.rmaStatusData,
        timestamp: TEST_DATA.BASE_PARAMS.timestamp,
        orderId: TEST_DATA.BASE_PARAMS.orderId,
        isPaymentAutoRefunded: true
      };

      const result = await updateNormalPickupForNoAutoRefund(params);

      expect(result.status).toBe(false);
    });
  });

  describe('update_status', () => {
    it('full_return_valid', async () => {
      const params = {
        orderId: TEST_DATA.BASE_PARAMS.orderId,
        returnItemCount: 2,
        orderedItemCount: 2
      };

      const result = await updateStatus(params);

      expect(result).toBe('refunded');
      expect(updateOrderStateStatus).toHaveBeenCalled();
      expect(updatePreviousComment).toHaveBeenCalled();
      expect(updateStatusHistory).toHaveBeenCalled();
    });

    it('partial_return_valid', async () => {
      const params = {
        orderId: TEST_DATA.BASE_PARAMS.orderId,
        returnItemCount: 1,
        orderedItemCount: 2
      };

      const result = await updateStatus(params);

      expect(result).toBe('delivered');
    });

    it('java_update_refund_valid', async () => {
      global.baseConfig.configs.javaUpdateRefund = true;

      const params = {
        orderId: TEST_DATA.BASE_PARAMS.orderId,
        returnItemCount: 2,
        orderedItemCount: 2
      };

      const result = await updateStatus(params);

      expect(result).toBe('refunded');
      expect(updateOrderStateStatus).not.toHaveBeenCalled();

      global.baseConfig.configs.javaUpdateRefund = false;
    });
  });
});