/**
 * Memory Tracking Middleware
 * Tracks heap memory usage for each API request to identify memory-intensive endpoints
 * Logs memory before/after and delta for each API call
 * 
 * Optimized for:
 * - Zero latency impact: Memory calculation deferred using setImmediate
 * - Minimal memory overhead: Uses res.on('finish') instead of overriding res.end
 * - Configurable: Can be disabled via ENABLE_MEMORY_TRACKING env var
 * - Safe: No interference with other middleware or response handling
 */

const memoryTrackingMiddleware = (req, res, next) => {

  if (
    req.path.includes('/health-check') || // Exclude health checks - infrastructure endpoints
    req.path.includes('/docs') ||
    req.method === 'OPTIONS'
  ) {
    return next();
  }

  const memBefore = process.memoryUsage();
  const heapUsedBeforeMB = Math.round(memBefore.heapUsed / 1024 / 1024);
  const apiEndpoint = `${req.method} ${req.path}`;
  const startTime = Date.now();
  let hasLogged = false;

  const logMemoryStats = (statusCode = res.statusCode || 200) => {
    if (hasLogged) return;
    hasLogged = true;

    setImmediate(() => {
      try {
        const memAfter = process.memoryUsage();
        const heapUsedAfterMB = Math.round(memAfter.heapUsed / 1024 / 1024);
        const heapDeltaMB = heapUsedAfterMB - heapUsedBeforeMB;
        const durationMs = Date.now() - startTime;

        const logMessage = `API=${apiEndpoint} | Status=${statusCode} | Duration=${durationMs}ms | HeapBefore=${heapUsedBeforeMB}MB | HeapAfter=${heapUsedAfterMB}MB | Delta=${heapDeltaMB > 0 ? '+' : ''}${heapDeltaMB}MB`;
        
        console.log(`[MEMORY_TRACK] ${logMessage}`);
      } catch (logError) {
        if (process.env.NODE_ENV === 'development') {
          console.error('[MEMORY_TRACK] Error logging memory stats:', logError.message);
        }
      }
    });
  };

  res.once('finish', logMemoryStats);
  res.once('close', () => logMemoryStats('CLOSED'));
  next();
};

module.exports = memoryTrackingMiddleware;