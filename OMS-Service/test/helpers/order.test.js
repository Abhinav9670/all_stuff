/* eslint-disable no-undef */
const {
    getOrder,
    updateOrder,
    getTotalItemCount,
    getOrderItems,
    getOrderData,
    getOrderStatus,
    getOrderStatusDetails,
    updateOrderStateStatus,
    findFailedPrOrders,
    findFailedRTOrders,
    checkUndeliveredStatus
} = require('../../src/helpers/order');

const {
    Order,
    Shipment,
    OrderAddress,
    OrderItem,
    OrderPayment,
    OrderGrid,
    Creditmemo,
    SubSalesOrder,
    RmaRequest,
    sequelize
} = require('../../src/models/seqModels/index');

const { QueryTypes } = require('sequelize');
const moment = require('moment');

// Mock all required dependencies
jest.mock('../../src/models/seqModels/index', () => ({
    Order: {
        findOne: jest.fn(),
        update: jest.fn(),
        findAll: jest.fn()
    },
    Shipment: { findAll: jest.fn() },
    OrderAddress: { findAll: jest.fn() },
    OrderItem: { findAll: jest.fn(), update: jest.fn() },
    OrderPayment: { findAll: jest.fn() },
    OrderGrid: { update: jest.fn() },
    Creditmemo: { findAll: jest.fn() },
    SubSalesOrder: { findAll: jest.fn(), update: jest.fn() },
    RmaRequest: { findAll: jest.fn() },
    sequelize: {
        query: jest.fn()
    }
}));

jest.mock('../../src/helpers/sms', () => ({
    sendSMS: jest.fn().mockResolvedValue(true)
}));

jest.mock('../../src/helpers/email', () => ({
    sendEmail: jest.fn().mockResolvedValue(true)
}));

jest.mock('../../src/helpers/braze', () => ({
    logBrazeCustomEventForDelivery: jest.fn().mockResolvedValue(true)
}));

jest.mock('../../src/helpers/utilities', () => ({
    updateStatusHistory: jest.fn().mockResolvedValue(true),
    promiseAll: jest.fn().mockImplementation(promises => Promise.all(promises))
}));

jest.mock('../../src/helpers/eas/earnIntegration', () => ({
    earnEventForDeliverySuccess: jest.fn().mockResolvedValue(true)
}));

jest.mock('../../src/helpers/cleverTap', () => ({
    eventForDelivery: jest.fn().mockResolvedValue(true)
}));

jest.mock('../../src/helpers/adjust', () => ({
    adjustDeliveredEvent: jest.fn().mockResolvedValue(true)
}));

jest.mock('../../src/helpers/forwardShipment', () => ({
    sendMessage: jest.fn().mockResolvedValue(true)
}));

describe('Order Helper Functions', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        global.baseConfig = {
            featureBasedFlag: {
                unDeliveredWAMsg: true
            },
            whatsappConfig: {
                failedDeliveryTemplateName_en: 'template_en',
                failedDeliveryTemplateName_ar: 'template_ar',
                fromPhoneNumber: '1234567890'
            }
        };
        global.config = {
            environments: [
                {
                    stores: [
                        {
                            storeId: '1',
                            storeLanguage: 'en_US'
                        }
                    ]
                }
            ]
        };
        global.logError = jest.fn();
        global.logInfo = jest.fn();
    });

    describe('getOrder', () => {
        it('should fetch order by increment_id', async () => {
            const mockOrder = {
                dataValues: {
                    entity_id: 1,
                    increment_id: 'ORDER123',
                    Shipments: [{ dataValues: { id: 1 } }],
                    OrderAddresses: [
                        { dataValues: { address_type: 'shipping', street: 'Test St' } }
                    ],
                    OrderItems: [
                        { dataValues: { product_type: 'simple', sku: 'SKU1' } },
                        { dataValues: { product_type: 'configurable', sku: 'SKU2' } }
                    ],
                    OrderPayments: [{ dataValues: { method: 'card' } }],
                    Creditmemos: [{ dataValues: { id: 1 } }]
                }
            };

            Order.findOne.mockResolvedValue(mockOrder);

            const result = await getOrder({ incrementId: 'ORDER123' });
            expect(result).toBeDefined();
            expect(result.simpleItems).toHaveLength(1);
            expect(result.configItems).toHaveLength(1);
            expect(Order.findOne).toHaveBeenCalledWith({
                where: { increment_id: 'ORDER123' },
                include: expect.any(Array)
            });
        });
    });

    describe('updateOrder', () => {
        const mockOrderData = {
            entity_id: 1,
            increment_id: 'ORDER123',
            OrderItems: [{ dataValues: { item_id: 1, qty_ordered: 2 } }],
            shippingAddress: {
                telephone: '1234567890',
                firstname: 'John'
            },
            store_id: '1'
        };

        it('should update order status and related data', async () => {
            const params = {
                incrementId: 'ORDER123',
                entityId: 1,
                orderData: mockOrderData,
                updateObj: { status: 'complete' },
                smsStatus: 'delivered'
            };

            Order.update.mockResolvedValue([1]);
            OrderGrid.update.mockResolvedValue([1]);

            const result = await updateOrder(params);
            expect(result).toBeDefined();
            expect(Order.update).toHaveBeenCalled();
            expect(OrderGrid.update).toHaveBeenCalled();
        });

        it('should handle undelivered status with WhatsApp message', async () => {
            const params = {
                incrementId: 'ORDER123',
                entityId: 1,
                orderData: mockOrderData,
                updateObj: { status: 'undelivered' },
                smsStatus: 'undelivered'
            };

            sequelize.query.mockResolvedValue([{ cnt: 0 }]);
            Order.update.mockResolvedValue([1]);
            OrderGrid.update.mockResolvedValue([1]);

            const result = await updateOrder(params);
            expect(result).toBeDefined();
        });
    });

    describe('getTotalItemCount', () => {
        it('should calculate total item count correctly', async () => {
            const mockItems = [
                { qty_ordered: 2, qty_canceled: 0, qty_refunded: 0 },
                { qty_ordered: 3, qty_canceled: 1, qty_refunded: 1 }
            ];

            OrderItem.findAll.mockResolvedValue(mockItems);

            const result = await getTotalItemCount({ orderId: 1 });
            expect(result).toBe(3); // 2 + (3-2)
            expect(OrderItem.findAll).toHaveBeenCalledWith({
                where: { order_id: 1, product_type: 'simple' }
            });
        });
    });

    describe('getOrderItems', () => {
        it('should return simple items for IN region', async () => {
            process.env.REGION = 'IN';
            const mockItems = [
                { product_type: 'simple', item_id: 1 },
                { product_type: 'configurable', item_id: 2 }
            ];

            OrderItem.findAll.mockResolvedValue(mockItems);

            const result = await getOrderItems({ orderId: 1 });
            expect(result).toEqual([1]);
        });

        it('should return configurable items for other regions', async () => {
            process.env.REGION = 'UAE';
            const mockItems = [
                { product_type: 'simple', item_id: 1 },
                { product_type: 'configurable', item_id: 2 }
            ];

            OrderItem.findAll.mockResolvedValue(mockItems);

            const result = await getOrderItems({ orderId: 1 });
            expect(result).toEqual([2]);
        });
    });

    describe('findFailedPrOrders', () => {
        it('should handle empty results', async () => {
            SubSalesOrder.findAll.mockResolvedValue([]);

            const result = await findFailedPrOrders(12);
            expect(result).toEqual([]);
        });
    });

    describe('findFailedRTOrders', () => {
        it('should find failed RT orders within time window', async () => {
            const mockRmaRequests = [
                { order_id: 1, shukran_rt_successful: 0, status: 7 },
                { order_id: 2, shukran_rt_successful: null, status: 15 }
            ];

            const mockSubSalesOrders = [
                {
                    order_id: 1,
                    shukran_linked: true,
                    shukran_card_number: '123',
                    customer_profile_id: '456',
                    shukran_pr_successful: 1,
                    total_shukran_coins_earned: 100
                },
                {
                    order_id: 2,
                    shukran_linked: true,
                    shukran_card_number: '789',
                    customer_profile_id: '012',
                    shukran_pr_successful: 1,
                    total_shukran_coins_earned: 200
                }
            ];

            const mockOrders = [
                { increment_id: 'ORDER1' },
                { increment_id: 'ORDER2' }
            ];

            RmaRequest.findAll.mockResolvedValue(mockRmaRequests);
            SubSalesOrder.findAll.mockResolvedValue(mockSubSalesOrders);
            Order.findAll.mockResolvedValue(mockOrders);

            const result = await findFailedRTOrders(12);
            expect(result).toEqual(['ORDER1', 'ORDER2']);
        });

        it('should handle empty results', async () => {
            RmaRequest.findAll.mockResolvedValue([]);

            const result = await findFailedRTOrders(12);
            expect(result).toEqual([]);
        });
    });
});
