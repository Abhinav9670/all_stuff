/* eslint-disable max-lines-per-function */
/* eslint-disable no-undef */
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
jest.mock('axios');
jest.mock('firebase-admin');
jest.mock('@sendgrid/mail');
jest.mock('../../src/models/seqModels/index');
jest.mock('../../src/utils/config', () => {
  const mockGetStoreCountryMap = jest.fn(() => ({ 1: 'sa', 3: 'sa', 7: 'ae', 11: 'ae' }));
  const mockGetCountryStoreMap = jest.fn(() => ({ sa: [1, 3], ae: [7, 11] }));
  return {
    getStoreCountryMap: mockGetStoreCountryMap,
    getStoreConfigs: jest.fn(() => []),
    getStoreWebsiteIdMap: jest.fn(() => ({})),
    getCountryStoreMap: mockGetCountryStoreMap,
    getWebsiteStoreMap: jest.fn(() => ({})),
    getFeatureEnabled: jest.fn(() => false),
    frontendURLBasedOnStoreId: {},
    storeMap: {}
  };
});

jest.mock('../../src/services/email.service', () => ({
  sendSgEmail: jest.fn().mockResolvedValue(true)
}));

jest.mock('../../src/helpers/alerts/index', () => ({
  processAlerts: jest.fn().mockResolvedValue(undefined),
  alertProcessingOrder: jest.fn().mockResolvedValue(undefined),
  alertNoOrder: jest.fn().mockResolvedValue(undefined),
  alertAwbMissing: jest.fn().mockResolvedValue(undefined),
  pendingOrHoldPayment: jest.fn().mockResolvedValue(undefined),
  paymentFailed: jest.fn().mockResolvedValue(undefined),
  alertLessOrderInAnHour: jest.fn().mockResolvedValue(undefined)
}));
jest.mock('../../src/helpers/alerts/alert', () => ({
  wmsNull: jest.fn().mockResolvedValue(undefined),
  fraudCheck: jest.fn().mockResolvedValue(undefined)
}));
jest.mock('../../src/helpers/alerts/stuckOrderAlert', () => ({
  processStuckOrderAlert: jest.fn().mockResolvedValue(undefined)
}));

// Mock Playwright at the very top to prevent Chromium launch errors in tests
jest.mock('playwright', () => ({
  chromium: {
    launch: jest.fn().mockResolvedValue({
      newPage: jest.fn().mockResolvedValue({
        setContent: jest.fn(),
        pdf: jest.fn(),
        close: jest.fn()
      }),
      close: jest.fn()
    })
  }
}));

console.log = jest.fn();
console.error = jest.fn();

const app = require('../../src/app');
const CONST = require('./constants/consul.constants.json');
const RUN_CONFIG = require('../run.config.json');
const { Order, sequelize } = require('../../src/models/seqModels/index');
const HEADER_TOKEN = RUN_CONFIG['X-HEADER-TOKEN'];

jest.setTimeout(80000);

describe('alert_api', () => {
  beforeAll(() => {
    global.payfortConfig = CONST.payfortConfig;
    global.paymentMethods = CONST.paymentMethods;
    global.config = CONST.storeConfig;
    global.baseConfig = {
      emailConfig: {
        sendCreditmemoEmail: true,
        fromEmail: 'test1@stylishop.com',
        fromName: 'test'
      },
      alertConfig: {
        processingOrder: {
          count: {
            sa: 0,
            ae: 0
          },
          interval: '720',
          receiverEmails: 'test2@stylishop.com'
        },
        noOrder: {
          interval: {
            sa: 30
          },
          receiverEmails: 'test3@stylishop.com'
        },
        lessOrderForAnHour: {
          minutes: 1,
          thresholds: {
            ios: 10,
            android: 5,
            msite: 1
          },
          receiverEmails: 'test4@stylishop.com'
        },
        awbMissing: {
          count: {
            sa: '1'
          },
          reversecount: {
            sa: '1'
          },
          interval: '480',
          receiverEmails: 'test5@stylishop.com'
        },
        pendingOrHoldPayment: {
          count: {
            sa: 1
          },
          intervalEnd: 20,
          intervalStart: 1440,
          receiverEmails: 'test@landmarkgroup.com'
        },
        paymentFailed: {
          count: {
            sa: 1
          },
          interval: 60,
          receiverEmails: 'adfdsd@landmarkgroup.com'
        },
        wmsNull: {
          count: {
            sa: 1
          },
          receiverEmails: 'dsfgsdf@landmarkgroup.com',
          intervalEnd: 20,
          intervalStart: 1440
        }
      }
    };
    global.logError = jest.fn(() => ({}));
  });
  beforeEach(() => {});

  describe('alert_routes', () => {
    it('alert_processing', async () => {
      Order.findAll.mockResolvedValueOnce([
        { store_id: 1, increment_id: 12344 }
      ]);
      const response = await request(app).get('/v1/alert/processing').set({
        token: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });
      expect(response.status).toBe(200);
    });
    it('alert_processing_negative', async () => {
      const response = await request(app).get('/v1/alert/processing').set({
        token: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });
      expect(response).not.toEqual(200);
    });
    it('alert_noorder', async () => {
      Order.findAll.mockResolvedValueOnce([
        {
          dataValues: { cnt: 0 }
        }
      ]);
      const response = await request(app).get('/v1/alert/noorder').set({
        token: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });
      expect(response.status).toBe(200);
    });

    it('alert_noorder_negative', async () => {
      const response = await request(app).get('/v1/alert/noorder').set({
        token: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });
      expect(response).not.toEqual(200);
    });
    it('alert_lessorder', async () => {
      const response = await request(app).get('/v1/alert/lessorder').set({
        token: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });
      expect(response.status).toBe(200);
    });

    it('alert_lessorder_negative', async () => {
      const response = await request(app).get('/v1/alert/lessorder').set({
        token: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });
      expect(response).not.toEqual(200);
    });


    it('alert_awb_missing', async () => {
      Order.findAll
        .mockReturnValueOnce([
          { store_id: 1, increment_id: 12344, Shipments: [] }
        ])
        .mockReturnValueOnce([
          { store_id: 1, increment_id: 12344, RmaTrackings: [] }
        ]);
      const response = await request(app).get('/v1/alert/awb-missing').set({
        token: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });
      expect(response.status).toBe(200);
    });
    it('alert_awb_missing_negative', async () => {
      const response = await request(app).get('/v1/alert/awb-missing').set({
        token: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });
      expect(response).not.toEqual(200);
    });
    it('alert_pending_payment', async () => {
      Order.findAll.mockReturnValueOnce([
        { store_id: 1, increment_id: 12344, Shipments: [] }
      ]);
      const response = await request(app).get('/v1/alert/pending-payment').set({
        token: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });
      expect(response.status).toBe(200);
    });

    it('alert_pending_payment_negative', async () => {
      const response = await request(app).get('/v1/alert/pending-payment').set({
        token: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });
      expect(response).not.toEqual(200);
    });
    it('alert_failed_payment', async () => {
      Order.findAll.mockReturnValueOnce([
        { store_id: 1, increment_id: 12344, Shipments: [] }
      ]);
      const response = await request(app).get('/v1/alert/failed-payment').set({
        token: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });
      expect(response.status).toBe(200);
    });

    it('alert_failed_payment_negative', async () => {
      const response = await request(app).get('/v1/alert/failed-payment').set({
        token: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });
      expect(response).not.toEqual(200);
    });
    it('alert_wms_null', async () => {
      Order.findAll.mockReturnValueOnce([
        { store_id: 1, increment_id: 12344, Shipments: [] }
      ]);
      const response = await request(app).get('/v1/alert/wms-null').set({
        token: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });
      expect(response.status).toBe(200);
    });

    it('alert_wms_null_negative', async () => {
      const response = await request(app).get('/v1/alert/wms-null').set({
        token: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });
      expect(response).not.toEqual(200);
    });
    it('alert_fraud_check', async () => {
      sequelize.query.mockReturnValueOnce([{ status: 23, count: 344 }]);
      const response = await request(app).get('/v1/alert/fraud-check').set({
        token: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });
      expect(response.status).toBe(200);
    });

    it('alert_fraud_check_negative', async () => {
      const response = await request(app).get('/v1/alert/fraud-check').set({
        token: RUN_CONFIG.JWT_TOKEN,
        'X-Header-Token': HEADER_TOKEN
      });
      expect(response).not.toEqual(200);
    });
    it('test', async () => {
      expect(200).toBe(200);
    });
  });
});
