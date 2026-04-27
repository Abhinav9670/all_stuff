package org.styli.services.order.helper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.styli.services.order.utility.Constants;

/**
 * Helper to safely get OMS Service Base URL with proper null handling
 * Provides fallback mechanisms when Consul configuration is not available
 * 
 * @author Configuration Team
 */
@Component
public class OmsServiceUrlHelper {

    private static final Log LOGGER = LogFactory.getLog(OmsServiceUrlHelper.class);

    @Autowired(required = false)
    private ConfigurationDiagnosticHelper diagnosticHelper;

    // Fallback URL from application properties
    @Value("${oms.service.base.url:}")
    private String fallbackOmsServiceUrl;

    /**
     * Get OMS Service Base URL with proper null checking and fallback
     * @return OMS Service Base URL or fallback/default
     */
    public String getOmsServiceBaseUrl() {
        try {
            // First, try to get from Consul configuration
            if (Constants.orderCredentials != null && 
                Constants.orderCredentials.getOrderDetails() != null) {
                
                String omsUrl = Constants.orderCredentials.getOrderDetails().getOmsServiceBaseUrl();
                if (omsUrl != null && !omsUrl.trim().isEmpty()) {
                    return omsUrl.trim();
                }
            }

            // If Consul config is null or empty, use fallback from properties
            if (fallbackOmsServiceUrl != null && !fallbackOmsServiceUrl.trim().isEmpty()) {
                LOGGER.warn("OMS Service URL from Consul is null/empty, using fallback from properties: " + fallbackOmsServiceUrl);
                return fallbackOmsServiceUrl.trim();
            }

            // Last resort - return a default local URL (for development)
            String defaultUrl = "http://localhost:8080";
            LOGGER.error("Both Consul and properties OMS Service URL are null/empty, using default: " + defaultUrl);
            return defaultUrl;

        } catch (Exception e) {
            LOGGER.error("Error getting OMS Service Base URL: " + e.getMessage(), e);
            return "http://localhost:8080"; // Emergency fallback
        }
    }

    /**
     * Check if OMS Service URL is properly configured
     * @return true if URL is available from Consul
     */
    public boolean isOmsServiceUrlConfigured() {
        try {
            return Constants.orderCredentials != null && 
                   Constants.orderCredentials.getOrderDetails() != null &&
                   Constants.orderCredentials.getOrderDetails().getOmsServiceBaseUrl() != null &&
                   !Constants.orderCredentials.getOrderDetails().getOmsServiceBaseUrl().trim().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get detailed diagnostic information about OMS Service URL
     * @return diagnostic message
     */
    public String getDiagnosticInfo() {
        if (diagnosticHelper != null) {
            return diagnosticHelper.diagnoseOmsServiceBaseUrl();
        }
        return "Diagnostic helper not available";
    }

    /**
     * Build complete OMS API URL
     * @param endpoint the API endpoint (e.g., "/api/orders")
     * @return complete URL
     */
    public String buildOmsApiUrl(String endpoint) {
        String baseUrl = getOmsServiceBaseUrl();
        
        // Remove trailing slash from baseUrl if present
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        
        // Add leading slash to endpoint if missing
        if (!endpoint.startsWith("/")) {
            endpoint = "/" + endpoint;
        }
        
        return baseUrl + endpoint;
    }

    /**
     * Log current configuration status
     */
    public void logConfigurationStatus() {
        LOGGER.info("=== OMS SERVICE URL CONFIGURATION STATUS ===");
        LOGGER.info("Consul configured: " + isOmsServiceUrlConfigured());
        LOGGER.info("Current URL: " + getOmsServiceBaseUrl());
        LOGGER.info("Fallback URL: " + (fallbackOmsServiceUrl != null ? fallbackOmsServiceUrl : "Not set"));
        
        if (diagnosticHelper != null) {
            LOGGER.info("Configuration Status: " + diagnosticHelper.getConfigurationStatus());
        }
    }
}
