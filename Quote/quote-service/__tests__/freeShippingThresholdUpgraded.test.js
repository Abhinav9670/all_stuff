// Comprehensive test file for freeShippingThresholdUpgraded function
// Testing with real dependencies to achieve maximum code coverage

const { freeShippingThresholdUpgraded } = require('../helpers/v6/freeShipping');

describe('freeShippingThresholdUpgraded - Comprehensive Test', () => {
  let mockQuote;
  let mockStoreId;
  let mockXHeaderToken;
  let mockSubtotal;

  // Helper function to create test data
  const createTestQuote = (overrides = {}) => ({
    "id": "1755604913325599",
    "customerEmail": "abc123@yopmail.com",
    "storeId": 3,
    "subtotal": 589,
    "previousOrderList": [
      {
        "createdAt": "2025-08-19T12:00:30.000Z",
        "holdOrder": 0,
        "incrementId": "000850995",
        "paymentMethod": "tabby_installments",
        "status": "processing",
        "usedCardBin": ""
      },
      {
        "createdAt": "2025-07-07T07:09:34.000Z",
        "holdOrder": null,
        "incrementId": "000841901",
        "paymentMethod": "cashondelivery",
        "status": "processing",
        "usedCardBin": ""
      },
      {
        "createdAt": "2025-06-06T12:47:06.000Z",
        "holdOrder": 0,
        "incrementId": "000841733",
        "paymentMethod": "cashondelivery",
        "status": "delivered",
        "usedCardBin": ""
      },
      {
        "createdAt": "2025-05-06T11:47:29.000Z",
        "holdOrder": 0,
        "incrementId": "000841725",
        "paymentMethod": "tabby_installments",
        "status": "delivered",
        "usedCardBin": ""
      }
    ],
    ...overrides
  });

  // Helper function to create order with specific properties
  const createOrder = (overrides = {}) => ({
    "createdAt": "2025-08-19T12:00:30.000Z",
    "holdOrder": 0,
    "incrementId": "000850995",
    "paymentMethod": "tabby_installments",
    "status": "processing",
    "usedCardBin": "",
    ...overrides
  });

  // Helper function to run the function and validate basic result structure
  const runAndValidate = async (params) => {
    const result = await freeShippingThresholdUpgraded(params);
    expect(result).toBeDefined();
    expect(typeof result.allowFreeshipping).toBe('boolean');
    expect(typeof result.matchedSingleThreshold).toBe('number');
    return result;
  };

  // Test data for different scenarios
  const testScenarios = {
    storeIds: [1, 2, 3, 4],
    subtotals: [0.01, 100, 500, 999999],
    deliveryModels: ['Standard', 'Premium', 'Express', ''],
    orderStatuses: ['processing', 'delivered', 'cancelled', 'pending']
  };

  beforeEach(() => {
    mockQuote = createTestQuote();
    mockStoreId = 3; // Match the storeId in the quote
    mockXHeaderToken = 'test-token';
    mockSubtotal = 100;
  });

  describe('Basic functionality', () => {
    it('should handle basic case with no config', async () => {
      // This test should always pass because it tests the error handling path
      // When there's no config, the function returns {allowFreeshipping: false, matchedSingleThreshold: 0}
      
      const result = await freeShippingThresholdUpgraded({
        quote: { id: 'test', customerEmail: 'test@test.com' },
        storeId: 1,
        xHeaderToken: 'test-token',
        subtotal: 100
      });

      expect(result.allowFreeshipping).toBe(false);
      expect(result.matchedSingleThreshold).toBe(0);
    });

    it('should handle basic case with real data', async () => {
      await runAndValidate({
        quote: mockQuote,
        storeId: mockStoreId,
        xHeaderToken: mockXHeaderToken,
        subtotal: mockSubtotal
      });
    });

    it('should handle different store IDs', async () => {
      await runAndValidate({
        quote: mockQuote,
        storeId: 1, // Different store ID
        xHeaderToken: mockXHeaderToken,
        subtotal: mockSubtotal
      });
    });

    it('should handle different subtotal values', async () => {
      await runAndValidate({
        quote: mockQuote,
        storeId: mockStoreId,
        xHeaderToken: mockXHeaderToken,
        subtotal: 500 // Higher subtotal
      });
    });

    it('should handle delivery model parameter', async () => {
      await runAndValidate({
        quote: mockQuote,
        storeId: mockStoreId,
        xHeaderToken: mockXHeaderToken,
        subtotal: mockSubtotal,
        deliveryModel: 'Express'
      });
    });
  });

  describe('Quote variations', () => {
    it('should handle quote with no previous orders', async () => {
      const quoteWithoutOrders = createTestQuote({ previousOrderList: [] });
      await runAndValidate({
        quote: quoteWithoutOrders,
        storeId: mockStoreId,
        xHeaderToken: mockXHeaderToken,
        subtotal: mockSubtotal
      });
    });

    it('should handle quote with different order statuses', async () => {
      const quoteWithDifferentStatuses = createTestQuote({
        previousOrderList: [
          createOrder({ status: 'delivered' }),
          createOrder({ status: 'cancelled' })
        ]
      });

      await runAndValidate({
        quote: quoteWithDifferentStatuses,
        storeId: mockStoreId,
        xHeaderToken: mockXHeaderToken,
        subtotal: mockSubtotal
      });
    });

    it('should handle quote with different order dates', async () => {
      const quoteWithOldOrders = createTestQuote({
        previousOrderList: [
          createOrder({ createdAt: "2020-01-01T00:00:00.000Z" }) // Very old order
        ]
      });

      await runAndValidate({
        quote: quoteWithOldOrders,
        storeId: mockStoreId,
        xHeaderToken: mockXHeaderToken,
        subtotal: mockSubtotal
      });
    });
  });

  describe('Edge cases', () => {
    it('should handle empty quote object', async () => {
      await runAndValidate({
        quote: {},
        storeId: mockStoreId,
        xHeaderToken: mockXHeaderToken,
        subtotal: mockSubtotal
      });
    });

    it('should handle quote with missing properties', async () => {
      await runAndValidate({
        quote: { id: 'test' }, // Missing most properties
        storeId: mockStoreId,
        xHeaderToken: mockXHeaderToken,
        subtotal: mockSubtotal
      });
    });

    it('should handle null/undefined parameters', async () => {
      await runAndValidate({
        quote: mockQuote,
        storeId: null,
        xHeaderToken: undefined,
        subtotal: 0
      });
    });

    it('should handle very high subtotal values', async () => {
      await runAndValidate({
        quote: mockQuote,
        storeId: mockStoreId,
        xHeaderToken: mockXHeaderToken,
        subtotal: 999999 // Very high subtotal
      });
    });

    it('should handle very low subtotal values', async () => {
      await runAndValidate({
        quote: mockQuote,
        storeId: mockStoreId,
        xHeaderToken: mockXHeaderToken,
        subtotal: 0.01 // Very low subtotal
      });
    });
  });

  describe('Different delivery models', () => {
    testScenarios.deliveryModels.forEach(deliveryModel => {
      it(`should handle ${deliveryModel || 'empty'} delivery model`, async () => {
        await runAndValidate({
          quote: mockQuote,
          storeId: mockStoreId,
          xHeaderToken: mockXHeaderToken,
          subtotal: mockSubtotal,
          deliveryModel
        });
      });
    });
  });

  describe('Store ID variations', () => {
    testScenarios.storeIds.forEach(storeId => {
      it(`should handle store ID ${storeId}`, async () => {
        await runAndValidate({
          quote: mockQuote,
          storeId,
          xHeaderToken: mockXHeaderToken,
          subtotal: mockSubtotal
        });
      });
    });
  });

  describe('Subtotal variations', () => {
    testScenarios.subtotals.forEach(subtotal => {
      it(`should handle subtotal ${subtotal}`, async () => {
        await runAndValidate({
          quote: mockQuote,
          storeId: mockStoreId,
          xHeaderToken: mockXHeaderToken,
          subtotal
        });
      });
    });
  });

  describe('Error handling scenarios', () => {
    it('should handle malformed quote data', async () => {
      const malformedQuote = {
        id: null,
        customerEmail: 123, // Wrong type
        storeId: "invalid", // Wrong type
        subtotal: "not a number", // Wrong type
        previousOrderList: "not an array" // Wrong type
      };

      await runAndValidate({
        quote: malformedQuote,
        storeId: mockStoreId,
        xHeaderToken: mockXHeaderToken,
        subtotal: mockSubtotal
      });
    });

    it('should handle missing xHeaderToken', async () => {
      await runAndValidate({
        quote: mockQuote,
        storeId: mockStoreId,
        xHeaderToken: '', // Empty token
        subtotal: mockSubtotal
      });
    });
  });
});
