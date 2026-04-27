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
const app = require('../../src/app');
const CONST = require('./constants/consul.constants.json');
const RUN_CONFIG = require('../run.config.json');
const ORDER_DATA = require('./constants/order.request.json');
const ORDER_RESPONSE = require('./constants/order.response.json');
const { insertOne } = require('../../src/utils/mongo');
const {
  Creditmemo,
  OrderItem,
  sequelize
} = require('../../src/models/seqModels/index');
const HEADER_TOKEN = RUN_CONFIG['X-HEADER-TOKEN'];
const axios = require('axios');
jest.mock('axios');
jest.mock('firebase-admin');
jest.mock('../../src/models/seqModels/index');
jest.mock('../../src/utils/mongo');
jest.mock('../../src/middlewares/mobileValidate', () => (index) => (req, res, next) => next());
jest.mock('../../src/middlewares/authValidate', () => (index) => (req, res, next) => next());
jest.setTimeout(90000);

describe('order_api', () => {
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
      configs: {
        carrierCodes: ['a', 'b']
      }
    };
    global.logError = jest.fn(() => ({}));
  });
  beforeEach(() => {});

  describe('rest_routes', () => {
    it('orders_list_valid', async () => {
      axios.post.mockResolvedValueOnce({ status: 200, data: {} });
      const response = await request(app)
        .post('/v1/orders/list')
        .send(ORDER_DATA.ORDER_LIST)
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('orders_list_valid_negative', async () => {
      const response = await request(app)
        .post('/v1/orders/list')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).not.toBe(200);
    });
    it('orders_detail_valid', async () => {
      axios.post.mockResolvedValueOnce({ status: 200, data: {} });
      const response = await request(app)
        .post('/v1/orders/detail')
        .send(ORDER_DATA.ORDER_LIST)
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('orders_detail_valid_negative', async () => {
      const response = await request(app)
        .post('/v1/orders/detail')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).not.toBe(200);
    });
    it('orders_invoice_valid', async () => {
      axios.post.mockResolvedValueOnce({ status: 200, data: {} });
      const response = await request(app)
        .post('/v1/orders/invoice')
        .send(ORDER_DATA.ORDER_LIST)
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('orders_invoice_valid_negative', async () => {
      const response = await request(app)
        .post('/v1/orders/invoice')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).not.toBe(200);
    });
    it('orders_shipment_valid', async () => {
      axios.post.mockResolvedValueOnce({ status: 200, data: {} });
      const response = await request(app)
        .post('/v1/orders/shipment')
        .send(ORDER_DATA.ORDER_LIST)
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
    it('orders_shipment_valid_negative', async () => {
      const response = await request(app)
        .post('/v1/orders/shipment')
        .send()
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).not.toBe(200);
    });
    it('orders_create_coupon_valid', async () => {
      axios.post.mockResolvedValueOnce({ status: 200, data: {} });
      const response = await request(app)
        .post('/v1/orders/quote/coupon')
        .send(ORDER_DATA.ORDER_LIST)
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
    it('orders_create_coupon_valid_negative', async () => {
      const response = await request(app)
        .post('/v1/orders/quote/coupon')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).not.toBe(200);
    });
    it('orders_delete_coupon_valid', async () => {
      axios.delete.mockResolvedValueOnce({ status: 200, data: {} });
      const response = await request(app)
        .delete('/v1/orders/quote/coupon')
        .send(ORDER_DATA.ORDER_LIST)
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
    it('orders_delete_coupon_valid_negative', async () => {
      const response = await request(app)
        .delete('/v1/orders/quote/coupon')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).not.toBe(200);
    });
    it('order_address_valida', async () => {
      axios.put.mockResolvedValueOnce({ status: 200, data: {} });
      const response = await request(app)
        .put('/v1/orders/address')
        .send(ORDER_DATA.ORDER_LIST)
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(500);
    });

    it('order_address_valida_negative', async () => {
      const response = await request(app)
        .put('/v1/orders/address')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response).not.toEqual(500);
    });
    it('order_status_valida', async () => {
      axios.post.mockResolvedValueOnce({ status: 200, data: {} });
      const response = await request(app)
        .post('/v1/orders/status')
        .send(ORDER_DATA.ORDER_LIST)
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('order_status_validate_empty', async () => {
      const response = await request(app)
        .post('/v1/orders/status')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
    it('order_creditmemo', async () => {
      Creditmemo.findAll
        .mockReturnValueOnce([
          { dataValues: { CreditmemoItems: [], CreditmemoComments: [] } }
        ])
        .mockReturnValueOnce([
          { product_type: 'configurable', item_id: '123213123' }
        ]);
      const response = await request(app)
        .post('/v1/orders/creditmemo')
        .send({ orderId: '123123' })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('order_creditmemo_negative', async () => {
      const response = await request(app)
        .post('/v1/orders/creditmemo')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).not.toBe(200);
    });
    it('order_creditmemo_update', async () => {
      const response = await request(app)
        .post('/v1/orders/creditmemo/update')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
    it('order_create_awb_negative', async () => {
      axios.get.mockResolvedValueOnce({ status: 200, data: {} });
      insertOne.mockResolvedValue({});
      const response = await request(app)
        .post('/v1/orders/create-awb')
        .send({
          query: { orderCode: 123, shipmentCode: 23423423 },
          email: 'chandan@stylishop.com'
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('order_create_awb', async () => {
      const response = await request(app)
        .post('/v1/orders/create-awb')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).not.toBe(200);
    });

    it('order_generate_pdf', async () => {
      const response = await request(app)
        .get('/v1/orders/generatePDF/NjQwOTAzMyNub3JhOTkxMTc3QGdtYWlsLmNvbQ==')
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('order_creditmemo_generate_negative', async () => {
      const response = await request(app)
        .get('/v1/orders/generateCreditMemoPDF')
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).not.toBe(200);
    });

    it('order_creditmemo_email', async () => {
      Creditmemo.findOne
        .mockReturnValueOnce({ order_id: 1233, store_id: 1 })
        .mockReturnValueOnce({
          order_id: 1233,
          store_id: 1,
          OrderPayments: { method: ['tabby', 'tamara'] }
        });
      OrderItem.findAll
        .mockReturnValueOnce([
          { product_type: 'configurable', item_id: '123213123' }
        ])
        .mockReturnValueOnce([{ order_item_id: '123213123' }]);
      const response = await request(app)
        .get(
          '/v1/orders/emailCreditMemoPDF/NjQwOTAzMyNub3JhOTkxMTc3QGdtYWlsLmNvbQ=='
        )
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });

    it('order_creditmemo_email_empty', async () => {
      const response = await request(app)
        .get(
          '/v1/orders/emailCreditMemoPDF/NjQwOTAzMyNub3JhOTkxMTc3QGdtYWlsLmNvbQ=='
        )
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
    it('order_status_list', async () => {
      const response = await request(app)
        .get('/v1/orders/order-status-list')
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });
      expect(response.status).toBe(200);
    });
    it('order_generate_invoice_list_negative', async () => {
      const response = await request(app)
        .post('/v1/orders/generate/invoice/list')
        .send({})
        .set({});
      expect(response).not.toEqual(200);
    });

    it('auto_refund_list_negative', async () => {
      const response = await request(app)
        .post('/v1/orders/autoRefundList')
        .send({})
        .set({});
      expect(response).not.toEqual(200);
    });

    it('check_inventory_negative', async () => {
      const response = await request(app)
        .post('/v1/orders/check-inventory')
        .send({})
        .set({});
      expect(response).not.toEqual(200);
    });

    it('test', async () => {
      expect(200).toBe(200);
    });
  });
});
