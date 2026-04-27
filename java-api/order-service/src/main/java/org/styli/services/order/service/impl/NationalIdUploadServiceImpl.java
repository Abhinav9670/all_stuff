package org.styli.services.order.service.impl;

import java.sql.Timestamp;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.persistence.criteria.Predicate;

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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import org.styli.services.order.helper.LogistiqShipmentHelper;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderAddress;
import org.styli.services.order.model.sales.SalesShipmentTrack;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderAddressRepository;
import org.styli.services.order.repository.SalesOrder.ShipmentTrackerRepository;
import org.styli.services.order.service.LogistiqApiClient;
import org.styli.services.order.service.NationalIdUploadService;
import org.styli.services.order.utility.Constants;

/**
 * National ID: fetch from Customer Service, convert to Base64, upload to Logistiq via {@link LogistiqApiClient}.
 */
@Component
public class NationalIdUploadServiceImpl implements NationalIdUploadService {

	private static final Log LOGGER = LogFactory.getLog(NationalIdUploadServiceImpl.class);
	private static final String STATUS_PENDING = "PENDING";
	private static final String STATUS_SUCCESS = "SUCCESS";
	private static final String STATUS_FAILED = "FAILED";
	private static final int MAX_ERROR_MESSAGE_LENGTH = 500;
	private static final String LOG_CUSTOMER_ID = ", customerId: ";
	/** Country code for Oman; National ID upload is only for Oman orders. */
	private static final String COUNTRY_OMAN = "OM";

	@Value("${customer.service.national-id-path:/rest/customer/document/national-id}")
	private String customerServiceNationalIdPath;

	@Value("${auth.internal.header.bearer.token:}")
	private String internalHeaderBearerToken;

	@Autowired
	@Qualifier("restTemplateBuilder")
	private RestTemplate restTemplate;

	@Autowired
	private ShipmentTrackerRepository shipmentTrackerRepository;

	@Autowired
	private SalesOrderAddressRepository salesOrderAddressRepository;

	@Autowired
	private SalesOrderRepository salesOrderRepository;

	@Autowired
	private LogistiqApiClient logistiqApiClient;

	@Override
	@Async("asyncExecutor")
	@Transactional
	public void scheduleNationalIdUploadAsync(String awbNumber, SalesOrder order, SalesShipmentTrack track) {
		if (order == null || track == null) {
			LOGGER.warn("scheduleNationalIdUploadAsync skipped: order or track is null, AWB: " + awbNumber);
			return;
		}
		if (!isAlphaBaseUrlConfigured()) {
			LOGGER.warn("Alpha base URL not configured, skipping National ID upload for AWB: " + awbNumber);
			return;
		}
		if (!isOmanOrder(order)) {
			LOGGER.info("Skipping National ID upload - order is not from Oman (AWB: " + awbNumber + ")");
			return;
		}
		SalesShipmentTrack managedTrack = shipmentTrackerRepository.findById(track.getEntityId()).orElse(track);
		try {
			runNationalIdUploadFlow(awbNumber, order, managedTrack);
		} catch (Exception e) {
			LOGGER.error("Error in National ID upload for AWB: " + awbNumber, e);
			managedTrack.setNationalIdUploadStatus(STATUS_FAILED);
			managedTrack.setNationalIdUploadError(truncateErrorMessage(e.getMessage()));
			shipmentTrackerRepository.saveAndFlush(managedTrack);
		}
	}

	/**
	 * Processes tracks that have an AWB but National ID upload is still null or PENDING (batch of up to 100).
	 * Intended to be called from a scheduler when configured; National ID can also be triggered asynchronously
	 * from get-shipment-v3 via {@link #scheduleNationalIdUploadAsync}.
	 */
	@Override
	public void processPendingNationalIdUploads() {
		if (!isAlphaBaseUrlConfigured()) {
			return;
		}
		Specification<SalesShipmentTrack> spec = (root, query, cb) -> {
			Predicate hasAwb = cb.or(
				cb.isNotNull(root.get("trackNumber")),
				cb.isNotNull(root.get("alphaAwb"))
			);
			Predicate needsUpload = cb.or(
				cb.isNull(root.get("nationalIdUploadStatus")),
				cb.equal(root.get("nationalIdUploadStatus"), STATUS_PENDING)
			);
			return cb.and(hasAwb, needsUpload);
		};
		List<SalesShipmentTrack> pending = shipmentTrackerRepository.findAll(spec, PageRequest.of(0, 100)).getContent();
		if (pending.isEmpty()) {
			return;
		}
		LOGGER.info("National ID batch upload: processing " + pending.size() + " tracks");
		for (SalesShipmentTrack track : pending) {
			try {
				String awb = track.getTrackNumber() != null ? track.getTrackNumber() : track.getAlphaAwb();
				SalesOrder order = salesOrderRepository.findById(track.getOrderId()).orElse(null);
				if (order == null || awb == null) {
					continue;
				}
				SalesShipmentTrack managed = shipmentTrackerRepository.findById(track.getEntityId()).orElse(track);
				runNationalIdUploadFlow(awb, order, managed);
			} catch (Exception e) {
				LOGGER.error("National ID batch upload: error for track " + track.getEntityId(), e);
			}
		}
	}

	/**
	 * National ID flow: resolve request -> fetch PDF as Base64 -> build payload -> upload to Logistiq -> update track.
	 */
	void runNationalIdUploadFlow(String awb, SalesOrder order, SalesShipmentTrack track) {
		LOGGER.info("National ID upload flow started - AWB: " + awb + ", orderId: " + (order != null ? order.getEntityId() : "null"));
		setNationalIdPending(track);
		NationalIdRequestDto req = resolveNationalIdRequest(order, track);
		if (req == null) {
			return;
		}
		String nationalIdBase64 = fetchNationalIdPdfAsBase64(req);
		if (StringUtils.isBlank(nationalIdBase64)) {
			LOGGER.warn("National ID PDF not received or empty for AWB: " + awb);
			track.setNationalIdUploadStatus(STATUS_FAILED);
			track.setNationalIdUploadError("Failed to fetch National ID PDF from Customer Service API");
			shipmentTrackerRepository.saveAndFlush(track);
			return;
		}
		Map<String, Object> payload = buildNationalIdDocumentPayload(awb, nationalIdBase64, order);
		boolean success = uploadNationalIdToLogistiq(payload);
		if (success) {
			track.setNationalIdUploadStatus(STATUS_SUCCESS);
			track.setNationalIdUploadedAt(new Timestamp(new Date().getTime()));
			track.setNationalIdUploadError(null);
			LOGGER.info("Successfully sent National ID to Logistiq for AWB: " + awb);
		} else {
			track.setNationalIdUploadStatus(STATUS_FAILED);
			track.setNationalIdUploadError("Logistiq API returned non-success status");
			LOGGER.warn("Failed to send National ID to Logistiq for AWB: " + awb);
		}
		shipmentTrackerRepository.saveAndFlush(track);
	}

	private void setNationalIdPending(SalesShipmentTrack track) {
		track.setNationalIdUploadStatus(STATUS_PENDING);
		shipmentTrackerRepository.saveAndFlush(track);
	}

	/** Resolves addressId, customerId, customerEmail for National ID fetch; returns null if invalid.
	 * addressId must be sales_order_address.customer_address_id (customer's address id), NOT sales_order_address.entity_id.
	 */
	NationalIdRequestDto resolveNationalIdRequest(SalesOrder order, SalesShipmentTrack track) {
		SalesOrderAddress shippingAddress = salesOrderAddressRepository.findByOrderId(order.getEntityId())
				.stream()
				.filter(addr -> "shipping".equalsIgnoreCase(addr.getAddressType()))
				.findFirst()
				.orElse(null);
		if (shippingAddress == null || shippingAddress.getCustomerAddressId() == null) {
			track.setNationalIdUploadError("Shipping address or address ID not found");
			track.setNationalIdUploadStatus(STATUS_FAILED);
			shipmentTrackerRepository.saveAndFlush(track);
			return null;
		}
		// Use customer_address_id (customer address id), not sales_order_address.entity_id
		Integer addressId = shippingAddress.getCustomerAddressId();
		Integer customerId = order.getCustomerId() != null ? order.getCustomerId() : shippingAddress.getCustomerId();
		String customerEmail = order.getCustomerEmail();
		if (addressId == null) {
			track.setNationalIdUploadError("AddressId is null");
			track.setNationalIdUploadStatus(STATUS_FAILED);
			shipmentTrackerRepository.saveAndFlush(track);
			return null;
		}
		if (customerId == null) {
			track.setNationalIdUploadError("CustomerId is null");
			track.setNationalIdUploadStatus(STATUS_FAILED);
			shipmentTrackerRepository.saveAndFlush(track);
			return null;
		}
		return new NationalIdRequestDto(addressId, customerId, customerEmail);
	}

	/** Fetches National ID PDF from Customer Service API and converts response to Base64. */
	String fetchNationalIdPdfAsBase64(NationalIdRequestDto req) {
		LOGGER.info("Fetching National ID PDF - addressId: " + req.addressId + LOG_CUSTOMER_ID + req.customerId);
		String customerServiceBaseUrl = getCustomerServiceBaseUrl();
		if (StringUtils.isBlank(customerServiceBaseUrl)) {
			LOGGER.error("Customer service base URL is not configured");
			return null;
		}
		String internalToken = parseAuthorizationToken(internalHeaderBearerToken);
		if (StringUtils.isBlank(internalToken)) {
			LOGGER.warn("Internal bearer token not available for National ID PDF fetch");
			return null;
		}
		String url = customerServiceBaseUrl + customerServiceNationalIdPath
				+ "?addressId=" + req.addressId + "&customerId=" + req.customerId;
		LOGGER.info("Calling Customer Service API - URL: " + url);
		HttpHeaders headers = buildNationalIdRequestHeaders(internalToken, req.customerEmail);
		try {
			ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
			return convertResponseToBase64(response);
		} catch (RestClientException e) {
			logAndRethrowRestClientException(e, customerServiceBaseUrl, req);
		} catch (Exception e) {
			LOGGER.error("Unexpected exception fetching National ID PDF - addressId: " + req.addressId + LOG_CUSTOMER_ID + req.customerId, e);
			throw new IllegalStateException(e.getMessage() != null ? e.getMessage() : "Failed to fetch National ID PDF from Customer Service API");
		}
		return null;
	}

	private HttpHeaders buildNationalIdRequestHeaders(String internalToken, String customerEmail) {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_PDF));
		headers.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
		headers.add(Constants.AUTH_BEARER_HEADER, internalToken);
		if (StringUtils.isNotBlank(customerEmail)) {
			headers.add("x-header-token", customerEmail);
		}
		return headers;
	}

	private String convertResponseToBase64(ResponseEntity<byte[]> response) {
		if (response.getBody() == null || response.getBody().length == 0) {
			LOGGER.warn("Response body is null or empty - Status: " + response.getStatusCode());
			return null;
		}
		if (!response.getStatusCode().equals(HttpStatus.OK)) {
			LOGGER.warn("Failed to fetch National ID PDF - HTTP Status: " + response.getStatusCode());
			return null;
		}
		String base64Pdf = Base64.getEncoder().encodeToString(response.getBody());
		LOGGER.info("Successfully converted National ID PDF to Base64");
		return base64Pdf;
	}

	private void logAndRethrowRestClientException(RestClientException e, String baseUrl, NationalIdRequestDto req) {
		String errorUrl = baseUrl != null ? baseUrl + customerServiceNationalIdPath : "N/A";
		LOGGER.error("RestClientException fetching National ID PDF - URL: " + errorUrl + ", addressId: " + req.addressId + LOG_CUSTOMER_ID + req.customerId, e);
		String detail = e instanceof HttpStatusCodeException
				? buildHttpStatusErrorDetail((HttpStatusCodeException) e)
				: e.getMessage();
		throw new IllegalStateException(detail != null ? detail : "Customer Service national-id request failed");
	}

	private static String buildHttpStatusErrorDetail(HttpStatusCodeException hce) {
		int code = hce.getStatusCode() != null ? hce.getStatusCode().value() : 0;
		String statusText = hce.getStatusCode() != null ? hce.getStatusCode().getReasonPhrase() : "";
		String body = hce.getResponseBodyAsString();
		if (StringUtils.isBlank(body)) {
			return code + " " + statusText;
		}
		String snippet = body.length() > 400 ? body.substring(0, 400) + "..." : body;
		return code + " " + statusText + ": " + snippet;
	}

	/** Builds document payload for Logistiq upload-shipment-document (National ID). */
	Map<String, Object> buildNationalIdDocumentPayload(String awb, String fileContentBase64, SalesOrder order) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("awb", awb);
		payload.put("file_name", "National_ID");
		payload.put("file_extension", "PDF");
		payload.put("file_content", fileContentBase64);
		payload.put("attachment_type", "document_id");
		payload.put("is_international_shipment", LogistiqShipmentHelper.isInternationalShipment(order));
		return payload;
	}

	/** Uploads National ID document to Logistiq (Alpha upload-shipment-document API). */
	boolean uploadNationalIdToLogistiq(Map<String, Object> document) {
		return logistiqApiClient.uploadShipmentDocument(document, false);
	}

	private static String getCustomerServiceBaseUrl() {
		if (Constants.orderCredentials == null || Constants.orderCredentials.getOrderDetails() == null) {
			return null;
		}
		return Constants.orderCredentials.getOrderDetails().getCustomerServiceBaseUrl();
	}

	private static boolean isAlphaBaseUrlConfigured() {
		return Constants.orderCredentials != null
				&& Constants.orderCredentials.getOrderDetails() != null
				&& StringUtils.isNotBlank(Constants.orderCredentials.getOrderDetails().getAlphaBaseUrl());
	}

	/** Returns true only if the order's shipping address country is Oman (OM). */
	private boolean isOmanOrder(SalesOrder order) {
		if (order == null || order.getEntityId() == null) {
			return false;
		}
		SalesOrderAddress shippingAddress = salesOrderAddressRepository.findByOrderId(order.getEntityId())
				.stream()
				.filter(addr -> "shipping".equalsIgnoreCase(addr.getAddressType()))
				.findFirst()
				.orElse(null);
		return shippingAddress != null && COUNTRY_OMAN.equals(shippingAddress.getCountryId());
	}

	private static String parseAuthorizationToken(String authToken) {
		if (StringUtils.isBlank(authToken)) {
			return null;
		}
		String token = authToken.contains(",") ? authToken.split(",")[0].trim() : authToken.trim();
		return StringUtils.isNotBlank(token) ? token : null;
	}

	private static String truncateErrorMessage(String message) {
		if (message == null) {
			return null;
		}
		return message.length() > MAX_ERROR_MESSAGE_LENGTH ? message.substring(0, MAX_ERROR_MESSAGE_LENGTH) + "..." : message;
	}

	private static final class NationalIdRequestDto {
		final int addressId;
		final int customerId;
		final String customerEmail;

		NationalIdRequestDto(int addressId, int customerId, String customerEmail) {
			this.addressId = addressId;
			this.customerId = customerId;
			this.customerEmail = customerEmail;
		}
	}
}
