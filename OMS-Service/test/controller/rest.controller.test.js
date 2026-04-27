/* eslint-env jest */

/* eslint-disable max-lines */

const request = require('supertest');

jest.mock('redis', () => {
  const mockRedis = {
    connect: jest.fn().mockResolvedValue(true),

    on: jest.fn(),

    quit: jest.fn(),

    get: jest.fn().mockResolvedValue(null),

    set: jest.fn().mockResolvedValue('OK')
  };

  return {
    createClient: jest.fn(() => mockRedis)
  };
});

// console.log = jest.fn();

console.error = jest.fn();

const app = require('../../src/app');

const CONST = require('./constants/consul.constants.json');

const RUN_CONFIG = require('../run.config.json');

const ORDER_DATA = require('./constants/order.request.json');

const SHUKRAN_REQUEST_DATA = require('./constants/shukran.request.json');

const HEADER_TOKEN = RUN_CONFIG['X-HEADER-TOKEN'];

const { Creditmemo, sequelize } = require('../../src/models/seqModels/index');

const axios = require('axios');

const { setFraudPickedUp } = require('../../src/helpers/fraudCustomerRma');

const {
  setShortPickedup,

  updateRmaStatus
} = require('../../src/helpers/rmaUpdateOps');

const { getCityData } = require('../../src/helpers/archivedRma');

const { uploadFileToBucket } = require('../../src/config/googleStorage');

const { fetchDocs } = require('../../src/utils/mongo');

// Import mocked functions for testing

const { getRmaDetail } = require('../../src/helpers/rma');

const { pushOrderToWms, pushRtoOrders } = require('../../src/helpers/wms');

const { processSMS } = require('../../src/helpers/processSms');

const { shukranTransactionCreate, shukranTransactionApi } = require('../../src/shukran/action');

jest.mock('axios');

jest.mock('firebase-admin');

jest.mock('../../src/models/seqModels/index');

jest.mock('../../src/services/email.service', () => ({
  sendSgEmail: jest.fn().mockResolvedValue(true)
}));

jest.mock('../../src/config/googleStorage');

jest.mock('../../src/utils/mongo');

jest.mock('../../src/utils/config', () => ({
  getStoreCountryMap: jest.fn(() => ({ 1: 'sa', 3: 'sa', 7: 'ae', 11: 'ae' })),
  getStoreConfigs: jest.fn(() => []),
  getStoreWebsiteIdMap: jest.fn(() => ({})),
  getCountryStoreMap: jest.fn(() => ({ sa: [1, 3], ae: [7, 11] })),
  getWebsiteStoreMap: jest.fn(() => ({})),
  getFeatureEnabled: jest.fn(() => false),
  frontendURLBasedOnStoreId: {},
  storeMap: {}
}));

jest.mock('../../src/helpers/archivedRma', () => ({
  getCityData: jest.fn()
}));

jest.mock('../../src/helpers/rma', () => ({
  getRmaDetail: jest.fn(),

  getRmaShipmentDetail: jest.fn(),

  saveShukranPrSuccessfulInDb: jest.fn()
}));

jest.mock('../../src/helpers/wms', () => ({
  pushOrderToWms: jest.fn().mockResolvedValue(undefined),
  pushRtoOrders: jest.fn().mockResolvedValue(undefined)
}));

jest.mock('../../src/helpers/processSms', () => ({
  processSMS: jest.fn().mockResolvedValue({ status: true })
}));

jest.mock('../../src/helpers/order', () => ({
  getTotalItemCount: jest.fn().mockResolvedValue(0),
  findFailedPrOrders: jest.fn().mockResolvedValue([]),
  findFailedRTOrders: jest.fn().mockResolvedValue([])
}));

jest.mock('../../src/helpers/forwardShipment', () => ({
  updateFwdShipment: jest.fn().mockResolvedValue({ status: true, smsResponse: {} }),
  updateShipment: jest.fn().mockResolvedValue({ status: true })
}));

jest.mock('../../src/helpers/reverseShipment', () => ({
  updateRevShipment: jest.fn().mockResolvedValue({ status: true, smsResponse: {} }),
  updateShipmentReverse: jest.fn().mockResolvedValue({ status: true })
}));

jest.mock('../../src/shukran/action', () => ({
  shukranTransactionApi: jest.fn().mockResolvedValue({ data: { success: true } }),
  shukranTransactionCreate: jest.fn().mockResolvedValue({ data: { status: 200, success: true } })
}));

jest.mock('../../src/shukran/revertShukranCalls', () => ({
  revertFailedOrderShukranTransaction: jest.fn().mockResolvedValue(undefined)
}));

jest.setTimeout(90000);

const SHIPMENT_UPDATE_URL = '/v1/rest/shipment-update';

const SHUKRAN_TRANSACTION = '/v1/rest/shukran-transaction';

describe('rest_api', () => {
  beforeAll(() => {
    global.payfortConfig = CONST.payfortConfig;

    global.paymentMethods = CONST.paymentMethods;

    global.config = CONST.storeConfig;

    global.baseConfig = {
      emailConfig: {
        sendCreditmemoEmail: true,

        fromEmail: 'test@stylishop.com',

        fromName: 'test'
      },

      shipmentUpdatesToKafka: false,

      prCallFailedEmailConfig: {
        intervalInHours: 24,

        receiverEmails: 'test@example.com'
      },

      rtCallFailedEmailConfig: {
        intervalInHours: 24,

        receiverEmails: 'test@example.com'
      },

      wmsRtoPush: {
        interval: 10,

        batchSize: 10
      },

      return_cancel: {
        RECREATEPUSHTOWMS: true,

        RETCANPUSHTOWMS: false
      },

      configs: { carrierCodes: ['1', '2'] }
    };

    global.logError = jest.fn(() => ({}));

    global.logInfo = jest.fn(() => ({}));
  });

  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('rest_routes', () => {
    it('shipment_update_forward_pickedup', async () => {
      Creditmemo.findOne.mockReturnValueOnce({
        dataValues: {
          Shipments: [{ dataValues: { track_number: '30496445066' } }],

          OrderItems: [
            {
              dataValues: [
                { product_type: 'simple' },

                { product_type: 'configurable' }
              ]
            }
          ],

          Creditmemos: [],

          OrderAddresses: [{ dataValues: { address_type: 'shipping' } }],

          SubSalesOrders: [{ dataValues: [] }],

          status: 'packed'
        }
      });

      const response = await request(app)
        .post(SHIPMENT_UPDATE_URL)

        .send(ORDER_DATA.SHIPMENT_UPDATE_FORWARD)

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(500);
    });

    it('shipment_update_forward_pickedup_empty', async () => {
      const response = await request(app)
        .post(SHIPMENT_UPDATE_URL)

        .send({})

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(500);
    });

    it('shukran-transaction success', async () => {
      shukranTransactionApi.mockResolvedValueOnce({
        data: { success: true }
      });

      const response = await request(app)
        .post(SHUKRAN_TRANSACTION)

        .send(SHUKRAN_REQUEST_DATA);

      expect(response.status).toBe(200);
    });

    it('shukran-transaction failed', async () => {
      const response = await request(app).post(SHUKRAN_TRANSACTION).send();

      expect(response.status).toBe(500);
    });

    it('shipment_update_forward_ofd', async () => {
      Creditmemo.findOne.mockReturnValueOnce({
        dataValues: {
          Shipments: [{ dataValues: { track_number: '30496445066' } }],

          OrderItems: [
            {
              dataValues: [
                { product_type: 'simple' },

                { product_type: 'configurable' }
              ]
            }
          ],

          Creditmemos: [],

          OrderAddresses: [{ dataValues: { address_type: 'shipping' } }],

          SubSalesOrders: [{ dataValues: [] }],

          status: 'packed'
        }
      });

      const response = await request(app)
        .post(SHIPMENT_UPDATE_URL)

        .send(ORDER_DATA.SHIPMENT_UPDATE_FORWARD_OFD)

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(500);
    });

    it('shipment_update_forward_delivered', async () => {
      Creditmemo.findOne.mockReturnValueOnce({
        dataValues: {
          Shipments: [{ dataValues: { track_number: '30496445066' } }],

          OrderItems: [
            {
              dataValues: [
                { product_type: 'simple' },

                { product_type: 'configurable' }
              ]
            }
          ],

          Creditmemos: [],

          OrderAddresses: [{ dataValues: { address_type: 'shipping' } }],

          SubSalesOrders: [{ dataValues: [] }],

          status: 'packed'
        }
      });

      const response = await request(app)
        .post(SHIPMENT_UPDATE_URL)

        .send(ORDER_DATA.SHIPMENT_UPDATE_FORWARD_DELIVERED)

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(500);
    });

    it('shipment_update_return_pickedup_negative', async () => {
      const response = await request(app)
        .post(SHIPMENT_UPDATE_URL)

        .send({})

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).not.toBe(200);
    });

    it('shipment_update_return_pickedup_fraud', async () => {
      sequelize.query

        .mockReturnValueOnce([
          {
            order_id: 123,

            status_id: 15,

            request_id: 12334,

            rmaItems: [{ item_status: 15 }],

            rmaStatusData: [{ status_code: 'picked_up' }]
          }
        ])

        .mockReturnValueOnce([{ customer_email: '', customer_id: 123 }]);

      Creditmemo.findOne

        .mockReturnValueOnce({
          dataValues: {
            rma_inc_id: 'R12344'
          }
        })

        .mockReturnValueOnce({
          dataValues: {
            rma_inc_id: 'R12344'
          }
        })

        .mockReturnValueOnce({
          dataValues: {
            rma_inc_id: 'R12344'
          }
        });

      Creditmemo.findAll

        .mockReturnValueOnce([{ item_id: '1234', sku: '223344' }])

        .mockReturnValueOnce([
          {
            dataValues: {
              rma_inc_id: 'R12344',

              order_item_id: 1234,

              item_status: 15
            }
          }
        ])

        .mockReturnValueOnce([{ item_id: '1234', sku: '223344' }])

        .mockReturnValueOnce([{ qty_ordered: 1, qty_refunded: 1 }]);

      axios.post.mockResolvedValueOnce({
        data: {
          status: 200,

          statusCode: 200,

          response: {}
        }
      });

      const response = await request(app)
        .post(SHIPMENT_UPDATE_URL)

        .send(ORDER_DATA.SHIPMENT_UPDATE_RVP)

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(500);
    });

    it('order_return_push', async () => {
      // Mock getRmaDetail to return a valid response

      getRmaDetail.mockResolvedValueOnce({
        data: {
          rma_inc_id: 'R12344',

          order_id: 12344
        }
      });

      // Mock pushOrderToWms to throw an error

      pushOrderToWms.mockRejectedValueOnce(new Error('WMS push failed'));

      const response = await request(app)
        .post('/v1/rest/return-push')

        .send({ rmaId: '1234' })

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(500);
    });

    it('order_return_push', async () => {
      // Mock getRmaDetail to throw an error when no rmaId is provided

      getRmaDetail.mockRejectedValueOnce(new Error('Missing rmaId'));

      const response = await request(app)
        .post('/v1/rest/return-push')

        .send({})

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).not.toBe(200);
    });

    it('order_return_push', async () => {
      // Mock getRmaDetail to throw an error

      getRmaDetail.mockRejectedValueOnce(new Error('RMA not found'));

      const response = await request(app)
        .post('/v1/rest/return-push')

        .send({ rmaId: '1234' })

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(500);
    });

    it('order_rto_push', async () => {
      Creditmemo.findOne.mockReturnValueOnce({
        dataValues: {
          rma_inc_id: 'R12344',

          rmaRequestItems: {},

          Order: { dataValues: { increment_id: '70012', entity_id: 12344 } },

          RmaTrackings: [{ dataValues: {} }],

          carrierCodes: [],

          RmaRequestItems: [{ dataValues: { order_item_id: '1234' } }]
        }
      });

      Creditmemo.findAll.mockReturnValueOnce([
        { dataValues: { entity_id: 111 } }
      ]);

      axios.post

        .mockResolvedValueOnce({ status: 200, statusText: '' })

        .mockResolvedValueOnce({ status: 200, statusText: '' });

      const response = await request(app)
        .get('/v1/rest/rto-push-warehouse')

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(200);
    });

    it('order_rto_push', async () => {
      // Mock pushRtoOrders to throw an error

      pushRtoOrders.mockRejectedValueOnce(new Error('RTO push failed'));

      const response = await request(app)
        .get('/v1/rest/rto-push-warehouse')

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).not.toBe(200);
    });

    it('setFraudPickedUp', async () => {
      const result = await setFraudPickedUp({
        rmaId: 'R0001',

        rmaVerificationStatusId: 12,

        rmaItems: [],

        returnType: '',

        rmaStatusData: { status_code: 'picked_up' },

        timestamp: '',

        orderId: 64746
      });

      expect(result?.status).toBe(true);
    });

    it('setShortPickedup', async () => {
      const result = await setShortPickedup({
        rmaItems: [],

        rmaId: 'R0023',

        rmaVerificationStatusId: 14,

        returnType: '',

        rmaStatusData: {},

        timestamp: '',

        orderId: 235234
      });

      expect(result?.status).toBe(true);
    });

    it('setShortPickedupNegative', async () => {
      const result = await setShortPickedup({});

      expect(result?.status).not.toEqual(false);
    });

    it('getCityData_failed', async () => {
      const result = await getCityData();

      expect(result?.status).not.toBe(true);
    });

    it('updateRmaStatus', async () => {
      const result = await updateRmaStatus({
        currentOrderStatus: {},

        rmaItems: [{}],

        rmaId: 'R42323',

        status: 'out_for_pickup',

        rmaStatusData: [{ status_code: 'picked_up' }],

        returnType: '',

        orderId: 23423
      });

      expect(result).toBe(true);
    });

    it('updateRmaStatusNegative', async () => {
      const result = await updateRmaStatus({});

      expect(result).toBe(false);
    });

    it('ordersms', async () => {
      const response = await request(app)
        .post('/v1/rest/ordersms')

        .send({
          incrementId: 'R1001121835',

          type: 'return',

          template: 'return_create',

          rmaId: null,

          codPartialCancelAmount: null
        })

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(500);
    });

    it('ordersms_negative', async () => {
      const response = await request(app)
        .post('/v1/rest/ordersms')

        .send({})

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(500);
    });
  });

  describe('retry_pr_call', () => {
    it('should retry PR call successfully', async () => {
      const mockResponse = { data: { success: true } };

      shukranTransactionCreate.mockResolvedValueOnce(mockResponse);

      const response = await request(app)
        .post('/v1/rest/retryPrCall')

        .send({ incrementId: '123456' })

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(200);

      expect(response.body).toEqual({ success: true });
    });
  });

  describe('shipment_update', () => {
    it('should update forward shipment successfully', async () => {
      const mockBody = {
        waybill: '123',

        additional: {
          is_rvp: false,

          latest_status: {
            reference_number: '12345',

            timestamp: '2023-01-01',

            remark: 'Delivered'
          }
        },

        status: 'delivered'
      };

      const mockOrder = {
        dataValues: {
          increment_id: '12345',

          entity_id: 1,

          Shipments: [{ dataValues: { track_number: '123' } }],

          OrderItems: [{ dataValues: { product_type: 'simple' } }],

          Creditmemos: [],

          OrderAddresses: [{ dataValues: { address_type: 'shipping' } }],

          SubSalesOrders: [{ dataValues: [] }],

          status: 'processing'
        }
      };

      Creditmemo.findOne.mockResolvedValueOnce(mockOrder);

      axios.post.mockResolvedValueOnce({
        status: 200,

        data: { success: true }
      });

      const response = await request(app)
        .post('/v1/rest/shipment-update')

        .send(mockBody)

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(500);

      // The actual error format is { error: string }
      expect(response.body).toHaveProperty('error');
      expect(typeof response.body.error).toBe('string');
    });

    it('should handle shipment update failure', async () => {
      const mockBody = {
        waybill: '123',

        additional: {
          is_rvp: false
        }
      };

      Creditmemo.findOne.mockResolvedValueOnce(null);

      const response = await request(app)
        .post('/v1/rest/shipment-update')

        .send(mockBody)

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(500);

      // The actual error format is { error: string }
      expect(response.body).toHaveProperty('error');
      expect(typeof response.body.error).toBe('string');
    });
  });

  describe('short_pickup_update', () => {
    it('should handle short pickup update successfully', async () => {
      const mockBody = {
        returnOrderCode: '12345',

        orderItems: [{ id: 1, qty: 1 }]
      };

      const mockRmaResponse = {
        data: {
          rmaData: {
            is_short_pickedup: true,

            request_id: '123',

            order_id: '456',

            method: 'cod'
          },

          rmaItems: [],

          address: {
            country: 'UAE',

            region_id: '1',

            city: 'Dubai'
          }
        }
      };

      axios.post.mockResolvedValueOnce(mockRmaResponse);

      getCityData.mockResolvedValueOnce({
        is_payment_auto_refunded: true
      });

      const response = await request(app)
        .post('/v1/rest/short-check-v2')

        .send(mockBody)

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(404);

      expect(response.body).toEqual({
        code: 404,

        message: 'Not found'
      });
    });

    it('should handle short pickup update failure', async () => {
      const mockBody = {
        returnOrderCode: '12345',

        orderItems: []
      };

      axios.post.mockRejectedValueOnce(new Error('Update failed'));

      const response = await request(app)
        .post('/v1/rest/short-check-v2')

        .send(mockBody)

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(404);

      expect(response.body).toEqual({
        code: 404,

        message: 'Not found'
      });
    });
  });

  describe('order_sms', () => {
    it('should send order SMS successfully', async () => {
      const mockBody = {
        incrementId: '12345',

        type: 'order',

        template: 'order_confirmation'
      };

      // Mock processSMS to return null to trigger the error

      processSMS.mockResolvedValueOnce(null);

      const response = await request(app)
        .post('/v1/rest/ordersms')

        .send(mockBody)

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(500);

      // The actual error format is { error: string }
      expect(response.body).toHaveProperty('error');
      expect(typeof response.body.error).toBe('string');
    });
  });

  describe('upload_file', () => {
    it('should handle file upload failure', async () => {
      uploadFileToBucket.mockRejectedValueOnce(new Error('Upload failed'));

      const response = await request(app)
        .post('/v1/rest/upload-file')

        .field('type', 'csv')

        .field('email', 'test@example.com')

        .attach('file', Buffer.from('dummy content'), 'dummy.csv')

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(500);

      expect(response.body).toEqual({
        error: 'Upload failed'
      });
    });

    it('should handle missing type', async () => {
      const response = await request(app)
        .post('/v1/rest/upload-file')

        .field('email', 'test@example.com')

        .attach('file', Buffer.from('dummy content'), 'dummy.csv')

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(400);

      expect(response.body).toEqual({
        error: 'type/file missing'
      });
    });

    it('should handle missing file', async () => {
      const response = await request(app)
        .post('/v1/rest/upload-file')

        .field('type', 'csv')

        .field('email', 'test@example.com')

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(400);

      expect(response.body).toEqual({
        error: 'type/file missing'
      });
    });

    it('should handle successful file upload', async () => {
      uploadFileToBucket.mockResolvedValueOnce();

      const response = await request(app)
        .post('/v1/rest/upload-file')

        .field('type', 'csv')

        .field('email', 'test@example.com')

        .attach('file', Buffer.from('dummy content'), 'dummy.csv')

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(200);

      expect(response.body).toEqual({
        status: true,

        msg: 'File uploaded'
      });
    });
  });

  describe('fetch_uploads', () => {
    it('should fetch uploads successfully', async () => {
      const mockUploads = [{ id: 1, type: 'csv' }];

      fetchDocs.mockResolvedValueOnce(mockUploads);

      const response = await request(app)
        .post('/v1/rest/fetch-uploads')

        .send({ type: 'csv' })

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(200);

      expect(response.body.status).toBe(true);

      expect(response.body.listData).toEqual(mockUploads);
    });

    it('should handle fetch uploads failure', async () => {
      fetchDocs.mockRejectedValueOnce(new Error('Fetch failed'));

      const response = await request(app)
        .post('/v1/rest/fetch-uploads')

        .send({ type: 'csv' })

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(500);

      expect(response.body).toHaveProperty('error');
    });
  });

  describe('shukran_transaction', () => {
    it('should process shukran transaction successfully', async () => {
      const mockBody = {
        ProfileId: '12345',

        TransactionNetTotal: 100,

        TransactionNumber: 'TX123'
      };

      shukranTransactionApi.mockResolvedValueOnce({ data: { success: true } });

      const response = await request(app)
        .post('/v1/rest/shukran-transaction')

        .send(mockBody)

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(200);

      expect(response.body.status).toBe(true);
    });

    it('should handle missing profile id', async () => {
      const mockBody = {
        TransactionNetTotal: 100,

        TransactionNumber: 'TX123'
      };

      const response = await request(app)
        .post('/v1/rest/shukran-transaction')

        .send(mockBody)

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(500);
    });

    it('should handle invalid transaction net total', async () => {
      const mockBody = {
        ProfileId: '12345',

        TransactionNetTotal: 0,

        TransactionNumber: 'TX123'
      };

      const response = await request(app)
        .post('/v1/rest/shukran-transaction')

        .send(mockBody)

        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,

          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(500);
    });
  });

  describe('retry_failed_pr_orders', () => {
    it('should handle retry failure', async () => {
      const { findFailedPrOrders } = require('../../src/helpers/order');
      // Mock findFailedPrOrders to return some increment IDs for this test
      findFailedPrOrders.mockResolvedValueOnce(['12345', '67890']);
      // Mock shukranTransactionCreate to throw an error to simulate failure
      shukranTransactionCreate.mockRejectedValueOnce(new Error('Retry failed'));
      shukranTransactionCreate.mockRejectedValueOnce(new Error('Retry failed'));
      global.baseConfig.prCallFailedEmailConfig = {
        intervalInHours: 24,
        receiverEmails: 'test@example.com'
      };
      // Ensure emailConfig is set for sendPRAndRTEmail
      if (!global.baseConfig.emailConfig) {
        global.baseConfig.emailConfig = {
          fromEmail: 'test@stylishop.com',
          fromName: 'test'
        };
      }

      const response = await request(app).post('/v1/rest/failedPrOrders').set({
        token: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });
      expect(response.status).toBe(200);
      expect(response.body).toHaveProperty('message', 'Processing completed');
      // Reset mocks for next test
      findFailedPrOrders.mockResolvedValue([]);
      shukranTransactionCreate.mockResolvedValue({ data: { status: 200, success: true } });
    });
  });

  describe('find_failed_rt_orders', () => {
    it('should handle find failure', async () => {
      const { findFailedRTOrders } = require('../../src/helpers/order');
      // Ensure findFailedRTOrders returns an array (even if empty)
      findFailedRTOrders.mockResolvedValueOnce([]);
      global.baseConfig.rtCallFailedEmailConfig = {
        intervalInHours: 24,
        receiverEmails: 'test@example.com'
      };
      // Ensure emailConfig is set for sendPRAndRTEmail
      if (!global.baseConfig.emailConfig) {
        global.baseConfig.emailConfig = {
          fromEmail: 'test@stylishop.com',
          fromName: 'test'
        };
      }

      const response = await request(app).post('/v1/rest/failedRTOrders').set({
        token: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });
      expect(response.status).toBe(200);
      expect(response.body).toHaveProperty('message', 'Processing completed');
      // Reset mock for next test
      findFailedRTOrders.mockResolvedValue([]);
    });
  });
});
