package org.styli.services.order.helper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.pojo.braze.BrazePendingPaymentEvent;
import org.styli.services.order.pojo.braze.BrazePendingPaymentPush;
import org.styli.services.order.pojo.braze.BrazePendingPaymentRequestBody;
import org.styli.services.order.pojo.braze.BrazePushAttribute;
import org.styli.services.order.pojo.braze.BrazePushAttributeRequestBody;
import org.styli.services.order.pojo.braze.BrazeResponseBody;
import org.styli.services.order.pojo.braze.BrazeWalletUpdateEvent;
import org.styli.services.order.pojo.braze.BrazeWalletUpdateEventProperty;
import org.styli.services.order.pojo.braze.BrazeWalletUpdateEventRequestBody;
import org.styli.services.order.pojo.kafka.BulkWalletUpdateAllString;
import org.styli.services.order.pojo.order.StoreCredit;
import org.styli.services.order.pojo.response.StoreCreditResponse;
import org.styli.services.order.utility.Constants;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

/**
 * @author umesh.mahato@landmarkgroup.com
 * @project order-service
 * @created 24/02/2022 - 11:09 AM
 */

@Component
public class KafkaBrazeHelper {

    private static final String USERS_TRACK = "/users/track";

	private static final Log LOGGER = LogFactory.getLog(KafkaBrazeHelper.class);

    private static final ObjectMapper mapper = new ObjectMapper();
    
    private static final String CREDIT_UPLOAD_FAILED = "credit_upload_failed";

    @Autowired
    @Qualifier("withoutEureka")
    private RestTemplate restTemplate;

    @Value("${braze.auth.token}")
    private String brazeAuthToken;

    public void sendErrorsListToBraze(List<BulkWalletUpdateAllString> errorsList) {

        try {
            List<BrazeWalletUpdateEvent> events = errorsList
                    .stream()
                    .filter(e -> ObjectUtils.isNotEmpty(e.getCustomerId()) && ObjectUtils.isNotEmpty(e.getAmount_to_be_refunded()))
                    .map(e -> {
                        BrazeWalletUpdateEvent event = new BrazeWalletUpdateEvent();
                        event.setName(CREDIT_UPLOAD_FAILED);
                        event.setExternalId(String.valueOf(e.getCustomerId()));
                        event.setTime(String.valueOf(Instant.now()));
                        BrazeWalletUpdateEventProperty property = new BrazeWalletUpdateEventProperty();
                        property.setAmount(e.getAmount_to_be_refunded());
                        event.setProperties(property);
                        return event;
                    }).collect(Collectors.toList());

            sendEventsToBraze(events);

        } catch (Exception e) {
            LOGGER.error("Error occurred while sending errors to braze " + e);
        }

    }

    public void sendResultToBraze(List<StoreCreditResponse> successRows, List<StoreCreditResponse> failedRows) {

        try {
            List<BrazeWalletUpdateEvent> events = new ArrayList<>();

            if (CollectionUtils.isNotEmpty(successRows)) {
                for (StoreCreditResponse scr : successRows) {
                    StoreCredit sc = scr.getActualRequest();
                    if (ObjectUtils.isNotEmpty(sc.getCustomerId())
                            && ObjectUtils.isNotEmpty(sc.getStoreCredit())) {
                        BrazeWalletUpdateEvent event = new BrazeWalletUpdateEvent();
                        event.setTime(String.valueOf(Instant.now()));
                        event.setName("credit_upload_success");
                        event.setExternalId(String.valueOf(sc.getCustomerId()));
                        BrazeWalletUpdateEventProperty property = new BrazeWalletUpdateEventProperty();
                        property.setAmount(sc.getStoreCredit().toString());
                        event.setProperties(property);
                        events.add(event);
                    }
                }
            }

            if (CollectionUtils.isNotEmpty(failedRows)) {
                for (StoreCreditResponse scr : failedRows) {
                    StoreCredit sc = scr.getActualRequest();
                    if (ObjectUtils.isNotEmpty(sc.getCustomerId())
                            && ObjectUtils.isNotEmpty(sc.getStoreCredit())) {
                        BrazeWalletUpdateEvent event = new BrazeWalletUpdateEvent();
                        event.setTime(String.valueOf(Instant.now()));
                        event.setName(CREDIT_UPLOAD_FAILED);
                        event.setExternalId(String.valueOf(sc.getCustomerId()));
                        BrazeWalletUpdateEventProperty property = new BrazeWalletUpdateEventProperty();
                        property.setAmount(sc.getStoreCredit().toString());
                        event.setProperties(property);
                        events.add(event);
                    }
                }
            }

            sendEventsToBraze(events);

        } catch (Exception e) {
            LOGGER.error("Error occurred while sending results to braze " + e);
        }

    }

    public void sendExceptionListToBraze(List<StoreCredit> storeCredits) {

        try {
            List<BrazeWalletUpdateEvent> events = storeCredits
                    .stream()
                    .filter(e -> ObjectUtils.isNotEmpty(e.getCustomerId()) && ObjectUtils.isNotEmpty(e.getStoreCredit()))
                    .map(e -> {
                        BrazeWalletUpdateEvent event = new BrazeWalletUpdateEvent();
                        event.setName(CREDIT_UPLOAD_FAILED);
                        event.setExternalId(String.valueOf(e.getCustomerId()));
                        event.setTime(String.valueOf(Instant.now()));
                        BrazeWalletUpdateEventProperty property = new BrazeWalletUpdateEventProperty();
                        property.setAmount(e.getStoreCredit().toString());
                        event.setProperties(property);
                        return event;
                    }).collect(Collectors.toList());

            sendEventsToBraze(events);
        } catch (Exception e) {
            LOGGER.error("Error occurred while sending exceptions to braze " + e);
        }

    }

	private void sendEventsToBraze(List<BrazeWalletUpdateEvent> events) {
		if (CollectionUtils.isNotEmpty(events)) {
			Lists.partition(events, 50).stream().forEach(this::triggerBrazeEvent);
		}
	}
    
    private void triggerBrazeEvent(List<BrazeWalletUpdateEvent> events) {

		BrazeWalletUpdateEventRequestBody request = new BrazeWalletUpdateEventRequestBody();
		request.setEvents(events);

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.add(Constants.AUTHORIZATION_HEADER_KEY, Constants.BEARER_HEADER_KEY + brazeAuthToken);

		HttpEntity<BrazeWalletUpdateEventRequestBody> requestHttpEntity = new HttpEntity<>(request, requestHeaders);

		String url = "";
		if (ObjectUtils.isNotEmpty(Constants.orderCredentials)
				&& ObjectUtils.isNotEmpty(Constants.orderCredentials.getOrderDetails().getBrazeServiceBaseUrl())) {
			url = Constants.orderCredentials.getOrderDetails().getBrazeServiceBaseUrl() + USERS_TRACK;
		}
		if (ObjectUtils.isEmpty(url)) {
			LOGGER.error(" Braze base url not found! ");
		} else {
			LOGGER.info(" Braze submit Url: " + url);
			try {
				LOGGER.info("Braze event request body:" + mapper.writeValueAsString(requestHttpEntity.getBody()));
				ResponseEntity<BrazeResponseBody> apiResponse = restTemplate.exchange(url, HttpMethod.POST,
						requestHttpEntity, BrazeResponseBody.class);
				if (ObjectUtils.isNotEmpty(apiResponse) && ObjectUtils.isNotEmpty(apiResponse.getBody())) {
					LOGGER.info("Braze event response body:" + mapper.writeValueAsString(apiResponse.getBody()));
				}
			} catch (JsonProcessingException e) {
				LOGGER.error("Error from Braze event submit:" + e.getMessage());
			}
		}
	}


    public void sendAttributesToBraze(List<BrazePushAttribute> attributes) {
        if (CollectionUtils.isNotEmpty(attributes)) {

            BrazePushAttributeRequestBody request = new BrazePushAttributeRequestBody();
            request.setAttributes(attributes);

            HttpHeaders requestHeaders = new HttpHeaders();
            requestHeaders.setContentType(MediaType.APPLICATION_JSON);
            requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
            requestHeaders.add(Constants.AUTHORIZATION_HEADER_KEY, Constants.BEARER_HEADER_KEY + brazeAuthToken);

            HttpEntity<BrazePushAttributeRequestBody> requestHttpEntity = new HttpEntity<>(request, requestHeaders);

            String url = "";
            if (ObjectUtils.isNotEmpty(Constants.orderCredentials)
                    && ObjectUtils.isNotEmpty(Constants.orderCredentials.getOrderDetails().getBrazeServiceBaseUrl())) {
                url = Constants.orderCredentials.getOrderDetails().getBrazeServiceBaseUrl() + USERS_TRACK;
            }
            if (ObjectUtils.isEmpty(url)) {
                LOGGER.error("Braze base url not found!");
            } else {
                LOGGER.info("Braze submit Url:" + url);
                try {
                    LOGGER.info("Braze attribute submit request body:" + mapper.writeValueAsString(requestHttpEntity.getBody()));
                    ResponseEntity<BrazeResponseBody> apiResponse
                            = restTemplate.exchange(url, HttpMethod.POST, requestHttpEntity, BrazeResponseBody.class);
                    if(ObjectUtils.isNotEmpty(apiResponse) && ObjectUtils.isNotEmpty(apiResponse.getBody())) {
                        LOGGER.info("Braze attribute submit response body:" + mapper.writeValueAsString(apiResponse.getBody()));
                    }
                } catch (JsonProcessingException e) {
                    LOGGER.error("Error from Braze attribute submit:" + e.getMessage());
                }
            }

        }

    }
    
    public void sendPendingPaymentToBraze(List<BrazePendingPaymentEvent> attributes) {
        if (CollectionUtils.isNotEmpty(attributes)) {

        	BrazePendingPaymentRequestBody request = new BrazePendingPaymentRequestBody();
            request.setEvents(attributes);

            HttpHeaders requestHeaders = new HttpHeaders();
            requestHeaders.setContentType(MediaType.APPLICATION_JSON);
            requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
            requestHeaders.add(Constants.AUTHORIZATION_HEADER_KEY, Constants.BEARER_HEADER_KEY + brazeAuthToken);

            HttpEntity<BrazePendingPaymentRequestBody> requestHttpEntity = new HttpEntity<>(request, requestHeaders);

            String url = "";
            if (ObjectUtils.isNotEmpty(Constants.orderCredentials)
                    && ObjectUtils.isNotEmpty(Constants.orderCredentials.getOrderDetails().getBrazeServiceBaseUrl())) {
                url = Constants.orderCredentials.getOrderDetails().getBrazeServiceBaseUrl() + USERS_TRACK;
            }
            if (ObjectUtils.isEmpty(url)) {
                LOGGER.error("Braze base url not found!");
            } else {
                LOGGER.info("Braze submit Url:" + url);
                try {
                    LOGGER.info("Braze attribute submit request body:" + mapper.writeValueAsString(requestHttpEntity.getBody()));
                    ResponseEntity<BrazeResponseBody> apiResponse
                            = restTemplate.exchange(url, HttpMethod.POST, requestHttpEntity, BrazeResponseBody.class);
                    if(ObjectUtils.isNotEmpty(apiResponse) && ObjectUtils.isNotEmpty(apiResponse.getBody())) {
                        LOGGER.info("Braze attribute submit response body:" + mapper.writeValueAsString(apiResponse.getBody()));
                    }
                } catch (JsonProcessingException e) {
                    LOGGER.error("Error from Braze attribute submit:" + e.getMessage());
                }
            }

        }

    }

}
