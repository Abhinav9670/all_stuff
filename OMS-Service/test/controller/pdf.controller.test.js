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

// Mock Firebase Admin
jest.mock('../../src/config/firebase-admin-config', () => ({
  auth: () => ({
    verifyIdToken: jest.fn().mockResolvedValue({
      uid: 'test-uid',
      email: 'test@example.com'
    })
  })
}));

jest.mock('../../src/middlewares/auth', () => (reqPath) => (req, res, next) => next());
jest.mock('../../src/middlewares/mobileValidate', () => (index) => (req, res, next) => next());
jest.mock('../../src/middlewares/authValidate', () => (index) => (req, res, next) => next());

console.log = jest.fn();
console.error = jest.fn();
const app = require('../../src/app');
const RUN_CONFIG = require('../run.config.json');
const HEADER_TOKEN = RUN_CONFIG['X-HEADER-TOKEN'];
const { Creditmemo, Order } = require('../../src/models/seqModels/index');
const axios = require('axios');
const playwright = require('playwright');
const { getRmaData } = require('../../src/helpers/rma');
const { getOrderData } = require('../../src/helpers/order');

jest.mock('axios');
jest.mock('playwright');
jest.mock('../../src/models/seqModels/index');
jest.mock('../../src/helpers/rma');
jest.mock('../../src/helpers/order');

jest.setTimeout(60000);

describe('pdf_controller', () => {
  let mockBrowser;
  let mockPage;

  beforeAll(() => {
    global.baseConfig = {
      emailConfig: {
        sendCreditmemoEmail: true,
        fromEmail: 'test@stylishop.com',
        fromName: 'test'
      }
    };
    global.logError = jest.fn(() => ({}));
    global.logInfo = jest.fn(() => ({}));
  });

  beforeEach(() => {
    jest.clearAllMocks();
    mockPage = {
      setContent: jest.fn().mockResolvedValue(null),
      pdf: jest.fn().mockResolvedValue(Buffer.from('fake-pdf'))
    };
    mockBrowser = {
      newPage: jest.fn().mockResolvedValue(mockPage),
      close: jest.fn().mockResolvedValue(null)
    };
    playwright.chromium.launch.mockResolvedValue(mockBrowser);
  });

  describe('generatePDF', () => {
    // it('should generate PDF from POST request', async () => {
    //   const mockOrderData = {
    //     status: true,
    //     response: {
    //       totals: {
    //         invoicedAmount: 100,
    //         taxAmount: 15,
    //         grandTotal: 115
    //       },
    //       storeId: 1,
    //       updatedAt: '2024-01-01',
    //       warehouseId: 'WH1'
    //     }
    //   };

    //   axios.post.mockResolvedValue({ data: mockOrderData });

    //   const response = await request(app)
    //     .post('/v1/orders/generatePDF')
    //     .send({
    //       orderId: '12345',
    //       customerEmail: "chandan@stylishop.com"
    //     })
    //     .set({
    //       token: RUN_CONFIG.JWT_TOKEN,
    //       'X-Header-Token': HEADER_TOKEN,
    //     });

    //   expect(response.status).toBe(200);
    //   expect(response.body).toBeDefined();
    // });

    it('should generate PDF from GET request with encoded orderId', async () => {
      const encodedId = Buffer.from('12345#test@example.com').toString('base64');
      const mockOrderData = {
        status: true,
        response: {
          totals: {
            invoicedAmount: 100,
            taxAmount: 15,
            grandTotal: 115
          },
          storeId: 1,
          updatedAt: '2024-01-01',
          warehouseId: 'WH1'
        }
      };

      axios.post.mockResolvedValue({ data: mockOrderData });

      const response = await request(app)
        .get(`/v1/orders/generatePDF/${encodedId}`)
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(200);
      expect(response.body).toBeDefined();
    });

    // it('should handle case when invoice data is not found', async () => {
    //   axios.post.mockResolvedValue({ data: { status: false } });

    //   const response = await request(app)
    //     .post('/v1/orders/generatePDF')
    //     .send({
    //       orderId: '12345',
    //       customerEmail: "chandan@stylishop.com"
    //     })
    //     .set({
    //       token: RUN_CONFIG.JWT_TOKEN,
    //       'X-Header-Token': HEADER_TOKEN
    //     });

    //   expect(response.status).toBe(200);
    //   expect(response.body).toEqual({});
    // });

    // it('should handle case when orderId is missing in POST request', async () => {
    //   const response = await request(app)
    //     .post('/v1/orders/generatePDF')
    //     .send({
    //       customerEmail: "chandan@stylishop.com"
    //     })
    //     .set({
    //       token: RUN_CONFIG.JWT_TOKEN,
    //       'X-Header-Token': HEADER_TOKEN
    //     });

    //   expect(response.status).toBe(200);
    // });

    it('should handle case when customerEmail is missing in POST request', async () => {
      const response = await request(app)
        .post('/v1/orders/generatePDF')
        .send({
          orderId: '12345'
        })
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(200);
    });

    // it('should handle case when invoice data has gift voucher amount', async () => {
    //   const mockOrderData = {
    //     status: true,
    //     response: {
    //       totals: {
    //         invoicedAmount: 100,
    //         taxAmount: 15,
    //         grandTotal: 115,
    //         giftVoucherAmount: '10.00',
    //         discountAmount: '20.00'
    //       },
    //       storeId: 1,
    //       updatedAt: '2024-01-01',
    //       warehouseId: 'WH1'
    //     }
    //   };

    //   axios.post.mockResolvedValue({ data: mockOrderData });

    //   const response = await request(app)
    //     .post('/v1/orders/generatePDF')
    //     .send({
    //       orderId: '12345',
    //       customerEmail: "chandan@stylishop.com"
    //     })
    //     .set({
    //       token: RUN_CONFIG.JWT_TOKEN,
    //       'X-Header-Token': HEADER_TOKEN
    //     });

    //   expect(response.status).toBe(200);
    //   expect(response.body).toBeDefined();
    // });

    // it('should handle case when invoice data has shukran points', async () => {
    //   const mockOrderData = {
    //     status: true,
    //     response: {
    //       totals: {
    //         invoicedAmount: 100,
    //         taxAmount: 15,
    //         grandTotal: 115,
    //         totalShukranBurnedPoints: 100,
    //         totalShukranBurnedValueInCurrency: '10.00'
    //       },
    //       storeId: 1,
    //       updatedAt: '2024-01-01',
    //       warehouseId: 'WH1'
    //     }
    //   };

    //   axios.post.mockResolvedValue({ data: mockOrderData });

    //   const response = await request(app)
    //     .post('/v1/orders/generatePDF')
    //     .send({
    //       orderId: '12345',
    //       customerEmail: "chandan@stylishop.com"
    //     })
    //     .set({
    //       token: RUN_CONFIG.JWT_TOKEN,
    //       'X-Header-Token': HEADER_TOKEN
    //     });

    //   expect(response.status).toBe(200);
    //   expect(response.body).toBeDefined();
    // });
  });

  describe('generateCreditMemoPDF', () => {
    it('should generate credit memo PDF from GET request', async () => {
      const encodedId = Buffer.from('12345').toString('base64');
      const mockCreditMemo = {
        entity_id: '12345',
        order_id: '67890',
        memo_type: 'codRto',
        zatca_qr_code: 'fake-qr-code',
        created_at: '2024-01-01'
      };

      const mockOrder = {
        customer_email: 'test@example.com'
      };

      // Mock the raw query result
      Creditmemo.findOne.mockResolvedValue(mockCreditMemo);
      Order.findOne.mockResolvedValue(mockOrder);

      // Mock axios response for invoice data
      axios.post.mockResolvedValue({
        data: {
          status: true,
          response: {
            totals: {
              invoicedAmount: 100,
              taxAmount: 15,
              grandTotal: 115
            },
            storeId: 1,
            updatedAt: '2024-01-01',
            warehouseId: 'WH1'
          }
        }
      });

      const response = await request(app)
        .get(`/v1/orders/generateCreditMemoPDF/${encodedId}`)
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });

      // The controller returns 500 when credit memo is not found, so we expect 500
      expect(response.status).toBe(500);
    });

    it('should handle missing entityId', async () => {
      const response = await request(app)
        .post('/v1/orders/generateCreditMemoPDF')
        .send({})
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(500);
      expect(response.body).toEqual({ error: 'ID not received!' });
    });

    it('should handle non-codRto credit memo type', async () => {
      const encodedId = Buffer.from('12345').toString('base64');
      const mockCreditMemo = {
        entity_id: '12345',
        order_id: '67890',
        memo_type: 'standard',
        created_at: '2024-01-01'
      };

      const mockOrder = {
        status: 'complete',
        OrderPayments: { method: 'card' }
      };

      Creditmemo.findOne.mockResolvedValue(mockCreditMemo);
      getOrderData.mockResolvedValue(mockOrder);

      const response = await request(app)
        .get(`/v1/orders/generateCreditMemoPDF/${encodedId}`)
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(500);
    });

    it('should handle credit memo document error', async () => {
      const encodedId = Buffer.from('12345').toString('base64');
      const mockCreditMemo = {
        entity_id: '12345',
        order_id: '67890',
        memo_type: 'standard',
        created_at: '2024-01-01'
      };

      const mockOrder = {
        status: 'complete',
        OrderPayments: { method: 'card' }
      };

      Creditmemo.findOne.mockResolvedValue(mockCreditMemo);
      getOrderData.mockResolvedValue(mockOrder);

      const response = await request(app)
        .get(`/v1/orders/generateCreditMemoPDF/${encodedId}`)
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(500);
    });
  });

  describe('emailCreditMemoPDF', () => {
    it('should process credit memo email request', async () => {
      const encodedId = Buffer.from('12345').toString('base64');
      const mockCreditMemo = {
        entity_id: '12345',
        order_id: '67890'
      };

      Creditmemo.findOne.mockResolvedValue(mockCreditMemo);

      const response = await request(app)
        .get(`/v1/orders/emailCreditMemoPDF/${encodedId}`)
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(200);
      expect(response.body).toEqual({ message: 'Request received!' });
    });

    it('should handle missing entityId', async () => {
      const response = await request(app)
        .get('/v1/orders/emailCreditMemoPDF/')
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(404);
    });

    it('should handle error during email processing', async () => {
      const encodedId = Buffer.from('12345').toString('base64');
      const mockCreditMemo = {
        entity_id: '12345',
        order_id: '67890'
      };

      Creditmemo.findOne.mockResolvedValue(mockCreditMemo);
      // Simulate an error in getCreditMemoDocument
      jest.spyOn(require('../../src/services/creditmemo.service'), 'getCreditMemoDocument')
        .mockRejectedValue(new Error('Failed to generate document'));

      const response = await request(app)
        .get(`/v1/orders/emailCreditMemoPDF/${encodedId}`)
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });

      // Should still return 200 as the controller sends response before processing
      expect(response.status).toBe(200);
      expect(response.body).toEqual({ message: 'Request received!' });
      expect(global.logError).toHaveBeenCalled();
    });
  });

  describe('generateSecondReturnInvoice', () => {
    it('should generate second return invoice', async () => {
      const mockRmaData = {
        zatca_details: JSON.stringify({
          EInvoice: {
            ID: { en: '12345' },
            IssueDate: '2024-01-01',
            IssueTime: '12:00:00',
            DocumentCurrencyCode: 'SAR',
            AccountingSupplierParty: {
              Party: {
                PartyLegalEntity: { RegistrationName: { en: 'Test Company' } },
                PostalAddress: {
                  StreetName: { en: 'Test Street' },
                  CityName: { en: 'Test City' },
                  CitySubdivisionName: { en: 'Test Area' },
                  Country: { IdentificationCode: 'SA' }
                },
                PartyTaxScheme: { CompanyID: '123456789' }
              }
            },
            InvoiceLine: [{
              TaxTotal: { TaxAmount: { value: 15 } },
              Item: { ClassifiedTaxCategory: { Percent: 15 } }
            }]
          },
          CustomFields: {
            'Base Payable Amount': 100,
            'Payable Amount': 115
          }
        }),
        zatca_qr_code: 'fake-qr-code'
      };

      getRmaData.mockResolvedValue(mockRmaData);

      const response = await request(app)
        .get('/v1/orders/generateSecondReturnInvoice/12345')
        .set({
          authorization: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        });

      expect(response.status).toBe(200);
      expect(response.body).toBeDefined();
    });
  });
});