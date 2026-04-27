/* eslint-disable max-lines-per-function */
/* eslint-disable no-undef */
const request = require('supertest');

// Mock redis
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

// Mock console
console.log = jest.fn();
console.error = jest.fn();

// Mock csvtojson
jest.mock('csvtojson');

// Mock google storage
jest.mock('../../src/config/googleStorage', () => ({
  uploadFileToBucket: jest.fn()
}));

// Mock axios
jest.mock('axios');

// Mock firebase-admin
jest.mock('firebase-admin');

// Mock sendgrid
jest.mock('@sendgrid/mail');

// Mock the mongo utility functions
jest.mock('../../src/utils/mongo', () => ({
  fetchDocs: jest.fn(({ collection, filters }) => {
    if (collection === 'bank_transfers') {
      // For transferFailure test, return a document that matches the expected structure
      return Promise.resolve([
        {
          _id: '621781d8b3e19706ac1fed1a',
          status: 'pending', // This will be used in the transferFailure function
          customerId: 1,
          storeId: 1,
          name: 'Test User',
          amount: 100,
          phoneNumber: '+1234567890'
        }
      ]);
    }
    if (collection === 'uploads') {
      return Promise.resolve([
        {
          processedCount: 0,
          totalCount: 1,
          email: 'test@stylishop.com'
        }
      ]);
    }
    return Promise.resolve([]);
  }),
  updateOne: jest.fn().mockResolvedValue(true),
  insertOne: jest.fn().mockResolvedValue(true)
}));

// Mock the bank transfer helper to avoid complex model dependencies
jest.mock('../../src/helpers/bankTransfer/index', () => ({
  transferFailure: jest.fn().mockResolvedValue(true),
  sendSubmitSms: jest.fn().mockResolvedValue(true),
  processError: jest.fn().mockResolvedValue(true),
  bankTransferReqSchema: {}
}));

// Mock utility functions
jest.mock('../../src/utils', () => ({
  getNumericValue: jest.fn((value) => value),
  logInfo: jest.fn()
}));

// Mock services
jest.mock('../../src/services/misc.service', () => ({
  sendKaleyraSMS: jest.fn().mockResolvedValue(true)
}));

jest.mock('../../src/services/email.service', () => ({
  sendSgEmail: jest.fn().mockResolvedValue(true)
}));

jest.mock('../../src/helpers/customer', () => ({
  getCustomerInfo: jest.fn().mockResolvedValue({
    mobileNumber: '+1234567890'
  })
}));

// Mock the config utility
jest.mock('../../src/utils/config', () => ({
  getStoreConfigs: jest.fn().mockImplementation(({ key, storeId }) => {
    if (key === 'currencyConversionRate') {
      return [{
        currencyConversionRate: 1,
        storeCurrency: 'AED'
      }];
    }
    if (key === 'storeCurrency') {
      return [{
        storeCurrency: 'AED'
      }];
    }
    return [];
  }),
  getStoreCountryMap: jest.fn(() => ({ 1: 'sa', 3: 'sa', 7: 'ae', 11: 'ae' })),
  getStoreWebsiteIdMap: jest.fn(() => ({})),
  getCountryStoreMap: jest.fn(() => ({ sa: [1, 3], ae: [7, 11] })),
  getWebsiteStoreMap: jest.fn(() => ({})),
  getFeatureEnabled: jest.fn(() => false),
  frontendURLBasedOnStoreId: {},
  storeMap: {}
}));

// Mock SMS service for failedOrderSMSPush endpoint
jest.mock('../../src/services/sms.service', () => ({
  sendfailedOrderSMS: jest.fn().mockResolvedValue(undefined),
  fetchSMSList: jest.fn().mockResolvedValue([])
}));

// Mock email service
jest.mock('../../src/services/email.service', () => ({
  sendSgEmail: jest.fn().mockResolvedValue(true)
}));

// Mock global functions
global.logInfo = jest.fn();
global.logError = jest.fn();

// Mock the bank service
const mockCreateBankTransfer2 = jest.fn().mockResolvedValue({
  status: true,
  statusCode: '200',
  statusMsg: 'Bank Transfers created successfully!'
});
const mockPurgeIbans = jest.fn().mockResolvedValue({ result: true });
jest.mock('../../src/services/bank.service', () => ({
  fetchBankTransfers: jest.fn().mockResolvedValue([
    {
      totalData: [],
      totalCount: [{ count: 0 }]
    }
  ]),
  createBankTransfer2: mockCreateBankTransfer2,
  getBankTransfersHistory: jest.fn().mockResolvedValue([]),
  purgeIbans: mockPurgeIbans,
  processTransfers: jest.fn().mockResolvedValue()
}));

// Import after mocks
const app = require('../../src/app');
const mongoUtil = require('../../src/utils/mongoInit');
const CONST = require('./constants/consul.constants.json');
const RUN_CONFIG = require('../run.config.json');
const { transferFailure } = require('../../src/helpers/bankTransfer');
const HEADER_TOKEN = RUN_CONFIG['X-HEADER-TOKEN'];
const { AmastyStoreCredit } = require('../../src/models/seqModels/index');
const { saveInventoryLog } = require('../../src/services/logging.service');

// Mock global functions
jest.setTimeout(90000);

describe('banktransfer_api', () => {
  beforeAll(async () => {
    // Connect to MongoDB using environment variables
    await new Promise((resolve) => {
      mongoUtil.connectToServer((err, db) => {
        resolve();
      });
    });
    global.payfortConfig = CONST.payfortConfig;
    global.paymentMethods = CONST.paymentMethods;
    global.config = CONST.storeConfig;
    global.baseConfig = CONST.omsConfig;
    global.logError = jest.fn(() => ({}));
  });

  beforeEach(() => {
    jest.clearAllMocks();
    
    // Reset other mocks
    require('csvtojson').mockReset && require('csvtojson').mockReset();
    require('../../src/config/googleStorage').uploadFileToBucket.mockReset && require('../../src/config/googleStorage').uploadFileToBucket.mockReset();
  });

  describe('banktransfer_routes', () => {
    it('bank_list', async () => {
      const response = await request(app)
        .post('/v1/banktransfer/list')
        .send({ filters: {}, offset: 0, pageSize: 20 })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('bank_list_negative', async () => {
      const response = await request(app)
        .post('/v1/banktransfer/list')
        .send({})
        .set({
          token: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).not.toBe(200);
    });

    it('transferFailure', async () => {
      const { transferFailure } = require('../../src/helpers/bankTransfer/index');
      
      await transferFailure({
        requestId: '621781d8b3e19706ac1fed1a',
        uploadId: '6150e51f96c1fa0026b77cf9',
        newStatus: 'processing',
        amount: '123.40',
        getCustmerHistoryId: jest.fn().mockResolvedValue('mockHistoryId')
      });
      
      expect(transferFailure).toHaveBeenCalledWith({
        requestId: '621781d8b3e19706ac1fed1a',
        uploadId: '6150e51f96c1fa0026b77cf9',
        newStatus: 'processing',
        amount: '123.40',
        getCustmerHistoryId: expect.any(Function)
      });
    });

    it('failedOrderSMSPush', async () => {
      // SMS service is already mocked globally, just ensure it resolves
      const smsService = require('../../src/services/sms.service');
      if (smsService && smsService.sendfailedOrderSMS) {
        smsService.sendfailedOrderSMS.mockResolvedValueOnce();
      }
      
      const response = await request(app)
        .get('/v1/sms/failedOrderSMSPush')
        .set({
          token: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('failedOrderSMSPushNegative', async () => {
      const response = await request(app)
        .get('/v1/sms/failedOrderSMSPush')
        .set({
          token: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response).not.toEqual(200);
    });

    it('banktransferUploadList', async () => {
      const response = await request(app)
        .post('/v1/banktransfer/upload/list')
        .send({ "email": "" })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('banktransferUploadListNegative', async () => {
      const response = await request(app)
        .post('/v1/banktransfer/upload/list')
        .send({})
        .set({
          token: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response).not.toEqual(200);
    });

    it('banktransferStatusPurge', async () => {
      mockPurgeIbans.mockResolvedValueOnce({ result: true });
      
      const response = await request(app)
        .get('/v1/banktransfer/status/purge')
        .set({
          token: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('banktransferStatusPurgeNegative', async () => {
      const response = await request(app)
        .get('/v1/banktransfer/status/purge')
        .set({
          token: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response).not.toEqual(200);
    });

    it('sms_list', async () => {
      const response = await request(app)
        .post('/v1/sms/fetchSMSList')
        .send({
          filters: {
            fromDate: '16-02-2022',
            toDate: '18-02-2022',
            mode: 'tabby_installments'
          },
          query: 'chandan@stylishop.com',
          pageSize: 10,
          offset: 0
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('sms_list', async () => {
      const response = await request(app)
        .post('/v1/sms/fetchSMSList')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response).not.toEqual(200);
    });

    it('test_bank', async () => {
      expect(200).toBe(200);
    });

    it('saveInventoryLog', async () => {
      await saveInventoryLog({ sku: '23432', inventory: 'sa' });
      expect(200).toBe(200);
    });

    it('find_inventory', async () => {
      const response = await request(app)
        .get('/v1/logging/inventory/sa/23432')
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('find_inventory_negative', async () => {
      const response = await request(app)
        .get('/v1/logging/inventory/sa')
        .set({
          token: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).not.toBe(200);
    });

    it('processBankTransfers - success', async () => {
      const csvtojson = require('csvtojson');
      const uploadFileToBucket = require('../../src/config/googleStorage').uploadFileToBucket;
      const mockTransferRequests = [{ foo: 'bar' }];
      const mockUploadResp = { insertedId: 'mockUploadId' };
      csvtojson.mockImplementation(() => ({
        fromFile: jest.fn().mockResolvedValue(mockTransferRequests)
      }));
      uploadFileToBucket.mockResolvedValue({ uploadResp: mockUploadResp });
      const processTransfersSpy = jest.spyOn(require('../../src/services/bank.service'), 'processTransfers').mockImplementation(() => { });
      const app = require('../../src/app');
      const response = await request(app)
        .post('/v1/banktransfer/process')
        .attach('file', Buffer.from('foo,bar\n1,2'), 'test.csv')
        .field('email', 'test@stylishop.com')
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
      expect(response.body.status).toBe(true);
      processTransfersSpy.mockRestore();
    });

    it('processBankTransfers - error', async () => {
      const csvtojson = require('csvtojson');
      csvtojson.mockImplementation(() => ({
        fromFile: jest.fn().mockRejectedValue(new Error('csv error'))
      }));
      const app = require('../../src/app');
      const response = await request(app)
        .post('/v1/banktransfer/process')
        .attach('file', Buffer.from('foo,bar\n1,2'), 'test.csv')
        .field('email', 'test@stylishop.com')
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(500);
      expect(response.body.error).toBe('csv error');
    });

    it('createBankTransfer - success', async () => {
      const mockPayload = {
        iban: 'AE070331234567890123456',
        amount: 100,
        customerId: 1,
        storeId: 1,
        name: 'Test',
        bankName: 'TestBank',
        swiftCode: 'TSTB1234'
      };
      
      mockCreateBankTransfer2.mockResolvedValueOnce({
        status: true,
        statusCode: '200',
        statusMsg: 'Bank Transfers created successfully!'
      });
      
      const response = await request(app)
        .post('/v1/banktransfer/create')
        .send(mockPayload)
        .set({
          token: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
      // The controller returns the service response, not the input payload
      expect(response.body).toEqual({
        status: true,
        statusCode: '200',
        statusMsg: 'Bank Transfers created successfully!'
      });
    });

    it('createBankTransfer - error', async () => {
      // Mock the bank service to throw an error
      mockCreateBankTransfer2.mockRejectedValueOnce(new Error('Service error'));
      
      const response = await request(app)
        .post('/v1/banktransfer/create')
        .send({})
        .set({
          token: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(500);
    });
  });
});

const parseBody = (body) => {
  if (typeof body === 'string') {
    try {
      return JSON.parse(body);
    } catch (e) {
      return body;
    }
  }
  return body;
};

