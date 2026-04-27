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
const app = require('../../src/app');
const mongoUtil = require('../../src/utils/mongoInit');
const RUN_CONFIG = require('../run.config.json');
const HEADER_TOKEN = RUN_CONFIG['X-HEADER-TOKEN'];

jest.mock('../../src/helpers/store', () => ({
    getStores: jest.fn(),
    getStoresWebsite: jest.fn(),
    createNewWebsite: jest.fn(),
    createNewStore: jest.fn(),
    getStoreDetails: jest.fn(),
    updateStoreDetails: jest.fn()
}));

const {
    getStores,
    getStoresWebsite,
    createNewWebsite,
    createNewStore,
    getStoreDetails,
    updateStoreDetails
} = require('../../src/helpers/store');

jest.setTimeout(90000);

describe('store_routes', () => {
    beforeAll(async () => {
        await mongoUtil.connectToServer();
        global.logError = jest.fn(() => ({}));
    });

    it('storeList - GET /list', async () => {
        getStores.mockResolvedValueOnce([{ id: 1, name: 'Store 1' }]);

        const response = await request(app)
            .get('/v1/store/list')
            .set({
                authorization: RUN_CONFIG.JWT_TOKEN,
                'X-Header-Token': HEADER_TOKEN
            });

        expect(response.status).toBe(200);
    });

    it('websiteList - GET /website-list', async () => {
        getStoresWebsite.mockResolvedValueOnce([{ id: 1, website: 'site1.com' }]);

        const response = await request(app)
            .get('/v1/store/website-list')
            .set({
                authorization: RUN_CONFIG.JWT_TOKEN,
                'X-Header-Token': HEADER_TOKEN
            });

        expect(response.status).toBe(200);
    });

    it('createStore - POST /create-store', async () => {
        createNewStore.mockResolvedValueOnce({ id: 123, name: 'New Store' });

        const response = await request(app)
            .post('/v1/store/create-store')
            .send({ name: 'New Store' })
            .set({
                authorization: RUN_CONFIG.JWT_TOKEN,
                'X-Header-Token': HEADER_TOKEN
            });

        expect(response.status).toBe(200);
    });

    it('createWebsite - POST /create-website', async () => {
        createNewWebsite.mockResolvedValueOnce({ id: 456, url: 'newsite.com' });

        const response = await request(app)
            .post('/v1/store/create-website')
            .send({ url: 'newsite.com' })
            .set({
                authorization: RUN_CONFIG.JWT_TOKEN,
                'X-Header-Token': HEADER_TOKEN
            });

        expect(response.status).toBe(200);
    });

    it('updateStoreDetail - POST /update-store', async () => {
        updateStoreDetails.mockResolvedValueOnce({ updated: true });

        const response = await request(app)
            .post('/v1/store/update-store')
            .send({ options: { name: 'Updated Store' } })
            .set({
                authorization: RUN_CONFIG.JWT_TOKEN,
                'X-Header-Token': HEADER_TOKEN
            });

        expect(response.status).toBe(200);
    });

    it('getStore - POST /detail with store_id', async () => {
        getStoreDetails.mockResolvedValueOnce({ id: 789, name: 'Store Detail' });

        const response = await request(app)
            .post('/v1/store/detail')
            .send({ store_id: 789 })
            .set({
                authorization: RUN_CONFIG.JWT_TOKEN,
                'X-Header-Token': HEADER_TOKEN
            });

        expect(response.status).toBe(200);
    });

    it('getStore - POST /detail without store_id should error', async () => {
        const response = await request(app)
            .post('/v1/store/detail')
            .send({})
            .set({
                authorization: RUN_CONFIG.JWT_TOKEN,
                'X-Header-Token': HEADER_TOKEN
            });

        expect(response.status).toBe(500);
        expect(response.body.statusMsg).toBe('store id required');
    });

    it('getWarehouseLocation - GET /warehouse-location-list', async () => {
        // Because this one reads consul internally, just call endpoint directly
        // and test it returns 200 (assumes consul is mocked elsewhere or no error)
        const response = await request(app)
            .get('/v1/store/warehouse-location-list')
            .set({
                authorization: RUN_CONFIG.JWT_TOKEN,
                'X-Header-Token': HEADER_TOKEN
            });

        expect(response.status).toBe(200);
    });
});
