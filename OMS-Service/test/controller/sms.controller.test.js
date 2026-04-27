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
console.log = jest.fn();
console.error = jest.fn();

const RUN_CONFIG = require('../run.config.json');
const HEADER_TOKEN = RUN_CONFIG['X-HEADER-TOKEN'];

const app = require('../../src/app');
const mongoUtil = require('../../src/utils/mongoInit');

jest.mock('../../src/utils/config', () => ({
  getStoreCountryMap: jest.fn(() => ({ 1: 'sa', 3: 'sa', 7: 'ae', 11: 'ae' })),
  getStoreConfigs: jest.fn(() => []),
  getStoreWebsiteIdMap: jest.fn(() => ({ 1: 1, 3: 1, 7: 2, 11: 2 })),
  getCountryStoreMap: jest.fn(() => ({ sa: [1, 3], ae: [7, 11] })),
  getWebsiteStoreMap: jest.fn(() => ({})),
  getFeatureEnabled: jest.fn(() => false),
  frontendURLBasedOnStoreId: {},
  storeMap: {}
}));

jest.mock('../../src/helpers/orderOps', () => ({
  getCustomersDataWithFailedProxyOrders2: jest.fn().mockResolvedValue({
    finalArrayForNonProxyUsers: [],
    finalArrayForProxyUsers: []
  }),
  getQuoteIdsNotPaymentFailedInDuration: jest.fn().mockResolvedValue([])
}));

jest.mock('../../src/helpers/smsHelper', () => ({
  sendSMSNonProxyOrders: jest.fn(() => ({
    smsCurrentRecipients: [],
    bulkUpdateArray: []
  })),
  getQuoteIdsNotPaymentFailedInDuration: jest.fn().mockResolvedValue([])
}));

jest.mock('../../src/services/misc.service', () => ({
  sendKaleyraSMS: jest.fn().mockResolvedValue(undefined)
}));

jest.mock('../../src/services/email.service', () => ({
  sendSgEmail: jest.fn().mockResolvedValue(true)
}));

jest.mock('../../src/services/sms.service', () => {
  const mockSendfailedOrderSMS = jest.fn().mockResolvedValue(undefined);
  const mockFetchSMSList = jest.fn().mockResolvedValue([]);
  return {
    sendfailedOrderSMS: mockSendfailedOrderSMS,
    fetchSMSList: mockFetchSMSList
  };
});

jest.setTimeout(90000);

describe('sms_routes', () => {
    beforeAll(async () => {
        global.logError = jest.fn(() => ({}));
        global.baseConfig = {
            smsConfig: {
                failureSMSJourney: {
                    smsRestrictionInHrs: 24,
                    endOffsetInMinutes: 30,
                    templates: {
                        nonProxyOrdersFailed: {
                            en: 'Test template en',
                            ar: 'Test template ar'
                        },
                        proxyOrdersFailed: {
                            en: 'Test proxy template en',
                            ar: 'Test proxy template ar'
                        }
                    }
                },
                failureBNPLPaymentEmail: {}
            },
            emailConfig: {
                fromEmail: 'test@example.com',
                fromName: 'Test'
            }
        };
        // Connect to MongoDB using environment variables
        await new Promise((resolve) => {
            mongoUtil.connectToServer((err, db) => {
                resolve();
            });
        });
    });

    it('sendFailedOrderSMS - GET /failedOrderSMSPush', async () => {
        const smsService = require('../../src/services/sms.service');
        smsService.sendfailedOrderSMS.mockResolvedValueOnce(undefined);
        
        const response = await request(app)
            .get('/v1/sms/failedOrderSMSPush')
            .set({
                authorization: RUN_CONFIG.JWT_TOKEN,
                'X-Header-Token': HEADER_TOKEN
            });

        expect(response.status).toBe(200);
        expect(response.body.statusMsg).toBe('Request acknowledged!');
    });

    it('SMSList - POST /fetchSMSList', async () => {
        const smsService = require('../../src/services/sms.service');
        smsService.fetchSMSList.mockResolvedValueOnce([
            {
                totalData: [{ msg: 'Sample SMS' }],
                totalCount: [{ count: 1 }]
            }
        ]);

        const response = await request(app)
            .post('/v1/sms/fetchSMSList')
            .send({})
            .set({
                authorization: RUN_CONFIG.JWT_TOKEN,
                'X-Header-Token': HEADER_TOKEN
            });

        expect(response.status).toBe(200);
        expect(response.body.status).toBe(true);
        expect(response.body.statusCode).toBe('200');
        expect(response.body.totalCount).toBe(1);
    });
});
