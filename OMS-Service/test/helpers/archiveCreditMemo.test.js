/* eslint-disable max-lines-per-function */
/* eslint-disable no-undef */
const { getArchivedCreditMemos } = require('../../src/helpers/archiveCreditMemo');

jest.mock('../../src/models/seqModels/archiveIndex', () => ({
  Creditmemo: { findAll: jest.fn() },
  CreditmemoItem: {},
  CreditmemoComment: {},
  OrderItem: { findAll: jest.fn() }
}));
jest.mock('../../src/services/misc.service', () => ({ getProductsBySKU: jest.fn() }));
jest.mock('../../src/utils', () => ({ sanitiseImageUrl: jest.fn(url => url) }));
jest.mock('../../src/utils/config', () => ({ getStoreConfigs: jest.fn(() => []) }));
jest.mock('../../src/helpers/moment', () => ({ getKSATime: jest.fn(date => 'formatted-' + date) }));

const { Creditmemo, OrderItem } = require('../../src/models/seqModels/archiveIndex');
const { getProductsBySKU } = require('../../src/services/misc.service');


describe('archiveCreditMemo', () => {
  beforeAll(() => {
    global.logError = jest.fn(() => ({}));
    global.logInfo = jest.fn(() => ({}));
  });

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should return error if no credit memos found', async () => {
    require('../../src/utils/config').getStoreConfigs.mockReturnValueOnce([]);
    Creditmemo.findAll.mockResolvedValue([]);
    const result = await getArchivedCreditMemos({ orderId: '1' });
    expect(result).toEqual({ error: 'Credit Memo(s) not found!' });
  });

  it('should return formatted credit memos', async () => {
    require('../../src/utils/config').getStoreConfigs.mockReturnValueOnce([{ taxPercentage: 5 }]);
    Creditmemo.findAll.mockResolvedValue([
      {
        dataValues: {
          grand_total: '100',
          amstorecredit_amount: '10',
          created_at: '2024-01-01',
          store_id: 1,
          CreditmemoItems: [{ dataValues: { order_item_id: 1, sku: 'sku1' } }],
          CreditmemoComments: [{ dataValues: { created_at: '2024-01-01', comment: 'test' } }],
          rma_number: 'RMA1'
        }
      }
    ]);
    OrderItem.findAll.mockResolvedValue([
      { product_type: 'configurable', item_id: 1 },
      { product_type: 'simple', sku: 'sku1' }
    ]);
    getProductsBySKU.mockResolvedValue({ sku1: { variants: [{ sku: 'sku1' }], media_gallery: [{ value: 'img.jpg' }] } });
    const result = await getArchivedCreditMemos({ orderId: '1' });
    expect(result.error).toBe(false);
    expect(result.response.length).toBe(1);
    expect(result.response[0].refundedAmount).toBe(110);
    expect(result.response[0].items.length).toBe(1);
    // expect(result.response[0].comments[0].created_at).toContain('formatted-');
  });

  it('should handle empty CreditmemoItems and CreditmemoComments', async () => {
    require('../../src/utils/config').getStoreConfigs.mockReturnValueOnce([{ taxPercentage: 5 }]);
    Creditmemo.findAll.mockResolvedValue([
      {
        dataValues: {
          grand_total: '100',
          amstorecredit_amount: '0',
          created_at: undefined,
          store_id: 1,
          CreditmemoItems: [],
          CreditmemoComments: [],
          rma_number: 'RMA2'
        }
      }
    ]);
    OrderItem.findAll.mockResolvedValue([]);
    getProductsBySKU.mockResolvedValue({});
    const result = await getArchivedCreditMemos({ orderId: '2' });
    expect(result.error).toBe(false);
    expect(result.response[0].items).toEqual([]);
    expect(result.response[0].comments).toEqual([]);
    expect(result.response[0].created_at).toBeUndefined();
  });

  it('should handle missing variants and media_gallery in getProductsBySKU', async () => {
    require('../../src/utils/config').getStoreConfigs.mockReturnValueOnce([{ taxPercentage: 5 }]);
    Creditmemo.findAll.mockResolvedValue([
      {
        dataValues: {
          grand_total: '50',
          amstorecredit_amount: '0',
          created_at: '2024-01-02',
          store_id: 1,
          CreditmemoItems: [{ dataValues: { order_item_id: 2, sku: 'sku2' } }],
          CreditmemoComments: [],
          rma_number: 'RMA3'
        }
      }
    ]);
    OrderItem.findAll.mockResolvedValue([
      { product_type: 'configurable', item_id: 2 },
      { product_type: 'simple', sku: 'sku2' }
    ]);
    getProductsBySKU.mockResolvedValue({ sku2: { variants: [], media_gallery: [] } });
    const result = await getArchivedCreditMemos({ orderId: '3' });
    expect(result.error).toBe(false);
    expect(result.response[0].items[0].imageUrl).toBeUndefined();
  });

  it('should set showTax to false if taxPercentage is 0', async () => {
    require('../../src/utils/config').getStoreConfigs.mockReturnValueOnce([{ taxPercentage: 0 }]);
    Creditmemo.findAll.mockResolvedValue([
      {
        dataValues: {
          grand_total: '100',
          amstorecredit_amount: '0',
          created_at: '2024-01-03',
          store_id: 1,
          CreditmemoItems: [],
          CreditmemoComments: [],
          rma_number: 'RMA4'
        }
      }
    ]);
    OrderItem.findAll.mockResolvedValue([]);
    getProductsBySKU.mockResolvedValue({});
    const result = await getArchivedCreditMemos({ orderId: '4' });
    expect(result.error).toBe(false);
    expect(result.response[0].showTax).toBe(false);
  });

  it('should set showTax to true if getStoreConfigs returns empty array', async () => {
    require('../../src/utils/config').getStoreConfigs.mockReturnValueOnce([]);
    Creditmemo.findAll.mockResolvedValue([
      {
        dataValues: {
          grand_total: '100',
          amstorecredit_amount: '0',
          created_at: '2024-01-04',
          store_id: 1,
          CreditmemoItems: [],
          CreditmemoComments: [],
          rma_number: 'RMA5'
        }
      }
    ]);
    OrderItem.findAll.mockResolvedValue([]);
    getProductsBySKU.mockResolvedValue({});
    const result = await getArchivedCreditMemos({ orderId: '5' });
    expect(result.error).toBe(false);
    expect(result.response[0].showTax).toBe(true);
  });

  it('should handle multiple memos and items', async () => {
    require('../../src/utils/config').getStoreConfigs.mockReturnValue([{ taxPercentage: 5 }]);
    Creditmemo.findAll.mockResolvedValue([
      {
        dataValues: {
          grand_total: '100',
          amstorecredit_amount: '10',
          created_at: '2024-01-01',
          store_id: 1,
          CreditmemoItems: [
            { dataValues: { order_item_id: 1, sku: 'sku1' } },
            { dataValues: { order_item_id: 2, sku: 'sku2' } }
          ],
          CreditmemoComments: [
            { dataValues: { created_at: '2024-01-01', comment: 'test1' } },
            { dataValues: { created_at: '2024-01-02', comment: 'test2' } }
          ],
          rma_number: 'RMA6'
        }
      },
      {
        dataValues: {
          grand_total: '200',
          amstorecredit_amount: '20',
          created_at: '2024-01-02',
          store_id: 2,
          CreditmemoItems: [
            { dataValues: { order_item_id: 3, sku: 'sku3' } }
          ],
          CreditmemoComments: [
            { dataValues: { created_at: '2024-01-03', comment: 'test3' } }
          ],
          rma_number: 'RMA7'
        }
      }
    ]);
    OrderItem.findAll.mockResolvedValue([
      { product_type: 'configurable', item_id: 1 },
      { product_type: 'configurable', item_id: 2 },
      { product_type: 'configurable', item_id: 3 },
      { product_type: 'simple', sku: 'sku1' },
      { product_type: 'simple', sku: 'sku2' },
      { product_type: 'simple', sku: 'sku3' }
    ]);
    getProductsBySKU.mockResolvedValue({
      sku1: { variants: [{ sku: 'sku1' }], media_gallery: [{ value: 'img1.jpg' }] },
      sku2: { variants: [{ sku: 'sku2' }], media_gallery: [{ value: 'img2.jpg' }] },
      sku3: { variants: [{ sku: 'sku3' }], media_gallery: [{ value: 'img3.jpg' }] }
    });
    const result = await getArchivedCreditMemos({ orderId: '6' });
    expect(result.error).toBe(false);
    expect(result.response.length).toBe(2);
    expect(result.response[0].items.length).toBe(2);
    expect(result.response[1].items.length).toBe(1);
    expect(result.response[0].comments.length).toBe(2);
    expect(result.response[1].comments.length).toBe(1);
  });
});
