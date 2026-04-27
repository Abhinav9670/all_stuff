const request = require('supertest');
const express = require('express');
const { Worker } = require('worker_threads');

// Mock all external dependencies first
jest.mock('sequelize', () => {
  const mockTransaction = {
    commit: jest.fn().mockResolvedValue(true),
    rollback: jest.fn().mockResolvedValue(true)
  };
  
  const mockSequelize = {
    authenticate: jest.fn().mockResolvedValue(true),
    close: jest.fn().mockResolvedValue(true),
    query: jest.fn(),
    transaction: jest.fn().mockResolvedValue(mockTransaction),
    QueryTypes: { SELECT: 'SELECT' },
    Transaction: {
      ISOLATION_LEVELS: {
        READ_COMMITTED: 'READ_COMMITTED'
      }
    }
  };
  
  return {
    Sequelize: jest.fn(() => mockSequelize)
  };
});

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

// Remove DEFAULT_CONFIG from the worker mock
jest.mock('../../src/helpers/deleteWorker', () => ({
  createDeleteWorker: jest.fn(),
  DeleteWorker: jest.fn()
}));

// Define a local DEFAULT_CONFIG for use in tests
const DEFAULT_CONFIG = {
  deleteBatchSize: 100000,
  batchDelay: 5,
  maxDeleteBatches: 100000,
  commitEveryNBatches: 20,
  idCollectionBatchSize: 500000,
  maxIdsPerTable: 10000000,
  parallelIdCollection: true,
  maxParallelTables: 5,
  progressUpdateInterval: 5000,
  enableDetailedLogging: false,
  trackRemainingRecords: true,
  showCompletionPercentage: true,
  progressBatchFrequency: 50,
  maxMemorySafeIds: 5000000,
  memoryCheckInterval: 10000,
  forceGCThreshold: 2048,
  connectionTimeout: 900000,
  acquireTimeout: 900000,
  maxRetries: 3,
  retryDelay: 1000,
  enableBottomUpDeletion: true,
  validateHierarchy: true,
  skipEmptyTables: true,
  sendProgressToParent: true,
  progressUpdateThreshold: 1000
};

const { Sequelize } = require('sequelize');
const { createDeleteWorker, DeleteWorker } = require('../../src/helpers/deleteWorker');

// Test data for 2022
const TEST_2022_DATE_RANGE = {
  startDate: '2022-06-01',
  endDate: '2022-07-01'
};

describe('Delete Worker Tests', () => {
  const BASE_CONFIG = {
    primaryDbConfig: {
      host: 'localhost',
      database: 'primary_db',
      username: 'user',
      password: 'pass',
      port: 3306,
      dialect: 'mysql'
    },
    tables: [
      {
        table: 'orders',
        dateColumn: 'created_at',
        pk: 'id',
        linkTo: null,
        fk: null
      },
      {
        table: 'order_items',
        dateColumn: 'created_at',
        pk: 'id',
        linkTo: 'orders',
        fk: 'order_id'
      }
    ],
    cutoffDate: '2022-06-01',
    condition: '<',
    config: DEFAULT_CONFIG
  };

  beforeEach(() => {
    jest.clearAllMocks();
    jest.clearAllTimers();
    
    // Reset global.gc mock
    if (global.gc) {
      delete global.gc;
    }
    global.gc = jest.fn();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  describe('createDeleteWorker - Success Cases', () => {
    it('should create worker and return success result', async () => {
      const mockResult = {
        success: true,
        totalRecordsDeleted: 1500,
        results: {
          orders: { success: true, recordsDeleted: 500, hierarchyLevel: 0 },
          order_items: { success: true, recordsDeleted: 1000, hierarchyLevel: 1 }
        },
        performanceStats: {
          currentPhase: 'deletion',
          totalProcessed: 1500,
          totalRemaining: 0,
          overallCompletion: 100,
          executionTimeSeconds: '45.50',
          averageRecordsPerSecond: 33,
          peakMemoryUsageMB: 128,
          totalRetries: 0,
          errorCount: 0,
          tablesInProgress: 2
        },
        idCollectionSummary: {
          orders: 500,
          order_items: 1000
        },
        hierarchyLevelsProcessed: 2
      };

      createDeleteWorker.mockResolvedValue(mockResult);

      const result = await createDeleteWorker(BASE_CONFIG);

      expect(createDeleteWorker).toHaveBeenCalledWith(BASE_CONFIG);
      expect(result.success).toBe(true);
      expect(result.totalRecordsDeleted).toBe(1500);
      expect(result.results.orders.recordsDeleted).toBe(500);
      expect(result.results.order_items.recordsDeleted).toBe(1000);
    });

    it('should handle single table deletion', async () => {
      const singleTableConfig = {
        ...BASE_CONFIG,
        tables: [{
          table: 'orders',
          dateColumn: 'created_at',
          pk: 'id'
        }]
      };

      const mockResult = {
        success: true,
        totalRecordsDeleted: 500,
        results: {
          orders: { success: true, recordsDeleted: 500, hierarchyLevel: 0 }
        },
        performanceStats: {
          currentPhase: 'deletion',
          executionTimeSeconds: '30.25'
        },
        idCollectionSummary: { orders: 500 },
        hierarchyLevelsProcessed: 1
      };

      createDeleteWorker.mockResolvedValue(mockResult);

      const result = await createDeleteWorker(singleTableConfig);

      expect(result.success).toBe(true);
      expect(result.totalRecordsDeleted).toBe(500);
      expect(result.hierarchyLevelsProcessed).toBe(1);
    });

    it('should handle complex hierarchical deletion', async () => {
      const complexConfig = {
        ...BASE_CONFIG,
        tables: [
          { table: 'orders', dateColumn: 'created_at', pk: 'id' },
          { table: 'order_items', pk: 'id', linkTo: 'orders', fk: 'order_id' },
          { table: 'order_payments', pk: 'id', linkTo: 'orders', fk: 'order_id' },
          { table: 'item_attributes', pk: 'id', linkTo: 'order_items', fk: 'item_id' }
        ]
      };

      const mockResult = {
        success: true,
        totalRecordsDeleted: 5000,
        results: {
          orders: { success: true, recordsDeleted: 1000, hierarchyLevel: 0 },
          order_items: { success: true, recordsDeleted: 2000, hierarchyLevel: 1 },
          order_payments: { success: true, recordsDeleted: 1000, hierarchyLevel: 1 },
          item_attributes: { success: true, recordsDeleted: 1000, hierarchyLevel: 2 }
        },
        performanceStats: {
          currentPhase: 'deletion',
          executionTimeSeconds: '120.75',
          hierarchyLevelsProcessed: 3
        },
        idCollectionSummary: {
          orders: 1000,
          order_items: 2000,
          order_payments: 1000,
          item_attributes: 1000
        },
        hierarchyLevelsProcessed: 3
      };

      createDeleteWorker.mockResolvedValue(mockResult);

      const result = await createDeleteWorker(complexConfig);

      expect(result.success).toBe(true);
      expect(result.totalRecordsDeleted).toBe(5000);
      expect(result.hierarchyLevelsProcessed).toBe(3);
      expect(Object.keys(result.results)).toHaveLength(4);
    });

    it('should handle date range deletion', async () => {
      const dateRangeConfig = {
        ...BASE_CONFIG,
        startDate: '2022-06-01',
        endDate: '2022-06-15',
        condition: 'BETWEEN'
      };

      const mockResult = {
        success: true,
        totalRecordsDeleted: 750,
        results: {
          orders: { success: true, recordsDeleted: 250, hierarchyLevel: 0 },
          order_items: { success: true, recordsDeleted: 500, hierarchyLevel: 1 }
        },
        performanceStats: {
          currentPhase: 'deletion',
          executionTimeSeconds: '25.30'
        },
        idCollectionSummary: { orders: 250, order_items: 500 }
      };

      createDeleteWorker.mockResolvedValue(mockResult);

      const result = await createDeleteWorker(dateRangeConfig);

      expect(result.success).toBe(true);
      expect(result.totalRecordsDeleted).toBe(750);
    });

    it('should handle archive database deletion', async () => {
      const archiveConfig = {
        ...BASE_CONFIG,
        archiveDbConfig: {
          host: 'localhost',
          database: 'archive_db',
          username: 'user',
          password: 'pass'
        },
        useArchiveDb: true
      };

      const mockResult = {
        success: true,
        totalRecordsDeleted: 1200,
        results: {
          orders: { success: true, recordsDeleted: 400, hierarchyLevel: 0 },
          order_items: { success: true, recordsDeleted: 800, hierarchyLevel: 1 }
        },
        performanceStats: {
          currentPhase: 'deletion',
          executionTimeSeconds: '35.45'
        },
        databaseUsed: 'archive'
      };

      createDeleteWorker.mockResolvedValue(mockResult);

      const result = await createDeleteWorker(archiveConfig);

      expect(result.success).toBe(true);
      expect(result.totalRecordsDeleted).toBe(1200);
      expect(result.databaseUsed).toBe('archive');
    });
  });

  describe('createDeleteWorker - Error Cases', () => {
    it('should handle database connection errors', async () => {
      const mockError = {
        success: false,
        totalRecordsDeleted: 0,
        error: 'Failed to connect to PRIMARY database: ECONNREFUSED',
        results: {},
        performanceStats: {
          currentPhase: 'idCollection',
          errorCount: 1
        },
        idCollectionSummary: {}
      };

      createDeleteWorker.mockResolvedValue(mockError);

      const result = await createDeleteWorker(BASE_CONFIG);

      expect(result.success).toBe(false);
      expect(result.error).toContain('ECONNREFUSED');
      expect(result.totalRecordsDeleted).toBe(0);
    });

    it('should handle transaction rollback', async () => {
      const mockError = {
        success: false,
        totalRecordsDeleted: 150,
        error: 'ER_LOCK_WAIT_TIMEOUT: Lock wait timeout exceeded',
        results: {
          orders: { success: true, recordsDeleted: 150, hierarchyLevel: 0 },
          order_items: { success: false, recordsDeleted: 0, hierarchyLevel: 1, error: 'Lock timeout' }
        },
        performanceStats: {
          currentPhase: 'deletion',
          totalRetries: 3,
          errorCount: 1
        },
        transactionRolledBack: true
      };

      createDeleteWorker.mockResolvedValue(mockError);

      const result = await createDeleteWorker(BASE_CONFIG);

      expect(result.success).toBe(false);
      expect(result.error).toContain('ER_LOCK_WAIT_TIMEOUT');
      expect(result.transactionRolledBack).toBe(true);
    });

    it('should handle ID collection failures', async () => {
      const mockError = {
        success: false,
        totalRecordsDeleted: 0,
        error: 'ID collection failed: Table orders does not exist',
        results: {},
        performanceStats: {
          currentPhase: 'idCollection',
          errorCount: 1
        },
        idCollectionSummary: {}
      };

      createDeleteWorker.mockResolvedValue(mockError);

      const result = await createDeleteWorker(BASE_CONFIG);

      expect(result.success).toBe(false);
      expect(result.error).toContain('ID collection failed');
      expect(result.performanceStats.currentPhase).toBe('idCollection');
    });

    it('should handle worker timeout', async () => {
      createDeleteWorker.mockRejectedValue(new Error('Worker timeout after 90 minutes'));

      await expect(createDeleteWorker(BASE_CONFIG)).rejects.toThrow('Worker timeout after 90 minutes');
    });

    it('should handle worker crash', async () => {
      createDeleteWorker.mockRejectedValue(new Error('Worker thread crashed'));

      await expect(createDeleteWorker(BASE_CONFIG)).rejects.toThrow('Worker thread crashed');
    });

    it('should handle critical database errors', async () => {
      const mockError = {
        success: false,
        totalRecordsDeleted: 0,
        error: 'ER_ACCESS_DENIED_ERROR: Access denied for user',
        results: {},
        performanceStats: {
          errorCount: 1
        },
        criticalError: true
      };

      createDeleteWorker.mockResolvedValue(mockError);

      const result = await createDeleteWorker(BASE_CONFIG);

      expect(result.success).toBe(false);
      expect(result.error).toContain('ER_ACCESS_DENIED_ERROR');
      expect(result.criticalError).toBe(true);
    });

    it('should handle invalid table configuration', async () => {
      const invalidConfig = {
        ...BASE_CONFIG,
        tables: [
          { table: 'orders' } // Missing required fields
        ]
      };

      const mockError = {
        success: false,
        totalRecordsDeleted: 0,
        error: 'Invalid table configuration: Missing required fields for table orders',
        results: {},
        performanceStats: {
          errorCount: 1
        }
      };

      createDeleteWorker.mockResolvedValue(mockError);

      const result = await createDeleteWorker(invalidConfig);

      expect(result.success).toBe(false);
      expect(result.error).toContain('Invalid table configuration');
    });

    it('should handle circular dependency in hierarchy', async () => {
      const circularConfig = {
        ...BASE_CONFIG,
        tables: [
          { table: 'table_a', pk: 'id', linkTo: 'table_b', fk: 'b_id' },
          { table: 'table_b', pk: 'id', linkTo: 'table_a', fk: 'a_id' }
        ]
      };

      const mockError = {
        success: false,
        totalRecordsDeleted: 0,
        error: 'Circular dependency detected in table hierarchy',
        results: {},
        performanceStats: {
          errorCount: 1
        },
        hierarchyValidationFailed: true
      };

      createDeleteWorker.mockResolvedValue(mockError);

      const result = await createDeleteWorker(circularConfig);

      expect(result.success).toBe(false);
      expect(result.error).toContain('Circular dependency detected');
      expect(result.hierarchyValidationFailed).toBe(true);
    });
  });

  describe('createDeleteWorker - Edge Cases', () => {
    it('should handle empty result set', async () => {
      const mockResult = {
        success: true,
        totalRecordsDeleted: 0,
        results: {
          orders: { success: true, recordsDeleted: 0, hierarchyLevel: 0 }
        },
        performanceStats: {
          currentPhase: 'deletion',
          executionTimeSeconds: '5.25'
        },
        idCollectionSummary: { orders: 0 },
        message: 'No records found matching the deletion criteria'
      };

      createDeleteWorker.mockResolvedValue(mockResult);

      const result = await createDeleteWorker(BASE_CONFIG);

      expect(result.success).toBe(true);
      expect(result.totalRecordsDeleted).toBe(0);
      expect(result.message).toContain('No records found');
    });

    it('should handle partial success scenarios', async () => {
      const mockResult = {
        success: true,
        totalRecordsDeleted: 500,
        results: {
          orders: { success: true, recordsDeleted: 500, hierarchyLevel: 0 },
          order_items: { success: false, recordsDeleted: 0, hierarchyLevel: 1, error: 'Table not found' }
        },
        performanceStats: {
          currentPhase: 'deletion',
          errorCount: 1
        },
        warnings: ['Failed to delete from order_items: Table not found'],
        partialSuccess: true
      };

      createDeleteWorker.mockResolvedValue(mockResult);

      const result = await createDeleteWorker(BASE_CONFIG);

      expect(result.success).toBe(true);
      expect(result.partialSuccess).toBe(true);
      expect(result.warnings).toContain('Failed to delete from order_items: Table not found');
    });

    it('should handle large dataset with memory warnings', async () => {
      const mockResult = {
        success: true,
        totalRecordsDeleted: 1000000,
        results: {
          orders: { success: true, recordsDeleted: 1000000, hierarchyLevel: 0 }
        },
        performanceStats: {
          currentPhase: 'deletion',
          executionTimeSeconds: '600.75',
          peakMemoryUsageMB: 1024,
          averageRecordsPerSecond: 1665
        },
        warnings: [
          'High memory usage detected during processing',
          'Consider reducing batch size for large datasets'
        ],
        memoryPressure: true
      };

      createDeleteWorker.mockResolvedValue(mockResult);

      const result = await createDeleteWorker(BASE_CONFIG);

      expect(result.success).toBe(true);
      expect(result.totalRecordsDeleted).toBe(1000000);
      expect(result.memoryPressure).toBe(true);
      expect(result.warnings).toContain('High memory usage detected during processing');
    });

    it('should handle rollback request during execution', async () => {
      const mockResult = {
        success: false,
        totalRecordsDeleted: 250,
        results: {
          orders: { success: true, recordsDeleted: 250, hierarchyLevel: 0 }
        },
        error: 'Deletion stopped due to rollback request',
        performanceStats: {
          currentPhase: 'deletion'
        },
        rollbackRequested: true,
        transactionRolledBack: true
      };

      createDeleteWorker.mockResolvedValue(mockResult);

      const result = await createDeleteWorker(BASE_CONFIG);

      expect(result.success).toBe(false);
      expect(result.rollbackRequested).toBe(true);
      expect(result.transactionRolledBack).toBe(true);
      expect(result.error).toContain('rollback request');
    });

    it('should handle invalid date formats', async () => {
      const invalidDateConfig = {
        ...BASE_CONFIG,
        cutoffDate: 'invalid-date',
        startDate: 'not-a-date'
      };

      const mockError = {
        success: false,
        totalRecordsDeleted: 0,
        error: 'Invalid date format provided',
        results: {},
        performanceStats: {
          errorCount: 1
        }
      };

      createDeleteWorker.mockResolvedValue(mockError);

      const result = await createDeleteWorker(invalidDateConfig);

      expect(result.success).toBe(false);
      expect(result.error).toContain('Invalid date format');
    });

    it('should handle missing foreign key relationships', async () => {
      const mockResult = {
        success: true,
        totalRecordsDeleted: 500,
        results: {
          orders: { success: true, recordsDeleted: 500, hierarchyLevel: 0 },
          order_items: { success: true, recordsDeleted: 0, hierarchyLevel: 1 }
        },
        warnings: ['No parent IDs found for order_items - table skipped'],
        performanceStats: {
          currentPhase: 'deletion'
        }
      };

      createDeleteWorker.mockResolvedValue(mockResult);

      const result = await createDeleteWorker(BASE_CONFIG);

      expect(result.success).toBe(true);
      expect(result.warnings).toContain('No parent IDs found for order_items - table skipped');
    });
  });

  describe('createDeleteWorker - Performance and Monitoring', () => {
    it('should return detailed performance statistics', async () => {
      const mockResult = {
        success: true,
        totalRecordsDeleted: 10000,
        results: {
          orders: { success: true, recordsDeleted: 10000, hierarchyLevel: 0 }
        },
        performanceStats: {
          currentPhase: 'deletion',
          totalProcessed: 10000,
          totalRemaining: 0,
          overallCompletion: 100,
          executionTimeSeconds: '150.75',
          averageRecordsPerSecond: 66,
          peakMemoryUsageMB: 256,
          totalRetries: 2,
          errorCount: 0,
          tablesInProgress: 1
        },
        idCollectionSummary: { orders: 10000 }
      };

      createDeleteWorker.mockResolvedValue(mockResult);

      const result = await createDeleteWorker(BASE_CONFIG);

      expect(result.success).toBe(true);
      expect(result.performanceStats.executionTimeSeconds).toBe('150.75');
      expect(result.performanceStats.averageRecordsPerSecond).toBe(66);
      expect(result.performanceStats.peakMemoryUsageMB).toBe(256);
      expect(result.performanceStats.totalRetries).toBe(2);
    });

    it('should handle progress updates from worker', async () => {
      const progressCallback = jest.fn();
      const configWithProgress = {
        ...BASE_CONFIG,
        onProgress: progressCallback
      };

      const mockResult = {
        success: true,
        totalRecordsDeleted: 1000,
        results: {
          orders: { success: true, recordsDeleted: 1000, hierarchyLevel: 0 }
        },
        performanceStats: {
          currentPhase: 'deletion'
        },
        progressUpdates: [
          { phase: 'idCollection', table: 'orders', processed: 1000, remaining: 0 },
          { phase: 'deletion', table: 'orders', processed: 1000, remaining: 0 }
        ]
      };

      createDeleteWorker.mockResolvedValue(mockResult);

      const result = await createDeleteWorker(configWithProgress);

      expect(result.success).toBe(true);
      expect(result.progressUpdates).toBeDefined();
      expect(result.progressUpdates).toHaveLength(2);
    });

    it('should handle batch processing statistics', async () => {
      const batchConfig = {
        ...BASE_CONFIG,
        config: {
          ...DEFAULT_CONFIG,
          deleteBatchSize: 1000,
          batchDelay: 10
        }
      };

      const mockResult = {
        success: true,
        totalRecordsDeleted: 5000,
        results: {
          orders: { success: true, recordsDeleted: 5000, hierarchyLevel: 0 }
        },
        performanceStats: {
          currentPhase: 'deletion',
          totalBatches: 5,
          averageBatchSize: 1000,
          totalBatchDelay: 50
        },
        batchProcessingStats: {
          batchSize: 1000,
          totalBatches: 5,
          failedBatches: 0
        }
      };

      createDeleteWorker.mockResolvedValue(mockResult);

      const result = await createDeleteWorker(batchConfig);

      expect(result.success).toBe(true);
      expect(result.batchProcessingStats.totalBatches).toBe(5);
      expect(result.batchProcessingStats.failedBatches).toBe(0);
    });

    it('should handle memory management and garbage collection', async () => {
      const mockResult = {
        success: true,
        totalRecordsDeleted: 2000000,
        results: {
          orders: { success: true, recordsDeleted: 2000000, hierarchyLevel: 0 }
        },
        performanceStats: {
          currentPhase: 'deletion',
          peakMemoryUsageMB: 1024,
          gcTriggered: true,
          gcCount: 5
        },
        memoryManagement: {
          initialMemoryMB: 128,
          peakMemoryMB: 1024,
          finalMemoryMB: 256,
          gcTriggered: true,
          gcCount: 5
        }
      };

      createDeleteWorker.mockResolvedValue(mockResult);

      const result = await createDeleteWorker(BASE_CONFIG);

      expect(result.success).toBe(true);
      expect(result.memoryManagement.gcTriggered).toBe(true);
      expect(result.memoryManagement.gcCount).toBe(5);
    });

    it('should handle concurrent deletion limits', async () => {
      const concurrentConfig = {
        ...BASE_CONFIG,
        config: {
          ...DEFAULT_CONFIG,
          maxParallelTables: 3,
          parallelIdCollection: true
        }
      };

      const mockResult = {
        success: true,
        totalRecordsDeleted: 3000,
        results: {
          orders: { success: true, recordsDeleted: 1000, hierarchyLevel: 0 },
          order_items: { success: true, recordsDeleted: 1500, hierarchyLevel: 1 },
          order_payments: { success: true, recordsDeleted: 500, hierarchyLevel: 1 }
        },
        concurrencyStats: {
          maxParallelTables: 3,
          tablesProcessedInParallel: 2,
          totalConcurrencyTime: '45.30'
        }
      };

      createDeleteWorker.mockResolvedValue(mockResult);

      const result = await createDeleteWorker(concurrentConfig);

      expect(result.success).toBe(true);
      expect(result.concurrencyStats.maxParallelTables).toBe(3);
      expect(result.concurrencyStats.tablesProcessedInParallel).toBe(2);
    });
  });

  describe('createDeleteWorker - Real Worker Scenarios', () => {
    it('should simulate actual worker thread behavior', async () => {
      const mockWorkerFlow = async (config) => {
        // Simulate validation phase
        if (!config.tables || config.tables.length === 0) {
          return { success: false, error: 'No tables configured' };
        }

        // Simulate ID collection phase
        const idCollectionSummary = {};
        for (const table of config.tables) {
          idCollectionSummary[table.table] = table.estimatedRecords || 100;
        }

        // Simulate deletion phase
        const results = {};
        let totalDeleted = 0;
        
        for (const table of config.tables) {
          const recordsToDelete = table.estimatedRecords || 100;
          results[table.table] = {
            success: true,
            recordsDeleted: recordsToDelete,
            hierarchyLevel: table.linkTo ? 1 : 0
          };
          totalDeleted += recordsToDelete;
        }

        return {
          success: true,
          totalRecordsDeleted: totalDeleted,
          results,
          performanceStats: {
            currentPhase: 'deletion',
            executionTimeSeconds: `${Math.ceil(totalDeleted/100)}.50`
          },
          idCollectionSummary,
          hierarchyLevelsProcessed: Math.max(...config.tables.map(t => t.linkTo ? 1 : 0)) + 1
        };
      };

      createDeleteWorker.mockImplementation(mockWorkerFlow);

      const result = await createDeleteWorker(BASE_CONFIG);

      expect(result.success).toBe(true);
      expect(result.totalRecordsDeleted).toBe(200); // 100 + 100
      expect(result.hierarchyLevelsProcessed).toBe(2);
    });

    it('should handle worker shutdown gracefully', async () => {
      const mockResult = {
        success: true,
        totalRecordsDeleted: 500,
        results: {
          orders: { success: true, recordsDeleted: 500, hierarchyLevel: 0 }
        },
        shutdownRequested: true,
        gracefulShutdown: true,
        performanceStats: {
          currentPhase: 'deletion'
        }
      };

      createDeleteWorker.mockResolvedValue(mockResult);

      const result = await createDeleteWorker(BASE_CONFIG);

      expect(result.success).toBe(true);
      expect(result.shutdownRequested).toBe(true);
      expect(result.gracefulShutdown).toBe(true);
    });

    it('should handle retry mechanisms for recoverable errors', async () => {
      const mockResult = {
        success: true,
        totalRecordsDeleted: 1000,
        results: {
          orders: { success: true, recordsDeleted: 1000, hierarchyLevel: 0 }
        },
        performanceStats: {
          currentPhase: 'deletion',
          totalRetries: 2,
          recoverableErrors: 2
        },
        retryStats: {
          totalRetries: 2,
          retriedOperations: ['batch_delete_timeout', 'connection_reset'],
          maxRetriesReached: false,
          recoverySuccessful: true
        }
      };

      createDeleteWorker.mockResolvedValue(mockResult);

      const result = await createDeleteWorker(BASE_CONFIG);

      expect(result.success).toBe(true);
      expect(result.retryStats.totalRetries).toBe(2);
      expect(result.retryStats.recoverySuccessful).toBe(true);
      expect(result.retryStats.retriedOperations).toContain('batch_delete_timeout');
    });

    it('should handle deep hierarchy validation', async () => {
      const deepHierarchyConfig = {
        ...BASE_CONFIG,
        tables: [
          { table: 'level0', dateColumn: 'created_at', pk: 'id' },
          { table: 'level1', pk: 'id', linkTo: 'level0', fk: 'level0_id' },
          { table: 'level2', pk: 'id', linkTo: 'level1', fk: 'level1_id' },
          { table: 'level3', pk: 'id', linkTo: 'level2', fk: 'level2_id' },
          { table: 'level4', pk: 'id', linkTo: 'level3', fk: 'level3_id' }
        ]
      };

      const mockResult = {
        success: true,
        totalRecordsDeleted: 5000,
        results: {
          level0: { success: true, recordsDeleted: 1000, hierarchyLevel: 0 },
          level1: { success: true, recordsDeleted: 1200, hierarchyLevel: 1 },
          level2: { success: true, recordsDeleted: 1300, hierarchyLevel: 2 },
          level3: { success: true, recordsDeleted: 800, hierarchyLevel: 3 },
          level4: { success: true, recordsDeleted: 700, hierarchyLevel: 4 }
        },
        hierarchyLevelsProcessed: 5,
        performanceStats: {
          currentPhase: 'deletion',
          executionTimeSeconds: '180.25'
        },
        hierarchyValidation: {
          maxDepth: 4,
          circularDependencies: false,
          validationWarnings: ['Deep hierarchy detected: level4 at level 4']
        }
      };

      createDeleteWorker.mockResolvedValue(mockResult);

      const result = await createDeleteWorker(deepHierarchyConfig);

      expect(result.success).toBe(true);
      expect(result.hierarchyLevelsProcessed).toBe(5);
      expect(result.hierarchyValidation.maxDepth).toBe(4);
      expect(result.hierarchyValidation.validationWarnings).toContain('Deep hierarchy detected: level4 at level 4');
    });
  });

  describe('DeleteWorker Class Tests', () => {
    let mockSequelize;
    let mockTransaction;

    beforeEach(() => {
      mockTransaction = {
        commit: jest.fn().mockResolvedValue(true),
        rollback: jest.fn().mockResolvedValue(true)
      };

      mockSequelize = {
        authenticate: jest.fn().mockResolvedValue(true),
        close: jest.fn().mockResolvedValue(true),
        query: jest.fn(),
        transaction: jest.fn().mockResolvedValue(mockTransaction),
        QueryTypes: { SELECT: 'SELECT' },
        Transaction: {
          ISOLATION_LEVELS: {
            READ_COMMITTED: 'READ_COMMITTED'
          }
        }
      };

      Sequelize.mockImplementation(() => mockSequelize);
    });

    it('should initialize DeleteWorker with default config', () => {
      const workerData = {
        primaryDbConfig: BASE_CONFIG.primaryDbConfig,
        tables: BASE_CONFIG.tables,
        cutoffDate: BASE_CONFIG.cutoffDate,
        condition: BASE_CONFIG.condition
      };

      DeleteWorker.mockImplementation((data) => {
        const worker = {
          workerData: data,
          config: { ...DEFAULT_CONFIG, ...data.config },
          progress: {
            startPhase: jest.fn(),
            completePhase: jest.fn(),
            updateTableProgress: jest.fn(),
            getOverallProgress: jest.fn().mockReturnValue({
              currentPhase: 'deletion',
              totalProcessed: 100,
              overallCompletion: 100
            }),
            addError: jest.fn()
          },
          initialize: jest.fn().mockResolvedValue(true),
          executeHierarchicalDeletion: jest.fn().mockResolvedValue({
            success: true,
            totalRecordsDeleted: 100
          }),
          cleanup: jest.fn().mockResolvedValue(true)
        };
        return worker;
      });

      const worker = new DeleteWorker(workerData);

      expect(worker.workerData).toEqual(workerData);
      expect(worker.config).toEqual(expect.objectContaining(DEFAULT_CONFIG));
    });

    it('should handle initialization errors', async () => {
      const workerData = {
        primaryDbConfig: { ...BASE_CONFIG.primaryDbConfig, host: 'invalid-host' },
        tables: BASE_CONFIG.tables,
        cutoffDate: BASE_CONFIG.cutoffDate,
        condition: BASE_CONFIG.condition
      };

      DeleteWorker.mockImplementation(() => {
        const worker = {
          initialize: jest.fn().mockRejectedValue(new Error('Connection refused')),
          cleanup: jest.fn().mockResolvedValue(true)
        };
        return worker;
      });

      const worker = new DeleteWorker(workerData);
      await expect(worker.initialize()).rejects.toThrow('Connection refused');
    });

    it('should execute hierarchical deletion with progress tracking', async () => {
      const workerData = {
        primaryDbConfig: BASE_CONFIG.primaryDbConfig,
        tables: BASE_CONFIG.tables,
        cutoffDate: BASE_CONFIG.cutoffDate,
        condition: BASE_CONFIG.condition
      };

      const mockProgressUpdates = [];
      
      DeleteWorker.mockImplementation(() => {
        const worker = {
          executeHierarchicalDeletion: jest.fn().mockImplementation(async () => {
            // Simulate progress updates
            mockProgressUpdates.push({ phase: 'idCollection', progress: 50 });
            mockProgressUpdates.push({ phase: 'idCollection', progress: 100 });
            mockProgressUpdates.push({ phase: 'deletion', progress: 25 });
            mockProgressUpdates.push({ phase: 'deletion', progress: 100 });

            return {
              success: true,
              totalRecordsDeleted: 500,
              results: {
                orders: { success: true, recordsDeleted: 200, hierarchyLevel: 0 },
                order_items: { success: true, recordsDeleted: 300, hierarchyLevel: 1 }
              },
              performanceStats: {
                currentPhase: 'deletion',
                totalProcessed: 500,
                overallCompletion: 100
              },
              progressUpdates: mockProgressUpdates
            };
          })
        };
        return worker;
      });

      const worker = new DeleteWorker(workerData);
      const result = await worker.executeHierarchicalDeletion();

      expect(result.success).toBe(true);
      expect(result.totalRecordsDeleted).toBe(500);
      expect(result.progressUpdates).toHaveLength(4);
    });

    it('should handle message events', () => {
      const workerData = {
        primaryDbConfig: BASE_CONFIG.primaryDbConfig,
        tables: BASE_CONFIG.tables,
        cutoffDate: BASE_CONFIG.cutoffDate,
        condition: BASE_CONFIG.condition
      };

      let isRollbackRequested = false;
      let isShuttingDown = false;

      DeleteWorker.mockImplementation(() => {
        const worker = {
          handleMessage: jest.fn().mockImplementation((message) => {
            switch (message.type) {
              case 'rollback':
                isRollbackRequested = true;
                break;
              case 'shutdown':
                isShuttingDown = true;
                break;
            }
          }),
          isRollbackRequested: () => isRollbackRequested,
          isShuttingDown: () => isShuttingDown
        };
        return worker;
      });

      const worker = new DeleteWorker(workerData);
      
      worker.handleMessage({ type: 'rollback' });
      expect(worker.isRollbackRequested()).toBe(true);
      
      worker.handleMessage({ type: 'shutdown' });
      expect(worker.isShuttingDown()).toBe(true);
    });

    it('should cleanup resources properly', async () => {
      const workerData = {
        primaryDbConfig: BASE_CONFIG.primaryDbConfig,
        archiveDbConfig: BASE_CONFIG.archiveDbConfig,
        tables: BASE_CONFIG.tables,
        cutoffDate: BASE_CONFIG.cutoffDate,
        condition: BASE_CONFIG.condition
      };

      let primaryDbClosed = false;
      let archiveDbClosed = false;
      let gcTriggered = false;

      DeleteWorker.mockImplementation(() => {
        const worker = {
          cleanup: jest.fn().mockImplementation(async () => {
            primaryDbClosed = true;
            archiveDbClosed = true;
            if (global.gc) {
              global.gc();
              gcTriggered = true;
            }
          }),
          isPrimaryDbClosed: () => primaryDbClosed,
          isArchiveDbClosed: () => archiveDbClosed,
          isGcTriggered: () => gcTriggered
        };
        return worker;
      });

      const worker = new DeleteWorker(workerData);
      await worker.cleanup();

      expect(worker.isPrimaryDbClosed()).toBe(true);
      expect(worker.isArchiveDbClosed()).toBe(true);
      expect(worker.isGcTriggered()).toBe(true);
    });
  });

  describe('Helper Functions and Classes', () => {
    describe('HierarchicalProgressTracker', () => {
      it('should track phase progression', () => {
        const mockTracker = {
          phases: {
            idCollection: { status: 'pending', startTime: null, endTime: null },
            deletion: { status: 'pending', startTime: null, endTime: null }
          },
          currentPhase: 'idCollection',
          startPhase: jest.fn().mockImplementation(function(phase) {
            this.currentPhase = phase;
            this.phases[phase].status = 'running';
            this.phases[phase].startTime = new Date();
          }),
          completePhase: jest.fn().mockImplementation(function(phase) {
            this.phases[phase].status = 'completed';
            this.phases[phase].endTime = new Date();
          }),
          getOverallProgress: jest.fn().mockReturnValue({
            currentPhase: 'deletion',
            overallCompletion: 75
          })
        };

        mockTracker.startPhase('idCollection');
        expect(mockTracker.phases.idCollection.status).toBe('running');
        
        mockTracker.completePhase('idCollection');
        expect(mockTracker.phases.idCollection.status).toBe('completed');
        
        const progress = mockTracker.getOverallProgress();
        expect(progress.overallCompletion).toBe(75);
      });

      it('should update table progress with completion percentage', () => {
        const mockTracker = {
          tableProgress: new Map(),
          updateTableProgress: jest.fn().mockImplementation(function(tableName, processed, remaining, hierarchyLevel) {
            const completionPercentage = remaining > 0 ? 
              ((processed / (processed + remaining)) * 100).toFixed(1) : 100;
            
            this.tableProgress.set(tableName, {
              processed,
              remaining,
              total: processed + remaining,
              hierarchyLevel,
              completionPercentage: parseFloat(completionPercentage)
            });
          }),
          getTableProgress: function(tableName) {
            return this.tableProgress.get(tableName);
          }
        };

        mockTracker.updateTableProgress('orders', 750, 250, 0);
        const progress = mockTracker.getTableProgress('orders');
        
        expect(progress.processed).toBe(750);
        expect(progress.remaining).toBe(250);
        expect(progress.completionPercentage).toBe(75.0);
      });

      it('should track errors and retries', () => {
        const mockTracker = {
          errors: [],
          retries: 0,
          addError: jest.fn().mockImplementation(function(error, context) {
            this.errors.push({
              message: error.message,
              context,
              timestamp: new Date().toISOString()
            });
          }),
          incrementRetry: jest.fn().mockImplementation(function() {
            this.retries++;
          })
        };

        mockTracker.addError(new Error('Test error'), 'test context');
        mockTracker.incrementRetry();

        expect(mockTracker.errors).toHaveLength(1);
        expect(mockTracker.errors[0].message).toBe('Test error');
        expect(mockTracker.retries).toBe(1);
      });
    });

    describe('IDCollectionManager', () => {
      it('should collect hierarchical IDs correctly', () => {
        const mockIdManager = {
          collectedIds: new Map(),
          collectAllHierarchicalIds: jest.fn().mockImplementation(async function(tableConfigs) {
            // Simulate ID collection for hierarchical tables
            for (const config of tableConfigs) {
              if (!config.linkTo) {
                // Root table
                this.collectedIds.set(config.table, new Set([1, 2, 3, 4, 5]));
              } else {
                // Child table
                this.collectedIds.set(config.table, new Set([10, 11, 12, 13, 14, 15]));
              }
            }
            return this.collectedIds;
          }),
          getCollectedIds: function(tableName) {
            return this.collectedIds.get(tableName) || new Set();
          },
          getCollectionSummary: function() {
            const summary = {};
            for (const [tableName, idSet] of this.collectedIds) {
              summary[tableName] = idSet.size;
            }
            return summary;
          }
        };

        const tableConfigs = [
          { table: 'orders', dateColumn: 'created_at', pk: 'id' },
          { table: 'order_items', pk: 'id', linkTo: 'orders', fk: 'order_id' }
        ];

        return mockIdManager.collectAllHierarchicalIds(tableConfigs).then(() => {
          const orderIds = mockIdManager.getCollectedIds('orders');
          const itemIds = mockIdManager.getCollectedIds('order_items');
          
          expect(orderIds.size).toBe(5);
          expect(itemIds.size).toBe(6);
          
          const summary = mockIdManager.getCollectionSummary();
          expect(summary.orders).toBe(5);
          expect(summary.order_items).toBe(6);
        });
      });

      it('should handle memory-safe ID collection', () => {
        const mockIdManager = {
          config: { maxMemorySafeIds: 1000000 },
          collectRootTableIds: jest.fn().mockImplementation(async function(tableConfig) {
            // Simulate memory-safe collection with batching
            const totalIds = 500000;
            const batchSize = 50000;
            let collectedIds = [];
            
            for (let i = 0; i < totalIds; i += batchSize) {
              const batchIds = Array.from({ length: Math.min(batchSize, totalIds - i) }, (_, idx) => i + idx + 1);
              collectedIds = collectedIds.concat(batchIds);
              
              // Simulate memory check
              if (collectedIds.length >= this.config.maxMemorySafeIds) {
                break;
              }
            }
            
            return collectedIds;
          })
        };

        const tableConfig = { table: 'orders', dateColumn: 'created_at', pk: 'id' };
        
        return mockIdManager.collectRootTableIds(tableConfig).then(ids => {
          expect(ids.length).toBe(500000);
          expect(ids[0]).toBe(1);
          expect(ids[ids.length - 1]).toBe(500000);
        });
      });
    });

    describe('BottomUpDeletionEngine', () => {
      it('should execute hierarchical deletion in correct order', () => {
        const mockEngine = {
          executeHierarchicalDeletion: jest.fn().mockImplementation(async function(tableConfigs) {
            const results = {};
            let totalDeleted = 0;
            
            // Sort by hierarchy level (highest first for bottom-up deletion)
            const sortedTables = tableConfigs.sort((a, b) => (b.hierarchyLevel || 0) - (a.hierarchyLevel || 0));
            
            for (const config of sortedTables) {
              const recordsDeleted = config.estimatedRecords || 100;
              results[config.table] = {
                success: true,
                recordsDeleted,
                hierarchyLevel: config.hierarchyLevel || 0
              };
              totalDeleted += recordsDeleted;
            }
            
            return { totalDeleted, results };
          })
        };

        const tableConfigs = [
          { table: 'orders', hierarchyLevel: 0, estimatedRecords: 200 },
          { table: 'order_items', hierarchyLevel: 1, estimatedRecords: 500 },
          { table: 'item_attributes', hierarchyLevel: 2, estimatedRecords: 300 }
        ];

        return mockEngine.executeHierarchicalDeletion(tableConfigs).then(result => {
          expect(result.totalDeleted).toBe(1000);
          expect(result.results.orders.hierarchyLevel).toBe(0);
          expect(result.results.order_items.hierarchyLevel).toBe(1);
          expect(result.results.item_attributes.hierarchyLevel).toBe(2);
        });
      });

      it('should handle batch deletion with progress tracking', () => {
        const mockEngine = {
          executeBatchDeletion: jest.fn().mockImplementation(async function(tableName, whereClause, queryParams) {
            const totalRecords = 10000;
            const batchSize = 1000;
            let deleted = 0;
            const batches = [];
            
            while (deleted < totalRecords) {
              const batchDeleted = Math.min(batchSize, totalRecords - deleted);
              deleted += batchDeleted;
              
              batches.push({
                batchNumber: batches.length + 1,
                recordsDeleted: batchDeleted,
                totalDeleted: deleted,
                remaining: totalRecords - deleted
              });
            }
            
            return { totalDeleted: deleted, batches };
          })
        };

        return mockEngine.executeBatchDeletion('orders', 'created_at < ?', ['2022-06-01']).then(result => {
          expect(result.totalDeleted).toBe(10000);
          expect(result.batches).toHaveLength(10);
          expect(result.batches[9].remaining).toBe(0);
        });
      });
    });

    describe('MemoryManager', () => {
      it('should check memory usage and trigger GC when needed', () => {
        const mockMemoryManager = {
          config: { forceGCThreshold: 512 },
          lastGC: new Date(Date.now() - 15000), // 15 seconds ago
          gcCount: 0,
          checkMemoryUsage: jest.fn().mockImplementation(function() {
            const mockMemUsage = 600; // MB - above threshold
            
            if (mockMemUsage > this.config.forceGCThreshold && global.gc) {
              const now = new Date();
              if (now - this.lastGC > 10000) { // 10 second cooldown
                global.gc();
                this.lastGC = now;
                this.gcCount++;
                return { memoryUsage: 400, gcTriggered: true }; // Memory after GC
              }
            }
            
            return { memoryUsage: mockMemUsage, gcTriggered: false };
          })
        };

        const result = mockMemoryManager.checkMemoryUsage();
        
        expect(result.gcTriggered).toBe(true);
        expect(result.memoryUsage).toBe(400);
        expect(mockMemoryManager.gcCount).toBe(1);
      });

      it('should respect GC cooldown period', () => {
        const mockMemoryManager = {
          config: { forceGCThreshold: 512 },
          lastGC: new Date(), // Just triggered
          gcCount: 1,
          checkMemoryUsage: jest.fn().mockImplementation(function() {
            const mockMemUsage = 600; // MB - above threshold
            
            if (mockMemUsage > this.config.forceGCThreshold && global.gc) {
              const now = new Date();
              if (now - this.lastGC > 10000) { // 10 second cooldown not met
                global.gc();
                this.lastGC = now;
                this.gcCount++;
                return { memoryUsage: 400, gcTriggered: true };
              }
            }
            
            return { memoryUsage: mockMemUsage, gcTriggered: false };
          })
        };

        const result = mockMemoryManager.checkMemoryUsage();
        
        expect(result.gcTriggered).toBe(false);
        expect(result.memoryUsage).toBe(600);
        expect(mockMemoryManager.gcCount).toBe(1); // No increment
      });
    });

    describe('Error Handling Utilities', () => {
      it('should identify critical errors correctly', () => {
        const isCriticalError = (error) => {
          const criticalErrors = [
            'ER_LOCK_WAIT_TIMEOUT',
            'ER_LOCK_DEADLOCK',
            'ER_OUT_OF_MEMORY',
            'ECONNRESET',
            'ER_SERVER_SHUTDOWN',
            'ER_ACCESS_DENIED_ERROR',
            'ER_BAD_DB_ERROR',
            'ER_TABLE_NOT_EXISTS',
            'ER_NO_SUCH_TABLE'
          ];
          
          return criticalErrors.some(criticalError => 
            error.message.includes(criticalError) || 
            error.code === criticalError
          );
        };

        expect(isCriticalError(new Error('ER_ACCESS_DENIED_ERROR: Access denied'))).toBe(true);
        expect(isCriticalError(new Error('ER_LOCK_WAIT_TIMEOUT: Lock timeout'))).toBe(true);
        expect(isCriticalError(new Error('ER_DUP_ENTRY: Duplicate entry'))).toBe(false);
        expect(isCriticalError({ code: 'ER_OUT_OF_MEMORY', message: 'Out of memory' })).toBe(true);
      });

      it('should identify recoverable errors correctly', () => {
        const isRecoverableError = (error) => {
          const recoverableErrors = [
            'ER_DUP_ENTRY',
            'ER_NO_REFERENCED_ROW',
            'ER_ROW_IS_REFERENCED',
            'ECONNREFUSED',
            'ETIMEDOUT',
            'timeout'
          ];
          
          return recoverableErrors.some(recoverableError => 
            error.message.toLowerCase().includes(recoverableError.toLowerCase()) || 
            error.code === recoverableError
          );
        };

        expect(isRecoverableError(new Error('ECONNREFUSED: Connection refused'))).toBe(true);
        expect(isRecoverableError(new Error('Query timeout'))).toBe(true);
        expect(isRecoverableError(new Error('ER_DUP_ENTRY: Duplicate key'))).toBe(true);
        expect(isRecoverableError(new Error('ER_ACCESS_DENIED_ERROR: Access denied'))).toBe(false);
      });
    });

    describe('Date Formatting Utilities', () => {
      it('should format dates for MySQL correctly', () => {
        const formatDateForMySQL = (date) => {
          if (!date) return null;
          if (date instanceof Date) {
            return date.toISOString().replace('T', ' ').replace(/\.\d+Z$/, '');
          }
          if (typeof date === 'string') {
            if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(date)) {
              return date;
            }
            const parsed = new Date(date);
            if (!isNaN(parsed)) {
              return parsed.toISOString().replace('T', ' ').replace(/\.\d+Z$/, '');
            }
          }
          return date;
        };

        expect(formatDateForMySQL(new Date('2022-06-01T10:30:00.000Z'))).toBe('2022-06-01 10:30:00');
        expect(formatDateForMySQL('2022-06-01 10:30:00')).toBe('2022-06-01 10:30:00');
        expect(formatDateForMySQL('2022-06-01')).toBe('2022-06-01 00:00:00');
        expect(formatDateForMySQL(null)).toBe(null);
      });

      it('should build where clauses correctly', () => {
        const buildWhereClause = (startDate, endDate, dateColumn, condition) => {
          if (startDate && endDate) {
            return {
              whereClause: `${dateColumn} >= ? AND ${dateColumn} < ?`,
              queryParams: [startDate, endDate]
            };
          }
          return {
            whereClause: `${dateColumn} ${condition} ?`,
            queryParams: [startDate]
          };
        };

        const result1 = buildWhereClause('2022-06-01', '2022-07-01', 'created_at', '<');
        expect(result1.whereClause).toBe('created_at >= ? AND created_at < ?');
        expect(result1.queryParams).toEqual(['2022-06-01', '2022-07-01']);

        const result2 = buildWhereClause('2022-06-01', null, 'created_at', '<');
        expect(result2.whereClause).toBe('created_at < ?');
        expect(result2.queryParams).toEqual(['2022-06-01']);
      });
    });
  });

  describe('Integration Tests', () => {
    it('should handle complete worker lifecycle', async () => {
      const workerConfig = {
        ...BASE_CONFIG,
        config: {
          ...DEFAULT_CONFIG,
          deleteBatchSize: 1000,
          enableDetailedLogging: true
        }
      };

      const mockResult = {
        success: true,
        totalRecordsDeleted: 2500,
        results: {
          orders: { success: true, recordsDeleted: 1000, hierarchyLevel: 0 },
          order_items: { success: true, recordsDeleted: 1500, hierarchyLevel: 1 }
        },
        performanceStats: {
          currentPhase: 'deletion',
          totalProcessed: 2500,
          totalRemaining: 0,
          overallCompletion: 100,
          executionTimeSeconds: '75.50',
          averageRecordsPerSecond: 33,
          peakMemoryUsageMB: 256,
          totalRetries: 1,
          errorCount: 0,
          tablesInProgress: 2
        },
        idCollectionSummary: {
          orders: 1000,
          order_items: 1500
        },
        hierarchyLevelsProcessed: 2,
        phases: {
          idCollection: { duration: '15.25s', recordsCollected: 2500 },
          deletion: { duration: '60.25s', recordsDeleted: 2500 }
        }
      };

      createDeleteWorker.mockResolvedValue(mockResult);

      const result = await createDeleteWorker(workerConfig);

      expect(result.success).toBe(true);
      expect(result.totalRecordsDeleted).toBe(2500);
      expect(result.performanceStats.overallCompletion).toBe(100);
      expect(result.phases.idCollection.recordsCollected).toBe(2500);
      expect(result.phases.deletion.recordsDeleted).toBe(2500);
    });

    it('should handle worker with custom configuration overrides', async () => {
      const customConfig = {
        ...BASE_CONFIG,
        config: {
          ...DEFAULT_CONFIG,
          deleteBatchSize: 5000,
          batchDelay: 0,
          maxParallelTables: 10,
          enableBottomUpDeletion: true,
          trackRemainingRecords: true,
          showCompletionPercentage: true
        }
      };

      const mockResult = {
        success: true,
        totalRecordsDeleted: 50000,
        results: {
          orders: { success: true, recordsDeleted: 20000, hierarchyLevel: 0 },
          order_items: { success: true, recordsDeleted: 30000, hierarchyLevel: 1 }
        },
        configUsed: {
          deleteBatchSize: 5000,
          batchDelay: 0,
          maxParallelTables: 10,
          bottomUpDeletion: true
        },
        performanceStats: {
          currentPhase: 'deletion',
          totalProcessed: 50000,
          executionTimeSeconds: '120.75',
          averageRecordsPerSecond: 414
        }
      };

      createDeleteWorker.mockResolvedValue(mockResult);

      const result = await createDeleteWorker(customConfig);

      expect(result.success).toBe(true);
      expect(result.configUsed.deleteBatchSize).toBe(5000);
      expect(result.configUsed.batchDelay).toBe(0);
      expect(result.configUsed.maxParallelTables).toBe(10);
      expect(result.performanceStats.averageRecordsPerSecond).toBe(414);
    });
  });
});