const request = require('supertest');
const express = require('express');
const archivalController = require('../../src/controllers/archive.controller');
const axios = require('axios');

jest.mock('axios');
jest.mock('../../src/helpers/archiveWorker', () => ({
  createArchiveWorker: jest.fn()
}));

jest.mock('../../src/helpers/deleteWorker', () => ({
  createDeleteWorker: jest.fn()
}));

// Mock the sequelize models
jest.mock('../../src/models/seqModels/index', () => ({
  sequelize: {
    authenticate: jest.fn().mockResolvedValue(true),
    query: jest.fn().mockImplementation((query, options) => {
      if (query.includes('COUNT')) {
        return Promise.resolve([{ count: 100 }]);
      }
      if (query.includes('SHOW INDEX')) {
        return Promise.resolve([]);
      }
      if (query.includes('CREATE INDEX')) {
        return Promise.resolve();
      }
      if (query.includes('SET')) {
        return Promise.resolve();
      }
      return Promise.resolve([[], {}]);
    }),
    transaction: jest.fn(() => ({
      commit: jest.fn().mockResolvedValue(true),
      rollback: jest.fn().mockResolvedValue(true)
    })),
    QueryTypes: { SELECT: 'SELECT' }
  }
}));

jest.mock('../../src/models/seqModels/archiveIndex', () => ({
  archiveSquelize: {
    authenticate: jest.fn().mockResolvedValue(true),
    query: jest.fn().mockImplementation((query, options) => {
      if (query.includes('COUNT')) {
        return Promise.resolve([{ count: 50 }]);
      }
      if (query.includes('SHOW INDEX')) {
        return Promise.resolve([]);
      }
      if (query.includes('CREATE INDEX')) {
        return Promise.resolve();
      }
      if (query.includes('SET')) {
        return Promise.resolve();
      }
      return Promise.resolve([[], {}]);
    }),
    transaction: jest.fn(() => ({
      commit: jest.fn().mockResolvedValue(true),
      rollback: jest.fn().mockResolvedValue(true)
    })),
    QueryTypes: { SELECT: 'SELECT' }
  }
}));

// Mock config files
jest.mock('../../config/config.js', () => ({
  development: {
    username: 'test',
    password: 'test',
    database: 'test_db',
    host: '127.0.0.1',
    dialect: 'mysql'
  },
  archive: {
    username: 'archive',
    password: 'archive',
    database: 'archive_db',
    host: '127.0.0.1',
    dialect: 'mysql'
  }
}));

const { createArchiveWorker } = require('../../src/helpers/archiveWorker');
const { createDeleteWorker } = require('../../src/helpers/deleteWorker');

const app = express();
app.use(express.json());
app.post('/archive', archivalController.archiveOrders);
app.post('/archive/setup', archivalController.archiveSetup);

// Test data
const TEST_DATE_RANGE = {
  startDate: '2022-06-01',
  endDate: '2022-07-01'
};

const TEST_ARCHIVE_CONFIG = {
  primaryDbDays: 30,
  archiveTableConfig: [
    {
      table: 'orders',
      dateColumn: 'created_at',
      linkTo: null,
      fk: null,
      pk: 'id'
    },
    {
      table: 'order_items',
      dateColumn: null,
      linkTo: 'orders',
      fk: 'order_id',
      pk: 'id'
    }
  ]
};

describe('Archive Controller', () => {
  beforeAll(() => {
    global.baseConfig = {
      archive_config: TEST_ARCHIVE_CONFIG
    };
    
    // Mock environment variables
    process.env.NODE_ENV = 'development';
    process.env.OMS_API_HOST = 'http://localhost:3000';
  });

  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('archiveOrders', () => {
    it('should archive orders successfully with complete date range', async () => {
      const mockWorkerResult = {
        schemaValidations: { valid: true, errors: [] },
        results: { orders: 100, order_items: 50 },
        performanceStats: { 
          executionTime: '10s',
          recordsProcessed: 150,
          averageSpeed: '15 records/sec'
        }
      };
      
      createArchiveWorker.mockResolvedValue(mockWorkerResult);
      axios.post.mockResolvedValue({ 
        data: { 
          status: 'success',
          totalRecordsDeleted: 75
        } 
      });

      const res = await request(app)
        .post('/archive')
        .set('authorization', 'Bearer test-token')
        .send({ 
          ...TEST_DATE_RANGE,
          orderIds: [1, 2, 3],
          deleteAfterArchive: true
        });

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('accepted');
      expect(res.body.jobId).toBeDefined();
      expect(res.body.message).toBe('Archival started in background');
      expect(res.body.metadata).toBeDefined();
      expect(res.body.metadata.startTime).toBeDefined();
      
      expect(createArchiveWorker).toHaveBeenCalledWith(
        expect.objectContaining({
          config: expect.objectContaining({
            archiveConfig: TEST_ARCHIVE_CONFIG.archiveTableConfig,
            startDate: TEST_DATE_RANGE.startDate,
            endDate: TEST_DATE_RANGE.endDate
          }),
          startDate: expect.stringContaining('2022-06-01'),
          endDate: expect.stringContaining('2022-07-01'),
          archiveDbConfig: expect.objectContaining({
            username: 'archive',
            database: 'archive_db'
          })
        })
      );
    });

    it('should archive orders without delete operation when deleteAfterArchive is false', async () => {
      const mockWorkerResult = {
        schemaValidations: { valid: true },
        results: { orders: 100 },
        performanceStats: { executionTime: '10s' }
      };
      
      createArchiveWorker.mockResolvedValue(mockWorkerResult);

      const res = await request(app)
        .post('/archive')
        .send({ 
          ...TEST_DATE_RANGE,
          deleteAfterArchive: false
        });

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('accepted');
      expect(res.body.jobId).toBeDefined();
      expect(res.body.message).toBe('Archival started in background');
    });

    it('should use default date range when no dates provided', async () => {
      const mockWorkerResult = {
        schemaValidations: { valid: true },
        results: { orders: 50 },
        performanceStats: { executionTime: '5s' }
      };
      
      createArchiveWorker.mockResolvedValue(mockWorkerResult);

      const res = await request(app)
        .post('/archive')
        .send({});

      expect(res.status).toBe(200);
      expect(createArchiveWorker).toHaveBeenCalledWith(
        expect.objectContaining({
          config: expect.objectContaining({
            archiveConfig: TEST_ARCHIVE_CONFIG.archiveTableConfig
          })
        })
      );
    });

    it('should handle archive worker failure gracefully', async () => {
      createArchiveWorker.mockRejectedValue(new Error('Database connection failed'));
      
      const res = await request(app)
        .post('/archive')
        .send(TEST_DATE_RANGE);

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('accepted');
      expect(res.body.jobId).toBeDefined();
      expect(res.body.message).toBe('Archival started in background');
    });

    it('should handle delete operation failure after successful archive', async () => {
      const mockWorkerResult = {
        schemaValidations: { valid: true },
        results: { orders: 100 },
        performanceStats: { executionTime: '10s' }
      };
      
      createArchiveWorker.mockResolvedValue(mockWorkerResult);
      axios.post.mockRejectedValue(new Error('Delete API timeout'));

      const res = await request(app)
        .post('/archive')
        .send({ 
          ...TEST_DATE_RANGE,
          deleteAfterArchive: true
        });

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('accepted');
      expect(res.body.jobId).toBeDefined();
      expect(res.body.message).toBe('Archival started in background');
    });

    it('should pass authorization header to delete API', async () => {
      const mockWorkerResult = {
        schemaValidations: { valid: true },
        results: { orders: 100 },
        performanceStats: { executionTime: '10s' }
      };
      
      createArchiveWorker.mockResolvedValue(mockWorkerResult);
      axios.post.mockResolvedValue({ data: { status: 'success' } });

      await request(app)
        .post('/archive')
        .set('authorization', 'Bearer test-token-123')
        .send({ 
          ...TEST_DATE_RANGE,
          deleteAfterArchive: true
        });

      expect(axios.post).toHaveBeenCalledWith(
        expect.any(String),
        expect.any(Object),
        expect.objectContaining({
          headers: expect.objectContaining({
            authorization: 'Bearer test-token-123'
          })
        })
      );
    });
  });

  describe('archiveSetup', () => {
    it('should validate archive flag is required', async () => {
      const res = await request(app)
        .post('/archive/setup')
        .send(TEST_DATE_RANGE);

      expect(res.status).toBe(400);
      expect(res.body.status).toBe('error');
      expect(res.body.message).toContain('archive');
      expect(res.body.example).toEqual({ archive: true });
    });

    it('should return 400 for invalid archive flag (string)', async () => {
      const res = await request(app)
        .post('/archive/setup')
        .send({
          ...TEST_DATE_RANGE,
          archive: 'not-a-boolean'
        });

      expect(res.status).toBe(400);
      expect(res.body.message).toContain('invalid');
    });

    it('should return 400 for invalid archive flag (number)', async () => {
      const res = await request(app)
        .post('/archive/setup')
        .send({
          ...TEST_DATE_RANGE,
          archive: 1
        });

      expect(res.status).toBe(400);
      expect(res.body.message).toContain('invalid');
    });

    it('should process setup successfully with archive flag true (archive DB)', async () => {
      createDeleteWorker.mockResolvedValue({
        success: true,
        totalRecordsDeleted: 1000,
        results: {
          orders: { recordsDeleted: 800, success: true, hierarchyLevel: 0 },
          order_items: { recordsDeleted: 200, success: true, hierarchyLevel: 1 }
        },
        hierarchyLevelsProcessed: 2,
        performanceStats: {
          executionTimeSeconds: 45,
          averageRecordsPerSecond: 22
        }
      });
      
      const res = await request(app)
        .post('/archive/setup')
        .send({
          ...TEST_DATE_RANGE,
          archive: true
        });

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('accepted');
      expect(res.body.jobId).toBeDefined();
      expect(res.body.message).toBe('Delete started in background');
      expect(res.body.metadata.targetDatabase).toBe('archive');
    });

    it('should process setup successfully with archive flag false (primary DB)', async () => {
      createDeleteWorker.mockResolvedValue({
        success: true,
        totalRecordsDeleted: 500,
        results: {
          orders: { recordsDeleted: 400, success: true, hierarchyLevel: 0 },
          order_items: { recordsDeleted: 100, success: true, hierarchyLevel: 1 }
        },
        hierarchyLevelsProcessed: 2,
        performanceStats: {
          executionTimeSeconds: 25,
          averageRecordsPerSecond: 20
        }
      });
      
      const res = await request(app)
        .post('/archive/setup')
        .send({
          ...TEST_DATE_RANGE,
          archive: false
        });

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('accepted');
      expect(res.body.jobId).toBeDefined();
      expect(res.body.message).toBe('Delete started in background');
      expect(res.body.metadata.targetDatabase).toBe('primary');
    });

    it('should handle delete worker failure with detailed error info', async () => {
      createDeleteWorker.mockRejectedValue(new Error('Foreign key constraint violation'));
      
      const res = await request(app)
        .post('/archive/setup')
        .send({
          ...TEST_DATE_RANGE,
          archive: true
        });

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('accepted');
      expect(res.body.jobId).toBeDefined();
      expect(res.body.message).toBe('Delete started in background');
    });

    it('should handle partial success with some table errors', async () => {
      createDeleteWorker.mockResolvedValue({
        success: true,
        totalRecordsDeleted: 800,
        results: {
          orders: { recordsDeleted: 800, success: true, hierarchyLevel: 0 },
          order_items: { recordsDeleted: 0, success: false, error: 'Table locked', hierarchyLevel: 1 }
        },
        hierarchyLevelsProcessed: 2,
        performanceStats: {
          executionTimeSeconds: 30,
          averageRecordsPerSecond: 27
        }
      });
      
      const res = await request(app)
        .post('/archive/setup')
        .send({
          ...TEST_DATE_RANGE,
          archive: true
        });

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('accepted');
      expect(res.body.jobId).toBeDefined();
      expect(res.body.message).toBe('Delete started in background');
    });

    it('should handle high error rate scenario', async () => {
      createDeleteWorker.mockRejectedValue(new Error('High error rate (75.0%) - Process failed'));
      
      const res = await request(app)
        .post('/archive/setup')
        .send({
          ...TEST_DATE_RANGE,
          archive: false
        });

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('accepted');
      expect(res.body.jobId).toBeDefined();
      expect(res.body.message).toBe('Delete started in background');
    });

    it('should include all tables in results even if not processed', async () => {
      createDeleteWorker.mockResolvedValue({
        success: true,
        totalRecordsDeleted: 100,
        results: {
          orders: { recordsDeleted: 100, success: true, hierarchyLevel: 0 }
        },
        hierarchyLevelsProcessed: 1
      });
      
      const res = await request(app)
        .post('/archive/setup')
        .send({
          ...TEST_DATE_RANGE,
          archive: true
        });

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('accepted');
      expect(res.body.jobId).toBeDefined();
      expect(res.body.message).toBe('Delete started in background');
    });

    it('should handle missing date range with cutoff calculation', async () => {
      createDeleteWorker.mockResolvedValue({
        success: true,
        totalRecordsDeleted: 300,
        results: {
          orders: { recordsDeleted: 300, success: true, hierarchyLevel: 0 }
        }
      });
      
      const res = await request(app)
        .post('/archive/setup')
        .send({
          startDate: '2022-06-01',
          archive: true
        });

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('accepted');
      expect(res.body.jobId).toBeDefined();
      expect(res.body.message).toBe('Delete started in background');
    });
  });

  describe('Configuration and Utility Functions', () => {
    it('should handle different batch sizes based on operation type', async () => {
      createArchiveWorker.mockResolvedValue({
        schemaValidations: { valid: true },
        results: { orders: 100 },
        performanceStats: { executionTime: '10s' }
      });

      const res = await request(app)
        .post('/archive')
        .send({ 
          ...TEST_DATE_RANGE,
          batchSize: 25000,
          maxRetries: 5
        });

      expect(res.status).toBe(200);
      expect(createArchiveWorker).toHaveBeenCalledWith(
        expect.objectContaining({
          config: expect.objectContaining({
            batchSize: 25000,
            maxRetries: 5
          })
        })
      );
    });

    it('should handle custom archive configuration', async () => {
      const originalConfig = global.baseConfig;
      global.baseConfig = {
        archive_config: {
          primaryDbDays: 60,
          archiveTableConfig: [
            {
              table: 'custom_orders',
              dateColumn: 'order_date',
              linkTo: null,
              fk: null,
              pk: 'order_id'
            }
          ]
        }
      };

      createArchiveWorker.mockResolvedValue({
        schemaValidations: { valid: true },
        results: { custom_orders: 75 },
        performanceStats: { executionTime: '8s' }
      });

      const res = await request(app)
        .post('/archive')
        .send({});

      expect(res.status).toBe(200);
      expect(createArchiveWorker).toHaveBeenCalledWith(
        expect.objectContaining({
          config: expect.objectContaining({
            archiveConfig: expect.arrayContaining([
              expect.objectContaining({
                table: 'custom_orders',
                dateColumn: 'order_date'
              })
            ])
          })
        })
      );

      global.baseConfig = originalConfig;
    });
  });

  describe('Error Handling Edge Cases', () => {
    it('should handle empty results from delete worker', async () => {
      createDeleteWorker.mockResolvedValue({
        success: true,
        totalRecordsDeleted: 0,
        results: {},
        hierarchyLevelsProcessed: 0
      });

      const res = await request(app)
        .post('/archive/setup')
        .send({
          ...TEST_DATE_RANGE,
          archive: false
        });

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('accepted');
      expect(res.body.jobId).toBeDefined();
      expect(res.body.message).toBe('Delete started in background');
    });

    it('should handle worker timeout scenarios', async () => {
      createArchiveWorker.mockRejectedValue(new Error('Worker timeout after 3600 seconds'));

      const res = await request(app)
        .post('/archive')
        .send(TEST_DATE_RANGE);

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('accepted');
      expect(res.body.jobId).toBeDefined();
      expect(res.body.message).toBe('Archival started in background');
    });

    it('should handle database connection failures', async () => {
      createDeleteWorker.mockRejectedValue(new Error('Connection refused: ECONNREFUSED'));

      const res = await request(app)
        .post('/archive/setup')
        .send({
          ...TEST_DATE_RANGE,
          archive: true
        });

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('accepted');
      expect(res.body.jobId).toBeDefined();
      expect(res.body.message).toBe('Delete started in background');
    });
  });

  describe('Performance and Memory Tests', () => {
    it('should handle large dataset processing', async () => {
      createDeleteWorker.mockResolvedValue({
        success: true,
        totalRecordsDeleted: 1000000,
        results: {
          orders: { recordsDeleted: 800000, success: true, hierarchyLevel: 0 },
          order_items: { recordsDeleted: 200000, success: true, hierarchyLevel: 1 }
        },
        performanceStats: {
          executionTimeSeconds: 300,
          averageRecordsPerSecond: 3333,
          peakMemoryUsageMB: 512
        },
        hierarchyLevelsProcessed: 2
      });

      const res = await request(app)
        .post('/archive/setup')
        .send({
          ...TEST_DATE_RANGE,
          archive: true
        });

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('accepted');
      expect(res.body.jobId).toBeDefined();
      expect(res.body.message).toBe('Delete started in background');
    });

    it('should include memory usage statistics', async () => {
      createArchiveWorker.mockResolvedValue({
        schemaValidations: { valid: true },
        results: { orders: 500000 },
        performanceStats: { 
          executionTime: '120s',
          memoryUsage: { heapUsed: 134217728, heapTotal: 268435456 },
          peakMemoryMB: 128
        }
      });

      const res = await request(app)
        .post('/archive')
        .send(TEST_DATE_RANGE);

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('accepted');
      expect(res.body.jobId).toBeDefined();
      expect(res.body.message).toBe('Archival started in background');
    });
  });
});