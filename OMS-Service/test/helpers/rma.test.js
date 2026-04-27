// Mock dotenv-expand to prevent recursion
jest.mock('dotenv-expand', () => jest.fn((env) => env));

jest.mock('../../src/models/seqModels/index');
jest.mock('../../src/services/misc.service');
jest.mock('../../src/utils/config');

const {
    RmaRequest,
    Order,
    SubSalesOrder,
    OrderItem,
    RmaStatus,
    OrderComment
} = require('../../src/models/seqModels/index');
const {
    getRmaDetail,
    getRmaRequests,
    isShortPickUp,
    getRmaStatusDetails,
    updateRmaStatusDetails,
    saveShukranEarnedPointsInDb,
    saveShukranEarnedPointsInOrderHistory
} = require('../../src/helpers/rma');
const { Op } = require('sequelize');

describe('RMA Helper Functions', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        global.baseConfig = {
            configs: {
                trackingBaseUrl: 'https://tracking.test',
                shortPickupEnabled: true,
                shortPickupCodes: 'SHORT,PICK',
                carrierCodes: ['DHL', 'FEDEX']
            }
        };
        global.javaOrderServiceConfig = {
            order_details: {
                AWB_ENCRYPTION_SECRET: 'test-secret',
                AWB_ENCRYPTION_SALT: 'test-salt'
            }
        };
        global.logInfo = jest.fn();
        global.logError = jest.fn();
        global.config = {
            shukranEnrollmentCommonCode: 'SHUKRAN'
        };
    });

    describe('getRmaDetail', () => {
        const mockRmaData = {
            dataValues: {
                request_id: 1,
                rma_inc_id: 'RMA123',
                status: 'pending',
                created_at: '2024-01-01T00:00:00Z',
                shipping_label: 'http://test.com/label.pdf',
                RmaRequestItems: [
                    {
                        dataValues: {
                            order_item_id: 1,
                            item_status: 1
                        }
                    }
                ],
                RmaTrackings: [
                    {
                        tracking_number: '1234567890'
                    }
                ],
                Order: {
                    dataValues: {
                        entity_id: 1
                    }
                }
            }
        };

        const mockOrderItem = {
            item_id: 1,
            sku: 'TEST-SKU',
            name: 'Test Product'
        };

        beforeEach(() => {
            RmaRequest.findOne.mockResolvedValue(mockRmaData);
            OrderItem.findAll.mockResolvedValue([mockOrderItem]);
        });

        it('should return RMA details with request ID', async () => {
            const result = await getRmaDetail({ requestId: 1 });

            expect(RmaRequest.findOne).toHaveBeenCalledWith({
                where: expect.objectContaining({
                    [Op.or]: [
                        { request_id: 1 },
                        { request_id: '1' }
                    ]
                }),
                include: expect.any(Array)
            });

            expect(result).toHaveProperty('rmaRequestItems');
            expect(result.shipping_label).toContain('https://');
            expect(result.carrierCodes).toEqual(['DHL', 'FEDEX']);
        });

        it('should return RMA details with increment ID', async () => {
            const result = await getRmaDetail({ rmaIncrementId: 'RMA123' });

            expect(RmaRequest.findOne).toHaveBeenCalledWith({
                where: { rma_inc_id: 'RMA123' },
                include: expect.any(Array)
            });
        });
    });

    describe('getRmaRequests', () => {
        const mockRmaRequests = {
            count: 1,
            rows: [{
                dataValues: {
                    request_id: 1,
                    created_at: '2024-01-01T00:00:00Z',
                    Order: {
                        entity_id: 1,
                        increment_id: 'ORDER123'
                    }
                }
            }]
        };

        beforeEach(() => {
            RmaRequest.findAndCountAll.mockResolvedValue(mockRmaRequests);
        });

        it('should return RMA requests with pagination', async () => {
            const result = await getRmaRequests({
                offset: 0,
                limit: 10
            });

            expect(result.count).toBe(1);
            expect(result.hits).toHaveLength(1);
            expect(result.hits[0]).toHaveProperty('order_increment_id', 'ORDER123');
        });

        it('should apply filters', async () => {
            await getRmaRequests({
                offset: 0,
                limit: 10,
                filters: { order_id: 'ORDER123' }
            });

            expect(RmaRequest.findAndCountAll).toHaveBeenCalledWith(
                expect.objectContaining({
                    include: [expect.objectContaining({
                        where: { increment_id: 'ORDER123' }
                    })]
                })
            );
        });
    });

    describe('isShortPickUp', () => {
        it('should return true for valid short pickup code', () => {
            expect(isShortPickUp('Order SHORT picked')).toBe(true);
            expect(isShortPickUp('PICK up issue')).toBe(true);
        });

        it('should return false for invalid short pickup code', () => {
            expect(isShortPickUp('Normal return')).toBe(false);
        });

        it('should handle disabled short pickup', () => {
            global.baseConfig.configs.shortPickupEnabled = false;
            expect(isShortPickUp('SHORT picked')).toBe(false);
        });
    });

    describe('Shukran Points Management', () => {
        const mockOrderData = {
            entity_id: 1,
            store_id: 1,
            status: 'complete'
        };

        beforeEach(() => {
            Order.findOne.mockResolvedValue(mockOrderData);
            SubSalesOrder.update.mockResolvedValue([1]);
            OrderComment.create.mockResolvedValue({});
        });

        it('should save Shukran earned points', async () => {
            await saveShukranEarnedPointsInDb('ORDER123', 100, 1000);

            expect(global.logInfo).toHaveBeenCalledWith('saveShukranEarnedPointsInDb');
            expect(SubSalesOrder.update).toHaveBeenCalledWith(
                expect.objectContaining({
                    total_shukran_coins_earned: 100,
                    shukran_pr_successful: 1
                }),
                expect.any(Object)
            );
        });

        it('should save points in order history', async () => {
            await saveShukranEarnedPointsInOrderHistory('ORDER123', 100);

            expect(global.logInfo).toHaveBeenCalledWith('saveShukranEarnedPointsInOrderHistory');
            expect(OrderComment.create).toHaveBeenCalledWith(
                expect.objectContaining({
                    parent_id: 1,
                    comment: 'Shukran Points Earned: 100'
                })
            );
        });
    });

    describe('RMA Status Management', () => {
        const mockStatus = {
            status_id: 1,
            title: 'Pending',
            is_enabled: true
        };

        beforeEach(() => {
            RmaStatus.findOne.mockResolvedValue(mockStatus);
            RmaStatus.update.mockResolvedValue([1]);
        });

        it('should get RMA status details', async () => {
            const result = await getRmaStatusDetails(1);
            expect(result).toEqual(mockStatus);
        });

        it('should update RMA status details', async () => {
            const updateData = {
                statusId: 1,
                isEnabled: true,
                title: 'Updated Status',
                statusCode: 'updated',
                color: '#000000',
                priority: 1
            };

            await updateRmaStatusDetails(updateData);

            expect(RmaStatus.update).toHaveBeenCalledWith(
                expect.objectContaining({
                    is_enabled: true,
                    title: 'Updated Status'
                }),
                expect.objectContaining({
                    where: { status_id: 1 }
                })
            );
        });
    });
});