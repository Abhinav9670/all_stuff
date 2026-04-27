package org.styli.services.order.helper;

import java.math.BigDecimal;
import java.util.Collections;

import org.apache.commons.lang.StringUtils;
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.pojo.CreateRetryPaymentReplicaDTO;
import org.styli.services.order.pojo.QuoteDTO;
import org.styli.services.order.pojo.order.CreateRetryPaymentReplicaV4Request;
import org.styli.services.order.pojo.quote.response.GetQuoteResponse;
import org.styli.services.order.pojo.quote.response.GetQuoteV5Request;
import org.styli.services.order.pojo.quote.response.QuoteUpdateDTOV2;
import org.styli.services.order.pojo.quote.response.QuoteV7Response;
import org.styli.services.order.pojo.request.AddToQuoteV4Request;
import org.styli.services.order.utility.Constants;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Umesh, 24/09/2020
 * @project product-service
 */
@Component
public class ExternalQuoteHelper {

	private static final Log LOGGER = LogFactory.getLog(ExternalQuoteHelper.class);

	private static final String USER_AGENT = "user-agent";
	private static final String TOKEN = "Token";
	private static final String X_HEADER_TOKEN = "x-header-token";
	
	@Autowired
	@Qualifier("withoutEureka")
	private RestTemplate restTemplate;

	@Value("${quote.service.base.url}")
	String quoteServiceBaseUrl;

	@Value("${order.jwt.flag}")
	String jwtFlag;

	private static final ObjectMapper mapper = new ObjectMapper();

	public GetQuoteResponse fetchQuote(String quoteId, Integer customerId, Integer storeId, String tokenHeader,
			boolean unprocessed, String xHeaderToken, String xSource, String xClientVersion , boolean isRetryPayment, String deviceId) {

		String url = quoteServiceBaseUrl + "/rest/quote/auth/v6/get";
		if (unprocessed)
			url = quoteServiceBaseUrl + "/rest/quote/auth/v5/get/unprocessed";

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);

		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.set(TOKEN, tokenHeader);
		requestHeaders.set(X_HEADER_TOKEN, xHeaderToken);
		requestHeaders.set("x-source", xSource);
		requestHeaders.set("x-client-version", xClientVersion);
		if(StringUtils.isNotBlank(deviceId) && StringUtils.isNotEmpty(deviceId)){
			requestHeaders.add("device-id", deviceId);
		}
		GetQuoteV5Request payload = new GetQuoteV5Request();
		payload.setBagView(0);
		payload.setCustomerId(customerId);
		payload.setQuoteId(quoteId);
		payload.setStoreId(storeId);
		payload.setOrderCreation(true);
		payload.setRetryPayment(isRetryPayment);

		ResponseEntity<GetQuoteResponse> response = null;
		HttpEntity<GetQuoteV5Request> requestBody = new HttpEntity<>(payload, requestHeaders);

		try {
			LOGGER.info("Quote URL:" + url);
			LOGGER.info("request Body: " + mapper.writeValueAsString(requestBody.getBody()));

			response = restTemplate.exchange(url, HttpMethod.POST, requestBody, GetQuoteResponse.class);

			LOGGER.info("respone fetched successfully!!");
			LOGGER.info("response Body: " + mapper.writeValueAsString(response.getBody()));

		} catch (RestClientException | JsonProcessingException e) {

			LOGGER.error("Exception occurred during get quote:" + e.getMessage());

		}
		if (null != response)
			return response.getBody();
		else {

			return null;
		}

	}


	public QuoteV7Response fetchQuotev7(String quoteId, Integer customerId, Integer storeId, String tokenHeader,
										boolean unprocessed, String xHeaderToken, String xSource, String xClientVersion , boolean isRetryPayment, String deviceId) {

		String url = quoteServiceBaseUrl + "/rest/quote/auth/v7/get";
		if (unprocessed)
			url = quoteServiceBaseUrl + "/rest/quote/auth/v5/get/unprocessed";

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);

		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.set(TOKEN, tokenHeader);
		requestHeaders.set(X_HEADER_TOKEN, xHeaderToken);
		requestHeaders.set("x-source", xSource);
		requestHeaders.set("x-client-version", xClientVersion);
		if(StringUtils.isNotBlank(deviceId) && StringUtils.isNotEmpty(deviceId)){
			requestHeaders.add("device-id", deviceId);
		}
		GetQuoteV5Request payload = new GetQuoteV5Request();
		payload.setBagView(0);
		payload.setCustomerId(customerId);
		payload.setQuoteId(quoteId);
		payload.setStoreId(storeId);
		payload.setOrderCreation(true);
		payload.setRetryPayment(isRetryPayment);

		ResponseEntity<QuoteV7Response> response = null;
		HttpEntity<GetQuoteV5Request> requestBody = new HttpEntity<>(payload, requestHeaders);

		try {
			LOGGER.info("Quote URL:" + url);
			LOGGER.info("request Body: " + mapper.writeValueAsString(requestBody.getBody()));

			response = restTemplate.exchange(url, HttpMethod.POST, requestBody, QuoteV7Response.class);

			LOGGER.info("respone fetched successfully!!");
			LOGGER.info("response Body: " + mapper.writeValueAsString(response.getBody()));

		} catch (RestClientException | JsonProcessingException e) {

			LOGGER.error("Exception occurred during get quote:" + e.getMessage());

		}
		if (null != response)
			return response.getBody();
		else {

			return null;
		}

	}

	public void disableExternalQuote(QuoteDTO quote, String tokenHeader, String xHeaderToken, String deviceId) {

		String url = quoteServiceBaseUrl + "/rest/quote/auth/v5/disable";

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.set(TOKEN, tokenHeader);
		if(StringUtils.isNotBlank(deviceId) && StringUtils.isNotEmpty(deviceId)){
			requestHeaders.add("device-id", deviceId);
		}
		GetQuoteV5Request payload = new GetQuoteV5Request();
		payload.setCustomerId(null);
		payload.setQuoteId(quote.getQuoteId());
		payload.setStoreId(Integer.parseInt(quote.getStoreId()));
		payload.setRetryPayment(false);
		requestHeaders.set(X_HEADER_TOKEN, xHeaderToken);

		if ("1".equals(jwtFlag) && StringUtils.isNotBlank(quote.getCustomerId()) && Constants.IS_JWT_TOKEN_ENABLE) {
			payload.setCustomerId(Integer.parseInt(quote.getCustomerId()));
		}

		HttpEntity<GetQuoteV5Request> requestBody = new HttpEntity<>(payload, requestHeaders);

		try {

			LOGGER.info("Disable Quote URL:" + url);
			LOGGER.info("request Body :" + mapper.writeValueAsString(requestBody.getBody()));

			ResponseEntity<GetQuoteResponse> response = restTemplate.exchange(url, HttpMethod.POST, requestBody,
					GetQuoteResponse.class);

			LOGGER.info("disable quote response fetched successfully!!");
			LOGGER.info("response Body :" + mapper.writeValueAsString(response.getBody()));

		} catch (RestClientException e) {
			LOGGER.error("Quote disabling failed for quote: " + quote.getQuoteId());
		} catch (Exception e) {
			LOGGER.error("Quote disabling failed in json parsing for quote: " + quote.getQuoteId());

		}

	}

	public QuoteUpdateDTOV2 addToQuote(AddToQuoteV4Request request, String tokenHeader, String xHeaderToken, String deviceId) {

		String url = quoteServiceBaseUrl + "/rest/quote/auth/v5";

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.set(TOKEN, tokenHeader);
		requestHeaders.set(X_HEADER_TOKEN, xHeaderToken);
		if(StringUtils.isNotBlank(deviceId) && StringUtils.isNotEmpty(deviceId)){
			requestHeaders.add("device-id", deviceId);
		}
		HttpEntity<AddToQuoteV4Request> requestBody = new HttpEntity<>(request, requestHeaders);

		try {
			LOGGER.info("Quote request Body:" + mapper.writeValueAsString(requestBody.getBody()));

			ResponseEntity<QuoteUpdateDTOV2> response = restTemplate.exchange(url, HttpMethod.POST, requestBody,
					QuoteUpdateDTOV2.class);

			LOGGER.info("Add to quote response fetched successfully!!");
			LOGGER.info("Quote response Body:" + mapper.writeValueAsString(response.getBody()));
			return response.getBody();
		} catch (RestClientException e) {
			LOGGER.error("External addToQuote rest call failed!");
			return null;
		} catch (Exception e) {
			LOGGER.error("External addToQuote rest call failed in converting json!");
			return null;
		}

	}

	public QuoteUpdateDTOV2 enableExternalQuote(String quoteId, Integer storeId, String tokenHeader, String deviceId) {

		String url = quoteServiceBaseUrl + "/rest/quote/auth/v5/replica";

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.set(TOKEN, tokenHeader);
		requestHeaders.set(X_HEADER_TOKEN, Constants.X_HEADER_TOKEN);
		if(StringUtils.isNotBlank(deviceId) && StringUtils.isNotEmpty(deviceId)){
			requestHeaders.add("device-id", deviceId);
		}
		GetQuoteV5Request payload = new GetQuoteV5Request();
		payload.setCustomerId(null);
		payload.setQuoteId(quoteId);
		payload.setStoreId(storeId);

		HttpEntity<GetQuoteV5Request> requestBody = new HttpEntity<>(payload, requestHeaders);

		try {

			LOGGER.info("Enable external Quote URL:" + url);
			LOGGER.info("request Body:" + mapper.writeValueAsString(requestBody.getBody()));

			ResponseEntity<QuoteUpdateDTOV2> response = restTemplate.exchange(url, HttpMethod.POST, requestBody,
					QuoteUpdateDTOV2.class);

			LOGGER.info("Enable external quote response fetched successfully!!");
			LOGGER.info("response Body:" + mapper.writeValueAsString(response.getBody()));
			return response.getBody();
		} catch (RestClientException e) {
			LOGGER.error("Quote enabling failed for quote: " + quoteId);
			return null;
		} catch (Exception e) {
			LOGGER.error("Quote enabling failed in converting json for quote: " + quoteId);
			return null;
		}
	}
	
	public CreateRetryPaymentReplicaDTO enableExternalQuoteForRetryPayment(String quoteId, Integer storeId, String payfortMerchantReference, String failedPaymentMethod, String tokenHeader
			, BigDecimal paidStyliCredit, String deviceId) {

		String url = quoteServiceBaseUrl + "/rest/quote/auth/v6/retry-payment/replica";

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.set(TOKEN, tokenHeader);
		requestHeaders.set(X_HEADER_TOKEN, Constants.X_HEADER_TOKEN);
		if(StringUtils.isNotBlank(deviceId) && StringUtils.isNotEmpty(deviceId)){
			requestHeaders.add("device-id", deviceId);
		}
		CreateRetryPaymentReplicaV4Request payload = new CreateRetryPaymentReplicaV4Request();
		payload.setQuoteId(quoteId);
		payload.setStoreId(storeId);
		payload.setFailedPaymentMethod(failedPaymentMethod);
		payload.setPayfortMerchantReference(payfortMerchantReference);
		if (null != paidStyliCredit) {
		payload.setPaidStyliCredit(paidStyliCredit.toString());
		}

		HttpEntity<CreateRetryPaymentReplicaV4Request> requestBody = new HttpEntity<>(payload, requestHeaders);

		try {

			LOGGER.info("Retry Payment Enable external Quote URL:" + url);
			LOGGER.info("Retry Payment request Body:" + mapper.writeValueAsString(requestBody.getBody()));

			ResponseEntity<CreateRetryPaymentReplicaDTO> response = restTemplate.exchange(url, HttpMethod.POST, requestBody,
					CreateRetryPaymentReplicaDTO.class);

			LOGGER.info("Retry Payment Enable external quote response fetched successfully!!");
			LOGGER.info("Retry Payment response Body:" + mapper.writeValueAsString(response.getBody()));
			return response.getBody();
		} catch (RestClientException e) {
			LOGGER.error("Retry Payment Quote enabling failed for quote: " + quoteId);
			return null;
		} catch (Exception e) {
			LOGGER.error("Retry Payment Quote enabling failed in converting json for quote: " + quoteId);
			return null;
		}
	}

	public void saveStoreCredit(Integer customerId, BigDecimal storeCredit, String tokenHeader, String deviceId) {
		String url = quoteServiceBaseUrl + "/rest/quote/auth/v5/savestorecredit";

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.set(TOKEN, tokenHeader);
		if(StringUtils.isNotBlank(deviceId) && StringUtils.isNotEmpty(deviceId)){
			requestHeaders.add("device-id", deviceId);
		}
		GetQuoteV5Request payload = new GetQuoteV5Request();
		payload.setCustomerId(customerId);
		payload.setStoreCredit(storeCredit);

		HttpEntity<GetQuoteV5Request> requestBody = new HttpEntity<>(payload, requestHeaders);

		try {

			LOGGER.info("SaveStoreCredit external Quote URL:" + url);
			LOGGER.info("request Body:" + mapper.writeValueAsString(requestBody.getBody()));

			ResponseEntity<QuoteUpdateDTOV2> response = restTemplate.exchange(url, HttpMethod.POST, requestBody,
					QuoteUpdateDTOV2.class);

			LOGGER.info("SaveStoreCredit external Quote fetched successfully!!");
			LOGGER.info("response Body:" + mapper.writeValueAsString(response.getBody()));

		} catch (RestClientException e) {
			LOGGER.error("SaveStoreCredit external Quote for customer: " + customerId);

		} catch (Exception e) {
			LOGGER.error("SaveStoreCredit external Quote failed in converting json for customer: " + customerId);

		}

	}
}
