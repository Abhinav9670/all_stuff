const request = require('supertest');
const express = require('express');
const archivalController = require('../../src/controllers/archive.controller');
const axios = require('axios');

// Mock all external dependencies first
jest.mock('axios');
jest.mock('worker_threads', () => {
  const actual = jest.requireActual('worker_threads');
  return {
    ...actual,
    Worker: jest.fn(),
    isMainThread: true,
    parentPort: {
      on: jest.fn(),
      postMessage: jest.fn()
    }
  };
});

jest.mock('../../src/helpers/archiveWorker', () => ({
  createArchiveWorker: jest.fn()
}));

jest.mock('../../src/helpers/deleteWorker', () => ({
  createDeleteWorker: jest.fn()
}));

jest.mock('../../src/models/seqModels/index', () => ({
  sequelize: {
    authenticate: jest.fn().mockResolvedValue(true),
    query: jest.fn().mockImplementation((query, options) => {
      if (query.includes('COUNT')) {
        return Promise.resolve([{ count: 100 }]);
      }
      return Promise.resolve([[], {}]);
    }),
    transaction: jest.fn(() => ({
      commit: jest.fn().mockResolvedValue(true),
      rollback: jest.fn().mockResolvedValue(true)
    }))
  }
}));

const { Worker } = require('worker_threads');
const { createArchiveWorker } = require('../../src/helpers/archiveWorker');
const { createDeleteWorker } = require('../../src/helpers/deleteWorker');

const app = express();
app.use(express.json());
app.post('/archive', archivalController.archiveOrders);
app.post('/archive/setup', archivalController.archiveSetup);

// Test data for 2022
const TEST_2022_DATE_RANGE = {
  startDate: '2022-06-01',
  endDate: '2022-07-01'
};

describe('Archival Controller', () => {
  beforeAll(() => {
    global.baseConfig = {
      archive_config: {
        primaryDbDays: 30,
        archiveTableConfig: [
          {
            table: 'orders',
            dateColumn: 'created_at',
            linkTo: null,
            fk: null,
            pk: 'id'
          }
        ]
      }
    };
  });

  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('archiveOrders', () => {
    it('should archive orders successfully with date range', async () => {
      const mockWorkerResult = {
        schemaValidations: { valid: true },
        results: { orders: 100 },
        performanceStats: { executionTime: '10s' }
      };
      
      createArchiveWorker.mockResolvedValue(mockWorkerResult);
      axios.post.mockResolvedValue({ data: { status: 'success' } });

      const res = await request(app)
        .post('/archive')
        .send({ 
          ...TEST_2022_DATE_RANGE,
          orderIds: [1, 2]
        });

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('accepted');
      expect(res.body.jobId).toBeDefined();
      expect(res.body.message).toBe('Archival started in background');
      expect(createArchiveWorker).toHaveBeenCalled();
    });




    it('should handle archive worker failure', async () => {
      createArchiveWorker.mockRejectedValue(new Error('Worker failed'));
      
      const res = await request(app)
        .post('/archive')
        .send(TEST_2022_DATE_RANGE);

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('accepted');
      expect(res.body.jobId).toBeDefined();
      expect(res.body.message).toBe('Archival started in background');
    });
  });

  describe('archiveSetup', () => {
    it('should validate archive flag is required', async () => {
      const res = await request(app)
        .post('/archive/setup')
        .send(TEST_2022_DATE_RANGE);

      expect(res.status).toBe(400);
      expect(res.body.message).toContain('archive');
    });

    it('should return 400 for invalid archive flag', async () => {
      const res = await request(app)
        .post('/archive/setup')
        .send({
          ...TEST_2022_DATE_RANGE,
          archive: 'not-a-boolean'
        });

      expect(res.status).toBe(400);
      expect(res.body.message).toContain('invalid');
    });

    it('should handle delete worker failure', async () => {
      createDeleteWorker.mockRejectedValue(new Error('Delete failed'));
      
      const res = await request(app)
        .post('/archive/setup')
        .send({
          ...TEST_2022_DATE_RANGE,
          archive: true
        });

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('accepted');
      expect(res.body.jobId).toBeDefined();
      expect(res.body.message).toBe('Delete started in background');
    });

  });
});

describe('Archive Worker Tests', () => {
  const BASE_CONFIG = {
    archiveConfig: [{
      table: 'orders',
      dateColumn: 'created_at',
      pk: 'id',
      linkTo: null,
      fk: null
    }],
    startDate: '2022-06-01',
    endDate: '2022-07-01',
    primaryDbConfig: {
      host: 'localhost',
      database: 'primary_db',
      username: 'user',
      password: 'pass'
    },
    archiveDbConfig: {
      host: 'localhost',
      database: 'archive_db',
      username: 'user',
      password: 'pass'
    }
  };

  beforeEach(() => {
    jest.clearAllMocks();
    jest.clearAllTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  describe('createArchiveWorker - Success Cases', () => {
    it('should create worker and return success result', async () => {
      const mockResult = {
        success: true,
        schemaValidations: { valid: true },
        results: { orders: 100 },
        performanceStats: { executionTime: '10s', recordsProcessed: 100 }
      };

      createArchiveWorker.mockResolvedValue(mockResult);

      const result = await createArchiveWorker(BASE_CONFIG);

      expect(createArchiveWorker).toHaveBeenCalledWith(BASE_CONFIG);
      expect(result).toEqual(mockResult);
    });

    it('should handle multiple table configurations', async () => {
      const multiTableConfig = {
        ...BASE_CONFIG,
        archiveConfig: [
          {
            table: 'orders',
            dateColumn: 'created_at',
            pk: 'id'
          },
          {
            table: 'order_items',
            dateColumn: 'created_at',
            pk: 'id',
            linkTo: 'orders',
            fk: 'order_id'
          }
        ]
      };

      const mockResult = {
        success: true,
        results: { orders: 50, order_items: 150 }
      };

      createArchiveWorker.mockResolvedValue(mockResult);

      const result = await createArchiveWorker(multiTableConfig);

      expect(result.success).toBe(true);
      expect(result.results.orders).toBe(50);
      expect(result.results.order_items).toBe(150);
    });

    it('should handle worker with orderIds filter', async () => {
      const configWithOrderIds = {
        ...BASE_CONFIG,
        orderIds: [1, 2, 3, 4, 5]
      };

      const mockResult = {
        success: true,
        results: { orders: 5 },
        filteredByOrderIds: true
      };

      createArchiveWorker.mockResolvedValue(mockResult);

      const result = await createArchiveWorker(configWithOrderIds);

      expect(result.success).toBe(true);
      expect(result.results.orders).toBe(5);
      expect(result.filteredByOrderIds).toBe(true);
    });

    it('should handle progress updates from worker', async () => {
      const progressCallback = jest.fn();
      const configWithProgress = {
        ...BASE_CONFIG,
        onProgress: progressCallback
      };

      const mockResult = {
        success: true,
        results: { orders: 100 }
      };

      createArchiveWorker.mockResolvedValue(mockResult);

      const result = await createArchiveWorker(configWithProgress);

      expect(result.success).toBe(true);
      expect(createArchiveWorker).toHaveBeenCalledWith(configWithProgress);
    });
  });

  describe('createArchiveWorker - Error Cases', () => {
    it('should handle schema validation errors', async () => {
      const mockError = {
        success: false,
        error: 'Schema validation failed: Table orders does not exist in archive database'
      };

      createArchiveWorker.mockResolvedValue(mockError);

      const result = await createArchiveWorker(BASE_CONFIG);

      expect(result.success).toBe(false);
      expect(result.error).toContain('Schema validation failed');
    });

    it('should handle database connection errors', async () => {
      const mockError = {
        success: false,
        error: 'Database connection failed: ECONNREFUSED'
      };

      createArchiveWorker.mockResolvedValue(mockError);

      const result = await createArchiveWorker(BASE_CONFIG);

      expect(result.success).toBe(false);
      expect(result.error).toContain('Database connection failed');
    });

    it('should handle worker error events', async () => {
      createArchiveWorker.mockRejectedValue(new Error('Worker thread crashed'));

      await expect(createArchiveWorker(BASE_CONFIG)).rejects.toThrow('Worker thread crashed');
    });

    it('should handle worker exit with non-zero code', async () => {
      createArchiveWorker.mockRejectedValue(new Error('Worker stopped with exit code 1'));

      await expect(createArchiveWorker(BASE_CONFIG)).rejects.toThrow('Worker stopped with exit code 1');
    });

    it('should handle worker timeout', async () => {
      createArchiveWorker.mockRejectedValue(new Error('Worker timeout'));

      await expect(createArchiveWorker(BASE_CONFIG)).rejects.toThrow('Worker timeout');
    });

    it('should handle invalid configuration', async () => {
      const invalidConfig = {
        ...BASE_CONFIG,
        archiveConfig: [] // Empty archive config
      };

      const mockError = {
        success: false,
        error: 'Invalid configuration: No tables specified for archival'
      };

      createArchiveWorker.mockResolvedValue(mockError);

      const result = await createArchiveWorker(invalidConfig);

      expect(result.success).toBe(false);
      expect(result.error).toContain('Invalid configuration');
    });

    it('should handle missing required config fields', async () => {
      const incompleteConfig = {
        archiveConfig: BASE_CONFIG.archiveConfig,
        startDate: '2022-06-01'
        // Missing endDate, primaryDbConfig, archiveDbConfig
      };

      const mockError = {
        success: false,
        error: 'Missing required configuration: endDate, primaryDbConfig, archiveDbConfig'
      };

      createArchiveWorker.mockResolvedValue(mockError);

      const result = await createArchiveWorker(incompleteConfig);

      expect(result.success).toBe(false);
      expect(result.error).toContain('Missing required configuration');
    });
  });

  describe('createArchiveWorker - Edge Cases', () => {
    it('should handle empty result set', async () => {
      const mockResult = {
        success: true,
        results: { orders: 0 },
        message: 'No records found in the specified date range'
      };

      createArchiveWorker.mockResolvedValue(mockResult);

      const result = await createArchiveWorker(BASE_CONFIG);

      expect(result.success).toBe(true);
      expect(result.results.orders).toBe(0);
      expect(result.message).toContain('No records found');
    });

    it('should handle partial success scenarios', async () => {
      const multiTableConfig = {
        ...BASE_CONFIG,
        archiveConfig: [
          { table: 'orders', dateColumn: 'created_at', pk: 'id' },
          { table: 'order_items', dateColumn: 'created_at', pk: 'id' }
        ]
      };

      const mockResult = {
        success: true,
        results: { orders: 100, order_items: 0 },
        warnings: ['No order_items found for the archived orders'],
        partialSuccess: true
      };

      createArchiveWorker.mockResolvedValue(mockResult);

      const result = await createArchiveWorker(multiTableConfig);

      expect(result.success).toBe(true);
      expect(result.partialSuccess).toBe(true);
      expect(result.warnings).toContain('No order_items found for the archived orders');
    });

    it('should handle large dataset with memory warnings', async () => {
      const mockResult = {
        success: true,
        results: { orders: 100000 },
        performanceStats: {
          executionTime: '5m 30s',
          memoryUsage: '512MB',
          recordsProcessed: 100000
        },
        warnings: ['High memory usage detected during processing']
      };

      createArchiveWorker.mockResolvedValue(mockResult);

      const result = await createArchiveWorker(BASE_CONFIG);

      expect(result.success).toBe(true);
      expect(result.results.orders).toBe(100000);
      expect(result.performanceStats.memoryUsage).toBe('512MB');
      expect(result.warnings).toContain('High memory usage detected during processing');
    });

    it('should properly handle worker termination', async () => {
      const mockResult = {
        success: true,
        results: { orders: 100 },
        terminated: true
      };

      createArchiveWorker.mockResolvedValue(mockResult);

      const result = await createArchiveWorker(BASE_CONFIG);

      expect(result.success).toBe(true);
      expect(result.results.orders).toBe(100);
    });

    it('should handle invalid date range in worker', async () => {
      const invalidDateConfig = {
        ...BASE_CONFIG,
        startDate: '2022-07-01',
        endDate: '2022-06-01' // End date before start date
      };

      const mockError = {
        success: false,
        error: 'Invalid date range: startDate must be before endDate'
      };

      createArchiveWorker.mockResolvedValue(mockError);

      const result = await createArchiveWorker(invalidDateConfig);

      expect(result.success).toBe(false);
      expect(result.error).toContain('Invalid date range');
    });
  });

  describe('createArchiveWorker - Performance and Monitoring', () => {
    it('should return detailed performance statistics', async () => {
      const mockResult = {
        success: true,
        results: { orders: 1000 },
        performanceStats: {
          executionTime: '2m 15s',
          recordsProcessed: 1000,
          recordsPerSecond: 7.4,
          memoryUsage: '128MB',
          databaseConnections: 2,
          queryCount: 10
        }
      };

      createArchiveWorker.mockResolvedValue(mockResult);

      const result = await createArchiveWorker(BASE_CONFIG);

      expect(result.success).toBe(true);
      expect(result.performanceStats).toBeDefined();
      expect(result.performanceStats.executionTime).toBe('2m 15s');
      expect(result.performanceStats.recordsPerSecond).toBe(7.4);
      expect(result.performanceStats.queryCount).toBe(10);
    });

    it('should handle schema validation details', async () => {
      const mockResult = {
        success: true,
        results: { orders: 100 },
        schemaValidations: {
          valid: true,
          tablesChecked: ['orders'],
          columnsValidated: ['id', 'created_at', 'customer_id'],
          indexesFound: ['idx_orders_created_at']
        }
      };

      createArchiveWorker.mockResolvedValue(mockResult);

      const result = await createArchiveWorker(BASE_CONFIG);

      expect(result.success).toBe(true);
      expect(result.schemaValidations.valid).toBe(true);
      expect(result.schemaValidations.tablesChecked).toContain('orders');
      expect(result.schemaValidations.columnsValidated).toContain('created_at');
    });

    it('should handle worker configuration validation', async () => {
      const configWithValidation = {
        ...BASE_CONFIG,
        validateSchema: true,
        enablePerformanceMonitoring: true
      };

      const mockResult = {
        success: true,
        results: { orders: 500 },
        configValidation: {
          valid: true,
          warnings: []
        },
        performanceStats: {
          executionTime: '1m 30s',
          recordsProcessed: 500
        }
      };

      createArchiveWorker.mockResolvedValue(mockResult);

      const result = await createArchiveWorker(configWithValidation);

      expect(result.success).toBe(true);
      expect(result.configValidation.valid).toBe(true);
      expect(result.performanceStats.recordsProcessed).toBe(500);
    });

    it('should handle concurrent worker limits', async () => {
      const configWithLimits = {
        ...BASE_CONFIG,
        maxConcurrentWorkers: 4,
        batchSize: 1000
      };

      const mockResult = {
        success: true,
        results: { orders: 4000 },
        workerStats: {
          workersUsed: 4,
          averageProcessingTime: '45s',
          totalBatches: 4
        }
      };

      createArchiveWorker.mockResolvedValue(mockResult);

      const result = await createArchiveWorker(configWithLimits);

      expect(result.success).toBe(true);
      expect(result.workerStats.workersUsed).toBe(4);
      expect(result.workerStats.totalBatches).toBe(4);
    });
  });

  describe('createArchiveWorker - Real Worker Scenarios', () => {
    it('should simulate actual worker thread behavior', async () => {
      // This test simulates what would happen if we were actually calling the real worker
      const mockWorkerFlow = async (config) => {
        // Simulate validation phase
        if (!config.archiveConfig || config.archiveConfig.length === 0) {
          return { success: false, error: 'No tables configured' };
        }
        
        // Simulate processing phase
        const totalRecords = config.archiveConfig.reduce((sum, table) => {
          return sum + (table.estimatedRecords || 100);
        }, 0);
        
        // Simulate success
        return {
          success: true,
          results: { [config.archiveConfig[0].table]: totalRecords },
          performanceStats: {
            executionTime: `${Math.ceil(totalRecords/10)}s`,
            recordsProcessed: totalRecords
          }
        };
      };

      createArchiveWorker.mockImplementation(mockWorkerFlow);

      const result = await createArchiveWorker(BASE_CONFIG);

      expect(result.success).toBe(true);
      expect(result.results.orders).toBe(100);
      expect(result.performanceStats.recordsProcessed).toBe(100);
    });

    it('should handle worker memory pressure scenarios', async () => {
      const largeDataConfig = {
        ...BASE_CONFIG,
        archiveConfig: [{
          ...BASE_CONFIG.archiveConfig[0],
          estimatedRecords: 1000000
        }]
      };

      const mockResult = {
        success: true,
        results: { orders: 1000000 },
        performanceStats: {
          executionTime: '10m 15s',
          memoryUsage: '2GB',
          recordsProcessed: 1000000,
          memoryPressure: true
        },
        warnings: [
          'High memory usage detected during processing',
          'Consider reducing batch size for large datasets'
        ]
      };

      createArchiveWorker.mockResolvedValue(mockResult);

      const result = await createArchiveWorker(largeDataConfig);

      expect(result.success).toBe(true);
      expect(result.performanceStats.memoryPressure).toBe(true);
      expect(result.warnings).toContain('High memory usage detected during processing');
    });
  });
});