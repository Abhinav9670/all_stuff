package org.styli.services.customer.service.impl;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.styli.services.customer.pojo.webhook.ShukranWebhookRequest;
import org.styli.services.customer.pojo.webhook.ShukranWebhookResponse;
import org.styli.services.customer.service.Client;
import org.styli.services.customer.service.ConfigService;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.consul.ServiceConfigs;

/**
 * Service implementation for Shukran webhook operations
 * Handles common validation logic for Shukran webhook endpoints
 * 
 * @author Generated
 */
@Service
public class ShukranWebhookServiceImpl {

    private static final Log LOGGER = LogFactory.getLog(ShukranWebhookServiceImpl.class);

    @Autowired
    private ConfigService configService;

    @Autowired
    private Client client;

    /**
     * Handles common Shukran webhook validation logic including authorization,
     * feature flag validation, and country-based store ID checks.
     * 
     * @param webhookRequest The webhook request
     * @param authorizationToken The authorization token
     * @param endpointName The endpoint name for logging
     * @param serviceCall The specific service method to call
     * @return ShukranWebhookResponse
     */
    public ShukranWebhookResponse handleShukranWebhook(
            ShukranWebhookRequest webhookRequest,
            String authorizationToken,
            String endpointName,
            java.util.function.Function<ShukranWebhookRequest, ShukranWebhookResponse> serviceCall) {

        // 1. Authorization check
        ShukranWebhookResponse errorResponse = checkShukranAuthorization(authorizationToken, endpointName);
        if (errorResponse != null) {
            return errorResponse;
        }

        // 2. Feature flag and country validation
        errorResponse = checkShukranFeatureAndCountryEnabled(webhookRequest, endpointName);
        if (errorResponse != null) {
            return errorResponse;
        }

        // 3. Execute the specific service call
        LOGGER.info("All checks passed, proceeding with flow");
        return serviceCall.apply(webhookRequest);
    }

    /**
     * Checks authorization for Shukran webhook requests
     * 
     * @param authorizationToken The authorization token
     * @param endpointName The endpoint name for logging
     * @return ShukranWebhookResponse with error if authorization fails, null if successful
     */
    private ShukranWebhookResponse checkShukranAuthorization(String authorizationToken, String endpointName) {
        LOGGER.info("Checking authorization for " +endpointName);
        if (authorizationToken == null || authorizationToken.trim().isEmpty()) {
            LOGGER.info("Missing authorization-token header for "+ endpointName);
            ShukranWebhookResponse response = new ShukranWebhookResponse();
            response.setStatus(false);
            response.setStatusCode("401");
            response.setStatusMessage("Missing authorization-token header");
            return response;
        }
        
        try {
            boolean isAuthorized = configService.checkAuthorizationExternal(authorizationToken);
            if (!isAuthorized) {
                LOGGER.info("Authorization failed via ConfigService for " +endpointName);
                ShukranWebhookResponse response = new ShukranWebhookResponse();
                response.setStatus(false);
                response.setStatusCode("401");
                response.setStatusMessage("Authorization failed");
                return response;
            }
        } catch (Exception ex) {
            LOGGER.info("Authorization verification error for :" +ex);
            ShukranWebhookResponse response = new ShukranWebhookResponse();
            response.setStatus(false);
            response.setStatusCode("401");
            response.setStatusMessage("Authorization verification failed");
            return response;
        }
        
        return null; // Authorization successful
    }

    /**
     * Checks feature flag and country-based store ID validation for Shukran webhook requests
     * 
     * @param webhookRequest The webhook request
     * @param endpointName The endpoint name for logging
     * @return ShukranWebhookResponse with error if validation fails, null if successful
     */
    private ShukranWebhookResponse checkShukranFeatureAndCountryEnabled(ShukranWebhookRequest webhookRequest, String endpointName) {

        LOGGER.info("Checking feature flag and country-based store ID validation for " +endpointName);
        // Check Shukran linking/delinking feature flag
        List<Integer> enabledStoreIds = ServiceConfigs.getShukranLinkingDelinkingEnabled();
        LOGGER.info("Shukran Linking/Delinking enabled store IDs: {}" +enabledStoreIds);
        
        if (enabledStoreIds == null || enabledStoreIds.isEmpty()) {
            LOGGER.info("Shukran Linking/Delinking feature is disabled for all stores");
            ShukranWebhookResponse response = new ShukranWebhookResponse();
            response.setStatus(false);
            response.setStatusCode("403");
            response.setStatusMessage("Shukran Linking/Delinking feature is disabled");
            return response;
        }
        
        // Check mobile number country code and store ID
        String mobileNumber = webhookRequest.getPrimaryPhoneNumber();
        if (mobileNumber != null && !mobileNumber.trim().isEmpty()) {
            // Clean the mobile number by removing spaces, hyphens, and plus signs
            String cleanedNumber = mobileNumber.replaceAll("[\\s\\-\\+]", "");
            
            // Validate that the cleaned number has at least 3 characters for country code
            if (cleanedNumber.length() < 3) {
                LOGGER.info("Mobile number too short after cleaning" +cleanedNumber);
                ShukranWebhookResponse response = new ShukranWebhookResponse();
                response.setStatus(false);
                response.setStatusCode("400");
                response.setStatusMessage("Mobile number is too short to extract country code");
                return response;
            }
            
            // Extract country code from mobile number (first 3 digits)
            String countryCode = cleanedNumber.substring(0, 3);
            LOGGER.info("Extracted country code " +countryCode + "for mobile : " +mobileNumber);
            
            // Get store IDs for this country code
            List<Integer> countryStoreIds = Constants.getStoreIdsByCountryCode(countryCode);
            LOGGER.info("Store IDs for country " +countryCode + "for country code : " +countryCode);
    
            
            // Check if country code is supported
            if (countryStoreIds == null) {
                LOGGER.info("Country code {} is not supported" +countryCode);
                ShukranWebhookResponse response = new ShukranWebhookResponse();
                response.setStatus(false);
                response.setStatusCode("400");
                response.setStatusMessage("Country code not supported");
                return response;
            }
            
            // Check if any of the country's store IDs are enabled
            boolean isCountryEnabled = false;
            for (Integer storeId : countryStoreIds) {
                if (enabledStoreIds.contains(storeId)) {
                    isCountryEnabled = true;
                    break;
                }
            }
            
            if (isCountryEnabled) {
                LOGGER.info("Store IDs are enabled, proceeding with flow" +countryCode);
                return null; 
            } else {
                LOGGER.info("Store IDs are not enabled" +countryCode);
                ShukranWebhookResponse response = new ShukranWebhookResponse();
                response.setStatus(false);
                response.setStatusCode("403");
                response.setStatusMessage("Shukran Linking/Delinking not enabled for this country");
                return response;
            }
        } else {
            LOGGER.info("No mobile number provided in request");
            ShukranWebhookResponse response = new ShukranWebhookResponse();
            response.setStatus(false);
            response.setStatusCode("400");
            response.setStatusMessage("Mobile number is required");
            return response;
        }
    }
}
