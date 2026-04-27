package org.styli.services.order.service;

import java.io.IOException;

/**
 * Service for managing shipping label URLs with GCS integration
 * 
 * This service handles:
 * - Downloading shipping labels from carrier APIs
 * - Uploading to private GCS buckets
 * - Generating signed URLs
 * - Managing URL expiry and auto-refresh
 * 
 * Works with any entity type through Strategy Pattern
 */
public interface ShippingLabelUrlService {
    
    /**
     * Process shipping label: download from carrier URL, upload to GCS, generate signed URL
     * The signed URL will be stored in the entity's shipping_label field
     * 
     * @param <T> Entity type (SalesShipmentTrack, AmastyRmaRequest, etc.)
     * @param carrierShippingLabelUrl Original URL from carrier API (Navik, Alpha, etc.)
     * @param entity Entity to update with shipping label data
     * @param entityType Class of the entity
     * @param bucketName Optional bucket name (null = use default from config)
     * @return Signed URL that was stored in the entity
     * @throws IOException if download or upload fails
     */
    <T> String processAndSaveShippingLabel(
        String carrierShippingLabelUrl,
        T entity,
        Class<T> entityType,
        String bucketName
    ) throws IOException;
    
    /**
     * Get shipping label URL - returns valid signed URL or refreshes if expired
     * 
     * @param <T> Entity type
     * @param entity Entity with shipping label data
     * @param entityType Class of the entity
     * @return Valid signed URL (refreshed if needed)
     * @throws Exception if refresh fails
     */
    <T> String getOrRefreshShippingLabelUrl(T entity, Class<T> entityType) throws Exception;
    
    /**
     * Check if signed URL is expired or about to expire (within buffer time)
     * 
     * @param <T> Entity type
     * @param entity Entity to check
     * @param entityType Class of the entity
     * @return true if expired or within 5 minutes of expiry
     */
    <T> boolean isSignedUrlExpired(T entity, Class<T> entityType);
    
    /**
     * Refresh signed URL from existing GCS object
     * Generates new signed URL and updates entity with new URL and expiry
     * 
     * @param <T> Entity type
     * @param entity Entity to refresh
     * @param entityType Class of the entity
     * @return New signed URL
     * @throws Exception if refresh fails
     */
    <T> String refreshSignedUrl(T entity, Class<T> entityType) throws Exception;
    
    /**
     * Get shipping label URL with feature flag awareness
     * 
     * IMPORTANT: Use this method when retrieving shipping labels from the database
     * to ensure proper handling of the gcs_shipping_label_enabled flag.
     * 
     * Behavior:
     * - If GCS feature is DISABLED (gcs_shipping_label_enabled=false):
     *   Returns null, indicating caller should use alternative URL source
     * - If GCS feature is ENABLED:
     *   Returns the current URL from entity (may refresh if expired)
     * 
     * @param <T> Entity type
     * @param entity Entity with shipping label data
     * @param entityType Class of the entity
     * @return Shipping label URL if feature enabled, null if feature disabled
     */
    <T> String getShippingLabelWithFeatureCheck(T entity, Class<T> entityType);
}
