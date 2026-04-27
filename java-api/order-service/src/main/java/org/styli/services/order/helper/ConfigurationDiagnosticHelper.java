package org.styli.services.order.helper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Component;
import org.styli.services.order.pojo.OrderKeyDetails;
import org.styli.services.order.utility.Constants;

/**
 * Helper class for diagnosing configuration loading issues
 * Specifically for debugging null omsServiceBaseUrl and other config values
 * 
 * @author Configuration Team
 */
@Component
public class ConfigurationDiagnosticHelper {

    private static final Log LOGGER = LogFactory.getLog(ConfigurationDiagnosticHelper.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Diagnose OMS Service Base URL configuration issue
     * @return diagnostic report as string
     */
    public String diagnoseOmsServiceBaseUrl() {
        StringBuilder report = new StringBuilder();
        report.append("\n=== OMS SERVICE BASE URL DIAGNOSTIC REPORT ===\n");

        try {
            // Check if Constants.orderCredentials is null
            if (Constants.orderCredentials == null) {
                report.append("❌ ERROR: Constants.orderCredentials is NULL\n");
                report.append("   - Consul configuration not loaded\n");
                report.append("   - Check Consul connection and credentials\n");
                report.append("   - Verify ConsulComponent.init() was called\n");
                return report.toString();
            }
            report.append("✅ Constants.orderCredentials is loaded\n");

            // Check if orderDetails is null
            OrderKeyDetails orderDetails = Constants.orderCredentials.getOrderDetails();
            if (orderDetails == null) {
                report.append("❌ ERROR: orderCredentials.getOrderDetails() is NULL\n");
                report.append("   - 'order_details' key missing in Consul configuration\n");
                report.append("   - Check Consul key: java/order-service/credentials_{env}\n");
                return report.toString();
            }
            report.append("✅ orderCredentials.getOrderDetails() is loaded\n");

            // Check specific OMS Service Base URL
            String omsServiceBaseUrl = orderDetails.getOmsServiceBaseUrl();
            if (omsServiceBaseUrl == null) {
                report.append("❌ ERROR: omsServiceBaseUrl is NULL\n");
                report.append("   - 'OMS_SERVICE_BASE_URL' key missing in order_details configuration\n");
                report.append("   - Add 'OMS_SERVICE_BASE_URL': 'your-oms-url' to Consul config\n");
            } else if (omsServiceBaseUrl.trim().isEmpty()) {
                report.append("❌ ERROR: omsServiceBaseUrl is EMPTY\n");
                report.append("   - Value: '" + omsServiceBaseUrl + "'\n");
            } else {
                report.append("✅ omsServiceBaseUrl is loaded: " + omsServiceBaseUrl + "\n");
            }

            // Show other URL configurations for comparison
            report.append("\n--- Other Service URLs Status ---\n");
            report.append("OTS Service URL: " + (orderDetails.getOtsServiceBaseUrl() != null ? orderDetails.getOtsServiceBaseUrl() : "NULL") + "\n");
            report.append("Customer Service URL: " + (orderDetails.getCustomerServiceBaseUrl() != null ? orderDetails.getCustomerServiceBaseUrl() : "NULL") + "\n");
            report.append("Payment Service URL: " + (orderDetails.getPaymentServiceBaseUrl() != null ? orderDetails.getPaymentServiceBaseUrl() : "NULL") + "\n");

        } catch (Exception e) {
            report.append("❌ EXCEPTION during diagnosis: " + e.getMessage() + "\n");
            LOGGER.error("Configuration diagnosis failed", e);
        }

        return report.toString();
    }

    /**
     * Log full configuration for debugging
     */
    public void logFullConfiguration() {
        try {
            if (Constants.orderCredentials != null) {
                LOGGER.info("=== FULL CONFIGURATION DUMP ===");
                LOGGER.info(mapper.writeValueAsString(Constants.orderCredentials));
            } else {
                LOGGER.warn("Constants.orderCredentials is null - cannot log configuration");
            }
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to serialize configuration: " + e.getMessage());
        }
    }

    /**
     * Get safe OMS Service Base URL with fallback
     * @param fallbackUrl fallback URL if configuration is missing
     * @return OMS Service Base URL or fallback
     */
    public String getSafeOmsServiceBaseUrl(String fallbackUrl) {
        try {
            if (Constants.orderCredentials != null && 
                Constants.orderCredentials.getOrderDetails() != null &&
                Constants.orderCredentials.getOrderDetails().getOmsServiceBaseUrl() != null) {
                
                String url = Constants.orderCredentials.getOrderDetails().getOmsServiceBaseUrl().trim();
                if (!url.isEmpty()) {
                    return url;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error getting OMS Service Base URL: " + e.getMessage());
        }
        
        LOGGER.warn("Using fallback OMS Service Base URL: " + fallbackUrl);
        return fallbackUrl;
    }

    /**
     * Check if configuration is properly loaded
     * @return true if configuration is available
     */
    public boolean isConfigurationLoaded() {
        return Constants.orderCredentials != null && 
               Constants.orderCredentials.getOrderDetails() != null;
    }

    /**
     * Get configuration loading status
     * @return status message
     */
    public String getConfigurationStatus() {
        if (Constants.orderCredentials == null) {
            return "FAILED: Consul configuration not loaded";
        }
        
        if (Constants.orderCredentials.getOrderDetails() == null) {
            return "PARTIAL: Configuration loaded but order_details missing";
        }
        
        OrderKeyDetails details = Constants.orderCredentials.getOrderDetails();
        int loadedUrls = 0;
        int totalUrls = 8; // Total number of service URLs we expect
        
        if (details.getOmsServiceBaseUrl() != null) loadedUrls++;
        if (details.getOtsServiceBaseUrl() != null) loadedUrls++;
        if (details.getCustomerServiceBaseUrl() != null) loadedUrls++;
        if (details.getPaymentServiceBaseUrl() != null) loadedUrls++;
        if (details.getInventoryBaseUrl() != null) loadedUrls++;
        if (details.getBrazeServiceBaseUrl() != null) loadedUrls++;
        if (details.getWmsUrl() != null) loadedUrls++;
        if (details.getAlphaBaseUrl() != null) loadedUrls++;
        
        return String.format("LOADED: %d/%d service URLs configured", loadedUrls, totalUrls);
    }
}
