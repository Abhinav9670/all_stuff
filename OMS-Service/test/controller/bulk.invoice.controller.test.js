/* eslint-disable max-lines-per-function */
/* eslint-disable no-undef */

// Mock environment variables
process.env.NODE_ENV = 'test';
process.env.MONGODB_URL = 'mongodb://localhost:27017/test';
process.env.AUTH_INTERNAL_HEADER_BEARER_TOKEN = 'test-token';

// Mock key dependencies
jest.mock('../../src/helpers/invoice', () => ({
  prepareInvoice: jest.fn().mockResolvedValue({ storeId: '1' }),
  findOrders: jest.fn().mockResolvedValue([{ orderId: '123' }]),
  storeInvoice: jest.fn().mockResolvedValue(true),
  findAllInvoice: jest.fn().mockResolvedValue([{
    id: 1,
    country: 'KSA',
    bucket_path: 'test.zip'
  }]),
  getInvoiceTemplateSource: jest.fn().mockReturnValue('<html>{{data.test}}</html>')
}));

jest.mock('playwright', () => ({
  chromium: {
    launch: jest.fn().mockImplementation(() => ({
      newPage: jest.fn().mockResolvedValue({
        setContent: jest.fn(),
        pdf: jest.fn(),
        close: jest.fn()
      }),
      close: jest.fn()
    }))
  }
}));

// Mock fs with all required functions
const mockFs = {
  mkdirSync: jest.fn(),
  readFileSync: jest.fn().mockReturnValue(Buffer.from('test')),
  unlink: jest.fn().mockImplementation((path, cb) => cb()),
  existsSync: jest.fn().mockReturnValue(false),
  promises: {
    access: jest.fn().mockResolvedValue(true)
  },
  readdirSync: jest.fn().mockReturnValue([])
};

jest.mock('fs', () => mockFs);

// Mock firebase-admin
jest.mock('firebase-admin', () => ({
  initializeApp: jest.fn().mockReturnValue({
    auth: jest.fn().mockReturnValue({})
  }),
  credential: {
    applicationDefault: jest.fn().mockReturnValue({})
  }
}));

// Mock mongoose and its plugins before requiring any other modules
jest.mock('mongoose', () => ({
  Schema: jest.fn(),
  model: jest.fn(),
  plugin: jest.fn()
}));

jest.mock('../../src/models/plugins/toJSON.plugin', () => jest.fn());
jest.mock('../../src/models/plugins/paginate.plugin', () => jest.fn());

const request = require('supertest');
const express = require('express');
const moment = require('moment');
const { ORDER_INVOICE_ENDPOINT } = require('../../src/constants/javaEndpoints');

// Mock catchAsync to pass through the function
jest.mock('../../src/utils/catchAsync', () => fn => async (req, res, next) => {
  try {
    await fn(req, res, next);
  } catch (err) {
    res.status(500).json({ status: false, error: err.message });
  }
});

// Mock the required modules
const mockInvoiceHelpers = {
  findOrders: jest.fn(),
  prepareInvoice: jest.fn(),
  storeInvoice: jest.fn(),
  findAllInvoice: jest.fn(),
  getInvoiceTemplateSource: jest.fn()
};

jest.mock('../../src/helpers/invoice', () => mockInvoiceHelpers);

jest.mock('../../src/utils/config', () => ({
  getStoreConfigs: jest.fn().mockReturnValue([{ taxPercentage: 15 }])
}));

jest.mock('../../src/config/googleStorage', () => ({
  uploadFile: jest.fn()
}));

jest.mock('../../src/services/creditmemo.service', () => ({
  setQrCode: jest.fn()
}));

jest.mock('axios');
jest.mock('fs');
jest.mock('adm-zip');
jest.mock('handlebars');
jest.mock('playwright');

const fs = require('fs');
const AdmZip = require('adm-zip');
const handlebars = require('handlebars');
const axios = require('axios');
const playwright = require('playwright');
const { uploadFile } = require('../../src/config/googleStorage');

// Create a minimal express app for testing
const app = express();
app.use(express.json());

// Import the controller functions directly
const { generateInvoice, findAllGeneratedInvoice } = require('../../src/controllers/bulk.invoice.controller');

// Set up routes
app.post('/v1/orders/generate/invoice', generateInvoice);
app.get('/v1/orders/generated/invoices', findAllGeneratedInvoice);

describe('Bulk Invoice Controller', () => {
  const mockHeaders = {
    authorization: 'Bearer test-token',
    'X-Header-Token': 'test-header-token'
  };

  beforeEach(() => {
    jest.clearAllMocks();

    // Reset mock implementations
    mockInvoiceHelpers.findOrders.mockResolvedValue([
      { orderId: '123', customerEmail: 'test@example.com' }
    ]);
    mockInvoiceHelpers.prepareInvoice.mockResolvedValue({
      zatcaStatus: true,
      storeId: '1',
      orderIncrementId: 'TEST123',
      zatcaQrCode: 'qr-code-data'
    });
    mockInvoiceHelpers.storeInvoice.mockResolvedValue(true);
    mockInvoiceHelpers.findAllInvoice.mockResolvedValue([
      {
        id: 1,
        country: 'KSA',
        from_date: '2024-05-01',
        to_date: '2024-05-31',
        bucket_path: 'invoices/test.zip',
        created_at: '2024-05-01T10:00:00Z'
      }
    ]);
    mockInvoiceHelpers.getInvoiceTemplateSource.mockReturnValue('<html>{{data.test}}</html>');

    // Mock AdmZip
    AdmZip.mockImplementation(() => ({
      addFile: jest.fn(),
      writeZip: jest.fn()
    }));

    // Mock handlebars
    handlebars.compile = jest.fn().mockReturnValue(() => '<html>test</html>');

    // Mock axios
    axios.post.mockResolvedValue({
      data: {
        status: true,
        response: {
          totals: {
            invoicedAmount: 100,
            taxAmount: 15
          },
          storeId: '1',
          updatedAt: '2024-05-01T10:00:00Z',
          warehouseId: 'WH1'
        }
      }
    });

    // Mock playwright
    const mockPage = {
      setContent: jest.fn().mockResolvedValue(undefined),
      pdf: jest.fn().mockResolvedValue(undefined),
      close: jest.fn().mockResolvedValue(undefined)
    };
    const mockBrowser = {
      newPage: jest.fn().mockResolvedValue(mockPage),
      close: jest.fn().mockResolvedValue(undefined)
    };
    playwright.chromium.launch = jest.fn().mockResolvedValue(mockBrowser);

    // Mock uploadFile
    uploadFile.mockResolvedValue({ success: true, fileName: 'invoices/test.zip' });

    // Set up global config
    global.config = {
      environments: [{
        stores: [{
          storeId: '1',
          websiteCode: 'TEST'
        }]
      }]
    };
    global.logError = jest.fn();
  });

  describe('POST /v1/orders/generate/invoice', () => {
    const validPayload = {
      fromDate: '2024-05-01',
      toDate: '2024-05-31',
      country: 'KSA'
    };

    it('should generate invoice successfully', async () => {
      const response = await request(app)
        .post('/v1/orders/generate/invoice')
        .send(validPayload)
        .set(mockHeaders);

      // Since buildInvoiceData is called asynchronously, we need to wait a bit
      await new Promise(resolve => setTimeout(resolve, 100));

      // Verify response
      expect(response.status).toBe(200);
      expect(response.body).toEqual({ status: true });

      // Verify directory creation
      expect(fs.mkdirSync).toHaveBeenCalledWith('./downloads', { recursive: true });

      // Verify order fetching
      expect(mockInvoiceHelpers.findOrders).toHaveBeenCalledWith(validPayload);

      // Verify invoice data fetching
      expect(axios.post).toHaveBeenCalledWith(
        ORDER_INVOICE_ENDPOINT,
        { orderId: '123', customerEmail: 'test@example.com' },
        expect.any(Object)
      );

      // Verify PDF generation
      expect(playwright.chromium.launch).toHaveBeenCalled();

      // Verify file upload
      expect(uploadFile).toHaveBeenCalledWith(
        expect.objectContaining({
          name: expect.stringContaining('_'),
          tempFilePath: expect.stringContaining('.zip')
        }),
        'invoice'
      );

      // Verify cleanup
      expect(fs.unlink).toHaveBeenCalled();
    });

    it('should handle empty orders gracefully', async () => {
      mockInvoiceHelpers.findOrders.mockResolvedValueOnce([]);

      const response = await request(app)
        .post('/v1/orders/generate/invoice')
        .send(validPayload)
        .set(mockHeaders);

      // Since buildInvoiceData is called asynchronously, we need to wait a bit
      await new Promise(resolve => setTimeout(resolve, 100));

      expect(response.status).toBe(200);
      expect(response.body).toEqual({ status: true });
      expect(playwright.chromium.launch).toHaveBeenCalled(); // Controller still launches browser
    });

  });

  describe('GET /v1/orders/generated/invoices', () => {
    it('should return sorted list of generated invoices', async () => {
      mockInvoiceHelpers.findAllInvoice.mockResolvedValueOnce([
        {
          id: 1,
          created_at: '2024-05-02T10:00:00Z',
          country: 'KSA'
        },
        {
          id: 2,
          created_at: '2024-05-01T10:00:00Z',
          country: 'KSA'
        }
      ]);

      const response = await request(app)
        .get('/v1/orders/generated/invoices')
        .set(mockHeaders);

      expect(response.status).toBe(200);
      expect(response.body.status).toBe(true);
      expect(response.body.response).toHaveLength(2);
      expect(response.body.response[0].id).toBe(1); // Most recent first
    });

    it('should handle empty invoice list', async () => {
      mockInvoiceHelpers.findAllInvoice.mockResolvedValueOnce([]);

      const response = await request(app)
        .get('/v1/orders/generated/invoices')
        .set(mockHeaders);

      expect(response.status).toBe(200);
      expect(response.body.status).toBe(true);
      expect(response.body.response).toHaveLength(0);
    });

    it('should handle database errors', async () => {
      const error = new Error('Database error');
      mockInvoiceHelpers.findAllInvoice.mockRejectedValueOnce(error);

      const response = await request(app)
        .get('/v1/orders/generated/invoices')
        .set(mockHeaders);

      expect(response.status).toBe(500);
      expect(response.body).toEqual({
        status: false,
        error: 'Database error'
      });
    });
  });
});