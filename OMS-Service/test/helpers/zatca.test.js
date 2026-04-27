const zatca = require('../../src/helpers/zatca');

jest.mock('../../src/utils', () => ({
  getNumericValue: jest.fn(val => val),
}));

describe('zatca.js', () => {
  describe('roundingTo2Decimal', () => {
    it('should round to 2 decimals', () => {
      expect(zatca.roundingTo2Decimal(1.234)).toBe(1.23);
      expect(zatca.roundingTo2Decimal(1.235)).toBe(1.24);
      expect(zatca.roundingTo2Decimal(1)).toBe(1);
    });
  });

  describe('getZatcaItemDetails', () => {
    const baseItemObject = {
      qty: 2,
      price_incl_tax: 90,
      discount_amount: 10,
      voucher_amount: 2
    };
    const baseOrderItem = {
      original_price: 100,
      tax_percent: 10
    };

    it('should calculate details for normal item', () => {
      const result = zatca.getZatcaItemDetails({
        itemObject: baseItemObject,
        orderItem: baseOrderItem,
        status: 'DELIVERED',
        paymentMethod: 'PREPAID'
      });
      expect(result).toHaveProperty('taxPercent');
      expect(result).toHaveProperty('unitPriceInclTax');
      expect(result).toHaveProperty('unitPriceExclTax');
      expect(result).toHaveProperty('discountProductLevelExclTax');
      expect(result).toHaveProperty('discountCouponExclTaxProduct');
      expect(result).toHaveProperty('discountExclTaxProduct');
      expect(result).toHaveProperty('taxablePriceProduct');
      expect(result).toHaveProperty('totalTaxAmountProduct');
      expect(result).toHaveProperty('totalPriceInclTaxProduct');
      expect(result).toHaveProperty('subTotalExclTax');
    });

    it('should handle RTO status and non-COD', () => {
      const result = zatca.getZatcaItemDetails({
        itemObject: { ...baseItemObject, discount_amount: 20 },
        orderItem: baseOrderItem,
        status: 'RTO',
        paymentMethod: 'PREPAID'
      });
      expect(result).toHaveProperty('discountCouponExclTaxProduct', '0.00');
    });

    it('should handle RTO status and COD', () => {
      const result = zatca.getZatcaItemDetails({
        itemObject: { ...baseItemObject, discount_amount: 20 },
        orderItem: baseOrderItem,
        status: 'RTO',
        paymentMethod: 'CASHONDELIVERY'
      });
      expect(result).toHaveProperty('discountCouponExclTaxProduct');
    });

    it('should handle zero/undefined values', () => {
      const result = zatca.getZatcaItemDetails({
        itemObject: { qty: 0, price_incl_tax: 0, discount_amount: 0, voucher_amount: 0 },
        orderItem: { original_price: 0, tax_percent: 0 },
        status: '',
        paymentMethod: ''
      });
      expect(result).toBeDefined();
    });
  });

  describe('getZatcaTotals', () => {
    it('should sum up item details', () => {
      const items = [
        {
          taxPercent: 10,
          subTotalExclTax: 100,
          discountProductLevelExclTax: 10,
          discountCouponExclTaxProduct: 5,
          taxablePriceProduct: 85
        },
        {
          taxPercent: 10,
          subTotalExclTax: 200,
          discountProductLevelExclTax: 20,
          discountCouponExclTaxProduct: 10,
          taxablePriceProduct: 170
        }
      ];
      const result = zatca.getZatcaTotals(items);
      expect(result).toHaveProperty('zatcaSubtotalExclTax', 300);
      expect(result).toHaveProperty('zatcaProductLevelDiscountExclTax', 30);
      expect(result).toHaveProperty('zatcaCouponDiscountExclTax', 15);
      expect(result).toHaveProperty('zatcaTaxablePrice', 255);
      expect(result).toHaveProperty('zatcaTaxAmount');
      expect(result).toHaveProperty('zatcaTotalInclTax');
    });

    it('should handle empty items', () => {
      const result = zatca.getZatcaTotals([]);
      expect(result).toBeDefined();
    });
  });
});
