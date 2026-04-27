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
const app = require('../../src/app');
const {
  processCreditmemoEmail
} = require('../../src/services/creditmemo.service');
const mongoUtil = require('../../src/utils/mongoInit');
const RUN_CONFIG = require('../run.config.json');
const { insertOne } = require('../../src/utils/mongo');
const HEADER_TOKEN = RUN_CONFIG['X-HEADER-TOKEN'];
jest.mock('axios');
jest.mock('firebase-admin');
jest.mock('../../src/models/seqModels/index');
jest.setTimeout(80000);
jest.mock('../../src/utils/mongo', () => {
  return {
    insertOne: jest.fn(),
    updateOne: jest.fn()
  };
});

describe('services_methods', () => {
  beforeAll(async () => {
    await mongoUtil.connectToServer();
    global.logError = jest.fn(() => ({}));
    global.baseConfig = {
      emailConfig: {
        sendCreditmemoEmail: true
      }
    };
  });
  beforeEach(() => { });

  describe('services', () => {
    it('processCreditmemoEmail', async () => {
      await processCreditmemoEmail({
        document: {
          context: {
            data: {
              entity_id: 1352583,
              order_id: 7623773,
              subtotal_incl_tax: '64.17',
              subtotal: '64.17',
              grand_total: '0.00',
              adjustment: null,
              adjustment_negative: '0.0000',
              adjustment_positive: null,
              shipping_amount: null,
              tax_amount: '8.37',
              amstorecredit_amount: '64.17',
              discount_amount: null,
              cash_on_delivery_fee: null,
              order_currency_code: 'SAR',
              increment_id: '000096197',
              created_at: '2024-03-14T06:40:16.000Z',
              rma_number: '1264777',
              store_id: 1,
              eas_coins: null,
              eas_value_in_currency: null,
              eas_value_in_base_currency: null,
              zatca_qr_code: 'aa',
              zatca_status: 'PENDING',
              items: [
                {
                  entity_id: 7103025,
                  parent_id: 1352583,
                  sku: '700398533602',
                  name: 'Wide Neck Regular Fit Faux Fur Blouse',
                  price_incl_tax: '64.17',
                  qty: 1,
                  discount_amount: '0.0000',
                  tax_amount: '8.37',
                  row_total_incl_tax: '64.17',
                  order_item_id: 75806147,
                  voucher_amount: '0.00',
                  showTax: true,
                  isUaeOrKsa: true,
                  subTotal: '64.17',
                  taxPercent: '15.00',
                  unitPriceInclTax: '69.00',
                  unitPriceExclTax: '60.00',
                  discountProductLevelExclTax: '4.20',
                  discountCouponExclTaxProduct: '0.00',
                  discountExclTaxProduct: '4.20',
                  taxablePriceProduct: '55.80',
                  totalTaxAmountProduct: '8.37',
                  totalPriceInclTaxProduct: '64.17',
                  subTotalExclTax: '60.00'
                }
              ],
              comments: [
                {
                  entity_id: 1601995,
                  parent_id: 1352583,
                  comment: 'Amount refunded to styli credit :SAR 64.1700',
                  created_at: '2024-03-14T06:40:17.000Z'
                }
              ],
              voucherAmount: 0,
              orderCreatedAt: '2024-03-14T06:39:31.000Z',
              orderIncrementId: '000617583',
              shippingAddress: {
                entity_id: 15277051,
                parent_id: 7623773,
                address_type: 'shipping',
                area: 'Ad Difa',
                city: 'Riyadh',
                company: null,
                country_id: 'SA',
                customer_address_id: 3406301,
                email: 'test_1710398366427@gmail.com',
                firstname: 'TEST',
                lastname: 'TEST',
                middlename: null,
                region: 'Al-Riyadh',
                region_id: '600',
                street: '3.7.700 NM-201',
                suffix: null,
                telephone: '+966 763836653',
                nearest_landmark: '',
                postcode: '',
                firstName: 'TEST',
                lastName: 'TEST',
                streetAddress: '3.7.700 NM-201',
                mobileNumber: '+966 763836653',
                country: 'SA'
              },
              customerEmail: 'test_1710398366427@gmail.com',
              paymentInformation: {
                paymentMethod: 'cashondelivery',
                ccNumber: null,
                ccType: null
              },
              warehouseId: 110,
              rmaTrackingCode: null,
              rmaTrackingNumber: 'ALPR000000003541',
              invoice: {
                entity_id: 6641897,
                order_id: 7623773,
                increment_id: '006641897',
                created_at: '2024-03-14T06:39:46.000Z'
              },
              base_subtotal_incl_tax: '64.17',
              base_subtotal: '64.17',
              base_cash_on_delivery_fee: 0,
              base_grand_total: 0,
              base_shipping_amount: 0,
              base_adjustment: 0,
              base_discount_amount: 0,
              refundedAmount: '64.17',
              base_refundedAmount: '64.17',
              factor: 1,
              easCoins: null,
              easValueInCurrency: null,
              isUaeOrKsa: true,
              isUae: false,
              isKsa: true,
              isExcludeKsa: false,
              paymentModeEn: 'Cash on delivery',
              paymentModeAr: 'الدفع عند الإستلام',
              companyEmail: 'hello.ksa@stylishop.com',
              invoiceCreatedAt: '2024-03-14 09:39:46',
              createdAt: '2024-03-14 09:40:16',
              incrementId: '000096197',
              companyAddressEn: [
                'Retail Cart Trading Company Sole Person Company',
                'Riyadh Gallery Mall',
                'King Fahad Road',
                'P.O. Box 86003 Riyadh – 11622,',
                'KSA VAT Registration No: 310415422600003',
                'CRN: 1010589431'
              ],
              companyAddressAr: [
                'شركة ريتيل كارت للتجارة شركة شخص واحد',
                'مجمع رياض جاليري ',
                'طريق الملك فهد',
                'ص.ب 86003 ',
                'الرياض – 11622 ،المملكة العربية السعودية',
                'رقم التسجيل ضريبة القيمة المضافة : 310415422600003',
                'س.ت : 1010589431'
              ],
              roundingPayableAmount: 0,
              zatcaRefundedAmount: 0,
              base_zatcaRefundedAmount: 0,
              zatcaSubtotalExclTax: 60,
              zatcaProductLevelDiscountExclTax: 4.2,
              zatcaCouponDiscountExclTax: 0,
              zatcaTaxablePrice: 55.8,
              zatcaTaxAmount: 8.37,
              zatcaTotalInclTax: 64.17,
              showTax: true,
              currencyConversionRate: 1,
              creditheadingar: 'الإشعار الدائن الضريبي',
              creditheadingen: 'Tax Credit Note',
              taxPercent: '15%',
              grandTotalExcludingVat: '55.80',
              grandTotalIncludingVat: '64.17',
              currencyText: {
                en: '(Value in SAR)',
                ar: '(القيمة بالريال السعودي)'
              },
              headers: {
                unitPrice: {
                  en: 'Unit Price',
                  ar: 'سعر الوحده'
                },
                subTotal: {
                  en: 'Sub Total',
                  ar: 'المجموع الفرعي'
                },
                rowTotal: {
                  en: 'Row Total',
                  ar: 'المجموع الصافي'
                },
                invoiceTotal: {
                  en: 'Sub Total',
                  ar: 'المجموع الفرعي'
                },
                invoiceDiscount: {
                  en: 'Discount',
                  ar: 'تخفيض'
                },
                invoiceHeading: {
                  en: 'Tax Invoice',
                  ar: 'فاتورة ضريبية'
                },
                shippingAmount: {
                  en: 'Shipping & Handling',
                  ar: 'الشحن والتسليم'
                },
                codFee: {
                  en: 'COD Charges',
                  ar: 'تكلفة الدفع عند الإستلام'
                },
                currencyText: {
                  en: '(Value in undefined)',
                  ar: '(undefined القيمة بال)'
                },
                itemCode: {
                  en: 'Item code',
                  ar: 'رمز المنتج'
                },
                productName: {
                  en: 'Product name',
                  ar: 'اسم المنتج'
                },
                qty: {
                  en: 'Qty',
                  ar: 'الكمية'
                },
                unitPriceExclVat: {
                  en: 'Unit Price (excl VAT)',
                  ar: 'سعر الوحدة ( بدون  قيمة الضريبة المضافه )'
                },
                subtotalExclVat: {
                  en: 'Sub Total (excl VAT)',
                  ar: 'المجموع الفرعي ( بدون قيمة الضريبة المضافه )'
                },
                discountExclVat: {
                  en: 'Discount (excl VAT)',
                  ar: 'التخفيض ( بدون قيمة الضريبه المضافه )'
                },
                taxableAmount: {
                  en: 'Total Taxable Amount',
                  ar: 'مجموع المبلغ الخاضع للضريبة'
                },
                vat: {
                  en: 'VAT',
                  ar: ' الضريبه المضافه'
                },
                vatRate: {
                  en: 'VAT Rate',
                  ar: 'قيمة الضريبة'
                },
                vatAmount: {
                  en: 'VAT Amount',
                  ar: 'قيمة الضريبه المضافه'
                },
                totalPayable: {
                  en: 'Total payable',
                  ar: 'مجموع المبلغ المستحق'
                },
                settlement: {
                  en: 'Settlement',
                  ar: 'التسوية'
                },
                totalExclVat: {
                  en: 'Total excl vat',
                  ar: 'المجموع غير شاملاً قيمة الضريبة المضافه'
                },
                payableRounding: {
                  en: 'Payable Rounding Amount',
                  ar: 'مجموع المبلغ المستحق بعد التقريب'
                },
                giftVoucher: {
                  en: 'Gift Voucher',
                  ar: 'قسيمة شراء'
                },
                styliCoins: {
                  en: 'Styli Coins',
                  ar: 'نقاط ستايلي'
                },
                styliCredit: {
                  en: 'STYLI Credits',
                  ar: 'رصيد ستايلي'
                },
                totalNetPayable: {
                  en: 'Total Payable amount (Incl VAT)',
                  ar: 'مجموع المبلغ المستحق ( شاملا ضريبة القيمة المضافه )'
                },
                importFee: {
                  en: 'Import Fee',
                  ar: 'رسوم التوريد'
                },
                grandTotalExclVAT: {
                  en: 'Grand Total(Excl VAT)',
                  ar: 'المجموع النهائي ( غير شاملاً قيمة الضريبة المضافه )'
                },
                codFeeExclVat: {
                  en: 'Cash on delivery Fee',
                  ar: 'رسوم الدفع عند الإستلام'
                },
                shippingAmountExclVat: {
                  en: 'Shipping & Handling',
                  ar: 'قيمة الشحن والتسليم'
                },
                refundedAmount: {
                  en: 'Total Refundable amount (Incl VAT)',
                  ar: 'مجموع المبلغ المسترد ( شاملا ضريبة القيمة المضافة )'
                },
                exchangeRate: {
                  en: 'Exchange Rate',
                  ar: 'سعر الصرف'
                },
                refundReason: {
                  en: 'Reason for Issuing Note: Refund',
                  ar: 'سبب إصدار الملاحظة: استرداد المبلغ'
                },
                supplyDate: {
                  en: 'Supply Date',
                  ar: 'تاريخ التوريد'
                },
                originalSupplyDate: {
                  en: 'Original Supply date',
                  ar: 'تاريخ التوريد الأصلي'
                },
                isUaeOrKsa: false,
                isUae: false,
                isKsa: false,
                isNotKsa: true
              },
              qrCode:
                '<image width="100" height="100" src="data:image/png;base64,iVBOR" />'
            }
          },
          html: '<!DOCTYPE html></html>\n'
        },
        filePath: 'creditmemo_1352583.pdf',
        entityId: '1352583'
      });
    });

    it('omsConsole_get', async () => {
      const response = await request(app).get('/v1/config/consul/oms').set({
        'X-Header-Token': HEADER_TOKEN,
        authorization: RUN_CONFIG.JWT_TOKEN,
      });
      expect(response.status).toBe(200);
    });

    it('omsConsole_update', async () => {
      insertOne.mockReturnValue([]);
      const response = await request(app)
        .post('/v1/config/consul/save')
        .set({
          'X-Header-Token': HEADER_TOKEN,
          authorization: RUN_CONFIG.JWT_TOKEN,
        })
        .send({
          data: {},
          save: true,
          sync: false,
          configType: 'oms'
        });
      expect(response.status).toBe(200);
    });
    it('omsConsole_update_negative', async () => {
      insertOne.mockReturnValue([]);
      const response = await request(app)
        .post('/v1/config/consul/save')
        .set({
          token: RUN_CONFIG.JWT_TOKEN,
          'X-Header-Token': HEADER_TOKEN
        })
        .send({});
      expect(response.status).not.toBe(200);
    });
  });
});
