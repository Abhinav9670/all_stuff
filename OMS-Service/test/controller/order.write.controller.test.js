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
const axios = require('axios');

jest.mock('axios');

const { updateOrderStatusDetail } = require('../../src/helpers/order');
jest.mock('../../src/helpers/order');

jest.setTimeout(90000);

// Mock the logging helper to avoid DB calls during tests
jest.mock('../../src/helpers/logging', () => ({
    addAdminLog: jest.fn().mockResolvedValue(true),
}));

describe('orderWrite_routes', () => {
    beforeAll(async () => {
        await mongoUtil.connectToServer();
        global.logError = jest.fn(() => ({}));
    });

    describe('POST /create-awb', () => {
        it('should create AWB successfully', async () => {
            axios.get.mockResolvedValueOnce({
                data: { success: true }
            });

            const response = await request(app)
                .post('/v1/orders/create-awb')
                .send({
                    query: { orderCode: 'OC123', shipmentCode: 'SC456' },
                    email: 'test@example.com'
                })
                .set({
                    authorization: RUN_CONFIG.JWT_TOKEN,
                    'X-Header-Token': HEADER_TOKEN,
                });

            expect(response.status).toBe(200);
            expect(response.body.success).toBe(true);
        });

        it('should return 500 if response has error', async () => {
            axios.get.mockResolvedValueOnce({
                data: { hasError: true, errorMessage: 'AWB failed' }
            });

            const response = await request(app)
                .post('/v1/orders/create-awb')
                .send({
                    query: { orderCode: 'OC123', shipmentCode: 'SC456' },
                    email: 'test@example.com'
                })
                .set({
                    'X-Header-Token': HEADER_TOKEN,
                    authorization: RUN_CONFIG.JWT_TOKEN,
                });

            expect(response.status).toBe(500);
            expect(response.body.error).toBe('AWB failed');
        });
    });

    describe('PUT /create-shipment', () => {
        it('should create shipment successfully', async () => {
            axios.post.mockResolvedValueOnce({
                data: { shipmentId: 'SHIP123' }
            });

            const response = await request(app)
                .put('/v1/orders/create-shipment')
                .send({ orderCode: 'OC123', details: 'some shipment details' })
                .set({
                    'X-Header-Token': HEADER_TOKEN,
                    authorization: RUN_CONFIG.JWT_TOKEN,
                });

            expect(response.status).toBe(200);
            expect(response.body.shipmentId).toBe('SHIP123');
        });
    });

    describe('POST /update/order-status-details/', () => {
        it('should update order status details successfully', async () => {
            updateOrderStatusDetail.mockResolvedValueOnce({ updated: true });

            const response = await request(app)
                .post('/v1/orders/update/order-status-details/')
                .send({
                    options: {
                        status: 'shipped',
                        label: 'Shipped',
                        color_state: 'green',
                        is_default: true,
                        visible_on_front: true,
                        step: 3
                    }
                })
                .set({
                    'X-Header-Token': HEADER_TOKEN
                });

            expect(response.status).toBe(200);
            expect(response.body.status).toBe(true);
            expect(response.body.statusMsg).toBe('Success');
            expect(response.body.response).toEqual({ updated: true });
        });

        it('should return 500 if update fails', async () => {
            updateOrderStatusDetail.mockRejectedValueOnce(new Error('Failed update'));

            const response = await request(app)
                .post('/v1/orders/update/order-status-details/')
                .send({
                    options: {
                        status: 'shipped',
                        label: 'Shipped'
                    }
                })
                .set({
                    'X-Header-Token': HEADER_TOKEN
                });

            expect(response.status).toBe(500);
            expect(response.body.error).toBe('Failed update');
        });
    });

    describe('PUT /address', () => {
        it('should update address successfully', async () => {
            axios.put.mockResolvedValueOnce({
                data: { addressUpdated: true }
            });

            const response = await request(app)
                .put('/v1/orders/address')
                .send({
                    beforeData: { address: 'Old Address' },
                    afterData: { address: 'New Address', regionId: 123 }
                })
                .set({
                    'X-Header-Token': HEADER_TOKEN,
                    authorization: RUN_CONFIG.JWT_TOKEN,
                });

            expect(response.status).toBe(200);
            expect(response.body.addressUpdated).toBe(true);
        });
    });

    describe('POST /status', () => {
        it('should update status successfully', async () => {
            axios.put.mockResolvedValueOnce({
                data: { statusUpdated: true }
            });

            const response = await request(app)
                .post('/v1/orders/status')
                .send({ orderCode: 'OC123', status: 'delivered' })
                .set({
                    'X-Header-Token': HEADER_TOKEN,
                    authorization: RUN_CONFIG.JWT_TOKEN,
                });

            expect(response.status).toBe(200);
            expect(response.body.statusUpdated).toBe(true);
        });
    });
});

