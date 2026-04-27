package org.styli.services.customer.scheduler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.styli.services.customer.component.GcpStorage;

/**
 * Daily cleanup of orphaned National ID PDFs in GCP bucket NATIONAL_ID_DOC/temp.
 * Files in temp are from document/validate that were never associated with an address (user didn't save).
 */
@Component
public class NationalIdTempCleanupScheduler {

    private static final Log LOGGER = LogFactory.getLog(NationalIdTempCleanupScheduler.class);

    @Autowired
    private GcpStorage gcpStorage;

    @Value("${national.id.temp.cleanup.enabled:false}")
    private boolean cleanupEnabled;

    /** Runs daily at 2:00 AM (server timezone). Override with national.id.temp.cleanup.cron if needed. */
    @Scheduled(cron = "${national.id.temp.cleanup.cron:0 0 2 * * ?}")
    public void cleanupNationalIdTempFiles() {
        if (!cleanupEnabled) {
            LOGGER.debug("National ID temp cleanup is disabled.");
            return;
        }
        try {
            int deleted = gcpStorage.deleteAllNationalIdTempFiles();
            if (deleted >= 0) {
                LOGGER.info("National ID temp cleanup completed. Deleted " + deleted + " file(s).");
            }
        } catch (Exception e) {
            LOGGER.error("National ID temp cleanup failed: " + e.getMessage(), e);
        }
    }
}
