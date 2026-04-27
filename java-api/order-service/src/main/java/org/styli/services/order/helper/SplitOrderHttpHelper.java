package org.styli.services.order.helper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.pojo.request.Order.SplitOrderApiRequest;
import org.styli.services.order.pojo.response.Order.SplitOrderApiResponse;

import java.util.Collections;

/**
 * Helper class for making HTTP POST API calls with Split Order payloads
 * Contains parentOrderId and splitOrderId for external API communication
 * 
 * @author API Helper
 */
@Component
public class SplitOrderHttpHelper {

    private static final Log LOGGER = LogFactory.getLog(SplitOrderHttpHelper.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    @Qualifier("withoutEureka")
    private RestTemplate restTemplate;

    /**
     * Makes an HTTP POST API call with parentOrderId and splitOrderId payload
     * 
     * @param url The API endpoint URL
     * @param parentOrderId The parent order ID
     * @param splitOrderId The split order ID
     * @param authToken Optional authorization token
     * @param additionalHeaders Optional additional headers
     * @return SplitOrderApiResponse containing the API response
     */
    public SplitOrderApiResponse postSplitOrderApi(String url, Integer parentOrderId, Integer splitOrderId, 
                                                  String authToken, HttpHeaders additionalHeaders) {
        
        if (!StringUtils.hasText(url)) {
            LOGGER.error("Split Order API URL is empty or null");
            return createErrorResponse("INVALID_URL", "API URL cannot be empty");
        }

        if (parentOrderId == null || splitOrderId == null) {
            LOGGER.error("Split Order API - parentOrderId or splitOrderId is null");
            return createErrorResponse("INVALID_PAYLOAD", "Parent Order ID and Split Order ID are required");
        }

        try {
            // Create request payload
            SplitOrderApiRequest payload = new SplitOrderApiRequest(parentOrderId, splitOrderId);

            // Setup headers
            HttpHeaders requestHeaders = setupHeaders(authToken, additionalHeaders);

            // Create HTTP entity
            HttpEntity<SplitOrderApiRequest> requestBody = new HttpEntity<>(payload, requestHeaders);

            // Log request details
            LOGGER.info("Split Order API URL: " + url);
            LOGGER.info("Split Order API Request Body: " + mapper.writeValueAsString(payload));

            // Make the API call
            ResponseEntity<SplitOrderApiResponse> response = restTemplate.exchange(
                url, HttpMethod.POST, requestBody, SplitOrderApiResponse.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                LOGGER.info("Split Order API Response: " + mapper.writeValueAsString(response.getBody()));
                return response.getBody();
            } else {
                LOGGER.error("Split Order API - Unexpected response status: " + response.getStatusCode());
                return createErrorResponse("API_ERROR", "Unexpected response status: " + response.getStatusCode());
            }

        } catch (RestClientException e) {
            LOGGER.error("Split Order API - RestClientException: " + e.getMessage());
            return createErrorResponse("REST_CLIENT_ERROR", "API call failed: " + e.getMessage());
        } catch (JsonProcessingException e) {
            LOGGER.error("Split Order API - JsonProcessingException: " + e.getMessage());
            return createErrorResponse("JSON_ERROR", "JSON processing failed: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Split Order API - Unexpected exception: " + e.getMessage());
            return createErrorResponse("UNEXPECTED_ERROR", "Unexpected error occurred: " + e.getMessage());
        }
    }

    /**
     * Simplified method without additional headers and auth token
     */
    public SplitOrderApiResponse postSplitOrderApi(String url, Integer parentOrderId, Integer splitOrderId) {
        return postSplitOrderApi(url, parentOrderId, splitOrderId, null, null);
    }

    /**
     * Method with auth token but no additional headers
     */
    public SplitOrderApiResponse postSplitOrderApi(String url, Integer parentOrderId, Integer splitOrderId, String authToken) {
        return postSplitOrderApi(url, parentOrderId, splitOrderId, authToken, null);
    }

    /**
     * Setup HTTP headers for the request
     */
    private HttpHeaders setupHeaders(String authToken, HttpHeaders additionalHeaders) {
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);

        // Add authorization header if token is provided
        if (StringUtils.hasText(authToken)) {
            requestHeaders.set("Authorization", "Bearer " + authToken);
        }

        // Add any additional headers
        if (additionalHeaders != null && !additionalHeaders.isEmpty()) {
            additionalHeaders.forEach((key, values) -> {
                values.forEach(value -> requestHeaders.add(key, value));
            });
        }

        return requestHeaders;
    }

    /**
     * Create error response object
     */
    private SplitOrderApiResponse createErrorResponse(String errorCode, String errorMessage) {
        SplitOrderApiResponse errorResponse = new SplitOrderApiResponse();
        errorResponse.setStatus(false);
        errorResponse.setStatusCode(errorCode);
        errorResponse.setStatusMsg(errorMessage);
        return errorResponse;
    }
}
