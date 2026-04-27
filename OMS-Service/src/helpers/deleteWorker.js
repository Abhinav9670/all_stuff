/**
 * Delete Worker - High-Performance Hierarchical Deletion Engine
 *
 * NOTE: All config must be provided in full from the controller or test. This worker does not provide any default config values.
 *
 * Deletion Strategy (Bottom-Up Approach):
 * 1. PHASE 1: ID Collection - Collect all IDs from root tables based on date criteria
 * 2. PHASE 2: Cascade ID Collection - Collect child IDs based on parent IDs (Level 1, Level 2, etc.)
 * 3. PHASE 3: Hierarchical Deletion - Delete from deepest level first (Level 2 → Level 1 → Level 0)
 * 4. PHASE 4: Progress Tracking - Real-time progress with remaining record counts
 *
 * Features:
 * - Bottom-up deletion to maintain referential integrity
 * - Real-time progress tracking with remaining counts
 * - Memory-safe ID collection in batches
 * - Comprehensive error handling and rollback
 * - Worker thread architecture for parallel processing
 */

const { Worker, isMainThread, parentPort, workerData } = require('worker_threads');
const { Sequelize } = require('sequelize');

class HierarchicalProgressTracker {
  constructor() {
    this.startTime = new Date();
    this.phases = {
      idCollection: { status: 'pending', startTime: null, endTime: null, progress: {} },
      deletion: { status: 'pending', startTime: null, endTime: null, progress: {} }
    };
    this.currentPhase = 'idCollection';
    this.tableProgress = new Map();
    this.totalEstimatedRecords = 0;
    this.totalProcessedRecords = 0;
    this.memoryPeakUsage = 0;
    this.errors = [];
    this.retries = 0;
  }

  startPhase(phase) {
    this.currentPhase = phase;
    this.phases[phase].status = 'running';
    this.phases[phase].startTime = new Date();
    console.log(`Starting ${phase.toUpperCase()} phase`);
  }

  completePhase(phase) {
    this.phases[phase].status = 'completed';
    this.phases[phase].endTime = new Date();
    const duration = (this.phases[phase].endTime - this.phases[phase].startTime) / 1000;
    console.log(`Completed ${phase.toUpperCase()} phase in ${duration.toFixed(2)}s`);
  }

  updateTableProgress(tableName, processed, remaining, hierarchyLevel) {
    const tableInfo = {
      processed: processed,
      remaining: remaining,
      total: processed + remaining,
      hierarchyLevel: hierarchyLevel,
      lastUpdate: new Date(),
      completionPercentage: remaining > 0 ? ((processed / (processed + remaining)) * 100).toFixed(1) : 100
    };

    this.tableProgress.set(tableName, tableInfo);
    this.totalProcessedRecords += (processed - (this.tableProgress.get(tableName)?.processed || 0));

    const memUsage = Math.round(process.memoryUsage().heapUsed / 1024 / 1024);
    if (memUsage > this.memoryPeakUsage) {
      this.memoryPeakUsage = memUsage;
    }

    const elapsedTime = (new Date() - this.startTime) / 1000;
    const processingRate = this.totalProcessedRecords / elapsedTime;
    const totalRemaining = Array.from(this.tableProgress.values()).reduce((sum, info) => sum + info.remaining, 0);
    const estimatedTimeRemaining = processingRate > 0 ? Math.round(totalRemaining / processingRate) : null;

    if (parentPort) {
      parentPort.postMessage({
        type: 'progress',
        phase: this.currentPhase,
        table: tableName,
        processed: processed,
        remaining: remaining,
        total: tableInfo.total,
        hierarchyLevel: hierarchyLevel,
        completionPercentage: parseFloat(tableInfo.completionPercentage),
        totalProcessed: this.totalProcessedRecords,
        totalRemaining: totalRemaining,
        memoryUsage: memUsage,
        processingRate: Math.round(processingRate),
        estimatedTimeRemaining: estimatedTimeRemaining,
        elapsedTime: Math.round(elapsedTime),
        timestamp: new Date().toISOString()
      });
    }
  }

  setTableEstimate(tableName, estimated, hierarchyLevel) {
    const tableInfo = {
      processed: 0,
      remaining: estimated,
      total: estimated,
      hierarchyLevel: hierarchyLevel,
      lastUpdate: new Date(),
      completionPercentage: 0
    };

    this.tableProgress.set(tableName, tableInfo);
    this.totalEstimatedRecords += estimated;
  }

  getOverallProgress() {
    const elapsedSeconds = (new Date() - this.startTime) / 1000;
    const recordsPerSecond = this.totalProcessedRecords / elapsedSeconds;

    let totalRemaining = 0;
    let totalProcessed = 0;

    for (const info of this.tableProgress.values()) {
      totalProcessed += info.processed;
      totalRemaining += info.remaining;
    }

    const overallCompletion = totalRemaining > 0 ?
      ((totalProcessed / (totalProcessed + totalRemaining)) * 100).toFixed(1) : 100;

    return {
      currentPhase: this.currentPhase,
      totalProcessed: totalProcessed,
      totalRemaining: totalRemaining,
      overallCompletion: parseFloat(overallCompletion),
      executionTimeSeconds: elapsedSeconds.toFixed(2),
      averageRecordsPerSecond: Math.round(recordsPerSecond),
      peakMemoryUsageMB: this.memoryPeakUsage,
      totalRetries: this.retries,
      errorCount: this.errors.length,
      tablesInProgress: this.tableProgress.size
    };
  }

  addError(error, context = '') {
    this.errors.push({
      message: error.message,
      context: context,
      timestamp: new Date().toISOString()
    });
  }

  incrementRetry() {
    this.retries++;
  }
}

// Helper function to build where clause and parameters
const buildWhereClause = (formattedStartDate, formattedEndDate, dateColumn, condition) => {
  if (formattedStartDate && formattedEndDate) {
    return {
      whereClause: `${dateColumn} >= ? AND ${dateColumn} < ?`,
      queryParams: [formattedStartDate, formattedEndDate]
    };
  }
  return {
    whereClause: `${dateColumn} ${condition} ?`,
    queryParams: [formattedStartDate]
  };
};

// Helper function to execute ID collection query
const executeIdCollectionQuery = async (db, selectQuery, queryParams, tableName, offset) => {
  const queryTimeout = setTimeout(() => {
    console.warn(`   ⚠️ ID collection timeout for ${tableName} at offset ${offset}`);
  }, 180000);

  try {
    const result = await db.query(selectQuery, {
      replacements: queryParams,
      type: db.QueryTypes.SELECT,
      timeout: 300000
    });

    clearTimeout(queryTimeout);
    return result;
  } catch (queryError) {
    clearTimeout(queryTimeout);
    throw queryError;
  }
};

// Helper function to check if error is critical
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

  const recoverableErrors = [
    'ER_DUP_ENTRY',
    'ER_NO_REFERENCED_ROW',
    'ER_ROW_IS_REFERENCED',
    'ECONNREFUSED',
    'ETIMEDOUT',
    'timeout'
  ];

  const isRecoverable = recoverableErrors.some(recoverableError =>
    error.message.toLowerCase().includes(recoverableError.toLowerCase()) ||
    error.code === recoverableError
  );

  if (isRecoverable) {
    return false;
  }

  return criticalErrors.some(criticalError =>
    error.message.includes(criticalError) ||
    error.code === criticalError
  );
};

// ID Collection Manager
class IDCollectionManager {
  constructor(dbConnection, config, progressTracker) {
    this.db = dbConnection;
    this.config = config;
    this.progress = progressTracker;
    this.collectedIds = new Map();
  }

  async collectAllHierarchicalIds(tableConfigs, startDate, endDate, condition) {
    console.log(`Starting PARALLEL hierarchical ID collection for ${tableConfigs.length} tables`);

    const tablesByLevel = this.groupTablesByLevel(tableConfigs);
    const maxLevel = Math.max(...Object.keys(tablesByLevel).map(l => parseInt(l)));
    console.log(`Tables grouped into ${Object.keys(tablesByLevel).length} hierarchy levels (0-${maxLevel})`);

    console.log(`\nPHASE 1: Collecting Root Table IDs (Level 0) - PARALLEL PROCESSING`);
    const rootTables = tablesByLevel[0] || [];

    if (rootTables.length > 0) {
      const rootIdPromises = rootTables.map(async (rootTable) => {
        if (rootTable.dateColumn) {
          console.log(`[PARALLEL] Collecting IDs from root table: ${rootTable.table}`);
          const rootIds = await this.collectRootTableIds(rootTable, startDate, endDate, condition);
          this.collectedIds.set(rootTable.table, new Set(rootIds));
          console.log(`[PARALLEL] Collected ${rootIds.length.toLocaleString()} IDs from ${rootTable.table}`);
          return { table: rootTable.table, count: rootIds.length };
        } else {
          this.collectedIds.set(rootTable.table, new Set());
          return { table: rootTable.table, count: 0 };
        }
      });

      const rootResults = await Promise.all(rootIdPromises);
      const totalRootIds = rootResults.reduce((sum, result) => sum + result.count, 0);
      console.log(`PARALLEL Root Collection Complete: ${totalRootIds.toLocaleString()} total IDs from ${rootTables.length} tables`);
    }

    console.log(`\nPHASE 2: Collecting Child Table IDs (Levels 1-${maxLevel}) - PARALLEL PROCESSING`);

    for (let level = 1; level <= maxLevel; level++) {
      const levelTables = tablesByLevel[level] || [];
      if (levelTables.length === 0) continue;

      console.log(`\nLevel ${level}: Processing ${levelTables.length} child tables IN PARALLEL`);

      const levelPromises = levelTables.map(async (childTable) => {
        if (childTable.linkTo && childTable.fk) {
          const parentIds = this.collectedIds.get(childTable.linkTo);

          if (parentIds && parentIds.size > 0) {
            console.log(`  [PARALLEL-L${level}] Collecting IDs from: ${childTable.table} (parent: ${childTable.linkTo})`);

            const parentIdsArray = Array.from(parentIds);
            const childIds = await this.collectChildTableIds(childTable, parentIdsArray);
            this.collectedIds.set(childTable.table, new Set(childIds));

            console.log(`[PARALLEL-L${level}] Collected ${childIds.length.toLocaleString()} IDs from ${childTable.table} using ${parentIdsArray.length.toLocaleString()} parent IDs`);
            return { table: childTable.table, count: childIds.length, level: level };
          } else {
            this.collectedIds.set(childTable.table, new Set());
            return { table: childTable.table, count: 0, level: level };
          }
        } else {
          this.collectedIds.set(childTable.table, new Set());
          return { table: childTable.table, count: 0, level: level };
        }
      });

      const levelResults = await Promise.all(levelPromises);
      const levelTotalIds = levelResults.reduce((sum, result) => sum + result.count, 0);
      console.log(`  PARALLEL Level ${level} Complete: ${levelTotalIds.toLocaleString()} total IDs from ${levelTables.length} tables`);
    }

    console.log(`\nPARALLEL ID Collection Summary:`);
    let totalCollectedIds = 0;

    for (let level = 0; level <= maxLevel; level++) {
      const levelTables = tablesByLevel[level] || [];
      if (levelTables.length === 0) continue;

      console.log(`   Level ${level}:`);
      for (const table of levelTables) {
        const idCount = this.collectedIds.get(table.table)?.size || 0;
        totalCollectedIds += idCount;

        if (idCount > 0) {
          console.log(`     • ${table.table}: ${idCount.toLocaleString()} IDs`);
        } else {
          console.log(`     • ${table.table}: No IDs (will be skipped during deletion)`);
        }
      }
    }

    console.log(`\nTotal IDs collected: ${totalCollectedIds.toLocaleString()}`);
    console.log(` Ready for PARALLEL bottom-up hierarchical deletion (Level ${maxLevel} → Level 0)`);

    return this.collectedIds;
  }

  async collectRootTableIds(tableConfig, startDate, endDate, condition) {
    const { table: tableName, dateColumn, pk } = tableConfig;
    const formattedStartDate = this.formatDateForMySQL(startDate);
    const formattedEndDate = endDate ? this.formatDateForMySQL(endDate) : null;
    const primaryKey = pk || 'entity_id';

    try {
        const batchSize = this.config.batchSize;
        let allIds = [];
        let offset = 0;

        const collectBatch = async () => {
            const { whereClause, queryParams } = buildWhereClause(formattedStartDate, formattedEndDate, dateColumn, condition);
            const selectQuery = `
                SELECT ${primaryKey}
                FROM ${tableName}
                WHERE ${whereClause}
                ORDER BY ${primaryKey}
                LIMIT ${batchSize} OFFSET ${offset}
            `;

            const result = await executeIdCollectionQuery(this.db, selectQuery, queryParams, tableName, offset);
            const batchIds = result.map(row => row[primaryKey]).filter(id => id != null);
            allIds = allIds.concat(batchIds);
            offset += batchSize;

            if (allIds.length % 100000 === 0 && allIds.length > 0) {
                console.log(` Collected batch: ${batchIds.length} IDs (total: ${allIds.length.toLocaleString()})`);
            }

            return result.length < batchSize;
        };

        while (true) {
            try {
                const shouldBreak = await collectBatch();
                if (shouldBreak) {
                    console.log(`Finished collecting IDs for ${tableName}. Total: ${allIds.length.toLocaleString()}`);
                    break;
                }
                await new Promise(resolve => setTimeout(resolve, 10));
            } catch (queryError) {
                console.error(`  Query failed for ${tableName} at offset ${offset}:`, queryError.message);
                if (isCriticalError(queryError)) throw queryError;
                console.warn(`  Skipping batch at offset ${offset}, continuing...`);
                offset += batchSize;
            }
        }

        return allIds;
    } catch (error) {
        console.error(` Failed to collect root IDs from ${tableName}:`, error.message);
        this.progress.addError(error, `ID collection: ${tableName}`);
        return [];
    }
  }

  async collectChildTableIds(tableConfig, parentIds) {
    const { table: tableName, fk, pk } = tableConfig;
    const primaryKey = pk || 'entity_id';

    if (!parentIds || parentIds.length === 0) {
      return [];
    }

    try {
      const chunkSize = this.config.chunkSize;
      let allChildIds = [];

      const chunks = [];
      for (let i = 0; i < parentIds.length; i += chunkSize) {
        chunks.push(parentIds.slice(i, i + chunkSize));
      }

      const parallelBatchSize = 3;

      for (let i = 0; i < chunks.length; i += parallelBatchSize) {
        const chunkBatch = chunks.slice(i, i + parallelBatchSize);

        const chunkPromises = chunkBatch.map(async (parentChunk, index) => {
          const placeholders = parentChunk.map(() => '?').join(',');

          const selectQuery = `
            SELECT ${primaryKey}
            FROM ${tableName}
            WHERE ${fk} IN (${placeholders})
          `;

          const queryTimeout = setTimeout(() => {
            console.warn(` Query timeout for ${tableName} chunk ${index + 1}`);
          }, 180000);

          try {
            const result = await this.db.query(selectQuery, {
              replacements: parentChunk,
              type: this.db.QueryTypes.SELECT,
              timeout: 300000
            });

            clearTimeout(queryTimeout);
            const chunkIds = result.map(row => row[primaryKey]).filter(id => id != null);
            return chunkIds;
          } catch (queryError) {
            clearTimeout(queryTimeout);
            console.error(` Query failed for ${tableName} chunk ${index + 1}:`, queryError.message);
            return [];
          }
        });

        const chunkResults = await Promise.all(chunkPromises);
        const batchIds = chunkResults.flat();
        allChildIds = allChildIds.concat(batchIds);

        const processedIds = (i + chunkBatch.length) * chunkSize;
        if (processedIds % 50000 === 0 && processedIds > 0) {
          console.log(`[PARALLEL] Processed ${Math.min(processedIds, parentIds.length).toLocaleString()}/${parentIds.length.toLocaleString()} parent IDs (found ${allChildIds.length.toLocaleString()} child IDs)`);
        }

        if (i + parallelBatchSize < chunks.length) {
          await new Promise(resolve => setTimeout(resolve, 100));
        }
      }

      return allChildIds;

    } catch (error) {
      console.error(`Failed to collect child IDs from ${tableName}:`, error.message);
      this.progress.addError(error, `Child ID collection: ${tableName}`);
      return [];
    }
  }

  groupTablesByLevel(tableConfigs) {
    const tablesByLevel = {};
    const tableMap = new Map(tableConfigs.map(t => [t.table, t]));

    for (const tableConfig of tableConfigs) {
      const level = this.calculateHierarchyLevel(tableConfig, tableMap);

      if (!tablesByLevel[level]) {
        tablesByLevel[level] = [];
      }

      tablesByLevel[level].push({
        ...tableConfig,
        hierarchyLevel: level
      });
    }

    return tablesByLevel;
  }

  calculateHierarchyLevel(tableConfig, tableMap, visited = new Set()) {
    if (visited.has(tableConfig.table)) {
      console.warn(`Circular dependency detected for table: ${tableConfig.table}`);
      return 0;
    }

    if (!tableConfig.linkTo || !tableConfig.fk) {
      return 0;
    }

    visited.add(tableConfig.table);

    const parentTable = tableMap.get(tableConfig.linkTo);
    if (!parentTable) {
      console.warn(`Parent table ${tableConfig.linkTo} not found for ${tableConfig.table}, treating as level 1`);
      return 1;
    }

    const parentLevel = this.calculateHierarchyLevel(parentTable, tableMap, visited);
    const childLevel = parentLevel + 1;

    if (childLevel > 5) {
      console.warn(`Deep hierarchy detected: ${tableConfig.table} at level ${childLevel}. Consider reviewing table relationships.`);
    }

    return childLevel;
  }

  formatDateForMySQL(date) {
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
  }

  getCollectedIds(tableName) {
    return this.collectedIds.get(tableName) || new Set();
  }

  getCollectionSummary() {
    const summary = {};
    for (const [tableName, idSet] of this.collectedIds) {
      summary[tableName] = idSet.size;
    }
    return summary;
  }
}

// Helper function for initial record count estimation
const getInitialRecordCount = async (db, tableName, idColumn, idsArray, transaction) => {
  try {
    const sampleSize = Math.min(1000, idsArray.length);
    const countQuery = `SELECT COUNT(*) as count FROM ${tableName} WHERE ${idColumn} IN (${idsArray.slice(0, sampleSize).map(() => '?').join(',')})`;
    const countResult = await db.query(countQuery, {
      replacements: idsArray.slice(0, sampleSize),
      transaction,
      type: db.QueryTypes.SELECT,
      timeout: 30000
    });
    const recordCount = countResult[0]?.count || 0;
    if (idsArray.length > sampleSize) {
      const estimatedTotal = Math.round((recordCount / sampleSize) * idsArray.length);
      console.log(`   📊 Estimated records to delete: ~${estimatedTotal.toLocaleString()} (based on ${idsArray.length.toLocaleString()} IDs)`);
      return estimatedTotal;
    }
    return recordCount;
  } catch (error) {
    console.warn(`   ⚠️ Could not get initial count for ${tableName}: ${error.message}`);
    return Math.min(idsArray.length, 100000);
  }
};

// Helper function for processing deletion batches
const processDeletionBatch = async (options) => {
    const {
        db,
        tableName,
        idColumn,
        idChunk,
        transaction,
        progress,
        state
    } = options;

    const placeholders = idChunk.map(() => '?').join(',');
    const deleteQuery = `DELETE FROM ${tableName} WHERE ${idColumn} IN (${placeholders})`;

    const deleteTimeout = setTimeout(() => {
        console.warn(` Deletion timeout warning for ${tableName} at position ${state.processedIds}`);
    }, 120000);

    try {
        const deleteResult = await db.query(deleteQuery, {
            replacements: idChunk,
            transaction,
            timeout: 300000
        });

        clearTimeout(deleteTimeout);

        const [result] = deleteResult || [];
        let affectedRows = 0;
        if (result && typeof result.affectedRows !== 'undefined') {
            affectedRows = result.affectedRows;
        } else if (Array.isArray(deleteResult) && deleteResult.length > 0) {
            affectedRows = deleteResult[0];
        }

        const newTotalDeleted = state.totalDeleted + affectedRows;
        const newProcessedIds = state.processedIds + idChunk.length;
        const newRemainingRecords = Math.max(0, state.remainingRecords - affectedRows);

        progress.updateTableProgress(tableName, newTotalDeleted, newRemainingRecords, state.hierarchyLevel);

        return {
            affectedRows,
            newTotalDeleted,
            newProcessedIds,
            newRemainingRecords
        };
    } catch (error) {
        clearTimeout(deleteTimeout);
        throw error;
    }
};

// Bottom-Up Deletion Engine
class BottomUpDeletionEngine {
  constructor(dbConnection, config, progressTracker, idCollectionManager) {
    this.db = dbConnection;
    this.config = config;
    this.progress = progressTracker;
    this.idManager = idCollectionManager;
    this.isRollbackRequested = false;
  }

  async executeHierarchicalDeletion(tableConfigs, startDate, endDate, condition, transaction) {
    console.log(`Starting PARALLEL bottom-up hierarchical deletion`);

    const tablesByLevel = this.idManager.groupTablesByLevel(tableConfigs);
    const levels = Object.keys(tablesByLevel).map(l => parseInt(l)).sort((a, b) => b - a);

    console.log(`PARALLEL Deletion order: Levels ${levels.join(' → ')}`);

    let totalDeleted = 0;
    const results = {};

    for (const level of levels) {
      if (this.isRollbackRequested) {
        console.log(` Stopping deletion due to rollback request at level ${level}`);
        break;
      }

      const levelTables = tablesByLevel[level];
      console.log(`\nProcessing Level ${level}: ${levelTables.length} tables IN PARALLEL`);

      const levelPromises = levelTables.map(async (tableConfig) => {
        if (this.isRollbackRequested) {
          console.log(`Stopping deletion due to rollback request`);
          return { tableName: tableConfig.table, recordsDeleted: 0, success: false };
        }

        const tableName = tableConfig.table;
        console.log(`[PARALLEL-L${level}] Deleting from: ${tableName} (Level ${level})`);

        try {
          let recordsDeleted = 0;

          if (level === 0) {
            recordsDeleted = await this.deleteRootTableByDate(tableConfig, startDate, endDate, condition, transaction);
          } else {
            recordsDeleted = await this.deleteChildTableByIds(tableConfig, transaction);
          }

          console.log(`[PARALLEL-L${level}] ${tableName}: ${recordsDeleted.toLocaleString()} records deleted`);

          return {
            tableName: tableName,
            recordsDeleted: recordsDeleted,
            success: true,
            hierarchyLevel: level
          };

        } catch (error) {
          console.error(` [PARALLEL-L${level}] ${tableName} deletion failed:`, error.message);
          this.progress.addError(error, `Deletion: ${tableName}`);

          return {
            tableName: tableName,
            recordsDeleted: 0,
            success: false,
            error: error.message,
            hierarchyLevel: level
          };
        }
      });

      const levelResults = await Promise.all(levelPromises);

      let levelTotalDeleted = 0;
      let levelSuccessCount = 0;

      for (const result of levelResults) {
        results[result.tableName] = {
          success: result.success,
          recordsDeleted: result.recordsDeleted,
          hierarchyLevel: result.hierarchyLevel,
          error: result.error
        };

        levelTotalDeleted += result.recordsDeleted;
        if (result.success) levelSuccessCount++;

        if (!result.success && result.error && isCriticalError(new Error(result.error))) {
          throw new Error(`Critical error in ${result.tableName}: ${result.error}`);
        }
      }

      totalDeleted += levelTotalDeleted;
      console.log(`PARALLEL Level ${level} Complete: ${levelTotalDeleted.toLocaleString()} records deleted from ${levelSuccessCount}/${levelTables.length} tables`);

      if (level > Math.min(...levels)) {
        await this.sleep(500);
      }
    }

    console.log(`\nPARALLEL Hierarchical deletion completed: ${totalDeleted.toLocaleString()} total records deleted`);

    return {
      totalDeleted,
      results,
      levelsProcessed: levels.length
    };
  }

  async deleteRootTableByDate(tableConfig, startDate, endDate, condition, transaction) {
    const { table: tableName, dateColumn } = tableConfig;

    if (!dateColumn) {
      return 0;
    }

    const formattedStartDate = this.idManager.formatDateForMySQL(startDate);
    const formattedEndDate = endDate ? this.idManager.formatDateForMySQL(endDate) : null;

    const { whereClause, queryParams } = buildWhereClause(formattedStartDate, formattedEndDate, dateColumn, condition);

    return await this.executeBatchDeletion(
      tableName,
      whereClause,
      queryParams,
      transaction,
      tableConfig.hierarchyLevel || 0
    );
  }

  async deleteChildTableByIds(tableConfig, transaction) {
    const { table: tableName, fk, linkTo } = tableConfig;

    const parentIds = this.idManager.getCollectedIds(linkTo);

    if (!parentIds || parentIds.size === 0) {
      this.progress.updateTableProgress(tableName, 0, 0, tableConfig.hierarchyLevel || 0);
      return 0;
    }

    const parentIdsArray = Array.from(parentIds);

    return await this.executeBatchDeletionByIds(
      tableName,
      fk,
      parentIdsArray,
      transaction,
      tableConfig.hierarchyLevel || 0
    );
  }

  async executeBatchDeletion(tableName, whereClause, queryParams, transaction, hierarchyLevel) {
    const batchSize = this.config.batchSize;
    const batchDelay = this.config.batchDelay;
    const maxBatches = this.config.maxDeleteBatches;
    let totalDeleted = 0;
    let batchCount = 0;
    let remainingRecords = await this.getRemainingRecordCount(tableName, whereClause, queryParams, transaction);

    console.log(`Starting batch deletion: ~${remainingRecords.toLocaleString()} records to delete`);
    this.progress.updateTableProgress(tableName, 0, remainingRecords, hierarchyLevel);

    while (remainingRecords > 0 && !this.isRollbackRequested) {
      try {
        const deleteQuery = `DELETE FROM ${tableName} WHERE ${whereClause} LIMIT ${batchSize}`;

        const deleteResult = await this.db.query(deleteQuery, {
          replacements: queryParams,
          transaction
        });

        const affectedRows = this.extractAffectedRows(deleteResult);
        totalDeleted += affectedRows;
        batchCount++;
        remainingRecords = Math.max(0, remainingRecords - affectedRows);

        this.progress.updateTableProgress(tableName, totalDeleted, remainingRecords, hierarchyLevel);

        if (batchCount % 50 === 0 || affectedRows === 0) {
          console.log(`Batch ${batchCount}: ${affectedRows} deleted | Total: ${totalDeleted.toLocaleString()} | Remaining: ~${remainingRecords.toLocaleString()}`);
        }

        if (batchCount % (this.config.progressBatchFrequency || 50) === 0 || affectedRows === 0) {
          this.progress.updateTableProgress(tableName, totalDeleted, remainingRecords, hierarchyLevel);
        }

        if (affectedRows === 0) {
          console.log(` No more records to delete`);
          break;
        }

        if (batchDelay > 0) {
          await this.sleep(batchDelay);
        }

        if (affectedRows < batchSize) {
          remainingRecords = 0;
        }

      } catch (error) {
        console.error(` Error in batch ${batchCount + 1}:`, error.message);
        throw error;
      }
    }

    this.progress.updateTableProgress(tableName, totalDeleted, 0, hierarchyLevel);

    return totalDeleted;
  }

  async executeBatchDeletionByIds(tableName, idColumn, idsArray, transaction, hierarchyLevel) {
    const idChunkSize = this.config.chunkSize;
    const state = {
        totalDeleted: 0,
        processedIds: 0,
        remainingRecords: await getInitialRecordCount(this.db, tableName, idColumn, idsArray, transaction)
    };
    const totalIds = idsArray.length;

    console.log(`Starting ID-based deletion: ${totalIds.toLocaleString()} parent IDs → ~${state.remainingRecords.toLocaleString()} records to delete`);
    this.progress.updateTableProgress(tableName, 0, state.remainingRecords, hierarchyLevel);

    const processIdBatch = async (i) => {
        const idChunk = idsArray.slice(i, i + idChunkSize);
        try {
            const batchResult = await processDeletionBatch({
                db: this.db,
                tableName,
                idColumn,
                idChunk,
                transaction,
                progress: this.progress,
                state: {
                    ...state,
                    hierarchyLevel
                }
            });

            state.totalDeleted = batchResult.newTotalDeleted;
            state.processedIds = batchResult.newProcessedIds;
            state.remainingRecords = batchResult.newRemainingRecords;

            if (i % (idChunkSize * 50) === 0 && i > 0) {
                const progressPercent = ((state.processedIds / totalIds) * 100).toFixed(1);
                const deletionRate = state.remainingRecords > 0 ? ((state.totalDeleted / (state.totalDeleted + state.remainingRecords)) * 100).toFixed(1) : 100;
                console.log(`Processed ${state.processedIds.toLocaleString()}/${totalIds.toLocaleString()} IDs (${progressPercent}%) | Deleted: ${state.totalDeleted.toLocaleString()} | Remaining: ~${state.remainingRecords.toLocaleString()} (${deletionRate}% complete)`);
            }

            if (this.config.batchDelay > 0) {
                await this.sleep(Math.max(this.config.batchDelay, 10));
            }
        } catch (error) {
            console.error(` Error in ID batch at position ${i}:`, error.message);
            if (isCriticalError(error)) throw error;
            console.warn(` Skipping batch at position ${i} due to error, continuing...`);
            state.processedIds += idChunk.length;
        }
    };

    for (let i = 0; i < idsArray.length && !this.isRollbackRequested; i += idChunkSize) {
        await processIdBatch(i);
    }

    this.progress.updateTableProgress(tableName, state.totalDeleted, 0, hierarchyLevel);
    const completionPercent = totalIds > 0 ? ((state.processedIds / totalIds) * 100).toFixed(1) : 100;
    console.log(` Completed ${tableName}: ${state.totalDeleted.toLocaleString()} records deleted | ${state.processedIds.toLocaleString()}/${totalIds.toLocaleString()} IDs processed (${completionPercent}%)`);

    return state.totalDeleted;
  }

  async getRemainingRecordCount(tableName, whereClause, queryParams, transaction) {
    try {
      const countQuery = `SELECT COUNT(*) as count FROM ${tableName} WHERE ${whereClause}`;
      const result = await this.db.query(countQuery, {
        replacements: queryParams,
        transaction,
        type: this.db.QueryTypes.SELECT
      });

      return result[0]?.count || 0;
    } catch (error) {
      console.warn(`  Could not get remaining count for ${tableName}: ${error.message}`);
      return 0;
    }
  }

  extractAffectedRows(deleteResult) {
    const [result] = deleteResult || [];
    if (result && typeof result.affectedRows !== 'undefined') {
      return result.affectedRows;
    }
    if (Array.isArray(deleteResult) && deleteResult.length > 0) {
      return deleteResult[0];
    }
    return 0;
  }

  async sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  requestRollback() {
    this.isRollbackRequested = true;
    console.log(`Rollback requested for deletion engine`);
  }
}

// Memory Management
class MemoryManager {
  constructor(config) {
    this.config = config;
    this.lastGC = new Date();
    this.gcCount = 0;
  }

  checkMemoryUsage() {
    const memUsage = Math.round(process.memoryUsage().heapUsed / 1024 / 1024);

    if (memUsage > this.config.forceGCThreshold && global.gc) {
      const now = new Date();
      if (now - this.lastGC > 10000) {
        global.gc();
        this.lastGC = now;
        this.gcCount++;

        const newMemUsage = Math.round(process.memoryUsage().heapUsed / 1024 / 1024);
        console.log(`🧹 GC executed: ${memUsage}MB → ${newMemUsage}MB (${this.gcCount} total)`);

        if (parentPort) {
          parentPort.postMessage({
            type: 'memory-warning',
            memoryUsage: newMemUsage,
            gcTriggered: true
          });
        }
      }
    }

    return memUsage;
  }
}

// Database Connection Manager
class DatabaseConnection {
  constructor(dbConfig, isArchive = false) {
    this.dbConfig = dbConfig;
    this.isArchive = isArchive;
    this.sequelize = null;
    this.connected = false;
  }

  async connect() {
    try {
      this.sequelize = new Sequelize(
        this.dbConfig.database,
        this.dbConfig.username,
        this.dbConfig.password,
        {
          host: this.dbConfig.host,
          port: this.dbConfig.port,
          dialect: this.dbConfig.dialect || 'mysql',
          logging: false,
          pool: {
            max: 15,
            min: 3,
            acquire: 120000,
            idle: 10000
          },
          dialectOptions: {
            connectTimeout: 300000,
            multipleStatements: true,
            charset: 'utf8mb4'
          },
          retry: {
            max: 3,
            timeout: 300000
          }
        }
      );

      await this.sequelize.authenticate();
      this.connected = true;

      console.log(`Connected to ${this.isArchive ? 'ARCHIVE' : 'PRIMARY'} database`);
      return this.sequelize;

    } catch (error) {
      console.error(`Failed to connect to ${this.isArchive ? 'ARCHIVE' : 'PRIMARY'} database:`, error.message);
      throw error;
    }
  }

  async disconnect() {
    if (this.sequelize && this.connected) {
      await this.sequelize.close();
      this.connected = false;
      console.log(`Disconnected from ${this.isArchive ? 'ARCHIVE' : 'PRIMARY'} database`);
    }
  }

  getConnection() {
    if (!this.connected || !this.sequelize) {
      throw new Error('Database not connected');
    }
    return this.sequelize;
  }
}

// Main Delete Worker Class
class DeleteWorker {
  constructor(workerData) {
    this.workerData = workerData;
    this.config = workerData.config; // No merge with DEFAULT_CONFIG
    this.progress = new HierarchicalProgressTracker();
    this.memory = new MemoryManager(this.config);

    this.primaryDb = null;
    this.archiveDb = null;
    this.idManager = null;
    this.deletionEngine = null;

    this.isShuttingDown = false;
    this.isRollbackRequested = false;

    if (parentPort) {
      parentPort.on('message', this.handleMessage.bind(this));
    }
  }

  async initialize() {
    try {
      console.log(`Initializing Hierarchical Delete Worker`);

      this.primaryDb = new DatabaseConnection(this.workerData.primaryDbConfig, false);
      await this.primaryDb.connect();

      if (this.workerData.archiveDbConfig) {
        this.archiveDb = new DatabaseConnection(this.workerData.archiveDbConfig, true);
        await this.archiveDb.connect();
      }

      const targetDb = this.workerData.useArchiveDb ? this.archiveDb : this.primaryDb;
      this.idManager = new IDCollectionManager(
        targetDb.getConnection(),
        this.config,
        this.progress
      );

      this.deletionEngine = new BottomUpDeletionEngine(
        targetDb.getConnection(),
        this.config,
        this.progress,
        this.idManager
      );

      console.log(`Hierarchical Delete Worker initialized successfully`);
      return true;

    } catch (error) {
      console.error(`Failed to initialize Delete Worker:`, error.message);
      this.progress.addError(error, 'initialization');
      throw error;
    }
  }

  async executeHierarchicalDeletion() {
    let transaction = null;
    let totalDeleted = 0;
    const results = {};

    try {
      const targetDb = this.workerData.useArchiveDb ? this.archiveDb : this.primaryDb;
      const dbConnection = targetDb.getConnection();
      const tableConfigs = this.workerData.tables || [this.workerData.tableConfig];
      const cutoffDate = new Date(this.workerData.cutoffDate);
      const condition = this.workerData.condition;

      console.log(`Starting hierarchical deletion for ${tableConfigs.length} tables`);
      console.log(`Cutoff date: ${cutoffDate.toISOString()}`);
      console.log(`Condition: ${condition}`);

      this.progress.startPhase('idCollection');

      const startDate = this.workerData.startDate ? new Date(this.workerData.startDate) : undefined;
      const endDate = this.workerData.endDate ? new Date(this.workerData.endDate) : undefined;
      await this.idManager.collectAllHierarchicalIds(
        tableConfigs,
        startDate,
        endDate,
        condition
      );

      this.progress.completePhase('idCollection');

      transaction = await dbConnection.transaction({
        isolationLevel: Sequelize.Transaction.ISOLATION_LEVELS.READ_COMMITTED
      });

      console.log(`Transaction started for deletion phase`);

      this.progress.startPhase('deletion');

      const deletionResult = await this.deletionEngine.executeHierarchicalDeletion(
        tableConfigs,
        startDate,
        endDate,
        condition,
        transaction
      );

      totalDeleted = deletionResult.totalDeleted;
      Object.assign(results, deletionResult.results);

      this.progress.completePhase('deletion');

      if (!this.isRollbackRequested && transaction) {
        await transaction.commit();
        console.log(`Hierarchical deletion transaction committed successfully`);
      }

      return {
        success: true,
        totalRecordsDeleted: totalDeleted,
        results: results,
        performanceStats: this.progress.getOverallProgress(),
        idCollectionSummary: this.idManager.getCollectionSummary(),
        hierarchyLevelsProcessed: deletionResult.levelsProcessed
      };

    } catch (error) {
      console.error(`Hierarchical Delete Worker execution failed:`, error.message);

      if (transaction) {
        try {
          await transaction.rollback();
          console.log(`Transaction rolled back successfully`);
        } catch (rollbackError) {
          console.error(`Rollback failed:`, rollbackError.message);
        }
      }

      this.progress.addError(error, 'execution');

      return {
        success: false,
        totalRecordsDeleted: totalDeleted,
        error: error.message,
        results: results,
        performanceStats: this.progress.getOverallProgress(),
        idCollectionSummary: this.idManager ? this.idManager.getCollectionSummary() : {}
      };
    }
  }

  async cleanup() {
    try {
      console.log(`Cleaning up Delete Worker...`);

      if (this.primaryDb) {
        await this.primaryDb.disconnect();
      }

      if (this.archiveDb) {
        await this.archiveDb.disconnect();
      }

      if (global.gc) {
        global.gc();
      }

      console.log(`Delete Worker cleanup completed`);

    } catch (error) {
      console.error(`Error during cleanup:`, error.message);
    }
  }

  handleMessage(message) {
    switch (message.type) {
      case 'shutdown':
        console.log(`Shutdown requested`);
        this.isShuttingDown = true;
        break;

      case 'rollback':
        console.log(`Rollback requested`);
        this.isRollbackRequested = true;
        if (this.deletionEngine) {
          this.deletionEngine.requestRollback();
        }
        break;

      default:
        console.log(`Unknown message type: ${message.type}`);
    }
  }

  sendProgress(data) {
    if (parentPort) {
      parentPort.postMessage({
        type: 'progress',
        ...data
      });
    }
  }

  sendResult(result) {
    if (parentPort) {
      parentPort.postMessage({
        type: 'result',
        success: result.success,
        result: result
      });
    }
  }

  sendError(error) {
    if (parentPort) {
      parentPort.postMessage({
        type: 'error',
        error: error.message
      });
    }
  }
}

// Worker Thread Execution
if (!isMainThread && workerData) {
  (async () => {
    const worker = new DeleteWorker(workerData);

    try {
      await worker.initialize();

      const result = await worker.executeHierarchicalDeletion();

      worker.sendResult(result);

    } catch (error) {
      console.error(`Delete Worker fatal error:`, error);
      worker.sendError(error);

    } finally {
      await worker.cleanup();

      if (worker.isRollbackRequested && parentPort) {
        parentPort.postMessage({ type: 'rollback-complete' });
      }
    }
  })();
}

// Factory function for creating delete workers
const createDeleteWorker = (workerConfig) => {
  return new Promise((resolve, reject) => {
    const workerPath = __filename;

    const worker = new Worker(workerPath, {
      workerData: workerConfig,
      resourceLimits: {
        maxOldGenerationSizeMb: 2048,
        maxYoungGenerationSizeMb: 1024
      }
    });

    const timeout = setTimeout(() => {
      console.warn(`Worker timeout after 90 minutes, attempting graceful shutdown...`);
      worker.postMessage({ type: 'shutdown' });

      setTimeout(() => {
        worker.terminate();
        reject(new Error('Worker timeout after 90 minutes'));
      }, 30000);
    }, 5400000);

    let progressMessageCount = 0;

    worker.on('message', (message) => {
      if (message.type === 'result') {
        clearTimeout(timeout);
        resolve(message.result);
      } else if (message.type === 'error') {
        clearTimeout(timeout);
        reject(new Error(message.error));
      } else if (message.type === 'progress') {
        progressMessageCount++;
        if (progressMessageCount % 10 === 0) {
          console.log(`Worker progress: ${message.table} - ${message.processed}/${message.total} (${message.completionPercentage}%)`);
        }
      }
    });

    worker.on('error', (error) => {
      clearTimeout(timeout);
      console.error(`Worker error:`, error.message);
      reject(error);
    });

    worker.on('exit', (code) => {
      clearTimeout(timeout);
      if (code !== 0) {
        console.error(`Worker stopped with exit code ${code}`);
        reject(new Error(`Worker stopped with exit code ${code}`));
      }
    });
  });
};

module.exports = {
  DeleteWorker,
  createDeleteWorker
};