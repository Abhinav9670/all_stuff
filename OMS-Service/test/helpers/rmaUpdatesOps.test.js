// Mock dotenv-expand to prevent recursion
jest.mock('dotenv-expand', () => jest.fn((env) => env));

jest.mock('../../src/models/seqModels/index');
jest.mock('../../src/helpers/utilities');

const {
    RmaRequest,
    RmaRequestItem,
    OrderComment
} = require('../../src/models/seqModels/index');
const {
    updateRmaStatus,
    setShortPickedup,
    updateRmaItemStatus,
    updateRmaHistory
} = require('../../src/helpers/rmaUpdateOps');
const { updateStatusHistory } = require('../../src/helpers/utilities');
const { RECEIVED_BY_ADMIN_STATUS_CODE } = require('../../src/constants/order');

describe('RMA Update Operations', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        global.logInfo = jest.fn();
        global.logError = jest.fn();
    });

    describe('updateRmaStatus', () => {
        const mockRmaStatusData = [
            { status_code: 'received', status_id: 1 },
            { status_code: 'dropped_off', status_id: 2 },
            { status_code: RECEIVED_BY_ADMIN_STATUS_CODE, status_id: 3 }
        ];

        const mockParams = {
            rmaId: 1,
            status: 'received',
            rmaStatusData: mockRmaStatusData,
            orderId: 100,
            waybill: 'AWB123',
            currentOrderStatus: 'processing',
            rmaItems: [
                { request_item_id: 1, item_status: 1 },
                { request_item_id: 2, item_status: 1 }
            ]
        };

        beforeEach(() => {
            RmaRequest.update.mockResolvedValue([1]);
            RmaRequestItem.update.mockResolvedValue([1]);
            OrderComment.create.mockResolvedValue({});
            updateStatusHistory.mockResolvedValue({});
        });

        it('should update RMA status successfully', async () => {
            const result = await updateRmaStatus(mockParams);

            expect(result).toBe(true);
            expect(RmaRequest.update).toHaveBeenCalledWith(
                { status: 1 },
                { where: { request_id: 1 } }
            );
        });

        it('should handle short pickup case', async () => {
            const result = await updateRmaStatus({
                ...mockParams,
                isShortPickup: true
            });

            expect(result).toBe(true);
            expect(RmaRequest.update).toHaveBeenCalledWith(
                { status: 1, is_short_pickedup: 1 },
                { where: { request_id: 1 } }
            );
        });

        it('should handle fraud pickup case', async () => {
            const result = await updateRmaStatus({
                ...mockParams,
                isFraudPickup: true
            });

            expect(result).toBe(true);
            expect(RmaRequest.update).toHaveBeenCalledWith(
                { status: 1, is_fraud_pickedup: 1 },
                { where: { request_id: 1 } }
            );
        });

        it('should create order comment for received_by_admin status', async () => {
            await updateRmaStatus({
                ...mockParams,
                status: RECEIVED_BY_ADMIN_STATUS_CODE
            });

            expect(OrderComment.create).toHaveBeenCalledWith({
                parent_id: 100,
                comment: 'RMA: AWB123 received at warehouse',
                status: 'processing',
                entity_name: 'rma'
            });
        });

        it('should handle auto-refund cases', async () => {
            await updateRmaStatus({
                ...mockParams,
                returnType: 'refund',
                isPaymentAutoRefunded: true,
                status: 'received'
            });

            expect(RmaRequest.update).toHaveBeenCalledWith(
                { status: 2 }, // dropped_off status
                { where: { request_id: 1 } }
            );
        });
    });

    describe('updateRmaItemStatus', () => {
        const mockRmaItems = [
            { request_item_id: 1, item_status: 1 },
            { request_item_id: 2, item_status: 2 },
            { request_item_id: 3, item_status: 12 } // Should be excluded
        ];

        it('should update items with itemStatusMap', async () => {
            const itemStatusMap = {
                1: { status: 5, actualRetrunedQty: 1, qcFailedQty: 0 },
                2: { status: 6, actualRetrunedQty: 2, qcFailedQty: 1 }
            };

            await updateRmaItemStatus({
                rmaItems: [{ request_id: 100, request_item_id: 1 }],
                itemStatusMap
            });

            expect(RmaRequestItem.update).toHaveBeenCalledWith(
                {
                    item_status: 5,
                    actual_qty_returned: 1,
                    qc_failed_qty: 0
                },
                {
                    where: { order_item_id: '1', request_id: 100 }
                }
            );
        });

        it('should handle errors in itemStatusMap updates', async () => {
            const itemStatusMap = {
                1: { status: 5, actualRetrunedQty: 1, qcFailedQty: 0 }
            };
            RmaRequestItem.update.mockRejectedValue(new Error('DB Error'));

            await updateRmaItemStatus({
                rmaItems: [{ request_id: 100, request_item_id: 1 }],
                itemStatusMap
            });

            expect(global.logError).toHaveBeenCalled();
        });
    });

    describe('setShortPickedup', () => {
        const mockParams = {
            rmaId: 1,
            rmaVerificationStatusId: 5,
            rmaItems: [{ request_item_id: 1 }],
            returnType: 'refund',
            rmaStatusData: [],
            timestamp: '2024-01-01T00:00:00Z',
            orderId: 100
        };

        it('should set short pickup status successfully', async () => {
            RmaRequest.update.mockResolvedValue([1]);
            RmaRequestItem.update.mockResolvedValue([1]);

            const result = await setShortPickedup(mockParams);

            expect(result).toEqual({ status: true, msg: 'success' });
            expect(RmaRequest.update).toHaveBeenCalledWith(
                { status: 5, is_short_pickedup: 1 },
                { where: { request_id: 1 } }
            );
        });
    });

    describe('updateRmaHistory', () => {
        it('should update status history for valid status', async () => {
            const promiseArray = [];
            await updateRmaHistory({
                statusId: '19',
                status: 'picked_up',
                orderId: 100,
                timestamp: '2024-01-01T00:00:00Z'
            }, promiseArray);

            expect(promiseArray).toHaveLength(1);
        });

        it('should handle refunded status', async () => {
            const promiseArray = [];
            await updateRmaHistory({
                statusId: '19',
                status: 'picked_up',
                orderId: 100,
                timestamp: '2024-01-01T00:00:00Z',
                isRefunded: true
            }, promiseArray);

            expect(promiseArray).toHaveLength(1);
        });

        it('should not update for invalid status', async () => {
            const promiseArray = [];
            await updateRmaHistory({
                statusId: '999',
                status: 'invalid_status',
                orderId: 100,
                timestamp: '2024-01-01T00:00:00Z'
            }, promiseArray);

            expect(promiseArray).toHaveLength(0);
        });
    });
});
