package org.styli.services.customer.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import org.styli.services.customer.config.KafkaAsyncService;
import org.styli.services.customer.pojo.InfluencerEmailPayload;
import org.styli.services.customer.pojo.response.Influencer;
import org.styli.services.customer.pojo.response.InfluencerResponse;
import org.styli.services.customer.service.impl.PubSubServiceImpl;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.consul.ServiceConfigs;
import org.springframework.util.MultiValueMap;
import org.springframework.util.LinkedMultiValueMap;
import com.fasterxml.jackson.databind.JsonNode;

@Service
public class InfluencerApiService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private PubSubTemplate pubSubTemplate;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    PubSubServiceImpl pubSubServiceImpl;

    @Autowired
    @Lazy
    KafkaAsyncService kafkaService;

    private static final Logger LOGGER = LoggerFactory.getLogger(InfluencerApiService.class);

    /**
     * This method computes the date range based on Consul configuration and triggers the
     * fetch/publish flow.
     */
    public void fetchEmailsAndPublishToPubSub() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(1);
        String dateRangeSourceInfo = "default 24-hour range";

        if (ServiceConfigs.getInfluencerManualDateRangeFlag()) {
            String manualStartDateStr = ServiceConfigs.getInfluencerManualStartDate();
            String manualEndDateStr = ServiceConfigs.getInfluencerManualEndDate();

            if (manualStartDateStr != null && manualEndDateStr != null) {
                try {
                    startDate = LocalDate.parse(manualStartDateStr);
                    endDate = LocalDate.parse(manualEndDateStr);
                    dateRangeSourceInfo = "manual date range from Consul";
                } catch (java.time.format.DateTimeParseException e) {
                    LOGGER.error("Error parsing manual dates from Consul, falling back to 24 hours.", e);
                }
            } else {
                LOGGER.warn("Manual date range flag is enabled but dates are not configured in Consul, falling back to 24 hours");
            }
        }

        LOGGER.info("Using {}: {} to {}", dateRangeSourceInfo, startDate, endDate);
        LOGGER.info("Fetching influencer data from {} to {}", startDate, endDate);
        fetchEmailsAndPublish(startDate, endDate);
    }

    /**
     * Core logic to call third-party API, extract emails, and push to Pub/Sub.
     */
    public void fetchEmailsAndPublish(LocalDate startDate, LocalDate endDate) {
        boolean useKafka = ServiceConfigs.getKafkaForInfluencerPortalFeature();
        LOGGER.info("useKafka flag is " + useKafka);
        LOGGER.info(" inside fetchEmailsAndPublishtokenUrl: " );
        String tokenUrl = ServiceConfigs.getInfluencerTokenUrl();
        String influencerApiBaseUrl = ServiceConfigs.getInfluencerPortalUrl();
        String apiKey = ServiceConfigs.getApiKey();
        String clientId = ServiceConfigs.getInfluencerClientId();
        String clientSecret = ServiceConfigs.getInfluencerClientSecret();
        
        RestTemplate restTemplate = new RestTemplate();

        LOGGER.info("useKafka flag is " + useKafka);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("grant_type", "client_credentials");
        body.add("scope", "https://graph.microsoft.com/.default");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> tokenResponse = restTemplate.postForEntity(tokenUrl, request, String.class);

            if (!tokenResponse.getStatusCode().is2xxSuccessful() || tokenResponse.getBody() == null) {
                LOGGER.error("Failed to get access token. Response: " + tokenResponse);
                return;
            }

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode tokenJson = objectMapper.readTree(tokenResponse.getBody());
            String accessToken = tokenJson.get("access_token").asText();

            LOGGER.info("Successfully received access token.");

            String influencerUrl = influencerApiBaseUrl + "?startDate=" + startDate + "&endDate=" + endDate;

            HttpHeaders influencerHeaders = new HttpHeaders();
            influencerHeaders.setBearerAuth(accessToken);
            influencerHeaders.set("X-API-KEY", apiKey);
            HttpEntity<Void> influencerRequest = new HttpEntity<>(influencerHeaders);

            ResponseEntity<InfluencerResponse> response = restTemplate.exchange(
                    influencerUrl, HttpMethod.GET, influencerRequest, InfluencerResponse.class
            );

            List<Influencer> influencers = response.getBody().getBody();
            LOGGER.info("Number of influencers received: " + influencers.size());
            LOGGER.info("All influencers: " + new ObjectMapper().writeValueAsString(influencers));

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<String> emails = influencers.stream()
                        .map(Influencer::getEmailAddress)
                        .filter(email -> email != null && !email.trim().isEmpty())
                        .distinct()
                        .toList();

                LOGGER.info("Emails extracted: " + emails);

                if (emails.isEmpty()) {
                    LOGGER.info("No valid influencer emails found.");
                    return;
                }

                String emailCsv = String.join(",", emails);
                LOGGER.info("Publishing influencer emails: " + emailCsv);

                if (useKafka) {
                    kafkaService.publishInfluencerEmailsToKafka(new InfluencerEmailPayload(emailCsv));
                    LOGGER.info("Published emails to Kafka.");
                } else {
                    pubSubServiceImpl.publishEmails(emails);
                    LOGGER.info("Published emails via Pub/Sub.");
                }
            } else {
                LOGGER.info("Failed to fetch influencer data. Status " + response.getStatusCode());
            }

        } catch (Exception e) {
            LOGGER.info("Exception occurred while fetching influencer data:" + e);
        }
    }

    public void sendInfluencerToBraze(String email) {
        try {
            String brazeUrl = Constants.getConsulConfigResponse().getBrazeWishlistUrl();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(Constants.getConsulConfigResponse().getBrazeWishlistToken());
            Map<String, Object> userAttributes = new HashMap<>();
            userAttributes.put("email", email);
            userAttributes.put("is_influencer", true);

            Map<String, Object> body = new HashMap<>();
            body.put("attributes", List.of(userAttributes));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(brazeUrl, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getStatusCode().is2xxSuccessful()) {
                LOGGER.info("Successfully sent influencer flag to Braze for email: " + email);
            } else {
                LOGGER.info(
                        "Braze responded with status: " + response.getStatusCode() + ", body: " + response.getBody());
            }
        } catch (Exception ex) {
            LOGGER.info("Failed to send influencer data to Braze for email: " + ex);
        }
    }
}
