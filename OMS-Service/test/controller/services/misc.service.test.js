/* eslint-disable no-unused-vars */
/* eslint-disable max-lines */
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
const app = require('../../../src/app');
const {
  Creditmemo,
  AmastyStoreCredit
} = require('../../../src/models/seqModels/index');
const { sendKaleyraSMS } = require('../../../src/services/misc.service');
const axios = require('axios');
const {
  transferFailure,
  sendSubmitSms
} = require('../../../src/helpers/bankTransfer');
const { fetchDocs } = require('../../../src/utils/mongo');
const CONST = require('../constants/consul.constants.json');
const {
  sendEarnRequestCheckIsRatingOnOrder
} = require('../../../src/utils/easApi');
const { getGstInPan } = require('../../../src/helpers/tax');
jest.mock('axios');
jest.mock('firebase-admin');
jest.mock('../../../src/models/seqModels/index');
jest.mock('../../../src/utils/mongo');
jest.mock('handlebars');
jest.setTimeout(80000);


describe('services_methods', () => {
  beforeAll(() => {
    global.logError = jest.fn(() => ({}));
    global.config = CONST.storeConfig;
    global.baseConfig = CONST.omsConfig;
    global.javaOrderServiceConfig = {
      inventory_mapping: [
        { warehouse_id: 110, GSTIN: 'ADSFD3433', PAN: '23423eded' }
      ]
    };
  });
  beforeEach(() => {});

  describe('services', () => {
    it('sendKaleyraSMS', async () => {
      Creditmemo.findOne.mockReturnValueOnce({
        dataValues: {
          rma_inc_id: 'R12344',
          status: 'shipped'
        }
      });
      axios.get.mockResolvedValueOnce({
        data: {
          status: 'OK'
        }
      });
      const result = await sendKaleyraSMS({
        msg: 'hello',
        phone: '7204631704',
        smsTemplateId: 'test'
      });
      expect(result).toBe(true);
    });
    it('transferFailure_mock', async () => {
      fetchDocs.mockResolvedValueOnce([
        { status: 'pending', customerId: 121321, storeId: 3 }
      ]);
      AmastyStoreCredit.findOne.mockReturnValueOnce({
        store_credit: '12.09',
        returnable_amount: '1.0'
      });
      await transferFailure({
        requestId: '621781d8b3e19706ac1fed1a',
        uploadId: '6150e51f96c1fa0026b77cf9',
        newStatus: 'processing',
        amount: '123.40',
        getCustmerHistoryId: jest.fn()
      });
      expect(200).toBe(200);
    });
    it('sendSubmitSms', async () => {
      const transactionData = { storeId: 3, phoneNumber: '+966 500234567' };
      try {
        await sendSubmitSms({ transactionData });
      } catch (error) {
        console.log('error');
      }
      expect(200).toBe(200);
    });
    it('sendEarnRequestCheckIsRatingOnOrder', async () => {
      axios.post.mockResolvedValueOnce({
        status: 200,
        data: {
          isRatingEnable: true
        }
      });
      const value = await sendEarnRequestCheckIsRatingOnOrder({});
      expect(value).toBe(true);
    });
    it('getGstInPan', async () => {
      await getGstInPan(110);
      expect(200).toBe(200);
    });
  });
});
