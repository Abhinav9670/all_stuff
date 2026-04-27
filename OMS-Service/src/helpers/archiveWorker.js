/**
 * Archive Worker - High-Performance Hierarchical Archival Engine
 *
 * NOTE: All config must be provided in full from the controller or test. This worker does not provide any default config values.
 *
 * Archival Strategy (Top-Down Approach):
 * 1. PHASE 1: ID Collection - Collect all IDs from root tables based on date criteria
 * 2. PHASE 2: Cascade ID Collection - Collect child IDs based on parent IDs (Level 1, Level 2, etc.)
 * 3. PHASE 3: Hierarchical Archival - Archive from the highest level down (Level 0 → Level 1 → Level 2)
 * 4. PHASE 4: Progress Tracking - Real-time progress with remaining record counts
 *
 * Features:
 * - Top-down archival to ensure data integrity during copy
 * - Real-time progress tracking
 * - Memory-safe ID collection in batches
 * - Comprehensive error handling and transactional integrity
 * - Worker thread architecture for parallel processing
 */

const { Worker, isMainThread, parentPort, workerData } = require('worker_threads');
const { Sequelize } = require('sequelize');

// Helper function to check table existence
const checkTableExistence = async (tableName, db, dbType) => {
  const result = await db.query(
    `SELECT COUNT(*) as count FROM information_schema.tables WHERE table_name = ? AND table_schema = DATABASE()`,
    {
      replacements: [tableName],
      type: Sequelize.QueryTypes.SELECT
    }
  );
  const exists = result && result.length > 0 && result[0].count > 0;
  console.log(`Table ${tableName} exists - ${dbType}: ${exists}`);
  return exists;
};

// Helper function to get table columns
const getTableColumns = async (tableName, db) => {
  console.log(`Fetching columns for table: ${tableName}`);

  const columnsRows = await db.query(
    `SELECT COLUMN_NAME, COLUMN_TYPE, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT
     FROM information_schema.columns
     WHERE table_name = ? AND table_schema = DATABASE()
     ORDER BY ORDINAL_POSITION`,
    {
      replacements: [tableName],
      type: Sequelize.QueryTypes.SELECT
    }
  );

  console.log(`${tableName} columns count: ${columnsRows.length}`);

  if (columnsRows.length > 0) {
    console.log('Sample columns:', columnsRows.slice(0, 3).map(col => ({
      name: col.COLUMN_NAME,
      type: col.COLUMN_TYPE
    })));
  }

  return Array.isArray(columnsRows) ? columnsRows : [];
};

// Helper function to validate column compatibility
const validateColumnCompatibility = (primaryColumns, archiveColumns) => {
  const normalize = s => (s || '').toLowerCase().trim();

  const primaryMap = new Map(primaryColumns.map(col => [
    normalize(col.COLUMN_NAME),
    {
      type: col.COLUMN_TYPE,
      dataType: col.DATA_TYPE,
      nullable: col.IS_NULLABLE,
      default: col.COLUMN_DEFAULT
    }
  ]));

  const archiveMap = new Map(archiveColumns.map(col => [
    normalize(col.COLUMN_NAME),
    {
      type: col.COLUMN_TYPE,
      dataType: col.DATA_TYPE,
      nullable: col.IS_NULLABLE,
      default: col.COLUMN_DEFAULT
    }
  ]));

  const missingInArchive = [];
  const typeMismatch = [];

  for (const [colName, colInfo] of primaryMap.entries()) {
    if (!archiveMap.has(colName)) {
      missingInArchive.push(colName);
    } else {
      const archiveInfo = archiveMap.get(colName);
      if (archiveInfo.type !== colInfo.type) {
        typeMismatch.push({
          column: colName,
          primaryType: colInfo.type,
          archiveType: archiveInfo.type
        });
      }
    }
  }

  const missingInPrimary = [];
  for (const [colName] of archiveMap.entries()) {
    if (!primaryMap.has(colName)) {
      missingInPrimary.push(colName);
    }
  }

  return { missingInArchive, typeMismatch, missingInPrimary };
};

// Helper function to build validation error message
const buildValidationErrorMessage = (missingInArchive, typeMismatch, missingInPrimary) => {
  let reason = '';
  if (missingInArchive.length > 0) {
    reason += `Missing in archive: ${missingInArchive.join(', ')}. `;
  }
  if (typeMismatch.length > 0) {
    reason += `Type mismatches: ${typeMismatch.map(m =>
      `${m.column} (primary: ${m.primaryType}, archive: ${m.archiveType})`
    ).join('; ')}. `;
  }
  if (missingInPrimary.length > 0) {
    reason += `Extra columns in archive: ${missingInPrimary.join(', ')}.`;
  }
  return reason.trim();
};

// Refactored validateTableSchema function - Reduced from 32 to under 15 complexity
const validateTableSchema = async (tableName, primaryDb, archiveDb) => {
  try {
    console.log(`Validating schema for table: ${tableName}`);

    // Check table existence in both databases
    const primaryExists = await checkTableExistence(tableName, primaryDb, 'Primary');
    const archiveExists = await checkTableExistence(tableName, archiveDb, 'Archive');

    if (!primaryExists && !archiveExists) {
      return { isValid: false, reason: 'Table does not exist in either primary or archive DB' };
    }
    if (!primaryExists) {
      return { isValid: false, reason: 'Table does not exist in primary DB' };
    }
    if (!archiveExists) {
      return { isValid: false, reason: 'Table does not exist in archive DB' };
    }

    // Get columns from both databases
    const primaryColumns = await getTableColumns(tableName, primaryDb);
    const archiveColumns = await getTableColumns(tableName, archiveDb);

    if (primaryColumns.length === 0 && archiveColumns.length === 0) {
      return { isValid: false, reason: 'No columns found in either database - possible permissions issue' };
    }
    if (primaryColumns.length === 0) {
      return { isValid: false, reason: 'No columns found in primary database - possible permissions issue' };
    }
    if (archiveColumns.length === 0) {
      return { isValid: false, reason: 'No columns found in archive database - possible permissions issue' };
    }

    // Validate column compatibility
    const { missingInArchive, typeMismatch, missingInPrimary } = validateColumnCompatibility(primaryColumns, archiveColumns);

    if (missingInArchive.length > 0 || typeMismatch.length > 0 || missingInPrimary.length > 0) {
      const reason = buildValidationErrorMessage(missingInArchive, typeMismatch, missingInPrimary);
      return { isValid: false, reason };
    }

    console.log(`Schema validation successful for table: ${tableName}`);
    return {
      isValid: true,
      reason: 'Table and columns match in both primary and archive DBs',
      columnCount: primaryColumns.length
    };

  } catch (error) {
    console.error(`Schema validation error for table ${tableName}:`, error);
    return {
      isValid: false,
      reason: `Schema validation error: ${error.message}`
    };
  }
};

// Worker Configuration
// REMOVE DEFAULT_CONFIG

// Enhanced Progress Tracker
class HierarchicalProgressTracker {
  constructor() {
    this.startTime = new Date();
    this.phases = {
      validation: { status: 'pending', startTime: null, endTime: null },
      idCollection: { status: 'pending', startTime: null, endTime: null },
      archival: { status: 'pending', startTime: null, endTime: null }
    };
    this.currentPhase = 'idCollection';
    this.tableProgress = new Map();
    this.totalEstimatedRecords = 0;
    this.totalProcessedRecords = 0;
    this.errors = [];
  }

  startPhase(phase) {
    this.currentPhase = phase;
    this.phases[phase].status = 'running';
    this.phases[phase].startTime = new Date();
    console.log(`Starting ${phase.toUpperCase()} phase`);
    this.sendProgressUpdate();
  }

  completePhase(phase) {
    this.phases[phase].status = 'completed';
    this.phases[phase].endTime = new Date();
    const duration = (this.phases[phase].endTime - this.phases[phase].startTime) / 1000;
    console.log(`Completed ${phase.toUpperCase()} phase in ${duration.toFixed(2)}s`);
    this.sendProgressUpdate();
  }

  updateTableProgress(tableName, processed, total) {
    const tableInfo = this.tableProgress.get(tableName) || { processed: 0, total: 0 };
    tableInfo.processed += processed;
    tableInfo.total = total;
    tableInfo.completionPercentage = total > 0 ? ((tableInfo.processed / total) * 100).toFixed(1) : 100;
    this.tableProgress.set(tableName, tableInfo);

    this.totalProcessedRecords += processed;
    this.sendProgressUpdate();
  }

  setTableEstimate(tableName, estimated) {
    this.tableProgress.set(tableName, { processed: 0, total: estimated, completionPercentage: 0 });
    this.totalEstimatedRecords += estimated;
  }

  sendProgressUpdate() {
    if (parentPort) {
      parentPort.postMessage({
        type: 'progress',
        phase: this.currentPhase,
        progress: this.getOverallProgress()
      });
    }
  }

  getOverallProgress() {
    const elapsedSeconds = (new Date() - this.startTime) / 1000;
    const recordsPerSecond = this.totalProcessedRecords / elapsedSeconds;

    const overallCompletion = this.totalEstimatedRecords > 0 ?
      ((this.totalProcessedRecords / this.totalEstimatedRecords) * 100).toFixed(1) : 0;

    return {
      currentPhase: this.currentPhase,
      totalProcessed: this.totalProcessedRecords,
      totalEstimated: this.totalEstimatedRecords,
      overallCompletion: parseFloat(overallCompletion),
      executionTimeSeconds: elapsedSeconds.toFixed(2),
      averageRecordsPerSecond: Math.round(recordsPerSecond),
      errorCount: this.errors.length,
      tables: Object.fromEntries(this.tableProgress)
    };
  }

  addError(error, context = '') {
    this.errors.push({
      message: error.message,
      context: context,
      timestamp: new Date().toISOString()
    });
  }
}

// Database Connection Manager
class DatabaseConnection {
  constructor(dbConfig, name) {
    this.dbConfig = dbConfig;
    this.name = name;
    this.sequelize = null;
    this.connected = false;
  }

  async connect() {
    try {
      this.sequelize = new Sequelize({
        ...this.dbConfig,
        pool: {
          max: 10,
          min: 2,
          acquire: workerData.config.connectionTimeout,
          idle: 10000,
        },
        logging: false,
        retry: { max: 3 },
        dialectOptions: {
          connectTimeout: workerData.config.connectionTimeout,
          dateStrings: true,
          typeCast: function (field, next) {
            if (field.type === 'DATETIME' || field.type === 'TIMESTAMP') {
              return field.string();
            }
            return next();
          },
        },
      });

      await this.sequelize.authenticate();
      this.connected = true;
      console.log(`Connected to ${this.name} database`);
      return this.sequelize;
    } catch (error) {
      console.error(`Failed to connect to ${this.name} database:`, error.message);
      throw error;
    }
  }

  async disconnect() {
    if (this.sequelize && this.connected) {
      await this.sequelize.close();
      this.connected = false;
      console.log(`Disconnected from ${this.name} database`);
    }
  }

  getConnection() {
    if (!this.connected || !this.sequelize) {
      throw new Error(`Database ${this.name} not connected`);
    }
    return this.sequelize;
  }
}

class IDCollectionManager {
    constructor(dbConnection, config, progressTracker) {
        this.db = dbConnection;
        this.config = config;
        this.progress = progressTracker;
        this.collectedIds = new Map();
    }

    groupTablesByLevel(tableConfigs) {
        const tablesByLevel = {};
        const tableMap = new Map(tableConfigs.map(t => [t.table, t]));

        for (const tableConfig of tableConfigs) {
            const level = this.calculateHierarchyLevel(tableConfig, tableMap);
            if (!tablesByLevel[level]) {
                tablesByLevel[level] = [];
            }
            tablesByLevel[level].push({ ...tableConfig, hierarchyLevel: level });
        }
        return tablesByLevel;
    }

    calculateHierarchyLevel(tableConfig, tableMap, visited = new Set()) {
        if (visited.has(tableConfig.table)) return 0;
        if (!tableConfig.linkTo || !tableConfig.fk) return 0;

        visited.add(tableConfig.table);
        const parentTable = tableMap.get(tableConfig.linkTo);
        if (!parentTable) return 1;

        return this.calculateHierarchyLevel(parentTable, tableMap, visited) + 1;
    }

    formatDateForMySQL(date) {
        if (!date) return null;
        return new Date(date).toISOString().slice(0, 19).replace('T', ' ');
    }

    async collectAllHierarchicalIds(tableConfigs, startDate, endDate) {
        console.log(`Starting hierarchical ID collection for ${tableConfigs.length} tables`);
        const tablesByLevel = this.groupTablesByLevel(tableConfigs);
        const maxLevel = Math.max(...Object.keys(tablesByLevel).map(l => parseInt(l)));

        for (let level = 0; level <= maxLevel; level++) {
            const levelTables = tablesByLevel[level] || [];
            if (levelTables.length === 0) continue;

            const promises = levelTables.map(async (table) => {
                let ids = [];
                if (level === 0) {
                    ids = await this.collectRootTableIds(table, startDate, endDate);
                } else {
                    const parentIds = this.collectedIds.get(table.linkTo);
                    if (parentIds && parentIds.size > 0) {
                        ids = await this.collectChildTableIds(table, Array.from(parentIds));
                    }
                }
                this.collectedIds.set(table.table, new Set(ids));
                this.progress.setTableEstimate(table.table, ids.length);
                console.log(`Collected ${ids.length.toLocaleString()} IDs from ${table.table} (Level ${level})`);
            });
            await Promise.all(promises);
        }
        return this.collectedIds;
    }

    async collectRootTableIds(tableConfig, startDate, endDate) {
        const { table, dateColumn, pk } = tableConfig;
        const primaryKey = Array.isArray(pk) ? pk[0] : pk;
        const formattedStartDate = this.formatDateForMySQL(startDate);
        const formattedEndDate = endDate ? this.formatDateForMySQL(endDate) : null;
        let allIds = [];

        try {
            let whereClause;
            const replacements = [];
            if (formattedEndDate) {
                whereClause = `\`${dateColumn}\` >= ? AND \`${dateColumn}\` < ?`;
                replacements.push(formattedStartDate, formattedEndDate);
            } else {
                whereClause = `\`${dateColumn}\` <= ?`;
                replacements.push(formattedStartDate);
            }

            // Using safe parameterized query with proper identifier escaping
            const query = `SELECT \`${primaryKey}\` FROM \`${table}\` WHERE ${whereClause}`;
            const [results] = await this.db.query(query, { replacements });
            allIds = results.map(r => r[primaryKey]);
        } catch (error) {
            console.error(`Failed to collect root IDs from ${table}:`, error.message);
            this.progress.addError(error, `ID collection: ${table}`);
        }
        return allIds;
    }

    async collectChildTableIds(tableConfig, parentIds) {
        const { table, fk, pk } = tableConfig;
        const primaryKey = Array.isArray(pk) ? pk[0] : pk;
        let allChildIds = [];
        if (!parentIds || parentIds.length === 0) return [];

        try {
            const chunkSize = this.config.chunkSize;
            for (let i = 0; i < parentIds.length; i += chunkSize) {
                const chunk = parentIds.slice(i, i + chunkSize);
                // Using safe parameterized query with proper identifier escaping
                const query = `SELECT \`${primaryKey}\` FROM \`${table}\` WHERE \`${fk}\` IN (?)`;
                const [results] = await this.db.query(query, { replacements: [chunk] });
                allChildIds.push(...results.map(r => r[primaryKey]));
            }
        } catch (error) {
            console.error(`Failed to collect child IDs from ${table}:`, error.message);
            this.progress.addError(error, `Child ID collection: ${table}`);
        }
        return allChildIds;
    }
}

// Helper function to safely execute SELECT query for existing records
const safeSelectExistingRecords = async (db, table, pkField, pkValues, transaction) => {
  // Using parameterized query with proper escaping - fix identifier placement
  const query = `SELECT \`${pkField}\` FROM \`${table}\` WHERE \`${pkField}\` IN (?)`;
  const [existingRows] = await db.query(query, {
    replacements: [pkValues],
    transaction
  });
  return existingRows;
};

// Helper function to safely execute SELECT query for records to archive
const safeSelectRecordsToArchive = async (db, table, pkField, idChunk, transaction) => {
  // Using parameterized query with proper escaping - fix identifier placement
  const query = `SELECT * FROM \`${table}\` WHERE \`${pkField}\` IN (?)`;
  const [records] = await db.query(query, {
    replacements: [idChunk],
    transaction
  });
  return records;
};

// Helper function to safely execute INSERT query
const safeInsertRecords = async (db, table, columns, values, transaction) => {
  if (values.length === 0) return;

  // Build safe INSERT query with proper identifier escaping
  const escapedTable = `\`${table}\``;
  const escapedColumns = columns.map(col => `\`${col}\``).join(', ');
  const valuePlaceholders = values.map(() => `(${columns.map(() => '?').join(', ')})`).join(', ');

  const query = `INSERT INTO ${escapedTable} (${escapedColumns}) VALUES ${valuePlaceholders}`;

  // Flatten the values array for replacements
  const flattenedValues = [];
  for (const valueRow of values) {
    flattenedValues.push(...valueRow);
  }

  await db.query(query, {
    replacements: flattenedValues,
    transaction
  });
};

class TopDownArchivalEngine {
    constructor(primaryDb, archiveDb, config, progress, idManager) {
        this.primaryDb = primaryDb;
        this.archiveDb = archiveDb;
        this.config = config;
        this.progress = progress;
        this.idManager = idManager;
    }

    async executeHierarchicalArchival(tableConfigs, primaryTransaction, archiveTransaction) {
        console.log(`Starting top-down hierarchical archival within a single transaction.`);
        const tablesByLevel = this.idManager.groupTablesByLevel(tableConfigs);
        const levels = Object.keys(tablesByLevel).map(l => parseInt(l)).sort((a, b) => a - b);
        let totalArchived = 0;
        const results = {};

        for (const level of levels) {
            const levelTables = tablesByLevel[level];
            console.log(`\n Processing Level ${level}: ${levelTables.length} tables`);

            // At each level, process tables sequentially to avoid overwhelming the transaction
            for (const tableConfig of levelTables) {
                const idsToArchive = Array.from(this.idManager.collectedIds.get(tableConfig.table) || []);
                if (idsToArchive.length === 0) {
                    console.log(`Skipping ${tableConfig.table} - no records to archive.`);
                    results[tableConfig.table] = { archived: 0, skipped: [], status: 'success' };
                    continue;
                }

                const { archived, skipped } = await this.archiveTableInChunks(tableConfig, idsToArchive, primaryTransaction, archiveTransaction);
                totalArchived += archived;
                results[tableConfig.table] = { archived, skipped, status: 'success' };
                console.log(`${tableConfig.table}: ${archived.toLocaleString()} records archived, ${skipped.length.toLocaleString()} skipped.`);
            }
        }

        console.log(`\nHierarchical archival completed: ${totalArchived.toLocaleString()} total records archived.`);
        return { totalArchived, results };
    }

    // Refactored archiveTableInChunks to use safe SQL execution
    async archiveTableInChunks(tableConfig, ids, primaryTransaction, archiveTransaction) {
        let archivedCount = 0;
        let allSkippedIds = [];
        const idChunkSize = this.config.chunkSize;
        const pkField = Array.isArray(tableConfig.pk) ? tableConfig.pk[0] : tableConfig.pk;

        console.log(`   Archiving ${ids.length.toLocaleString()} records for ${tableConfig.table} in chunks of ${idChunkSize}...`);

        // Process IDs in chunks to avoid memory issues and ensure transaction safety
        for (let chunkStart = 0; chunkStart < ids.length; chunkStart += idChunkSize) {
            const idChunk = ids.slice(chunkStart, chunkStart + idChunkSize);

            // Safely select records to archive using parameterized query
            const records = await safeSelectRecordsToArchive(
                this.primaryDb,
                tableConfig.table,
                pkField,
                idChunk,
                primaryTransaction
            );

            if (records.length > 0) {
                const { inserted, skippedIds } = await this.batchInsert(tableConfig, records, archiveTransaction);
                archivedCount += inserted;
                if (skippedIds && skippedIds.length > 0) {
                    allSkippedIds.push(...skippedIds);
                }
            }
            this.progress.updateTableProgress(tableConfig.table, idChunk.length, ids.length);
        }
        return { archived: archivedCount, skipped: allSkippedIds };
    }

    // Refactored batchInsert to use safe SQL execution
    async batchInsert(tableConfig, records, transaction) {
        if (records.length === 0) return { inserted: 0, skippedIds: [] };

        const { table, pk } = tableConfig;
        const pkField = Array.isArray(pk) ? pk[0] : pk;
        const pkValues = records.map(r => r[pkField]);

        // Safely check for existing records using parameterized query
        const existingRows = await safeSelectExistingRecords(
            this.archiveDb,
            table,
            pkField,
            pkValues,
            transaction
        );

        const existingSet = new Set(existingRows.map(row => row[pkField]));

        const filteredRecords = records.filter(r => !existingSet.has(r[pkField]));
        const skippedIds = records.filter(r => existingSet.has(r[pkField])).map(r => r[pkField]);

        // Safely insert new records using parameterized query
        if (filteredRecords.length > 0) {
            const columns = Object.keys(filteredRecords[0]);
            const values = filteredRecords.map(rec => columns.map(col => rec[col]));

            await safeInsertRecords(this.archiveDb, table, columns, values, transaction);
        }

        return { inserted: filteredRecords.length, skippedIds };
    }
}

class ArchiveWorker {
    constructor(workerData) {
        this.workerData = workerData;
        this.config = workerData.config;
        this.progress = new HierarchicalProgressTracker();

        this.primaryDb = null;
        this.archiveDb = null;
        this.idManager = null;
        this.archivalEngine = null;

        if (parentPort) {
            parentPort.on('message', (msg) => {
              if (msg.type === 'shutdown') process.exit(0);
            });
        }
    }

    async initialize() {
        try {
            console.log(`Initializing Hierarchical Archive Worker`);
            this.primaryDb = new DatabaseConnection(this.workerData.primaryDbConfig, 'PRIMARY');
            this.archiveDb = new DatabaseConnection(this.workerData.archiveDbConfig, 'ARCHIVE');

            await this.primaryDb.connect();
            await this.archiveDb.connect();

            this.idManager = new IDCollectionManager(this.primaryDb.getConnection(), this.config, this.progress);
            this.archivalEngine = new TopDownArchivalEngine(
                this.primaryDb.getConnection(),
                this.archiveDb.getConnection(),
                this.config,
                this.progress,
                this.idManager
            );
            console.log(`Hierarchical Archive Worker initialized successfully`);
        } catch (error) {
            console.error(`Failed to initialize Archive Worker:`, error.message);
            this.progress.addError(error, 'initialization');
            throw error;
        }
    }

    async validateSchemas() {
      if (this.config.skipSchemaValidation) {
          console.log('Skipping schema validation as requested.');
          return { skipped: true };
      }

      this.progress.startPhase('validation');
      console.log('Starting schema validation for all tables...');
      const schemaValidations = {};
      const tableConfigs = this.config.archiveConfig || [];

      for (const tableConfig of tableConfigs) {
          const validation = await validateTableSchema(
              tableConfig.table,
              this.primaryDb.getConnection(),
              this.archiveDb.getConnection()
          );
          schemaValidations[tableConfig.table] = validation;
          if (!validation.isValid) {
              const error = new Error(`Schema validation failed for ${tableConfig.table}: ${validation.reason}`);
              this.progress.addError(error, `Validation: ${tableConfig.table}`);
              throw error;
          }
      }

      this.progress.completePhase('validation');
      console.log('Schema validation completed successfully.');
      return schemaValidations;
    }

    async execute() {
        let primaryTransaction;
        let archiveTransaction;

        try {
            const { config, startDate, endDate } = this.workerData;

            const schemaValidations = await this.validateSchemas();

            this.progress.startPhase('idCollection');
            await this.idManager.collectAllHierarchicalIds(config.archiveConfig, startDate, endDate);
            this.progress.completePhase('idCollection');

            primaryTransaction = await this.primaryDb.getConnection().transaction({
                isolationLevel: Sequelize.Transaction.ISOLATION_LEVELS.READ_COMMITTED
            });
            archiveTransaction = await this.archiveDb.getConnection().transaction({
                isolationLevel: Sequelize.Transaction.ISOLATION_LEVELS.READ_COMMITTED
            });
            console.log('Single transaction started for archival phase.');

            this.progress.startPhase('archival');
            const { totalArchived, results } = await this.archivalEngine.executeHierarchicalArchival(
                config.archiveConfig,
                primaryTransaction,
                archiveTransaction
            );
            this.progress.completePhase('archival');

            await archiveTransaction.commit();
            console.log('Archival transaction committed successfully.');
            await primaryTransaction.rollback();

            return {
                success: true,
                totalRecordsArchived: totalArchived,
                results: results,
                performanceStats: this.progress.getOverallProgress(),
                schemaValidations,
            };
        } catch (error) {
            console.error(`Archive Worker execution failed:`, error.message);
            this.progress.addError(error, 'execution');

            // If an error occurred, roll back the transaction.
            if (archiveTransaction) {
                try {
                    await archiveTransaction.rollback();
                    console.log('Archival transaction rolled back successfully due to error.');
                } catch (rollbackError) {
                    console.error('Rollback failed:', rollbackError.message);
                }
            }
            if (primaryTransaction) {
                await primaryTransaction.rollback();
            }

            return {
                success: false,
                error: error.message,
                performanceStats: this.progress.getOverallProgress()
            };
        }
    }

    async cleanup() {
        try {
            console.log(`Cleaning up Archive Worker...`);
            if (this.primaryDb) await this.primaryDb.disconnect();
            if (this.archiveDb) await this.archiveDb.disconnect();
            console.log(`Archive Worker cleanup completed`);
        } catch (error) {
            console.error(`Error during cleanup:`, error.message);
        }
    }
}

if (!isMainThread && workerData) {
    (async () => {
        const worker = new ArchiveWorker(workerData);
        let result;
        try {
            await worker.initialize();
            result = await worker.execute();
        } catch (error) {
            result = { success: false, error: error.message };
        } finally {
            if (parentPort) {
                parentPort.postMessage({ type: 'result', result });
            }
            await worker.cleanup();
            process.exit(result.success ? 0 : 1);
        }
    })();
}

const createArchiveWorker = (workerConfig) => {
    const onProgress = workerConfig.onProgress;
    const workerDataForThread = { ...workerConfig };
    delete workerDataForThread.onProgress;

    return new Promise((resolve, reject) => {
        const worker = new Worker(__filename, {
            workerData: workerDataForThread,
            resourceLimits: { maxOldGenerationSizeMb: 2048, maxYoungGenerationSizeMb: 1024 }
        });

        const timeout = setTimeout(() => {
            worker.terminate();
            reject(new Error('Worker timeout after 1 hour'));
        }, 3600000);

        worker.on('message', (message) => {
            if (message.type === 'result') {
                clearTimeout(timeout);
                if (message.result.success) {
                    resolve(message.result);
                } else {
                    reject(new Error(message.result.error || 'Worker execution failed'));
                }
            } else if (message.type === 'progress' && onProgress) {
                onProgress(message.progress);
            }
        });

        worker.on('error', (error) => {
            clearTimeout(timeout);
            reject(error);
        });

        worker.on('exit', (code) => {
            clearTimeout(timeout);
            if (code !== 0) {
                reject(new Error(`Worker stopped with exit code ${code}`));
            }
        });
    });
};

module.exports = { createArchiveWorker };