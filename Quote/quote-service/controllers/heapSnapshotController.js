const v8 = require('v8');
const fs = require('fs');
const path = require('path');
const { logError } = require('../helpers/utils');

/**
 * Captures a heap snapshot and saves it to disk
 * @param {string} label - Optional label for the snapshot filename
 * @returns {string|null} - Path to the saved snapshot file, or null on error
 */
const takeHeapSnapshot = (label = 'snapshot') => {
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-').replace('T', '_').split('.')[0];
  const filename = `heap-${label}-${timestamp}.heapsnapshot`;
  
  // Use heap-snapshots directory created by Docker
  const snapshotsDir = path.join(__dirname, '../heap-snapshots');
  
  // Verify directory exists
  if (!fs.existsSync(snapshotsDir)) {
    throw new Error(`Heap snapshot directory does not exist: ${snapshotsDir}. Please ensure Docker creates this directory with proper permissions.`);
  }
  
  // Verify write permissions
  try {
    fs.accessSync(snapshotsDir, fs.constants.W_OK);
  } catch (error) {
    throw new Error(`No write permission for heap snapshot directory: ${snapshotsDir}. Error: ${error.message}. Please check Dockerfile permissions.`);
  }
  
  const filepath = path.join(snapshotsDir, filename);
  const snapshotPath = v8.writeHeapSnapshot(filepath);
  
  console.log(`[HeapSnapshot] Snapshot saved: ${snapshotPath}`);
  return snapshotPath;
};

/**
 * API endpoint handler for capturing heap snapshot
 * Public endpoint - no authentication required
 */
exports.captureHeapSnapshot = async (req, res) => {
  const LOG_PREFIX = '[HeapSnapshot-API]';
  const startTime = Date.now();
  
  try {
    // Get optional label from query parameter or request body
    const label = req.query?.label || req.body?.label || 'manual';
    
    console.log(`${LOG_PREFIX} Capturing heap snapshot with label: ${label}`);
    
    // Get current memory stats before snapshot
    const memBefore = process.memoryUsage();
    const heapUsedBeforeMB = Math.round(memBefore.heapUsed / 1024 / 1024);
    const heapTotalBeforeMB = Math.round(memBefore.heapTotal / 1024 / 1024);
    const rssBeforeMB = Math.round(memBefore.rss / 1024 / 1024);
    
    // Capture heap snapshot
    const snapshotPath = takeHeapSnapshot(label);
    
    if (!snapshotPath) {
      throw new Error('Failed to capture heap snapshot');
    }
    
    // Get memory stats after snapshot
    const memAfter = process.memoryUsage();
    const heapUsedAfterMB = Math.round(memAfter.heapUsed / 1024 / 1024);
    const heapTotalAfterMB = Math.round(memAfter.heapTotal / 1024 / 1024);
    const rssAfterMB = Math.round(memAfter.rss / 1024 / 1024);
    
    const duration = Date.now() - startTime;
    
    // Get file size
    const stats = fs.statSync(snapshotPath);
    const fileSizeMB = Math.round((stats.size / 1024 / 1024) * 100) / 100;
    
    console.log(`${LOG_PREFIX} Snapshot captured successfully in ${duration}ms`);
    console.log(`${LOG_PREFIX} File: ${snapshotPath}, Size: ${fileSizeMB}MB`);
    console.log(`${LOG_PREFIX} Memory - HeapUsed: ${heapUsedBeforeMB}MB -> ${heapUsedAfterMB}MB, RSS: ${rssBeforeMB}MB -> ${rssAfterMB}MB`);
    
    // Extract relative path for response
    const relativePath = path.relative(process.cwd(), snapshotPath);
    
    res.status(200).json({
      status: true,
      statusCode: 200,
      statusMsg: 'Heap snapshot captured successfully',
      response: {
        snapshotPath: relativePath,
        absolutePath: snapshotPath,
        filename: path.basename(snapshotPath),
        fileSizeMB: fileSizeMB,
        duration: `${duration}ms`,
        memory: {
          before: {
            heapUsedMB: heapUsedBeforeMB,
            heapTotalMB: heapTotalBeforeMB,
            rssMB: rssBeforeMB
          },
          after: {
            heapUsedMB: heapUsedAfterMB,
            heapTotalMB: heapTotalAfterMB,
            rssMB: rssAfterMB
          }
        },
        timestamp: new Date().toISOString()
      }
    });
  } catch (error) {
    const duration = Date.now() - startTime;
    console.error(`${LOG_PREFIX} Error capturing heap snapshot after ${duration}ms:`, error);
    logError(error, 'Error in captureHeapSnapshot API');
    
    res.status(500).json({
      status: false,
      statusCode: 500,
      statusMsg: error.message || 'Failed to capture heap snapshot',
      error: process.env.NODE_ENV === 'development' ? error.stack : undefined
    });
  }
};

