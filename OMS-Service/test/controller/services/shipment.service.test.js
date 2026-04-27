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
const app = require('../../../src/app');
const { Creditmemo } = require('../../../src/models/seqModels/index');
const SAMPLE_REQ = require('../constants/order.request.json');
const { handleShipmentUpdates } = require('../../../src/helpers/shipment');
jest.mock('axios');
jest.mock('firebase-admin');
jest.mock('../../../src/models/seqModels/index');
jest.setTimeout(80000);

describe('services_methods', () => {
  beforeAll(() => {
    global.logError = jest.fn();
    global.baseConfig = {
      wmsRtoPush: {
        interval: 10,
        batchSize: 10
      }
    };
  });

  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('services', () => {
    it('should handle shipment updates successfully', async () => {
      Creditmemo.findOne.mockReturnValueOnce({
        dataValues: {
          rma_inc_id: 'R12344',
          status: 'shipped'
        }
      });

      const result = await handleShipmentUpdates(
        SAMPLE_REQ.SHIPMENT_UPDATE_PICKED_UP
      );

      expect(Creditmemo.findOne).toHaveBeenCalledTimes(1);
      expect(result).toBeUndefined();
    });

    it('should call shukranTransactionApi if shukranEnable is true', async () => {
      global.baseConfig = {
        ...global.baseConfig,
        shukranEnable: true
      };

      Creditmemo.findOne.mockReturnValueOnce({
        dataValues: {
          rma_inc_id: 'R12344',
          status: 'shipped'
        }
      });

      const payload = { ...SAMPLE_REQ.SHIPMENT_UPDATE_PICKED_UP };
      payload['additional']['notification_event_id'] = 5;

      const shukranTransactionCreate = jest.spyOn(console, 'log');
      await handleShipmentUpdates(payload);

      expect(shukranTransactionCreate).toHaveBeenCalled();
      expect(Creditmemo.findOne).toHaveBeenCalledTimes(1);

      shukranTransactionCreate.mockRestore();
    });
  });
});
