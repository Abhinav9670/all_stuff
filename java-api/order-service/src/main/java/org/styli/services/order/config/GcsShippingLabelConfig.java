package org.styli.services.order.config;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.styli.services.order.utility.Constants;

/**
 * Configuration properties for GCS Shipping Label feature
 * 
 * ALL Configuration Priority (Consul > Environment Variable > Default):
 * 1. Consul (config/orderConsulKeys JSON properties) - HIGHEST
 * 2. Environment variables (GCS_*) - FALLBACK
 * 3. application.properties defaults - LOWEST
 * 
 * Consul Properties (in config/orderConsulKeys):
 * - gcs_shipping_label_enabled: Boolean - Feature enable/disable
 * - gcs_shipping_label_bucket: String - GCS bucket name
 * - gcs_shipping_label_folder_prefix: String - Folder prefix
 * - gcs_signed_url_expiry_minutes: Integer - URL expiry duration
 * 
 * Environment Variable Fallbacks:
 * - GCS_SHIPPING_LABEL_ENABLED
 * - GCS_SHIPPING_LABEL_BUCKET
 * - GCS_SHIPPING_LABEL_FOLDER_PREFIX
 * - GCS_SIGNED_URL_EXPIRY_MINUTES
 * 
 * Integrated APIs (using common ShippingLabelUrlService):
 * 1. Regular Shipments (/oms/get-shipment-v3)
 * 2. Seller Shipments (/oms/seller/get-shipment-v3)
 * 3. Return Shipments (getReturnShipment)
 */
@Component
@ConfigurationProperties(prefix = "gcs.shipping.label")
@Getter
@Setter
public class GcsShippingLabelConfig {
    
    private static final Log LOGGER = LogFactory.getLog(GcsShippingLabelConfig.class);
    
    /**
     * GCS bucket name where shipping labels will be stored
     * Populated from environment variable: GCS_SHIPPING_LABEL_BUCKET
     * Default from application.properties: oms_styli_dev
     */
    private String bucket;
    
    /**
     * Folder prefix within the bucket (e.g., styli-shipping-labels)
     * Populated from environment variable: GCS_SHIPPING_LABEL_FOLDER_PREFIX
     * Default from application.properties: styli-shipping-labels
     */
    private String folderPrefix;
    
    /**
     * Feature flag from environment variable
     * Populated from environment variable: GCS_SHIPPING_LABEL_ENABLED
     * Used as fallback when Consul flag is not set
     * Default: true
     */
    private Boolean enabled;
    
    /**
     * Signed URL expiry duration in minutes
     * Populated from environment variable: GCS_SIGNED_URL_EXPIRY_MINUTES
     * Default from application.properties: 60 minutes
     */
    private Integer signedUrlExpiryMinutes;
    
    /**
     * Get GCS bucket name with Consul override
     * Priority: Consul > Environment Variable > Default
     */
    public String getBucket() {
        try {
            String consulBucket = Constants.getGcsShippingLabelBucket();
            if (consulBucket != null && !consulBucket.isEmpty()) {
                LOGGER.debug("GCS bucket from Consul: " + consulBucket);
                return consulBucket;
            }
        } catch (Exception e) {
            LOGGER.error("Error getting bucket from Consul: " + e.getMessage());
        }
        
        // Fallback to environment variable / application.properties
        return bucket != null ? bucket : "oms_styli_dev";
    }
    
    /**
     * Get GCS folder prefix with Consul override
     * Priority: Consul > Environment Variable > Default
     */
    public String getFolderPrefix() {
        try {
            String consulPrefix = Constants.getGcsShippingLabelFolderPrefix();
            if (consulPrefix != null && !consulPrefix.isEmpty()) {
                LOGGER.debug("GCS folder prefix from Consul: " + consulPrefix);
                return consulPrefix;
            }
        } catch (Exception e) {
            LOGGER.error("Error getting folder prefix from Consul: " + e.getMessage());
        }
        
        // Fallback to environment variable / application.properties
        return folderPrefix != null ? folderPrefix : "styli-shipping-labels";
    }
    
    /**
     * Get signed URL expiry minutes with Consul override
     * Priority: Consul > Environment Variable > Default
     */
    public Integer getSignedUrlExpiryMinutes() {
        try {
            Integer consulExpiry = Constants.getGcsSignedUrlExpiryMinutes();
            if (consulExpiry != null && consulExpiry > 0) {
                LOGGER.debug("GCS expiry minutes from Consul: " + consulExpiry);
                return consulExpiry;
            }
        } catch (Exception e) {
            LOGGER.error("Error getting expiry minutes from Consul: " + e.getMessage());
        }
        
        // Fallback to environment variable / application.properties
        return signedUrlExpiryMinutes != null ? signedUrlExpiryMinutes : 60;
    }
    
    /**
     * Check if feature is enabled
     * 
     * Priority:
     * 1. Consul flag "gcs_shipping_label_enabled" (highest priority)
     * 2. Environment variable GCS_SHIPPING_LABEL_ENABLED (fallback)
     * 3. Default: true (if neither is set)
     * 
     * @return true if feature is enabled, false if explicitly disabled
     */
    public boolean isEnabled() {
        try {
            // Priority 1: Check Consul flag first
            Boolean consulFlag = Constants.getGcsShippingLabelEnabled();
            
            if (consulFlag != null) {
                LOGGER.debug("GCS Shipping Label feature flag from Consul: " + consulFlag);
                return consulFlag;
            }
            
            // Priority 2: Check environment variable (via Spring property)
            if (enabled != null) {
                LOGGER.debug("GCS Shipping Label feature flag from environment variable: " + enabled);
                return enabled;
            }
            
            // Priority 3: Default to true if neither is set
            LOGGER.debug("GCS Shipping Label feature flag not set in Consul or environment, using default: true");
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Error checking GCS Shipping Label feature flag, defaulting to true: " + e.getMessage());
            return true;  // Default to enabled on error
        }
    }
    
    /**
     * Get configuration summary for logging
     */
    public String getConfigSummary() {
        // Check Consul values
        Boolean consulEnabled = Constants.getGcsShippingLabelEnabled();
        String consulBucket = Constants.getGcsShippingLabelBucket();
        String consulPrefix = Constants.getGcsShippingLabelFolderPrefix();
        Integer consulExpiry = Constants.getGcsSignedUrlExpiryMinutes();
        
        // Format status strings
        String enabledStatus = consulEnabled != null ? consulEnabled.toString() : "not set";
        String envEnabledStatus = enabled != null ? enabled.toString() : "not set";
        String bucketSource = consulBucket != null ? "Consul" : (bucket != null ? "Env/Config" : "Default");
        String prefixSource = consulPrefix != null ? "Consul" : (folderPrefix != null ? "Env/Config" : "Default");
        String expirySource = consulExpiry != null ? "Consul" : (signedUrlExpiryMinutes != null ? "Env/Config" : "Default");
        
        return String.format(
            "\n" +
            "┌────────────────────────────────────────────────────────────────┐\n" +
            "│         GCS Shipping Label Configuration Summary              │\n" +
            "├────────────────────────────────────────────────────────────────┤\n" +
            "│ Status:            %-42s │\n" +
            "│                                                                │\n" +
            "│ Feature Control:                                               │\n" +
            "│  • Consul:         %-42s │\n" +
            "│  • Env Variable:   %-42s │\n" +
            "│  • Effective:      %-42s │\n" +
            "│                                                                │\n" +
            "│ GCS Settings (Source):                                         │\n" +
            "│  • Bucket:         %-30s (%s)%s │\n" +
            "│  • Folder:         %-30s (%s)%s │\n" +
            "│  • URL Expiry:     %-30s (%s)%s │\n" +
            "│                                                                │\n" +
            "│ Priority: Consul > Environment Variable > Default              │\n" +
            "│                                                                │\n" +
            "│ Integrated APIs (Common Functionality):                        │\n" +
            "│  1. Regular Shipments  (/oms/get-shipment-v3)                 │\n" +
            "│  2. Seller Shipments   (/oms/seller/get-shipment-v3)          │\n" +
            "│  3. Return Shipments   (getReturnShipment)                     │\n" +
            "└────────────────────────────────────────────────────────────────┘",
            isEnabled() ? "ENABLED" : "DISABLED",
            enabledStatus,
            envEnabledStatus,
            isEnabled() ? "ENABLED" : "DISABLED",
            getBucket(), bucketSource, "",
            getFolderPrefix(), prefixSource, "",
            getSignedUrlExpiryMinutes() + " min", expirySource, ""
        );
    }
}
