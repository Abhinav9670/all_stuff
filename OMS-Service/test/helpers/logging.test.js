const { addAdminLog, fetchAdminLogs } = require('../../src/helpers/logging');
const { insertOne, fetchDocs, fetchDocCount } = require('../../src/utils/mongo');

// Mock the mongo utility functions
jest.mock('../../src/utils/mongo', () => ({
    insertOne: jest.fn(),
    fetchDocs: jest.fn(),
    fetchDocCount: jest.fn()
}));

describe('Logging Helper', () => {
    // Store original console methods
    const originalConsoleLog = console.log;

    beforeEach(() => {
        jest.clearAllMocks();
        // Mock console methods
        console.log = jest.fn();
    });

    afterEach(() => {
        // Restore console methods
        console.log = originalConsoleLog;
    });

    describe('addAdminLog', () => {
        it('should throw error when type is missing', async () => {
            await expect(addAdminLog({ data: 'test data' })).rejects.toThrow(
                'type and data are mandatory for logging'
            );
        });

        it('should throw error when data is missing', async () => {
            await expect(addAdminLog({ type: 'test' })).rejects.toThrow(
                'type and data are mandatory for logging'
            );
        });

        it('should stringify data for non-standard types', async () => {
            const testData = { key: 'value' };
            insertOne.mockResolvedValueOnce({ acknowledged: true });

            await addAdminLog({
                type: 'nonStandardType',
                data: testData,
                email: 'test@example.com'
            });

            expect(insertOne).toHaveBeenCalledWith({
                collection: 'adminLogs',
                data: expect.objectContaining({
                    type: 'nonStandardType',
                    data: JSON.stringify(testData),
                    email: 'test@example.com'
                })
            });
        });

        it('should not stringify data for configUpdate type', async () => {
            const testData = { key: 'value' };
            insertOne.mockResolvedValueOnce({ acknowledged: true });

            await addAdminLog({
                type: 'configUpdate',
                data: testData,
                email: 'test@example.com'
            });

            expect(insertOne).toHaveBeenCalledWith({
                collection: 'adminLogs',
                data: expect.objectContaining({
                    type: 'configUpdate',
                    data: testData,
                    email: 'test@example.com'
                })
            });
        });

        it('should not stringify data for customer type', async () => {
            const testData = { key: 'value' };
            insertOne.mockResolvedValueOnce({ acknowledged: true });

            await addAdminLog({
                type: 'customer',
                data: testData,
                email: 'test@example.com'
            });

            expect(insertOne).toHaveBeenCalledWith({
                collection: 'adminLogs',
                data: expect.objectContaining({
                    type: 'customer',
                    data: testData,
                    email: 'test@example.com'
                })
            });
        });

        it('should not stringify data for order type', async () => {
            const testData = { key: 'value' };
            insertOne.mockResolvedValueOnce({ acknowledged: true });

            await addAdminLog({
                type: 'order',
                data: testData,
                email: 'test@example.com'
            });

            expect(insertOne).toHaveBeenCalledWith({
                collection: 'adminLogs',
                data: expect.objectContaining({
                    type: 'order',
                    data: testData,
                    email: 'test@example.com'
                })
            });
        });

        it('should handle file upload flag', async () => {
            insertOne.mockResolvedValueOnce({ acknowledged: true });

            await addAdminLog({
                type: 'test',
                data: 'test data',
                isFileUpload: true,
                email: 'test@example.com'
            });

            expect(insertOne).toHaveBeenCalledWith({
                collection: 'adminLogs',
                data: expect.objectContaining({
                    isFileUpload: true
                })
            });
        });

        it('should include description when provided', async () => {
            insertOne.mockResolvedValueOnce({ acknowledged: true });

            await addAdminLog({
                type: 'test',
                data: 'test data',
                desc: 'Test description',
                email: 'test@example.com'
            });

            expect(insertOne).toHaveBeenCalledWith({
                collection: 'adminLogs',
                data: expect.objectContaining({
                    desc: 'Test description'
                })
            });
        });

        it('should handle errors during insertion', async () => {
            // Setup the mock to simulate a database error
            insertOne.mockImplementationOnce(() => {
                throw new Error('Database error');
            });

            // The function should not throw but log the error
            await addAdminLog({
                type: 'test',
                data: 'test data',
                email: 'test@example.com'
            });

            // Verify error was logged
            expect(console.log).toHaveBeenCalledWith('Error saving admin log!');
            expect(console.log).toHaveBeenCalledWith('Database error');
        });
    });

    describe('fetchAdminLogs', () => {
        it('should fetch logs without query', async () => {
            const mockFilters = { type: 'test' };
            const mockOffset = 0;
            const mockPagesize = 10;

            fetchDocs.mockResolvedValue(['log1', 'log2']);
            fetchDocCount.mockResolvedValue(2);

            const result = await fetchAdminLogs({
                filters: mockFilters,
                offset: mockOffset,
                pagesize: mockPagesize
            });

            expect(fetchDocs).toHaveBeenCalledWith({
                collection: 'adminLogs',
                filters: mockFilters,
                offset: mockOffset,
                sort: { createdAt: -1 },
                pagesize: mockPagesize
            });

            expect(fetchDocCount).toHaveBeenCalledWith({
                collection: 'adminLogs',
                filters: mockFilters
            });

            expect(result).toEqual({
                result: ['log1', 'log2'],
                totalCount: 2
            });
        });

        it('should fetch logs with query', async () => {
            const mockFilters = { type: 'test' };
            const mockQuery = 'searchTerm';
            const expectedFilters = {
                type: 'test',
                $or: [
                    { email: 'searchTerm' },
                    { data: { $regex: 'searchTerm' } }
                ]
            };

            fetchDocs.mockResolvedValue(['log1']);
            fetchDocCount.mockResolvedValue(1);

            const result = await fetchAdminLogs({
                filters: mockFilters,
                query: mockQuery,
                offset: 0,
                pagesize: 10
            });

            expect(fetchDocs).toHaveBeenCalledWith({
                collection: 'adminLogs',
                filters: expectedFilters,
                offset: 0,
                sort: { createdAt: -1 },
                pagesize: 10
            });

            expect(fetchDocCount).toHaveBeenCalledWith({
                collection: 'adminLogs',
                filters: expectedFilters
            });

            expect(result).toEqual({
                result: ['log1'],
                totalCount: 1
            });
        });

        it('should handle empty results', async () => {
            fetchDocs.mockResolvedValue([]);
            fetchDocCount.mockResolvedValue(0);

            const result = await fetchAdminLogs({
                filters: {},
                offset: 0,
                pagesize: 10
            });

            expect(result).toEqual({
                result: [],
                totalCount: 0
            });
        });

        it('should handle pagination correctly', async () => {
            fetchDocs.mockResolvedValue(['log3', 'log4']);
            fetchDocCount.mockResolvedValue(4);

            const result = await fetchAdminLogs({
                filters: {},
                offset: 2,
                pagesize: 2
            });

            expect(fetchDocs).toHaveBeenCalledWith({
                collection: 'adminLogs',
                filters: {},
                offset: 2,
                sort: { createdAt: -1 },
                pagesize: 2
            });

            expect(result).toEqual({
                result: ['log3', 'log4'],
                totalCount: 4
            });
        });
    });
}); 
