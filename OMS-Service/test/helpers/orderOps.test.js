/* eslint-disable no-undef */
const {
    updatePreviousComment,
    copyOrderDataToCreditMemo,
    getCustomersDataWithFailedProxyOrders2,
    getFirstOrder
} = require('../../src/helpers/orderOps');
const {
    Order,
    OrderAddress,
    OrderComment,
    OrderPayment,
    ProxyOrder,
    SubSalesOrder
} = require('../../src/models/seqModels/index');
const { Op } = require('sequelize');
const { ORDER_REFUNDED_STATUS_CODE } = require('../../src/constants/order');
const { getStoreWebsiteIdMap } = require('../../src/utils/config');
const { getLifetimeOrders } = require('../../src/helpers/utilities');
const moment = require('moment');

// Mock Sequelize
jest.mock('sequelize', () => ({
    Op: {
        like: 'like'
    }
}));

// Mock the models and dependencies
jest.mock('../../src/models/seqModels/index', () => ({
    Order: {
        findAll: jest.fn()
    },
    OrderAddress: {
        findAll: jest.fn()
    },
    OrderComment: {
        findOne: jest.fn()
    },
    OrderPayment: {
        findAll: jest.fn()
    },
    ProxyOrder: {
        findAll: jest.fn()
    },
    SubSalesOrder: {
        findAll: jest.fn()
    }
}));

jest.mock('../../src/utils/config', () => ({
    getStoreWebsiteIdMap: jest.fn()
}));

jest.mock('../../src/helpers/utilities', () => ({
    getLifetimeOrders: jest.fn()
}));

describe('Order Operations Helper Functions', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        global.baseConfig = {
            smsConfig: {
                failureSMSJourney: {
                    startOffsetInHrs: 24,
                    endOffsetInMinutes: 30
                }
            }
        };
        global.logError = jest.fn();
        global.logInfo = jest.fn();
    });

    describe('updatePreviousComment', () => {
        it('should update refund comment status', async () => {
            const mockComment = {
                update: jest.fn().mockResolvedValue(true)
            };
            OrderComment.findOne.mockResolvedValue(mockComment);

            await updatePreviousComment({ orderId: '123' });

            expect(OrderComment.findOne).toHaveBeenCalledWith({
                where: {
                    parent_id: '123',
                    comment: { [Op.like]: '%We refunded%' }
                },
                order: [['entity_id', 'DESC']]
            });
            expect(mockComment.update).toHaveBeenCalledWith({
                status: ORDER_REFUNDED_STATUS_CODE
            });
        });

        it('should handle no refund comment found', async () => {
            OrderComment.findOne.mockResolvedValue(null);

            await updatePreviousComment({ orderId: '123' });

            expect(OrderComment.findOne).toHaveBeenCalled();
        });
    });

    describe('copyOrderDataToCreditMemo', () => {
        const mockOrderData = {
            createdAt: '2024-03-20',
            increment_id: '100000001',
            OrderAddresses: {
                address: 'Test Address'
            },
            customer_email: 'test@example.com',
            OrderPayments: {
                method: 'credit_card',
                additional_information: JSON.stringify({
                    card_number: '1234',
                    payment_option: 'VISA'
                })
            },
            SubSalesOrders: {
                warehouse_id: '1'
            }
        };

        it('should copy order data to credit memo correctly', () => {
            const creditMemo = {};

            copyOrderDataToCreditMemo({
                orderData: mockOrderData,
                creditMemo
            });

            expect(creditMemo).toEqual({
                orderCreatedAt: '2024-03-20',
                orderIncrementId: '100000001',
                shippingAddress: {
                    address: 'Test Address'
                },
                customerEmail: 'test@example.com',
                paymentInformation: {
                    paymentMethod: 'credit_card',
                    ccNumber: '1234',
                    ccType: 'VISA'
                },
                warehouseId: '1'
            });
        });

        it('should handle missing payment information', () => {
            const orderDataWithoutPayment = {
                ...mockOrderData,
                OrderPayments: {
                    method: 'credit_card',
                    additional_information: null
                }
            };

            const creditMemo = {};

            copyOrderDataToCreditMemo({
                orderData: orderDataWithoutPayment,
                creditMemo
            });

            expect(creditMemo.paymentInformation).toEqual({
                paymentMethod: 'credit_card',
                ccNumber: undefined,
                ccType: undefined
            });
        });
    });

    describe('getCustomersDataWithFailedProxyOrders2', () => {
        const mockProxyOrders = [
            {
                quote_id: '1',
                customer_email: 'test1@example.com',
                amount: 100,
                currency: 'SAR'
            }
        ];

        const mockOrders = [
            {
                entity_id: '1',
                store_id: '1',
                grand_total: '100.00',
                amstorecredit_amount: '0.00',
                order_currency_code: 'SAR',
                increment_id: '100000001',
                updated_at: '2024-03-20',
                status: 'payment_failed',
                SubSalesOrders: [
                    {
                        dataValues: {
                            external_quote_id: '1'
                        }
                    }
                ],
                OrderAddresses: [
                    {
                        dataValues: {
                            email: 'test1@example.com',
                            firstname: 'Test',
                            telephone: '1234567890'
                        }
                    }
                ],
                OrderPayments: [
                    {
                        dataValues: {
                            method: 'credit_card'
                        }
                    }
                ]
            }
        ];

        it('should process failed orders correctly', async () => {
            ProxyOrder.findAll.mockResolvedValue(mockProxyOrders);
            Order.findAll.mockResolvedValue(mockOrders);

            const result = await getCustomersDataWithFailedProxyOrders2();

            expect(result).toHaveProperty('finalArrayForNonProxyUsers');
            expect(result).toHaveProperty('finalArrayForProxyUsers');
            expect(ProxyOrder.findAll).toHaveBeenCalled();
            expect(Order.findAll).toHaveBeenCalled();
        });

        it('should handle errors gracefully', async () => {
            ProxyOrder.findAll.mockRejectedValue(new Error('Database error'));

            const result = await getCustomersDataWithFailedProxyOrders2();

            expect(result).toEqual({});
        });
    });

    describe('getFirstOrder', () => {
        it('should return first order by creation date', async () => {
            const mockOrders = [
                { createdAt: '2024-03-20', orderId: '2' },
                { createdAt: '2024-03-19', orderId: '1' },
                { createdAt: '2024-03-21', orderId: '3' }
            ];

            getStoreWebsiteIdMap.mockReturnValue({ '1': 'website1' });
            getLifetimeOrders.mockResolvedValue({ responseList: mockOrders });

            const result = await getFirstOrder({
                customerEmail: 'test@example.com',
                storeId: '1'
            });

            expect(result.orderId).toBe('1');
            expect(getLifetimeOrders).toHaveBeenCalledWith({
                customerEmail: 'test@example.com',
                websiteId: 'website1'
            });
        });

        it('should handle error in fetching orders', async () => {
            getStoreWebsiteIdMap.mockReturnValue({ '1': 'website1' });
            getLifetimeOrders.mockRejectedValue(new Error('API Error'));

            const result = await getFirstOrder({
                customerEmail: 'test@example.com',
                storeId: '1'
            });

            expect(result).toBeUndefined();
            expect(global.logError).toHaveBeenCalled();
        });

        it('should handle empty order list', async () => {
            getStoreWebsiteIdMap.mockReturnValue({ '1': 'website1' });
            getLifetimeOrders.mockResolvedValue({ responseList: [] });

            const result = await getFirstOrder({
                customerEmail: 'test@example.com',
                storeId: '1'
            });

            expect(result).toBeUndefined();
        });
    });
}); 