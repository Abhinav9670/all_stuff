// Mock dotenv-expand to prevent recursion
jest.mock('dotenv-expand', () => jest.fn((env) => env));

// Mock config
jest.mock('../../src/config/config', () => ({
  MONGODB_URL: 'mongodb://localhost:27017',
  MONGODB_DB_NAME: 'test_db'
}));

// Mock Redis
jest.mock('redis', () => ({
  createClient: jest.fn().mockReturnValue({
    connect: jest.fn().mockResolvedValue(),
    on: jest.fn(),
    get: jest.fn(),
    set: jest.fn()
  })
}));

// Mock Kafka
jest.mock('../../src/kafka', () => ({
  producer: {
    send: jest.fn().mockResolvedValue()
  }
}));

// Mock MongoDB
jest.mock('mongodb', () => ({
  MongoClient: {
    connect: jest.fn().mockResolvedValue({
      db: jest.fn().mockReturnValue({
        collection: jest.fn().mockReturnValue({
          findOne: jest.fn(),
          find: jest.fn()
        })
      })
    })
  }
}));

// Mock axios
jest.mock('axios', () => ({
  post: jest.fn().mockResolvedValue({ data: { success: true } })
}));

// Mock dependencies
jest.mock('../../src/shukran/action', () => ({
  shukranTransactionApi: jest.fn().mockResolvedValue({ data: { success: true } }),
  generateRandomNumber: jest.fn().mockResolvedValue('RANDOM123'),
  getDiscountAmount: jest.fn().mockResolvedValue(0)
}));
jest.mock('../../src/helpers/order');
jest.mock('../../src/utils/mongoInit');

const { revertFailedOrderShukranTransaction } = require('../../src/shukran/revertShukranCalls');
const { shukranTransactionApi } = require('../../src/shukran/action');
const orderObj = require('../../src/helpers/order');
const mongoUtil = require('../../src/utils/mongoInit');

// Mock global.logError and global.logInfo
global.logError = jest.fn();
global.logInfo = jest.fn();

describe('revertShukranCalls', () => {
  let mockOrder;
  let mockDb;

  beforeEach(() => {
    // Reset all mocks
    jest.clearAllMocks();

    // Setup mock order data
    mockOrder = {
      increment_id: '123456',
      store_id: '1',
      subSales: {
        shukran_linked: true,
        shukran_card_number: '1234567890',
        customer_profile_id: 'CUST123',
        quote_id: 'QUOTE123',
        shukran_tenders: JSON.stringify([
          {
            TenderAmount: 100,
            TenderType: 'CASH'
          }
        ])
      },
      OrderItems: [
        {
          product_type: 'simple',
          sku: 'SKU123',
          qty_ordered: '2',
          qty_canceled: '0',
          original_price: '100',
          price_incl_tax: '90',
          discount_amount: '10',
          description: 'Test Product',
          name: 'Test Product Name',
          on_sale: true,
          shukran_l4_category: 'CAT123',
          order_id: 'ORDER123',
          parent_item_id: 'PARENT123'
        }
      ],
      order_currency_code: 'AED',
      created_at: '2024-03-20T00:00:00Z',
      createdAt: '2024-03-20T00:00:00Z',
      cash_on_delivery_fee: 0
    };

    // Setup mock DB
    mockDb = {
      collection: jest.fn().mockReturnValue({
        findOne: jest.fn(),
        find: jest.fn()
      })
    };

    // Setup global config
    global.config = {
      shukranEnrollmentCommonCode: 'SEC',
      shukarnEnrollmentStoreCode: 'SES',
      shukranEnrollmentConceptCode: 'SCO',
      shukranProgramCode: 'SPR',
      shukranItemTypeCode: 'SIT',
      shukranTransactionTypeCode: 'STT',
      shukranCodItemName: 'COD',
      shukranBasicItemName: 'BASIC',
      environments: [
        {
          stores: [
            {
              storeId: '1',
              shukranStoreCode: 'SSC',
              invoiceTerritory: 'UAE',
              taxPercentage: '5'
            }
          ]
        }
      ]
    };

    // Setup mock implementations
    orderObj.getOrder.mockResolvedValue(mockOrder);
    if (global.mockGetDbFn) {
      global.mockGetDbFn.mockReturnValue(mockDb);
    } else {
      mongoUtil.getDb = jest.fn(() => mockDb);
    }
    shukranTransactionApi.mockResolvedValue({ data: { success: true } });
  });

  it('should successfully revert a Shukran transaction', async () => {
    const result = await revertFailedOrderShukranTransaction('123456');

    expect(orderObj.getOrder).toHaveBeenCalledWith({
      incrementId: '000123456',
      inclSubSales: true
    });

    expect(shukranTransactionApi).toHaveBeenCalledWith({
      payload: expect.objectContaining({
        ProfileId: 'CUST123',
        TransactionTypeCode: 'RT',
        CardNumber: '1234567890',
        CurrencyCode: 'AED',
        TransactionNumber: expect.stringContaining('RXX'),
        OriginalTransactionNumber: expect.stringContaining('SEC123456'),
        TransactionDetails: expect.any(Array)
      })
    });

    expect(result).toEqual({ data: { success: true } });
  });

  it('should handle orders without Shukran details', async () => {
    mockOrder.subSales.shukran_linked = false;

    const result = await revertFailedOrderShukranTransaction('123456');

    expect(shukranTransactionApi).not.toHaveBeenCalled();
    expect(result).toEqual({ message: 'Pr Updated Successfully' });
  });

  it('should handle COD orders correctly', async () => {
    mockOrder.cash_on_delivery_fee = 10;

    const result = await revertFailedOrderShukranTransaction('123456');

    const taxFactor = 1.05;
    const codNetPrice = parseFloat((10 / taxFactor).toFixed(2));

    expect(shukranTransactionApi).toHaveBeenCalledWith({
      payload: expect.objectContaining({
        ProfileId: 'CUST123',
        CardNumber: '1234567890',
        TransactionTypeCode: 'RT',
        CurrencyCode: 'AED',
        TransactionDateTime: '2024-03-20T00:00:00Z',
        StoreCode: 'SCOSESSSC',
        TransactionNumber: 'RXXSEC123456',
        ProgramCode: 'SPR',
        OriginalTransactionNumber: 'SEC123456',
        OriginalTransactionDateTime: '2024-03-20T00:00:00Z',
        OriginalStoreCode: 'SCOSESSSC',
        TransactionDetails: expect.arrayContaining([
          expect.objectContaining({
            ItemDescription: '',
            DollarValueNet: -codNetPrice,
            Quantity: 1,
            ItemNumberTypeCode: 'SIT',
            FulfillStoreCode: 'SCOSESSSC',
            TransactionDateTime: '2024-03-20T00:00:00Z',
            JsonExternalData: expect.objectContaining({
              ItemDescription: 'Cash On Delivery',
              ConceptCode: 'SCO',
              SaleFlag: 'N'
            })
          })
        ])
      })
    });
  });

  it('should handle orders with 6-digit increment IDs', async () => {
    const shortIncrementId = '123456';

    const result = await revertFailedOrderShukranTransaction(shortIncrementId);

    expect(orderObj.getOrder).toHaveBeenCalledWith({
      incrementId: '000123456',
      inclSubSales: true
    });
  });

  it('should handle database errors gracefully', async () => {
    orderObj.getOrder.mockRejectedValue(new Error('Database error'));

    await expect(revertFailedOrderShukranTransaction('123456')).rejects.toThrow('Database error');
    expect(global.logError).toHaveBeenCalled();
  });

  it('should handle API errors gracefully and log them', async () => {
    const apiError = new Error('API error');
    apiError.response = {
      data: { message: 'Invalid transaction' },
      status: 400
    };
    shukranTransactionApi.mockRejectedValueOnce(apiError);

    await expect(revertFailedOrderShukranTransaction('123456')).rejects.toThrow('API error');

    expect(global.logError).toHaveBeenCalledWith(
      'shukran PR log error',
      JSON.stringify(apiError)
    );
  });

  it('should handle missing order data gracefully', async () => {
    orderObj.getOrder.mockResolvedValueOnce(null);

    const result = await revertFailedOrderShukranTransaction('123456');
    expect(result).toEqual({ message: 'Pr Updated Successfully' });
    expect(shukranTransactionApi).not.toHaveBeenCalled();
  });

  it('should handle orders with multiple tenders', async () => {
    mockOrder.subSales.shukran_tenders = JSON.stringify([
      { TenderAmount: 50, TenderType: 'CASH' },
      { TenderAmount: 50, TenderType: 'CARD' }
    ]);

    const result = await revertFailedOrderShukranTransaction('123456');

    expect(shukranTransactionApi).toHaveBeenCalledWith({
      payload: expect.objectContaining({
        Tenders: expect.arrayContaining([
          expect.objectContaining({ TenderAmount: -50 }),
          expect.objectContaining({ TenderAmount: -50 })
        ])
      })
    });
  });

  it('should handle cross-border orders', async () => {
    mockOrder.subSales.cross_border = true;

    const result = await revertFailedOrderShukranTransaction('123456');

    expect(shukranTransactionApi).toHaveBeenCalledWith({
      payload: expect.objectContaining({
        JsonExternalData: expect.objectContaining({
          CrossBorderFlag: 'Y'
        })
      })
    });
  });


});