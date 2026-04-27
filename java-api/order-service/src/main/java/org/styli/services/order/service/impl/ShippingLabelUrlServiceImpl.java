package org.styli.services.order.service.impl;

import com.google.common.io.ByteStreams;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.commons.lang.StringUtils;
import org.styli.services.order.component.GcpStorage;
import org.styli.services.order.config.GcsShippingLabelConfig;
import org.styli.services.order.model.rma.AmastyRmaRequest;
import org.styli.services.order.model.sales.SalesShipmentPackDetails;
import org.styli.services.order.model.sales.SalesShipmentTrack;
import org.styli.services.order.model.sales.SellerShipmentPackDetails;
import org.styli.services.order.service.ShippingLabelUrlService;
import org.styli.services.order.service.handler.ShippingLabelEntityHandler;
import org.styli.services.order.service.handler.impl.AmastyRmaRequestHandler;
import org.styli.services.order.service.handler.impl.SalesShipmentPackDetailsHandler;
import org.styli.services.order.service.handler.impl.SalesShipmentTrackHandler;
import org.styli.services.order.service.handler.impl.SellerShipmentPackDetailsHandler;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of ShippingLabelUrlService
 * 
 * This service provides a common, reusable implementation for handling shipping labels
 * across all 3 APIs (Regular Orders, Seller Orders, Return Shipments)
 * 
 * Flow:
 * 1. Download label file from carrier URL (Navik/Alpha)
 * 2. Upload to private GCS bucket
 * 3. Generate signed URL
 * 4. Update entity with signed URL (in shipping_label column) and expiry
 * 5. When URL expires, regenerate from GCS object
 */
@Service
public class ShippingLabelUrlServiceImpl implements ShippingLabelUrlService {

    private static final Log LOGGER = LogFactory.getLog(ShippingLabelUrlServiceImpl.class);
    
    private static final int CONNECTION_TIMEOUT_MS = 30000; // 30 seconds
    private static final int READ_TIMEOUT_MS = 60000; // 60 seconds
    private static final int EXPIRY_BUFFER_MINUTES = 5; // Refresh 5 min before expiry
    
    @Autowired
    private GcpStorage gcpStorage;
    
    @Autowired
    private GcsShippingLabelConfig gcsConfig;
    
    @Autowired
    private SalesShipmentTrackHandler salesShipmentTrackHandler;
    
    @Autowired
    private AmastyRmaRequestHandler amastyRmaRequestHandler;
    
    @Autowired
    private SellerShipmentPackDetailsHandler sellerShipmentPackDetailsHandler;
    
    @Autowired
    private SalesShipmentPackDetailsHandler salesShipmentPackDetailsHandler;
    
    // Handler registry - maps entity types to their handlers
    private Map<Class<?>, ShippingLabelEntityHandler<?>> handlerRegistry;
    
    /**
     * Initialize handler registry after all dependencies are injected
     */
    @PostConstruct
    public void initializeHandlers() {
        handlerRegistry = new HashMap<>();
        handlerRegistry.put(SalesShipmentTrack.class, salesShipmentTrackHandler);
        handlerRegistry.put(AmastyRmaRequest.class, amastyRmaRequestHandler);
        handlerRegistry.put(SellerShipmentPackDetails.class, sellerShipmentPackDetailsHandler);
        handlerRegistry.put(SalesShipmentPackDetails.class, salesShipmentPackDetailsHandler);
        
        LOGGER.info("==============================================");
        LOGGER.info("GCS Shipping Label Service Initialized");
        LOGGER.info("==============================================");
        LOGGER.info("Entity Handlers: " + handlerRegistry.size());
        LOGGER.info(gcsConfig.getConfigSummary());
        LOGGER.info("Configuration Priority: 1) Consul (config/oms/base) → 2) Environment Variables");
        LOGGER.info("==============================================");
    }

    @Override
    @Transactional
    public <T> String processAndSaveShippingLabel(
            String carrierShippingLabelUrl, 
            T entity,
            Class<T> entityType,
            String bucketName) throws IOException {
        
        // Check if feature is enabled (Consul flag takes priority over env var)
        if (!gcsConfig.isEnabled()) {
            LOGGER.info("GCS shipping label feature is DISABLED - using original carrier URL");
            LOGGER.info("Config source: " + (org.styli.services.order.utility.Constants.getGcsShippingLabelEnabled() != null ? "Consul" : "Environment Variable"));
            ShippingLabelEntityHandler<T> handler = getHandler(entityType);
            handler.setShippingLabelUrl(entity, carrierShippingLabelUrl);
            handler.saveEntity(entity);
            return carrierShippingLabelUrl;
        }
        
        LOGGER.debug("GCS shipping label feature is ENABLED - will process to GCS");
        LOGGER.debug("Bucket: " + (bucketName != null ? bucketName : gcsConfig.getBucket()) + " (from env: GCS_SHIPPING_LABEL_BUCKET)");
        LOGGER.debug("Folder Prefix: " + gcsConfig.getFolderPrefix() + " (from env: GCS_SHIPPING_LABEL_FOLDER_PREFIX)");
        
        if (StringUtils.isBlank(carrierShippingLabelUrl)) {
            throw new IllegalArgumentException("Carrier shipping label URL cannot be empty");
        }
        
        // Get appropriate handler for this entity type
        ShippingLabelEntityHandler<T> handler = getHandler(entityType);
        
        // SAFETY: Save backup of original carrier URL before GCS processing (for rollback safety)
        String currentBackupUrl = handler.getCarrierBackupUrl(entity);
        if (StringUtils.isBlank(currentBackupUrl)) {
            // Only save backup if not already saved
            handler.setCarrierBackupUrl(entity, carrierShippingLabelUrl);
            handler.saveEntity(entity);
            LOGGER.info(String.format("[%s] Saved backup carrier URL for %s for rollback safety",
                handler.getEntityTypeName(),
                handler.getEntityIdentifier(entity)));
        }
        
        try {
            LOGGER.info(String.format("[%s] Processing shipping label for %s from URL: %s", 
                handler.getEntityTypeName(), 
                handler.getEntityIdentifier(entity),
                carrierShippingLabelUrl));
            
            // OPTIMIZATION 1: Check if entity already has a valid signed URL stored in DB
            String existingGcsPath = handler.getGcsObjectPath(entity);
            Timestamp existingExpiry = handler.getGcsSignedUrlExpiry(entity);
            String existingUrl = handler.getShippingLabelUrl(entity);
            
            if (StringUtils.isNotBlank(existingGcsPath) && 
                existingExpiry != null && 
                StringUtils.isNotBlank(existingUrl)) {
                
                // Check if the signed URL is still valid
                if (!isSignedUrlExpiredInternal(existingExpiry)) {
                    LOGGER.info(String.format("[%s] Found existing valid signed URL for %s (expires at: %s). " +
                               "Returning cached URL without processing.",
                        handler.getEntityTypeName(),
                        handler.getEntityIdentifier(entity),
                        existingExpiry));
                    return existingUrl;
                } else {
                    // URL expired but GCS object exists, just refresh the signed URL
                    LOGGER.info(String.format("[%s] Existing signed URL expired for %s, but GCS object exists. " +
                               "Generating new signed URL without re-uploading.",
                        handler.getEntityTypeName(),
                        handler.getEntityIdentifier(entity)));
                    
                    return refreshSignedUrlInternal(entity, handler, existingGcsPath);
                }
            }
            
            // Step 1: Generate GCS object path (entity-specific)
            String gcsObjectPath = handler.generateGcsObjectPath(entity);
            String targetBucket = bucketName != null ? bucketName : gcsConfig.getBucket();
            
            // Prepend folder prefix if configured
            String fullObjectPath = gcsConfig.getFolderPrefix() != null && !gcsConfig.getFolderPrefix().isEmpty()
                ? gcsConfig.getFolderPrefix() + "/" + gcsObjectPath
                : gcsObjectPath;
            
            String gcsFullPath = String.format("gs://%s/%s", targetBucket, fullObjectPath);
            
            // OPTIMIZATION 2: Check if GCS object already exists (prevents duplicate uploads)
            if (gcpStorage.objectExists(targetBucket, fullObjectPath)) {
                LOGGER.info(String.format("[%s] GCS object already exists for %s at: %s. " +
                           "Reusing existing object without downloading/uploading.",
                    handler.getEntityTypeName(),
                    handler.getEntityIdentifier(entity),
                    gcsFullPath));
                
                // Object exists, just generate signed URL
                int expiryMinutes = gcsConfig.getSignedUrlExpiryMinutes();
                String signedUrl = gcpStorage.generateSignedUrlFromPath(gcsFullPath, expiryMinutes);
                
                Timestamp expiryTimestamp = Timestamp.from(
                    Instant.now().plus(expiryMinutes, java.time.temporal.ChronoUnit.MINUTES)
                );
                
                // Update entity with signed URL
                handler.setShippingLabelUrl(entity, signedUrl);
                handler.setGcsObjectPath(entity, gcsFullPath);
                handler.setGcsSignedUrlExpiry(entity, expiryTimestamp);
                handler.saveEntity(entity);
                
                LOGGER.info(String.format("[%s] Reused existing GCS object for %s. " +
                           "New signed URL expires at: %s",
                    handler.getEntityTypeName(),
                    handler.getEntityIdentifier(entity),
                    expiryTimestamp));
                
                return signedUrl;
            }
            
            // Step 2: Download file from carrier URL (only if needed)
            LOGGER.info(String.format("[%s] GCS object doesn't exist, downloading from carrier URL for %s",
                handler.getEntityTypeName(),
                handler.getEntityIdentifier(entity)));
            
            byte[] fileContent = downloadFileFromUrl(carrierShippingLabelUrl);
            LOGGER.info("Downloaded shipping label file: " + fileContent.length + " bytes");
            
            // Step 3: Upload to GCS (new file)
            LOGGER.info("Uploading new file to GCS: " + targetBucket + "/" + fullObjectPath);
            gcpStorage.uploadFile(
                targetBucket, 
                fullObjectPath, 
                fileContent, 
                determineContentType(carrierShippingLabelUrl)
            );
            
            // Step 4: Generate signed URL
            int expiryMinutes = gcsConfig.getSignedUrlExpiryMinutes();
            String signedUrl = gcpStorage.generateSignedUrlFromPath(gcsFullPath, expiryMinutes);
            
            // Step 5: Calculate expiry timestamp
            Timestamp expiryTimestamp = Timestamp.from(
                Instant.now().plus(expiryMinutes, java.time.temporal.ChronoUnit.MINUTES)
            );
            
            // Step 6: Update entity with signed URL (replaces original carrier URL)
            handler.setShippingLabelUrl(entity, signedUrl);
            handler.setGcsObjectPath(entity, gcsFullPath);
            handler.setGcsSignedUrlExpiry(entity, expiryTimestamp);
            handler.saveEntity(entity);
            
            LOGGER.info(String.format("[%s] Successfully processed and uploaded new shipping label for %s. " +
                       "GCS path: %s, Signed URL expires at: %s", 
                handler.getEntityTypeName(),
                handler.getEntityIdentifier(entity),
                gcsFullPath,
                expiryTimestamp));
            
            return signedUrl;
            
        } catch (Exception e) {
            LOGGER.error("Failed to process shipping label to GCS: " + e.getMessage(), e);
            // Fallback: save original carrier URL if GCS processing fails
            handler.setShippingLabelUrl(entity, carrierShippingLabelUrl);
            handler.saveEntity(entity);
            throw new IOException("Failed to process shipping label", e);
        }
    }

    @Override
    public <T> String getOrRefreshShippingLabelUrl(T entity, Class<T> entityType) throws Exception {
        
        ShippingLabelEntityHandler<T> handler = getHandler(entityType);
        
        // If feature is disabled or no GCS path, return current URL
        if (!gcsConfig.isEnabled() || StringUtils.isBlank(handler.getGcsObjectPath(entity))) {
            String currentUrl = handler.getShippingLabelUrl(entity);
            LOGGER.info(String.format("[%s] GCS disabled or no GCS path, returning existing URL for %s",
                handler.getEntityTypeName(),
                handler.getEntityIdentifier(entity)));
            return currentUrl;
        }
        
        // Check if signed URL is expired
        if (isSignedUrlExpired(entity, entityType)) {
            LOGGER.info(String.format("[%s] Signed URL expired for %s, refreshing...",
                handler.getEntityTypeName(),
                handler.getEntityIdentifier(entity)));
            return refreshSignedUrl(entity, entityType);
        }
        
        // Return existing signed URL (still valid)
        String existingUrl = handler.getShippingLabelUrl(entity);
        LOGGER.info(String.format("[%s] Returning existing valid signed URL for %s",
            handler.getEntityTypeName(),
            handler.getEntityIdentifier(entity)));
        return existingUrl;
    }

    @Override
    public <T> boolean isSignedUrlExpired(T entity, Class<T> entityType) {
        
        ShippingLabelEntityHandler<T> handler = getHandler(entityType);
        Timestamp expiryTimestamp = handler.getGcsSignedUrlExpiry(entity);
        
        if (expiryTimestamp == null) {
            LOGGER.debug("No expiry timestamp set, considering URL as expired");
            return true; // No expiry set, consider expired
        }
        
        // Check if expired or within buffer time (5 minutes before expiry)
        Instant now = Instant.now();
        Instant expiry = expiryTimestamp.toInstant();
        Instant bufferTime = now.plus(EXPIRY_BUFFER_MINUTES, java.time.temporal.ChronoUnit.MINUTES);
        
        boolean isExpired = expiry.isBefore(bufferTime);
        
        if (isExpired) {
            LOGGER.info(String.format("[%s] Signed URL is expired or expiring soon for %s. " +
                       "Expiry: %s, Now: %s",
                handler.getEntityTypeName(),
                handler.getEntityIdentifier(entity),
                expiryTimestamp,
                Timestamp.from(now)));
        }
        
        return isExpired;
    }

    @Override
    @Transactional
    public <T> String refreshSignedUrl(T entity, Class<T> entityType) throws Exception {
        
        ShippingLabelEntityHandler<T> handler = getHandler(entityType);
        String gcsPath = handler.getGcsObjectPath(entity);
        
        if (StringUtils.isBlank(gcsPath)) {
            throw new IllegalStateException(String.format(
                "No GCS path found for %s: %s. Cannot refresh signed URL.",
                handler.getEntityTypeName(),
                handler.getEntityIdentifier(entity)
            ));
        }
        
        // Generate new signed URL from the existing GCS object
        int expiryMinutes = gcsConfig.getSignedUrlExpiryMinutes();
        String newSignedUrl = gcpStorage.generateSignedUrlFromPath(gcsPath, expiryMinutes);
        
        // Update expiry timestamp
        Timestamp newExpiryTimestamp = Timestamp.from(
            Instant.now().plus(expiryMinutes, java.time.temporal.ChronoUnit.MINUTES)
        );
        
        // Update entity with new signed URL and expiry
        handler.setShippingLabelUrl(entity, newSignedUrl);
        handler.setGcsSignedUrlExpiry(entity, newExpiryTimestamp);
        handler.saveEntity(entity);
        
        LOGGER.info(String.format("[%s] Refreshed signed URL for %s. New expiry: %s",
            handler.getEntityTypeName(),
            handler.getEntityIdentifier(entity),
            newExpiryTimestamp));
        
        return newSignedUrl;
    }

    /**
     * Get the appropriate handler for entity type
     */
    @SuppressWarnings("unchecked")
    private <T> ShippingLabelEntityHandler<T> getHandler(Class<T> entityType) {
        ShippingLabelEntityHandler<?> handler = handlerRegistry.get(entityType);
        
        if (handler == null) {
            throw new IllegalArgumentException(
                "No handler registered for entity type: " + entityType.getName() + ". " +
                "Available handlers: " + handlerRegistry.keySet()
            );
        }
        
        return (ShippingLabelEntityHandler<T>) handler;
    }

    /**
     * Download file content from URL
     */
    private byte[] downloadFileFromUrl(String fileUrl) throws IOException {
        
        HttpURLConnection connection = null;
        
        try {
            URL url = new URL(fileUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "StyliOrderService/1.0");
            
            int responseCode = connection.getResponseCode();
            
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Failed to download file from " + fileUrl + 
                                    ", HTTP response code: " + responseCode);
            }
            
            try (InputStream inputStream = connection.getInputStream()) {
                byte[] content = ByteStreams.toByteArray(inputStream);
                LOGGER.info("Downloaded file from " + fileUrl + ": " + content.length + " bytes");
                return content;
            }
            
        } catch (IOException e) {
            LOGGER.error("Error downloading file from " + fileUrl + ": " + e.getMessage(), e);
            throw e;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Determine content type from URL
     */
    private String determineContentType(String url) {
        if (url == null) {
            return "application/octet-stream";
        }
        
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.endsWith(".pdf") || lowerUrl.contains(".pdf?")) {
            return "application/pdf";
        } else if (lowerUrl.endsWith(".png") || lowerUrl.contains(".png?")) {
            return "image/png";
        } else if (lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg") || 
                   lowerUrl.contains(".jpg?") || lowerUrl.contains(".jpeg?")) {
            return "image/jpeg";
        }
        
        return "application/octet-stream";
    }

    /**
     * Internal helper: Check if signed URL is expired based on timestamp
     * 
     * @param expiryTimestamp The expiry timestamp to check
     * @return true if expired or expiring soon, false if still valid
     */
    private boolean isSignedUrlExpiredInternal(Timestamp expiryTimestamp) {
        if (expiryTimestamp == null) {
            return true; // No expiry set, consider expired
        }
        
        // Check if expired or within buffer time (5 minutes before expiry)
        Instant now = Instant.now();
        Instant expiry = expiryTimestamp.toInstant();
        Instant bufferTime = now.plus(EXPIRY_BUFFER_MINUTES, java.time.temporal.ChronoUnit.MINUTES);
        
        return expiry.isBefore(bufferTime);
    }

    /**
     * Internal helper: Refresh signed URL for entity
     * 
     * @param entity The entity to refresh
     * @param handler The entity handler
     * @param gcsPath The GCS object path
     * @return New signed URL
     * @throws Exception if refresh fails
     */
    private <T> String refreshSignedUrlInternal(T entity, ShippingLabelEntityHandler<T> handler, String gcsPath) 
            throws Exception {
        
        // Generate new signed URL from the existing GCS object
        int expiryMinutes = gcsConfig.getSignedUrlExpiryMinutes();
        String newSignedUrl = gcpStorage.generateSignedUrlFromPath(gcsPath, expiryMinutes);
        
        // Update expiry timestamp
        Timestamp newExpiryTimestamp = Timestamp.from(
            Instant.now().plus(expiryMinutes, java.time.temporal.ChronoUnit.MINUTES)
        );
        
        // Update entity with new signed URL and expiry
        handler.setShippingLabelUrl(entity, newSignedUrl);
        handler.setGcsSignedUrlExpiry(entity, newExpiryTimestamp);
        handler.saveEntity(entity);
        
        LOGGER.info(String.format("[%s] Refreshed signed URL for %s. New expiry: %s",
            handler.getEntityTypeName(),
            handler.getEntityIdentifier(entity),
            newExpiryTimestamp));
        
        return newSignedUrl;
    }

    @Override
    public <T> String getShippingLabelWithFeatureCheck(T entity, Class<T> entityType) {
        
        // CRITICAL: Check if GCS feature is currently enabled
        if (!gcsConfig.isEnabled()) {
            LOGGER.info("GCS shipping label feature is DISABLED - returning null (caller should use alternative URL)");
            LOGGER.info("Config source: " + (org.styli.services.order.utility.Constants.getGcsShippingLabelEnabled() != null ? "Consul" : "Environment Variable"));
            return null; // Feature disabled, caller should handle fallback
        }
        
        ShippingLabelEntityHandler<T> handler = getHandler(entityType);
        String shippingLabel = handler.getShippingLabelUrl(entity);
        
        // If no shipping label stored, return null
        if (StringUtils.isBlank(shippingLabel)) {
            LOGGER.debug(String.format("[%s] No shipping label URL found for %s",
                handler.getEntityTypeName(),
                handler.getEntityIdentifier(entity)));
            return null;
        }
        
        // Check if URL is expired
        Timestamp expiryTimestamp = handler.getGcsSignedUrlExpiry(entity);
        if (expiryTimestamp != null && isSignedUrlExpiredInternal(expiryTimestamp)) {
            LOGGER.info(String.format("[%s] Shipping label URL expired for %s (expired at: %s). " +
                       "Caller should refresh or provide alternative.",
                handler.getEntityTypeName(),
                handler.getEntityIdentifier(entity),
                expiryTimestamp));
            // Return null to indicate caller needs to handle expired URL
            // Caller can either call refreshSignedUrl() or provide alternative URL
            return null;
        }
        
        LOGGER.debug(String.format("[%s] Returning valid shipping label URL for %s (expires: %s)",
            handler.getEntityTypeName(),
            handler.getEntityIdentifier(entity),
            expiryTimestamp));
        
        return shippingLabel;
    }
}
