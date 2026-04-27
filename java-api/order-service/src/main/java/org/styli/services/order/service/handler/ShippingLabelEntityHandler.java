package org.styli.services.order.service.handler;

import java.sql.Timestamp;

/**
 * Strategy interface for handling different entity types that have shipping labels
 * Each entity (SalesShipmentTrack, AmastyRmaRequest, etc.) has its own handler implementation
 * 
 * This allows the ShippingLabelUrlService to work with any entity type without knowing the details
 */
public interface ShippingLabelEntityHandler<T> {

    /**
     * Get the shipping label URL from entity (will be updated with signed URL)
     */
    String getShippingLabelUrl(T entity);
    
    /**
     * Set the shipping label URL (signed URL)
     */
    void setShippingLabelUrl(T entity, String url);
    
    /**
     * Get GCS object path from entity
     */
    String getGcsObjectPath(T entity);
    
    /**
     * Set GCS object path (gs://bucket/path format)
     */
    void setGcsObjectPath(T entity, String gcsPath);
    
    /**
     * Get expiry timestamp for the signed URL
     */
    Timestamp getGcsSignedUrlExpiry(T entity);
    
    /**
     * Set expiry timestamp
     */
    void setGcsSignedUrlExpiry(T entity, Timestamp expiry);
    
    /**
     * Get the backup carrier URL (for rollback safety)
     */
    String getCarrierBackupUrl(T entity);
    
    /**
     * Set the backup carrier URL before GCS processing (for rollback safety)
     */
    void setCarrierBackupUrl(T entity, String backupUrl);
    
    /**
     * Generate unique GCS object path for this entity
     * Format: {type}/{year}/{month}/entity-{id}_{timestamp}.pdf
     */
    String generateGcsObjectPath(T entity);
    
    /**
     * Save entity to database
     */
    void saveEntity(T entity);
    
    /**
     * Get entity identifier for logging (e.g., "TrackID:123,OrderID:456")
     */
    String getEntityIdentifier(T entity);
    
    /**
     * Get entity type name for logging (e.g., "SalesShipmentTrack")
     */
    String getEntityTypeName();
}
