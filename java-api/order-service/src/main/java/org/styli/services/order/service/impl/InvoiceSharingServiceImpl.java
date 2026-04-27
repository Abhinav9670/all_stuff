package org.styli.services.order.service.impl;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.*;
import java.util.Base64;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.model.sales.SalesInvoice;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderAddress;
import org.styli.services.order.model.sales.SalesShipmentTrack;
import org.styli.services.order.model.sales.SplitSalesOrder;
import org.styli.services.order.helper.LogistiqShipmentHelper;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.ShipmentTrackerRepository;
import org.styli.services.order.service.InvoiceSharingService;
import org.styli.services.order.service.InvoiceSharingService.RetryResult;
import org.styli.services.order.service.LogistiqApiClient;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.model.Customer.CustomerAddressEntity;
import org.styli.services.order.repository.Customer.CustomerAddressEntityRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderAddressRepository;
import org.styli.services.order.repository.SalesOrder.SplitSalesOrderRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
@Component
public class InvoiceSharingServiceImpl implements InvoiceSharingService {

	private static final Log LOGGER = LogFactory.getLog(InvoiceSharingServiceImpl.class);
	private static final int MAX_RETRY_ATTEMPTS = 10;
	private static final String STATUS_PENDING = "PENDING";
	private static final String STATUS_SUCCESS = "SUCCESS";
	private static final String STATUS_FAILED = "FAILED";
	private static final String LOG_ORDER_ID = ", Order ID: ";
	private static final String LOG_TRACK_ID = ", Track ID: ";
	private static final String LOG_AUTH_TOKEN_LABEL = ", AuthorizationToken: ";
	private static final String LOG_AUTH_PRESENT = "[PRESENT]";
	private static final String LOG_AUTH_NULL = "[NULL/BLANK]";
	private static final String LOG_SPLIT_ORDER_ID = ", splitOrderId: ";

	@Value("${oms.generate-pdf-path:/v1/orders/generatePDF}")
	private String generatePdfPath;
	@Value("${customer.service.national-id-path:/rest/customer/document/national-id}")
	private String customerServiceNationalIdPath;
	private static final String ERROR_UNKNOWN = "Unknown error";
	private static final String ATTACHMENT_INVOICE = "invoice";
	private static final String BEARER_PREFIX = "Bearer ";
	private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

	/**
	 * Document type enum for tracking invoice vs National ID uploads
	 */
	private enum DocumentType {
		INVOICE, NATIONAL_ID
	}

	@Autowired
	@Qualifier("restTemplateBuilder")
	private RestTemplate restTemplate;

	@Autowired
	private ShipmentTrackerRepository shipmentTrackerRepository;

	@Autowired
	private SalesOrderRepository salesOrderRepository;

	@Autowired
	private CustomerAddressEntityRepository customerAddressEntityRepository;

	@Autowired
	private SalesOrderAddressRepository salesOrderAddressRepository;

	@Autowired
	private SplitSalesOrderRepository splitSalesOrderRepository;

	@Autowired
	@Lazy
	private InvoiceSharingService invoiceSharingService; // Self-injection for transaction management

	@Autowired
	private LogistiqApiClient logistiqApiClient;

	@Value("${auth.internal.header.bearer.token:}")
	private String internalHeaderBearerToken;

	@Value("${auth.internal.jwt.token:}")
	private String authInternalJwtToken;

	private static final ObjectMapper mapper = new ObjectMapper();

	/**
	 * Asynchronously send invoice to Logistiq after AWB is created
	 */
	@Override
	@Async("asyncExecutor")
	@Transactional
	public void sendInvoiceToLogistiqAsync(String awb, SalesOrder order, SalesShipmentTrack track, String authorizationToken) {
		InvoiceUploadContext ctx = new InvoiceUploadContext(awb, authorizationToken);
		LOGGER.info("=== sendInvoiceToLogistiqAsync ENTRY POINT (ASYNC) - AWB: " + ctx.awb + 
			LOG_ORDER_ID + (order != null ? order.getEntityId() : "null") + 
			LOG_TRACK_ID + (track != null ? track.getEntityId() : "null") + 
			formatAuthTokenLogSuffix(ctx.authorizationToken));
		
		if (!isAlphaBaseUrlConfigured()) {
			LOGGER.warn("Alpha base URL not configured, skipping invoice upload for AWB: " + ctx.awb);
			return;
		}
		if (track == null || track.getEntityId() == null) {
			LOGGER.warn("Track or track entityId is null, skipping async invoice upload for AWB: " + ctx.awb);
			return;
		}

		// Reload track and order for async thread (caller may pass detached entities)
		SalesShipmentTrack managedTrack = shipmentTrackerRepository.findById(track.getEntityId()).orElse(track);
		SalesOrder orderToUse = managedTrack.getSalesOrder();
		if (orderToUse == null && managedTrack.getOrderId() != null) {
			orderToUse = salesOrderRepository.findById(managedTrack.getOrderId()).orElse(order);
		}
		if (orderToUse == null) {
			orderToUse = order;
		}
		if (orderToUse == null) {
			LOGGER.warn("Order could not be resolved for track " + managedTrack.getEntityId() + ", skipping async invoice upload for AWB: " + ctx.awb);
			return;
		}
		initializeLazyCollectionsForAsyncInvoice(orderToUse, managedTrack);

		LOGGER.info("Starting async invoice upload for AWB: " + ctx.awb + LOG_TRACK_ID + managedTrack.getEntityId());

		try {
			LOGGER.info("=== Calling sendInvoiceToLogistiq from async method for AWB: " + ctx.awb + " ===");
			sendInvoiceToLogistiq(ctx.awb, orderToUse, managedTrack, ctx.authorizationToken);
			LOGGER.info("=== sendInvoiceToLogistiq completed from async method for AWB: " + ctx.awb + " ===");
		} catch (Exception e) {
			LOGGER.error("Error in async invoice upload for AWB: " + ctx.awb, e);
		}
	}

	/** Initialize lazy collections used during async invoice upload. */
	private void initializeLazyCollectionsForAsyncInvoice(SalesOrder orderToUse, SalesShipmentTrack managedTrack) {
		if (orderToUse != null && orderToUse.getSalesInvoices() != null) {
			Hibernate.initialize(orderToUse.getSalesInvoices());
		}
		if (managedTrack.getSplitSalesOrder() != null) {
			Hibernate.initialize(managedTrack.getSplitSalesOrder());
			SplitSalesOrder split = managedTrack.getSplitSalesOrder();
			if (split.getSplitSalesInvoices() != null) {
				Hibernate.initialize(split.getSplitSalesInvoices());
			}
		}
	}

	/**
	 * Send invoice to Logistiq API
	 */
	@Override
	@Transactional
	public boolean sendInvoiceToLogistiq(String awb, SalesOrder order, SalesShipmentTrack track, String authorizationToken) {
		InvoiceUploadContext ctx = new InvoiceUploadContext(awb, authorizationToken);
		LOGGER.info("=== sendInvoiceToLogistiq ENTRY POINT - AWB: " + ctx.awb + LOG_ORDER_ID + 
			(order != null ? order.getEntityId() : "null") + LOG_TRACK_ID + 
			(track != null ? track.getEntityId() : "null") + 
			formatAuthTokenLogSuffix(ctx.authorizationToken));
		
		// Increment attempts first to prevent infinite retry on early failures
		incrementAttemptAndSetPending(track, DocumentType.INVOICE);

		// Validate inputs
		if (!validateInputs(ctx, track)) {
			LOGGER.warn("Input validation failed for AWB: " + ctx.awb);
			return false;
		}

		// Get invoice and split order info
		InvoiceInfo invoiceInfo = getInvoiceInfo(order, track);
		if (!validateInvoiceInfo(invoiceInfo, order, track)) {
			LOGGER.warn("Invoice info validation failed for AWB: " + ctx.awb);
			return false;
		}

		// Send invoice to Logistiq
		LOGGER.info("=== Proceeding to sendInvoiceToLogistiqInternal for AWB: " + ctx.awb + " ===");
		return sendInvoiceToLogistiqInternal(ctx, order, track, invoiceInfo.getSplitSalesOrder());
	}

	/**
	 * Increment attempt count and set status to PENDING (for invoice or National ID)
	 */
	private void incrementAttemptAndSetPending(SalesShipmentTrack track, DocumentType docType) {
		if (docType == DocumentType.INVOICE) {
			int currentAttempts = track.getInvoiceUploadAttempts() != null ? track.getInvoiceUploadAttempts() : 0;
			track.setInvoiceUploadAttempts(currentAttempts + 1);
			track.setInvoiceUploadStatus(STATUS_PENDING);
		} else {
			track.setNationalIdUploadStatus(STATUS_PENDING);
		}
		shipmentTrackerRepository.saveAndFlush(track);
	}

	private static boolean isAlphaBaseUrlConfigured() {
		return Constants.orderCredentials != null
				&& Constants.orderCredentials.getOrderDetails() != null
				&& StringUtils.isNotBlank(Constants.orderCredentials.getOrderDetails().getAlphaBaseUrl());
	}
	private static String formatAuthTokenLogSuffix(String authorizationToken) {
		return LOG_AUTH_TOKEN_LABEL + (StringUtils.isNotBlank(authorizationToken) ? LOG_AUTH_PRESENT : LOG_AUTH_NULL) + " ===";
	}

	/**
	 * Validate AWB and max attempts
	 */
	private boolean validateInputs(InvoiceUploadContext ctx, SalesShipmentTrack track) {
		if (StringUtils.isBlank(ctx.awb)) {
			LOGGER.error("AWB is blank for track: " + track.getEntityId());
			updateUploadStatus(track, new UploadStatusUpdate(STATUS_FAILED, "AWB is blank"));
			return false;
		}

		// Check max attempts (after incrementing)
		// Use > instead of >= to allow MAX_RETRY_ATTEMPTS actual attempts (1-10, not 1-9)
		if (track.getInvoiceUploadAttempts() > MAX_RETRY_ATTEMPTS) {
			LOGGER.warn("Max retry attempts reached for track: " + track.getEntityId() +
					", attempts: " + track.getInvoiceUploadAttempts());
			updateUploadStatus(track, new UploadStatusUpdate(STATUS_FAILED, "Max retry attempts (" + MAX_RETRY_ATTEMPTS + ") reached"));
			return false;
		}

		return true;
	}

	/**
	 * Validate invoice info exists
	 */
	private boolean validateInvoiceInfo(InvoiceInfo invoiceInfo, SalesOrder order, SalesShipmentTrack track) {
		if (invoiceInfo == null || invoiceInfo.getInvoice() == null) {
			String errorMsg = "Invoice not found for order: " + order.getIncrementId() + 
					", track: " + track.getEntityId() + 
					(invoiceInfo != null && invoiceInfo.getSplitSalesOrder() != null ? 
							LOG_SPLIT_ORDER_ID + invoiceInfo.getSplitSalesOrder().getEntityId() : "");
			LOGGER.error(errorMsg);
			updateUploadStatus(track, new UploadStatusUpdate(STATUS_FAILED, "Invoice not found"));
			return false;
		}
		return true;
	}

	/**
	 * Internal method to send invoice to Logistiq
	 * National ID is sent independently regardless of invoice status
	 */
	private boolean sendInvoiceToLogistiqInternal(InvoiceUploadContext ctx, SalesOrder order, SalesShipmentTrack track, 
			SplitSalesOrder splitSalesOrder) {
		LOGGER.info("=== sendInvoiceToLogistiqInternal called - AWB: " + ctx.awb + LOG_ORDER_ID + 
			(order != null ? order.getEntityId() : "null") + 
			formatAuthTokenLogSuffix(ctx.authorizationToken));
		boolean invoiceSuccess = false;
		
		// Send invoice (may succeed or fail)
		try {
			String invoiceBase64 = fetchInvoiceAsBase64(order, splitSalesOrder, ctx.authorizationToken);
			if (StringUtils.isBlank(invoiceBase64)) {
				LOGGER.error("[get-shipment-v3] Invoice PDF not received or empty - cannot send to Logistiq for AWB: " + ctx.awb);
				throw new IllegalStateException("Failed to fetch invoice PDF: empty response");
			}
			LOGGER.info("[get-shipment-v3] Invoice PDF received and Base64 ready, sending to Logistiq for AWB: " + ctx.awb);

			Map<String, Object> invoiceDoc = buildInvoiceDocumentPayload(new DocumentPayloadParams(ctx.awb, invoiceBase64, ATTACHMENT_INVOICE), order);
			invoiceSuccess = logistiqApiClient.uploadShipmentDocument(invoiceDoc, true);
			
			if (invoiceSuccess) {
				LOGGER.info("[get-shipment-v3] Invoice document sent to Logistiq successfully for AWB: " + ctx.awb);
			} else {
				LOGGER.warn("[get-shipment-v3] Invoice document send to Logistiq FAILED for AWB: " + ctx.awb);
			}
		} catch (Exception e) {
			LOGGER.error("Error sending invoice for AWB: " + ctx.awb, e);
			invoiceSuccess = false;
		}

		// Send National ID independently (regardless of invoice status)
		LOGGER.info("=== About to call sendNationalIdIfAvailable for AWB: " + ctx.awb + " ===");
		sendNationalIdIfAvailable(ctx, order, track);
		LOGGER.info("=== sendNationalIdIfAvailable completed for AWB: " + ctx.awb + " ===");

		// Update track status based on invoice result only
		updateUploadStatusAfterSend(track, new UploadOutcome(invoiceSuccess, ctx.awb, DocumentType.INVOICE));
		shipmentTrackerRepository.saveAndFlush(track);
		
		return invoiceSuccess;
	}

	/**
	 * Get invoice information for normal or split orders
	 */
	private InvoiceInfo getInvoiceInfo(SalesOrder order, SalesShipmentTrack track) {
		SplitSalesOrder splitSalesOrder = track.getSplitSalesOrder();
		if (needsSplitOrderLoad(track, splitSalesOrder)) {
			splitSalesOrder = splitSalesOrderRepository.findByEntityId(track.getSplitSalesOrderId());
			LOGGER.info("Loaded SplitSalesOrder from repository for track: " + track.getEntityId() +
					LOG_SPLIT_ORDER_ID + (splitSalesOrder != null ? splitSalesOrder.getEntityId() : "null"));
		}
		if (splitSalesOrder != null) {
			return getSplitOrderInvoiceInfo(track, splitSalesOrder);
		}
		return getNormalOrderInvoiceInfo(order, track);
	}

	private static boolean needsSplitOrderLoad(SalesShipmentTrack track, SplitSalesOrder splitSalesOrder) {
		return splitSalesOrder == null && track.getSplitSalesOrderId() != null && track.getSplitSalesOrderId() > 0;
	}

	/**
	 * Get invoice info for split orders
	 */
	private InvoiceInfo getSplitOrderInvoiceInfo(SalesShipmentTrack track, SplitSalesOrder splitSalesOrder) {
		SalesInvoice invoice = null;
		if (CollectionUtils.isNotEmpty(splitSalesOrder.getSplitSalesInvoices())) {
			invoice = splitSalesOrder.getSplitSalesInvoices().stream().findFirst().orElse(null);
		}
		LOGGER.info("Split order detected for track: " + track.getEntityId() + 
				LOG_SPLIT_ORDER_ID + splitSalesOrder.getEntityId());
		return new InvoiceInfo(invoice, splitSalesOrder);
	}

	/**
	 * Get invoice info for normal orders
	 */
	private InvoiceInfo getNormalOrderInvoiceInfo(SalesOrder order, SalesShipmentTrack track) {
		SalesInvoice invoice = null;
		if (CollectionUtils.isNotEmpty(order.getSalesInvoices())) {
			invoice = order.getSalesInvoices().stream().findFirst().orElse(null);
		}
		LOGGER.info("Normal order detected for track: " + track.getEntityId());
		return new InvoiceInfo(invoice, null);
	}

	/**
	 * Build document payload for Logistiq upload-shipment-document API
	 * @param params awb, fileContent, attachmentType ("invoice" or "document_id")
	 */
	private Map<String, Object> buildDocumentPayload(DocumentPayloadParams params, SalesOrder order) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("awb", params.awb);
		payload.put("file_name", ATTACHMENT_INVOICE.equals(params.attachmentType) ? "Invoice" : "National_ID");
		payload.put("file_extension", "PDF");
		payload.put("file_content", params.fileContent);
		payload.put("attachment_type", params.attachmentType);
		payload.put("is_international_shipment", LogistiqShipmentHelper.isInternationalShipment(order));
		return payload;
	}

	/**
	 * Build invoice document payload (delegates to generic method)
	 */
	private Map<String, Object> buildInvoiceDocumentPayload(DocumentPayloadParams params, SalesOrder order) {
		return buildDocumentPayload(params, order);
	}

	/**
	 * Send National ID to Logistiq if available and track upload status
	 * NOTE: National ID uploads are NOT retried via CRON - this is a one-time attempt only.
	 * If National ID upload fails, it will remain in FAILED status and will not be automatically retried.
	 * 
	 * Fetches National ID PDF from Customer Service API instead of database.
	 */
	private void sendNationalIdIfAvailable(InvoiceUploadContext ctx, SalesOrder order, SalesShipmentTrack track) {
		LOGGER.info("=== Starting National ID upload process for AWB: " + ctx.awb + LOG_ORDER_ID + 
			(order != null ? order.getEntityId() : "null") + " ===");
		try {
			incrementAttemptAndSetPending(track, DocumentType.NATIONAL_ID);
			NationalIdRequest req = resolveNationalIdRequest(order, track, ctx);
			if (req == null) {
				return;
			}
			String nationalIdBase64 = fetchNationalIdPdfAsBase64(req);
			if (StringUtils.isBlank(nationalIdBase64)) {
				LOGGER.warn("[get-shipment-v3] National ID PDF not received or empty for AWB: " + ctx.awb);
				updateUploadStatusAfterSend(track, new UploadOutcome(false, ctx.awb, DocumentType.NATIONAL_ID));
				track.setNationalIdUploadError("Failed to fetch National ID PDF from Customer Service API");
				return;
			}
			Map<String, Object> nationalIdDoc = buildDocumentPayload(new DocumentPayloadParams(ctx.awb, nationalIdBase64, "document_id"), order);
			boolean success = logistiqApiClient.uploadShipmentDocument(nationalIdDoc, true);
			updateUploadStatusAfterSend(track, new UploadOutcome(success, ctx.awb, DocumentType.NATIONAL_ID));
		} catch (Exception e) {
			LOGGER.error("Error sending National ID for AWB: " + ctx.awb, e);
			track.setNationalIdUploadStatus(STATUS_FAILED);
			track.setNationalIdUploadError(truncateErrorMessage(e.getMessage()));
		}
	}

	/** Holds addressId, customerId, customerEmail for National ID fetch. */
	private static class NationalIdRequest {
		final Integer addressId;
		final Integer customerId;
		final String customerEmail;

		NationalIdRequest(Integer addressId, Integer customerId, String customerEmail) {
			this.addressId = addressId;
			this.customerId = customerId;
			this.customerEmail = customerEmail;
		}
	}

	/** Holds URL, body and auth token for generatePDF request (reduces primitive args). */
	private static class InvoicePdfRequest {
		final String invoiceUrl;
		final Map<String, Object> body;
		String authToken;

		InvoicePdfRequest(String invoiceUrl, Map<String, Object> body, String authToken) {
			this.invoiceUrl = invoiceUrl;
			this.body = body;
			this.authToken = authToken;
		}
	}

	/** Context for invoice upload flow: AWB and optional authorization token. */
	private static class InvoiceUploadContext {
		final String awb;
		final String authorizationToken;

		InvoiceUploadContext(String awb, String authorizationToken) {
			this.awb = awb;
			this.authorizationToken = authorizationToken;
		}
	}

	/** Status and error message for upload status updates. */
	private static class UploadStatusUpdate {
		final String status;
		final String error;

		UploadStatusUpdate(String status, String error) {
			this.status = status;
			this.error = error;
		}
	}

	/** Outcome of an upload attempt (invoice or National ID). */
	private static class UploadOutcome {
		final boolean success;
		final String awb;
		final DocumentType docType;

		UploadOutcome(boolean success, String awb, DocumentType docType) {
			this.success = success;
			this.awb = awb;
			this.docType = docType;
		}
	}

	/** Parameters for building a document payload (invoice or National ID). */
	private static class DocumentPayloadParams {
		final String awb;
		final String fileContent;
		final String attachmentType;

		DocumentPayloadParams(String awb, String fileContent, String attachmentType) {
			this.awb = awb;
			this.fileContent = fileContent;
			this.attachmentType = attachmentType;
		}
	}

	/** Auth context for National ID API request (token + customer email). */
	private static class NationalIdAuth {
		final String internalToken;
		final String customerEmail;

		NationalIdAuth(String internalToken, String customerEmail) {
			this.internalToken = internalToken;
			this.customerEmail = customerEmail;
		}
	}

	/** Token and source for logging token preview. */
	private static class TokenPreview {
		final String authToken;
		final String source;

		TokenPreview(String authToken, String source) {
			this.authToken = authToken;
			this.source = source;
		}
	}

	/** Resolves shipping address and customer ids; updates track and returns null if invalid.
	 * addressId must be sales_order_address.customer_address_id (customer's address id), NOT sales_order_address.entity_id.
	 */
	private NationalIdRequest resolveNationalIdRequest(SalesOrder order, SalesShipmentTrack track, InvoiceUploadContext ctx) {
		String awb = ctx.awb;
		SalesOrderAddress shippingAddress = salesOrderAddressRepository.findByOrderId(order.getEntityId())
				.stream()
				.filter(addr -> "shipping".equalsIgnoreCase(addr.getAddressType()))
				.findFirst()
				.orElse(null);
		if (shippingAddress == null || shippingAddress.getCustomerAddressId() == null) {
			updateUploadStatusAfterSend(track, new UploadOutcome(false, awb, DocumentType.NATIONAL_ID));
			track.setNationalIdUploadError("Shipping address or address ID not found");
			return null;
		}
		// Use customer_address_id (customer address id), not sales_order_address.entity_id — required for /rest/customer/document/national-id
		Integer addressId = shippingAddress.getCustomerAddressId();
		Integer customerId = order.getCustomerId() != null ? order.getCustomerId() : shippingAddress.getCustomerId();
		String customerEmail = order.getCustomerEmail();
		if (addressId == null) {
			updateUploadStatusAfterSend(track, new UploadOutcome(false, awb, DocumentType.NATIONAL_ID));
			track.setNationalIdUploadError("AddressId is null");
			return null;
		}
		if (customerId == null) {
			updateUploadStatusAfterSend(track, new UploadOutcome(false, awb, DocumentType.NATIONAL_ID));
			track.setNationalIdUploadError("CustomerId is null");
			return null;
		}
		return new NationalIdRequest(addressId, customerId, customerEmail);
	}

	/**
	 * Update upload status after sending to Logistiq (for invoice or National ID)
	 */
	private void updateUploadStatusAfterSend(SalesShipmentTrack track, UploadOutcome outcome) {
		String docName = outcome.docType == DocumentType.INVOICE ? ATTACHMENT_INVOICE : "National ID";
		if (outcome.success) {
			applyUploadSuccessToTrack(track, outcome);
			LOGGER.info("Successfully sent " + docName + " to Logistiq for AWB: " + outcome.awb);
		} else {
			applyUploadFailureToTrack(track, outcome);
			LOGGER.warn("Failed to send " + docName + " to Logistiq for AWB: " + outcome.awb);
		}
	}

	private void applyUploadSuccessToTrack(SalesShipmentTrack track, UploadOutcome outcome) {
		if (outcome.docType == DocumentType.INVOICE) {
			track.setInvoiceUploadStatus(STATUS_SUCCESS);
			track.setInvoiceUploadedAt(new Timestamp(new Date().getTime()));
			track.setInvoiceUploadError(null);
		} else {
			track.setNationalIdUploadStatus(STATUS_SUCCESS);
			track.setNationalIdUploadedAt(new Timestamp(new Date().getTime()));
			track.setNationalIdUploadError(null);
		}
	}

	private void applyUploadFailureToTrack(SalesShipmentTrack track, UploadOutcome outcome) {
		String errorMsg = "Logistiq API returned non-success status";
		if (outcome.docType == DocumentType.INVOICE) {
			track.setInvoiceUploadStatus(STATUS_FAILED);
			track.setInvoiceUploadError(errorMsg);
		} else {
			track.setNationalIdUploadStatus(STATUS_FAILED);
			track.setNationalIdUploadError(errorMsg);
		}
	}

	/**
	 * Helper class to hold invoice information
	 */
	private static class InvoiceInfo {
		private final SalesInvoice invoice;
		private final SplitSalesOrder splitSalesOrder;

		public InvoiceInfo(SalesInvoice invoice, SplitSalesOrder splitSalesOrder) {
			this.invoice = invoice;
			this.splitSalesOrder = splitSalesOrder;
		}

		public SalesInvoice getInvoice() {
			return invoice;
		}

		public SplitSalesOrder getSplitSalesOrder() {
			return splitSalesOrder;
		}
	}

	/**
	 * Fetch invoice PDF via POST /v1/orders/generatePDF (same as working curl).
	 * Uses public OMS URL, JSON body (orderId, splitOrderId, customerEmail), Authorization: Bearer.
	 * Converts response (binary PDF or JSON with base64) to Base64 and returns.
	 */
	private String fetchInvoiceAsBase64(SalesOrder order, SplitSalesOrder splitSalesOrder, String authorizationToken) throws IllegalStateException {
		String baseUrl = getInvoicePdfBaseUrl();
		if (StringUtils.isBlank(baseUrl)) {
			throw new IllegalStateException("OMS base URL is not configured for invoice PDF fetch");
		}
		String invoiceUrl = baseUrl + generatePdfPath;
		LOGGER.info("Fetching invoice PDF via POST: " + invoiceUrl);
		String authToken = resolveAuthTokenForInvoiceFetch(authorizationToken);
		Map<String, Object> body = buildInvoicePdfRequestBody(order, splitSalesOrder);
		LOGGER.info("Invoice PDF request body: orderId=" + body.get("orderId") + ", splitOrderId=" + body.get("splitOrderId") + ", customerEmail=" + body.get("customerEmail"));
		InvoicePdfRequest request = new InvoicePdfRequest(invoiceUrl, body, authToken);
		return fetchInvoicePdfWithRetry(request);
	}

	private String resolveAuthTokenForInvoiceFetch(String authorizationToken) {
		String authToken = getAuthorizationToken(authorizationToken);
		if (StringUtils.isNotBlank(authToken)) {
			LOGGER.info("Using authorization token from get-shipment-v3 API for invoice PDF fetch");
			logTokenPreview(new TokenPreview(authToken, "from get-shipment-v3"));
			return authToken;
		}
		authToken = getAuthorizationToken(internalHeaderBearerToken);
		if (StringUtils.isBlank(authToken)) {
			throw new IllegalStateException("Authorization token is not configured or is blank");
		}
		LOGGER.warn("Using fallback internal authorization token for invoice PDF fetch (authorizationToken was null/blank)");
		logTokenPreview(new TokenPreview(authToken, "fallback internal"));
		return authToken;
	}

	private static void logTokenPreview(TokenPreview preview) {
		String token = preview.authToken;
		String text = token.substring(0, Math.min(50, token.length())) + (token.length() > 50 ? "..." : "");
		LOGGER.info("[generatePDF] Token being sent (" + preview.source + "): " + text);
	}

	private String fetchInvoicePdfWithRetry(InvoicePdfRequest request) {
		for (int attempt = 0; attempt < 2; attempt++) {
			prepareAuthTokenForAttempt(attempt, request);
			try {
				String base64 = fetchInvoicePdfSingleAttempt(request);
				if (base64 != null) {
					return base64;
				}
			} catch (RestClientException e) {
				if (shouldRetryInvoiceFetchOnException(attempt, e)) {
					LOGGER.warn("401 Unauthorized - will refresh token and retry: " + e.getMessage());
					continue;
				}
				LOGGER.error("Failed to fetch invoice PDF from URL: " + request.invoiceUrl, e);
				throw new IllegalStateException("Failed to fetch invoice PDF: " + e.getMessage(), e);
			}
		}
		throw new IllegalStateException("Failed to fetch invoice PDF: 401 Unauthorized after retry");
	}

	private void prepareAuthTokenForAttempt(int attempt, InvoicePdfRequest request) {
		if (attempt == 1) {
			sleepQuietly(1000);
			request.authToken = refreshTokenForInvoiceRetry(request.authToken);
		}
	}

	private boolean shouldRetryInvoiceFetchOnException(int attempt, RestClientException e) {
		return attempt == 0 && isUnauthorizedError(e);
	}

	private String refreshTokenForInvoiceRetry(String currentToken) {
		LOGGER.info("401 Unauthorized on first attempt - refreshing Alpha token and retrying");
		String fresh = logistiqApiClient.getFreshAlphaToken();
		return StringUtils.isNotBlank(fresh) ? fresh : currentToken;
	}

	private static void sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	private String fetchInvoicePdfSingleAttempt(InvoicePdfRequest request) {
		HttpHeaders headers = buildInvoiceFetchHeaders(request.authToken);
		HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(request.body, headers);
		ResponseEntity<byte[]> response = restTemplate.exchange(request.invoiceUrl, HttpMethod.POST, httpEntity, byte[].class);
		if (response.getStatusCode().equals(HttpStatus.UNAUTHORIZED)) {
			return null;
		}
		if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
			throw new IllegalStateException("Failed to fetch invoice PDF: HTTP " + response.getStatusCode());
		}
		String base64 = extractPdfBase64FromResponse(response);
		if (StringUtils.isBlank(base64)) {
			throw new IllegalStateException("Invoice PDF response was empty or could not be parsed");
		}
		LOGGER.info("[get-shipment-v3] Invoice PDF received and converted to Base64 - length: " + base64.length() + " chars");
		return base64;
	}

	/**
	 * Extract PDF as Base64 from generatePDF response. Handles both binary PDF and JSON with base64 field.
	 */
	private String extractPdfBase64FromResponse(ResponseEntity<byte[]> response) {
		byte[] body = response.getBody();
		if (body == null || body.length == 0) {
			return null;
		}
		MediaType contentType = response.getHeaders().getContentType();
		if (contentType != null && contentType.includes(MediaType.APPLICATION_PDF)) {
			return extractPdfBase64FromBinary(body);
		}
		if (contentType != null && contentType.includes(MediaType.APPLICATION_JSON)) {
			String fromJson = extractPdfBase64FromJsonBody(body);
			if (fromJson != null) {
				return fromJson;
			}
		}
		LOGGER.info("[get-shipment-v3] Invoice PDF received - size: " + body.length + " bytes (treated as binary)");
		return Base64.getEncoder().encodeToString(body);
	}

	private String extractPdfBase64FromBinary(byte[] body) {
		LOGGER.info("[get-shipment-v3] Invoice PDF received successfully - size: " + body.length + " bytes (binary)");
		return Base64.getEncoder().encodeToString(body);
	}

	@SuppressWarnings("unchecked")
	private String extractPdfBase64FromJsonBody(byte[] body) {
		try {
			String json = new String(body, StandardCharsets.UTF_8);
			Map<String, Object> map = mapper.readValue(json, Map.class);
			return findFirstNonBlankBase64FromKeys(map, Arrays.asList("pdf", "pdfBase64", "data", "file", "base64"));
		} catch (Exception e) {
			LOGGER.warn("Could not parse invoice PDF from JSON response: " + e.getMessage());
			return null;
		}
	}

	private static String findFirstNonBlankBase64FromKeys(Map<String, Object> map, List<String> keys) {
		for (String key : keys) {
			if (!map.containsKey(key) || map.get(key) == null) {
				continue;
			}
			String value = map.get(key).toString();
			if (StringUtils.isNotBlank(value)) {
				LOGGER.info("[get-shipment-v3] Invoice PDF from JSON field '" + key + "' - length: " + value.length() + " chars");
				return value;
			}
		}
		return null;
	}

	/**
	 * Extract authorization token (returns first token if comma-separated)
	 */
	private String getAuthorizationToken(String authToken) {
		if (StringUtils.isBlank(authToken)) {
			return null;
		}
		String token = authToken.contains(",") ? authToken.split(",")[0].trim() : authToken.trim();
		return StringUtils.isNotBlank(token) ? token : null;
	}

	/**
	 * Fetch National ID PDF from Customer Service API and convert to Base64
	 */
	private String fetchNationalIdPdfAsBase64(NationalIdRequest req) {
		LOGGER.info("Starting National ID PDF fetch - addressId: " + req.addressId + ", customerId: " + req.customerId + ", customerEmail: " + req.customerEmail);
		String customerServiceBaseUrl = getCustomerServiceBaseUrl();
		if (StringUtils.isBlank(customerServiceBaseUrl)) {
			LOGGER.error("Customer service base URL is not configured");
			return null;
		}
		String internalToken = getAuthorizationToken(internalHeaderBearerToken);
		if (StringUtils.isBlank(internalToken)) {
			LOGGER.warn("Internal bearer token not available for National ID PDF fetch");
			return null;
		}
		String url = customerServiceBaseUrl + customerServiceNationalIdPath +
				"?addressId=" + req.addressId + "&customerId=" + req.customerId;
		LOGGER.info("Calling Customer Service API - URL: " + url);
		HttpHeaders headers = buildNationalIdRequestHeaders(new NationalIdAuth(internalToken, req.customerEmail));
		try {
			ResponseEntity<byte[]> response = executeNationalIdRequest(url, headers);
			return convertNationalIdResponseToBase64(response);
		} catch (RestClientException e) {
			logAndRethrowNationalIdRestClientException(e, customerServiceBaseUrl, req);
		} catch (Exception e) {
			logAndRethrowNationalIdUnexpectedException(e, req);
		}
		return null;
	}

	private String getCustomerServiceBaseUrl() {
		if (Constants.orderCredentials == null || Constants.orderCredentials.getOrderDetails() == null) {
			return null;
		}
		return Constants.orderCredentials.getOrderDetails().getCustomerServiceBaseUrl();
	}

	private HttpHeaders buildNationalIdRequestHeaders(NationalIdAuth auth) {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_PDF));
		headers.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
		headers.add(Constants.AUTH_BEARER_HEADER, auth.internalToken);
		if (StringUtils.isNotBlank(auth.customerEmail)) {
			headers.add("x-header-token", auth.customerEmail);
		}
		return headers;
	}

	private ResponseEntity<byte[]> executeNationalIdRequest(String url, HttpHeaders headers) {
		return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
	}

	private String convertNationalIdResponseToBase64(ResponseEntity<byte[]> response) {
		if (response.getBody() == null || response.getBody().length == 0) {
			LOGGER.warn("Response body is null or empty - Status: " + response.getStatusCode());
			return null;
		}
		if (!response.getStatusCode().equals(HttpStatus.OK)) {
			LOGGER.warn("Failed to fetch National ID PDF - HTTP Status: " + response.getStatusCode());
			return null;
		}
		byte[] body = response.getBody();
		LOGGER.info("Response body size: " + body.length + " bytes");
		String base64Pdf = Base64.getEncoder().encodeToString(body);
		LOGGER.info("Successfully fetched and converted National ID PDF to Base64");
		return base64Pdf;
	}

	private void logAndRethrowNationalIdRestClientException(RestClientException e, String baseUrl, NationalIdRequest req) {
		String errorUrl = baseUrl != null ? baseUrl + customerServiceNationalIdPath : "N/A";
		LOGGER.error("RestClientException fetching National ID PDF - URL: " + errorUrl + ", AddressId: " + req.addressId + ", CustomerId: " + req.customerId, e);
		String errorDetail = e instanceof HttpStatusCodeException
				? buildHttpStatusErrorDetail((HttpStatusCodeException) e)
				: e.getMessage();
		throw new IllegalStateException(errorDetail != null ? errorDetail : "Customer Service national-id request failed");
	}

	private static String buildHttpStatusErrorDetail(HttpStatusCodeException hce) {
		int code = hce.getStatusCode() != null ? hce.getStatusCode().value() : 0;
		String statusText = hce.getStatusCode() != null ? hce.getStatusCode().getReasonPhrase() : "";
		String body = hce.getResponseBodyAsString();
		if (StringUtils.isBlank(body)) return code + " " + statusText;
		String bodySnippet = body.length() > 400 ? body.substring(0, 400) + "..." : body;
		return code + " " + statusText + ": " + bodySnippet;
	}

	private void logAndRethrowNationalIdUnexpectedException(Exception e, NationalIdRequest req) {
		LOGGER.error("Unexpected exception fetching National ID PDF - AddressId: " + req.addressId + ", CustomerId: " + req.customerId + ", CustomerEmail: " + req.customerEmail, e);
		throw new IllegalStateException(e.getMessage() != null ? e.getMessage() : "Failed to fetch National ID PDF from Customer Service API");
	}

	/**
	 * Build HTTP headers for invoice PDF fetch request (POST generatePDF).
	 * OMS API expects standard "Authorization: Bearer &lt;token&gt;" header (not authorization-token).
	 */
	private HttpHeaders buildInvoiceFetchHeaders(String authToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PDF, MediaType.ALL));
		headers.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
		
		String bearerToken = StringUtils.isNotBlank(authToken) && !authToken.startsWith(BEARER_PREFIX)
				? BEARER_PREFIX + authToken : authToken;
		LOGGER.info("[generatePDF] Authorization header value being sent: " + 
			bearerToken.substring(0, Math.min(60, bearerToken.length())) + 
			(bearerToken.length() > 60 ? "..." : ""));
		// OMS generatePDF requires standard "Authorization" header (not "authorization-token")
		headers.add("Authorization", bearerToken);
		return headers;
	}

	/**
	 * Build request body for POST /v1/orders/generatePDF (matches working curl).
	 * orderId and splitOrderId are entity_id (numeric); customerEmail is string.
	 */
	private Map<String, Object> buildInvoicePdfRequestBody(SalesOrder order, SplitSalesOrder splitSalesOrder) {
		Map<String, Object> body = new HashMap<>();
		body.put("orderId", order != null ? order.getEntityId() : null);
		if (splitSalesOrder != null) {
			body.put("splitOrderId", splitSalesOrder.getEntityId());
		}
		String email = order != null ? order.getCustomerEmail() : null;
		if (splitSalesOrder != null && StringUtils.isNotBlank(splitSalesOrder.getCustomerEmail())) {
			email = splitSalesOrder.getCustomerEmail();
		}
		if (StringUtils.isNotBlank(email)) {
			body.put("customerEmail", email);
		}
		return body;
	}

	/**
	 * Get base URL for invoice PDF fetch. Uses public OMS URL so the POST generatePDF API is reachable.
	 */
	private String getInvoicePdfBaseUrl() {
		String baseUrl = Constants.orderCredentials != null && Constants.orderCredentials.getOrderDetails() != null
				? Constants.orderCredentials.getOrderDetails().getOmsServicePublicBaseUrl()
				: null;
		if (StringUtils.isBlank(baseUrl)) {
			baseUrl = Constants.orderCredentials != null && Constants.orderCredentials.getOrderDetails() != null
					? Constants.orderCredentials.getOrderDetails().getOmsServiceBaseUrl()
					: null;
		}
		return baseUrl;
	}

	/**
	 * Check if exception is a 401 Unauthorized error
	 */
	private boolean isUnauthorizedError(RestClientException e) {
		return e.getMessage() != null && 
				(e.getMessage().contains("401") || e.getMessage().contains("Unauthorized"));
	}

	/**
	 * Update upload status in shipment track
	 */
	private void updateUploadStatus(SalesShipmentTrack track, UploadStatusUpdate update) {
		track.setInvoiceUploadStatus(update.status);
		if (update.error != null) {
			track.setInvoiceUploadError(truncateErrorMessage(update.error));
		}
		if (STATUS_SUCCESS.equals(update.status)) {
			track.setInvoiceUploadedAt(new Timestamp(new Date().getTime()));
		}
		shipmentTrackerRepository.saveAndFlush(track);
	}

	private static String truncateErrorMessage(String message) {
		String msg = message != null ? message : ERROR_UNKNOWN;
		return msg.length() > MAX_ERROR_MESSAGE_LENGTH ? msg.substring(0, MAX_ERROR_MESSAGE_LENGTH) : msg;
	}

	/**
	 * Update upload status and increment attempts
	 * Used to prevent infinite retry loops when validation fails
	 */
	private void updateUploadStatusWithAttempt(SalesShipmentTrack track, UploadStatusUpdate update) {
		int currentAttempts = track.getInvoiceUploadAttempts() != null ? track.getInvoiceUploadAttempts() : 0;
		track.setInvoiceUploadAttempts(currentAttempts + 1);
		track.setInvoiceUploadStatus(update.status);
		if (update.error != null) {
			track.setInvoiceUploadError(truncateErrorMessage(update.error));
		}
		if (STATUS_SUCCESS.equals(update.status)) {
			track.setInvoiceUploadedAt(new Timestamp(new Date().getTime()));
		}
		shipmentTrackerRepository.saveAndFlush(track);
	}

	/**
	 * Retry failed invoice uploads (called by CRON)
	 * 
	 * NOTE: This method ONLY retries invoice uploads, NOT National ID uploads.
	 * National ID uploads are one-time attempts and are not included in retry logic.
	 * 
	 * GCloud CRON Configuration:
	 * This endpoint should be configured in GCloud Cloud Scheduler (CRON) to periodically retry failed invoice uploads.
	 * 
	 * Recommended CRON schedule: Every 15-30 minutes
	 * Example CRON expression: Every 15 minutes (format: minute hour day month weekday)
	 * 
	 * API Endpoint: POST /rest/order/oms/invoice/retry-upload
	 * Required Header: authorization-token (Internal authentication token)
	 * 
	 * What it does:
	 * - Finds all tracks with invoice_upload_status = 'FAILED' and invoice_upload_attempts < 10
	 * - Retries sending invoice to Logistiq for each failed track
	 * - Does NOT retry National ID uploads (National ID is one-time attempt only)
	 * 
	 * Note: Not @Transactional to avoid long-lived transactions during HTTP calls
	 */
	@Override
	public void retryFailedInvoiceUploads() {
		LOGGER.info("Starting CRON job to retry failed invoice uploads");

		try {
			// Find tracks with failed status and attempts < 10
			// Exclude PENDING to avoid race conditions (PENDING means upload is in progress)
			List<SalesShipmentTrack> failedTracks = shipmentTrackerRepository
					.findByInvoiceUploadStatusInAndInvoiceUploadAttemptsLessThan(
							Arrays.asList(STATUS_FAILED),
							MAX_RETRY_ATTEMPTS);

			if (CollectionUtils.isEmpty(failedTracks)) {
				LOGGER.info("No failed invoice uploads to retry");
				return;
			}

			LOGGER.info("Found " + failedTracks.size() + " tracks with failed invoice uploads");

			int successCount = 0;
			int failureCount = 0;

			for (SalesShipmentTrack track : failedTracks) {
				// Use self-injected service to ensure @Transactional works via Spring proxy
				RetryResult result = invoiceSharingService.retryInvoiceUploadForTrack(track);
				if (result.isSuccess()) {
					successCount++;
				} else {
					failureCount++;
				}
			}

			LOGGER.info("CRON retry completed - Success: " + successCount + ", Failed: " + failureCount);

		} catch (Exception e) {
			LOGGER.error("Error in CRON job for retrying failed invoice uploads", e);
		}
	}

	/**
	 * Retry invoice upload for a single track
	 * Note: Not @Transactional to avoid holding transaction during HTTP calls
	 * sendInvoiceToLogistiq has its own @Transactional for database operations
	 */
	@Override
	public RetryResult retryInvoiceUploadForTrack(SalesShipmentTrack track) {
		SalesShipmentTrack managedTrack = null;
		try {
			managedTrack = reloadTrackWithRelations(track);
			
			SalesOrder order = validateAndGetOrder(managedTrack);
			if (order == null) {
				return new RetryResult(false);
			}

			initializeLazyCollectionsForRetry(order, managedTrack);

			String awb = validateAndGetAwb(managedTrack);
			if (awb == null) {
				return new RetryResult(false);
			}

			// Retry sending (runs in its own transaction)
			// Use null authorizationToken - will fallback to internal token
			boolean success = sendInvoiceToLogistiq(awb, order, managedTrack, null);
			return new RetryResult(success);

		} catch (Exception e) {
			return handleRetryException(track, managedTrack, e);
		}
	}

	/**
	 * Reload track to ensure it's managed and relationships are available
	 */
	private SalesShipmentTrack reloadTrackWithRelations(SalesShipmentTrack track) {
		return shipmentTrackerRepository.findById(track.getEntityId()).orElse(track);
	}

	/**
	 * Validate and get order from track
	 */
	private SalesOrder validateAndGetOrder(SalesShipmentTrack managedTrack) {
		SalesOrder order = managedTrack.getSalesOrder();
		if (order == null) {
			LOGGER.warn("Order not found for track: " + managedTrack.getEntityId());
			updateUploadStatusWithAttempt(managedTrack, new UploadStatusUpdate(STATUS_FAILED, "Order not found"));
			return null;
		}
		return order;
	}

	/**
	 * Initialize lazy collections that will be accessed
	 */
	private void initializeLazyCollectionsForRetry(SalesOrder order, SalesShipmentTrack managedTrack) {
		Hibernate.initialize(order.getSalesInvoices());
		if (managedTrack.getSplitSalesOrder() != null) {
			Hibernate.initialize(managedTrack.getSplitSalesOrder());
			SplitSalesOrder splitOrder = managedTrack.getSplitSalesOrder();
			if (splitOrder.getSplitSalesInvoices() != null) {
				Hibernate.initialize(splitOrder.getSplitSalesInvoices());
			}
		}
	}

	/**
	 * Validate and get AWB from track
	 */
	private String validateAndGetAwb(SalesShipmentTrack managedTrack) {
		String awb = getAwbFromTrack(managedTrack);
		if (StringUtils.isBlank(awb)) {
			LOGGER.warn("AWB not found for track: " + managedTrack.getEntityId());
			updateUploadStatusWithAttempt(managedTrack, new UploadStatusUpdate(STATUS_FAILED, "AWB not found"));
			return null;
		}
		return awb;
	}

	/**
	 * Handle exception during retry
	 */
	private RetryResult handleRetryException(SalesShipmentTrack track, SalesShipmentTrack managedTrack, Exception e) {
		LOGGER.error("Error retrying invoice upload for track: " + track.getEntityId(), e);
		if (managedTrack != null) {
			try {
				String errorMsg = truncateErrorMessage(e.getMessage());
				if (STATUS_PENDING.equals(managedTrack.getInvoiceUploadStatus())) {
					managedTrack.setInvoiceUploadStatus(STATUS_FAILED);
					managedTrack.setInvoiceUploadError(errorMsg);
					shipmentTrackerRepository.saveAndFlush(managedTrack);
				} else {
					updateUploadStatusWithAttempt(managedTrack, new UploadStatusUpdate(STATUS_FAILED, "Exception: " + errorMsg));
				}
			} catch (Exception updateEx) {
				LOGGER.error("Error updating track status after exception: " + track.getEntityId(), updateEx);
			}
		}
		return new RetryResult(false);
	}

	/**
	 * Get AWB from track
	 */
	private String getAwbFromTrack(SalesShipmentTrack track) {
		if (StringUtils.isNotBlank(track.getTrackNumber())) {
			return track.getTrackNumber();
		}
		if (StringUtils.isNotBlank(track.getAlphaAwb())) {
			return track.getAlphaAwb();
		}
		return null;
	}

	/**
	 * Helper class to hold retry result
	 */
}
