/* eslint-disable no-undef */
// Mock MongoDB and environment variables before requiring the module
jest.mock('../../src/utils/mongoInit', () => ({
    getDb: jest.fn()
}));

jest.mock('../../src/models/seqModels/index', () => ({
    sequelize: {
        authenticate: jest.fn()
    }
}));

// Set environment variables before requiring the module
process.env.MONGO_NODE_COUNT = '3';

const mongoUtil = require('../../src/utils/mongoInit');
const { sequelize } = require('../../src/models/seqModels/index');
const { checkMongoHealth, checkMysqlHealth } = require('../../src/helpers/health');

describe('Health Check Functions', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        // Ensure MONGO_NODE_COUNT is set for each test
        process.env.MONGO_NODE_COUNT = '3';
        console.log = jest.fn(); // Mock console.log
        console.error = jest.fn(); // Mock console.error
    });

    afterEach(() => {
        // Clean up environment variables after each test
        process.env.MONGO_NODE_COUNT = '3';
    });

    describe('checkMongoHealth', () => {
        it('should return unhealthy status when some nodes are down', async () => {
            const mockHosts = ['node1', 'node2']; // Only 2 nodes when expecting 3
            const mockDb = {
                command: jest.fn().mockResolvedValue({
                    hosts: mockHosts,
                    ok: 1
                })
            };
            mongoUtil.getDb.mockReturnValue(mockDb);

            const result = await checkMongoHealth();

            expect(result).toEqual({
                mongoHealth: false,
                activeNodes: mockHosts.length
            });
            expect(mockDb.command).toHaveBeenCalledWith({ isMaster: 1 });
            expect(console.log).toHaveBeenCalled();
        });

        it('should handle MongoDB connection errors', async () => {
            const mockDb = {
                command: jest.fn().mockRejectedValue(new Error('Connection failed'))
            };
            mongoUtil.getDb.mockReturnValue(mockDb);

            const result = await checkMongoHealth();

            expect(result).toEqual({
                mongoHealth: false,
                activeNodes: 0
            });
            expect(mockDb.command).toHaveBeenCalledWith({ isMaster: 1 });
            expect(console.log).toHaveBeenCalledWith(
                'Connection failed',
                '[checkMongoV2] - IsMaster Error'
            );
        });

        it('should handle null or undefined MongoDB response', async () => {
            const mockDb = {
                command: jest.fn().mockResolvedValue({
                    ok: 1,
                    hosts: undefined
                })
            };
            mongoUtil.getDb.mockReturnValue(mockDb);

            const result = await checkMongoHealth();

            expect(result).toEqual({
                mongoHealth: false,
                activeNodes: 0
            });
        });
    });

    describe('checkMysqlHealth', () => {
        it('should return true when MySQL connection is successful', async () => {
            sequelize.authenticate.mockResolvedValue();

            const result = await checkMysqlHealth();

            expect(result).toBe(true);
            expect(sequelize.authenticate).toHaveBeenCalled();
        });

        it('should return false when MySQL connection fails', async () => {
            sequelize.authenticate.mockRejectedValue(new Error('Connection failed'));

            const result = await checkMysqlHealth();

            expect(result).toBe(false);
            expect(sequelize.authenticate).toHaveBeenCalled();
            expect(console.error).toHaveBeenCalledWith(
                'Unable to connect to the My sql:',
                expect.any(Error)
            );
        });

        it('should handle unexpected MySQL errors gracefully', async () => {
            sequelize.authenticate.mockRejectedValue(null);

            const result = await checkMysqlHealth();

            expect(result).toBe(false);
            expect(sequelize.authenticate).toHaveBeenCalled();
            expect(console.error).toHaveBeenCalled();
        });
    });
}); 
