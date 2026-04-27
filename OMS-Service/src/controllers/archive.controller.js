const catchAsync = require('../utils/catchAsync');
const os = require('os');
const { sequelize: primaryDb } = require('../models/seqModels');
const { archiveSquelize: archiveDb } = require('../models/seqModels/archiveIndex');
const archiveConfig = require('../../config/config.js')["archive"];
const env = process.env.NODE_ENV || 'development';
const primaryConfig = require('../../config/config.js')[env];
const { createArchiveWorker } = require('../helpers/archiveWorker.js');
const crypto = require('crypto');

const getOperationConfig = (operationType, userConfig = {}) => {
  const baseConfig = {
    batchSize: 50000, 
    chunkSize: 5000, 
    deleteAfterArchive: true,
    skipSchemaValidation: false,
    maxRetries: 3,
    retryDelay: 1000,
    progressUpdateInterval: 5000,
    connectionTimeout: 900000,
    commitEveryNBatches: 20,
    batchDelay: 100
  };

  const operationConfigs = {
    archive: {
      ...baseConfig,
      maxParallelTables: Math.min(3, os.cpus().length),
      maxParallelChildTables: Math.min(3, os.cpus().length),
      maxDeleteBatches: 5000
    },
    delete: {
      ...baseConfig,
      maxParallelWorkers: Math.min(3, os.cpus().length),
      maxTablesPerWorker: 3,
      workerMemoryLimit: '4GB',
      enableDetailedLogging: true,
      queryTimeout: 1800000,
      transactionTimeout: 3600000,
      maxRetryAttempts: 5,
      retryBackoffMs: 2000
    }
  };

  const config = operationConfigs[operationType] || operationConfigs.archive;
  return { ...config, ...userConfig };
};

const getCurrentDate = () => new Date();

const generateJobId = () => `arch_${Date.now()}_${crypto.randomBytes(3).toString('hex')}`;

const calculateDateRange = (config) => {
  if (config.startDate && (config.endDate || config.endDate === null)) {
    const [startYear, startMonth, startDay] = config.startDate.split('-').map(Number);
    let startDate = new Date(Date.UTC(startYear, startMonth - 1, startDay, 0, 0, 0, 0));
    
    let endDate = null;
    if (config.endDate) {
      const [endYear, endMonth, endDay] = config.endDate.split('-').map(Number);
      endDate = new Date(Date.UTC(endYear, endMonth - 1, endDay, 0, 0, 0, 0));
    }
    
    return { startDate, endDate };
  }
};

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

const setupBodyConfig = (reqBody, confiData, primaryDbDays) => {
  let body = {...reqBody, ...confiData, archiveConfig: confiData?.archiveTableConfig};
  
  if (!body.startDate) {
    const startDate = new Date(new Date().setDate(new Date().getDate() - primaryDbDays));
    const formattedStartDate = startDate.getFullYear() + '-' + 
      String(startDate.getMonth() + 1).padStart(2, '0') + '-' + 
      String(startDate.getDate()).padStart(2, '0');
    body.endDate = formattedStartDate;
    
    const endDate = new Date(startDate);
    endDate.setDate(endDate.getDate() - 10);
    const formattedEndDate = endDate.getFullYear() + '-' + 
      String(endDate.getMonth() + 1).padStart(2, '0') + '-' + 
      String(endDate.getDate()).padStart(2, '0');
    body.startDate = formattedEndDate;
  }
  
  return body;
};

const handleDeleteOperation = async (config, dateRange, req, jobId) => {
  if (!config.deleteAfterArchive) {
    return null;
  }

  console.log(`[JOB ${jobId || 'n/a'}] Triggering delete operation after successful archival...`);
  const axios = require('axios');
  const deleteBody = {
    archive: false,
    startDate: dateRange.startDate ? dateRange.startDate.toISOString() : undefined,
    endDate: dateRange.endDate ? dateRange.endDate.toISOString() : undefined,
    jobId: jobId || undefined
  };
  
  try {
    const baseUrl = `${process.env.OMS_API_HOST}`;
    const apiUrl = `${baseUrl}/v1/archive/archive-setup`; 
    const { authorization, 'authorization-token': internalAuthToken } = req.headers;
    const deleteResponse = await axios.post(apiUrl, deleteBody, {
      headers: {
        'Content-Type': 'application/json',
        ...(authorization && { authorization }),
        ...(internalAuthToken && { 'authorization-token': internalAuthToken }),
        ...(jobId && { 'x-archival-job-id': jobId })
      },
      timeout: 3600000 
    });
    console.log(`[JOB ${jobId || 'n/a'}] Delete API accepted. Background delete worker will run.`);
    return deleteResponse.data;
  } catch (deleteErr) {
    console.error(`[JOB ${jobId || 'n/a'}] Delete operation trigger failed:`, deleteErr);
    return {
      status: 'error',
      message: 'Delete API call failed',
      error: deleteErr.message
    };
  }
};

const archiveOrders = catchAsync(async (req, res) => {
  const startTime = getCurrentDate();
  const confiData = global?.baseConfig?.archive_config;
  const primaryDbDays = confiData?.primaryDbDays;

  const body = setupBodyConfig(req.body, confiData, primaryDbDays);
  const config = { ...getOperationConfig('archive', body) };
  const dateRange = calculateDateRange(config);
  const jobId = generateJobId();
  console.log(`[JOB ${jobId}] config:`, JSON.stringify(config));
  if (dateRange?.startDate && dateRange?.endDate) {
    const diffMs = dateRange.endDate - dateRange.startDate;
    const diffDays = Math.round(diffMs / (1000 * 60 * 60 * 24));
    console.log(`[JOB ${jobId}] Archiving data from ${dateRange.startDate.toISOString()} to ${dateRange.endDate.toISOString()} (${diffDays} days)`);
  } else if (primaryDbDays !== undefined) {
    console.log(`[JOB ${jobId}] Archiving all data older than ${primaryDbDays} days`);
  } else {
    console.log(`[JOB ${jobId}] Archiving all data (no date range specified)`);
  }

  const { authorization, 'authorization-token': internalAuthToken } = req.headers || {};

  setImmediate(async () => {
    try {
      const workerConfig = {
        config,
        startDate: dateRange?.startDate ? dateRange.startDate.toISOString() : undefined,
        endDate: dateRange?.endDate ? dateRange.endDate.toISOString() : undefined,
        primaryDbConfig: primaryConfig,
        archiveDbConfig: archiveConfig,
        onProgress: (progress) => {
          console.log(`[JOB ${jobId}] [PROGRESS] ${progress.currentPhase} - ${progress.overallCompletion}%`);
        }
      };

      console.log(`[JOB ${jobId}] Starting archival worker...`);
      const workerResult = await createArchiveWorker(workerConfig);
      console.log(`[JOB ${jobId}] Archival worker completed successfully.`);

      const deleteResult = await handleDeleteOperation(config, dateRange, { headers: { authorization, 'authorization-token': internalAuthToken } }, jobId);

      console.log(`[JOB ${jobId}] Archival job completed`, {
        startTime: startTime.toISOString(),
        endTime: getCurrentDate().toISOString(),
        totalExecutionTimeSeconds: Math.round((new Date() - startTime) / 1000),
        deleteStatus: deleteResult?.status || 'unknown'
      });

      const finalArchiveOrdersResponse = {
        status: 'success',
        data: {
          metadata: {
            startTime: startTime.toISOString(),
            endTime: getCurrentDate().toISOString(),
            totalExecutionTime: `${(new Date() - startTime) / 1000} seconds`,
            config,
            jobId
          },
          schemaValidations: workerResult.schemaValidations,
          results: workerResult.results,
          performance: workerResult.performanceStats,
          deleteResult
        }
      };
      console.log(`[JOB ${jobId}] Final archive-orders result:`, JSON.stringify(finalArchiveOrdersResponse));
    } catch (error) {
      console.error(`[JOB ${jobId}] Archival process failed:`, error);
    }
  });

  res.set('x-archival-job-id', jobId);
  return res.status(200).json({
    status: 'accepted',
    jobId,
    message: 'Archival started in background',
    metadata: {
      startTime: startTime.toISOString(),
      dateRange: {
        startDate: dateRange?.startDate ? dateRange.startDate.toISOString() : null,
        endDate: dateRange?.endDate ? dateRange.endDate.toISOString() : null
      }
    }
  });
});

const validateArchiveFlag = (archiveFlag) => {
  return typeof archiveFlag === 'boolean';
};

const logDateInformation = (startDate, endDate, cutoffDate) => {  
  if (startDate && endDate) {
    const formattedStart = new Date(startDate).toISOString();
    const formattedEnd = new Date(endDate).toISOString();
    console.log(`   Start date: ${formattedStart}`);
    console.log(`   End date:   ${formattedEnd}`);
  } else if (startDate) {
    const formattedStart = new Date(startDate).toISOString();
    console.log(`   Start date: ${formattedStart}`);
  } else {
    const formattedCutoff = cutoffDate.toISOString();
    console.log(`   Cutoff date: ${formattedCutoff}`);
  }
};

const processHierarchicalDeletion = async (tableSizeAnalysis, dateParams, dbConfig, archiveFlag) => {
  const { createDeleteWorker } = require('../helpers/deleteWorker');
  const { startDate, endDate, cutoffDate, condition, config } = dateParams;
  const { primaryConfig, archiveConfig } = dbConfig;
  
  const hierarchicalWorkerConfig = {
    tables: tableSizeAnalysis.tables,
    startDate: startDate ? new Date(startDate).toISOString() : undefined,
    endDate: endDate ? new Date(endDate).toISOString() : undefined,
    cutoffDate: cutoffDate.toISOString(),
    condition: condition,
    config: config,
    primaryDbConfig: primaryConfig,
    archiveDbConfig: archiveConfig,
    useArchiveDb: archiveFlag
  };
  
  console.log(`Starting hierarchical deletion with real-time progress tracking...`);
  return await createDeleteWorker(hierarchicalWorkerConfig);
};

const processDeletionResults = (deletionResult, tableSizeAnalysis, results, deletionSummary) => {
  if (!deletionResult.success) {
    throw new Error(`Hierarchical deletion failed: ${deletionResult.error}`);
  }

  deletionSummary.totalRecordsDeleted = deletionResult.totalRecordsDeleted;
  deletionSummary.totalTablesProcessed = Object.keys(deletionResult.results).length;
  
  Object.entries(deletionResult.results).forEach(([tableName, tableResult]) => {
    results[tableName] = {
      recordsDeleted: tableResult.recordsDeleted,
      status: tableResult.success ? 'success' : 'error',
      ...(tableResult.error && { error: tableResult.error }),
      hierarchyLevel: tableResult.hierarchyLevel || 0
    };
    
    if (tableResult.success && tableResult.recordsDeleted > 0) {
      deletionSummary.tablesAffected.push(tableName);
    }
    
    if (!tableResult.success) {
      deletionSummary.errors.push({
        table: tableName,
        error: tableResult.error || 'Unknown error',
        type: 'hierarchical_deletion_error'
      });
    }
  });
  
  // ENSURE ALL TABLES ARE INCLUDED: Add any missing tables with 0 deletions
  tableSizeAnalysis.tables.forEach(tableConfig => {
    if (!results[tableConfig.table]) {
      results[tableConfig.table] = {
        recordsDeleted: 0,
        status: 'success',
        hierarchyLevel: tableConfig.hierarchyLevel || 0
      };
    }
  });
  
  // Update performance metrics
  deletionSummary.totalTablesProcessed = tableSizeAnalysis.tables.length;
  deletionSummary.performanceStats.workersUsed = 1;
  deletionSummary.performanceStats.hierarchicalDeletion = true;
  deletionSummary.performanceStats.idCollectionSummary = deletionResult.idCollectionSummary;
  deletionSummary.performanceStats.hierarchyLevelsProcessed = deletionResult.hierarchyLevelsProcessed;
  
  if (deletionResult.performanceStats) {
    deletionSummary.performanceStats.averageRecordsPerSecond = deletionResult.performanceStats.averageRecordsPerSecond;
    deletionSummary.performanceStats.peakMemoryUsageMB = deletionResult.performanceStats.peakMemoryUsageMB;
    deletionSummary.performanceStats.totalExecutionTimeSeconds = deletionResult.performanceStats.executionTimeSeconds;
    deletionSummary.performanceStats.errorCount = deletionResult.performanceStats.errorCount;
  }
};

// Helper function to build target log string
const buildTargetLogString = (targetDatabase, startDate, endDate, cutoffDate, maxParallelWorkers) => {
  let targetLog = `Target: ${targetDatabase.toUpperCase()} | `;
  
  // Fixed - Extract nested ternary operation into independent statements
  let dateRange;
  if (startDate && endDate) {
    dateRange = `Range: ${startDate} to ${endDate}`;
  } else if (startDate) {
    dateRange = `Start: ${startDate}`;
  } else {
    dateRange = `Cutoff: ${cutoffDate.toISOString().split('T')[0]}`;
  }
  
  targetLog += dateRange;
  targetLog += ` | Workers: ${maxParallelWorkers}`;
  
  return targetLog;
};

// Helper function to initialize deletion summary
const initializeDeletionSummary = () => {
  return {
    totalTablesProcessed: 0,
    totalRecordsDeleted: 0,
    tablesAffected: [],
    errors: [],
    performanceStats: {
      totalProcessingTime: 0,
      avgRecordsPerSecond: 0,
      peakMemoryUsage: 0,
      workersUsed: 0
    }
  };
};

// Helper function to handle post-processing operations
const handlePostProcessingOperations = async (dbConnection, config, tableSizeAnalysis) => {
  await optimizeMySQLForDeletion(dbConnection, null, config);
  return {};
};

// Fixed - Reduced complexity from 16 to under 15 by extracting helper functions
const archiveSetup = catchAsync(async (req, res) => {  
  const startTime = getCurrentDate();
  const { startDate, endDate, archive: archiveFlag } = req.body;
  const incomingJobId = req.headers['x-archival-job-id'] || req.body.jobId || generateJobId();
  const confiData = global?.baseConfig?.archive_config;
  const config = { ...getOperationConfig('delete', { ...confiData, archiveConfig: confiData?.archiveTableConfig}) };
  const cutoffDate = new Date(startDate);

  // Validate archive flag
  if (!validateArchiveFlag(archiveFlag)) {
    return res.status(400).json({
      status: 'error',
      message: 'Missing or invalid "archive" flag in request body. Use true for archive DB or false for primary DB.',
      example: { archive: true }
    });
  }

  const targetDatabase = archiveFlag ? 'archive' : 'primary';
  const condition = archiveFlag ? '>=' : '<';
  const dbConnection = archiveFlag ? archiveDb : primaryDb;

  // Kick off deletion in background
  setImmediate(async () => {
    const results = {};
    const deletionSummary = initializeDeletionSummary();

    try {
      logDateInformation(startDate, endDate, cutoffDate);
      console.log(`\n[JOB ${incomingJobId}] STARTING ARCHIVE CLEANUP`);
      const targetLog = buildTargetLogString(targetDatabase, startDate, endDate, cutoffDate, config.maxParallelWorkers);
      console.log(`[JOB ${incomingJobId}] ${targetLog}`);

      const tableSizeAnalysis = await analyzeTableSizes(dbConnection, config.archiveConfig, cutoffDate, condition, config);
      console.log(`[JOB ${incomingJobId}] Found ${tableSizeAnalysis.tables.length} tables, ~${tableSizeAnalysis.totalEstimatedRecords.toLocaleString()} records to process`);

      await handlePostProcessingOperations(dbConnection, config, tableSizeAnalysis);

      console.log(`[JOB ${incomingJobId}] Delete worker started`);
      const dateParams = { startDate, endDate, cutoffDate, condition, config };
      const dbConfig = { primaryConfig, archiveConfig };
      const deletionResult = await processHierarchicalDeletion(tableSizeAnalysis, dateParams, dbConfig, archiveFlag);

      processDeletionResults(deletionResult, tableSizeAnalysis, results, deletionSummary);
      console.log(`[JOB ${incomingJobId}] Delete worker completed`);
      console.log(`[JOB ${incomingJobId}] Results: ${deletionSummary.totalRecordsDeleted.toLocaleString()} records deleted from ${deletionSummary.totalTablesProcessed} tables`);

      const totalErrors = deletionSummary.errors.length;
      if (totalErrors > 0) {
        console.warn(`[JOB ${incomingJobId}] Completed with ${totalErrors} errors`);
        const errorRate = totalErrors / deletionSummary.totalTablesProcessed;
        if (errorRate > 0.5) {
          throw new Error(`High error rate (${(errorRate * 100).toFixed(1)}%) - Process failed`);
        }
      }

      const finalTime = (new Date() - startTime) / 1000;
      deletionSummary.performanceStats.totalProcessingTime = finalTime;
      deletionSummary.performanceStats.avgRecordsPerSecond = Math.round(deletionSummary.totalRecordsDeleted / finalTime);
      deletionSummary.performanceStats.peakMemoryUsage = Math.round(process.memoryUsage().heapUsed / 1024 / 1024);

      console.log(`\n[JOB ${incomingJobId}] ARCHIVE CLEANUP COMPLETED in ${finalTime.toFixed(1)}s`);

      const simplifiedResults = {};
      Object.entries(results).forEach(([tableName, result]) => {
        simplifiedResults[tableName] = {
          recordsDeleted: result.recordsDeleted || 0,
          status: result.error ? 'error' : 'success',
          ...(result.error && { error: result.error })
        };
      });

      const finalArchiveSetupResponse = {
        status: 'success',
        data: {
          jobId: incomingJobId,
          operation: {
            type: 'database_cleanup',
            targetDatabase: targetDatabase,
            cutoffDate: cutoffDate.toISOString(),
            condition: condition
          },
          summary: {
            totalRecordsDeleted: deletionSummary.totalRecordsDeleted,
            totalTablesProcessed: deletionSummary.totalTablesProcessed,
            totalErrors: deletionSummary.errors.length,
            processingTimeSeconds: parseFloat(finalTime.toFixed(1)),
            avgRecordsPerSecond: deletionSummary.performanceStats.avgRecordsPerSecond
          },
          deletions: simplifiedResults,
          errors: deletionSummary.errors.length > 0 ? deletionSummary.errors : null,
          metadata: {
            startTime: startTime.toISOString(),
            endTime: getCurrentDate().toISOString(),
            transactionCommitted: true
          }
        }
      };
      console.log(`[JOB ${incomingJobId}] Final archive-setup result:`, JSON.stringify(finalArchiveSetupResponse));
    } catch (error) {
      console.error(`[JOB ${incomingJobId}] Delete worker failed:`, error);
    }
  });

  res.set('x-archival-job-id', incomingJobId);
  return res.status(200).json({
    status: 'accepted',
    jobId: incomingJobId,
    message: 'Delete started in background',
    metadata: {
      startTime: startTime.toISOString(),
      targetDatabase: archiveFlag ? 'archive' : 'primary',
      dateRange: { startDate, endDate }
    }
  });
});

// Helper function to get record count for table analysis
const getRecordCount = async (dbConnection, tableConfig, formattedCutoffDate, condition) => {
  if (!tableConfig.dateColumn) {
    return { estimatedRecords: 0, analysisType: 'skipped' };
  }

  const countQuery = `SELECT COUNT(*) as count FROM ${tableConfig.table} WHERE ${tableConfig.dateColumn} ${condition} ?`;
  const countResult = await dbConnection.query(countQuery, {
    replacements: [formattedCutoffDate],
    type: dbConnection.QueryTypes.SELECT
  });
  
  return { 
    estimatedRecords: countResult?.[0]?.count || 0, 
    analysisType: 'direct_count' 
  };
};

// Helper function to estimate child records
const estimateChildRecords = async (dbConnection, tableConfig, archiveConfig, formattedCutoffDate, condition) => {
  if (!tableConfig.linkTo || !tableConfig.fk) {
    return { estimatedRecords: 0, analysisType: 'skipped' };
  }

  const parentConfig = archiveConfig.find(t => t.table === tableConfig.linkTo);
  if (!parentConfig?.dateColumn) {
    return { estimatedRecords: 0, analysisType: 'no_parent' };
  }

  const parentCountQuery = `SELECT COUNT(*) as count FROM ${parentConfig.table} WHERE ${parentConfig.dateColumn} ${condition} ?`;
  const parentCountResult = await dbConnection.query(parentCountQuery, {
    replacements: [formattedCutoffDate],
    type: dbConnection.QueryTypes.SELECT
  });
  
  const parentRecords = parentCountResult?.[0]?.count || 0;
  return { 
    estimatedRecords: Math.round(parentRecords * 2), 
    analysisType: 'parent_estimate' 
  };
};

const analyzeTableSizes = async (dbConnection, archiveConfig, cutoffDate, condition, config) => {
  const formattedCutoffDate = formatDateForMySQL(cutoffDate);
  const tables = [];
  let totalEstimatedRecords = 0;
  let largeTables = 0;
  let megaTables = 0;
  
  for (const tableConfig of archiveConfig) {
    try {
      let recordInfo;
      
      if (tableConfig.dateColumn) {
        recordInfo = await getRecordCount(dbConnection, tableConfig, formattedCutoffDate, condition);
      } else if (tableConfig.linkTo && tableConfig.fk) {
        recordInfo = await estimateChildRecords(dbConnection, tableConfig, archiveConfig, formattedCutoffDate, condition);
      } else {
        recordInfo = { estimatedRecords: 0, analysisType: 'skipped' };
      }
      
      const { estimatedRecords, analysisType } = recordInfo;
      
      // Categorize table size
      let sizeCategory = 'small';
      let priority = 3;
      
      if (estimatedRecords >= config.megaTableThreshold) {
        sizeCategory = 'mega';
        priority = 1;
        megaTables++;
      } else if (estimatedRecords >= config.largeTableThreshold) {
        sizeCategory = 'large';
        priority = 2;
        largeTables++;
      }
      
      const optimalBatchSize = calculateOptimalBatchSize(estimatedRecords, config);
      
      tables.push({
        ...tableConfig,
        estimatedRecords,
        sizeCategory,
        priority,
        analysisType,
        optimalBatchSize,
        processingOrder: priority
      });
      
      totalEstimatedRecords += estimatedRecords;
      
    } catch (error) {
      console.warn(`Error analyzing table ${tableConfig.table}: ${error.message}`);
      tables.push({
        ...tableConfig,
        estimatedRecords: 0,
        sizeCategory: 'unknown',
        priority: 4,
        analysisType: 'error',
        optimalBatchSize: config.minBatchSize,
        processingOrder: 4
      });
    }
  }
  
  // Sort tables by priority
  tables.sort((a, b) => a.priority - b.priority);
  
  const estimatedTimeMinutes = Math.ceil(totalEstimatedRecords / (50000 * config.maxParallelWorkers));
  
  return {
    tables,
    totalEstimatedRecords,
    largeTables,
    megaTables,
    estimatedTimeMinutes
  };
};

const calculateOptimalBatchSize = (recordCount, config) => {
  if (!config.adaptiveBatching) {
    return config.deleteBatchSize;
  }
  
  if (recordCount >= config.megaTableThreshold) {
    return Math.min(config.maxBatchSize, 50000);
  } else if (recordCount >= config.largeTableThreshold) {
    return Math.min(config.maxBatchSize, 25000);
  } else if (recordCount > 10000) {
    return Math.min(config.maxBatchSize, 10000);
  } else {
    return Math.max(config.minBatchSize, 1000);
  }
};

const optimizeMySQLForDeletion = async (dbConnection, transaction, config) => {
  try {
    const queryOptions = transaction ? { transaction } : {};
    
    await dbConnection.query('SET FOREIGN_KEY_CHECKS = 0', queryOptions);
    await dbConnection.query('SET UNIQUE_CHECKS = 0', queryOptions);
    await dbConnection.query('SET AUTOCOMMIT = 0', queryOptions);
    
    // Ultra-optimized settings for deletion mode
    await dbConnection.query('SET SESSION tmp_table_size = 64*1024*1024', queryOptions);
    await dbConnection.query('SET SESSION max_heap_table_size = 64*1024*1024', queryOptions);
    await dbConnection.query('SET SESSION sort_buffer_size = 8*1024*1024', queryOptions);
    await dbConnection.query('SET SESSION read_buffer_size = 2*1024*1024', queryOptions);
    await dbConnection.query('SET SESSION join_buffer_size = 4*1024*1024', queryOptions);
    await dbConnection.query('SET SESSION bulk_insert_buffer_size = 16*1024*1024', queryOptions);
    
    console.log(`   MySQL optimized for deletion mode`);
  } catch (error) {
    console.warn(`   Could not optimize MySQL settings: ${error.message}`);
  }
};


module.exports = {
  archiveOrders,
  archiveSetup
};