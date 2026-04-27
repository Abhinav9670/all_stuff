package org.styli.services.order.service;

import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.styli.services.order.pojo.NavikDetails;
import org.styli.services.order.pojo.request.GetShipmentV3.alpha.Auth;
import org.styli.services.order.utility.Constants;

/**
 * Shared Alpha/Logistiq API access: JWT lifecycle and upload-shipment-document.
 */
@Component
public class LogistiqApiClient {

	private static final Log LOGGER = LogFactory.getLog(LogistiqApiClient.class);
	private static final String BEARER_PREFIX = "Bearer ";
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final RestTemplate restTemplate;

	@Value("${logistiq.upload-shipment-document-path:/v1/orders/upload-shipment-document}")
	private String uploadShipmentDocumentPath;

	@Autowired
	public LogistiqApiClient(@Qualifier("restTemplateBuilder") RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	/**
	 * Returns a usable Alpha JWT, refreshing via login when missing or near expiry.
	 */
	public String getFreshAlphaToken() {
		String alphaToken = Constants.getAlphaToken();
		if (StringUtils.isNotBlank(alphaToken) && !isAlphaTokenExpired(alphaToken)) {
			return alphaToken;
		}
		LOGGER.info("Alpha auth token expired or missing. Refreshing token");
		String refreshed = refreshAlphaTokenViaApi();
		return refreshed != null ? refreshed : alphaToken;
	}

	/**
	 * POST /v1/orders/upload-shipment-document (invoice, National ID, etc.).
	 *
	 * @param logFullPayload when true, logs the full JSON body (omit for large/sensitive payloads in hot paths)
	 */
	public boolean uploadShipmentDocument(Map<String, Object> document, boolean logFullPayload) {
		try {
			if (Constants.orderCredentials == null || Constants.orderCredentials.getOrderDetails() == null) {
				LOGGER.error("Order credentials not configured");
				return false;
			}
			String alphaBaseUrl = Constants.orderCredentials.getOrderDetails().getAlphaBaseUrl();
			if (StringUtils.isBlank(alphaBaseUrl)) {
				LOGGER.error("Alpha base URL is not configured");
				return false;
			}
			String url = alphaBaseUrl + uploadShipmentDocumentPath;
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
			headers.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
			String authToken = getFreshAlphaToken();
			if (StringUtils.isBlank(authToken)) {
				LOGGER.error("Alpha authentication token is not available");
				return false;
			}
			headers.add(Constants.AUTHORIZATION_HEADER, BEARER_PREFIX + authToken);
			HttpEntity<Map<String, Object>> request = new HttpEntity<>(document, headers);
			LOGGER.info("Logistiq upload-shipment-document: " + url);
			if (logFullPayload) {
				logDocumentPayload(document);
			}
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
			boolean success = response.getStatusCode().is2xxSuccessful();
			LOGGER.info("Logistiq upload-shipment-document response - Status: " + response.getStatusCode()
					+ ", Success: " + success + ", Body: " + response.getBody());
			return success;
		} catch (RestClientException e) {
			LOGGER.error("RestClientException calling Logistiq upload-shipment-document API", e);
			return false;
		} catch (Exception e) {
			LOGGER.error("Exception calling Logistiq upload-shipment-document API", e);
			return false;
		}
	}

	private void logDocumentPayload(Map<String, Object> document) {
		try {
			LOGGER.info("Request payload: " + OBJECT_MAPPER.writeValueAsString(document));
		} catch (Exception e) {
			LOGGER.warn("Could not serialize request payload for logging: " + e.getMessage());
			LOGGER.info("Request payload (awb only): " + document.get("awb"));
		}
	}

	private String refreshAlphaTokenViaApi() {
		try {
			NavikDetails navik = Constants.orderCredentials != null ? Constants.orderCredentials.getNavik() : null;
			if (navik == null || StringUtils.isBlank(navik.getAlphaUsername()) || StringUtils.isBlank(navik.getAlphaPassword())) {
				LOGGER.error("Alpha credentials not configured");
				return null;
			}
			if (Constants.orderCredentials.getOrderDetails() == null
					|| StringUtils.isBlank(Constants.orderCredentials.getOrderDetails().getAlphaBaseUrl())) {
				LOGGER.error("Alpha base URL is not configured");
				return null;
			}
			String loginUrl = Constants.orderCredentials.getOrderDetails().getAlphaBaseUrl() + "/v1/accounts/login";
			Auth auth = new Auth(navik.getAlphaUsername(), navik.getAlphaPassword());
			HttpHeaders requestHeaders = new HttpHeaders();
			requestHeaders.setContentType(MediaType.APPLICATION_JSON);
			requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
			ResponseEntity<Auth> response = restTemplate.exchange(loginUrl, HttpMethod.POST, new HttpEntity<>(auth, requestHeaders), Auth.class);
			return extractAndStoreAlphaToken(response);
		} catch (Exception e) {
			LOGGER.error("Error refreshing Alpha Authentication Token", e);
			return null;
		}
	}

	private String extractAndStoreAlphaToken(ResponseEntity<Auth> response) {
		if (response == null) {
			return null;
		}
		if (!response.getStatusCode().equals(HttpStatus.OK) || response.getBody() == null) {
			LOGGER.error("Error refreshing Alpha token. Response Code: " + response.getStatusCode());
			return null;
		}
		String token = response.getBody().getToken();
		if (StringUtils.isBlank(token)) {
			LOGGER.error("Alpha token is blank in response");
			return null;
		}
		Constants.setAlphaToken(token);
		LOGGER.info("Successfully refreshed Alpha token");
		return token;
	}

	private boolean isAlphaTokenExpired(String token) {
		try {
			String[] chunks = token.split("\\.");
			if (chunks.length < 2) {
				LOGGER.warn("Invalid JWT token format");
				return true;
			}
			Base64.Decoder decoder = Base64.getUrlDecoder();
			String payload = new String(decoder.decode(chunks[1]));
			JsonObject jsonObj = JsonParser.parseString(payload).getAsJsonObject();
			long expTime = jsonObj.get("exp").getAsLong();
			long timeDiff = (expTime * 1000) - new Date().getTime();
			long minutesDiff = TimeUnit.MILLISECONDS.toMinutes(timeDiff);
			return minutesDiff < 10;
		} catch (Exception e) {
			LOGGER.error("Error checking Alpha JWT token expiration", e);
			return true;
		}
	}
}
