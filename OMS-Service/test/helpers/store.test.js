const moment = require('moment');
const {
    getStores,
    getStoreDetails,
    getStoresWebsite,
    createNewWebsite,
    createNewStore,
    getNewStore,
    updateStoreDetails,
    getStoreLink,
    getWebsiteLink,
    getTrackUrl,
    getProductUrl,
    getEstDelivery
} = require('../../src/helpers/store');
const { Store, StoreWebsite } = require('../../src/models/seqModels/index');
const { IBAN_COUNTRY_MAP, STORE_LANG_MAP } = require('../../src/constants');
const Sequelize = require('sequelize');

// Mock the models
jest.mock('../../src/models/seqModels/index', () => ({
    Store: {
        findAll: jest.fn(),
        findOne: jest.fn(),
        create: jest.fn(),
        update: jest.fn()
    },
    StoreWebsite: {
        findAll: jest.fn(),
        create: jest.fn()
    }
}));

// Mock the constants
jest.mock('../../src/constants', () => ({
    IBAN_COUNTRY_MAP: {
        1: 'SA',
        2: 'AE',
        3: 'KW'
    },
    STORE_LANG_MAP: {
        1: 'en',
        2: 'ar',
        3: 'en'
    }
}));

describe('Store Helper', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        // Mock environment variables
        process.env.MAIN_WEBSITE_HOST_NAME = 'https://example.com';
    });

    describe('getStores', () => {
        it('should return filtered stores excluding store_id 0', async () => {
            const mockStores = [
                { dataValues: { store_id: 0, name: 'Store 0' } },
                { dataValues: { store_id: 1, name: 'Store 1' } },
                { dataValues: { store_id: 2, name: 'Store 2' } }
            ];

            Store.findAll.mockResolvedValue(mockStores);

            const result = await getStores();
            expect(result).toHaveLength(2);
            expect(result.find(store => store.store_id === 0)).toBeUndefined();
        });

        it('should handle empty response', async () => {
            Store.findAll.mockResolvedValue([]);
            const result = await getStores();
            expect(result).toEqual([]);
        });
    });

    describe('getStoreDetails', () => {
        it('should return store details for given store_id', async () => {
            const mockStore = { store_id: 1, name: 'Test Store' };
            Store.findOne.mockResolvedValue(mockStore);

            const result = await getStoreDetails(1);
            expect(result).toEqual(mockStore);
            expect(Store.findOne).toHaveBeenCalledWith({
                where: { store_id: 1 },
                raw: true
            });
        });
    });

    describe('getStoresWebsite', () => {
        it('should return websites excluding website_id 0', async () => {
            const mockWebsites = [
                { dataValues: { website_id: 1, name: 'Website 1' } },
                { dataValues: { website_id: 2, name: 'Website 2' } }
            ];

            StoreWebsite.findAll.mockResolvedValue(mockWebsites);

            const result = await getStoresWebsite();
            expect(result).toHaveLength(2);
            expect(StoreWebsite.findAll).toHaveBeenCalledWith({
                where: {
                    website_id: {
                        [Sequelize.Op.not]: 0
                    }
                }
            });
        });

        it('should handle empty response', async () => {
            StoreWebsite.findAll.mockResolvedValue([]);
            const result = await getStoresWebsite();
            expect(result).toEqual([]);
        });
    });

    describe('getStoreLink and getWebsiteLink', () => {
        it('should generate correct store link', () => {
            const storeId = '1';
            const expectedLink = 'https://example.com/sa/en';

            const result = getStoreLink(storeId);
            expect(result).toBe(expectedLink);
        });

        it('should generate correct website link', () => {
            const storeId = '1';
            const expectedLink = 'https://example.com/sa/en';

            const result = getWebsiteLink(storeId);
            expect(result).toBe(expectedLink);
        });

        it('should handle different store IDs', () => {
            const storeId = '2';
            const expectedLink = 'https://example.com/ae/ar';

            const result = getStoreLink(storeId);
            expect(result).toBe(expectedLink);
        });
    });

    describe('getEstDelivery', () => {
        it('should format date with correct suffix - st', () => {
            const date = '2024-01-01';
            const result = getEstDelivery(date);
            expect(result).toBe('1<sup>st</sup> Jan 2024');
        });

        it('should format date with correct suffix - nd', () => {
            const date = '2024-01-02';
            const result = getEstDelivery(date);
            expect(result).toBe('2<sup>nd</sup> Jan 2024');
        });

        it('should format date with correct suffix - rd', () => {
            const date = '2024-01-03';
            const result = getEstDelivery(date);
            expect(result).toBe('3<sup>rd</sup> Jan 2024');
        });

        it('should format date with correct suffix - th', () => {
            const date = '2024-01-04';
            const result = getEstDelivery(date);
            expect(result).toBe('4<sup>th</sup> Jan 2024');
        });

        it('should handle empty date', () => {
            const result = getEstDelivery(null);
            expect(result).toBe('');
        });
    });

    describe('getProductUrl', () => {
        it('should generate correct product URLs', () => {
            const storeId = '1';
            const products = [
                { name: 'Test Product', sku: 'TEST123' },
                { name: 'Test Product Two', sku: 'TEST456' }
            ];

            const result = getProductUrl(storeId, products);
            expect(result).toEqual([
                'https://example.com/sa/en/product-Test-Product-TEST123',
                'https://example.com/sa/en/product-Test-Product-Two-TEST456'
            ]);
        });

        it('should handle products with spaces in names', () => {
            const storeId = '1';
            const products = [
                { name: 'Test   Product', sku: 'TEST123' }
            ];

            const result = getProductUrl(storeId, products);
            expect(result).toEqual([
                'https://example.com/sa/en/product-Test-Product-TEST123'
            ]);
        });
    });

    describe('getTrackUrl', () => {
        it('should generate correct tracking URL', () => {
            const storeId = '1';
            const orderId = '12345';
            const expectedUrl = 'https://example.com/sa/en/account/orderview/12345';

            const result = getTrackUrl(storeId, orderId);
            expect(result).toBe(expectedUrl);
        });

        it('should handle different store IDs', () => {
            const storeId = '2';
            const orderId = '12345';
            const expectedUrl = 'https://example.com/ae/ar/account/orderview/12345';

            const result = getTrackUrl(storeId, orderId);
            expect(result).toBe(expectedUrl);
        });
    });

    describe('createNewWebsite', () => {
        it('should create new website and return website_id', async () => {
            const mockRequest = {
                options: {
                    website_code: 'TEST',
                    website_name: 'Test Website',
                    sort_order: 1
                }
            };

            const mockResponse = {
                website_id: 1
            };

            StoreWebsite.create.mockResolvedValue(mockResponse);

            const result = await createNewWebsite(mockRequest);
            expect(result).toBe(1);
            expect(StoreWebsite.create).toHaveBeenCalledWith({
                code: 'TEST',
                name: 'Test Website',
                default_group_id: 0,
                sort_order: 1
            });
        });
    });

    describe('createNewStore', () => {
        it('should create new store', async () => {
            const mockRequest = {
                options: {
                    code: 'TEST',
                    website_id: 1,
                    name: 'Test Store',
                    is_external: 0,
                    warehouse_location_code: 'WH1',
                    warehouse_inventory_table: 'inventory',
                    currency: 'USD',
                    currency_conversion_rate: 1
                }
            };

            const mockResponse = {
                store_id: 1,
                ...mockRequest.options
            };

            Store.create.mockResolvedValue(mockResponse);

            const result = await createNewStore(mockRequest);
            expect(result).toEqual(mockResponse);
        });
    });

    describe('getNewStore', () => {
        it('should return formatted store object with response', () => {
            const mockResponse = {
                store_id: 1,
                website_id: 1,
                currency: 'USD',
                currency_conversion_rate: 1,
                warehouse_location_code: 'WH1',
                warehouse_inventory_table: 'inventory'
            };

            const result = getNewStore(mockResponse, '');
            expect(result.storeId).toBe('1');
            expect(result.storeCurrency).toBe('USD');
            expect(result.warehouseId).toBe('WH1');
            expect(result.mapperTable).toBe('inventory');
        });

        it('should return formatted store object with store parameter', () => {
            const mockResponse = {};
            const mockStore = {
                store_id: 1,
                website_id: 1,
                currency: 'USD',
                currency_conversion_rate: 1,
                warehouse_location_code: 'WH1',
                warehouse_inventory_table: 'inventory'
            };

            const result = getNewStore(mockResponse, mockStore);
            expect(result.storeCurrency).toBe('USD');
            expect(result.warehouseId).toBe('WH1');
            expect(result.mapperTable).toBe('inventory');
        });
    });

    describe('updateStoreDetails', () => {
        it('should update store details', async () => {
            const mockUpdateObj = {
                store_id: 1,
                code: 'TEST',
                website_id: 1,
                name: 'Updated Store',
                is_external: 0,
                warehouse_location_code: 'WH1',
                warehouse_inventory_table: 'inventory',
                currency: 'USD',
                currency_conversion_rate: 1
            };

            Store.update.mockResolvedValue([1]);

            const result = await updateStoreDetails(mockUpdateObj);
            expect(result).toEqual([1]);
            expect(Store.update).toHaveBeenCalledWith(
                expect.objectContaining({
                    code: 'TEST',
                    name: 'Updated Store'
                }),
                {
                    where: { store_id: 1 }
                }
            );
        });
    });
}); 
