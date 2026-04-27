/* eslint-disable no-undef */
// Mock all dependencies before requiring the module
jest.mock('fs', () => ({
    readFileSync: jest.fn(),
    readdirSync: jest.fn().mockReturnValue([])
}));

jest.mock('../../src/models/seqModels/index', () => ({
    sequelize: {
        query: jest.fn(),
        QueryTypes: { SELECT: 'SELECT' }
    }
}));

jest.mock('../../src/utils/mongoInit', () => ({
    getDb: jest.fn()
}));

jest.mock('../../src/models/seqModels/archiveIndex', () => ({
    // Add any archive model methods that might be used
    archiveModels: {}
}));

jest.mock('../../src/helpers/order', () => ({
    paymentMethodsLabels: {
        cashondelivery: { en: 'Cash on Delivery', ar: 'الدفع عند الاستلام' }
    }
}));

jest.mock('../../src/utils/config', () => ({
    getStoreConfigs: jest.fn().mockReturnValue([{ currencyConversionRate: 1 }])
}));

jest.mock('../../src/helpers/invoiceHeadings', () => ({
    getStoreWiseHeadings: jest.fn().mockReturnValue({})
}));

jest.mock('../../src/helpers/tax', () => ({
    getGstInPan: jest.fn().mockReturnValue({ gstIN: 'TEST123', panNo: 'PAN123' }),
    getStateCode: jest.fn().mockReturnValue('123')
}));

// Now require the module and its dependencies
const {
    prepareInvoice,
    getCompanyAddress,
    findOrders,
    storeInvoice,
    findAllInvoice,
    getSecondReturnTemplateSource,
    getCodRtoCreditMemoTemplateSource
} = require('../../src/helpers/invoice');
const { sequelize } = require('../../src/models/seqModels/index');
const mongoUtil = require('../../src/utils/mongoInit');
const fs = require('fs');
const moment = require('moment');

describe('Invoice Helper Functions', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        // Setup global configs used in the functions
        global.baseConfig = {
            donationConfig: { text: 'Donation text' },
            donationConfigExclTax: { text: 'Donation text excl tax' },
            bulkInvoice: { mongoTtlInSeconds: 1296000 }
        };
        global.javaOrderServiceConfig = {
            inventory_mapping: [
                {
                    warehouse_id: '1',
                    INVOICE_ADDRESS_EN: ['Test Address EN'],
                    INVOICE_ADDRESS_AR: ['Test Address AR']
                }
            ]
        };
        global.config = {
            environments: [
                {
                    stores: [
                        { websiteCode: 'KSA', storeId: '1' },
                        { websiteCode: 'UAE', storeId: '2' }
                    ]
                }
            ]
        };
        global.taxConfig = {
            warehouseIdStateCode: '123',
            warehouseState: 'Test State'
        };
        process.env.REGION = 'KSA';
    });

    describe('Return-specific template functions', () => {
        it('should get second return template source', () => {
            const mockTemplate = '<html>Second Return Template</html>';
            fs.readFileSync.mockReturnValue(mockTemplate);

            const result = getSecondReturnTemplateSource();

            expect(result).toBe(mockTemplate);
            expect(fs.readFileSync).toHaveBeenCalledWith(
                './src/templates/invoiceZatcaSecondReturn.html',
                'utf8'
            );
        });

        it('should get COD RTO credit memo template source', () => {
            const mockTemplate = '<html>COD RTO Credit Memo Template</html>';
            fs.readFileSync.mockReturnValue(mockTemplate);

            const result = getCodRtoCreditMemoTemplateSource();

            expect(result).toBe(mockTemplate);
            expect(fs.readFileSync).toHaveBeenCalledWith(
                './src/templates/codRtoCreditMemoZatca.html',
                'utf8'
            );
        });
    });

    describe('findOrders', () => {
        it('should find orders for invoice generation', async () => {
            const mockOrders = [
                { order_id: '1', customer_email: 'test1@example.com' },
                { order_id: '2', customer_email: 'test2@example.com' }
            ];

            sequelize.query.mockResolvedValue(mockOrders);

            const result = await findOrders({
                fromDate: '2024-03-01',
                toDate: '2024-03-20',
                country: ['KSA']
            });

            expect(result).toHaveLength(2);
            expect(result[0]).toHaveProperty('orderId', '1');
            expect(result[0]).toHaveProperty('customerEmail', 'test1@example.com');
        });
    });

    describe('storeInvoice', () => {
        it('should store invoice in MongoDB with TTL', async () => {
            const mockDb = {
                collection: jest.fn().mockReturnValue({
                    insertOne: jest.fn().mockResolvedValue({ acknowledged: true })
                })
            };
            mongoUtil.getDb.mockReturnValue(mockDb);

            const payload = {
                orderId: '100000001',
                invoiceData: { total: 100 }
            };

            await storeInvoice(payload);

            expect(payload).toHaveProperty('created_at');
            expect(payload).toHaveProperty('expire_at');
            expect(
                moment(payload.expire_at).diff(payload.created_at, 'seconds')
            ).toBe(1296000);
        });
    });

    describe('findAllInvoice', () => {
        it('should retrieve all invoices from MongoDB', async () => {
            const mockInvoices = [
                { orderId: '1', total: 100 },
                { orderId: '2', total: 200 }
            ];

            const mockCollection = {
                find: jest.fn().mockReturnValue({
                    toArray: jest.fn().mockResolvedValue(mockInvoices)
                })
            };

            const mockDb = {
                collection: jest.fn().mockReturnValue(mockCollection)
            };

            mongoUtil.getDb.mockReturnValue(mockDb);

            const result = await findAllInvoice();

            expect(result).toEqual(mockInvoices);
            expect(mockDb.collection).toHaveBeenCalledWith('bulk_invoices');
        });
    });

    describe('getCompanyAddress', () => {
        it('should return company address based on warehouse ID', () => {
            const result = getCompanyAddress('1');

            expect(result).toHaveProperty('addressEn');
            expect(result).toHaveProperty('addressAr');
            expect(result.addressEn).toEqual(['Test Address EN']);
            expect(result.addressAr).toEqual(['Test Address AR']);
        });

        it('should return default address when warehouse ID not found', () => {
            const result = getCompanyAddress('999');

            expect(result.addressEn).toContain(
                'Retail Cart Trading Company Sole Person Company'
            );
            expect(result.addressAr).toContain(
                'شركة ريتيل كارت للتجارة شركة شخص واحد'
            );
        });
    });
});

