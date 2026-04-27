package org.styli.services.order.service.impl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.validation.Valid;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.styli.services.order.component.ConsulComponent;
import org.styli.services.order.component.GcpStorage;
import org.styli.services.order.converter.OmsorderentityConverter;
import org.styli.services.order.converter.OrderEntityConverter;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.exception.RollbackException;
import org.styli.services.order.helper.*;
import org.styli.services.order.model.Customer.CustomerEntity;
import org.styli.services.order.model.SalesOrder.SalesCreditmemo;
import org.styli.services.order.model.rma.AmastyRmaRequest;
import org.styli.services.order.model.rma.AmastyRmaRequestItem;
import org.styli.services.order.model.rma.AmastyStoreCredit;
import org.styli.services.order.model.rma.AmastyStoreCreditHistory;
import org.styli.services.order.model.sales.*;
import org.styli.services.order.pojo.*;
import org.styli.services.order.pojo.oms.BankSubmitFormRequest;
import org.styli.services.order.pojo.oms.BankSwiftCode;
import org.styli.services.order.pojo.oms.BankSwiftCodeMapperResponse;
import org.styli.services.order.pojo.order.*;
import org.styli.services.order.pojo.recreate.RecreateOrder;
import org.styli.services.order.pojo.recreate.RecreateOrderResponse;
import org.styli.services.order.pojo.recreate.RecreateOrderResponseDTO;
import org.styli.services.order.pojo.request.GetShipmentV3.*;
import org.styli.services.order.pojo.request.GetShipmentV3.alpha.Auth;
import org.styli.services.order.pojo.request.NavikAddressUpdateDTO;
import org.styli.services.order.pojo.request.Order.*;
import org.styli.services.order.pojo.request.PaymentCodeENUM;
import org.styli.services.order.pojo.request.ProductStatusRequest;
import org.styli.services.order.pojo.response.*;
import org.styli.services.order.pojo.response.Order.OmsOrderresponsedto;
import org.styli.services.order.pojo.response.Order.OmsUnfulfilmentResponse;
import org.styli.services.order.pojo.response.Order.OrderResponse;
import org.styli.services.order.pojo.response.Order.OrderResponseDTO;
import org.styli.services.order.pojo.response.Order.OrderTrackingResponse;
import org.styli.services.order.pojo.response.V3.*;
import org.styli.services.order.pojo.response.V3.Meta;
import org.styli.services.order.pojo.tax.TaxObject;
import org.styli.services.order.pojo.zatca.ZatcaConfig;
import org.styli.services.order.pojo.zatca.ZatcaInvoice;
import org.styli.services.order.pojo.zatca.ZatcaInvoiceResponse;
import org.styli.services.order.repository.SalesShipmentPackDetailsItemRepository;
import org.styli.services.order.repository.SalesShipmentPackDetailsRepository;
import org.styli.services.order.repository.SalesShipmentRepository;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.service.InvoiceSharingService;
import org.styli.services.order.service.NationalIdUploadService;
import org.styli.services.order.repository.Customer.CustomerEntityRepository;
import org.styli.services.order.repository.Rma.AmastyRmaRequestItemRepository;
import org.styli.services.order.repository.Rma.AmastyRmaRequestRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditHistoryRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditRepository;
import org.styli.services.order.repository.SalesOrder.ReturnShipmentTrackerRepository;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoItemRepository;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderItemRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderPaymentRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusHistoryRepository;
import org.styli.services.order.repository.SalesOrder.SellerConfigRepository;
import org.styli.services.order.repository.SalesOrder.ShipmentTrackerRepository;
import org.styli.services.order.repository.SellerShipmentPackDetailsItemRepository;
import org.styli.services.order.repository.SellerShipmentPackDetailsRepository;
import org.styli.services.order.repository.SalesOrder.SplitSalesOrderItemRepository;
import org.styli.services.order.repository.SalesOrder.SplitSalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SplitSellerOrderRepository;
import org.styli.services.order.repository.SalesOrder.SplitSellerShipmentItemRepository;
import org.styli.services.order.pojo.braze.BrazeDangerousGoods;
import org.styli.services.order.pojo.braze.BrazeDangerousGoods.Event;
import org.styli.services.order.pojo.braze.BrazeResponseBody;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.styli.services.order.repository.SalesOrder.SplitSellerShipmentRepository;
import org.styli.services.order.repository.SalesOrder.SplitSellerShipmentTrackRepository;
import org.styli.services.order.repository.SalesOrder.StatusChaneHistoryRepository;
import org.styli.services.order.repository.SalesOrder.SubSalesOrderItemRepository;
import org.styli.services.order.repository.*;
import org.styli.services.order.service.*;
import org.styli.services.order.utility.*;

import javax.persistence.EntityManager;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Component
public class SalesOrderServiceV3Impl implements SalesOrderServiceV3 {

	private static final String RETURN = "return";

	private static final String SHIPPING = "shipping";

	private static final String CITYADDRESS = "Riyadh";

	private static final String HELLO_KSA_STYLISHOP_COM = "hello.ksa@stylishop.com";

	private static final String COUNTRYCODE = "+966";

	private static final String PHONENUMBNER = "8001111090";

	private static final String WWW_STYLISHOP_COM = "www.stylishop.com";

	private static final String SA = "SA";

	private static final String RETAIL_CART_TRADING_CO_MAKHZAN2_NEW_WAREHOUSE_AL_BARIAH = "Retail Cart Trading Co., Makhzan2 New Warehouse, Al Bariah";

	public static final String AL_RIYADH = "Al-Riyadh";

	private static final Log LOGGER = LogFactory.getLog(SalesOrderServiceV3Impl.class);

	private static final String GENERATE_PDF_URI = "/v1/orders/generatePDF/";

	private static final ObjectMapper mapper = new ObjectMapper();

	private static final String[] HEADERS = {"increment_id", "store_id", "payfort_authorized", "authorization_capture", "customer_email", "created_at"};

	/**
	 * Get Authorization or authorization-token from current HTTP request for use in async invoice/generatePDF.
	 * Returns null if not in a request context (e.g. test or job).
	 */
	private String getAuthorizationTokenFromCurrentRequest() {
		try {
			RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
			if (attrs instanceof ServletRequestAttributes) {
				HttpServletRequest request = ((ServletRequestAttributes) attrs).getRequest();
				String token = request.getHeader("Authorization");
				if (StringUtils.isBlank(token)) {
					token = request.getHeader("authorization-token");
				}
				return StringUtils.isNotBlank(token) ? token.trim() : null;
			}
		} catch (Exception e) {
			// not in a request context
		}
		return null;
	}

	@Autowired
	SalesOrderRepository salesOrderRepository;

	@Autowired
	SplitSalesOrderRepository splitSalesOrderRepository;
	@Autowired
	SplitSalesOrderItemRepository splitSalesOrderItemRepository;

	@Autowired
	SplitSellerOrderRepository splitSellerOrderRepository;

	@Autowired
	OrderpushHelper orderpushHelper;

	@Autowired
	SplitOrderpushHelper splitOrderpushHelper;

	@Autowired
	SalesOrderService salesOrderService;

	@Autowired
	ZatcaServiceImpl zatcaServiceImpl;

	@Autowired
	EntityManager entityManager;

	@Autowired
	StaticComponents staticComponents;

	@Autowired
	OrderHelperV2 orderHelperV2;

	@Autowired
	OrderHelper orderHelper;

	@Autowired
	CustomerService customerService;

	@Autowired
	AmastyStoreCreditRepository amastyStoreCreditRepository;

	@Autowired
	SalesOrderPaymentRepository salesOrderPaymentRepository;

	@Autowired
	AmastyStoreCreditHistoryRepository amastyStoreCreditHistoryRepository;

	@Autowired
	StatusChaneHistoryRepository statusChaneHistoryRepository;

	@Autowired
	CustomerEntityRepository customerEntityRepository;

	@Autowired
	SellerConfigRepository sellerConfigRepository;

	@Autowired
	SellerConfigService sellerConfigService;

	@Autowired
	SplitSellerShipmentRepository splitSellerShipmentRepository;


	@Autowired
	SplitSellerShipmentTrackRepository splitSellerShipmentTrackRepository;

	@Autowired
	SellerShipmentPackDetailsRepository sellerShipmentPackDetailsRepository;

	@Autowired
	SellerShipmentPackDetailsItemRepository sellerShipmentPackDetailsItemRepository;

	@Autowired
	CommonServiceImpl commonService;

	@Autowired
	OrderEntityConverter orderEntityConverter;

	@Value("${shipping.navik.base.url}")
	private String shippingNavikBaseUrl;

	@Autowired
	@Qualifier("withoutEureka")
	private RestTemplate restTemplate;

	@Autowired
	@Qualifier("restTemplateBuilder")
	private RestTemplate restTemplateBuilder;

	@Value("${magento.integration.token}")
	private String magentoIntegrationToken;

	@Value("${magento.base.url}")
	private String magentoBaseUrl;

	@Value("${service.navik.base.url}")
	private String serviceNavikBaseUrl;

	@Value("${auth.internal.header.bearer.token}")
	private String internalHeaderBearerToken;

	@Autowired
	ShipmentTrackerRepository shipmentTrackerRepository;

	@Autowired
	ReturnShipmentTrackerRepository returnShipmentTrackerRepository;

	@Autowired
	SalesShipmentRepository salesShipmentRepository;

	@Autowired
	private SalesShipmentPackDetailsRepository salesShipmentPackDetailsRepository;

	@Autowired
	private ShippingLabelUrlService shippingLabelUrlService;

	@Autowired
	private SalesShipmentPackDetailsItemRepository salesShipmentPackDetailsItemRepository;

	@Autowired
	private SplitSellerShipmentItemRepository splitSellerShipmentItemRepository;

	@Autowired
	PaymentDtfHelper paymentDtfHelper;

	@Autowired
	AmastyRmaRequestRepository amastyRmaRequestRepository;

	@Autowired
	AmastyRmaRequestItemRepository amastyRmaRequestItemRepository;

	@Autowired
	NationalIdUploadService nationalIdUploadService;

	@Autowired
	InvoiceSharingService invoiceSharingService;

	@Autowired
	PaymentRefundHelper paymentDtfRefundHelper;

	@Autowired
	RMAHelper rmaHelper;

	@Autowired
	SalesCreditmemoRepository salesCreditmemoRepository;

	@Autowired
	SalesCreditmemoItemRepository salesCreditmemoItemRepository;

	@Autowired
	SubSalesOrderItemRepository subSalesOrderItemRepository;

	@Autowired
	SalesOrderGridRepository salesOrderGridRepository;

	@Autowired
	SalesOrderServiceV2Impl salesOrderServiceV2Impl;

	@Autowired
	NavikHelper navikHelper;

	@Autowired
	PaymentUtility paymentUtility;

	@Autowired
	SalesOrderStatusHistoryRepository salesOrderStatusHistoryRepository;

	@Autowired
	@Lazy
	EASServiceImpl eASServiceImpl;

	@Autowired
	private ConsulComponent consulComponent;

	@Value("${region.value}")
	private String regionValue;

	@Autowired
	private OmsorderentityConverter omsorderentityConverter;

	@Autowired
	private RmaUtil rmaUtil;

	@Autowired
	private SalesOrderItemRepository orderItemRepository;

	@Autowired
	private SalesOrderRepository orderRepository;

	@Autowired
	private OrderHelperV2 orderHelperService;

	@Autowired
	PubSubServiceImpl pubSubServiceImpl;

	@Autowired
	private GcpStorage gcpStorage;

	@Value("${braze.auth.token}")
	private String brazeAuthToken;

	@Value("${pubsub.topic.payfort.refund}")
	private String omsPayfortRefundTopic;

	@Value("${pubsub.topic.tamara.refund}")
	private String omsTamaraRefundTopic;

	@Value("${pubsub.topic.wallet.refund}")
	private String omsWalletRefundTopic;

	@Value("${pubsub.topic.shukran.refund}")
	private String omsShukranRefundTopic;

	@Value("${pubsub.topic.split.order}")
	private String splitOrderTopic;
	@Autowired
	OrderHelperV3 orderHelperV3;


	@Override
	public void findSalesOrdersAndSalesGrid(Customer customer) {

		try {

			List<SalesOrder> salesOrderList = null;
			List<SalesOrderGrid> salesOrderGridList = null;
			if (customer.isWhatsApp()) {
				LOGGER.info("whtsapp request:" + customer.getMobileNumber());
				salesOrderList = salesOrderRepository.findGuestOrdersByTelephone(customer.getMobileNumber());
				salesOrderGridList = salesOrderGridRepository.findGuestOrdersByTelephone(customer.getMobileNumber());

			} else if (StringUtils.isBlank(customer.getUpdatedEmail())) {
				LOGGER.info("update email is blank");
				salesOrderList = salesOrderRepository
						.findByCustomerEmailAndCustomerId(customer.getEmail().toLowerCase(), null);
				salesOrderGridList = salesOrderGridRepository.findByCustomerEmail(customer.getEmail().toLowerCase());

			} else {
				salesOrderList = salesOrderRepository.findByCustomerEmail(customer.getEmail().toLowerCase());
				salesOrderGridList = salesOrderGridRepository.findByCustomerEmail(customer.getEmail().toLowerCase());

			}

			if (CollectionUtils.isNotEmpty(salesOrderList)) {

				for (final SalesOrder salesOrder : salesOrderList) {


					salesOrder.setCustomerIsGuest(0);
					String sanitizedFirstName = normalizeAndRemoveUnsupportedCharacters(customer.getFirstName());
					String sanitizedLastName = normalizeAndRemoveUnsupportedCharacters(customer.getLastName());

					salesOrder.setCustomerFirstname(sanitizedFirstName);
					salesOrder.setCustomerLastname(sanitizedLastName);
					salesOrder.setCustomerGroupId(1);
					if (customer.isUpdateEmail() && null != customer.getUpdatedEmail()) {

						salesOrder.setCustomerEmail(customer.getUpdatedEmail());

					}
					if (null == salesOrder.getCustomerId()) {

						salesOrder.setCustomerId(customer.getCustomerId());
					}
					LOGGER.info("customer id of order:" + salesOrder.getCustomerId());
					if (customer.isWhatsApp() && null == salesOrder.getCustomerId()) {
						salesOrderGridList = salesOrderGridRepository
								.findByCustomerEmail(salesOrder.getCustomerEmail());
					}
					salesOrderRepository.saveAndFlush(salesOrder);
				}
			}
			if (CollectionUtils.isNotEmpty(salesOrderGridList)) {

				for (final SalesOrderGrid salesOrderGrid : salesOrderGridList) {

					if (customer.isWhatsApp() && (null == salesOrderGrid.getCustomerId() || salesOrderGrid.getCustomerId().equals(0))) {
						LOGGER.info("What app Order Grid update customerid: " + customer.getCustomerId() + " of order: " + salesOrderGrid.getEntityId());
						salesOrderGrid.setCustomerId(customer.getCustomerId());
					}

					String sanitizedCustomerName = normalizeAndRemoveUnsupportedCharacters(customer.getFirstName()) + " " + normalizeAndRemoveUnsupportedCharacters(customer.getLastName());
					salesOrderGrid.setCustomerName(sanitizedCustomerName);
					salesOrderGrid.setCustomerGroup("1");
					salesOrderGridRepository.saveAndFlush(salesOrderGrid);
				}
			}
		} catch (Exception e) {
			LOGGER.error("Exception occurred:" + e.getMessage());
		}
	}

	@Override
	@Transactional
	public GetShipmentV3Response getShipmentV3(String orderCode, String shipmentCode) {

		GetShipmentV3Response response = new GetShipmentV3Response();
		GetShipmentV3ResponseBody responseBody = new GetShipmentV3ResponseBody();

		// Check if this is a split order based on order code pattern
		boolean isSplitOrder = false;
		if (orderCode.toUpperCase().contains("-G")) {
			isSplitOrder = true;
		}
		if (orderCode.toUpperCase().contains("-L")) {
			isSplitOrder = true;
		}

		SalesOrder order;
		SplitSalesOrder splitSalesOrder = null;
		SalesShipment salesShipMent = salesShipmentRepository.findByIncrementId(shipmentCode);

		if (isSplitOrder) {
			// Handle split order
			splitSalesOrder = splitSalesOrderRepository.findByIncrementId(orderCode);
			if (splitSalesOrder != null) {
				order = splitSalesOrder.getSalesOrder();
			} else {
				order = null;
			}
		} else {
			// Handle regular order
			order = salesOrderRepository.findByIncrementId(orderCode);
		}

		GetShipmentV3Request navikRequest = new GetShipmentV3Request();

		if (null == order) {
			response.setHasError(true);
			response.setErrorMessage("Invalid request");
			return response;
		} else if (null == salesShipMent) {
			response.setHasError(true);
			response.setErrorMessage("Shipment has not generated");
			return response;
		} else if (isSplitOrder) {
			// For split orders, check split sales invoices
			if (org.apache.commons.collections.CollectionUtils.isEmpty(splitSalesOrder.getSplitSalesInvoices())) {
				response.setHasError(true);
				response.setErrorMessage("invoice has not generated");
				return response;
			}
		} else if (org.apache.commons.collections.CollectionUtils.isEmpty(order.getSalesInvoices())) {
			response.setHasError(true);
			response.setErrorMessage("invoice has not generated");
			return response;
		}

		// Check if AWB already exists for this shipment
		List<SalesShipmentTrack> existingTracks = shipmentTrackerRepository.findByParentId(salesShipMent.getEntityId());
		if (existingTracks != null && !existingTracks.isEmpty()) {
			// Find the track with AWB (alphaAwb or trackNumber)
			SalesShipmentTrack existingTrack = existingTracks.stream()
					.filter(track -> (track.getAlphaAwb() != null && !track.getAlphaAwb().isEmpty()) 
							|| (track.getTrackNumber() != null && !track.getTrackNumber().isEmpty()))
					.findFirst()
					.orElse(null);
			
		if (existingTrack != null) {
			// AWB already exists, return from database
			responseBody.setTransporter(existingTrack.getTitle());
			responseBody.setAwbNumber(existingTrack.getTrackNumber());
			
			// CRITICAL: Use getOrRefreshShippingLabelUrl to automatically refresh expired URLs
			final String shippingLabelUrl;
			try {
			shippingLabelUrl = shippingLabelUrlService.getOrRefreshShippingLabelUrl(
				existingTrack, 
				SalesShipmentTrack.class
			);
			responseBody.setShippingLabelUrl(shippingLabelUrl);
				LOGGER.info("Returning GCS signed URL for existing AWB (refreshed if needed): " + existingTrack.getTrackNumber());
			} catch (Exception e) {
				LOGGER.error("Failed to get/refresh shipping label URL for existing AWB: " + e.getMessage(), e);
				responseBody.setShippingLabelUrl("");
			}
			
			String encodeValue = null;
			if (null != Constants.orderCredentials && Constants.orderCredentials.getWms().isNewInVoiceEncode()) {
				encodeValue = order.getEntityId().toString().concat("#").concat(order.getCustomerEmail());
			} else {
				encodeValue = order.getEntityId().toString();
			}
			String encoded = Base64.getEncoder().encodeToString(encodeValue.getBytes());
			responseBody.setInvoiceUrl(Constants.orderCredentials.getOrderDetails().getOmsServicePublicBaseUrl()
					+ GENERATE_PDF_URI + encoded);
			
			response.setAwbNumber(responseBody.getAwbNumber());
			response.setShippingLabelUrl(responseBody.getShippingLabelUrl());
			response.setTransporter(responseBody.getTransporter());
			
			// Populate box label details from pack details
			List<BoxLabelDetails> boxLabelDetails = buildBoxLabelDetails(salesShipMent);
			response.setBoxLabelDetails(boxLabelDetails);
			
			// Trigger async National ID upload (Oman only) and async invoice upload
			try {
				String awb = responseBody.getAwbNumber();
				if (awb != null && !awb.isEmpty()) {
					nationalIdUploadService.scheduleNationalIdUploadAsync(awb, order, existingTrack);
					invoiceSharingService.sendInvoiceToLogistiqAsync(awb, order, existingTrack, getAuthorizationTokenFromCurrentRequest());
				}
			} catch (Exception e) {
				LOGGER.error("Error triggering async National ID or invoice upload for existing AWB: " + responseBody.getAwbNumber(), e);
			}
			
			return response;
		}
		}

		try {
			ResponseEntity<NavikResponse> navikResponse;
			
			// Check if this is an MPS shipment
			if(Constants.orderCredentials.getNavik().isAlphaEnabled()) {
				MpsOrderCreateRequest mpsRequest = buildMpsOrderCreateRequest(order, salesShipMent, null, Constants.orderCredentials.getNavik(), false, null, splitSalesOrder);
				ResponseEntity<MpsOrderCreateResponse> mpsResponse = createsMpsShipmentWithAlpha(mpsRequest);
				LOGGER.info("Alpha MPS response body:" + mapper.writeValueAsString(mpsResponse.getBody()));
				
				// Store MPS response data to pack details
				storeMpsResponseToPackDetails(salesShipMent, mpsResponse);
				
				navikResponse = transformMpsResponseToNavikResponse(mpsResponse);
			} else {
				// Build the request using existing methods
				buildNavikRequest(order, salesShipMent, navikRequest, splitSalesOrder, isSplitOrder);
				navikResponse = createsShipmentWithNavik(navikRequest);
			}
			
			return processForwardShipmentResponse(responseBody, order, salesShipMent, navikResponse, splitSalesOrder);

		} catch (RestClientException | JsonProcessingException e) {
			LOGGER.error("exception occurred during navik call:" + e.getMessage());
			response.setHasError(true);
			response.setErrorMessage(e.getMessage());
		}

		return response;
	}

	private void buildNavikRequest(SalesOrder order, SalesShipment salesShipMent, GetShipmentV3Request navikRequest, SplitSalesOrder splitSalesOrder, boolean isSplitOrder) {
		NavikAddress addressDetails = null;
		Navikinfos navikInfos = null;

		List<Stores> stores = Constants.getStoresList();
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId())).findAny()
				.orElse(null);
		if (null != store) {
			navikInfos = Constants.orderCredentials.getNavik().getDropOffDetails().stream()
					.filter(e -> null != e.getWebSiteId() && e.getWebSiteId().intValue() == store.getWebsiteId())
					.findFirst().orElse(null);
			try {
				LOGGER.info("navik address mapper:" + mapper.writeValueAsString(navikInfos));
			} catch (JsonProcessingException e1) {
				LOGGER.error("exception occoured during navik addrss catch:" + e1.getMessage());
			}
			if (navikInfos != null && (order.getStoreId().equals(1) || order.getStoreId().equals(3)
					|| order.getStoreId().equals(7) || order.getStoreId().equals(11))) {

				LOGGER.info("Store value:" + order.getStoreId());
				navikInfos.setDutyFeePaid("");
			}
			if (navikInfos != null) {
				addressDetails = navikInfos.getAddressDetails();
			}

			if (Objects.nonNull(navikInfos) && (order.getStoreId().equals(7) || order.getStoreId().equals(11))
					&& Integer.valueOf(110).equals(order.getSubSalesOrder().getWarehouseLocationId())) {

				Navikinfos navikInfo = Constants.orderCredentials.getNavik().getDropOffDetails().stream()
						.filter(e -> null != e.getWebSiteId() && e.getWebSiteId().intValue() == 1).findFirst()
						.orElse(null);

				if (Objects.nonNull(navikInfo)) {
					navikInfo.setDutyFeePaid("S");
					addressDetails = navikInfo.getAddressDetails();
					navikInfos = navikInfo;
					LOGGER.info("KSA address override on UAE. Duty fee is set. Details : " + navikInfos);
				}
			}
		}

		PickupInfo pickupInfo = setPickupInfo(navikRequest, addressDetails);

		ReturnInfo returnInfo = new ReturnInfo();

		BeanUtils.copyProperties(pickupInfo, returnInfo);

		navikRequest.setReturnInfo(returnInfo);

		setDropInfo(order, navikRequest);

		setAdditional(order, navikRequest, navikInfos);

		// Use different shipment details method based on order type
		if (isSplitOrder) {
			setSplitOrderShipmentDetails(splitSalesOrder, navikRequest, navikInfos);
		} else {
			setShipmentDetails(order, navikRequest, navikInfos);
		}
	}

	private ResponseEntity<NavikResponse> createsShipmentWithNavik(GetShipmentV3Request navikRequest)
			throws JsonProcessingException {
		String url = Constants.orderCredentials.getOrderDetails().getNavikBase() + "/api/create-shipment";
		LOGGER.info("navik url:" + url);
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
		HttpEntity<GetShipmentV3Request> requestBody = new HttpEntity<>(navikRequest, requestHeaders);
		LOGGER.info("navik forward request body:" + mapper.writeValueAsString(requestBody.getBody()));
		ResponseEntity<NavikResponse> navikResponse = restTemplate.exchange(url, HttpMethod.POST, requestBody,
				NavikResponse.class);

		LOGGER.info("navik forward response body:" + mapper.writeValueAsString(navikResponse.getBody()));
		return navikResponse;
	}

	private ResponseEntity<NavikResponse> createsShipmentWithAlpha(GetShipmentV3Request navikRequest)
			throws JsonProcessingException {
		String authToken = alphaAuthToken();
		String url = Constants.orderCredentials.getOrderDetails().getAlphaBaseUrl() + "/v2/orders/order-create";
		LOGGER.info("Alpha url:" + url);
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.add(Constants.AUTHORIZATION_HEADER, "Bearer " + authToken);

		HttpEntity<GetShipmentV3Request> requestBody = new HttpEntity<>(navikRequest, requestHeaders);
		LOGGER.info("Alpha request body:" + mapper.writeValueAsString(requestBody.getBody()));
		ResponseEntity<NavikResponse> navikResponse = restTemplateBuilder.exchange(url, HttpMethod.POST, requestBody,
				NavikResponse.class);
		LOGGER.info("Alpha response body:" + navikResponse.getBody());
		return navikResponse;
	}

	private ResponseEntity<MpsOrderCreateResponse> createsMpsShipmentWithAlpha(MpsOrderCreateRequest mpsRequest)
			throws JsonProcessingException {
		String authToken = alphaAuthToken();
		String url = Constants.orderCredentials.getOrderDetails().getAlphaBaseUrl() + "/v1/orders/order-create";
		LOGGER.info("Alpha MPS url:" + url);
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.add(Constants.AUTHORIZATION_HEADER, "Bearer " + authToken);

        String originalStreet = mpsRequest.getCustomerStreetName();
        String originalBuilding = mpsRequest.getCustomerBuildingNumber();
        String normalizedStreet = org.apache.commons.lang3.StringUtils.left(org.apache.commons.lang3.StringUtils.defaultString(originalStreet), 100);
        String normalizedBuilding = org.apache.commons.lang3.StringUtils.left(org.apache.commons.lang3.StringUtils.defaultString(originalBuilding), 100);
        
        LOGGER.info("Normalizing MPS address: street before='" + originalStreet + "' after='" + normalizedStreet + "'");
        LOGGER.info("Normalizing MPS address: buildingNumber before='" + originalBuilding + "' after='" + normalizedBuilding + "'");
        
        mpsRequest.setCustomerStreetName(normalizedStreet);
        mpsRequest.setCustomerBuildingNumber(normalizedBuilding);

		LOGGER.info("Alpha MPS request body:" + mapper.writeValueAsString(mpsRequest));
		
		HttpEntity<MpsOrderCreateRequest> requestBody = new HttpEntity<>(mpsRequest, requestHeaders);
		
		try {
			ResponseEntity<MpsOrderCreateResponse> response = restTemplateBuilder.exchange(url, HttpMethod.POST, requestBody, MpsOrderCreateResponse.class);
			LOGGER.info("Alpha MPS response body not parsed:" + response.getBody());
			
			// Validate response before returning
			if (response.getBody() != null && response.getBody().getData() != null && !response.getBody().getData().isEmpty()) {
				MpsOrderCreateResponse.MpsOrderData firstData = response.getBody().getData().get(0);
				if (firstData.getUrl() == null || firstData.getCpAwb() == null) {
					String errorMessage = "MPS shipment creation failed: ";
					if (firstData.getMessage() != null && !firstData.getMessage().isEmpty()) {
						errorMessage += firstData.getMessage();
					} else if (firstData.getError() != null && !firstData.getError().isEmpty()) {
						errorMessage += firstData.getError();
					} else if (response.getBody().getMessage() != null && !response.getBody().getMessage().isEmpty()) {
						errorMessage += response.getBody().getMessage();
					} else {
						errorMessage += "URL or CP AWB is null";
					}
					
					// Create error response instead of throwing exception
					MpsOrderCreateResponse errorResponse = new MpsOrderCreateResponse();
					errorResponse.setStatus(false);
					errorResponse.setMessage(errorMessage);
					return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
				}
			}
			
			return response;
		} catch (HttpClientErrorException e) {
			LOGGER.info("Alpha catch response:" + e);
			if (e.getResponseBodyAsString() != null && !e.getResponseBodyAsString().isEmpty()) {
				MpsOrderCreateResponse errorResponse = mapper.readValue(e.getResponseBodyAsString(), MpsOrderCreateResponse.class);
				return ResponseEntity.status(e.getStatusCode()).body(errorResponse);
			} else {
				throw new RuntimeException("Alpha MPS API error: " + e.getStatusCode() + " - No response body available");
			}
		}
	}
	/**
	 * Create first mile shipment with Beta
	 * @param mpsRequest
	 * @return
	 * @throws JsonProcessingException
	 */
	private ResponseEntity<MpsOrderCreateResponse> createFirstMileShipmentWithBeta(MpsOrderCreateRequest mpsRequest)
			throws JsonProcessingException {
		String authToken = betaAuthToken();
		String url = Constants.orderCredentials.getOrderDetails().getBetaBaseUrl() + "/v1/orders/order-create";
		LOGGER.info("Beta first mile url:" + url);
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.add(Constants.AUTHORIZATION_HEADER, "Bearer " + authToken);

		LOGGER.info("Beta first mile request body:" + mapper.writeValueAsString(mpsRequest));
		
		HttpEntity<MpsOrderCreateRequest> requestBody = new HttpEntity<>(mpsRequest, requestHeaders);
		
		try {
			ResponseEntity<MpsOrderCreateResponse> response = restTemplateBuilder.exchange(url, HttpMethod.POST, requestBody, MpsOrderCreateResponse.class);
			LOGGER.info("Beta first mile response body not parsed:" + response.getBody());
			
			// Validate response before returning
			if (response.getBody() != null && response.getBody().getData() != null && !response.getBody().getData().isEmpty()) {
				MpsOrderCreateResponse.MpsOrderData firstData = response.getBody().getData().get(0);
				if (firstData.getUrl() == null || firstData.getCpAwb() == null) {
					String errorMessage = "first mile shipment creation failed: ";
					if (firstData.getMessage() != null && !firstData.getMessage().isEmpty()) {
						errorMessage += firstData.getMessage();
					} else if (firstData.getError() != null && !firstData.getError().isEmpty()) {
						errorMessage += firstData.getError();
					} else if (response.getBody().getMessage() != null && !response.getBody().getMessage().isEmpty()) {
						errorMessage += response.getBody().getMessage();
					} else {
						errorMessage += "URL or CP AWB is null";
					}
					
					// Create error response instead of throwing exception
					MpsOrderCreateResponse errorResponse = new MpsOrderCreateResponse();
					errorResponse.setStatus(false);
					errorResponse.setMessage(errorMessage);
					return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
				}
			}
			
			return response;
		} catch (HttpClientErrorException e) {
			LOGGER.info("Beta first mile catch response:" + e);
			if (e.getResponseBodyAsString() != null && !e.getResponseBodyAsString().isEmpty()) {
				MpsOrderCreateResponse errorResponse = mapper.readValue(e.getResponseBodyAsString(), MpsOrderCreateResponse.class);
				return ResponseEntity.status(e.getStatusCode()).body(errorResponse);
			} else {
				throw new RuntimeException("Beta first mile API error: " + e.getStatusCode() + " - No response body available");
			}
		}
	}

	private ResponseEntity<NavikResponse> transformMpsResponseToNavikResponse(ResponseEntity<MpsOrderCreateResponse> mpsResponse) 
			throws JsonProcessingException {
		NavikResponse navikResponse = new NavikResponse();
		Meta meta = new Meta();
		Result result = new Result();
		
		try {
			MpsOrderCreateResponse response = mpsResponse.getBody();
			
			// Check if this is an error response (status is false or HTTP status is not 2xx)
			if (response != null && response.getData() == null && !response.getStatus()) {
				
				meta.setSuccess(false);
				meta.setStatus(mpsResponse.getStatusCode().value());
				meta.setMessage(response != null && response.getMessage() != null ? response.getMessage() : "MPS shipment creation failed");
				result.setStatus(false);
				
			} else if (response != null && response.getData() != null && !response.getData().isEmpty() 
				&& response.getData().get(0).getAlphaAwb() != null && !response.getData().get(0).getAlphaAwb().isEmpty()
				&& response.getData().get(0).getUrl() != null && !response.getData().get(0).getUrl().isEmpty()) {
				MpsOrderCreateResponse.MpsOrderData firstData = response.getData().get(0);
				
				meta.setSuccess(firstData.getStatus() != null ? firstData.getStatus() : false);
				meta.setStatus(mpsResponse.getStatusCode().value());
				meta.setMessage(firstData.getMessage() != null ? firstData.getMessage() : "MPS Response");
				
				result.setStatus(firstData.getStatus() != null ? firstData.getStatus() : false);
				result.setWaybill(firstData.getCpAwb() != null ? firstData.getCpAwb() : "");
				result.setCourier_name(firstData.getCourierName() != null ? firstData.getCourierName() : "");
				result.setLabel(firstData.getUrl() != null ? firstData.getUrl() : "");
				result.setAlphaAwb(firstData.getAlphaAwb() != null ? firstData.getAlphaAwb() : "");
			} else {
				meta.setSuccess(false);
				meta.setStatus(mpsResponse.getStatusCode().value());
				meta.setMessage("No data found in MPS response");
				result.setStatus(false);
			}
		} catch (Exception e) {
			LOGGER.error("Error parsing MPS response: " + e.getMessage());
			meta.setSuccess(false);
			meta.setStatus(mpsResponse.getStatusCode().value());
			meta.setMessage("Error parsing MPS response: " + e.getMessage());
			result.setStatus(false);
		}
		
		navikResponse.setMeta(meta);
		navikResponse.setResult(result);
		
		return new ResponseEntity<>(navikResponse, mpsResponse.getHeaders(), mpsResponse.getStatusCode());
	}

	private void storeMpsResponseToPackDetails(SalesShipment salesShipment, ResponseEntity<MpsOrderCreateResponse> mpsResponse) {
		try {
			MpsOrderCreateResponse response = mpsResponse.getBody();
			
			if (response != null && response.getData() != null && !response.getData().isEmpty()) {
				MpsOrderCreateResponse.MpsOrderData firstData = response.getData().get(0);
				
				// Get the main response data
				String alphaAwb = firstData.getAlphaAwb() != null ? firstData.getAlphaAwb() : "";
				String carrierShippingLabelUrl = firstData.getUrl() != null ? firstData.getUrl() : "";
				if (org.apache.commons.lang.StringUtils.isBlank(carrierShippingLabelUrl)) {
					LOGGER.warn("MPS response has empty label URL; skipping GCS processing for pack details to avoid rollback-only.");
				}
				
				// Get child AWBs from label_data
				List<String> childAwbs = new ArrayList<>();
				if (firstData.getLabelData() != null && firstData.getLabelData().getChildAwb() != null) {
					childAwbs = firstData.getLabelData().getChildAwb();
				}
					
				// Get existing pack details for this shipment
				List<SalesShipmentPackDetails> packDetails = salesShipmentPackDetailsRepository.findByShipmentId(salesShipment.getEntityId());
				if (packDetails != null && !packDetails.isEmpty()) {						
					List<SalesShipmentPackDetails> shuffledPackDetails = new ArrayList<>(packDetails);
					Collections.shuffle(shuffledPackDetails);

					for (int i = 0; i < shuffledPackDetails.size(); i++) {
						SalesShipmentPackDetails packDetail = shuffledPackDetails.get(i);

						// Assign child AWB if available, otherwise fall back to parent AWB
						if (i < childAwbs.size()) {
							String childAwb = childAwbs.get(i);
							packDetail.setWayBill(childAwb);
						} else {
							packDetail.setWayBill(alphaAwb);
						}
						
						// CRITICAL: Process box label through GCS (same logic as seller shipments)
						// This ensures box labels are stored in our GCS bucket with signed URLs
						if (org.apache.commons.lang.StringUtils.isNotBlank(carrierShippingLabelUrl)) {
							try {
								String signedUrl = shippingLabelUrlService.processAndSaveShippingLabel(
									carrierShippingLabelUrl,
									packDetail,
									SalesShipmentPackDetails.class,
									null  // Use default bucket from config
								);
								LOGGER.info("Successfully processed box label to GCS for pack detail: " + packDetail.getEntityId());
							} catch (Exception e) {
							LOGGER.error("Failed to process box label to GCS for pack detail, using carrier URL: " + e.getMessage(), e);
							// Fallback: use original carrier URL
							packDetail.setShippingLabel(carrierShippingLabelUrl);
							packDetail.setUpdatedAt(new Timestamp(new Date().getTime()));
							salesShipmentPackDetailsRepository.save(packDetail);
						}
						}
					}
				}
			}
		} catch (Exception e) {
			LOGGER.error("Error storing MPS response data to pack details: " + e.getMessage(), e);
		}
	}

	private MpsOrderCreateRequest buildMpsOrderCreateRequest(SalesOrder order, SalesShipment salesShipment, 
			GetShipmentV3Request navikRequest, NavikDetails navik, boolean isReturn, AmastyRmaRequest amastyRmaRequest, SplitSalesOrder splitSalesOrder) {
		MpsOrderCreateRequest mpsRequest = new MpsOrderCreateRequest();
		
		// Set customer details from salesOrderAddress
		SalesOrderAddress shippingAddress = order.getSalesOrderAddress().stream()
				.filter(e -> e.getAddressType().equalsIgnoreCase(SHIPPING)).findAny().orElse(null);
		// Set customer address fields using the same logic as setDropInfo
		if (shippingAddress != null) {
			mpsRequest.setCustomerEmail(shippingAddress.getEmail() != null ? shippingAddress.getEmail() : "");
			String customerName = (shippingAddress.getFirstname() != null ? shippingAddress.getFirstname() : "") + 
				" " + (shippingAddress.getLastname() != null ? shippingAddress.getLastname() : "");
			mpsRequest.setCustomerName(customerName);
			// Set basic address fields
			mpsRequest.setCustomerAddress(escapeAmpersand(shippingAddress.getStreet() != null ? shippingAddress.getStreet() : ""));
			mpsRequest.setCustomerAddressLandmark(shippingAddress.getNearestLandmark() != null ? shippingAddress.getNearestLandmark() : "");
            if(Constants.orderCredentials.getOrderDetails().getEnableKSAAddressSupport()){
                mpsRequest.setCustomerUnitNumber(shippingAddress.getUnitNumber() != null ? shippingAddress.getUnitNumber() : "");
                mpsRequest.setShortAddress(shippingAddress.getShortAddress() != null ? shippingAddress.getShortAddress() : "");
                String streetName = shippingAddress.getStreet() != null ? shippingAddress.getStreet() : "";
                if (streetName.length() > 100) {
                	streetName = streetName.substring(0, 100);
                }
                mpsRequest.setCustomerStreetName(streetName);
                String buildingNumber = shippingAddress.getBuildingNumber() != null ? shippingAddress.getBuildingNumber() : "";
                if (buildingNumber.length() > 100) {
                	buildingNumber = buildingNumber.substring(0, 100);
                }
                mpsRequest.setCustomerBuildingNumber(buildingNumber);
                mpsRequest.setCustomerZipCode(shippingAddress.getPostalCode() != null ? shippingAddress.getPostalCode() : "");
            }
			// Apply the same address logic as setDropInfo
			boolean arabicTextCheck = checkArabicTextCheck(shippingAddress.getCity());

			if (Boolean.TRUE.equals(OrderConstants.CHECKENGLISHSTORE.get(order.getStoreId()))
					&& !arabicTextCheck) {
				// English store logic
				mpsRequest.setCustomerCity(shippingAddress.getCity() != null ? shippingAddress.getCity() : "");
				mpsRequest.setCustomerCountryCode(shippingAddress.getCountryId() != null ? shippingAddress.getCountryId() : "");
				mpsRequest.setCustomerPostalCode(shippingAddress.getArea() != null ? shippingAddress.getArea() : "");
			} else {
				// Arabic store logic
				try {
					AddressDetails addressDetails = navikHelper.getArabicAddressDetails(shippingAddress);
					// Use Navik city if non-blank, else fallback to shipping address city (fixes Logistiq "Customer City can not be empty" for e.g. Oman)
					String customerCity = addressDetails.getCityName();
					if (StringUtils.isBlank(customerCity)) {
						customerCity = StringUtils.defaultString(shippingAddress.getCity());
					}
					mpsRequest.setCustomerCity(customerCity);

					if (null != shippingAddress.getLatitude() && null != shippingAddress.getLongitude()) {
						mpsRequest.setCustomerPostalCode(shippingAddress.getArea() != null ? shippingAddress.getArea() : "");
					} else {
						mpsRequest.setCustomerPostalCode(addressDetails.getAreaName() != null ? addressDetails.getAreaName() : "");
					}

				} catch (JSONException e1) {
					LOGGER.error("error in json parsing during fetch arabic address");
					// Fallback to basic values
					mpsRequest.setCustomerCity(shippingAddress.getCity() != null ? shippingAddress.getCity() : "");
					mpsRequest.setCustomerPostalCode(shippingAddress.getArea() != null ? shippingAddress.getArea() : "");
				}
			}

			// Handle phone number parsing (same logic as setDropInfo)
			if (null != shippingAddress.getTelephone() && shippingAddress.getTelephone().contains(" ")
					&& ArrayUtils.isNotEmpty(shippingAddress.getTelephone().split(" "))
					&& shippingAddress.getTelephone().split(" ").length > 0) {
				mpsRequest.setCustomerPhone(shippingAddress.getTelephone().split(" ")[1]);
				mpsRequest.setCustomerPhoneCode(shippingAddress.getTelephone().split(" ")[0]);
			} else {
				mpsRequest.setCustomerPhone(shippingAddress.getTelephone() != null ? shippingAddress.getTelephone() : "");
				mpsRequest.setCustomerPhoneCode("");
			}

			// Handle formatted address with latitude/longitude (same logic as setDropInfo)
			if (Objects.nonNull(shippingAddress) && null != shippingAddress.getLatitude()) {
				String formattedAddress = shippingAddress.getFormattedAddress();
				String postalCode = mpsRequest.getCustomerPostalCode();
				mpsRequest.setCustomerPostalCode(mpsRequest.getCustomerCity());
				if (StringUtils.isNotBlank(postalCode)) {
					mpsRequest.setCustomerAddress(escapeAmpersand(
							shippingAddress.getStreet().concat(" ").concat(postalCode) + " " + formattedAddress));
				}
			}

		} else {
			// Set default empty values when shippingAddress is null
			mpsRequest.setCustomerAddress("");
			mpsRequest.setCustomerCity("");
			mpsRequest.setCustomerCountryCode("");
			mpsRequest.setCustomerPostalCode("");
			mpsRequest.setCustomerPhone("");
			mpsRequest.setCustomerAddressLandmark("");
		}
		
		// Set international order fields only for international stores (non-KSA) fulfilled from warehouse 110
		boolean isInternationalOrder = !order.getStoreId().equals(1) && !order.getStoreId().equals(3) 
				&& Integer.valueOf(110).equals(order.getSubSalesOrder().getWarehouseLocationId());
		
		if (isInternationalOrder) {
			// Override customer country code for international orders
			if (shippingAddress != null) {
				mpsRequest.setCustomerCountryCode(shippingAddress.getCountryId() != null ? shippingAddress.getCountryId() : "");
			}
			
			// Common international order fields
			mpsRequest.setCustomValue(1);
			mpsRequest.setCustomCurrencyCode(order.getOrderCurrencyCode() != null ? order.getOrderCurrencyCode() : "SAR");
			mpsRequest.setModeOfTransport("Road");
			mpsRequest.setCustomClearanceMode("Standard");
			mpsRequest.setHsCode("NA");	
		}
		
		if (isInternationalOrder) {
			// Override customer country code for international orders
			if (shippingAddress != null) {
				mpsRequest.setCustomerCountryCode(shippingAddress.getCountryId() != null ? shippingAddress.getCountryId() : "");
			}
			
			// Common international order fields
			mpsRequest.setCustomValue(1);
			mpsRequest.setCustomCurrencyCode(order.getOrderCurrencyCode() != null ? order.getOrderCurrencyCode() : "SAR");
			mpsRequest.setModeOfTransport("Road");
			mpsRequest.setCustomClearanceMode("Standard");
			mpsRequest.setHsCode("NA");	
		}

		// Set order details based on shipment type
		if (isReturn) {
			// Set invoice value for return orders using refund amount calculation
			if (amastyRmaRequest != null) {
				BigDecimal totalRefundAmount = getTotalRefundAmount(amastyRmaRequest, order);
				mpsRequest.setInvoiceValue(totalRefundAmount.doubleValue());
			}
			mpsRequest.setOrderRefNumber(amastyRmaRequest.getRmaIncId() != null ? amastyRmaRequest.getRmaIncId() : "");
			mpsRequest.setOrderType("PREPAID");
			mpsRequest.setDeliveryType("RETURN");
			
			// Use same MPS logic as forward flow - check if there's a salesShipment
			if (salesShipment != null) {
				mpsRequest.setIsMps(salesShipment.getIsMps() != null ? salesShipment.getIsMps() : false);
			} else {
				mpsRequest.setIsMps(false);
			}
		} else {
			mpsRequest.setInvoiceCurrencyCode(order.getOrderCurrencyCode() != null ? order.getOrderCurrencyCode() : "SAR");
			// Use split_sales_order incrementId for split orders, otherwise use sales_order incrementId
			if (splitSalesOrder != null && splitSalesOrder.getIncrementId() != null) {
				mpsRequest.setOrderRefNumber(splitSalesOrder.getIncrementId());
			} else {
			mpsRequest.setOrderRefNumber(order.getIncrementId() != null ? order.getIncrementId() : "");
			}
			// Calculate COD value and set order type based on payment method and order details
			BigDecimal codValue = BigDecimal.ZERO;
			SalesOrderPayment salesOrderPayment = order.getSalesOrderPayment().stream().findFirst().orElse(null);
			// Get invoice based on order type (split or regular)
			SalesInvoice salesInvoice = null;
			if (splitSalesOrder != null) {
				salesInvoice = splitSalesOrder.getSplitSalesInvoices().stream().findFirst().orElse(null);
			} else {
				salesInvoice = order.getSalesInvoices().stream().findFirst().orElse(null);
			}
			// Calculate grandToalInvoiced similar to shipment details
			BigDecimal grandToalInvoiced = BigDecimal.ZERO;
		
			if (salesOrderPayment != null) {
				
				if (salesOrderPayment.getMethod().equalsIgnoreCase(PaymentCodeENUM.CASH_ON_DELIVERY.getValue())) {
					mpsRequest.setOrderType("COD");
					
					// Regular order logic
					if (salesInvoice != null) {
						if (splitSalesOrder != null && splitSalesOrder.getGrandTotal() != null) {
							grandToalInvoiced = salesInvoice.getGrandTotal();
						} else if (order.getGrandTotal() != null) {
							grandToalInvoiced = salesInvoice.getGrandTotal();
						}
						if (splitSalesOrder != null && splitSalesOrder.getAmstorecreditAmount() != null) {
							grandToalInvoiced = grandToalInvoiced.add(salesInvoice.getAmstorecreditAmount());
						} else if (order.getAmstorecreditAmount() != null) {
							grandToalInvoiced = grandToalInvoiced.add(salesInvoice.getAmstorecreditAmount());
						}
					}
					
					// Apply COD calculation logic for regular orders
					// Check splitSalesOrder first if available
					if (splitSalesOrder != null) {
						if (splitSalesOrder.getSplitSubSalesOrder() != null && splitSalesOrder.getSplitSubSalesOrder().getIsUnfulfilmentOrder() == null) {
							codValue = splitSalesOrder.getGrandTotal();
						} else if (splitSalesOrder.getSplitSubSalesOrder() != null && splitSalesOrder.getSplitSubSalesOrder().getTotalShukranBurnedValueInCurrency() != null 
								&& splitSalesOrder.getSplitSubSalesOrder().getTotalShukranBurnedValueInCurrency().compareTo(BigDecimal.ZERO) > 0 
								&& grandToalInvoiced.compareTo(splitSalesOrder.getSplitSubSalesOrder().getTotalShukranBurnedValueInCurrency()) >= 0) {
							codValue = grandToalInvoiced.subtract(splitSalesOrder.getSplitSubSalesOrder().getTotalShukranBurnedValueInCurrency()).setScale(2, RoundingMode.HALF_UP);
						} else {
							codValue = grandToalInvoiced;
						}
					} else if (order.getSubSalesOrder() != null && order.getSubSalesOrder().getIsUnfulfilmentOrder() == null) {
						codValue = order.getGrandTotal();
					} else if (order.getSubSalesOrder() != null && order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency() != null 
							&& order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency().compareTo(BigDecimal.ZERO) > 0 
							&& grandToalInvoiced.compareTo(order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency()) >= 0) {
						codValue = grandToalInvoiced.subtract(order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency()).setScale(2, RoundingMode.HALF_UP);
					} else {
						codValue = grandToalInvoiced;
					}
				} else {
					mpsRequest.setOrderType("PREPAID");
					codValue = BigDecimal.ZERO;
				}
				if (null != salesInvoice) {
					if (null != order.getGrandTotal()) {
						grandToalInvoiced = salesInvoice.getGrandTotal();
					}
					if (null != order.getAmstorecreditAmount()) {
						grandToalInvoiced = grandToalInvoiced.add(salesInvoice.getAmstorecreditAmount());
					}
					mpsRequest.setInvoiceValue(grandToalInvoiced.doubleValue());
				}
			} else {
				mpsRequest.setOrderType("PREPAID");
				codValue = BigDecimal.ZERO;
			}
			
			mpsRequest.setCodValue(codValue.doubleValue());
			mpsRequest.setCodCurrencyCode(order.getOrderCurrencyCode() != null ? order.getOrderCurrencyCode() : "SAR");
			mpsRequest.setDeliveryType("FORWARD");	
			
			// Set invoice date from actual sales invoice creation date
			
			
			if (salesInvoice != null && salesInvoice.getCreatedAt() != null) {
				DateFormat dateFormat = new SimpleDateFormat(OrderConstants.ORDER_PUSH_OMS_DATE_FORMAT);
				Date createDate = new Date(salesInvoice.getCreatedAt().getTime());
				String invoiceDate = dateFormat.format(createDate);
				invoiceDate = invoiceDate.concat(OrderConstants.WMS_DEFAULT_TIME_ZONE_STRING);
				mpsRequest.setInvoiceDate(invoiceDate);
				
				// Set additional_order_date for forward flow (invoice date without milliseconds, in UTC)
				DateFormat additionalDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
				additionalDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
				Date orderCreateDate = order.getCreatedAt() != null ? new Date(order.getCreatedAt().getTime()) : createDate;
				String additionalOrderDate = additionalDateFormat.format(orderCreateDate).concat(OrderConstants.WMS_DEFAULT_TIME_ZONE_STRING);
				mpsRequest.setAdditionalOrderDate(additionalOrderDate);
			} else {
				Date currentDate = new Date();
				mpsRequest.setInvoiceDate(new SimpleDateFormat("yyyy-MM-dd").format(currentDate));
				
				// Set additional_order_date even when invoice is null
				DateFormat additionalDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
				additionalDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
				String additionalOrderDate = additionalDateFormat.format(currentDate).concat(OrderConstants.WMS_DEFAULT_TIME_ZONE_STRING);
				mpsRequest.setAdditionalOrderDate(additionalOrderDate);
			}
			
			// Set invoice number
			if (salesInvoice != null) {
				mpsRequest.setInvoiceNumber(salesInvoice.getIncrementId() != null ? salesInvoice.getIncrementId() : "");
			} else {
				mpsRequest.setInvoiceNumber(order.getIncrementId() != null ? order.getIncrementId() : "");
			}
			mpsRequest.setIsMps(salesShipment.getIsMps() != null ? salesShipment.getIsMps() : false);
			
			// Set box count for forward shipments
			List<SalesShipmentPackDetails> packDetails = salesShipmentPackDetailsRepository.findByShipmentId(salesShipment.getEntityId());
			mpsRequest.setBoxCount(packDetails != null ? packDetails.size() : 1);
		}
		
		// Set pickup/return info name

		String infoName = navik.getStyliPickupInfoName();
		
		if(!isReturn) {
			// Try to get pickup info name from seller inventory mapping using warehouse location
			String pickupInfoName = null;
			
			// Get sellerId and warehouseId from warehouse location
			if (order != null && order.getSubSalesOrder() != null && order.getSubSalesOrder().getWarehouseLocationId() != null) {
				String warehouseId = String.valueOf(order.getSubSalesOrder().getWarehouseLocationId());
				
				// Use service to get seller config based on config source (DB or Consul)
				SellerConfig matchingMapping = sellerConfigService.getSellerConfigForWarehouse(warehouseId);
					
				if (matchingMapping != null && matchingMapping.getSellerId() != null) {
					pickupInfoName = getPickupInfoNameBySellerAndWarehouse(matchingMapping.getSellerId(), warehouseId);
				}
			}
			
			// Use the pickup info name from seller mapping if found, otherwise fallback to default
			if (pickupInfoName != null) {
				mpsRequest.setPickupInfoName(pickupInfoName);
			} else {
				mpsRequest.setPickupInfoName(infoName != null ? infoName : "");
			}
		}
		// Always set return_info_name - empty string for non-return, actual value for return
		if (isReturn) {
			mpsRequest.setReturnInfoName(navik.getStyliReturnInfoName() != null ? navik.getStyliReturnInfoName() : "");
			// Set return_info_address based on carrier
			if (null != navik.getDropOffDetails() && null != amastyRmaRequest.getReturnType()
					&& amastyRmaRequest.getReturnType() == 1) {
				// Get navikInfos for the current store
				List<Stores> stores = Constants.getStoresList();
				Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId())).findAny()
						.orElse(null);
				
				Navikinfos navikInfos = null;
				if (null != store) {
					navikInfos = navik.getDropOffDetails().stream()
							.filter(e -> null != e.getWebSiteId() && e.getWebSiteId().intValue() == store.getWebsiteId())
							.findFirst().orElse(null);
				}

				String dropOffAddress = null;
				if (null != navikInfos) {
					if (null != amastyRmaRequest.getCpId() && "smsa".equalsIgnoreCase(amastyRmaRequest.getCpId())) {
						dropOffAddress = navik.getSmsaDropOff();
					} else if (null != amastyRmaRequest.getCpId() && "aramex".equalsIgnoreCase(amastyRmaRequest.getCpId())) {
						dropOffAddress = navik.getAramexDropOff();
					} else {
						dropOffAddress = navik.getSmsaDropOff();
					}
				}
				// Set return_info_address
				mpsRequest.setReturnInfoName(dropOffAddress != null ? dropOffAddress : "");
			} else {
				mpsRequest.setReturnInfoName(navik.getStyliReturnInfoName());
			}
		} else {
			mpsRequest.setReturnInfoName(navik.getStyliReturnInfoName());
		}
		
		// Set return-specific fields
		if (isReturn) {
			// Get navikInfos for the current store
			Navikinfos navikInfos = null;
			if (navik != null && navik.getDropOffDetails() != null) {
				List<Stores> stores = Constants.getStoresList();
				Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId())).findAny()
						.orElse(null);
				if (store != null) {
					navikInfos = navik.getDropOffDetails().stream()
							.filter(e -> null != e.getWebSiteId() && e.getWebSiteId().intValue() == store.getWebsiteId())
							.findFirst().orElse(null);
				}
			}
			
			// Get shipment details to extract SKU and quantity information
			List<MpsShipmentDetail> shipmentDetails = buildMpsReturnShipmentDetails(order, amastyRmaRequest, navikInfos);
			if (shipmentDetails != null && !shipmentDetails.isEmpty()) {
				// Get the first shipment detail (box)
				MpsShipmentDetail firstDetail = shipmentDetails.get(0);
				if (firstDetail.getItems() != null && !firstDetail.getItems().isEmpty()) {
					// Get the first item from the first box
					MpsShipmentItem firstItem = firstDetail.getItems().get(0);
					
					mpsRequest.setSkuDescription(firstItem.getSkuDescription() != null ? firstItem.getSkuDescription() : "Return Item");
					mpsRequest.setSku(firstItem.getSku() != null ? firstItem.getSku() : "SKU");
					mpsRequest.setQty(String.valueOf(firstItem.getQuantity() != null ? firstItem.getQuantity() : 1));
				}
			} else {
				// Fallback values if no shipment details found
				mpsRequest.setSkuDescription("Return Item");
				mpsRequest.setSku("SKU");
				mpsRequest.setQty("1");
			}
			
			mpsRequest.setDeliveryInstructions("Leave package with security if not available.");
			mpsRequest.setShipmentDetails(shipmentDetails);
			// Set box count from shipmentDetails size
			mpsRequest.setBoxCount(shipmentDetails != null ? shipmentDetails.size() : 1);
			
			// Set invoice and COD fields for return shipments
			mpsRequest.setInvoiceCurrencyCode(order.getOrderCurrencyCode() != null ? order.getOrderCurrencyCode() : "SAR");
			mpsRequest.setCodValue(0.0);
			mpsRequest.setCodCurrencyCode(order.getOrderCurrencyCode() != null ? order.getOrderCurrencyCode() : "SAR");
			
			// Set invoice date and number for return shipments
			SalesInvoice salesInvoice = order.getSalesInvoices().stream().findFirst().orElse(null);
			if (salesInvoice != null && salesInvoice.getCreatedAt() != null) {
				DateFormat dateFormat = new SimpleDateFormat(OrderConstants.ORDER_PUSH_OMS_DATE_FORMAT);
				Date createDate = new Date(salesInvoice.getCreatedAt().getTime());
				String invoiceDate = dateFormat.format(createDate);
				invoiceDate = invoiceDate.concat(OrderConstants.WMS_DEFAULT_TIME_ZONE_STRING);
				mpsRequest.setInvoiceDate(invoiceDate);
			} else {
				mpsRequest.setInvoiceDate(new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
			}
			
			if (salesInvoice != null) {
				mpsRequest.setInvoiceNumber(salesInvoice.getIncrementId() != null ? salesInvoice.getIncrementId() : "");
			} else {
				mpsRequest.setInvoiceNumber(order.getIncrementId() != null ? order.getIncrementId() : "");
			}
		} else {
			// Set default values for non-return shipments
			mpsRequest.setSkuDescription("");
			mpsRequest.setSku("");
			mpsRequest.setQty("");
			mpsRequest.setDeliveryInstructions("");

			if (salesShipment != null) {
				//SFP-338 Product Type modifications in Shipping Label for Dangerous Goods (DGG)
				if (Boolean.TRUE.equals(navik.getIsDggFeatureEnabled())) {
					mpsRequest.setShippingCategory(getShippingCategory(order,navik));
					mpsRequest.setSkuDescription(getSkuDescriptionFromProductAttributes(order, null,navik));
				}

				List<SalesShipmentPackDetails> packDetails = salesShipmentPackDetailsRepository.findByShipmentId(salesShipment.getEntityId());
				
				// Get navikInfos for the current store
				Navikinfos navikInfos = null;
				if (navik != null && navik.getDropOffDetails() != null) {
					List<Stores> stores = Constants.getStoresList();
					Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId())).findAny()
							.orElse(null);
					if (store != null) {
						navikInfos = navik.getDropOffDetails().stream()
								.filter(e -> null != e.getWebSiteId() && e.getWebSiteId().intValue() == store.getWebsiteId())
								.findFirst().orElse(null);
					}
				}
				
				mpsRequest.setShipmentDetails(buildMpsShipmentDetails(order, salesShipment, packDetails, navikInfos,navik));
			} else {
				mpsRequest.setShipmentDetails(null);
			}
		}
		
		return mpsRequest;
	}

	/**
	 * @param address the address string to escape
	 * @return the escaped address string with & replaced by &amp;
	 */
	private String escapeAmpersand(String address) {
		if (address == null) return null;
		return address.replace("&", "and");
	}
	
	private List<MpsShipmentDetail> buildMpsShipmentDetails(SalesOrder order, SalesShipment salesShipment, List<SalesShipmentPackDetails> packDetails, Navikinfos navikInfos,NavikDetails navikDetails) {
		List<MpsShipmentDetail> shipmentDetails = new ArrayList<>();
		
		if (packDetails != null && !packDetails.isEmpty()) {
			// Use pack details for dimensions
		for (SalesShipmentPackDetails packDetail : packDetails) {
		MpsShipmentDetail detail = new MpsShipmentDetail();
		detail.setId(packDetail.getBoxCode());
		detail.setWeight(packDetail.getWeight() != null ? packDetail.getWeight().doubleValue() : 1.0);
		detail.setBreadth(packDetail.getBreadth() != null ? packDetail.getBreadth().doubleValue() : 10.0);
		detail.setHeight(packDetail.getHeight() != null ? packDetail.getHeight().doubleValue() : 10.0);
		detail.setLength(packDetail.getLength() != null ? packDetail.getLength().doubleValue() : 10.0);
				
				// Create item details from pack details items
				List<SalesShipmentPackDetailsItem> packDetailsItems = salesShipmentPackDetailsItemRepository.findByPackDetailsId(packDetail.getEntityId());
		List<MpsShipmentItem> mpsItems = new ArrayList<>();
		
		if (packDetailsItems != null && !packDetailsItems.isEmpty()) {
					// Use pack details items for SKU and quantity
			for (SalesShipmentPackDetailsItem packItem : packDetailsItems) {
		MpsShipmentItem mpsItem = new MpsShipmentItem();
		//SFP-338 Product Type modifications in Shipping Label for Dangerous Goods (DGG)
						String skuDescription = null;
						if (Boolean.TRUE.equals(navikDetails.getIsDggFeatureEnabled())) {
							skuDescription = getSkuDescriptionFromProductAttributes(
									order, packItem.getClientSkuId(), navikDetails);
						}
						// Fallback to existing logic if feature is disabled OR sku description is blank
						if (StringUtils.isBlank(skuDescription)) {
							skuDescription = getProductDescriptionBySku(order, packItem.getClientSkuId(), navikInfos);
						}
		mpsItem.setSkuDescription(skuDescription);

		mpsItem.setQuantity(packItem.getCount() != null ? packItem.getCount() : 1);
		Double price = getPriceBySku(order, packItem.getClientSkuId());
		mpsItem.setPrice(price != null ? price : 0.0);
		mpsItem.setSku(packItem.getClientSkuId());
						mpsItems.add(mpsItem);
					}
				} 
				detail.setItems(mpsItems);
			shipmentDetails.add(detail);
		}
		} else {
			// Fallback to shipment items if no pack details found
			for (SalesShipmentItem item : salesShipment.getSalesShipmentItem()) {
		MpsShipmentDetail detail = new MpsShipmentDetail();
		detail.setId(item.getSku());
		detail.setWeight(item.getWeight() != null ? item.getWeight().doubleValue() : 1.0);
		detail.setBreadth(10.0); // Default values when no pack details
		detail.setHeight(10.0);
		detail.setLength(10.0);
	
				// Create item details
		MpsShipmentItem mpsItem = new MpsShipmentItem();
		mpsItem.setSkuDescription("NA");
		mpsItem.setQuantity(item.getQuantity() != null ? item.getQuantity().intValue() : 1);
		mpsItem.setPrice(item.getPrice() != null ? item.getPrice().doubleValue() : 0.0);
		mpsItem.setSku(item.getSku());
				
				detail.setItems(Arrays.asList(mpsItem));
				shipmentDetails.add(detail);
			}
		}
		
		return shipmentDetails;
	}

	private Double getPriceBySku(SalesOrder order, String sku) {
		if (order == null || order.getSalesOrderItem() == null || sku == null) {
			return 0.0;
		}
		
		// Find the order item with matching SKU
		SalesOrderItem orderItem = order.getSalesOrderItem().stream()
				.filter(item -> sku.equals(item.getSku()))
				.findFirst()
				.orElse(null);
		
		if (orderItem != null && orderItem.getPrice() != null) {
			return orderItem.getPrice().doubleValue();
		}
		
		// Fallback to 0.0 if not found
		return 0.0;
	}

	private String getProductDescriptionBySku(SalesOrder order, String sku, Navikinfos navikInfos) {
		if (order == null || order.getSalesOrderItem() == null || sku == null) {
			return "NA";
		}
		
		// Find the order item with matching SKU
		SalesOrderItem orderItem = order.getSalesOrderItem().stream()
				.filter(item -> sku.equals(item.getSku()))
				.findFirst()
				.orElse(null);
		
		if (orderItem != null) {
			// Apply language-based description logic similar to setDescription calls
			if (Boolean.TRUE.equals(OrderConstants.CHECKENGLISHSTORE.get(order.getStoreId())) && null != navikInfos) {
				return navikInfos.getDescriptionEn();
			} else if (null != navikInfos) {

				return navikInfos.getDescriptionAr();
			}

			// Fallback to original logic if navikInfos is null
			// Prefer name over description, fallback to description if name is null/empty
			if (orderItem.getName() != null && !orderItem.getName().trim().isEmpty()) {
				return orderItem.getName();
			} else if (orderItem.getDescription() != null && !orderItem.getDescription().trim().isEmpty()) {
				return orderItem.getDescription();
			}
		}
		
		// Fallback to "NA" if not found or both name and description are null/empty
		return "NA";
	}
	
	private List<MpsShipmentDetail> buildMpsReturnShipmentDetails(SalesOrder order, AmastyRmaRequest amastyRmaRequest, Navikinfos navikInfos) {
		List<MpsShipmentDetail> shipmentDetails = new ArrayList<>();
		// Get RMA request items for the given RMA request
		List<AmastyRmaRequestItem> rmaRequestItems = amastyRmaRequestItemRepository.findByRequestId(amastyRmaRequest.getRequestId());
		
		if (rmaRequestItems != null && !rmaRequestItems.isEmpty()) {
		
	
			// Fallback: Create a single box with all RMA items when no shipment found
		MpsShipmentDetail detail = new MpsShipmentDetail();
		detail.setId("BOX" + order.getIncrementId());
		detail.setWeight(1.0);
		detail.setBreadth(10.0);
		detail.setHeight(10.0);
		detail.setLength(10.0);
	
		List<MpsShipmentItem> mpsItems = new ArrayList<>();
		for (AmastyRmaRequestItem rmaItem : rmaRequestItems) {
				order.getSalesOrderItem()
					.stream()
					.filter(e -> e.getItemId().equals(rmaItem.getOrderItemId()))
					.findFirst()
					.ifPresent(orderItem -> {
						if (orderItem.getSku() != null) {
							MpsShipmentItem mpsItem = new MpsShipmentItem();
							mpsItem.setSkuDescription(getProductDescriptionBySku(order, orderItem.getSku(), navikInfos));
							mpsItem.setQuantity(rmaItem.getQty() != null ? rmaItem.getQty().intValue() : 1);
							Double price = getPriceBySku(order, orderItem.getSku());
							mpsItem.setPrice(price != null ? price : 0.0);
							mpsItem.setSku(orderItem.getSku());
				mpsItems.add(mpsItem);
			}
					});
			}
			
			detail.setItems(mpsItems);
			shipmentDetails.add(detail);
			
		}
		
		return shipmentDetails;
	}
	@Override
	public NavikAddressUpdateResponse updateNavikAddress(NavikAddressUpdateDTO navikAddressRequest)
			throws JsonProcessingException {
		String authToken = alphaAuthToken();
		String url = Constants.orderCredentials.getOrderDetails().getAlphaBaseUrl() + "/v1/orders/update-order-details";
		LOGGER.info("Alpha url:" + url);
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.add(Constants.AUTHORIZATION_HEADER, "Bearer " + authToken);

		Map<String, String> requestMap = new HashMap<>();
		requestMap.put("awb", navikAddressRequest.getAwb());
		requestMap.put("delivery_type", navikAddressRequest.getDeliveryType());
		requestMap.put("area", navikAddressRequest.getArea());
		requestMap.put("address", navikAddressRequest.getAddress().replace("\n", ", "));
		requestMap.put("phone_code", navikAddressRequest.getPhoneCode());
		requestMap.put("phone_number", navikAddressRequest.getPhoneNumber());
		requestMap.put("client", navikAddressRequest.getClient());

		HttpEntity<Map> requestBody = new HttpEntity<>(requestMap, requestHeaders);
		LOGGER.info("Alpha request body:" + mapper.writeValueAsString(requestBody.getBody()));
		NavikAddressUpdateResponse navikAddressUpdateResponse = new NavikAddressUpdateResponse();
		try {
			ResponseEntity<NavikAddressUpdateResponse> navikResponse = restTemplateBuilder.exchange(url, HttpMethod.PATCH, requestBody,
					NavikAddressUpdateResponse.class);
			navikAddressUpdateResponse = navikResponse.getBody();
			LOGGER.info("Alpha response body:" + navikResponse.getBody());
		} catch (RestClientException rce) {
			LOGGER.error("Error in updating address in Alpha. ", rce);
			Map<String, String> errorMap = new HashMap<>();
			errorMap.put("message", rce.getMessage());
			navikAddressUpdateResponse.setStatus(false);
			navikAddressUpdateResponse.setData(errorMap);
		}
		return navikAddressUpdateResponse;
	}

	private String alphaAuthToken() {
		String url = Constants.orderCredentials.getOrderDetails().getAlphaBaseUrl() + "/v1/accounts/login";
		NavikDetails navik = Constants.orderCredentials.getNavik();

		String alphaToken = Constants.getAlphaToken();
		if (Objects.isNull(alphaToken) || isJWTExpired(alphaToken)) {
			LOGGER.info("Alpha auth token expired. Generating new One" + alphaToken);
			try {
				Auth auth = new Auth(navik.getAlphaUsername(), navik.getAlphaPassword());
				HttpHeaders requestHeaders = new HttpHeaders();
				requestHeaders.setContentType(MediaType.APPLICATION_JSON);
				requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
				HttpEntity<Auth> requestBody = new HttpEntity<>(auth, requestHeaders);
				ResponseEntity<Auth> response = restTemplateBuilder.exchange(url, HttpMethod.POST, requestBody, Auth.class);
				if (response.getStatusCode() == HttpStatus.OK) {
					Auth body = response.getBody();
					if (Objects.nonNull(body)) {
						alphaToken = body.getToken();
						consulComponent.updateAlphaToken(alphaToken);
					} else
						LOGGER.error("Error in Updating Alpha Authentication Token. " + response);
				} else {
					LOGGER.error("Error in Updating Alpha Authentication Token. API Response Code : "
							+ response.getStatusCodeValue());
				}
			} catch (Exception e) {
				LOGGER.error("Error in Alpha Authentication. ", e);
			}
		}
		return alphaToken;
	}

	/**
	 * Get Beta authentication token for first mile
	 * @return
	 */
	private String betaAuthToken() {
		String url = Constants.orderCredentials.getOrderDetails().getBetaBaseUrl() + "/v1/accounts/login";
		NavikDetails navik = Constants.orderCredentials.getNavik();

		String betaToken = Constants.getBetaToken();
		if (Objects.isNull(betaToken) || isJWTExpired(betaToken)) {
			LOGGER.info("Beta auth token expired. Generating new One" + betaToken);
			try {
				Auth auth = new Auth(navik.getBetaUsername(), navik.getBetaPassword());
				HttpHeaders requestHeaders = new HttpHeaders();
				requestHeaders.setContentType(MediaType.APPLICATION_JSON);
				requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
				HttpEntity<Auth> requestBody = new HttpEntity<>(auth, requestHeaders);
				ResponseEntity<Auth> response = restTemplateBuilder.exchange(url, HttpMethod.POST, requestBody, Auth.class);
				if (response.getStatusCode() == HttpStatus.OK) {
					Auth body = response.getBody();
					if (Objects.nonNull(body)) {
						betaToken = body.getToken();
						consulComponent.updateBetaToken(betaToken);
					} else
						LOGGER.error("Error in Updating Beta Authentication Token. " + response);
				} else {
					LOGGER.error("Error in Updating Beta Authentication Token. API Response Code : "
							+ response.getStatusCodeValue());
				}
			} catch (Exception e) {
				LOGGER.error("Error in Beta Authentication. ", e);
			}
		}
		return betaToken;
	}

	private boolean isJWTExpired(String token) {
		try {
			String[] chunks = token.split("\\.");
			Base64.Decoder decoder = Base64.getUrlDecoder();
			String payload = new String(decoder.decode(chunks[1]));
			JsonObject jsonObj = JsonParser.parseString(payload).getAsJsonObject();
			long expTime = jsonObj.get("exp").getAsLong();
			long timeDiff = (expTime * 1000) - new Date().getTime();
			long minutesDiff = TimeUnit.MILLISECONDS.toMinutes(timeDiff);
			return minutesDiff < 10;
		} catch (Exception e) {
			LOGGER.error("Error in decoding Alpha JWT Token.", e);
		}
		return true;
	}

	private GetShipmentV3Response processForwardShipmentResponse(GetShipmentV3ResponseBody responseBody, SalesOrder order,
														 SalesShipment salesShipMent, ResponseEntity<NavikResponse> navikResponse, SplitSalesOrder splitSalesOrder) throws JsonProcessingException {

		GetShipmentV3Response response = new GetShipmentV3Response();

		NavikResponse body = navikResponse.getBody();
		if (body != null && body.getResult() != null 
			&& body.getResult().getAlphaAwb() != null && !body.getResult().getAlphaAwb().isEmpty()
			&& body.getResult().getLabel() != null && !body.getResult().getLabel().isEmpty()) {
				responseBody.setTransporter(body.getResult().getCourier_name());
				responseBody.setAwbNumber(body.getResult().getWaybill());
				// Note: shippingLabelUrl will be set after processing to use GCS signed URL
				String encodeValue = null;
				if (null != Constants.orderCredentials && Constants.orderCredentials.getWms().isNewInVoiceEncode()) {
					encodeValue = order.getEntityId().toString().concat("#").concat(order.getCustomerEmail());
				} else {
					encodeValue = order.getEntityId().toString();
				}
				String encoded = Base64.getEncoder().encodeToString(encodeValue.getBytes());

				responseBody.setInvoiceUrl(Constants.orderCredentials.getOrderDetails().getOmsServicePublicBaseUrl()
						+ GENERATE_PDF_URI + encoded);

				SalesShipmentTrack salesShipmentTrack = new SalesShipmentTrack();
				salesShipmentTrack.setCreatedAt(new Timestamp(new Date().getTime()));
				salesShipmentTrack.setUpdatedAt(new Timestamp(new Date().getTime()));
				salesShipmentTrack.setOrderId(salesShipMent.getSalesOrder().getEntityId());
				salesShipmentTrack.setTrackNumber(body.getResult().getWaybill());
				salesShipmentTrack.setTitle(body.getResult().getCourier_name());
				salesShipmentTrack.setParentId(salesShipMent.getEntityId());
				salesShipmentTrack.setCarrierCode(Constants.NAVIC_CARRIOR_CODE);
				salesShipmentTrack.setAlphaAwb(body.getResult().getAlphaAwb());
				
				// Set split order ID if this is a split order
				if (splitSalesOrder != null) {
					salesShipmentTrack.setSplitSalesOrderId(salesShipMent.getSplitSalesOrder().getEntityId());
				}
				
			// Integrate GCS for shipping label (Common functionality for all APIs)
			try {
				String signedUrl = shippingLabelUrlService.processAndSaveShippingLabel(
					body.getResult().getLabel(),
					salesShipmentTrack,
					SalesShipmentTrack.class,
					null  // Use default bucket from config
				);
				LOGGER.info("Successfully processed shipping label to GCS for regular shipment: " + salesShipMent.getIncrementId());
			} catch (Exception e) {
				LOGGER.error("Failed to process shipping label to GCS for regular shipment, using carrier URL: " + e.getMessage(), e);
				// Fallback: use original carrier URL
				salesShipmentTrack.setShippingLabel(body.getResult().getLabel());
			}
				
				// Save the shipment track with proper error handling
				try {
					shipmentTrackerRepository.saveAndFlush(salesShipmentTrack);
				} catch (Exception e) {
					LOGGER.error("Error saving SalesShipmentTrack for shipment: " + salesShipMent.getIncrementId() + 
								", parentId: " + salesShipMent.getEntityId(), e);
					throw e;
				}

				response.setAwbNumber(responseBody.getAwbNumber());
				response.setTransporter(responseBody.getTransporter());
				
				// Populate box label details from pack details (with GCS signed URLs)
				List<BoxLabelDetails> boxLabelDetails = buildBoxLabelDetails(salesShipMent);
				response.setBoxLabelDetails(boxLabelDetails);
				
				// Set shippingLabelUrl from saved entity (GCS signed URL if processed, carrier URL if fallback)
				String processedUrl = salesShipmentTrack.getShippingLabel();
				if (processedUrl != null && !processedUrl.isEmpty()) {
					responseBody.setShippingLabelUrl(processedUrl);
					response.setShippingLabelUrl(processedUrl);
					LOGGER.info("Using processed shipping label URL in response (GCS or carrier): " + processedUrl.substring(0, Math.min(100, processedUrl.length())) + "...");
				} else {
					// Fallback to carrier URL if processing failed
					String carrierUrl = body.getResult().getLabel();
					responseBody.setShippingLabelUrl(carrierUrl);
					response.setShippingLabelUrl(carrierUrl);
					LOGGER.warn("Processed shipping label URL not available, using carrier URL");
				}

				// Trigger async National ID upload (Oman only) and async invoice upload
				try {
					String awb = responseBody.getAwbNumber();
					if (awb != null && !awb.isEmpty()) {
						nationalIdUploadService.scheduleNationalIdUploadAsync(awb, order, salesShipmentTrack);
						invoiceSharingService.sendInvoiceToLogistiqAsync(awb, order, salesShipmentTrack, getAuthorizationTokenFromCurrentRequest());
					}
				} catch (Exception e) {
					LOGGER.error("Error triggering async National ID or invoice upload for new AWB: " + responseBody.getAwbNumber(), e);
				}

				updateOrderStatusHistory(order, OrderConstants.FORWARD_AWB_CREATED_MESSAGE + response.getAwbNumber(),
						OrderConstants.ORDER_STATUS_HISTORY_ENTITY, order.getStatus());
			} else {
				response.setHasError(true);
				processShipmentError(response, body);
			}
			
			// Publish OTS payload for split orders
			if (splitSalesOrder != null && !response.isHasError()) {
				orderHelper.buildOTSPayloadForSplitOrderAndPublishToPubSub(splitSalesOrder, OrderConstants.FORWARD_AWB_CREATED_MESSAGE + response.getAwbNumber(), "4.1");
			}
		return response;
	}

	/**
	 * Sends Braze notification for dangerous goods shipments
	 */
	/**
	 * Public method to send dangerous goods Braze notification (can be called from helper classes)
	 */
	public void sendDangerousGoodsBrazeNotification(SalesOrder order, SalesShipment salesShipment) {
		sendDangerousGoodsBrazeNotification(order, salesShipment, null);
	}
	
	private void sendDangerousGoodsBrazeNotification(SalesOrder order, SalesShipment salesShipment, 
			ResponseEntity<NavikResponse> navikResponse) {
		try {
			DangerousGoodsCheckResult checkResult = checkDangerousGoodsInShipment(order, salesShipment);
			
			// Only send notification if shipment has dangerous goods and not all are cancelled
			if (!checkResult.hasDangerousGoods || checkResult.allDangerousGoodsCancelled) {
				return;
			}
			
			BrazeDangerousGoods.Event event = buildDangerousGoodsEvent(order, salesShipment, navikResponse, checkResult);
			if (event == null) {
				return;
			}
			
			// Send to Braze
			sendDangerousGoodsEventToBraze(Collections.singletonList(event));
			
			LOGGER.info("Sent dangerous goods Braze notification for shipment: " + checkResult.shipmentId + ", order: " + checkResult.orderId);
		} catch (Exception e) {
			LOGGER.error("Error sending dangerous goods Braze notification: " + e.getMessage(), e);
		}
	}
	
	/**
	 * Result class for dangerous goods check
	 */
	private static class DangerousGoodsCheckResult {
		boolean hasDangerousGoods;
		boolean allDangerousGoodsCancelled;
		List<String> dangerousGoodsSkuNames = new ArrayList<>();
		String shipmentId = "";
		String orderId = "";
	}
	
	/**
	 * Checks if shipment contains dangerous goods and determines cancellation status
	 * Handles both normal orders (SalesOrder) and split orders (SplitSalesOrder)
	 */
	private DangerousGoodsCheckResult checkDangerousGoodsInShipment(SalesOrder order, SalesShipment salesShipment) {
		DangerousGoodsCheckResult result = new DangerousGoodsCheckResult();
		
		// Early return if no shipment items
		if (salesShipment.getSalesShipmentItem() == null || salesShipment.getSalesShipmentItem().isEmpty()) {
			return result;
		}
		
		// Check if this is a split order shipment
		SplitSalesOrder splitSalesOrder = null;
		if (salesShipment.getSplitSalesOrder() != null) {
			splitSalesOrder = salesShipment.getSplitSalesOrder();
		}
		
		// Handle split orders
		if (splitSalesOrder != null) {
			// Get split order items
			Set<SplitSalesOrderItem> splitOrderItems = splitSalesOrder.getSplitSalesOrderItems();
			if (CollectionUtils.isEmpty(splitOrderItems)) {
				return result;
			}
			
			// Create a Map of SKU to SplitSalesOrderItem and itemId to SplitSalesOrderItem for lookups
			Map<String, SplitSalesOrderItem> skuToSplitOrderItemMap = new HashMap<>();
			Map<Integer, SplitSalesOrderItem> itemIdToSplitOrderItemMap = new HashMap<>();
			for (SplitSalesOrderItem item : splitOrderItems) {
				if (item.getSku() != null && !item.getSku().isEmpty()) {
					skuToSplitOrderItemMap.put(item.getSku(), item);
				}
				if (item.getItemId() != null) {
					itemIdToSplitOrderItemMap.put(item.getItemId(), item);
				}
			}
			
			// Check each shipment item for dangerous goods
			for (SalesShipmentItem shipmentItem : salesShipment.getSalesShipmentItem()) {
				SplitSalesOrderItem splitOrderItem = null;
				
				// Try to get split order item by splitOrderItemId first
				if (shipmentItem.getSplitOrderItemId() != null) {
					splitOrderItem = itemIdToSplitOrderItemMap.get(shipmentItem.getSplitOrderItemId());
				}
				
				// Fallback to SKU lookup
				if (splitOrderItem == null && shipmentItem.getSku() != null) {
					splitOrderItem = skuToSplitOrderItemMap.get(shipmentItem.getSku());
				}
				
				if (splitOrderItem == null) {
					continue;
				}
				
				// Check if dangerous - use the linked SalesOrderItem if available, otherwise check split order item
				SalesOrderItem orderItem = splitOrderItem.getSalesOrderItem();
				if (orderItem != null && !isDangerousProduct(orderItem)) {
					continue;
				} else if (orderItem == null) {
					// If no linked SalesOrderItem, check split order item's attributes directly
					if (!isDangerousProductFromSplitOrderItem(splitOrderItem)) {
						continue;
					}
				}
				
				result.hasDangerousGoods = true;
				String skuName = splitOrderItem.getName() != null ? splitOrderItem.getName() : splitOrderItem.getSku();
				result.dangerousGoodsSkuNames.add(skuName);
				
				// Initialize allDangerousGoodsCancelled to true when first dangerous good is found
				if (result.dangerousGoodsSkuNames.size() == 1) {
					result.allDangerousGoodsCancelled = true;
				}
				
				// Check if this dangerous good item is fully cancelled
				BigDecimal qtyShipped = shipmentItem.getQuantity() != null ? shipmentItem.getQuantity() : BigDecimal.ZERO;
				if (qtyShipped.compareTo(BigDecimal.ZERO) > 0) {
					result.allDangerousGoodsCancelled = false;
				}
			}
			
			// Extract shipment and order IDs for split orders
			result.shipmentId = getShipmentId(salesShipment);
			result.orderId = getSplitOrderId(splitSalesOrder);
		} else {
			// Handle normal orders
		// Early return if no order items
		Set<SalesOrderItem> orderItems = order.getSalesOrderItem();
			if (CollectionUtils.isEmpty(orderItems)) {
			return result;
		}
		
		// Create a Map of SKU to SalesOrderItem for O(1) lookups (optimization: O(N+M) instead of O(N*M))
		Map<String, SalesOrderItem> skuToOrderItemMap = buildSkuToOrderItemMap(orderItems);
		
		// Check each shipment item for dangerous goods
		for (SalesShipmentItem shipmentItem : salesShipment.getSalesShipmentItem()) {
			SalesOrderItem orderItem = getOrderItemForShipmentItem(shipmentItem, skuToOrderItemMap);
			if (orderItem == null) {
				continue;
			}
			
			if (!isDangerousProduct(orderItem)) {
				continue;
			}
			
			result.hasDangerousGoods = true;
			String skuName = orderItem.getName() != null ? orderItem.getName() : orderItem.getSku();
			result.dangerousGoodsSkuNames.add(skuName);
			
			// Initialize allDangerousGoodsCancelled to true when first dangerous good is found
			// This assumes all are cancelled until we find one that isn't
			if (result.dangerousGoodsSkuNames.size() == 1) {
				result.allDangerousGoodsCancelled = true;
			}
			
			// Check if this dangerous good item is fully cancelled
			// If shipped quantity is greater than 0, then not all dangerous goods are cancelled
			BigDecimal qtyShipped = shipmentItem.getQuantity() != null ? shipmentItem.getQuantity() : BigDecimal.ZERO;
			if (qtyShipped.compareTo(BigDecimal.ZERO) > 0) {
				result.allDangerousGoodsCancelled = false;
			}
		}
		
			// Extract shipment and order IDs for normal orders
		result.shipmentId = getShipmentId(salesShipment);
		result.orderId = getOrderId(order);
		}
		
		return result;
	}
	
	/**
	 * Builds a map of SKU to SalesOrderItem for efficient lookups
	 */
	private Map<String, SalesOrderItem> buildSkuToOrderItemMap(Set<SalesOrderItem> orderItems) {
		Map<String, SalesOrderItem> skuToOrderItemMap = new HashMap<>();
		for (SalesOrderItem item : orderItems) {
			if (item.getSku() != null && !item.getSku().isEmpty()) {
				skuToOrderItemMap.put(item.getSku(), item);
			}
		}
		return skuToOrderItemMap;
	}
	
	/**
	 * Gets the order item corresponding to a shipment item
	 */
	private SalesOrderItem getOrderItemForShipmentItem(SalesShipmentItem shipmentItem, 
			Map<String, SalesOrderItem> skuToOrderItemMap) {
		if (shipmentItem.getSku() == null) {
			return null;
		}
		return skuToOrderItemMap.get(shipmentItem.getSku());
	}
	
	/**
	 * Gets shipment ID from salesShipment
	 */
	private String getShipmentId(SalesShipment salesShipment) {
		if (salesShipment.getIncrementId() != null) {
			return salesShipment.getIncrementId();
		}
		if (salesShipment.getEntityId() != null) {
			return String.valueOf(salesShipment.getEntityId());
		}
		return "";
	}
	
	/**
	 * Gets order ID from order
	 */
	private String getOrderId(SalesOrder order) {
		if (order.getIncrementId() != null) {
			return order.getIncrementId();
		}
		if (order.getEntityId() != null) {
			return String.valueOf(order.getEntityId());
		}
		return "";
	}
	
	/**
	 * Gets order ID from split order
	 */
	private String getSplitOrderId(SplitSalesOrder splitSalesOrder) {
		if (splitSalesOrder.getIncrementId() != null) {
			return splitSalesOrder.getIncrementId();
		}
		if (splitSalesOrder.getEntityId() != null) {
			return String.valueOf(splitSalesOrder.getEntityId());
		}
		return "";
	}
	
	/**
	 * Builds the Braze event for dangerous goods notification
	 * Handles both normal orders (SalesOrder) and split orders (SplitSalesOrder)
	 */
	private BrazeDangerousGoods.Event buildDangerousGoodsEvent(SalesOrder order, SalesShipment salesShipment,
			ResponseEntity<NavikResponse> navikResponse, DangerousGoodsCheckResult checkResult) {
		// Check if this is a split order shipment
		SplitSalesOrder splitSalesOrder = null;
		if (salesShipment != null && salesShipment.getSplitSalesOrder() != null) {
			splitSalesOrder = salesShipment.getSplitSalesOrder();
		}
		
		// Get customer email/ID - check split order first, then normal order
		String customerEmail = null;
		String customerId = null;
		Integer storeId = null;
		
		if (splitSalesOrder != null) {
			customerEmail = splitSalesOrder.getCustomerEmail();
			customerId = splitSalesOrder.getCustomerId() != null ? String.valueOf(splitSalesOrder.getCustomerId()) : null;
			storeId = splitSalesOrder.getStoreId();
		} else if (order != null) {
			customerEmail = order.getCustomerEmail();
			customerId = order.getCustomerId() != null ? String.valueOf(order.getCustomerId()) : null;
			storeId = order.getStoreId();
		}
		
		String externalId = customerId != null ? customerId : customerEmail;
		
		if (externalId == null || externalId.isEmpty()) {
			String orderId = splitSalesOrder != null ? splitSalesOrder.getIncrementId() : 
					(order != null ? order.getIncrementId() : "unknown");
			LOGGER.warn("Cannot send dangerous goods Braze notification: customer email/ID not found for order " + orderId);
			return null;
		}
		
		// Get customer name - use order (which may be parent order for split orders)
		String customerName = getCustomerName(order != null ? order : 
				(splitSalesOrder != null ? splitSalesOrder.getSalesOrder() : null), customerEmail);
		String skuName = String.join(", ", checkResult.dangerousGoodsSkuNames);
		String estimatedDeliveryDate = calculateEstimatedDeliveryDate(order != null ? order : 
				(splitSalesOrder != null ? splitSalesOrder.getSalesOrder() : null));
		String trackingLink = buildOrderTrackingUrl(order, salesShipment);
		
		// Create Braze event
		Event event = new BrazeDangerousGoods.Event();
		event.setExternalId(externalId);
		event.setName("Shipment_Packed");
		event.setTime(String.valueOf(Instant.now()));
		
		BrazeDangerousGoods.EventProperties properties = new BrazeDangerousGoods.EventProperties();
		properties.setPacked(true);
		properties.setShipmentId(checkResult.shipmentId);
		properties.setOrderId(checkResult.orderId);
		properties.setSkuName(skuName);
		properties.setDangerousGoodsFlag(true);
		properties.setEstimatedDeliveryDate(estimatedDeliveryDate);
		properties.setTrackingLink(trackingLink);
		properties.setCustomerName(customerName);
		properties.setStoreId(storeId);
		
		event.setProperties(properties);
		return event;
	}
	
	/**
	 * Gets customer name from order, with fallback to email username
	 */
	private String getCustomerName(SalesOrder order, String customerEmail) {
		if (order != null && order.getCustomerFirstname() != null) {
			String customerName = order.getCustomerFirstname();
			if (order.getCustomerLastname() != null) {
				customerName += " " + order.getCustomerLastname();
			}
			return customerName;
		}
		if (customerEmail != null && !customerEmail.isEmpty()) {
			return customerEmail.split("@")[0];
		}
		return "Customer";
	}
	
	/**
	 * Calculates estimated delivery date (original EDD + 2 days)
	 */
	private String calculateEstimatedDeliveryDate(SalesOrder order) {
		if (order.getEstimatedDeliveryTime() == null) {
			return "";
		}
		// Get timezone from store configuration, fallback to Asia/Riyadh for consistency
		ZoneId zoneId = getZoneIdForStore(order.getStoreId());
		LocalDate deliveryDate = order.getEstimatedDeliveryTime().toInstant()
				.atZone(zoneId)
				.toLocalDate();
		// Get daysToAdd from Consul configuration, default to 2 if not configured
		int daysToAdd = 2;
		if (Constants.orderCredentials != null && Constants.orderCredentials.getOrderDetails() != null
				&& Constants.orderCredentials.getOrderDetails().getEstimatedDeliveryDateDaysToAdd() != null) {
			daysToAdd = Constants.orderCredentials.getOrderDetails().getEstimatedDeliveryDateDaysToAdd();
		}
		LocalDate estimatedDate = deliveryDate.plusDays(daysToAdd);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		return estimatedDate.format(formatter);
	}
	
	/**
	 * Gets ZoneId for a store based on storeId, with fallback to Asia/Riyadh
	 */
	private ZoneId getZoneIdForStore(Integer storeId) {
		if (storeId != null && OrderConstants.timeZoneMap.containsKey(storeId)) {
			String timeZoneStr = OrderConstants.timeZoneMap.get(storeId);
			// Convert GMT offsets to proper ZoneId for consistent behavior across servers
			// GMT+03:00 -> Asia/Riyadh (Saudi Arabia, most stores)
			// GMT+04:00 -> Asia/Dubai (UAE)
			// GMT+05:30 -> Asia/Kolkata (India)
			switch (timeZoneStr) {
				case "GMT+03:00":
					return ZoneId.of("Asia/Riyadh");
				case "GMT+04:00":
					return ZoneId.of("Asia/Dubai");
				case "GMT+05:30":
					return ZoneId.of("Asia/Kolkata");
				default:
					// Fallback to Asia/Riyadh for unknown GMT offsets
					return ZoneId.of("Asia/Riyadh");
			}
		}
		// Default fallback to Asia/Riyadh for consistency
		return ZoneId.of("Asia/Riyadh");
	}
	
	/**
	 * Builds order tracking URL in format: https://qa.stylifashion.com/ae/en/account/orderview/{orderId}
	 * 
	 * @param order The sales order (may be null for split orders)
	 * @param salesShipment The sales shipment
	 * @return Tracking URL string, or empty string if not available
	 */
	private String buildOrderTrackingUrl(SalesOrder order, SalesShipment salesShipment) {
		try {
			// Get base URL from Consul config (same scope as ESTIMATED_DELIVERY_DATE_DAYS_TO_ADD)
			if (Constants.orderCredentials == null || Constants.orderCredentials.getOrderDetails() == null) {
				LOGGER.warn("OrderCredentials or OrderDetails is null, cannot build tracking URL");
				return "";
			}
			
			String baseUrl = Constants.orderCredentials.getOrderDetails().getOrderTrackingBaseUrl();
			if (StringUtils.isBlank(baseUrl)) {
				LOGGER.warn("ORDER_TRACKING_BASE_URL is not configured in Consul! Cannot build tracking URL for dangerous goods Braze event.");
				return "";
			}
			
			// Remove trailing slash from base URL if present
			if (baseUrl.endsWith("/")) {
				baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
			}
			
			// Check if this is a split order shipment
			SplitSalesOrder splitSalesOrder = null;
			if (salesShipment != null && salesShipment.getSplitSalesOrder() != null) {
				splitSalesOrder = salesShipment.getSplitSalesOrder();
			}
			
			// Get store ID and order ID
			// For both normal and split orders, use the parent order's entityId (same as OmsOrderoutboundresponse.orderId)
			Integer storeId = null;
			Integer orderId = null;
			
			if (splitSalesOrder != null) {
				storeId = splitSalesOrder.getStoreId();
				// Use parent order's entityId (same as what's set in OmsOrderoutboundresponse.orderId)
				SalesOrder parentOrder = splitSalesOrder.getSalesOrder();
				if (parentOrder != null) {
					orderId = parentOrder.getEntityId();
				}
			} else if (order != null) {
				storeId = order.getStoreId();
				orderId = order.getEntityId();
			}
			
			if (storeId == null || orderId == null) {
				LOGGER.warn("Cannot build tracking URL: storeId or orderId is null");
				return "";
			}
			
			// Get location code from store ID (ae, sa, kw, qa, bh, om)
			String locationCode = getLocationCodeFromStoreId(storeId);
			
			// Get language code from store (en, ar) - using existing logic pattern
			String languageCode = getLanguageCodeFromStore(storeId);
			
			// Build URL: https://qa.stylifashion.com/{locationCode}/{languageCode}/account/orderview/{orderId}
			String trackingUrl = baseUrl + "/" + locationCode + "/" + languageCode + "/account/orderview/" + orderId;
			
			LOGGER.info("Built order tracking URL for dangerous goods Braze event: " + trackingUrl);
			return trackingUrl;
			
		} catch (Exception e) {
			LOGGER.error("Error building order tracking URL for dangerous goods Braze event: " + e.getMessage(), e);
			return "";
		}
	}
	
	/**
	 * Gets location code (ae, sa, kw, qa, bh, om) from store ID
	 * 
	 * @param storeId The store ID
	 * @return Location code string
	 */
	private String getLocationCodeFromStoreId(Integer storeId) {
		if (storeId == null) {
			return "sa"; // Default to sa
		}
		
		switch (storeId) {
			case 1:
			case 3:
				return "sa";
			case 7:
			case 11:
				return "ae";
			case 12:
			case 13:
				return "kw";
			case 15:
			case 17:
				return "qa";
			case 19:
			case 21:
				return "bh";
			case 23:
			case 25:
				return "om";
			default:
				return "sa"; // Default to sa
		}
	}
	
	/**
	 * Gets language code (en, ar) from store ID using existing logic pattern
	 * Follows the same approach as OrderEntityConverter and WhatsappBotServiceImpl
	 * 
	 * @param storeId The store ID
	 * @return Language code string (en or ar)
	 */
	private String getLanguageCodeFromStore(Integer storeId) {
		if (storeId == null) {
			return "en"; // Default to en
		}
		
		List<Stores> stores = Constants.getStoresList();
		Optional<Stores> storeOpt = stores.stream()
				.filter(e -> Integer.valueOf(e.getStoreId()).equals(storeId))
				.findAny();
		
		if (storeOpt.isPresent()) {
			Stores storeValue = storeOpt.get();
			if (StringUtils.isNotBlank(storeValue.getStoreLanguage())) {
				String storeLanguage = storeValue.getStoreLanguage();
				// Extract language code (e.g., "en" from "en_US" or "ar" from "ar_SA")
				// Following the pattern used in CommonUtility.getLanguageCode
				if (storeLanguage.contains("_")) {
					return storeLanguage.split("_")[0];
				}
				return storeLanguage;
			}
		}
		return "en"; // Default to en
	}
	
	/**
	 * Checks if a split order item is a dangerous product based on product attributes
	 * Falls back to checking the linked SalesOrderItem if available
	 */
	private boolean isDangerousProductFromSplitOrderItem(SplitSalesOrderItem splitOrderItem) {
		// First try to use the linked SalesOrderItem if available
		if (splitOrderItem.getSalesOrderItem() != null) {
			return isDangerousProduct(splitOrderItem.getSalesOrderItem());
		}
		
		// Otherwise, check the split order item's additional_data field
		try {
			String additionalData = splitOrderItem.getAdditionalData();
			if (StringUtils.isBlank(additionalData)) {
				return false;
			}
			
			// Parse JSON from additional_data
			JsonNode attributesNode = mapper.readTree(additionalData);
			if (attributesNode == null || !attributesNode.isObject()) {
				return false;
			}
			
			// Check for dangerous_goods attribute
			JsonNode dangerousGoodsNode = attributesNode.get("dangerous_goods");
			if (dangerousGoodsNode != null && dangerousGoodsNode.isBoolean()) {
				return dangerousGoodsNode.asBoolean();
			}
			
			// Also check for is_dangerous_goods (alternative naming)
			JsonNode isDangerousGoodsNode = attributesNode.get("is_dangerous_goods");
			if (isDangerousGoodsNode != null && isDangerousGoodsNode.isBoolean()) {
				return isDangerousGoodsNode.asBoolean();
			}
		} catch (Exception e) {
			LOGGER.debug("Error parsing additional_data for split order item " + splitOrderItem.getItemId() + ": " + e.getMessage());
		}
		
		return false;
	}
	
	/**
	 * Checks if an order item is a dangerous product based on product attributes
	 */
	private boolean isDangerousProduct(SalesOrderItem orderItem) {
		try {
			String productAttributesJson = orderItem.getProductAttributes();
			if (productAttributesJson == null || productAttributesJson.isEmpty()) {
				return false;
			}
			Map<String, String> productAttributes = mapper.readValue(productAttributesJson, 
					new TypeReference<Map<String, String>>() {});
			return Boolean.parseBoolean(productAttributes.getOrDefault("is_dangerous_product", "false"));
		} catch (JsonProcessingException e) {
			LOGGER.warn("Error parsing product attributes for dangerous product check: " + e.getMessage());
			return false;
		}
	}
	
	/**
	 * Sends dangerous goods events to Braze
	 */
	private void sendDangerousGoodsEventToBraze(List<BrazeDangerousGoods.Event> events) {
		if (CollectionUtils.isEmpty(events)) {
			return;
		}
		
		BrazeDangerousGoods.RequestBody request = new BrazeDangerousGoods.RequestBody();
		request.setEvents(events);
		
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.add(Constants.AUTHORIZATION_HEADER_KEY, Constants.BEARER_HEADER_KEY + brazeAuthToken);
		
		HttpEntity<BrazeDangerousGoods.RequestBody> requestHttpEntity = new HttpEntity<>(request, requestHeaders);
		
		String url = StringUtils.EMPTY;
		if (ObjectUtils.isNotEmpty(Constants.orderCredentials)
				&& ObjectUtils.isNotEmpty(Constants.orderCredentials.getOrderDetails().getBrazeServiceBaseUrl())) {
			url = Constants.orderCredentials.getOrderDetails().getBrazeServiceBaseUrl() + "/users/track";
		}
		if (ObjectUtils.isEmpty(url)) {
			LOGGER.error("Braze base url not found!");
		} else {
			LOGGER.info("Braze dangerous goods submit Url: " + url);
			try {
				ResponseEntity<BrazeResponseBody> apiResponse = restTemplate.exchange(url, HttpMethod.POST,
						requestHttpEntity, BrazeResponseBody.class);
				if (ObjectUtils.isNotEmpty(apiResponse)) {
					LOGGER.info("Braze dangerous goods event response status: " + apiResponse.getStatusCode());
				} else {
					LOGGER.warn("Braze Response is null");
				}
			} catch (Exception e) {
				LOGGER.error("Error from Braze dangerous goods event submit:" + e.getMessage(), e);
				if (e instanceof HttpClientErrorException) {
					HttpClientErrorException httpException = (HttpClientErrorException) e;
					LOGGER.error("Braze HTTP Error Status: " + httpException.getStatusCode());
					LOGGER.error("Braze HTTP Error Response Body: " + httpException.getResponseBodyAsString());
					LOGGER.error("Braze HTTP Error Response Headers: " + httpException.getResponseHeaders());
				}
			}
		}
	}
	

	private List<BoxLabelDetails> buildBoxLabelDetails(SalesShipment salesShipment) {
		List<BoxLabelDetails> boxLabelDetails = new ArrayList<>();
		
		try {
			// Get pack details for this shipment
			List<SalesShipmentPackDetails> packDetails = salesShipmentPackDetailsRepository.findByShipmentId(salesShipment.getEntityId());
			
			if (packDetails != null && !packDetails.isEmpty()) {
				for (SalesShipmentPackDetails packDetail : packDetails) {
					BoxLabelDetails boxLabel = new BoxLabelDetails();
					boxLabel.setBoxId(packDetail.getBoxCode() != null ? packDetail.getBoxCode() : "BOX" + packDetail.getEntityId());
					
				// CRITICAL: Use getOrRefreshShippingLabelUrl to auto-refresh expired URLs
				// This ensures boxLabelUrl respects the gcs_shipping_label_enabled flag and refreshes expired URLs
				String boxLabelUrl = "";
				if (packDetail.getShippingLabel() != null && !packDetail.getShippingLabel().isEmpty()) {
					// Check if this is a GCS URL by checking if it has GCS object path
					if (packDetail.getGcsObjectPath() != null && !packDetail.getGcsObjectPath().isEmpty()) {
						// Has GCS data, use getOrRefresh to auto-refresh if expired
						try {
							String refreshedUrl = shippingLabelUrlService.getOrRefreshShippingLabelUrl(
								packDetail,
								SalesShipmentPackDetails.class
							);
							boxLabelUrl = refreshedUrl != null ? refreshedUrl : "";
						} catch (Exception e) {
							LOGGER.error("Failed to get/refresh box label URL: " + e.getMessage(), e);
							boxLabelUrl = "";
						}
					} else {
						// Not a GCS URL (original carrier URL or pre-GCS data), return as-is
						boxLabelUrl = packDetail.getShippingLabel();
					}
				}
					
					boxLabel.setBoxLabelUrl(boxLabelUrl);
					boxLabel.setBoxAwb(packDetail.getWayBill() != null ? packDetail.getWayBill() : "");
					boxLabelDetails.add(boxLabel);
				}
			}
		} catch (Exception e) {
			LOGGER.error("Error building box label details: " + e.getMessage(), e);
		}
		
		return boxLabelDetails;
	}

	private void processShipmentError(GetShipmentV3Response response, NavikResponse body)
			throws JsonProcessingException {
		if (null != body && null != body.getMeta()
				&& body.getMeta().getMessage() instanceof NavikErrormessage) {
			NavikErrormessage message = (NavikErrormessage) body.getMeta().getMessage();
			response.setErrorMessage(message.getErrorMessage());
		} else if (null != body && null != body.getMeta() && body.getMeta().getMessage() instanceof String) {
			response.setErrorMessage(body.getMeta().getMessage().toString());
		} else {
			if (null != body && null != body.getMeta()) {
				response.setErrorMessage(mapper.writeValueAsString(body.getMeta().getMessage()));
			}
		}
	}

	/**
	 * @param order
	 * @param order
	 */
	public void updateOrderStatusHistory(SalesOrder order, String message
			, String entity, String status) {
		SalesOrderStatusHistory sh = new SalesOrderStatusHistory();
		LOGGER.info("History set");
		sh.setParentId(order.getEntityId());
		sh.setCustomerNotified(0);
		sh.setVisibleOnFront(1);
		sh.setComment("Order updated with message: " + message);
		sh.setCreatedAt(new Timestamp(new Date().getTime()));
		sh.setStatus(status);
		sh.setEntityName(entity);
		salesOrderStatusHistoryRepository.saveAndFlush(sh);
	}

	@Override
	@Transactional(readOnly = true)
	public GetInvoiceV3Response getInvoiceV3(String orderCode, String shipmentCode) {
		GetInvoiceV3Response response = new GetInvoiceV3Response();
		GetInvoiceV3ResponseBody responseBody = new GetInvoiceV3ResponseBody();
		SplitSalesOrder splitSalesOrder;
		SalesOrder order;
		
		if(orderCode.contains(OrderConstants.LOCAL_ORDER_SUFFIX) || orderCode.contains(OrderConstants.GLOBAL_ORDER_SUFFIX)){
            order = null;
            splitSalesOrder = splitSalesOrderRepository.findByIncrementId(orderCode);
		}
		else {
            splitSalesOrder = null;
            order = salesOrderRepository.findByIncrementId(orderCode);
		}

		if (order == null && splitSalesOrder == null) {
			response.setHasError(true);
			response.setErrorMessage("invalid request");
			return response;
		}

		SalesInvoice salesInvoice = (order != null ? order.getSalesInvoices() : splitSalesOrder.getSalesInvoices())
									.stream().findFirst().orElse(null);
		
		DateFormat dateFormat = new SimpleDateFormat(OrderConstants.ORDER_PUSH_OMS_DATE_FORMAT);
		List<InvoiceDetail> invoiceDetails = new ArrayList<>();
		if (salesInvoice != null) {
			Date createDate = new Date(salesInvoice.getCreatedAt().getTime());
			String invoiceDate = dateFormat.format(createDate);
			invoiceDate = invoiceDate.concat(OrderConstants.WMS_DEFAULT_TIME_ZONE_STRING);
			responseBody.setInvoiceDate(invoiceDate);
			responseBody.setInvoiceCode(salesInvoice.getIncrementId());

			String encodeValue = null;
			Integer entityId = order != null ? order.getEntityId() : splitSalesOrder.getEntityId();
			String customerEmail = order != null ? order.getCustomerEmail() : splitSalesOrder.getCustomerEmail();
			if (null != Constants.orderCredentials && Constants.orderCredentials.getWms().isNewInVoiceEncode()) {
				String safeEmail = org.apache.commons.lang.StringUtils.defaultString(customerEmail);
				encodeValue = entityId.toString() + "#" + safeEmail;
			} else {
				encodeValue = entityId.toString();
			}
			String encoded = Base64.getEncoder().encodeToString(encodeValue.getBytes());
			responseBody.setInvoiceUrl(Constants.orderCredentials.getOrderDetails().getOmsServicePublicBaseUrl()
					+ GENERATE_PDF_URI + encoded);

			List<InvoiceDetail> invoiceDetailList = new ArrayList<>();
			salesInvoice.getSalesInvoiceItem()
					.forEach(ex -> {
						SalesOrderItem orderItem = null;
						SplitSalesOrderItem splitSalesOrderItem = null;
						if(order != null) {
							orderItem = order.getSalesOrderItem()
								.stream()
								.filter(e -> e.getItemId().equals(ex.getOrderItemId()))
								.findAny()
								.orElse(null);
						} else {
							splitSalesOrderItem = splitSalesOrder.getSplitSalesOrderItems()
							.stream()
							.filter(e -> e.getItemId().equals(ex.getSplitOrderItemId()))
							.findAny()
							.orElse(null);
						}

						InvoiceDetail invoiceDetail = new InvoiceDetail();
						invoiceDetail.setChannelSkuCode(ex.getSku());
						if (null != order && null != ex.getOrderItemId()) {
							invoiceDetail.setOrderItemCode(ex.getOrderItemId().toString());
						} else if (null != splitSalesOrder && null != ex.getSplitOrderItemId()) {
							invoiceDetail.setOrderItemCode(ex.getSplitOrderItemId().toString());
						} else {
							invoiceDetail.setOrderItemCode(null != ex.getOrderItemId()?ex.getOrderItemId().toString():"");
						}
						invoiceDetail.setNetTaxAmountPerUnit(parseNullBigDecimal(ex.getTaxAmount()));
						invoiceDetail.setBaseSellingPricePerUnit(parseNullBigDecimal(ex.getPrice()));
						invoiceDetail.setActualSellingPricePerUnit(parseNullBigDecimal(ex.getPriceInclTax()));
						BigDecimal qty = ex.getQuantity();
						invoiceDetail.setQuantity(qty.intValue());
						if (null != ex.getTaxAmount()) {
							invoiceDetail.setNetTaxAmountTotal(parseNullBigDecimal(ex.getTaxAmount().multiply(qty)));
						}
						if (null != ex.getRowTotal()) {
							BigDecimal itemRowTotal = ex.getRowTotal()
									.divide(qty, 4, RoundingMode.HALF_UP).setScale(4, RoundingMode.HALF_UP);
							invoiceDetail.setBaseSellingPriceTotal(parseNullBigDecimal(itemRowTotal));
							BigDecimal itemRowTotalIncTax = ex.getRowTotal().multiply(qty).setScale(4, RoundingMode.HALF_UP);
							invoiceDetail.setActualSellingPriceTotal(parseNullBigDecimal(itemRowTotalIncTax));

						}


						TaxItem taxItem = new TaxItem();
						if (null != ex.getTaxAmount()) {

							taxItem.setTaxTotal(parseNullBigDecimal(ex.getTaxAmount().multiply(qty)));

						}
						taxItem.setTaxPerUnit(parseNullBigDecimal(ex.getTaxAmount()));

						if (orderItem != null && null != orderItem.getTaxPercent()) {
							taxItem.setRate(orderItem.getTaxPercent().intValue());
						}
						else if(splitSalesOrderItem != null && null != splitSalesOrderItem.getTaxPercent()){
							taxItem.setRate(splitSalesOrderItem.getTaxPercent().intValue());
						}
						taxItem.setType("VAT");
						invoiceDetail.setTaxItems(Collections.singletonList(taxItem));
						invoiceDetailList.add(invoiceDetail);
					});

			Set<String> skuSet = new HashSet<>();
			List<InvoiceDetail> productBySkuList = invoiceDetailList.stream()
					.filter(e -> skuSet.add(e.getChannelSkuCode()))
					.collect(Collectors.toList());

			invoiceDetails.addAll(productBySkuList);

		} else {
			responseBody.setHasError(true);
			responseBody.setErrorMessage("no invoice found");
			return response;
		}

		Date createDate = new Date(salesInvoice.getCreatedAt().getTime());
		String invoiceDate = dateFormat.format(createDate);
		invoiceDate = invoiceDate.concat(OrderConstants.WMS_DEFAULT_TIME_ZONE_STRING);
		response.setInvoiceDate(invoiceDate);
		response.setInvoiceCode(responseBody.getInvoiceCode());
		String encodeValue = null;
		Integer entityId = order != null ? order.getEntityId() : splitSalesOrder.getEntityId();
		String customerEmail = order != null ? order.getCustomerEmail() : splitSalesOrder.getCustomerEmail();
		
		if(splitSalesOrder != null) {
			encodeValue = splitSalesOrder.getSalesOrder().getEntityId().toString().concat("#").concat(entityId.toString());
		} else {
			encodeValue = entityId.toString();
		}

		if (null != Constants.orderCredentials && Constants.orderCredentials.getWms().isNewInVoiceEncode()) {
			encodeValue = encodeValue.concat("#").concat(customerEmail);
		}

		String encoded = Base64.getEncoder().encodeToString(encodeValue.getBytes());
		response.setInvoiceUrl(
				Constants.orderCredentials.getOrderDetails().getOmsServicePublicBaseUrl() + GENERATE_PDF_URI + encoded);
		response.setInvoiceDetails(invoiceDetails);
		return response;
	}

	private void setShipmentDetails(SalesOrder order, GetShipmentV3Request navikRequest,
									Navikinfos navikInfos) {
		ShipmentDetails shipmentDetails = new ShipmentDetails();
		shipmentDetails.setWeight("0.1");
		shipmentDetails.setBreadth("10");
		shipmentDetails.setHeight("10");
		shipmentDetails.setLength("10");
		BigDecimal grandToalInvoiced = new BigDecimal(0);

		SalesInvoice salesInvoice = order.getSalesInvoices().stream().findFirst().orElse(null);
		if (null != salesInvoice) {


			if (null != order.getGrandTotal()) {

				grandToalInvoiced = salesInvoice.getGrandTotal();
			}
			if (null != order.getAmstorecreditAmount()) {

				grandToalInvoiced = grandToalInvoiced.add(salesInvoice.getAmstorecreditAmount());

			}
			shipmentDetails.setInvoice_value(grandToalInvoiced.toString());
		}
        
        
        SalesOrderPayment salesOrderPayment = order.getSalesOrderPayment().stream().findFirst().orElse(null);
        if (salesOrderPayment != null) {
            if (salesOrderPayment.getMethod().equalsIgnoreCase(OrderConstants.PAYMENT_METHOD_COD)) {
                shipmentDetails.setOrder_type("COD");
                
                if(null != order.getSubSalesOrder() && order.getSubSalesOrder().getIsUnfulfilmentOrder() == null) {
            		shipmentDetails.setCod_value(order.getGrandTotal().toString());
                }else if(null != order.getSubSalesOrder() && order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency() != null && order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency().compareTo(BigDecimal.ZERO)>0 && grandToalInvoiced.compareTo(order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency())>=0){
                	shipmentDetails.setCod_value(grandToalInvoiced.subtract(order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency()).setScale(2, RoundingMode.HALF_UP).toString());
                }else{
					shipmentDetails.setCod_value(grandToalInvoiced.toString());
				}
                		
            } else {
                shipmentDetails.setOrder_type("PREPAID");
                shipmentDetails.setCod_value("0");
            }
        }


		if (salesInvoice != null) {
			shipmentDetails.setInvoice_number(salesInvoice.getIncrementId());

			String[] dateParse = salesInvoice.getCreatedAt().toString().split(" ");

			if (ArrayUtils.isNotEmpty(dateParse) && dateParse.length > 0) {

				shipmentDetails.setInvoice_date(dateParse[0]);

			} else {
				shipmentDetails.setInvoice_date(salesInvoice.getCreatedAt().toString());
			}

		}
		shipmentDetails.setReference_number(order.getIncrementId());
		shipmentDetails.setCurrency_code(order.getOrderCurrencyCode());
		List<Item> items = new ArrayList<>();
		List<SalesOrderItem> childItemList = order.getSalesOrderItem().stream()
				.filter(e -> null != e.getProductType()
						&& !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
				.collect(Collectors.toList());
		for (SalesOrderItem salesOrderItem : childItemList) {
			if (null != salesOrderItem.getQtyCanceled() && null != salesOrderItem.getQtyOrdered()
					&& salesOrderItem.getQtyOrdered().compareTo(salesOrderItem.getQtyCanceled()) != 0) {
				Item item = new Item();
				item.setPrice(salesOrderItem.getPrice().toString());

				item.setQuantity(salesOrderItem.getQtyOrdered().subtract(salesOrderItem.getQtyCanceled()).toString());
				item.setSku(salesOrderItem.getSku());
				if (Boolean.TRUE.equals(OrderConstants.CHECKENGLISHSTORE.get(order.getStoreId()))
						&& null != navikInfos) {
					item.setDescription(navikInfos.getDescriptionEn());

				} else if (null != navikInfos) {
					item.setDescription(navikInfos.getDescriptionAr());
				}
				items.add(item);

			} else if (salesOrderItem.getQtyCanceled() == null) {

				Item item = new Item();
				item.setPrice(salesOrderItem.getPrice().toString());

				item.setQuantity(salesOrderItem.getQtyOrdered().toString());
				item.setSku(salesOrderItem.getSku());
				if (Boolean.TRUE.equals(OrderConstants.CHECKENGLISHSTORE.get(order.getStoreId()))
						&& null != navikInfos) {
					item.setDescription(navikInfos.getDescriptionEn());

				} else if (null != navikInfos) {
					item.setDescription(navikInfos.getDescriptionAr());
				}
				items.add(item);

			}
		}
		shipmentDetails.setItems(items);
		navikRequest.setShipmentDetails(shipmentDetails);
	}

	private void setSplitOrderShipmentDetails(SplitSalesOrder splitSalesOrder, GetShipmentV3Request navikRequest,
											Navikinfos navikInfos) {
		ShipmentDetails shipmentDetails = new ShipmentDetails();
		shipmentDetails.setWeight("0.1");
		shipmentDetails.setBreadth("10");
		shipmentDetails.setHeight("10");
		shipmentDetails.setLength("10");
		BigDecimal grandToalInvoiced = new BigDecimal(0);

		SalesOrder order = splitSalesOrder.getSalesOrder();
		SalesInvoice splitInvoice = splitSalesOrder.getSplitSalesInvoices().stream().findFirst().orElse(null);
		
		if (null != splitInvoice) {
			if (null != splitInvoice.getGrandTotal()) {
				grandToalInvoiced = splitInvoice.getGrandTotal();
			}
			if (null != splitInvoice.getAmstorecreditAmount()) {
				grandToalInvoiced = grandToalInvoiced.add(splitInvoice.getAmstorecreditAmount());
			}
			shipmentDetails.setInvoice_value(grandToalInvoiced.toString());
		}
        
		SalesOrderPayment salesOrderPayment = order.getSalesOrderPayment().stream().findFirst().orElse(null);
		if (salesOrderPayment != null) {
			if (salesOrderPayment.getMethod().equalsIgnoreCase(PaymentCodeENUM.CASH_ON_DELIVERY.getValue())) {
				shipmentDetails.setOrder_type("COD");
				
				if(null != order.getSubSalesOrder() && order.getSubSalesOrder().getIsUnfulfilmentOrder() == null) {
					shipmentDetails.setCod_value(splitSalesOrder.getGrandTotal().toString());
				} else if(null != order.getSubSalesOrder() && order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency() != null && order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency().compareTo(BigDecimal.ZERO)>0 && grandToalInvoiced.compareTo(order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency())>=0){
					shipmentDetails.setCod_value(grandToalInvoiced.subtract(order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency()).setScale(2, RoundingMode.HALF_UP).toString());
				} else {
					shipmentDetails.setCod_value(grandToalInvoiced.toString());
				}
			} else {
				shipmentDetails.setOrder_type("PREPAID");
				shipmentDetails.setCod_value("0");
			}
		}

		if (splitInvoice != null) {
			shipmentDetails.setInvoice_number(splitInvoice.getIncrementId());

			if (splitInvoice.getCreatedAt() != null) {
				shipmentDetails.setInvoice_date(splitInvoice.getCreatedAt().toLocalDateTime().toLocalDate().toString());
			}
		}
		shipmentDetails.setReference_number(splitSalesOrder.getIncrementId());
		shipmentDetails.setCurrency_code(splitSalesOrder.getOrderCurrencyCode());
		
		List<Item> items = new ArrayList<>();
		List<SplitSalesOrderItem> splitOrderItems = splitSalesOrder.getSplitSalesOrderItems().stream()
				.filter(e -> null != e.getProductType()
						&& !e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
				.collect(Collectors.toList());
				
		for (SplitSalesOrderItem splitOrderItem : splitOrderItems) {
			if (null != splitOrderItem.getQtyCanceled() && null != splitOrderItem.getQtyOrdered()
					&& splitOrderItem.getQtyOrdered().compareTo(splitOrderItem.getQtyCanceled()) != 0) {
				Item item = new Item();
				item.setPrice(splitOrderItem.getPrice().toString());
				item.setQuantity(splitOrderItem.getQtyOrdered().subtract(splitOrderItem.getQtyCanceled()).toString());
				item.setSku(splitOrderItem.getSku());
				if (Boolean.TRUE.equals(OrderConstants.CHECKENGLISHSTORE.get(order.getStoreId()))
						&& null != navikInfos) {
					item.setDescription(navikInfos.getDescriptionEn());
				} else if (null != navikInfos) {
					item.setDescription(navikInfos.getDescriptionAr());
				}
				items.add(item);
			} else if (splitOrderItem.getQtyCanceled() == null) {
				Item item = new Item();
				item.setPrice(splitOrderItem.getPrice().toString());
				item.setQuantity(splitOrderItem.getQtyOrdered().toString());
				item.setSku(splitOrderItem.getSku());
				if (Boolean.TRUE.equals(OrderConstants.CHECKENGLISHSTORE.get(order.getStoreId()))
						&& null != navikInfos) {
					item.setDescription(navikInfos.getDescriptionEn());
				} else if (null != navikInfos) {
					item.setDescription(navikInfos.getDescriptionAr());
				}
				items.add(item);
			}
		}
		shipmentDetails.setItems(items);
		navikRequest.setShipmentDetails(shipmentDetails);
	}

	private void  setRvpShipmentDetails(SalesOrder order, GetShipmentV3Request navikRequest,
									   AmastyRmaRequest amastyRmaRequest, Navikinfos navikInfos) {
		ShipmentDetails shipmentDetails = new ShipmentDetails();
		shipmentDetails.setWeight("0.1");
		shipmentDetails.setBreadth("10");
		shipmentDetails.setHeight("10");
		shipmentDetails.setLength("10");
		BigDecimal totaRefundAmount = getTotalRefundAmount(amastyRmaRequest, order);

		shipmentDetails.setInvoice_value(totaRefundAmount.toString());
		shipmentDetails.setOrder_type("PREPAID");
		shipmentDetails.setCod_value("0");

		SalesInvoice salesInvoice = order.getSalesInvoices().stream().findFirst().orElse(null);
		if (salesInvoice != null) {
			shipmentDetails.setInvoice_number(salesInvoice.getIncrementId());
			if (null != salesInvoice.getCreatedAt()) {
				String[] dateParse = salesInvoice.getCreatedAt().toString().split(" ");

				if (ArrayUtils.isNotEmpty(dateParse) && dateParse.length > 0) {

					shipmentDetails.setInvoice_date(dateParse[0]);

				} else {
					shipmentDetails.setInvoice_date(salesInvoice.getCreatedAt().toString());
				}
			}
		}
		shipmentDetails.setReference_number(amastyRmaRequest.getRmaIncId());
		shipmentDetails.setCurrency_code(order.getOrderCurrencyCode());
		List<Item> items = new ArrayList<>();

		for (AmastyRmaRequestItem amastyItem : amastyRmaRequest.getAmastyRmaRequestItems()) {
			if (amastyItem.getItemStatus().equals(12) || amastyItem.getItemStatus().equals(13)) continue;

			order.getSalesOrderItem()
					.stream()
					.filter(e -> e.getItemId().equals(amastyItem.getOrderItemId()))
					.findFirst()
					.ifPresent(orderItem -> {
						Item item = new Item();
						item.setPrice(orderItem.getPrice().toString());
						item.setQuantity(amastyItem.getQty().toString());
						item.setSku(orderItem.getSku());
						if (Boolean.TRUE.equals(OrderConstants.CHECKENGLISHSTORE.get(order.getStoreId()))
								&& null != navikInfos) {
							item.setDescription(navikInfos.getDescriptionEn());

						} else if (null != navikInfos) {
							item.setDescription(navikInfos.getDescriptionAr());
						}
						items.add(item);
					});
		}

		shipmentDetails.setItems(items);
		if ("IN".equals(regionValue) && Constants.orderCredentials.getNavik().isQcEnabled())
			shipmentDetails.setQc(Constants.orderCredentials.getNavik().getQcChecks());
		navikRequest.setShipmentDetails(shipmentDetails);
	}

	private void setAdditional(SalesOrder order,
							   GetShipmentV3Request navikRequest, Navikinfos navikInfos) {
		Additional additional = new Additional();
		additional.setDelivery_type("FORWARD");
		additional.setAsync(false);
		additional.setLabel(true);
		additional.setOrder_date(setFormatedDate(new Date(order.getCreatedAt().getTime())));
		if (null != navikInfos) {
			additional.setVendor_code(navikInfos.getVendorCode());
			additional.setDuty_fee_paid_by(navikInfos.getDutyFeePaid());
		}

		navikRequest.setAdditional(additional);
	}

	private void setRvpAdditional(SalesOrder order, GetShipmentV3Request navikRequest, String dutyFee
			, String rvpReason) {
		Additional additional = new Additional();
		additional.setDelivery_type("RVP");
		additional.setAsync(false);
		additional.setLabel(true);
		additional.setOrder_date(setFormatedDate(new Date(order.getCreatedAt().getTime())));
		additional.setRvp_reason(rvpReason);


		additional.setDuty_fee_paid_by(dutyFee);
		navikRequest.setAdditional(additional);
	}

	private void setDropInfo(SalesOrder order, GetShipmentV3Request navikRequest) {

		SalesOrderAddress shippingAddress = order.getSalesOrderAddress().stream()
				.filter(e -> e.getAddressType().equalsIgnoreCase(SHIPPING)).findAny().orElse(null);
		DropInfo dropInfo = new DropInfo();
		if (shippingAddress != null) {
			dropInfo.setEmail(shippingAddress.getEmail());
			dropInfo.setName(shippingAddress.getFirstname() + " " + shippingAddress.getLastname());
			dropInfo.setLandmark(shippingAddress.getNearestLandmark());
			dropInfo.setAddress(shippingAddress.getStreet());
			dropInfo.setCountry_code(shippingAddress.getCountryId());


			boolean arabicTextCheck = checkArabicTextCheck(shippingAddress.getCity());

			if (Boolean.TRUE.equals(OrderConstants.CHECKENGLISHSTORE.get(order.getStoreId()))
					&& !arabicTextCheck) {

				dropInfo.setCity(shippingAddress.getCity());
				dropInfo.setState(shippingAddress.getRegion());
				dropInfo.setCountry_code(shippingAddress.getCountryId());
				dropInfo.setPostal_code(shippingAddress.getArea());

			} else {

				try {
					AddressDetails addressDetails = navikHelper.getArabicAddressDetails(shippingAddress);
					dropInfo.setCity(addressDetails.getCityName());
					dropInfo.setState(addressDetails.getProvienceName());

					if (null != shippingAddress.getLatitude() && null != shippingAddress.getLongitude()) {

						dropInfo.setPostal_code(shippingAddress.getArea());

					} else {
						dropInfo.setPostal_code(addressDetails.getAreaName());
					}

				} catch (JSONException e1) {

					LOGGER.error("error in jon apring during fetch arabic address");
				}
			}


			if (null != shippingAddress.getTelephone() && shippingAddress.getTelephone().contains(" ")
					&& ArrayUtils.isNotEmpty(shippingAddress.getTelephone().split(" "))
					&& shippingAddress.getTelephone().split(" ").length > 0) {

				dropInfo.setPhone(shippingAddress.getTelephone().split(" ")[1]);
				dropInfo.setPhone_code(shippingAddress.getTelephone().split(" ")[0]);
			} else {

				dropInfo.setPhone(shippingAddress.getTelephone());
				dropInfo.setPhone_code("");
			}

		}

		if (Objects.nonNull(shippingAddress) && null != shippingAddress.getLatitude()) {
			String formattedAddress = shippingAddress.getFormattedAddress();
			dropInfo.setLatitude(shippingAddress.getLatitude().toString());
			String postalCode = dropInfo.getPostal_code();
			dropInfo.setPostal_code(dropInfo.getCity());
			if (StringUtils.isNotBlank(postalCode)) {
				dropInfo.setAddress(
						shippingAddress.getStreet().concat(" ").concat(postalCode) + " " + formattedAddress);
			}
		} else {
			dropInfo.setLatitude("");
		}

		if (Objects.nonNull(shippingAddress) && null != shippingAddress.getLongitude()) {
			dropInfo.setLongitude(shippingAddress.getLongitude().toString());
		} else {
			dropInfo.setLongitude("");
		}


		navikRequest.setDropInfo(dropInfo);
	}

	private void setRvpDropInfo(GetShipmentV3Request navikRequest, NavikAddress addressDetails
			, String dropOffAddress) {

		DropInfo dropInfo = new DropInfo();

		if (null != addressDetails) {

			dropInfo.setLandmark(null);
			dropInfo.setState(addressDetails.getState());
			if (null != dropOffAddress) {
				dropInfo.setAddress(dropOffAddress);
			} else {

				dropInfo.setAddress(addressDetails.getAddress());
			}

			dropInfo.setCountry_code(addressDetails.getCountryCode());
			dropInfo.setName(addressDetails.getName());
			dropInfo.setPhone(addressDetails.getPhone());
			dropInfo.setPhone_code(addressDetails.getPhoneCode());
			dropInfo.setEmail(addressDetails.getEmail());
			dropInfo.setCity(addressDetails.getCity());
			dropInfo.setPostal_code(addressDetails.getPosatalCode());
			navikRequest.setDropInfo(dropInfo);
		}

	}

	@NotNull
	private PickupInfo setPickupInfo(GetShipmentV3Request navikRequest, NavikAddress addressDetails) {


		PickupInfo pickupInfo = new PickupInfo();

		if (null != addressDetails) {

			pickupInfo.setLandmark(null);
			pickupInfo.setState(addressDetails.getState());
			pickupInfo.setAddress(addressDetails.getAddress());
			pickupInfo.setCountry_code(addressDetails.getCountryCode());
			pickupInfo.setTime(setFormatedDate(new Date()));
			pickupInfo.setName(addressDetails.getName());
			pickupInfo.setPhone(addressDetails.getPhone());
			pickupInfo.setPhone_code(addressDetails.getPhoneCode());
			pickupInfo.setEmail(addressDetails.getEmail());
			pickupInfo.setCity(addressDetails.getCity());
			pickupInfo.setPostal_code(addressDetails.getPosatalCode());
			pickupInfo.setLatitude("");
			pickupInfo.setLongitude("");
			navikRequest.setPickupInfo(pickupInfo);
		}

		return pickupInfo;
	}

	@NotNull
	private PickupInfo setRvpPickupInfo(GetShipmentV3Request navikRequest, SalesOrder order) {

		SalesOrderAddress shippingAddress = order.getSalesOrderAddress().stream()
				.filter(e -> e.getAddressType().equalsIgnoreCase(SHIPPING)).findAny().orElse(null);

		PickupInfo pickupInfo = new PickupInfo();

		if (shippingAddress != null) {
			String formattedAddress = shippingAddress.getFormattedAddress();
			pickupInfo.setTime(setFormatedDate(new Date()));
			pickupInfo.setEmail(shippingAddress.getEmail());
			pickupInfo.setName(shippingAddress.getFirstname() + " " + shippingAddress.getLastname());
			pickupInfo.setLandmark(shippingAddress.getNearestLandmark());
			pickupInfo.setAddress(shippingAddress.getStreet());
			pickupInfo.setCountry_code(shippingAddress.getCountryId());

			if (null != shippingAddress.getLatitude()) {
				pickupInfo.setLatitude(shippingAddress.getLatitude().toString());
				pickupInfo.setAddress(shippingAddress.getStreet() + " " + formattedAddress);
			} else {
				pickupInfo.setLatitude("");
			}

			if (null != shippingAddress.getLongitude()) {
				pickupInfo.setLongitude(shippingAddress.getLongitude().toString());
			} else {
				pickupInfo.setLongitude("");
			}

			boolean arabicTextCheck = checkArabicTextCheck(shippingAddress.getCity());


			if (Boolean.TRUE.equals(OrderConstants.CHECKENGLISHSTORE.get(order.getStoreId()))
					&& !arabicTextCheck) {

				pickupInfo.setCity(shippingAddress.getCity());
				pickupInfo.setState(shippingAddress.getRegion());
				pickupInfo.setPostal_code(shippingAddress.getArea());
			} else {

				try {
					AddressDetails addressDetails = navikHelper.getArabicAddressDetails(shippingAddress);
					pickupInfo.setCity(addressDetails.getCityName());
					pickupInfo.setState(addressDetails.getProvienceName());
					pickupInfo.setPostal_code(addressDetails.getAreaName());

				} catch (JSONException e1) {

					LOGGER.error("error in jon apring during fetch arabic address");
				}
			}

			if (null != shippingAddress.getLatitude()) {
				String postalCode = pickupInfo.getPostal_code();
				if (StringUtils.isNotBlank(postalCode)) {
					pickupInfo.setAddress(
							shippingAddress.getStreet().concat(" ").concat(postalCode) + " " + formattedAddress);
				}
				pickupInfo.setPostal_code(pickupInfo.getCity());
			}

			if (null != shippingAddress.getTelephone()
					&& shippingAddress.getTelephone().contains(" ") && shippingAddress.getTelephone().split(" ").length > 0) {

				pickupInfo.setPhone(shippingAddress.getTelephone().split(" ")[1]);
				pickupInfo.setPhone_code(shippingAddress.getTelephone().split(" ")[0]);

			} else {

				pickupInfo.setPhone(shippingAddress.getTelephone());
				pickupInfo.setPhone_code("");
			}


		}

		navikRequest.setPickupInfo(pickupInfo);
		return pickupInfo;
	}

	private boolean checkArabicTextCheck(String city) {

		for (int i = 0; i < city.length(); ) {
			int c = city.codePointAt(i);
			if (c >= 0x0600 && c <= 0x06E0)
				return true;
			i += Character.charCount(c);
		}
		return false;

	}

	@NotNull
	private PickupInfo setRvpPickupInfo(GetShipmentV3Request navikRequest) {


		PickupInfo pickupInfo = new PickupInfo();
		pickupInfo.setLandmark(null);
		pickupInfo.setState(AL_RIYADH);
		pickupInfo.setAddress(RETAIL_CART_TRADING_CO_MAKHZAN2_NEW_WAREHOUSE_AL_BARIAH);
		pickupInfo.setCountry_code(SA);
		pickupInfo.setTime(setFormatedDate(new Date()));
		pickupInfo.setName(WWW_STYLISHOP_COM);
		pickupInfo.setPhone(PHONENUMBNER);
		pickupInfo.setPhone_code(COUNTRYCODE);
		pickupInfo.setEmail(HELLO_KSA_STYLISHOP_COM);
		pickupInfo.setCity(CITYADDRESS);
		pickupInfo.setPostal_code("Ad Difa");
		navikRequest.setPickupInfo(pickupInfo);
		return pickupInfo;
	}


	private String setFormatedDate(Date currentDate) {

		DateFormat dateFormat = new SimpleDateFormat(OrderConstants.NAVIK_SHIPMENT_OMS_DATE_FORMAT);
		return dateFormat.format(currentDate);
	}

	private BigDecimal parseNullBigDecimal(BigDecimal val) {
		return (val == null) ? null : val.setScale(2, RoundingMode.HALF_UP);
	}

	@Override
	public OrderResponseDTO rmaUpdateVersionTwo(RMAUpdateV2Request request) {

		OrderResponseDTO resp = new OrderResponseDTO();

		if (ObjectUtils.isEmpty(request.getCustomerId())
				|| ObjectUtils.isEmpty(request.getRequestId())
				|| ObjectUtils.isEmpty(request.getStoreId())
				|| CollectionUtils.isEmpty(request.getItems())) {
			resp.setStatus(false);
			resp.setStatusCode("201");
			resp.setStatusMsg("Parameters missing!");
			return resp;
		}

		for (RMAUpdateItemV2Request item : request.getItems()) {
			if (ObjectUtils.isEmpty(item.getReasonId())) {
				resp.setStatus(false);
				resp.setStatusCode("202");
				resp.setStatusMsg("Reason(s) missing!");
				return resp;
			}
			if (ObjectUtils.isEmpty(item.getReturnQuantity())) {
				resp.setStatus(false);
				resp.setStatusCode("203");
				resp.setStatusMsg("item Qty missing!");
				return resp;
			}
		}

		AmastyRmaRequest rmaRequest = amastyRmaRequestRepository.findByRequestId(request.getRequestId());
		if (ObjectUtils.isEmpty(rmaRequest)) {
			resp.setStatus(false);
			resp.setStatusCode("204");
			resp.setStatusMsg("RMA not found!");
			return resp;
		}

		if (ObjectUtils.isNotEmpty(request.getStatus())) {
			rmaRequest.setStatus(request.getStatus());
			rmaRequest.getAmastyRmaRequestItems()
					.stream()
					.filter(e -> !e.getItemStatus().equals(12))
					.filter(e -> !e.getItemStatus().equals(13))
					.forEach(el -> el.setItemStatus(request.getStatus()));
		}

//            If RMA status selected as "cancelled by customer/rejected by admin
//            Request body should have this flag as true
		if (request.getCloseRma().booleanValue()) {
			rmaRequest.getAmastyRmaRequestItems()
					.forEach(el -> {
						el.setItemStatus(12);
					});
			setStatusMessageForRmaUpdate(rmaRequest, OrderConstants.CLOSE_RMA_ORDER);
			if (null != Constants.orderCredentials && null != Constants.orderCredentials.getOrderDetails()) {
				boolean pushToWmsFlag = Constants.orderCredentials.getOrderDetails().isRetCanPushToWms();
				if (pushToWmsFlag) {
					try {

						List<WMSReturnCancelItem> cancelItems = new ArrayList<>();
						WMSReturnCancelRequest cancelRequest = new WMSReturnCancelRequest();
						cancelRequest.setLocationCode(Constants.orderCredentials.getInventoryMapping().get(0).getWareHouseId());
						cancelRequest.setReturnOrderCode(rmaRequest.getRmaIncId());
						SalesOrder orderCode = orderRepository.findByEntityId(rmaRequest.getOrderId());
						cancelRequest.setOrderCode(orderCode.getIncrementId());
						LOGGER.info("Cancel the return order for order id: " + orderCode.getIncrementId());

						rmaRequest.getAmastyRmaRequestItems().forEach(item -> {

							SalesOrderItem orderItem = orderItemRepository.findByItemId(item.getOrderItemId());

							Integer requestItemId = item.getRequestItemId();
							Integer orderItemId = item.getOrderItemId();
							BigDecimal qty = item.getQty();
							for (int i = 1; i <= qty.intValue(); i++) {
								String returnItemOrderCode = String.format("%d_%d%d", orderItemId, requestItemId, i);
								LOGGER.info("returnItemOrderCode: " + returnItemOrderCode);
								LOGGER.info("Cancel the return order for return id : " + rmaRequest.getRmaIncId());
								WMSReturnCancelItem cancelItem = new WMSReturnCancelItem();
								cancelItem.setChannelSkuCode(orderItem.getSku());
								LOGGER.info("ChannelSkuCode: " + orderItem.getSku());
								cancelItem.setReturnOrderItemCode(returnItemOrderCode);
								cancelItems.add(cancelItem);
							}

						});

						cancelRequest.setReturnOrderItems(cancelItems);
						LOGGER.info("WMS return cancel request : " + cancelRequest.toString());
						rmaUtil.pushReturnCancelToWms(cancelRequest);
					} catch (Exception e) {
						LOGGER.error("updateCancelledReturnToWMS error:", e);
					}
				}
			}
		} else {
			for (RMAUpdateItemV2Request item : request.getItems()) {
				rmaRequest.getAmastyRmaRequestItems()
						.stream().filter(e -> e.getRequestItemId().equals(item.getRequestItemId()))
						.findAny().ifPresent(rmaRequestItem -> {
							if (item.getReturnQuantity() == 0) {
								rmaRequestItem.setQty(BigDecimal.ZERO);
								rmaRequestItem.setRequestQty(BigDecimal.ZERO);
								rmaRequestItem.setItemStatus(12);
							} else {
								rmaRequestItem.setQty(new BigDecimal(item.getReturnQuantity()));
								rmaRequestItem.setRequestQty(new BigDecimal(item.getReturnQuantity()));
								rmaRequestItem.setReasonId(item.getReasonId());
							}
						});

			}
		}

		List<AmastyRmaRequestItem> nonCancelledItems = rmaRequest.getAmastyRmaRequestItems()
				.stream()
				.filter(e -> !e.getItemStatus().equals(12))
				.filter(e -> !e.getItemStatus().equals(13))
				.collect(Collectors.toList());

		if (nonCancelledItems.isEmpty()) {
			rmaRequest.setStatus(12);
			setStatusMessageForRmaUpdate(rmaRequest, "close");
		}

		amastyRmaRequestRepository.saveAndFlush(rmaRequest);


		resp.setStatus(true);
		resp.setStatusCode("200");
		resp.setStatusMsg("Successfully updated the RMA!");
		return resp;
	}

	public void setStatusMessageForRmaUpdate(AmastyRmaRequest rmaRequest, String type) {
		try {
			List<StatusChangeHistory> statusChangeHistoryList = statusChaneHistoryRepository.findByOrderId(rmaRequest.getOrderId().toString());
			if (CollectionUtils.isNotEmpty(statusChangeHistoryList)) {
				for (StatusChangeHistory statusChangeHistory:statusChangeHistoryList) {
					if (Objects.equals(type, OrderConstants.CREATE_RMA_ORDER)) {
						statusChangeHistory.setRmaCreatedDate(new Timestamp(new Date().getTime()));
						statusChangeHistory.setUpdatedAt(new Timestamp(new Date().getTime()));
					} else if (Objects.equals(type, OrderConstants.CLOSE_RMA_ORDER)) {
						statusChangeHistory.setRmaCancelDate(new Timestamp(new Date().getTime()));
						statusChangeHistory.setUpdatedAt(new Timestamp(new Date().getTime()));
					}
					statusChaneHistoryRepository.saveAndFlush(statusChangeHistory);
				}
			}
		} catch (Exception e) {
			LOGGER.error("Error in setStatusMessageForRmaUpdate : "+e.getMessage(),e);
		}

	}

	private BigDecimal getQuantitySpecificValue(BigDecimal value, BigDecimal originalQty, BigDecimal newQty) {
		BigDecimal newValue = BigDecimal.ZERO;
		if (ObjectUtils.isNotEmpty(value) && ObjectUtils.isNotEmpty(originalQty) && ObjectUtils.isNotEmpty(newQty)) {
			newValue = value.multiply(newQty).divide(originalQty, 4, RoundingMode.HALF_UP);
		}
		return newValue;
	}

	@Override
	public RecreateOrderResponseDTO recreateOrder(Map<String, String> httpRequestHeaders,
												  RecreateOrder request,
												  String incrementId,
												  SalesOrder order) {

		RecreateOrderResponseDTO resp = new RecreateOrderResponseDTO();

		SubSalesOrder subSalesOrder = order.getSubSalesOrder();
		if (Objects.nonNull(request.getIsSubmit()) && request.getIsSubmit().booleanValue() && incrementId == null) {
			resp.setStatus(false);
			resp.setStatusCode("201");
			resp.setStatusMsg("Increment ID not found!");
			return resp;
		}

		List<Stores> stores = Constants.getStoresList();
		Stores store = stores.stream()
				.filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId()))
				.findAny()
				.orElse(null);

		if (store == null || store.getStoreCode() == null || store.getStoreCurrency() == null) {
			resp.setStatus(false);
			resp.setStatusCode("202");
			resp.setStatusMsg("Store not found!");
			return resp;
		} 

		QuoteDTO quote = new QuoteDTO();


		BigDecimal amastyBaseStoreBalance = new BigDecimal(0);

		BigDecimal appliedStoreCredit = request.getStoreCreditApplied();
		if (request.getStoreCreditApplied() != null && appliedStoreCredit.compareTo(BigDecimal.ZERO) > 0) {

			List<AmastyStoreCredit> amastyStoreCredits = amastyStoreCreditRepository
					.findByCustomerId(order.getCustomerId());
			AmastyStoreCredit amastyStoreCredit = !amastyStoreCredits.isEmpty() ? amastyStoreCredits.get(0) : null;
			if (amastyStoreCredit != null && amastyStoreCredit.getStoreCredit() != null) {
				LOGGER.info("conversion rate:" + store.getCurrencyConversionRate());
				amastyBaseStoreBalance = amastyStoreCredit.getStoreCredit();
				BigDecimal convertedStoreCredit = amastyStoreCredit.getStoreCredit().divide(store.getCurrencyConversionRate(),
						4, RoundingMode.HALF_UP);
				int result = appliedStoreCredit.compareTo(convertedStoreCredit);
				if (result > 0) {
					resp.setStatus(false);
					resp.setStatusCode("203");
					resp.setStatusMsg("Invalid Store Credit applied!");
					return resp;
				} else {
					quote.setStoreCreditApplied(parseNullStr(appliedStoreCredit));
					quote.setStoreCreditBalance(parseNullStr(amastyBaseStoreBalance));
				}
			}
		}

		String paymentMethod = request.getPaymentMethod().getValue();
		int source = 3;

		if (order.getStatus().equalsIgnoreCase(OrderConstants.CANCELED_ORDER_STATE)
				|| order.getStatus().equalsIgnoreCase(OrderConstants.CLOSED_ORDER_STATUS)) {


			/**
			 * Skipped fields
			 * calcSource
			 * discount
			 * estimatedTotal
			 * flashSale
			 * isCouponApplicable
			 * rowTotalWithDiscount
			 * selectedAddressId
			 * selectedPaymentMethod
			 * defaultAddress
			 * shippingDescription
			 * shippingFreeCeiling
			 * shippingMethod
			 * shippingThreshold
			 * shippingWaived
			 */

			List<CatalogProductEntityForQuoteDTO> products = new ArrayList<>();
			List<OmsProduct> responseProducts = new ArrayList<>();
			List<SalesOrderItem> SalesOrderItems = order.getSalesOrderItem().stream().filter(
							e -> e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
					.collect(Collectors.toList());

			BigDecimal subtotal = BigDecimal.ZERO;
			BigDecimal taxAmount = BigDecimal.ZERO;
			BigDecimal totalQtyOrdered = BigDecimal.ZERO;
			BigDecimal subtotalInclTax = BigDecimal.ZERO;
			BigDecimal discountTaxCompensationAmount = BigDecimal.ZERO;
			BigDecimal totalCouponDiscount = BigDecimal.ZERO;
			BigDecimal totalAutoCouponDiscount = BigDecimal.ZERO;

			List<String> skus = new ArrayList<>();
			for (Integer requestItemId : request.getRequestedItems()) {
				BigDecimal requestedQty = new BigDecimal(request.getRequestedItemsQty().get(requestItemId));
				SalesOrderItem parentItem = order.getSalesOrderItem()
						.stream()
						.filter(e -> Objects.equals(e.getItemId(), requestItemId))
						.findFirst()
						.orElse(null);
				SalesOrderItem item = order.getSalesOrderItem()
						.stream()
						.filter(e -> e.getProductType().equals("simple"))
						.filter(e -> Objects.equals(e.getParentOrderItem().getItemId(), requestItemId))
						.findFirst()
						.orElse(null);
				if (ObjectUtils.isNotEmpty(parentItem) && ObjectUtils.isNotEmpty(item)) {

					/**
					 * brandName
					 * discount
					 * isMulin
					 * priceInclTax
					 * productStatus
					 * quantityStock
					 * sizes
					 * superAttributeId
					 * superAttributeLabel
					 * superAttributeValue
					 */

					CatalogProductEntityForQuoteDTO product = new CatalogProductEntityForQuoteDTO();
					OmsProduct omsProduct = new OmsProduct();
					omsProduct.setParentOrderItemId(requestItemId);

					List<AppliedCouponValue> appliedCouponValues = new ArrayList<>();
					for (SubSalesOrderItem subSalesOrderItem : parentItem.getSubSalesOrderItem()) {
						AppliedCouponValue appliedCouponValue = new AppliedCouponValue();
						appliedCouponValue.setCoupon(subSalesOrderItem.getCouponName());
						appliedCouponValue.setDiscount(getQuantitySpecificValue(subSalesOrderItem.getDiscount(), parentItem.getQtyOrdered(), requestedQty));
						appliedCouponValue.setType(subSalesOrderItem.getCouponType());
						appliedCouponValues.add(appliedCouponValue);

						if (subSalesOrderItem.getCouponType().equalsIgnoreCase("MANUAL")) {
							BigDecimal currCouponDiscount = getQuantitySpecificValue(subSalesOrderItem.getDiscount(), parentItem.getQtyOrdered(), requestedQty);
							totalCouponDiscount = totalCouponDiscount.add(currCouponDiscount);
						} else if (subSalesOrderItem.getCouponType().equalsIgnoreCase("AUTO")) {
							BigDecimal currAutoCouponDiscount = getQuantitySpecificValue(subSalesOrderItem.getDiscount(), parentItem.getQtyOrdered(), requestedQty);
							totalAutoCouponDiscount = totalAutoCouponDiscount.add(currAutoCouponDiscount);
						}
					}
					product.setAppliedCouponValue(appliedCouponValues);

					BigDecimal itemDiscountAmount = getQuantitySpecificValue(item.getDiscountAmount(), parentItem.getQtyOrdered(), requestedQty);
					product.setDiscountAmount(parseNullStr(itemDiscountAmount));
					omsProduct.setDiscount(parseNullStr(itemDiscountAmount));
					product.setDiscountPercent(parseNullStr(item.getDiscountPercent()));
					if (item.getDiscountTaxCompensationAmount() == null) {
						product.setDiscountTaxCompensationAmount(parseNullStr(BigDecimal.ZERO));
					} else {
						product.setDiscountTaxCompensationAmount(parseNullStr(getQuantitySpecificValue(item.getDiscountTaxCompensationAmount(), parentItem.getQtyOrdered(), requestedQty)));
					}

					product.setName(parseNullStr(parentItem.getName()));
					product.setParentProductId(parseNullStr(parentItem.getProductId()));
					product.setParentSku(parseNullStr(parentItem.getSku()));
					product.setPrice(parseNullStr(parentItem.getPrice()));
					PriceDetails priceDetails = new PriceDetails();
					priceDetails.setPrice(parseNullStr(parentItem.getOriginalPrice()));
					priceDetails.setSpecialPrice(parseNullStr(parentItem.getPriceInclTax()));
					product.setPrices(priceDetails);
					product.setProductId(parseNullStr(item.getProductId()));
					product.setQuantity(parseNullStr(requestedQty));
					product.setRowTotal(parseNullStr(getQuantitySpecificValue(parentItem.getRowTotal(), parentItem.getQtyOrdered(), requestedQty)));

					BigDecimal itemRTIT = getQuantitySpecificValue(parentItem.getRowTotalInclTax(), parentItem.getQtyOrdered(), requestedQty);
					product.setRowTotalInclTax(parseNullStr(itemRTIT));
					omsProduct.setRowTotalInclTax(parseNullStr(itemRTIT));

					product.setSku(parseNullStr(item.getSku()));
					skus.add(item.getSku());
					product.setParentSku(parseNullStr(parentItem.getSku()));
					BigDecimal itemTaxAmount = BigDecimal.ZERO;
					List<OmsProductTax> productTaxObj = null;
					if ("IN".equalsIgnoreCase(regionValue)) {
						TaxObject taxObject = new TaxObject();
						taxObject.setIntraState(false);
						productTaxObj = omsorderentityConverter.getTaxObjects(parentItem, true);
						for (OmsProductTax taxObj : productTaxObj) {
							BigDecimal productTax = new BigDecimal(taxObj.getTaxAmount()).multiply(requestedQty).setScale(4, RoundingMode.HALF_UP);
							if (taxObj.getTaxType().equals("IGST")) {
								if (new BigDecimal(taxObj.getTaxPercentage()).compareTo(BigDecimal.ZERO) == 0) {
									taxObject.setIntraState(true);
								}
								taxObject.setTaxIGST(taxObj.getTaxPercentage());
								taxObject.setTaxIGSTAmount(parseNullStr(productTax));
							} else if (taxObj.getTaxType().equals("CGST")) {
								taxObject.setTaxCGST(taxObj.getTaxPercentage());
								taxObject.setTaxCGSTAmount(parseNullStr(productTax));
							} else if (taxObj.getTaxType().equals("SGST")) {
								taxObject.setTaxSGST(taxObj.getTaxPercentage());
								taxObject.setTaxSGSTAmount(parseNullStr(productTax));
							}
							itemTaxAmount = itemTaxAmount.add(productTax);
						}
						product.setTaxObj(taxObject);
						omsProduct.setTaxObjects(productTaxObj);
						product.setHsnCode(parseNullStr(item.getHsnCode()));
					} else {
						itemTaxAmount = getQuantitySpecificValue(parentItem.getTaxAmount(),
								parentItem.getQtyOrdered(), requestedQty);
					}


					product.setTaxAmount(parseNullStr(itemTaxAmount));
					omsProduct.setTaxAmount(parseNullStr(itemTaxAmount));

					product.setTaxPercent(parseNullStr(parentItem.getTaxPercent()));

					for (SalesOrderItem orderItem : SalesOrderItems) {
						if (orderItem != null && orderItem.getSku().equals(product.getProductId())) {
							if (orderItem.getReturnable() != null && orderItem.getReturnable() == 1) {
								product.setReturnable(true);
								LOGGER.info("Returnable Set in recreate : Returnable set true for " + orderItem.getProductId());
							}
						}
					}

					products.add(product);
					responseProducts.add(omsProduct);

					taxAmount = taxAmount.add(itemTaxAmount);
					totalQtyOrdered = totalQtyOrdered.add(requestedQty);
					subtotalInclTax = subtotalInclTax.add((getQuantitySpecificValue(item.getRowTotalInclTax(),
							parentItem.getQtyOrdered(), requestedQty)));
					discountTaxCompensationAmount = discountTaxCompensationAmount.add(getQuantitySpecificValue(
							item.getDiscountTaxCompensationAmount(), parentItem.getQtyOrdered(), requestedQty));
					subtotal = subtotal.add(
							getQuantitySpecificValue(item.getRowTotal(), parentItem.getQtyOrdered(), requestedQty));
					subtotal = subtotal.add(getQuantitySpecificValue(item.getDiscountTaxCompensationAmount(),
							parentItem.getQtyOrdered(), requestedQty));
				}
			}

			quote.setProducts(products);

			if (subSalesOrder != null) {
				quote.setQuoteId(parseNullStr(subSalesOrder.getExternalQuoteId()));
				quote.setAutoCouponApplied(subSalesOrder.getExternalAutoCouponCode());
				quote.setAutoCouponDiscount(parseNullStr(totalAutoCouponDiscount));
				if (subSalesOrder.getWhiteListedCustomer() != null) {
					quote.setIsWhitelistedCustomer(subSalesOrder.getWhiteListedCustomer() == 1);
				} else quote.setIsWhitelistedCustomer(false);
				if (subSalesOrder.getDiscountData() != null) {
					try {
						DiscountData[] discountData = mapper.readValue(subSalesOrder.getDiscountData(), DiscountData[].class);

						DiscountData manual = Arrays.stream(discountData)
								.filter(e -> e.getRedeemType().equalsIgnoreCase("MANUAL"))
								.findAny().orElse(null);
						if (manual != null) manual.setValue(parseNullStr(totalCouponDiscount));

						DiscountData auto = Arrays.stream(discountData)
								.filter(e -> e.getRedeemType().equalsIgnoreCase("AUTO"))
								.findAny().orElse(null);
						if (auto != null) auto.setValue(parseNullStr(totalAutoCouponDiscount));


						quote.setDiscountData(Arrays.asList(discountData));
					} catch (JsonProcessingException e) {
						LOGGER.error("Error getting discountData from subSalesOrder" + e.getMessage());
					}
				}
			}


			BigDecimal totalPromoDiscount = totalCouponDiscount.add(totalAutoCouponDiscount);
			BigDecimal subtotalWithDiscount = subtotalInclTax.subtract(totalPromoDiscount);

			BigDecimal codCharges = order.getCashOnDeliveryFee();
			BigDecimal shipmentCharges = order.getShippingAmount();

			BigDecimal baseGrandTotal = subtotalInclTax;
			baseGrandTotal = baseGrandTotal.add(shipmentCharges);
			baseGrandTotal = baseGrandTotal.add(codCharges);
			baseGrandTotal = baseGrandTotal.subtract(totalPromoDiscount);

			/* Calculation for import amount starts*/
			BigDecimal customDutiesPercentage = ObjectUtils.isNotEmpty(store.getCustomDutiesPercentage())
					? store.getCustomDutiesPercentage() : BigDecimal.ZERO;
			BigDecimal importMinFeePercentage = ObjectUtils.isNotEmpty(store.getImportFeePercentage())
					? store.getImportFeePercentage() : BigDecimal.ZERO;
			BigDecimal importMaxFeePercentage = ObjectUtils.isNotEmpty(store.getImportMaxFeePercentage())
					? store.getImportMaxFeePercentage() : BigDecimal.ZERO;
			BigDecimal minimumDutiesAmount = ObjectUtils.isNotEmpty(store.getMinimumDutiesAmount())
					? store.getMinimumDutiesAmount() : BigDecimal.ZERO;

			BigDecimal customDutiesAmount = BigDecimal.ZERO;
			BigDecimal importFeePercentage = importMinFeePercentage;
			if (baseGrandTotal.compareTo(minimumDutiesAmount) > 0) {
				customDutiesAmount = baseGrandTotal
						.multiply(customDutiesPercentage)
						.divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
				importFeePercentage = importMaxFeePercentage;
			}
			BigDecimal estimatedTotalWithCustoms = baseGrandTotal.add(customDutiesAmount);
			BigDecimal importFeesAmount = estimatedTotalWithCustoms
					.multiply(importFeePercentage)
					.divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);

			baseGrandTotal = baseGrandTotal.add(importFeesAmount).add(customDutiesAmount);
			/* Calculation for import amount ends*/

			if (appliedStoreCredit != null) {

				int result = baseGrandTotal.compareTo(appliedStoreCredit);
				if (result <= 0) {
					appliedStoreCredit = baseGrandTotal;
					paymentMethod = "free";
					quote.setStoreCreditApplied(parseNullStr(appliedStoreCredit));
				}
				baseGrandTotal = baseGrandTotal.subtract(appliedStoreCredit);
			}

			quote.setBaseGrandTotal(parseNullStr(baseGrandTotal));
			quote.setCodCharges(parseNullStr(codCharges));
			quote.setCouponCodeApplied(parseNullStr(order.getCouponCode()));
			quote.setCouponDiscount(parseNullStr(totalCouponDiscount));
			quote.setCurrency(parseNullStr(order.getOrderCurrencyCode()));
			quote.setCustomerEmail(parseNullStr(order.getCustomerEmail()));
			quote.setCustomerId(parseNullStr(order.getCustomerId()));
			quote.setCustomerIsGuest(parseNullStr(order.getCustomerIsGuest()));

			quote.setGrandTotal(parseNullStr(order.getGrandTotal()));
			quote.setImportFeesAmount(parseNullStr(importFeesAmount.add(customDutiesAmount)));

			quote.setShippingAmount(parseNullStr(shipmentCharges));
			quote.setShippingInclTax(parseNullStr(shipmentCharges));
			quote.setStoreId(parseNullStr(order.getStoreId()));
			quote.setSubtotal(parseNullStr(subtotal));
			quote.setSubtotalInclTax(parseNullStr(subtotalInclTax));
			quote.setSubtotalExclTax(parseNullStr(subtotal));
			quote.setSubtotalWithDiscount(parseNullStr(subtotalWithDiscount));
			quote.setTaxAmount(parseNullStr(taxAmount));
			quote.setTotal(parseNullStr(subtotalInclTax));

			quote.setItemsCount(parseNullStr("1"));
			quote.setItemsQty(parseNullStr(totalQtyOrdered.intValue()));

			order.getSalesOrderAddress()
					.stream()
					.filter(e -> e.getAddressType().equals(SHIPPING)).findAny().ifPresent(salesOrderAddress -> {
						AddressObject addressObject = new AddressObject();
						addressObject.setAddressType(SHIPPING);
						addressObject.setArea(parseNullStr(salesOrderAddress.getArea()));
						addressObject.setBuildingNumber(salesOrderAddress.getBuildingNumber() != null ? salesOrderAddress.getBuildingNumber() : parseNullStr(salesOrderAddress.getStreet()));
						addressObject.setCity(parseNullStr(salesOrderAddress.getCity()));
						addressObject.setCountryId(parseNullStr(salesOrderAddress.getCountryId()));
						addressObject.setCustomerAddressId(parseNullStr(salesOrderAddress.getCustomerAddressId()));
						addressObject.setFirstname(parseNullStr(salesOrderAddress.getFirstname()));
						addressObject.setLastname(parseNullStr(salesOrderAddress.getLastname()));
						addressObject.setEmail(parseNullStr(salesOrderAddress.getEmail()));
						addressObject.setMobileNumber(parseNullStr(salesOrderAddress.getTelephone()));
						addressObject.setNearestLandmark(parseNullStr(salesOrderAddress.getNearestLandmark()));
                        addressObject.setUnitNumber(parseNullStr(salesOrderAddress.getUnitNumber()));
                        addressObject.setPostalCode(parseNullStr(salesOrderAddress.getPostalCode()));
                        addressObject.setShortAddress(parseNullStr(salesOrderAddress.getShortAddress()));
                        addressObject.setKsaAddressComplaint(salesOrderAddress.getKsaAddressComplaint());
						addressObject.setPostcode(parseNullStr(salesOrderAddress.getPostcode()));
						addressObject.setRegion(parseNullStr(salesOrderAddress.getRegion()));
						addressObject.setRegionId(parseNullStr(salesOrderAddress.getRegionId()));
						addressObject.setStreet(parseNullStr(salesOrderAddress.getStreet()));
						addressObject.setTelephone(parseNullStr(salesOrderAddress.getTelephone()));
						quote.setShippingAddress(addressObject);
					});

			if (Objects.nonNull(request.getIsSubmit()) && !request.getIsSubmit().booleanValue()) {
				resp.setStatus(true);
				resp.setStatusCode("200");
				resp.setStatusMsg("Successfully fetched order recreate details!");
				RecreateOrderResponse recreateOrderResponse = new RecreateOrderResponse();

				OrderTotal totals = new OrderTotal();
				totals.setSubtotal(parseNullStr(quote.getSubtotal()));
				totals.setSubtotalInclTax(parseNullStr(quote.getSubtotalInclTax()));
				totals.setCurrency(parseNullStr(quote.getCurrency()));
				totals.setGrandTotal(parseNullStr(quote.getBaseGrandTotal()));
				totals.setCodCharges(parseNullStr(quote.getCodCharges()));
				totals.setShippingAmount(parseNullStr(quote.getShippingAmount()));
				totals.setCouponCode(parseNullStr(quote.getCouponCodeApplied()));
				totals.setImportFeesAmount(parseNullStr(quote.getImportFeesAmount()));
				totals.setTaxAmount(parseNullStr(quote.getTaxAmount()));
				totals.setCouponDiscountAmount(parseNullStr(quote.getCouponDiscount()));
				totals.setDiscountData(quote.getDiscountData());
				totals.setStoreCreditAmount(parseNullStr(quote.getStoreCreditApplied()));
				recreateOrderResponse.setTotals(totals);

				recreateOrderResponse.setProducts(responseProducts);
				resp.setResponse(recreateOrderResponse);
				return resp;
			}

			ProductStatusRequest productStatusReq = new ProductStatusRequest();
			productStatusReq.setStoreId(order.getStoreId());
			productStatusReq.setSkus(skus);
			ProductInventoryRes invResponse = salesOrderServiceV2Impl.getInventoryQty(productStatusReq);
			if (ObjectUtils.isEmpty(invResponse) || ObjectUtils.isEmpty(invResponse.getResponse())) {
				resp.setStatus(false);
				resp.setStatusCode("210");
				resp.setStatusMsg("ERROR!!");
				ErrorType error = new ErrorType();
				error.setErrorCode("210");
				error.setErrorMessage("Error: Inventory data not received! for recreate order: " + order.getIncrementId());
				resp.setError(error);
				return resp;
			}

			Map<String, Integer> skuInventoryMap = invResponse.getResponse().stream()
					.collect(Collectors.toMap(ProductValue::getSku, e -> (int) Double.parseDouble(e.getValue())));

			LOGGER.info("inventory for requested products" + skuInventoryMap);

			for (CatalogProductEntityForQuoteDTO quoteProduct : quote.getProducts()) {
				if (Integer.parseInt(quoteProduct.getQuantity()) > skuInventoryMap.get(quoteProduct.getSku())) {
					resp.setStatus(false);
					resp.setStatusCode("203");
					resp.setStatusMsg("Error: Some items have insufficient inventory!");
					ErrorType error = new ErrorType();
					error.setErrorCode("203");
					error.setErrorMessage("Some items have insufficient inventory!");
					resp.setError(error);
					return resp;
				}
			}


			SalesOrder newOrder;
			try {
				LOGGER.info("insertion started");
				newOrder = orderHelperV2.createOrderObjectToPersist(quote, paymentMethod, store, incrementId, null,
						source, null, "Admin", "Admin", null, null, false, false);
				LOGGER.info("sales_order  table insertion done!");

				orderHelperV2.setEstmateDate(quote, newOrder);
				LOGGER.info("sales_order estimated delivery date insertion done!");

				orderHelperV2.createOrderAddresses(quote, newOrder);
				LOGGER.info("sales_order_address table insertion done!");

				orderHelperV2.createOrderPayment(quote, newOrder, paymentMethod, store);
				LOGGER.info("sales_order_payment table insertion done!");

				orderHelperV2.createOrderItems(quote, newOrder, store, false,invResponse);
				LOGGER.info("sales_order child table insertion done!");

				//LOGGER.info("sales_order_product_details table insertion done!");

				orderHelperV2.createOrderGrid(quote, newOrder, paymentMethod, quote.getShippingAddress(), store, source,
						"appVersion");
				LOGGER.info("sales_order_grid table insertion done!");

				String commentMessage = "Order Recreated from existing order: " + order.getIncrementId();
				orderHelper.createOrderStatusHistory(newOrder, commentMessage);
				LOGGER.info("order_status_history table insertion done!");

				orderHelper.blockInventory(newOrder);


				customerService.deductStoreCreditV2(quote, newOrder, store, amastyBaseStoreBalance);
				LOGGER.info("deduct store credit done!");

				orderHelper.updateStatusHistory(newOrder, true, false, false, false, false);
				LOGGER.info("update status  done!");


				if (null != paymentMethod && (paymentMethod.equals(PaymentCodeENUM.CASH_ON_DELIVERY.getValue())
						|| paymentMethod.equals(PaymentCodeENUM.FREE.getValue()))) {

					OrderSms ordersms = new OrderSms();
					ordersms.setOrderid(newOrder.getEntityId().toString());

					paymentDtfHelper.publishToKafka(ordersms);

				}


				resp.setStatus(true);
				resp.setStatusCode("200");
				resp.setStatusMsg("Successfully re-created order!");
				RecreateOrderResponse recreateOrderResponse = new RecreateOrderResponse();
				recreateOrderResponse.setOrderId(newOrder.getEntityId());
				resp.setResponse(recreateOrderResponse);
				return resp;

			} catch (Exception e) {
				LOGGER.error("Error crateOrder: " + e);
				LOGGER.error("for quote: " + order.getQuoteId());
				resp.setStatus(false);
				resp.setStatusCode("204");
				resp.setStatusMsg("Error: " + e.getMessage());
				return resp;
			}

		} else {
			resp.setStatus(false);
			resp.setStatusCode("203");
			resp.setStatusMsg("Order not canceled/closed!");
			return resp;
		}


	}


	@Override
	public RecreateOrderResponseDTO recreateOrderForSplitOrder(Map<String, String> httpRequestHeaders,
												  RecreateOrder request,
												  String incrementId,
												  SplitSalesOrder order) {

		RecreateOrderResponseDTO resp = new RecreateOrderResponseDTO();

		SplitSubSalesOrder subSalesOrder = order.getSplitSubSalesOrder();
		if (Objects.nonNull(request.getIsSubmit()) && request.getIsSubmit().booleanValue() && incrementId == null) {
			resp.setStatus(false);
			resp.setStatusCode("201");
			resp.setStatusMsg("Increment ID not found!");
			return resp;
		}

		List<Stores> stores = Constants.getStoresList();
		Stores store = stores.stream()
				.filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId()))
				.findAny()
				.orElse(null);

		if (store == null || store.getStoreCode() == null || store.getStoreCurrency() == null) {
			resp.setStatus(false);
			resp.setStatusCode("202");
			resp.setStatusMsg("Store not found!");
			return resp;
		} 

		QuoteDTO quote = new QuoteDTO();


		BigDecimal amastyBaseStoreBalance = new BigDecimal(0);

		BigDecimal appliedStoreCredit = request.getStoreCreditApplied();
		if (request.getStoreCreditApplied() != null && appliedStoreCredit.compareTo(BigDecimal.ZERO) > 0) {

			List<AmastyStoreCredit> amastyStoreCredits = amastyStoreCreditRepository
					.findByCustomerId(order.getCustomerId());
			AmastyStoreCredit amastyStoreCredit = !amastyStoreCredits.isEmpty() ? amastyStoreCredits.get(0) : null;
			if (amastyStoreCredit != null && amastyStoreCredit.getStoreCredit() != null) {
				LOGGER.info("conversion rate:" + store.getCurrencyConversionRate());
				amastyBaseStoreBalance = amastyStoreCredit.getStoreCredit();
				BigDecimal convertedStoreCredit = amastyStoreCredit.getStoreCredit().divide(store.getCurrencyConversionRate(),
						4, RoundingMode.HALF_UP);
				int result = appliedStoreCredit.compareTo(convertedStoreCredit);
				if (result > 0) {
					resp.setStatus(false);
					resp.setStatusCode("203");
					resp.setStatusMsg("Invalid Store Credit applied!");
					return resp;
				} else {
					quote.setStoreCreditApplied(parseNullStr(appliedStoreCredit));
					quote.setStoreCreditBalance(parseNullStr(amastyBaseStoreBalance));
				}
			}
		}

		String paymentMethod = request.getPaymentMethod().getValue();
		int source = 3;

		if (order.getStatus().equalsIgnoreCase(OrderConstants.CANCELED_ORDER_STATE)
				|| order.getStatus().equalsIgnoreCase(OrderConstants.CLOSED_ORDER_STATUS)) {


			/**
			 * Skipped fields
			 * calcSource
			 * discount
			 * estimatedTotal
			 * flashSale
			 * isCouponApplicable
			 * rowTotalWithDiscount
			 * selectedAddressId
			 * selectedPaymentMethod
			 * defaultAddress
			 * shippingDescription
			 * shippingFreeCeiling
			 * shippingMethod
			 * shippingThreshold
			 * shippingWaived
			 */

			List<CatalogProductEntityForQuoteDTO> products = new ArrayList<>();
			List<OmsProduct> responseProducts = new ArrayList<>();
			List<SplitSalesOrderItem> SalesOrderItems = order.getSplitSalesOrderItems().stream().filter(
							e -> e.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
					.collect(Collectors.toList());

			BigDecimal subtotal = BigDecimal.ZERO;
			BigDecimal taxAmount = BigDecimal.ZERO;
			BigDecimal totalQtyOrdered = BigDecimal.ZERO;
			BigDecimal subtotalInclTax = BigDecimal.ZERO;
			BigDecimal discountTaxCompensationAmount = BigDecimal.ZERO;
			BigDecimal totalCouponDiscount = BigDecimal.ZERO;
			BigDecimal totalAutoCouponDiscount = BigDecimal.ZERO;

			List<String> skus = new ArrayList<>();
			for (Integer requestItemId : request.getRequestedItems()) {
				BigDecimal requestedQty = new BigDecimal(request.getRequestedItemsQty().get(requestItemId));
				SplitSalesOrderItem parentItem = order.getSplitSalesOrderItems()
						.stream()
						.filter(e -> Objects.equals(e.getItemId(), requestItemId))
						.findFirst()
						.orElse(null);
				SplitSalesOrderItem item = order.getSplitSalesOrderItems()
						.stream()
						.filter(e -> e.getProductType().equals("simple"))
						.filter(e -> Objects.equals(e.getSplitSalesOrderItem().getItemId(), requestItemId))
						.findFirst()
						.orElse(null);
				if (ObjectUtils.isNotEmpty(parentItem) && ObjectUtils.isNotEmpty(item)) {

					/**
					 * brandName
					 * discount
					 * isMulin
					 * priceInclTax
					 * productStatus
					 * quantityStock
					 * sizes
					 * superAttributeId
					 * superAttributeLabel
					 * superAttributeValue
					 */

					CatalogProductEntityForQuoteDTO product = new CatalogProductEntityForQuoteDTO();
					OmsProduct omsProduct = new OmsProduct();
					omsProduct.setParentOrderItemId(requestItemId);

					List<AppliedCouponValue> appliedCouponValues = new ArrayList<>();
					for (SplitSubSalesOrderItem subSalesOrderItem : parentItem.getSplitSubSalesOrderItem()) {
						AppliedCouponValue appliedCouponValue = new AppliedCouponValue();
						appliedCouponValue.setCoupon(subSalesOrderItem.getCouponName());
						appliedCouponValue.setDiscount(getQuantitySpecificValue(subSalesOrderItem.getDiscount(), parentItem.getQtyOrdered(), requestedQty));
						appliedCouponValue.setType(subSalesOrderItem.getCouponType());
						appliedCouponValues.add(appliedCouponValue);

						if (subSalesOrderItem.getCouponType().equalsIgnoreCase("MANUAL")) {
							BigDecimal currCouponDiscount = getQuantitySpecificValue(subSalesOrderItem.getDiscount(), parentItem.getQtyOrdered(), requestedQty);
							totalCouponDiscount = totalCouponDiscount.add(currCouponDiscount);
						} else if (subSalesOrderItem.getCouponType().equalsIgnoreCase("AUTO")) {
							BigDecimal currAutoCouponDiscount = getQuantitySpecificValue(subSalesOrderItem.getDiscount(), parentItem.getQtyOrdered(), requestedQty);
							totalAutoCouponDiscount = totalAutoCouponDiscount.add(currAutoCouponDiscount);
						}
					}
					product.setAppliedCouponValue(appliedCouponValues);

					BigDecimal itemDiscountAmount = getQuantitySpecificValue(item.getDiscountAmount(), parentItem.getQtyOrdered(), requestedQty);
					product.setDiscountAmount(parseNullStr(itemDiscountAmount));
					omsProduct.setDiscount(parseNullStr(itemDiscountAmount));
					product.setDiscountPercent(parseNullStr(item.getDiscountPercent()));
					if (item.getDiscountTaxCompensationAmount() == null) {
						product.setDiscountTaxCompensationAmount(parseNullStr(BigDecimal.ZERO));
					} else {
						product.setDiscountTaxCompensationAmount(parseNullStr(getQuantitySpecificValue(item.getDiscountTaxCompensationAmount(), parentItem.getQtyOrdered(), requestedQty)));
					}

					product.setName(parseNullStr(parentItem.getName()));
					product.setParentProductId(parseNullStr(parentItem.getProductId()));
					product.setParentSku(parseNullStr(parentItem.getSku()));
					product.setPrice(parseNullStr(parentItem.getPrice()));
					PriceDetails priceDetails = new PriceDetails();
					priceDetails.setPrice(parseNullStr(parentItem.getOriginalPrice()));
					priceDetails.setSpecialPrice(parseNullStr(parentItem.getPriceInclTax()));
					product.setPrices(priceDetails);
					product.setProductId(parseNullStr(item.getProductId()));
					product.setQuantity(parseNullStr(requestedQty));
					product.setRowTotal(parseNullStr(getQuantitySpecificValue(parentItem.getRowTotal(), parentItem.getQtyOrdered(), requestedQty)));

					BigDecimal itemRTIT = getQuantitySpecificValue(parentItem.getRowTotalInclTax(), parentItem.getQtyOrdered(), requestedQty);
					product.setRowTotalInclTax(parseNullStr(itemRTIT));
					omsProduct.setRowTotalInclTax(parseNullStr(itemRTIT));

					product.setSku(parseNullStr(item.getSku()));
					skus.add(item.getSku());
					product.setParentSku(parseNullStr(parentItem.getSku()));
					BigDecimal itemTaxAmount = BigDecimal.ZERO;
					List<OmsProductTax> productTaxObj = null;
					if ("IN".equalsIgnoreCase(regionValue)) {
						TaxObject taxObject = new TaxObject();
						taxObject.setIntraState(false);
						productTaxObj = omsorderentityConverter.getTaxObjects(parentItem, true);
						for (OmsProductTax taxObj : productTaxObj) {
							BigDecimal productTax = new BigDecimal(taxObj.getTaxAmount()).multiply(requestedQty).setScale(4, RoundingMode.HALF_UP);
							if (taxObj.getTaxType().equals("IGST")) {
								if (new BigDecimal(taxObj.getTaxPercentage()).compareTo(BigDecimal.ZERO) == 0) {
									taxObject.setIntraState(true);
								}
								taxObject.setTaxIGST(taxObj.getTaxPercentage());
								taxObject.setTaxIGSTAmount(parseNullStr(productTax));
							} else if (taxObj.getTaxType().equals("CGST")) {
								taxObject.setTaxCGST(taxObj.getTaxPercentage());
								taxObject.setTaxCGSTAmount(parseNullStr(productTax));
							} else if (taxObj.getTaxType().equals("SGST")) {
								taxObject.setTaxSGST(taxObj.getTaxPercentage());
								taxObject.setTaxSGSTAmount(parseNullStr(productTax));
							}
							itemTaxAmount = itemTaxAmount.add(productTax);
						}
						product.setTaxObj(taxObject);
						omsProduct.setTaxObjects(productTaxObj);
						product.setHsnCode(parseNullStr(item.getHsnCode()));
					} else {
						itemTaxAmount = getQuantitySpecificValue(parentItem.getTaxAmount(),
								parentItem.getQtyOrdered(), requestedQty);
					}


					product.setTaxAmount(parseNullStr(itemTaxAmount));
					omsProduct.setTaxAmount(parseNullStr(itemTaxAmount));

					product.setTaxPercent(parseNullStr(parentItem.getTaxPercent()));

					for (SplitSalesOrderItem orderItem : SalesOrderItems) {
						if (orderItem != null && orderItem.getSku().equals(product.getProductId())) {
							if (orderItem.getReturnable() != null && orderItem.getReturnable() == 1) {
								product.setReturnable(true);
								LOGGER.info("Returnable Set in recreate : Returnable set true for " + orderItem.getProductId());
							}
						}
					}

					products.add(product);
					responseProducts.add(omsProduct);

					taxAmount = taxAmount.add(itemTaxAmount);
					totalQtyOrdered = totalQtyOrdered.add(requestedQty);
					subtotalInclTax = subtotalInclTax.add((getQuantitySpecificValue(item.getRowTotalInclTax(),
							parentItem.getQtyOrdered(), requestedQty)));
					discountTaxCompensationAmount = discountTaxCompensationAmount.add(getQuantitySpecificValue(
							item.getDiscountTaxCompensationAmount(), parentItem.getQtyOrdered(), requestedQty));
					subtotal = subtotal.add(
							getQuantitySpecificValue(item.getRowTotal(), parentItem.getQtyOrdered(), requestedQty));
					subtotal = subtotal.add(getQuantitySpecificValue(item.getDiscountTaxCompensationAmount(),
							parentItem.getQtyOrdered(), requestedQty));
				}
			}

			quote.setProducts(products);

			if (subSalesOrder != null) {
				quote.setQuoteId(parseNullStr(subSalesOrder.getExternalQuoteId()));
				quote.setAutoCouponApplied(subSalesOrder.getExternalAutoCouponCode());
				quote.setAutoCouponDiscount(parseNullStr(totalAutoCouponDiscount));
				if (subSalesOrder.getWhiteListedCustomer() != null) {
					quote.setIsWhitelistedCustomer(subSalesOrder.getWhiteListedCustomer() == 1);
				} else quote.setIsWhitelistedCustomer(false);
				if (subSalesOrder.getDiscountData() != null) {
					try {
						DiscountData[] discountData = mapper.readValue(subSalesOrder.getDiscountData(), DiscountData[].class);

						DiscountData manual = Arrays.stream(discountData)
								.filter(e -> e.getRedeemType().equalsIgnoreCase("MANUAL"))
								.findAny().orElse(null);
						if (manual != null) manual.setValue(parseNullStr(totalCouponDiscount));

						DiscountData auto = Arrays.stream(discountData)
								.filter(e -> e.getRedeemType().equalsIgnoreCase("AUTO"))
								.findAny().orElse(null);
						if (auto != null) auto.setValue(parseNullStr(totalAutoCouponDiscount));


						quote.setDiscountData(Arrays.asList(discountData));
					} catch (JsonProcessingException e) {
						LOGGER.error("Error getting discountData from subSalesOrder" + e.getMessage());
					}
				}
			}


			BigDecimal totalPromoDiscount = totalCouponDiscount.add(totalAutoCouponDiscount);
			BigDecimal subtotalWithDiscount = subtotalInclTax.subtract(totalPromoDiscount);

			BigDecimal codCharges = order.getCashOnDeliveryFee();
			BigDecimal shipmentCharges = order.getShippingAmount();

			BigDecimal baseGrandTotal = subtotalInclTax;
			baseGrandTotal = baseGrandTotal.add(shipmentCharges);
			baseGrandTotal = baseGrandTotal.add(codCharges);
			baseGrandTotal = baseGrandTotal.subtract(totalPromoDiscount);

			/* Calculation for import amount starts*/
			BigDecimal customDutiesPercentage = ObjectUtils.isNotEmpty(store.getCustomDutiesPercentage())
					? store.getCustomDutiesPercentage() : BigDecimal.ZERO;
			BigDecimal importMinFeePercentage = ObjectUtils.isNotEmpty(store.getImportFeePercentage())
					? store.getImportFeePercentage() : BigDecimal.ZERO;
			BigDecimal importMaxFeePercentage = ObjectUtils.isNotEmpty(store.getImportMaxFeePercentage())
					? store.getImportMaxFeePercentage() : BigDecimal.ZERO;
			BigDecimal minimumDutiesAmount = ObjectUtils.isNotEmpty(store.getMinimumDutiesAmount())
					? store.getMinimumDutiesAmount() : BigDecimal.ZERO;

			BigDecimal customDutiesAmount = BigDecimal.ZERO;
			BigDecimal importFeePercentage = importMinFeePercentage;
			if (baseGrandTotal.compareTo(minimumDutiesAmount) > 0) {
				customDutiesAmount = baseGrandTotal
						.multiply(customDutiesPercentage)
						.divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
				importFeePercentage = importMaxFeePercentage;
			}
			BigDecimal estimatedTotalWithCustoms = baseGrandTotal.add(customDutiesAmount);
			BigDecimal importFeesAmount = estimatedTotalWithCustoms
					.multiply(importFeePercentage)
					.divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);

			baseGrandTotal = baseGrandTotal.add(importFeesAmount).add(customDutiesAmount);
			/* Calculation for import amount ends*/

			if (appliedStoreCredit != null) {

				int result = baseGrandTotal.compareTo(appliedStoreCredit);
				if (result <= 0) {
					appliedStoreCredit = baseGrandTotal;
					paymentMethod = "free";
					quote.setStoreCreditApplied(parseNullStr(appliedStoreCredit));
				}
				baseGrandTotal = baseGrandTotal.subtract(appliedStoreCredit);
			}

			quote.setBaseGrandTotal(parseNullStr(baseGrandTotal));
			quote.setCodCharges(parseNullStr(codCharges));
			quote.setCouponCodeApplied(parseNullStr(order.getCouponCode()));
			quote.setCouponDiscount(parseNullStr(totalCouponDiscount));
			quote.setCurrency(parseNullStr(order.getOrderCurrencyCode()));
			quote.setCustomerEmail(parseNullStr(order.getCustomerEmail()));
			quote.setCustomerId(parseNullStr(order.getCustomerId()));
			quote.setCustomerIsGuest(parseNullStr(order.getCustomerIsGuest()));

			quote.setGrandTotal(parseNullStr(order.getGrandTotal()));
			quote.setImportFeesAmount(parseNullStr(importFeesAmount.add(customDutiesAmount)));

			quote.setShippingAmount(parseNullStr(shipmentCharges));
			quote.setShippingInclTax(parseNullStr(shipmentCharges));
			quote.setStoreId(parseNullStr(order.getStoreId()));
			quote.setSubtotal(parseNullStr(subtotal));
			quote.setSubtotalInclTax(parseNullStr(subtotalInclTax));
			quote.setSubtotalExclTax(parseNullStr(subtotal));
			quote.setSubtotalWithDiscount(parseNullStr(subtotalWithDiscount));
			quote.setTaxAmount(parseNullStr(taxAmount));
			quote.setTotal(parseNullStr(subtotalInclTax));

			quote.setItemsCount(parseNullStr("1"));
			quote.setItemsQty(parseNullStr(totalQtyOrdered.intValue()));
			quote.setIsSplitOrder(1);

			order.getSalesOrder().getSalesOrderAddress()
					.stream()
					.filter(e -> e.getAddressType().equals(SHIPPING)).findAny().ifPresent(salesOrderAddress -> {
						AddressObject addressObject = new AddressObject();
						addressObject.setAddressType(SHIPPING);
						addressObject.setArea(parseNullStr(salesOrderAddress.getArea()));
						addressObject.setBuildingNumber(salesOrderAddress.getBuildingNumber() != null ? salesOrderAddress.getBuildingNumber() : parseNullStr(salesOrderAddress.getStreet()));
						addressObject.setCity(parseNullStr(salesOrderAddress.getCity()));
						addressObject.setCountryId(parseNullStr(salesOrderAddress.getCountryId()));
						addressObject.setCustomerAddressId(parseNullStr(salesOrderAddress.getCustomerAddressId()));
						addressObject.setFirstname(parseNullStr(salesOrderAddress.getFirstname()));
						addressObject.setLastname(parseNullStr(salesOrderAddress.getLastname()));
						addressObject.setEmail(parseNullStr(salesOrderAddress.getEmail()));
						addressObject.setMobileNumber(parseNullStr(salesOrderAddress.getTelephone()));
						addressObject.setNearestLandmark(parseNullStr(salesOrderAddress.getNearestLandmark()));
                        addressObject.setPostalCode(parseNullStr(salesOrderAddress.getPostalCode()));
                        addressObject.setKsaAddressComplaint(salesOrderAddress.getKsaAddressComplaint());
                        addressObject.setShortAddress(parseNullStr(salesOrderAddress.getShortAddress()));
                        addressObject.setUnitNumber(parseNullStr(salesOrderAddress.getUnitNumber()));
						addressObject.setPostcode(parseNullStr(salesOrderAddress.getPostcode()));
						addressObject.setRegion(parseNullStr(salesOrderAddress.getRegion()));
						addressObject.setRegionId(parseNullStr(salesOrderAddress.getRegionId()));
						addressObject.setStreet(parseNullStr(salesOrderAddress.getStreet()));
						addressObject.setTelephone(parseNullStr(salesOrderAddress.getTelephone()));
						quote.setShippingAddress(addressObject);
					});

			if (Objects.nonNull(request.getIsSubmit()) && !request.getIsSubmit().booleanValue()) {
				resp.setStatus(true);
				resp.setStatusCode("200");
				resp.setStatusMsg("Successfully fetched order recreate details!");
				RecreateOrderResponse recreateOrderResponse = new RecreateOrderResponse();

                OrderTotal totals = getOrderTotal(quote);
                recreateOrderResponse.setTotals(totals);

				recreateOrderResponse.setProducts(responseProducts);
				resp.setResponse(recreateOrderResponse);
				return resp;
			}

			ProductStatusRequest productStatusReq = new ProductStatusRequest();
			productStatusReq.setStoreId(order.getStoreId());
			productStatusReq.setSkus(skus);
			ProductInventoryRes invResponse = salesOrderServiceV2Impl.getInventoryQty(productStatusReq);
			if (ObjectUtils.isEmpty(invResponse) || ObjectUtils.isEmpty(invResponse.getResponse())) {
				resp.setStatus(false);
				resp.setStatusCode("210");
				resp.setStatusMsg("ERROR!!");
				ErrorType error = new ErrorType();
				error.setErrorCode("210");
				error.setErrorMessage("Error: Inventory data not received! for recreate order: " + order.getIncrementId());
				resp.setError(error);
				return resp;
			}

			Map<String, Integer> skuInventoryMap = invResponse.getResponse().stream()
					.collect(Collectors.toMap(ProductValue::getSku, e -> (int) Double.parseDouble(e.getValue())));

			LOGGER.info("inventory for requested products" + skuInventoryMap);

			for (CatalogProductEntityForQuoteDTO quoteProduct : quote.getProducts()) {
				if (Integer.parseInt(quoteProduct.getQuantity()) > skuInventoryMap.get(quoteProduct.getSku())) {
					resp.setStatus(false);
					resp.setStatusCode("203");
					resp.setStatusMsg("Error: Some items have insufficient inventory!");
					ErrorType error = new ErrorType();
					error.setErrorCode("203");
					error.setErrorMessage("Some items have insufficient inventory!");
					resp.setError(error);
					return resp;
				}
			}


			SalesOrder newOrder;
			try {
				LOGGER.info("insertion started");
				newOrder = orderHelperV2.createOrderObjectToPersist(quote, paymentMethod, store, incrementId, null,
						source, null, "Admin", "Admin", null, null, false, false);
				LOGGER.info("sales_order  table insertion done!");

				orderHelperV2.setEstmateDate(quote, newOrder);
				LOGGER.info("sales_order estimated delivery date insertion done!");

				orderHelperV2.createOrderAddresses(quote, newOrder);
				LOGGER.info("sales_order_address table insertion done!");

				orderHelperV2.createOrderPayment(quote, newOrder, paymentMethod, store);
				LOGGER.info("sales_order_payment table insertion done!");

				orderHelperV2.createOrderItems(quote, newOrder, store, false,invResponse);
				LOGGER.info("sales_order child table insertion done!");

				//LOGGER.info("sales_order_product_details table insertion done!");

				orderHelperV2.createOrderGrid(quote, newOrder, paymentMethod, quote.getShippingAddress(), store, source,
						"appVersion");
				LOGGER.info("sales_order_grid table insertion done!");

				String commentMessage = "Order Recreated from existing order: " + order.getIncrementId();
				orderHelper.createOrderStatusHistory(newOrder, commentMessage);
				LOGGER.info("order_status_history table insertion done!");

				orderHelper.blockInventory(newOrder);


				customerService.deductStoreCreditV2(quote, newOrder, store, amastyBaseStoreBalance);
				LOGGER.info("deduct store credit done!");

				orderHelper.updateStatusHistory(newOrder, true, false, false, false, false);
				LOGGER.info("update status  done!");


				if (null != paymentMethod && (paymentMethod.equals(PaymentCodeENUM.CASH_ON_DELIVERY.getValue())
						|| paymentMethod.equals(PaymentCodeENUM.FREE.getValue()))) {

					OrderSms ordersms = new OrderSms();
					ordersms.setOrderid(newOrder.getEntityId().toString());

					paymentDtfHelper.publishToKafka(ordersms);

				}

				// Create split order through pubsub
				Map<String,String> pubsubPayload = new HashMap<>();
				pubsubPayload.put("orderId", newOrder.getEntityId() != null ? newOrder.getEntityId().toString() : null);
				pubSubServiceImpl.publishSplitOrderPubSub(splitOrderTopic, pubsubPayload);

				resp.setStatus(true);
				resp.setStatusCode("200");
				resp.setStatusMsg("Successfully re-created order!");
				RecreateOrderResponse recreateOrderResponse = new RecreateOrderResponse();
				recreateOrderResponse.setOrderId(newOrder.getEntityId());
				resp.setResponse(recreateOrderResponse);
				return resp;

			} catch (Exception e) {
				LOGGER.error("Error crateOrder: " + e);
				LOGGER.error("for quote: " + order.getQuoteId());
				resp.setStatus(false);
				resp.setStatusCode("204");
				resp.setStatusMsg("Error: " + e.getMessage());
				return resp;
			}

		} else {
			resp.setStatus(false);
			resp.setStatusCode("203");
			resp.setStatusMsg("Order not canceled/closed!");
			return resp;
		}


	}

    @NotNull
    private OrderTotal getOrderTotal(QuoteDTO quote) {
        OrderTotal totals = new OrderTotal();
        totals.setSubtotal(parseNullStr(quote.getSubtotal()));
        totals.setSubtotalInclTax(parseNullStr(quote.getSubtotalInclTax()));
        totals.setCurrency(parseNullStr(quote.getCurrency()));
        totals.setGrandTotal(parseNullStr(quote.getBaseGrandTotal()));
        totals.setCodCharges(parseNullStr(quote.getCodCharges()));
        totals.setShippingAmount(parseNullStr(quote.getShippingAmount()));
        totals.setCouponCode(parseNullStr(quote.getCouponCodeApplied()));
        totals.setImportFeesAmount(parseNullStr(quote.getImportFeesAmount()));
        totals.setTaxAmount(parseNullStr(quote.getTaxAmount()));
        totals.setCouponDiscountAmount(parseNullStr(quote.getCouponDiscount()));
        totals.setDiscountData(quote.getDiscountData());
        totals.setStoreCreditAmount(parseNullStr(quote.getStoreCreditApplied()));
        return totals;
    }

    private String parseNullStr(Object val) {
		return (val == null) ? null : String.valueOf(val);
	}


	@Override
	public List<SalesOrder> orderpushTowms() {
		//SFP-323 Sales order Increff push restrict split order data
		return salesOrderRepository.ordersForWmsPushExcludingSplit(Constants.orderCredentials.getWms().getWmsOrderPushMinutes(), Constants.orderCredentials.getWms().getWmsHoldOrderPushMinutes());
	}

	@Override
	public List<SplitSalesOrder> orderpushTowmsV2() {
		return splitSalesOrderRepository.ordersforwmspush(Constants.orderCredentials.getWms().getWmsOrderPushMinutes(), Constants.orderCredentials.getWms().getWmsHoldOrderPushMinutes());
	}

    @Override
    public List<SplitSellerOrder> orderpushTowmsV3() {
        // Step 1: Use native query to get accurate time filtering (same as ordersforwmspushNative)
		// Include warehouses, check for push to wms TRUE using service
		List<String> includeWareHouseIds = sellerConfigService.getWmsEnabledWarehouseIds();
		
        List<SplitSellerOrder> splitSellerOrders = splitSellerOrderRepository.ordersforwmspushNative(
                Constants.orderCredentials.getWms().getWmsOrderPushMinutes(),
                Constants.orderCredentials.getWms().getWmsHoldOrderPushMinutes(),
				includeWareHouseIds
        );
        return splitSellerOrders;
    }

	@Override
	public List<SalesOrder> orderpushTowmsForApparel() {
		return salesOrderRepository.sellerWmsPushExcludingSplit(Constants.orderCredentials.getWms().getWmsOldOrderSellerSkuMinute());
	}

    @Override
    public SplitSellerOrder getSalesOrderForSellerOrder(SplitSellerOrder splitSellerOrder) {
        Integer salesOrderId = splitSellerOrderRepository.getSalesOrderIdByEntityId(splitSellerOrder.getEntityId());
        if (salesOrderId != null) {
            SalesOrder salesOrder = salesOrderRepository.findByEntityId(salesOrderId);
            if (salesOrder != null) {
                splitSellerOrder.setSalesOrder(salesOrder);
            }
        }
        return splitSellerOrder;
    }


	@Override
	public Map<Integer,List<WarehouseItem>> orderWmsCancel() {
		List<String> statusList = OrderConstants.CANCEL_WMS_ORDER_PUSH_STATUS_LIST;
		List<Object[]> results= salesOrderRepository.cancelledOrderforwmspushNew(statusList, Constants.orderCredentials.getWms().getWmsOrderCancelPushMinutes());
		return results.stream()
				.collect(Collectors.groupingBy(
						r -> Math.toIntExact(((Number) r[0]).longValue()),
						Collectors.mapping(
								r -> new WarehouseItem(
										(r[1]).toString(),
										((Number) r[2]).intValue(),
										((Number) r[3]).intValue(),
										((Number) r[4]).intValue(),
										(r[5]).toString(),
										BigDecimal.valueOf(((Number) r[6]).longValue()),
										(r[7]).toString()

								),
								Collectors.toList()
						)
				));
	}

	@Override
	public List<SplitSalesOrder> orderWmsCancelForSplitOrder() {
		List<String> statusList = OrderConstants.CANCEL_WMS_ORDER_PUSH_STATUS_LIST;
		return splitSalesOrderRepository.cancelledOrderforwmspush(statusList, Constants.orderCredentials.getWms().getWmsOrderCancelPushMinutes());
	}

	@Override
	public List<SplitSellerOrder> orderWmsCancelForSellerOrder() {
		List<String> statusList = OrderConstants.CANCEL_WMS_ORDER_PUSH_STATUS_LIST;
		List<String> excludeWareHouseIds = Constants.orderCredentials.getInventoryMapping().stream().map(InventoryMapping::getWareHouseId).collect(Collectors.toList());
		return splitSellerOrderRepository.cancelledOrderforwmspush(statusList, Constants.orderCredentials.getWms().getWmsOrderCancelPushMinutes(), excludeWareHouseIds);
	}

	@Override
@Transactional
public OmsUnfulfilmentResponse updateUnfulfilmentOrder(@Valid OrderunfulfilmentRequest request
        , Map<String, String> httpRequestHeadrs) {
    try {
        OmsUnfulfilmentResponse response = new OmsUnfulfilmentResponse();
        boolean isSplitOrder = false;
        if (request.getOrderCode().toUpperCase().contains("G")) {
            isSplitOrder = true;
        }
        if (request.getOrderCode().toUpperCase().contains("L") || request.getOrderCode().toUpperCase().contains("E")) {
            isSplitOrder = true;
        }
        // If order is split order, then use split shipment helper
        if (isSplitOrder) {
            SplitSalesOrder splitSalesOrder = splitSalesOrderRepository.findByIncrementIdAndStatus(request.getOrderCode(), OrderConstants.PROCESSING_ORDER_STATUS);
            return splitOrderpushHelper.cancelUnfulfilledSplitOrder(splitSalesOrder, request, httpRequestHeadrs);
        }
        // If order is not a split order, then use regular shipment helper
        else {
            SalesOrder order = salesOrderRepository.findByIncrementIdAndStatus(request.getOrderCode(), OrderConstants.PROCESSING_ORDER_STATUS);
            if (null == order) {
                response.setErrorMessage("Invalid order request to proceed ");
                response.setHasError(true);
                return response;
            }
            // If order is split order and club shipment is enabled, then use split shipment helper
            if (Objects.equals(order.getIsSplitOrder(), 1) && Objects.equals(order.getIsClubShipment(), 1)) {
                SplitSalesOrder splitSalesOrder = splitSalesOrderRepository.findByIncrementIdAndStatus(request.getOrderCode(), OrderConstants.PROCESSING_ORDER_STATUS);
                return splitOrderpushHelper.cancelUnfulfilledSplitOrder(splitSalesOrder, request, httpRequestHeadrs);
            }
            //If order is not split order, then use regular shipment helper
            return orderpushHelper.cancelUnfulfiorder(order, request, httpRequestHeadrs);
        }
    } catch (Exception e) {
        LOGGER.info("error in update update unfulfilled SplitOrder " + e.getMessage());
        throw new RuntimeException(e);
    }
}
	/**
	 *
	 */


	@Override
	@Transactional
	public ResponseEntity<?> dtfCall(Map<String, String> httpRequestHeadrs, Map<String, String> requestObject) {


		LOGGER.info("inside dtfCall");

		PayforDtfRequest request = null;
		SalesOrder order = null;

		LOGGER.info("dtf request body:" + requestObject);
		try {

			if (MapUtils.isNotEmpty(requestObject) && CollectionUtils.isNotEmpty(requestObject.keySet())) {


				LOGGER.info("requestObject:" + requestObject);

				request = mapper.convertValue(requestObject, PayforDtfRequest.class);

				if (null != request && request.getMerchantReference() == null) {

					String key = StringUtils.strip(requestObject.keySet().stream().findFirst().orElse(null));

					request = mapper.convertValue(key, PayforDtfRequest.class);
					LOGGER.info("mapper request:" + mapper.writeValueAsString(request));
				}

			} else if (MapUtils.isNotEmpty(requestObject)) {

				LOGGER.info("direct string");

				request = mapper.convertValue(requestObject, PayforDtfRequest.class);
				LOGGER.info("mapper request:" + mapper.writeValueAsString(request));


			} else {

				LOGGER.error("Bad Request!!");
				return ResponseEntity.badRequest().build();

			}


		} catch (JsonMappingException e) {
			LOGGER.error("JsonMappingException  jsonparcer:" + e);
		} catch (JsonProcessingException e) {
			LOGGER.error("JsonProcessingException  jsonparcer:" + e);
		}


		if (null != request) {
			LOGGER.info("reference :" + request.getMerchantReference());
			order = salesOrderRepository.findByIncrementId(request.getMerchantReference());
			LOGGER.info("not null");

			String digitalWallet = request.getDigitalWallet();
			String applePay = PaymentCodeENUM.APPLE_PAY.getValue();
			if (Objects.isNull(order) && applePay.equalsIgnoreCase(digitalWallet)) {
				order = salesOrderRepository.findByEditIncrement(request.getMerchantReference());
			}
		}
		if (null != request.getMerchantReference() && request.getMerchantReference().contains("R")) {

			LOGGER.info("DTF requet is for second return payment :" + request.getMerchantReference());
			paymentDtfHelper.updateStatusForSecondReturn(request);

			return ResponseEntity.accepted().build();

		} else if (null == order) {
			LOGGER.info("order is  null.");
			return ResponseEntity.badRequest().build();
		}

		ResponseEntity<?> response = paymentDtfHelper.payfortDtfcall(order, request, httpRequestHeadrs);
		// After successful DTF transaction, publish to split pubsub for further processing
		paymentUtility.publishToSplitPubSub(order.getEntityId());
		paymentUtility.publishToSplitPubSubOTSForSalesOrder(order,null,null);
		return response;
	}

	@Override
	@Transactional
	public RefundPaymentRespone payfortRefundCall(Map<String, String> httpRequestHeadrs, @Valid payFortRefund request) {

		AmastyRmaRequest rmaRequest = amastyRmaRequestRepository.findByRmaIncIdAndOrderId(request.getReturnIncrementId(), request.getOrderId());


		SalesOrder order = salesOrderRepository.findByEntityId(rmaRequest.getOrderId());
		BigDecimal taxFactor = BigDecimal.valueOf(1);
		Stores store = null;
		if (order.getStoreId() != null) {
			store = Constants.getStoresList().stream().filter(e -> e != null &&
					org.apache.commons.lang.StringUtils.isNotEmpty(e.getStoreId()) &&
					e.getStoreId().equalsIgnoreCase(order.getStoreId().toString())).findFirst().orElse(null);


			if (store != null && store.getTaxPercentage() != null && store.getTaxPercentage().compareTo(BigDecimal.ZERO) > 0) {
				BigDecimal decimalTaxValue = store.getTaxPercentage().divide(new BigDecimal(100), 4, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
				taxFactor = taxFactor.add(decimalTaxValue);
			}
		}
		RefundAmountObject refundAmountDetails = null;
		BigDecimal totalRefundOnlineAmount;
		BigDecimal totalRefundGiftVoucherAmount = BigDecimal.ZERO;
		String msgString = null;


		RefundPaymentRespone response = new RefundPaymentRespone();


		if (null != rmaRequest.getRequestId()) {


			String fortId = null;
			refundAmountDetails = new RefundAmountObject();

List<SalesCreditmemo> creditMemoList = salesCreditmemoRepository.findByRmaNumber(rmaRequest.getRequestId().toString());

			if (CollectionUtils.isNotEmpty(creditMemoList)) {

				response.setStatus(true);
				response.setStatusCode("200");
				response.setRequestId(rmaRequest.getRmaIncId());
				response.setStatusMsg("Refunded already done !!");
				response.setSendSms(false);
				return response;
			}


			if (null != order) {

				SalesOrderPayment orderPayment = order.getSalesOrderPayment().stream().findFirst().orElse(null);
				String paymentMethod = null;
				Map<String, BigDecimal> mapSkuList = new HashMap<>();
				if (null != orderPayment) {

					paymentMethod = orderPayment.getMethod();
					fortId = orderPayment.getCcTransId();
				}

				CalculateRefundAmountResponse calculateRefundAmountResponse = paymentDtfRefundHelper.calculaterefundamount(order,
						paymentMethod, rmaRequest, refundAmountDetails, mapSkuList, taxFactor, store, httpRequestHeadrs.get(Constants.deviceId));

				LOGGER.info("calculateRefundAmountResponse" + calculateRefundAmountResponse);
				String calcutedRefundAmount = calculateRefundAmountResponse.getAfterCalculatedRefundAmount();
				String afterCalculatedShukranValue = calculateRefundAmountResponse.getAfterCalculatedShukranValue();

				boolean checkQty = checkreturnQtyCheck(mapSkuList);
				LOGGER.info("inside payfortRefundCall :"+checkQty);
				LOGGER.info("mapList:" + mapSkuList);

				if (!checkQty) {
					response.setStatus(false);
					response.setStatusCode("209");
					response.setRequestId(rmaRequest.getRmaIncId());
					response.setStatusMsg("invalid refund qty !!");
					response.setSendSms(false);
					return response;
				} else if (isPayfortIdBlank(rmaRequest.getReturnIncPayfortId())
						&& paymentMethod != null
						&& isZero(calcutedRefundAmount)
						&& isZero(refundAmountDetails.getRefundStorecreditAmount())
						&& isZeroOrBlank(afterCalculatedShukranValue)) {

					response.setStatus(true);
					response.setStatusCode("200");
					response.setStatusMsg("Refundable amount is zero");
					response.setSendSms(false);
					return response;
				}

				if (null != order.getGiftVoucherDiscount()
						&& order.getGiftVoucherDiscount().compareTo(BigDecimal.ZERO) != 0) {
					refundAmountDetails.setGiftVoucher(true);
					totalRefundGiftVoucherAmount = paymentDtfRefundHelper.getGiftVoucherRefundAmount(order, rmaRequest, refundAmountDetails);
					paymentDtfRefundHelper.setReturnVoucherValueInDB(order, totalRefundGiftVoucherAmount);

				}
				BigDecimal totalAmountToShowInSMS = BigDecimal.ZERO;
				if (StringUtils.isNotEmpty(calculateRefundAmountResponse.getAfterRefundOnlineAmount()) && StringUtils.isNotBlank(calculateRefundAmountResponse.getAfterRefundOnlineAmount())) {
					totalAmountToShowInSMS = totalAmountToShowInSMS.add(new BigDecimal(calculateRefundAmountResponse.getAfterRefundOnlineAmount()));
				}
				if (StringUtils.isNotEmpty(calculateRefundAmountResponse.getAfterCreditAmount()) && StringUtils.isNotBlank(calculateRefundAmountResponse.getAfterCreditAmount())) {
					totalAmountToShowInSMS = totalAmountToShowInSMS.add(new BigDecimal(calculateRefundAmountResponse.getAfterCreditAmount()));
				}
				
				if (store != null && store.getIsShukranEnable() && order.getSubSalesOrder() != null && order.getSubSalesOrder().getShukranLinked() && StringUtils.isNotEmpty(order.getSubSalesOrder().getCustomerProfileId()) && StringUtils.isNotBlank(order.getSubSalesOrder().getCustomerProfileId()) && StringUtils.isNotBlank(order.getSubSalesOrder().getShukranCardNumber()) && StringUtils.isNotEmpty(order.getSubSalesOrder().getShukranCardNumber())) {
					try {
						String earnResponse = "Clawback Api Failed";
						if (calculateRefundAmountResponse.getOrderNetPrice() != null && calculateRefundAmountResponse.getOrderNetPrice().compareTo(BigDecimal.ZERO) > 0 && !rmaRequest.getShukranRtSuccessful() && order.getSubSalesOrder().getShukranPrSuccessful() != null && order.getSubSalesOrder().getShukranPrSuccessful().equals(1) && order.getSubSalesOrder().getShukranPrTransactionNetTotal() != null && order.getSubSalesOrder().getShukranPrTransactionNetTotal().compareTo(BigDecimal.ZERO) > 0) {
							LOGGER.info(" Inside clawback call");
							if (paymentMethod != null) {
								ShukranClawbackRequestBody shukranClawbackRequestBody = createShukranClawbackBody(order, rmaRequest, calculateRefundAmountResponse.getTransactionDetails(), calculateRefundAmountResponse.getOrderNetPrice(), calculateRefundAmountResponse.getTotalQty(), taxFactor);
								earnResponse = commonService.clawbackShukranEarned(shukranClawbackRequestBody);
								LOGGER.info("earn response" + earnResponse);
							} else {
								earnResponse = "clawback api response passed";
							}
						}
						rmaRequest.setShukranRtSuccessful(StringUtils.isNotEmpty(earnResponse) && StringUtils.isNotBlank(earnResponse) && earnResponse.equals("clawback api response passed"));
					} catch (Exception e) {
						LOGGER.info("Error While Clawback Data " + e.getMessage());
					}
					try {
						if (order.getSubSalesOrder().getTotalShukranCoinsBurned() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0 && calculateRefundAmountResponse.getAfterCalculatedShukranPoints() > 0 && !rmaRequest.getShukranRefundSuccessful() && order.getSubSalesOrder().getShukranLocked() != null && order.getSubSalesOrder().getShukranLocked().equals(0)) {
							RefundShukranBurnedBody refundShukranBurnedBody = createRefundShukranBody(order, rmaRequest, calculateRefundAmountResponse.getAfterCalculatedShukranPoints(), store);
							String burnResponse = commonService.refundShukranBurned(refundShukranBurnedBody);
							LOGGER.info("Burn response" + burnResponse);
							if (StringUtils.isNotBlank(burnResponse) && StringUtils.isNotEmpty(burnResponse) && burnResponse.equalsIgnoreCase("refund burned point api passed")) {
								rmaRequest.setShukranRefundSuccessful(true);
								BigDecimal points = BigDecimal.valueOf(calculateRefundAmountResponse.getAfterCalculatedShukranPoints());
								BigDecimal pointsValueInCurrency = points.multiply(store.getShukranPointConversion()).setScale(2, RoundingMode.HALF_UP);
								BigDecimal pointsValueInBaseCurreny = points.multiply(BigDecimal.valueOf(0.05)).setScale(2, RoundingMode.HALF_UP);
								ShukranLedgerData shukranLedgerData = orderHelperService.createShukranLedgerData(order, points, pointsValueInCurrency, pointsValueInBaseCurreny, store, true, "Refund Burned Shukran Points");
								eASServiceImpl.updateShukranLedger(shukranLedgerData);
							}
						}
					} catch (Exception e) {
						LOGGER.info("Error While Refund Shukran Data " + e.getMessage());
					}
					amastyRmaRequestRepository.saveAndFlush(rmaRequest);
				}
				if (null != paymentMethod && OrderConstants.checkPaymentMethod(paymentMethod)) {
					LOGGER.info("refunabale payfort amount:" + calcutedRefundAmount);
					if ((new BigDecimal(calcutedRefundAmount).compareTo(BigDecimal.ZERO) == 0)) {
						response.setStatus(true);

					} else if (StringUtils.isBlank(rmaRequest.getReturnIncPayfortId()) || StringUtils.isEmpty(rmaRequest.getReturnIncPayfortId())) {
						if (StringUtils.isNotEmpty(calculateRefundAmountResponse.getAfterRefundOnlineAmount()) && StringUtils.isNotBlank(calculateRefundAmountResponse.getAfterRefundOnlineAmount()) && new BigDecimal(calculateRefundAmountResponse.getAfterRefundOnlineAmount()).compareTo(BigDecimal.ZERO) > 0) {

							response = paymentDtfRefundHelper.payfortRefundcall(order, new BigDecimal(calculateRefundAmountResponse.getAfterRefundOnlineAmount()),
									fortId, paymentMethod);
						}


					}

					try {
						LOGGER.info("payfort response:" + mapper.writeValueAsString(response));
					} catch (JsonProcessingException e) {

						LOGGER.error("payfort refund response parse error:" + e.getMessage());

					}
					if (!response.isStatus()) {

						response.setSendSms(false);
						msgString = response.getStatusMsg();

					}
					LOGGER.info("Store Credit Amount To Be Credited1: " + calculateRefundAmountResponse.getAfterCreditAmount());
					if (StringUtils.isNotBlank(calculateRefundAmountResponse.getAfterCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getAfterCreditAmount())
							&& new BigDecimal(calculateRefundAmountResponse.getAfterCreditAmount()).compareTo(BigDecimal.ZERO) > 0 && (StringUtils.isBlank(rmaRequest.getReturnIncPayfortId()) || StringUtils.isEmpty(rmaRequest.getReturnIncPayfortId()))) {
						LOGGER.info("Store Credit Amount To Be Credited2: " + calculateRefundAmountResponse.getAfterCreditAmount());
						paymentDtfRefundHelper.addStoreCredit(order, new BigDecimal(calculateRefundAmountResponse.getAfterCreditAmount()),
								refundAmountDetails.isGiftVoucher());

					}


					if (StringUtils.isNotBlank(calculateRefundAmountResponse.getBeforeAmastyCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getBeforeAmastyCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getBeforeCreditAmount()) && StringUtils.isNotBlank(calculateRefundAmountResponse.getBeforeCreditAmount())) {
						refundAmountDetails.setBaseAmastyStoreCreditAmount(new BigDecimal(calculateRefundAmountResponse.getBeforeAmastyCreditAmount()));
						refundAmountDetails.setRefundStorecreditAmount(new BigDecimal(calculateRefundAmountResponse.getBeforeCreditAmount()));
					}
					if (StringUtils.isNotBlank(calculateRefundAmountResponse.getBeforeRefundOnlineAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getBeforeRefundOnlineAmount())) {
						refundAmountDetails.setRefundOnlineAmount(new BigDecimal(calculateRefundAmountResponse.getBeforeRefundOnlineAmount()));
					}
					LOGGER.info(" calculateRefundAmountResponse.getBeforeCalculatedRefundAmount()1" + calculateRefundAmountResponse.getBeforeCalculatedRefundAmount());
					paymentDtfRefundHelper.createReturnRma(order, paymentMethod, calculateRefundAmountResponse.getBeforeRefundOnlineAmount(), rmaRequest,
							refundAmountDetails, mapSkuList, msgString, response, calculateRefundAmountResponse.getAfterRefundOnlineAmount(), calculateRefundAmountResponse.getAfterAmastyCreditAmount(), totalAmountToShowInSMS);


				} else if (null != paymentMethod && (OrderConstants.checkBNPLPaymentMethods(paymentMethod) || PaymentConstants.CASHFREE.equalsIgnoreCase(paymentMethod))) {
					LOGGER.info(" calculateRefundAmountResponse.getBeforeCalculatedRefundAmount()2" + calculateRefundAmountResponse.getBeforeCalculatedRefundAmount());
					PaymentReturnAdditioanls additionals = new PaymentReturnAdditioanls();
					additionals.setPaymentMethod(paymentMethod);
					additionals.setRmaRequest(rmaRequest);
					additionals.setReturnAmount(calculateRefundAmountResponse.getAfterRefundOnlineAmount());
					if (StringUtils.isBlank(rmaRequest.getReturnIncPayfortId()) || StringUtils.isEmpty(rmaRequest.getReturnIncPayfortId())) {
						response = paymentUtility.initiateRefund(order, additionals);
						msgString = response.getStatusMsg();
						if (StringUtils.isNotBlank(calculateRefundAmountResponse.getAfterAmastyCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getAfterAmastyCreditAmount()) && StringUtils.isNotBlank(calculateRefundAmountResponse.getAfterCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getAfterCreditAmount())) {
							refundAmountDetails.setRefundStorecreditAmount(new BigDecimal(calculateRefundAmountResponse.getAfterCreditAmount()));
							refundAmountDetails.setBaseAmastyStoreCreditAmount(new BigDecimal(calculateRefundAmountResponse.getAfterAmastyCreditAmount()));
							refundToStoreCredit(order, refundAmountDetails);
						}
					}
					refundAmountDetails.setRefundOnlineAmount(new BigDecimal(calculateRefundAmountResponse.getBeforeRefundOnlineAmount()));
					if (StringUtils.isNotBlank(calculateRefundAmountResponse.getBeforeAmastyCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getBeforeAmastyCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getBeforeCreditAmount()) && StringUtils.isNotBlank(calculateRefundAmountResponse.getBeforeCreditAmount())) {
						refundAmountDetails.setBaseAmastyStoreCreditAmount(new BigDecimal(calculateRefundAmountResponse.getBeforeAmastyCreditAmount()));
						refundAmountDetails.setRefundStorecreditAmount(new BigDecimal(calculateRefundAmountResponse.getBeforeCreditAmount()));
					}
					if (StringUtils.isNotBlank(calculateRefundAmountResponse.getBeforeRefundOnlineAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getBeforeRefundOnlineAmount())) {
						refundAmountDetails.setRefundOnlineAmount(new BigDecimal(calculateRefundAmountResponse.getBeforeRefundOnlineAmount()));
					}
					LOGGER.info(" calculateRefundAmountResponse.getBeforeCalculatedRefundAmount()2" + calculateRefundAmountResponse.getBeforeCalculatedRefundAmount());
					paymentDtfRefundHelper.createReturnRma(order, paymentMethod, calculateRefundAmountResponse.getBeforeRefundOnlineAmount(), rmaRequest
							, refundAmountDetails, mapSkuList, msgString, response, calculateRefundAmountResponse.getAfterRefundOnlineAmount(), calculateRefundAmountResponse.getAfterAmastyCreditAmount(), totalAmountToShowInSMS);
					if (null != totalRefundGiftVoucherAmount && totalRefundGiftVoucherAmount.compareTo(BigDecimal.ZERO) != 0) {
						paymentDtfRefundHelper.setReturnVoucherValueInDB(order, totalRefundGiftVoucherAmount);
					}

				} else if (paymentMethod != null && paymentMethod.equalsIgnoreCase(PaymentCodeENUM.SHUKRAN_PAYMENT.getValue())) {
					paymentDtfRefundHelper.createReturnRma(order, paymentMethod, BigDecimal.ZERO.toString(), rmaRequest
							, refundAmountDetails, mapSkuList, msgString, response, BigDecimal.ZERO.toString(), BigDecimal.ZERO.toString(), BigDecimal.ZERO);
				} else {
					if (null != totalRefundGiftVoucherAmount && totalRefundGiftVoucherAmount.compareTo(BigDecimal.ZERO) != 0) {
						paymentDtfRefundHelper.setReturnVoucherValueInDB(order, totalRefundGiftVoucherAmount);
						refundAmountDetails.setRefundGiftVoucherAmount(totalRefundGiftVoucherAmount);

					}


					if (Regions.INDIA.equals(regionValue)) {
						if (PaymentConstants.CASHFREE.equalsIgnoreCase(rmaRequest.getRmaPaymentMethod())) {
							PaymentReturnAdditioanls additionals = new PaymentReturnAdditioanls();
							additionals.setPaymentMethod(paymentMethod);
							additionals.setRmaRequest(rmaRequest);
							additionals.setReturnAmount(calcutedRefundAmount);
							response = paymentUtility.initiateRefund(order, additionals);
							msgString = response.getStatusMsg();
						} else if (PaymentConstants.FREE.equalsIgnoreCase(rmaRequest.getRmaPaymentMethod()) && (StringUtils.isBlank(rmaRequest.getReturnIncPayfortId()) || StringUtils.isEmpty(rmaRequest.getReturnIncPayfortId()))) {
							refundToStoreCredit(order, refundAmountDetails);
						}
					} else {
						if (StringUtils.isNotBlank(calculateRefundAmountResponse.getAfterAmastyCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getAfterAmastyCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getAfterCreditAmount()) && StringUtils.isNotBlank(calculateRefundAmountResponse.getAfterCreditAmount())) {
							refundAmountDetails.setBaseAmastyStoreCreditAmount(new BigDecimal(calculateRefundAmountResponse.getAfterAmastyCreditAmount()));
							refundAmountDetails.setRefundStorecreditAmount(new BigDecimal(calculateRefundAmountResponse.getAfterCreditAmount()));
						}
						LOGGER.info(refundAmountDetails);

						if (StringUtils.isBlank(rmaRequest.getReturnIncPayfortId()) || StringUtils.isEmpty(rmaRequest.getReturnIncPayfortId())) {
							refundToStoreCredit(order, refundAmountDetails);
						}
					}
					if (StringUtils.isNotBlank(calculateRefundAmountResponse.getBeforeAmastyCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getBeforeAmastyCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getBeforeCreditAmount()) && StringUtils.isNotBlank(calculateRefundAmountResponse.getBeforeCreditAmount())) {
						refundAmountDetails.setBaseAmastyStoreCreditAmount(new BigDecimal(calculateRefundAmountResponse.getBeforeAmastyCreditAmount()).setScale(2, RoundingMode.HALF_UP));
						refundAmountDetails.setRefundStorecreditAmount(new BigDecimal(calculateRefundAmountResponse.getBeforeCreditAmount()).setScale(2, RoundingMode.HALF_UP));
					}
					if (StringUtils.isNotBlank(calculateRefundAmountResponse.getBeforeRefundOnlineAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getBeforeRefundOnlineAmount())) {
						refundAmountDetails.setRefundOnlineAmount(new BigDecimal(calculateRefundAmountResponse.getBeforeRefundOnlineAmount()).setScale(2, RoundingMode.HALF_UP));
					}
					LOGGER.info(" calculateRefundAmountResponse.getBeforeCalculatedRefundAmount()3" + calculateRefundAmountResponse.getBeforeCalculatedRefundAmount());
					paymentDtfRefundHelper.createReturnRma(order, paymentMethod, calculateRefundAmountResponse.getBeforeCalculatedRefundAmount(), rmaRequest
							, refundAmountDetails, mapSkuList, msgString, response, calculateRefundAmountResponse.getAfterRefundOnlineAmount(), calculateRefundAmountResponse.getAfterAmastyCreditAmount(), totalAmountToShowInSMS);
				}

				DecimalFormat df = new DecimalFormat(".##");
				BigDecimal totalRefunDecimal = new BigDecimal(calcutedRefundAmount);
				response.setRefundAmount(df.format(totalRefunDecimal));

			} else {

				ErrorType error = new ErrorType();

				error.setErrorCode("203");
				error.setErrorMessage("invalid request");

				response.setError(error);
				response.setSendSms(false);

			}
		} else {

			ErrorType error = new ErrorType();

			error.setErrorCode("203");
			error.setErrorMessage("invalid request!");

			response.setError(error);
			response.setSendSms(false);
			return response;
		}
		response.setStatus(true);
		response.setStatusCode("200");

		if (StringUtils.isNotBlank(msgString)) {

			response.setStatusMsg(msgString);

		} else {

			response.setStatusMsg("Refunded Successfully!");

		}
		return response;
	}

	/**
	 * Refund amount to Store Credit
	 *
	 * @param order
	 * @param refundAmountDetails
	 */
	private void refundToStoreCredit(SalesOrder order, RefundAmountObject refundAmountDetails) {
		if (null != refundAmountDetails.getBaseAmastyStoreCreditAmount()
				&& refundAmountDetails.getBaseAmastyStoreCreditAmount().compareTo(BigDecimal.ZERO) != 0) {
			paymentDtfRefundHelper.addStoreCredit(order, refundAmountDetails.getRefundStorecreditAmount(), refundAmountDetails.isGiftVoucher());
		}
	}


	/**
	 * @param mapSkuList
	 * @return
	 */
	private boolean checkreturnQtyCheck(Map<String, BigDecimal> mapSkuList) {

		boolean qtyZeroFlag = true;
		List<String> removeSkuList = new ArrayList<>();
		for (Map.Entry<String, BigDecimal> entrySet : mapSkuList.entrySet()) {

			if (null != entrySet.getValue() && entrySet.getValue().compareTo(BigDecimal.ZERO) == 0) {
				removeSkuList.add(entrySet.getKey());

			}

		}

		if (CollectionUtils.isNotEmpty(removeSkuList)) {

			for (String sku : removeSkuList) {

				mapSkuList.remove(sku);
			}


		}
		return qtyZeroFlag;
	}

	@Override
	public GetShipmentV3Response getReturnShipment(Map<String, String> httpRequestHeadrs, String requestId) {

		GetShipmentV3Response response = new GetShipmentV3Response();
		GetShipmentV3ResponseBody responseBody = new GetShipmentV3ResponseBody();

		AmastyRmaRequest amastyRmaRequest = amastyRmaRequestRepository.findByRmaIncId(requestId);
		SalesOrder order = salesOrderRepository.findByEntityId(amastyRmaRequest.getOrderId());

		try {
			ResponseEntity<NavikResponse> navikResponse;
			
			// Check if Alpha is enabled for return shipments
			if (Constants.orderCredentials.getNavik().isAlphaEnabled()) {
				// Use MPS Alpha flow for return shipments
				MpsOrderCreateRequest mpsRequest = buildMpsOrderCreateRequest(order, null, null, Constants.orderCredentials.getNavik(), true, amastyRmaRequest, null);
				ResponseEntity<MpsOrderCreateResponse> mpsResponse = createsMpsShipmentWithAlpha(mpsRequest);
				LOGGER.info("Alpha MPS return response body:" + mapper.writeValueAsString(mpsResponse.getBody()));
				
				// Transform MPS response to Navik response format
				navikResponse = transformMpsResponseToNavikResponse(mpsResponse);
			} else {
				// Fallback to old Navik flow if Alpha is not enabled
				GetShipmentV3Request navikRequest = new GetShipmentV3Request();
				// Build the request using existing methods
				buildReturnNavikRequest(order, amastyRmaRequest, navikRequest);
				navikResponse = createsShipmentWithNavik(navikRequest);
			}
			
			return processReturnShipmentResponse(responseBody, amastyRmaRequest, order, navikResponse);

		} catch (RestClientException | JsonProcessingException e) {
			LOGGER.error("exception occurred during navik call:" + e.getMessage());
			response.setHasError(true);
			response.setErrorMessage(e.getMessage());
		}
		return response;
	}

	private void buildReturnNavikRequest(SalesOrder order, AmastyRmaRequest amastyRmaRequest, GetShipmentV3Request navikRequest) {
		String dutyFee = null;
		String rvpReasion = null;
		String dropOffAddress = null;
		NavikAddress navikAddress = null;
		Navikinfos navikInfos = null;

		List<Stores> stores = Constants.getStoresList();
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId())).findAny()
				.orElse(null);
		if (null != store && null != Constants.orderCredentials.getNavik()
				&& CollectionUtils.isNotEmpty(Constants.orderCredentials.getNavik().getDropOffDetails())) {

			navikInfos = Constants.orderCredentials.getNavik().getDropOffDetails().stream()
					.filter(e -> null != e.getWebSiteId()
							&& e.getWebSiteId().intValue() == store.getWebsiteId()).findFirst().orElse(null);

			if (navikInfos != null
					&& (order.getStoreId().equals(1) || order.getStoreId().equals(3)
					|| order.getStoreId().equals(7) || order.getStoreId().equals(11))) {

				LOGGER.info("Return Store value:" + order.getStoreId());

				navikInfos.setDutyFeePaid("");
			}

			if (navikInfos != null) {

				dutyFee = navikInfos.getDutyFeePaid();
				rvpReasion = navikInfos.getRvpReason();
				navikAddress = navikInfos.getAddressDetails();


			}
			if (null != navikInfos && null != amastyRmaRequest.getReturnType()
					&& amastyRmaRequest.getReturnType() == 1) {
				if (null != amastyRmaRequest.getCpId() && "smsa".equalsIgnoreCase(amastyRmaRequest.getCpId())) {
					dropOffAddress = navikInfos.getSmsaDropOffAddress();
				} else if (null != amastyRmaRequest.getCpId() && "aramex".equalsIgnoreCase(amastyRmaRequest.getCpId())) {
					dropOffAddress = navikInfos.getArmxDropOffAddress();
				} else {
					dropOffAddress = navikInfos.getSmsaDropOffAddress();
				}
			}

			if (Objects.nonNull(navikInfos) && (order.getStoreId().equals(7) || order.getStoreId().equals(11))
					&& Integer.valueOf(110).equals(order.getSubSalesOrder().getWarehouseLocationId())) {
				Navikinfos navikInfo = Constants.orderCredentials.getNavik().getDropOffDetails().stream()
						.filter(e -> null != e.getWebSiteId() && e.getWebSiteId().intValue() == 1).findFirst()
						.orElse(null);
				if (Objects.nonNull(navikInfo)) {
					dutyFee = "S";
					navikInfo.setDutyFeePaid("S");
					navikAddress = navikInfo.getAddressDetails();
					navikInfos = navikInfo;
					LOGGER.info("Reverse KSA address override on UAE. Duty fee is set. Details : " + navikInfos);
				}
			}

		}

		/* */
		PickupInfo pickupInfo = setRvpPickupInfo(navikRequest, order);

		/* */
		ReturnInfo returnInfo = new ReturnInfo();

		BeanUtils.copyProperties(pickupInfo, returnInfo);

		navikRequest.setReturnInfo(returnInfo);

		/* */
		setRvpDropInfo(navikRequest, navikAddress, dropOffAddress);

		/* */
		setRvpAdditional(order, navikRequest, dutyFee, rvpReasion);

		/* */
		setRvpShipmentDetails(order, navikRequest, amastyRmaRequest, navikInfos);
	}

	private GetShipmentV3Response processReturnShipmentResponse(GetShipmentV3ResponseBody responseBody,
																AmastyRmaRequest amastyRmaRequest, SalesOrder order, ResponseEntity<NavikResponse> navikResponse)
			throws JsonProcessingException {
		GetShipmentV3Response response = new GetShipmentV3Response();

		NavikResponse body = navikResponse.getBody();
		if (body != null && body.getResult() != null 
			&& body.getResult().getAlphaAwb() != null && !body.getResult().getAlphaAwb().isEmpty()
			&& body.getResult().getLabel() != null && !body.getResult().getLabel().isEmpty()) {
				responseBody.setTransporter(body.getResult().getCourier_name());
				responseBody.setAwbNumber(body.getResult().getWaybill());
				// Note: shippingLabelUrl will be set after GCS processing to use signed URL

				String encodeValue = null;
				if (null != Constants.orderCredentials && Constants.orderCredentials.getWms().isNewInVoiceEncode()) {
					encodeValue = order.getEntityId().toString().concat("#").concat(order.getCustomerEmail());
				} else {
					encodeValue = order.getEntityId().toString();
				}
				String encoded = Base64.getEncoder().encodeToString(encodeValue.getBytes());
				responseBody.setInvoiceUrl(Constants.orderCredentials.getOrderDetails().getOmsServicePublicBaseUrl()
						+ GENERATE_PDF_URI + encoded);

				ReturnShipmentTrack returnShipmentTrack = new ReturnShipmentTrack();

				returnShipmentTrack.setTrackNumber(body.getResult().getWaybill());
				returnShipmentTrack.setRequestId(amastyRmaRequest.getRequestId());
				returnShipmentTrack.setTrackingCode(body.getResult().getCourier_name());
				returnShipmentTrack.setAlphaAwb(body.getResult().getAlphaAwb());
				
				// Integrate GCS for shipping label (Common functionality for all APIs)
				try {
					String signedUrl = shippingLabelUrlService.processAndSaveShippingLabel(
						body.getResult().getLabel(),
						amastyRmaRequest,
						AmastyRmaRequest.class,
						null  // Use default bucket from config
					);
					LOGGER.info("Successfully processed shipping label to GCS for return shipment RMA: " + amastyRmaRequest.getRequestId());
				} catch (Exception e) {
					LOGGER.error("Failed to process shipping label to GCS for return shipment, using carrier URL: " + e.getMessage(), e);
					// Fallback: use original carrier URL
					amastyRmaRequest.setShippingLabel(body.getResult().getLabel());
				}
				
				amastyRmaRequestRepository.saveAndFlush(amastyRmaRequest);
				returnShipmentTrackerRepository.saveAndFlush(returnShipmentTrack);

				response.setAwbNumber(responseBody.getAwbNumber());
				response.setTransporter(responseBody.getTransporter());
				
				// Set shippingLabelUrl from saved entity (GCS signed URL if processed, carrier URL if fallback)
				String processedUrl = amastyRmaRequest.getShippingLabel();
				if (processedUrl != null && !processedUrl.isEmpty()) {
					responseBody.setShippingLabelUrl(processedUrl);
					response.setShippingLabelUrl(processedUrl);
					LOGGER.info("Using processed shipping label URL in response for return shipment (GCS or carrier): " + processedUrl.substring(0, Math.min(100, processedUrl.length())) + "...");
				} else {
					// Fallback to carrier URL if processing failed
					String carrierUrl = body.getResult().getLabel();
					responseBody.setShippingLabelUrl(carrierUrl);
					response.setShippingLabelUrl(carrierUrl);
					LOGGER.warn("Processed shipping label URL not available for return shipment, using carrier URL");
				}
				updateOrderStatusHistory(order, OrderConstants.RETURN_AWB_CREATED_MESSAGE + response.getAwbNumber(),
						OrderConstants.ORDER_STATUS_HISTORY_ENTITY, order.getStatus());
				//AWB created push to OTS
				paymentUtility.publishToSplitPubSubOTSForSalesOrder(order,"4.1","AWB CREATED");
			} else {
				response.setHasError(true);
				processShipmentError(response, body);

		}
		return response;
	}


	/**
	 *
	 */
	@Override
	@Transactional
	public void sendSms(String amastyRmaRequestId, String type, String template, OrderResponseDTO response) {

		if (response != null && response.getResponse() != null && response.getResponse().getCpId() != null) {
			orderHelper.sendSmsAndEMail(amastyRmaRequestId, type, template, null, response.getResponse().getCpId());
		} else {
			orderHelper.sendSmsAndEMail(amastyRmaRequestId, type, template, null, null);
		}

		if (null != template && (template.equals(OrderConstants.SMS_TEMPLATE_RETURN_AWB_CREATE)
				|| template.equals(OrderConstants.SMS_TEMPLATE_RETURN_DROP_OFF))) {

			AmastyRmaRequest amastyRmaRequest = amastyRmaRequestRepository.findByRmaIncId(amastyRmaRequestId);
			orderHelper.returnInventoryWmsRestCall(amastyRmaRequest.getRequestId().toString());
		}

	}

	/**
	 *
	 */
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void createDropOff(String amastyRmaRequestId, String type, String template
			, OrderResponseDTO response) {

		GetShipmentV3Response getShipment = null;
		if (null != template && template.equalsIgnoreCase(OrderConstants.SMS_TEMPLATE_RETURN_DROP_OFF)) {
			getShipment = getReturnShipment(new HashMap<>(),
					amastyRmaRequestId);
		}
		if (null != response && null != getShipment) {

			response.getResponse().setAwbNumber(getShipment.getAwbNumber());
            try {
				if(StringUtils.isNotBlank(getShipment.getShippingLabelUrl())){
					response.getResponse().setReturnInvoiceLink(gcpStorage.generateSignedUrl(getShipment.getShippingLabelUrl()));
				}
            } catch (Exception e) {
				LOGGER.error("Error generating signed URL for shipment label: " + getShipment.getShippingLabelUrl(), e);
				throw new RuntimeException("Failed to generate shipment label link", e);
			}
        }


	}

	@Override
	public GetShipmentV3Response rmaAwbCreation(Map<String, String> httpRequestHeadrs) {

		GetShipmentV3Response response = new GetShipmentV3Response();

		Integer rmaClubbinghrs = Constants.orderCredentials.getNavik().getReturnAwbCreateQueryClubinghrs();

		String fromDateString = Constants.orderCredentials.getNavik().getReturnAwbCreateClubingStartDate();


		Calendar calenderNowTime = Calendar.getInstance();

		calenderNowTime.add(Calendar.HOUR, -rmaClubbinghrs);
		SimpleDateFormat sdf4 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

		LOGGER.info("current time:" + sdf4.format(calenderNowTime.getTime()));

		String currentDateString = sdf4.format(calenderNowTime.getTime());

		Integer limit = Constants.orderCredentials.getNavik().getReturnAwbCreateLimit();

		List<AmastyRmaRequest> amastyList = amastyRmaRequestRepository.createReturnAwb(
				fromDateString, currentDateString, limit);

		if (CollectionUtils.isNotEmpty(amastyList)) {
			List<String> ids = amastyList.stream().map(AmastyRmaRequest::getRmaIncId).collect(Collectors.toList());
			LOGGER.info("Orders to be processed :" + ids);

			for (AmastyRmaRequest request : amastyList) {
				if (null != request.getStatus()
						&& !(request.getStatus().equals(12) || request.getStatus().equals(13))
						&& (null != request.getReturnType() && !request.getReturnType().equals(1))) {

					GetShipmentV3Response shipMentresponse = getReturnShipment(httpRequestHeadrs,
							request.getRmaIncId());

					if (null != shipMentresponse && !shipMentresponse.isHasError()) {
						sendSms(request.getRmaIncId(), RETURN, OrderConstants.SMS_TEMPLATE_RETURN_AWB_CREATE, null);
					}
				}
			}
		}

		return response;
	}

	@Override
	public List<SalesOrder> payfortQueryFetch() {
		Integer hrsAgo = 24;
		if (null != Constants.orderCredentials.getPayfort().getPayfortQueryStatusCheckbfrhrsAgo()) {
			hrsAgo = Integer.parseInt(Constants.orderCredentials.getPayfort().getPayfortQueryStatusCheckbfrhrsAgo());
		}
		return salesOrderRepository.findPaymentFailedOrders(
				Constants.orderCredentials.getPayfort().getPayfortQueryFetchInMinute(), hrsAgo);
	}

	/**
	 * Update payment status for payfort
	 */
	@Override
	public RefundPaymentRespone payfortQueryUpdate(SalesOrder order, String deviceId) {
		RefundPaymentRespone response = new RefundPaymentRespone();
		SalesOrderPayment salesOrderPayment = order.getSalesOrderPayment().stream().findFirst().orElse(null);
		if (Objects.isNull(salesOrderPayment) || Objects.isNull(salesOrderPayment.getMethod())) {
			LOGGER.info("Order payment information is missing for Order ID: " + order.getIncrementId());
			return null;
		} else {
			boolean isValidPayment = OrderConstants.ORDER_PAYMENT_METHOD_LIST.contains(salesOrderPayment.getMethod());
			if (isValidPayment) {
				response = paymentDtfRefundHelper.payfortQuerycall(order, salesOrderPayment, deviceId);
			}
		}
		return response;
	}


	/**
	 * @param amastyRequest
	 * @param order
	 * @return
	 */
	private BigDecimal getTotalRefundAmount(AmastyRmaRequest amastyRequest, SalesOrder order) {

		BigDecimal totalRefundAmount = BigDecimal.ZERO;
		BigDecimal qty;
		for (AmastyRmaRequestItem amastyItem : amastyRequest.getAmastyRmaRequestItems()) {

			if (amastyItem.getItemStatus().equals(12) || amastyItem.getItemStatus().equals(13)) continue;

			if (null != amastyItem.getActualQuantyReturned() && !amastyItem.getActualQuantyReturned().equals(0)) {

				qty = new BigDecimal(amastyItem.getActualQuantyReturned());

			} else {

				qty = amastyItem.getQty();

			}
			SalesOrderItem orderItem = (order.getSalesOrderItem().stream()
					.filter(e -> e.getItemId().equals(amastyItem.getOrderItemId())).findFirst().orElse(null));
			if (null != orderItem) {

				BigDecimal indivisualValue = orderItem.getPriceInclTax().multiply(qty);
				if (null != orderItem.getDiscountAmount()) {

					BigDecimal indivisualDiscount = orderItem.getDiscountAmount()
							.divide(orderItem.getQtyOrdered(), 4, RoundingMode.HALF_UP)
							.setScale(4, RoundingMode.HALF_UP);
					BigDecimal actualDisAmount = indivisualDiscount.multiply(qty)
							.setScale(2, RoundingMode.HALF_UP)
							.setScale(4, RoundingMode.HALF_UP);
					indivisualValue = indivisualValue.subtract(actualDisAmount);
				}
				totalRefundAmount = totalRefundAmount.add(indivisualValue);

			}

		}

		return totalRefundAmount.setScale(4, RoundingMode.HALF_UP);
	}

	/**
	 *
	 */
	@Override
	public void sendCancelOrderSmsAndEMail(OrderunfulfilmentRequest request, String totalCodCancelledAmount) {

		SalesOrder order = salesOrderRepository.findByIncrementId(request.getOrderCode());
		if (null != order) {
			orderHelper.sendCancelOrderSmsAndEMail(order, totalCodCancelledAmount);
		} else {
			// Split order case
			SplitSalesOrder splitOrder = splitSalesOrderRepository.findByIncrementId(request.getOrderCode());
			if (splitOrder != null) {
				orderHelperV3.sendSplitCancelOrderSmsAndEMail(splitOrder, totalCodCancelledAmount);
			}
		}
	}

	/**
	 *
	 */
	public void sendCancelOrderSmsAndEMail(Integer orderId, boolean isRefund) {

		SalesOrder order = salesOrderRepository.findByEntityId(orderId);

		String paymentMethod = null;
		if (CollectionUtils.isNotEmpty(order.getSalesOrderPayment())) {
			for (SalesOrderPayment payment : order.getSalesOrderPayment()) {
				paymentMethod = payment.getMethod();
			}
		}
		String template = sendSmsAndRedemptionCall(paymentMethod, isRefund);

		orderHelper.sendSmsAndEMail(order.getIncrementId(), "order", template, null, null);

	}

	public void sendCancelOrderSmsAndEMailForSplit(Integer orderId, boolean isRefund) {

        SplitSalesOrder order = splitSalesOrderRepository.findByEntityId(orderId);

        if (order == null) {
            return;
        }

        String paymentMethod = null;
        if (CollectionUtils.isNotEmpty(order.getSplitSalesOrderPayments())) {
            paymentMethod = order.getSplitSalesOrderPayments().stream()
                    .map(SplitSalesOrderPayment::getMethod)
                    .findFirst()
                    .orElse(null);
        }
        String template = sendSmsAndRedemptionCall(paymentMethod, isRefund);

        orderHelper.sendSmsAndEMail(order.getIncrementId(), "order", template, null, null);

    }

	public String sendSmsAndRedemptionCall(String paymentMethod, boolean isRefund) {
		String smsTemplate;

		if (null != paymentMethod && paymentMethod.equalsIgnoreCase(PaymentCodeENUM.CASH_ON_DELIVERY.getValue())) {

			smsTemplate = OrderConstants.SMS_TEMPLATE_COD_ORDER_CANCEL;

		} else if (null != paymentMethod && paymentMethod.equalsIgnoreCase(PaymentCodeENUM.TABBY_IMSTALLMENTS.getValue())) {

			smsTemplate = !isRefund ? OrderConstants.SMS_TEMPLATE_PAYMENT_FAILED_ORDER_CANCEL : OrderConstants.SMS_TEMPLATE_TABBY_INSTALLMENTS_ORDER_CANCEL;

		} else if (null != paymentMethod && paymentMethod.equalsIgnoreCase(PaymentCodeENUM.TABBY_PAYLATER.getValue())) {

			smsTemplate = !isRefund ? OrderConstants.SMS_TEMPLATE_PAYMENT_FAILED_ORDER_CANCEL : OrderConstants.SMS_TEMPLATE_TABBY_PAYLATER_ORDER_CANCEL;

		} else {
			smsTemplate = !isRefund ? OrderConstants.SMS_TEMPLATE_PAYMENT_FAILED_ORDER_CANCEL : OrderConstants.SMS_TEMPLATE_PREPAID_ORDER_CANCEL;
		}
		return smsTemplate;
	}

	@Override
	public GetShipmentV3Response rmaAwbDropOffCreation(Map<String, String> httpRequestHeadrs) {

		GetShipmentV3Response response = new GetShipmentV3Response();

		List<AmastyRmaRequest> amastyList = amastyRmaRequestRepository.createReturnDropOffAwb();

		if (CollectionUtils.isNotEmpty(amastyList)) {

			for (AmastyRmaRequest request : amastyList) {

				GetShipmentV3Response shipMentresponse = getReturnShipment(httpRequestHeadrs, request.getRmaIncId());

				if (null != shipMentresponse && !shipMentresponse.isHasError()) {

					sendSms(request.getRmaIncId(), RETURN, OrderConstants.SMS_TEMPLATE_RETURN_AWB_CREATE, null);

				}

			}
		}

		return response;
	}

	@Override
	@Transactional
	public BankSwiftCodeMapperResponse submitBankReturnRequest(Map<String, String> httpRequestHeaders,
															   BankSubmitFormRequest request) throws RollbackException {
		
		request.setAmount(request.getAmount().setScale(2, RoundingMode.DOWN));
		BankSwiftCodeMapperResponse resp = new BankSwiftCodeMapperResponse();
		BigDecimal roundedAmount = request.getAmount().setScale(2, RoundingMode.DOWN);
		String ibanStr = Constants.IBAN_COUNTRY_MAP.get(request.getStoreId());
		String ibanMatchStr = ibanStr + "(.*)";
		if (!request.getIban().matches(ibanMatchStr)) {
			resp.setStatus(false);
			resp.setStatusCode("203");
			resp.setStatusMsg("IBAN is invalid!");
			return resp;
		}

		if (request.getStoreId() == 7 || request.getStoreId() == 11 || request.getStoreId() == 12 || request.getStoreId() == 13) {
			if (request.getIban().length() < 23) {
				resp.setStatus(false);
				resp.setStatusCode("210");
				resp.setStatusMsg("IBAN must be at least 23 characters!");
				return resp;
			}
		} else {
			if (request.getIban().length() != 24) {
				resp.setStatus(false);
				resp.setStatusCode("210");
				resp.setStatusMsg("IBAN must be exactly 24 characters!");
				return resp;
			}
		}

		CustomerEntity customer = orderHelper.getCustomerDetails(request.getCustomerId(), null);
		if (ObjectUtils.isEmpty(customer)) {
			resp.setStatus(false);
			resp.setStatusCode("204");
			resp.setStatusMsg("Invalid customer!");
			return resp;
		}

		List<Stores> stores = Constants.getStoresList();
		Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId()))
				.findAny().orElse(null);
		if (ObjectUtils.isEmpty(store) || Objects.isNull(store)) {
			resp.setStatus(false);
			resp.setStatusCode("205");
			resp.setStatusMsg("Invalid store!");
			return resp;
		}

		try {

			List<AmastyStoreCredit> amastyStoreCredits = amastyStoreCreditRepository
					.findByCustomerId(customer.getEntityId());
			AmastyStoreCredit amastyStoreCredit = !amastyStoreCredits.isEmpty() ? amastyStoreCredits.get(0) : null;
			if (ObjectUtils.isEmpty(amastyStoreCredit) || Objects.isNull(amastyStoreCredit)) {
				resp.setStatus(false);
				resp.setStatusCode("206");
				resp.setStatusMsg("Store credit not found!");
				return resp;
			}

			BigDecimal convertedStoreCredit = roundedAmount.multiply(store.getCurrencyConversionRate());
			BigDecimal storeCreditValue = amastyStoreCredit.getStoreCredit().abs();
			BigDecimal returnableStoreCreditValue = BigDecimal.ZERO;
			if (ObjectUtils.isNotEmpty(amastyStoreCredit.getReturnableAmount()))
				returnableStoreCreditValue = amastyStoreCredit.getReturnableAmount().abs();
			LOGGER.info("Wallet balance: " + storeCreditValue);
			LOGGER.info("Wallet balance returnable: " + returnableStoreCreditValue);
			LOGGER.info("request bank transfer amt: " + convertedStoreCredit);

			if (convertedStoreCredit.compareTo(storeCreditValue) > 0
					|| convertedStoreCredit.compareTo(returnableStoreCreditValue) > 0) {
				resp.setStatus(false);
				resp.setStatusCode("207");
				resp.setStatusMsg("Amount entered to deduct is greater than current wallet balance!");
				return resp;
			}

			amastyStoreCredit.setStoreCredit(storeCreditValue.subtract(convertedStoreCredit));
			amastyStoreCredit.setReturnableAmount(returnableStoreCreditValue.subtract(convertedStoreCredit));
			amastyStoreCreditRepository.saveAndFlush(amastyStoreCredit);

			List<AmastyStoreCreditHistory> histories = amastyStoreCreditHistoryRepository
					.findByCustomerId(customer.getEntityId());
			int newCustomerHistoryId = 1;
			if (CollectionUtils.isNotEmpty(histories)) {
				AmastyStoreCreditHistory lastHistory = histories.get(histories.size() - 1);
				newCustomerHistoryId = lastHistory.getCustomerHistoryId() + 1;
			}
			AmastyStoreCreditHistory history = new AmastyStoreCreditHistory();
			history.setCustomerHistoryId(newCustomerHistoryId);
			history.setMessage("Bank Transfer Initiated");
			history.setCustomerId(customer.getEntityId());
			history.setDeduct(1);
			history.setDifference(convertedStoreCredit);
			history.setStoreCreditBalance(storeCreditValue.subtract(convertedStoreCredit));
			history.setAction(2);
			history.setCreatedAt(new Timestamp(new Date().getTime()));
			history.setStoreId(request.getStoreId());
			amastyStoreCreditHistoryRepository.saveAndFlush(history);

		} catch (DataAccessException de) {
			LOGGER.error("DataAccessException occurred during bank transfer:" + de.getMessage());
			resp.setStatus(false);
			resp.setStatusCode("208");
			resp.setStatusMsg("DataAccessException: " + de.getMessage());
			return resp;
		} catch (Exception e) {
			LOGGER.error("Exception occurred during bank transfer:" + e.getMessage());
			resp.setStatus(false);
			resp.setStatusCode("209");
			resp.setStatusMsg("Exception: " + e.getMessage());
			return resp;
		}

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.add(OrderConstants.AUTH_BEARER_HEADER, orderHelper.getAuthorization(internalHeaderBearerToken));

		HttpEntity<BankSubmitFormRequest> requestHttpEntity = new HttpEntity<>(request, requestHeaders);

		String url = null;
		if (ObjectUtils.isNotEmpty(Constants.orderCredentials)
				&& ObjectUtils.isNotEmpty(Constants.orderCredentials.getOrderDetails().getOmsServiceBaseUrl())) {
			url = Constants.orderCredentials.getOrderDetails().getOmsServiceBaseUrl() + "/v1/banktransfer/create";
		}
		if (ObjectUtils.isEmpty(url)) {
			resp.setStatus(false);
			resp.setStatusCode("201");
			resp.setStatusMsg("OMS base url not found!");
			return resp;
		}
		LOGGER.info("OMS Submit Url:" + url);

		try {
			LOGGER.info("OMS bank request body:" + mapper.writeValueAsString(requestHttpEntity.getBody()));
			ResponseEntity<BankSwiftCodeMapperResponse> apiResponse = restTemplate.exchange(
					url, HttpMethod.POST, requestHttpEntity, BankSwiftCodeMapperResponse.class);

			resp = apiResponse.getBody();
			LOGGER.info("OMS bank response body:" + mapper.writeValueAsString(resp));

		} catch (Exception e) {
			resp = new BankSwiftCodeMapperResponse();
			LOGGER.error("Error from OMS bank submit:" + e.getMessage());
			resp.setStatus(false);
			resp.setStatusCode("202");
			resp.setStatusMsg("Bank submit exception" + e.getMessage());
			throw new RollbackException("211", "Error, rolling back! " + e.getMessage());
		}

		return resp;
	}

	@Override
	public BankSwiftCodeMapperResponse getBankSwiftCodes(Map<String, String> httpRequestHeaders) {

		BankSwiftCodeMapperResponse resp = new BankSwiftCodeMapperResponse();
		resp.setStatus(true);
		resp.setStatusCode("200");
		resp.setStatusMsg("Successfully fetched bank swift codes");
		if (ObjectUtils.isNotEmpty(Constants.orderCredentials)
				&& MapUtils.isNotEmpty(Constants.orderCredentials.getBankSwiftCodes())) {
			Map<String, List<BankSwiftCode>> swiftCodes = Constants.orderCredentials.getBankSwiftCodes();
			resp.setResponse(swiftCodes);
		} else resp.setResponse(new HashMap<>());
		return resp;
	}

	@Override
	@Transactional
	public OmsOrderresponsedto orderWmsUnhold() {

		List<SalesOrder> orderList = salesOrderRepository.ordersHoldwmspush(Constants.orderCredentials.getWms().getWmsOrderUnholdPushMinutes());
		List<SplitSalesOrder> splitSalesOrderList = splitSalesOrderRepository.ordersHoldwmspushForSplitOrder(Constants.orderCredentials.getWms().getWmsOrderUnholdPushMinutes());
		return orderpushHelper.ordersHoldwmspush(orderList, splitSalesOrderList);
	}

	@Override
	public String getFileForCaptureDropoffMailProcessing(String directoryName, List<SalesOrder> list) {
		String fileName = null;
		fileName = directoryName + "/" + new Date().getTime() + "_capture_dropoff.csv";
		try (FileWriter out = new FileWriter(fileName)) {
			CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.withHeader(HEADERS));
			list.stream().forEach(o -> {
				try {
					printer.printRecord(o.getIncrementId(), o.getStoreId(), o.getPayfortAuthorized(),
							o.getAuthorizationCapture(), o.getCustomerEmail(), o.getCreatedAt());
				} catch (IOException ex) {
					LOGGER.error("error printing to file");
					LOGGER.error(ex.getMessage());
				}
			});
			out.flush();
			printer.close();
		} catch (IOException e) {
			LOGGER.error("file read exception! : " + e);
		}
		return fileName;
	}

	@Override
	public void deleteDirectory(File file) throws IOException {
		if (file.isDirectory()) {
			File[] entries = file.listFiles();
			if (entries != null) {
				for (File entry : entries) {
					deleteDirectory(entry);
				}
			}
		}
		if (!file.delete()) {
			throw new IOException("Failed to delete " + file);
		}
	}

	@Override
	public OmsOrderresponsedto updateWmsOrderCancel() {

		List<String> statusList = OrderConstants.CANCEL_UNFULFILMENT_ORDER_PUSH_STATUS_LIST;
		Integer orderCheckInlastMinutes = 1440;
		if (null != Constants.orderCredentials.getWms().getWmsCancelOrderWmsStatusCheckMinutes()) {
			orderCheckInlastMinutes = Constants.orderCredentials.getWms().getWmsCancelOrderWmsStatusCheckMinutes();
		}
		List<SalesOrder> orderList = salesOrderRepository.updateOrdercancelforwmspush(statusList, orderCheckInlastMinutes);

		OmsOrderresponsedto response = new OmsOrderresponsedto();
		try {
			if (CollectionUtils.isNotEmpty(orderList)) {

				for (SalesOrder order : orderList) {

					order.setUpdatedAt(new Timestamp(new Date().getTime()));
					order.setWmsStatus(2);
					salesOrderRepository.saveAndFlush(order);
				}
				response.setStatus(true);
				response.setStatusCode("200");
				response.setStatusMsg("pushed successfully");
			}

		} catch (Exception ex) {

			LOGGER.error("exception occoured during cancel order update");
			response.setStatus(false);
			response.setStatusCode("201");
			response.setStatusMsg("pushed successfully" + ex.getMessage());
		}

		return response;
	}

	public Object getTrackingData(String waybill) {
		try {
			LOGGER.info("Tracking data : Inside getTrackingData fucntion ");
			LOGGER.info("Tracking data : waybill " + waybill);
			String url = Constants.orderCredentials.getOrderDetails().getHawkLiveUrl();
			String token = Constants.getAlphaToken();
			HttpHeaders headers = new HttpHeaders();
			headers.set("Authorization", "Bearer " + token);

			HttpEntity<Object> requestEntity = new HttpEntity<>(headers);

			ResponseEntity<Object> responseEntity = restTemplate.exchange(url + "?waybill=" + waybill, HttpMethod.GET,
					requestEntity, Object.class);
			LOGGER.info("Tracking data : Data Fetched. Returning response");
			return responseEntity.getBody();
		} catch (RestClientException e) {
			LOGGER.info("Tracking data : Error while fetching tracking data: {}" + e);
			OrderTrackingResponse.builder().status("false").statusCode("400").statusMsg("Bad request").build();
			return null;
		}

	}

	/** Aggregates native query rows for increment-id tracking (seller order line items). */
	private static final class SellerOrderTrackingAggregate {
		private final Set<String> processingSkus = new LinkedHashSet<>();
		private final Set<String> packedSkus = new LinkedHashSet<>();
		private final Set<String> shippedSkus = new LinkedHashSet<>();
		private java.sql.Timestamp maxSellerOrderUpdatedAt;
		private java.sql.Timestamp latestShippedUpdatedAt;
		private int shippedCount;
		private final int totalRows;

		private SellerOrderTrackingAggregate(List<Object[]> rows) {
			this.totalRows = rows.size();
			for (Object[] row : rows) {
				ingestRow(row);
			}
		}

		static SellerOrderTrackingAggregate fromRows(List<Object[]> rows) {
			return new SellerOrderTrackingAggregate(rows);
		}

		private void ingestRow(Object[] row) {
			String sellerOrderStatus = (String) row[4];
			String sku = (String) row[1];
			java.sql.Timestamp updatedAt = (java.sql.Timestamp) row[6];
			if (maxSellerOrderUpdatedAt == null || (updatedAt != null && updatedAt.after(maxSellerOrderUpdatedAt))) {
				maxSellerOrderUpdatedAt = updatedAt;
			}
			if ("processing".equals(sellerOrderStatus)) {
				processingSkus.add(sku);
			} else if ("packed".equals(sellerOrderStatus)) {
				packedSkus.add(sku);
			} else if ("shipped".equals(sellerOrderStatus) || "delivered".equals(sellerOrderStatus)) {
				shippedSkus.add(sku);
			}
			if ("shipped".equals(sellerOrderStatus) || "delivered".equals(sellerOrderStatus)) {
				shippedCount++;
				if (latestShippedUpdatedAt == null || (updatedAt != null && updatedAt.after(latestShippedUpdatedAt))) {
					latestShippedUpdatedAt = updatedAt;
				}
			}
		}

		boolean isAllSkusShipped() {
			return totalRows > 0 && shippedCount == totalRows;
		}

		java.sql.Timestamp getLatestShippedUpdatedAt() {
			return latestShippedUpdatedAt;
		}

		void appendProcessingSellerStatusIfNeeded(
				List<org.styli.services.order.pojo.response.Order.IncrementIdTrackingResponse.StatusHistoryItem> statusHistory,
				SplitSalesOrder splitSalesOrder) {
			if (totalRows == 0 || isAllSkusShipped()) {
				return;
			}
			String note = buildSellerProcessingNote();
			statusHistory.add(org.styli.services.order.pojo.response.Order.IncrementIdTrackingResponse.StatusHistoryItem.builder()
					.status("Seller is processing your order")
					.note(note)
					.timestamp(maxSellerOrderUpdatedAt != null ? maxSellerOrderUpdatedAt : splitSalesOrder.getCreatedAt())
					.build());
		}

		private String buildSellerProcessingNote() {
			StringBuilder noteBuilder = new StringBuilder();
			if (!shippedSkus.isEmpty()) {
				noteBuilder.append(String.join(", ", shippedSkus)).append(" dispatched");
			}
			if (!packedSkus.isEmpty()) {
				if (!noteBuilder.isEmpty()) {
					noteBuilder.append(". ");
				}
				noteBuilder.append(String.join(", ", packedSkus)).append(" is packed");
			}
			if (!processingSkus.isEmpty()) {
				if (!noteBuilder.isEmpty()) {
					noteBuilder.append(". ");
				}
				noteBuilder.append(String.join(", ", processingSkus)).append(" is being processed");
			}
			return noteBuilder.toString();
		}
	}

	private static String resolveTrackingOrderType(String incrementId) {
		if (incrementId.endsWith("-G1")) {
			return "GLOBAL";
		}
		return "LOCAL";
	}

	@SuppressWarnings("unchecked")
	private List<Object[]> querySellerOrdersForIncrementTracking(String incrementId) {
		String query = "SELECT " +
				"ssoi.item_id, " +
				"ssoi.sku, " +
				"sso.increment_id as seller_order_increment_id, " +
				"ssoi.seller_order_id, " +
				"sso.status as seller_order_status, " +
				"sso.has_global_shipment, " +
				"sso.updated_at " +
				"FROM split_seller_order_item ssoi " +
				"JOIN split_seller_order sso ON ssoi.seller_order_id = sso.entity_id " +
				"JOIN split_sales_order sso_main ON sso.split_order_id = sso_main.entity_id " +
				"WHERE sso_main.increment_id = :incrementId";
		return entityManager.createNativeQuery(query)
				.setParameter("incrementId", incrementId)
				.getResultList();
	}

	private static org.styli.services.order.pojo.response.Order.IncrementIdTrackingResponse.StatusHistoryItem buildOrderPlacedTrackingItem(
			SplitSalesOrder splitSalesOrder) {
		return org.styli.services.order.pojo.response.Order.IncrementIdTrackingResponse.StatusHistoryItem.builder()
				.status("Order Placed Successfully")
				.note("Your order has been placed successfully")
				.timestamp(splitSalesOrder.getCreatedAt())
				.build();
	}

	private static org.styli.services.order.pojo.response.Order.IncrementIdTrackingResponse.StatusHistoryItem buildCrossBorderTransitTrackingItem(
			SplitSalesOrder splitSalesOrder, java.sql.Timestamp latestShippedUpdatedAt) {
		return org.styli.services.order.pojo.response.Order.IncrementIdTrackingResponse.StatusHistoryItem.builder()
				.status("Cross border transit")
				.note("Your order is being shipped to local warehouse (Saudi/Riyadh)")
				.timestamp(latestShippedUpdatedAt != null ? latestShippedUpdatedAt : splitSalesOrder.getUpdatedAt())
				.build();
	}

	@Override
	public org.styli.services.order.pojo.response.Order.IncrementIdTrackingResponse getTrackingDataByIncrementId(String incrementId) {
		try {
			LOGGER.info("Tracking data by increment_id: Inside getTrackingDataByIncrementId function");
			LOGGER.info("Tracking data by increment_id: incrementId " + incrementId);

			SplitSalesOrder splitSalesOrder = splitSalesOrderRepository.findByIncrementId(incrementId);
			if (splitSalesOrder == null) {
				LOGGER.warn("Tracking data by increment_id: Order not found for incrementId " + incrementId);
				return null;
			}

			String orderType = resolveTrackingOrderType(incrementId);
			List<org.styli.services.order.pojo.response.Order.IncrementIdTrackingResponse.StatusHistoryItem> statusHistory = new ArrayList<>();
			statusHistory.add(buildOrderPlacedTrackingItem(splitSalesOrder));

			List<Object[]> sellerOrderResults = querySellerOrdersForIncrementTracking(incrementId);
			SellerOrderTrackingAggregate aggregate = SellerOrderTrackingAggregate.fromRows(sellerOrderResults);
			aggregate.appendProcessingSellerStatusIfNeeded(statusHistory, splitSalesOrder);

			if (aggregate.isAllSkusShipped()) {
				statusHistory.add(buildCrossBorderTransitTrackingItem(splitSalesOrder, aggregate.getLatestShippedUpdatedAt()));
			}

			return org.styli.services.order.pojo.response.Order.IncrementIdTrackingResponse.builder()
					.incrementId(incrementId)
					.orderType(orderType)
					.statusHistory(statusHistory)
					.build();

		} catch (Exception e) {
			LOGGER.error("Tracking data by increment_id: Error while fetching tracking data for incrementId: " + incrementId, e);
			return null;
		}
	}

	@Override
	@Transactional
	public OmsOrderresponsedto orderWmsHoldFalse() {

		int wmsOrderHoldFalseHours = (Constants.orderCredentials.getWms().getWmsOrderHoldFalseHours() != null)
				? Constants.orderCredentials.getWms().getWmsOrderHoldFalseHours()
				: 24;

		List<SalesOrder> orderList = null;
		try {
			orderList = salesOrderRepository.orderHoldFalseInWms(wmsOrderHoldFalseHours);
		} catch (Exception e) {
			LOGGER.info("Not able to fetch orders to set WMS Hold", e);
		}

		return orderpushHelper.orderHoldFalseInWms(orderList);
	}

	@Override
	@Transactional
	public OmsOrderresponsedto findOrdersNotRefunded() {
		LOGGER.info("findOrdersNotRefunded : Inside findOrdersNotRefunded function");
		WmsDetails data = Constants.orderCredentials.getWms();
		int daysFromStart = Optional.ofNullable(data.getRefundEmailDaysFromStart()).orElse(3);
		int daysFromEnd = Optional.ofNullable(data.getRefundEmailDaysFromEnd()).orElse(30);

		List<String> orderList = new ArrayList<>();

		try {
			List<String> returnOrdersNotRefunded = amastyRmaRequestRepository.findReturnOrdersNotRefundedQuery(daysFromStart, daysFromEnd);
			List<String> rtoOrdersNotRefunded = salesOrderRepository.findRtoOrdersNotRefundedQuery(daysFromStart, daysFromEnd);
			addOrdersToList(orderList, returnOrdersNotRefunded);
			addOrdersToList(orderList, rtoOrdersNotRefunded);
			LOGGER.info("findOrdersNotRefunded : Return Orders Not Yet Refunded :" + returnOrdersNotRefunded);
			LOGGER.info("findOrdersNotRefunded : RTO Orders  Not Yet Refunded :" + rtoOrdersNotRefunded);
			if (orderList.isEmpty()) {
				LOGGER.info("findOrdersNotRefunded : No non-refunded orders found within the specified date range.");
				return null;
			}

		} catch (Exception e) {
			LOGGER.error("findOrdersNotRefunded : Failed to fetch orders that are not refunded", e);
		}

		return orderpushHelper.triggerEmailForOrdersNotRefunded(orderList);
	}

	@Override
	@Transactional
	public OmsRtoCodResponse CreateOmsRtoOrderZatca(Map<String, String> httpRequestHeaders, @Valid OmsRtoCodRequest request) {

		SalesOrder order = salesOrderRepository.findByEntityId(request.getOrderId());
		OmsRtoCodResponse omsRtoCodResponse = new OmsRtoCodResponse();
		if (ObjectUtils.isEmpty(order)) {
			omsRtoCodResponse.setStatusMsg("No Such Order");
			omsRtoCodResponse.setStatusCode("400");
			omsRtoCodResponse.setStatus(false);
		}

		if (order.getStoreId() == null) {
			omsRtoCodResponse.setStatusMsg("Order Has No Store Data");
			omsRtoCodResponse.setStatusCode("400");
			omsRtoCodResponse.setStatus(false);
		}

		Stores store = zatcaServiceImpl.getStoreById(order.getStoreId());
		if (ObjectUtils.isEmpty(store)) {
			omsRtoCodResponse.setStatusMsg("Order Has No Store Data");
			omsRtoCodResponse.setStatusCode("400");
			omsRtoCodResponse.setStatus(false);
		}

		ZatcaConfig zatcaConfig = Constants.orderCredentials.getZatcaConfig();
		if (ObjectUtils.isEmpty(zatcaConfig)) {
			omsRtoCodResponse.setStatusMsg("Order Has No Zatca Config");
			omsRtoCodResponse.setStatusCode("400");
			omsRtoCodResponse.setStatus(false);
		}

		LOGGER.info(Constants.orderCredentials.getZatcaConfig().getCodRtoZatca());
		if (Constants.orderCredentials.getZatcaConfig().getCodRtoZatca()) {
			ZatcaInvoice zatcaInvoice = zatcaServiceImpl.codRtoZatcaInvoice(order, zatcaConfig);

			if (null != zatcaInvoice) {

				try {
					String URL = zatcaConfig.getBaseUrl() + "/v2/einvoices/generate/async";

					HttpHeaders requestHeaders = new HttpHeaders();
					requestHeaders.setContentType(MediaType.APPLICATION_JSON);
					requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
					requestHeaders.add(Constants.USER_CONSTANT, Constants.USER_AGENT_FOR_REST_CALLS);
					requestHeaders.add("x-cleartax-auth-token", zatcaConfig.getClearTaxToken());
					requestHeaders.add("vat", zatcaConfig.getVatNo());

					HttpEntity<ZatcaInvoice> requestBody = new HttpEntity<>(zatcaInvoice, requestHeaders);
					LOGGER.info("Zatca Body :  + " + mapper.writeValueAsString(zatcaInvoice));
					ResponseEntity<ZatcaInvoiceResponse> response = restTemplate.exchange(URL, HttpMethod.POST, requestBody,
							ZatcaInvoiceResponse.class);

					if (response.getStatusCode() == HttpStatus.OK) {
						SalesCreditmemo salesCreditmemo = new SalesCreditmemo();
						salesCreditmemo.setMemoType("codRto");
						salesCreditmemo.setOrderId(request.getOrderId());
						salesCreditmemo.setZatcaStatus(ObjectUtils.isNotEmpty(response.getBody()) && StringUtils.isNotBlank(response.getBody().getInvoiceStatus()) && StringUtils.isNotEmpty(response.getBody().getInvoiceStatus()) ? response.getBody().getInvoiceStatus() : response.getBody().getStatus());
						salesCreditmemo.setStoreId(order.getStoreId());
						if (ObjectUtils.isNotEmpty(response.getBody()) && response.getBody().getQrCode() != null) {
							salesCreditmemo.setZatcaQRCode(response.getBody().getQrCode());
						}
						salesCreditmemo.setCashOnDeliveryFee(order.getCashOnDeliveryFee());
						salesCreditmemo.setBaseCashOnDeliveryFee(order.getBaseCashOnDeliveryFee());
						salesCreditmemo.setCreatedAt(new Timestamp(new Date().getTime()));
						salesCreditmemo.setUpdatedAt(new Timestamp(new Date().getTime()));


						salesCreditmemo.setAmstorecreditAmount(order.getAmstorecreditAmount());
						salesCreditmemo.setAmstorecreditBaseAmount(order.getAmstorecreditBaseAmount());
						salesCreditmemo.setEasCoins(order.getSubSalesOrder().getEasCoins());
						salesCreditmemo.setEasValueInCurrency(order.getSubSalesOrder().getEasValueInCurrency());
						salesCreditmemo.setEasValueInBaseCurrency(order.getSubSalesOrder().getEasValueInBaseCurrency());
						salesCreditmemo.setBaseShippingTaxAmount(order.getBaseShippingTaxAmount());

						salesCreditmemo.setShukranPointsRefunded(order.getSubSalesOrder().getTotalShukranCoinsBurned());
						salesCreditmemo.setShukranPointsRefundedValueInBaseCurrency(order.getSubSalesOrder().getTotalShukranBurnedValueInBaseCurrency());
						salesCreditmemo.setShukranPointsRefundedValueInCurrency(order.getSubSalesOrder().getTotalShukranBurnedValueInCurrency());

						salesCreditmemo.setStoreToOrderRate(order.getStoreToOrderRate());

						salesCreditmemo.setBaseToOrderRate(order.getBaseToOrderRate());

						salesCreditmemo.setGrandTotal(order.getGrandTotal());
						salesCreditmemo.setBaseGrandTotal(order.getBaseGrandTotal());
						salesCreditmemo.setBaseSubtotalInclTax(order.getBaseSubtotalInclTax());
						salesCreditmemo.setSubtotalInclTax(order.getSubtotalInclTax());
						salesCreditmemo.setTaxAmount(order.getTaxAmount());

						salesCreditmemo.setBaseTaxAmount(order.getBaseTaxAmount());
						salesCreditmemo.setDiscountTaxCompensationAmount(order.getDiscountTaxCompensationInvoiced());
						salesCreditmemo.setBaseDiscountTaxCompensationAmount(
								order.getBaseDiscountTaxCompensationInvoiced());
						salesCreditmemo.setSubtotal(order.getSubtotal());
						salesCreditmemo.setBaseSubtotal(
								order.getBaseSubtotal());

						salesCreditmemo.setBaseDiscountAmount(order.getBaseDiscountAmount());
						salesCreditmemo.setDiscountAmount(order.getDiscountAmount());


						salesCreditmemo.setShippingAmount(order.getShippingAmount());
						salesCreditmemo.setBaseShippingAmount(order.getBaseShippingAmount());
						salesCreditmemo.setShippingTaxAmount(order.getShippingTaxAmount());
						salesCreditmemo.setShippingInclTax(order.getShippingInclTax());


						salesCreditmemo.setStoreToBaseRate(order.getStoreToBaseRate());
						salesCreditmemo.setBaseToGlobalRate(order.getBaseToGlobalRate());
						salesCreditmemo.setBaseAdjustment(order.getBaseAdjustmentPositive());


						salesCreditmemo.setAdjustment(order.getAdjustmentPositive());


						salesCreditmemo.setOrderId(order.getEntityId());
						salesCreditmemo.setState(2);
						salesCreditmemo.setShippingAddressId(order.getShippingAddressId());
						salesCreditmemo.setBillingAddressId(order.getBillingAddressId());
						salesCreditmemo.setBaseCurrencyCode(order.getBaseCurrencyCode());
						salesCreditmemo.setGlobalCurrencyCode(order.getGlobalCurrencyCode());
						salesCreditmemo.setOrderCurrencyCode(order.getOrderCurrencyCode());
						salesCreditmemo.setStoreCurrencyCode(order.getStoreCurrencyCode());
						salesCreditmemo.setIncrementId("RTO-COD-" + order.getEntityId());


						salesCreditmemo.setBaseShippingInclTax(order.getBaseShippingInclTax());
						salesCreditmemo.setDiscountDescription(order.getCouponCode());

						salesCreditmemo.setBaseCashOnDeliveryFee(order.getBaseCashOnDeliveryFee());

						salesCreditmemo.setRmaNumber(order.getEntityId().toString());

						salesCreditmemo.setAdjustmentNegative(order.getAdjustmentNegative());
						salesCreditmemo.setBaseAdjustmentNegative(order.getBaseAdjustmentNegative());


						SalesCreditmemo memo = salesCreditmemoRepository.save(salesCreditmemo);


						for (SalesOrderItem orderItem : order.getSalesOrderItem()) {
							SalesCreditmemoItem memoItem = new SalesCreditmemoItem();

							memoItem.setProductId(orderItem.getProductId());
							memoItem.setOrderItemId(orderItem.getItemId());
							memoItem.setSku(orderItem.getSku());
							memoItem.setName(orderItem.getName());
							memoItem.setHsnCode(orderItem.getHsnCode());
							memoItem.setQty(orderItem.getQtyInvoiced());
							memoItem.setRowTotal(orderItem.getRowTotal());
							memoItem.setParentId(memo.getEntityId());

							memoItem.setPriceInclTax(orderItem.getPriceInclTax());
							memoItem.setBasePriceInclTax(orderItem.getBasePriceInclTax());
							memoItem.setDiscountAmount(orderItem.getDiscountAmount());
							memoItem.setBaseDiscountAmount(orderItem.getBaseDiscountAmount());
							memoItem.setTaxAmount(orderItem.getTaxAmount());
							memoItem.setBaseTaxAmount(orderItem.getBaseTaxAmount());

							memoItem.setDiscountTaxCompensationAmount(orderItem.getDiscountTaxCompensationAmount());
							memoItem.setBaseDiscountTaxCompensationAmount(orderItem.getBaseDiscountTaxCompensationAmount());
							memoItem.setWeeeTaxAppliedRowAmount(orderItem.getWeeeTaxAppliedRowAmount());
							memoItem.setWeeeTaxRowDisposition(orderItem.getWeeeTaxRowDisposition());
							memoItem.setBaseWeeeTaxRowDisposition(orderItem.getBaseWeeeTaxRowDisposition());
							memoItem.setBaseCost(orderItem.getBaseCost());
							memoItem.setPrice(orderItem.getPrice());
							memoItem.setVoucherAmount(BigDecimal.ZERO);

							salesCreditmemoItemRepository.saveAndFlush(memoItem);
						}
						LOGGER.info("responseData" + response.getBody());
						omsRtoCodResponse.setStatusMsg("Success");
						omsRtoCodResponse.setStatusCode("200");
						omsRtoCodResponse.setStatus(true);
					}
				} catch (Exception e) {
					LOGGER.error("exception occoured during Zatca:" + e.getMessage());
					omsRtoCodResponse.setStatusMsg("Error" + e.getMessage());
					omsRtoCodResponse.setStatusCode("400");
					omsRtoCodResponse.setStatus(false);

				}
			}
		} else {
			omsRtoCodResponse.setStatusMsg("Error");
			omsRtoCodResponse.setStatusCode("400");
			omsRtoCodResponse.setStatus(false);
		}
		if (ObjectUtils.isEmpty(omsRtoCodResponse)) {
			omsRtoCodResponse.setStatusMsg("Error");
			omsRtoCodResponse.setStatusCode("400");
			omsRtoCodResponse.setStatus(false);
		}
		return omsRtoCodResponse;

	}

	private void addOrdersToList(List<String> orderList, List<String> orders) {
		if (orders != null && !orders.isEmpty()) {
			orderList.addAll(orders);
			LOGGER.info("findOrdersNotRefunded : All Orders pending to be refunded: " + orders);
		}
	}

	public static String normalizeAndRemoveUnsupportedCharacters(String input) {
		if (input == null) return null;
		String normalized = Normalizer.normalize(input, Normalizer.Form.NFKD);
		String asciiOnly = normalized.replaceAll("[^\\p{ASCII}]", "");
		String result = asciiOnly.replaceAll("[^\\u0000-\\uFFFF]", "");
		LOGGER.info("Final result: " + result);
		return result;
	}

	private static final class ClawbackOriginalTransactionInfo {
		String incrementId;
		Timestamp dateTime;
	}

	/**
	 * Split order: 1) two splits both delivered -> parent sales_order; 2) else resolve by return item SKU -> that split; 3) else first split.
	 * Not split order -> use sales_order (unchanged).
	 */
	private ClawbackOriginalTransactionInfo resolveClawbackOriginalTransaction(SalesOrder order, AmastyRmaRequest rmaRequest) {
		ClawbackOriginalTransactionInfo info = new ClawbackOriginalTransactionInfo();
		info.incrementId = order.getIncrementId();
		info.dateTime = order.getCreatedAt();
		if (!Integer.valueOf(1).equals(order.getIsSplitOrder())) {
			return info;
		}
		List<SplitSalesOrder> splitOrders = splitSalesOrderRepository.findByOrderId(order.getEntityId());
		if (splitOrders == null) {
			splitOrders = Collections.emptyList();
		}
		boolean twoSplitsBothDelivered = splitOrders.size() == 2
				&& splitOrders.stream().allMatch(s -> OrderConstants.DELIVERED_ORDER_STATUS.equalsIgnoreCase(s.getStatus()));
		if (twoSplitsBothDelivered) {
			LOGGER.info("Clawback OriginalTransaction: orderId=" + order.getEntityId() + ", split_sales_order has exactly two records and both status=delivered, using sales_order incrementId=" + info.incrementId + ", createdAt=" + info.dateTime);
			return info;
		}
		String returnItemSku = extractReturnItemSkuForClawback(order, rmaRequest);
		SplitSalesOrderItem splitItemBySku = StringUtils.isNotBlank(returnItemSku)
				? splitSalesOrderItemRepository.findFirstBySalesOrder_EntityIdAndProductTypeAndSku(order.getEntityId(), "configurable", returnItemSku)
				: null;
		if (splitItemBySku != null && splitItemBySku.getSplitSalesOrder() != null) {
			SplitSalesOrder splitForReturnItem = splitItemBySku.getSplitSalesOrder();
			if (StringUtils.isNotBlank(splitForReturnItem.getIncrementId())) {
				info.incrementId = splitForReturnItem.getIncrementId();
			}
			if (splitForReturnItem.getCreatedAt() != null) {
				info.dateTime = splitForReturnItem.getCreatedAt();
			}
			LOGGER.info("Clawback OriginalTransaction: orderId=" + order.getEntityId() + ", resolved split from return item sku=" + returnItemSku + ", using split incrementId=" + info.incrementId + ", createdAt=" + info.dateTime);
			return info;
		}
		applyFirstSplitFallbackForClawback(info, splitOrders);
		return info;
	}

	private void applyFirstSplitFallbackForClawback(ClawbackOriginalTransactionInfo info, List<SplitSalesOrder> splitOrders) {
		if (CollectionUtils.isEmpty(splitOrders)) {
			return;
		}
		SplitSalesOrder firstSplit = splitOrders.get(0);
		if (StringUtils.isNotBlank(firstSplit.getIncrementId())) {
			info.incrementId = firstSplit.getIncrementId();
		}
		if (firstSplit.getCreatedAt() != null) {
			info.dateTime = firstSplit.getCreatedAt();
		}
	}

	private static String extractReturnItemSkuForClawback(SalesOrder order, AmastyRmaRequest rmaRequest) {
		if (CollectionUtils.isEmpty(rmaRequest.getAmastyRmaRequestItems())) {
			return null;
		}
		Integer firstOrderItemId = rmaRequest.getAmastyRmaRequestItems().iterator().next().getOrderItemId();
		SalesOrderItem orderItem = order.getSalesOrderItem().stream()
				.filter(i -> firstOrderItemId.equals(i.getItemId()))
				.findFirst()
				.orElse(null);
		if (orderItem == null) {
			return null;
		}
		SalesOrderItem parentItem = orderItem.getParentOrderItem();
		return parentItem != null ? parentItem.getSku() : orderItem.getSku();
	}

	private void applyClawbackTendersFromOrder(ShukranClawbackRequestBody body, SalesOrder order, BigDecimal orderNetPrice, BigDecimal taxFactor) {
		ObjectMapper objectMapper = new ObjectMapper();
		try {
			List<ShukranTenders> shukranTenders = objectMapper.readValue(order.getSubSalesOrder().getTenders(), new TypeReference<List<ShukranTenders>>() {
			});
			BigDecimal totalPrPrice = order.getSubSalesOrder().getShukranPrTransactionNetTotal();
			BigDecimal codFee = BigDecimal.ZERO;
			BigDecimal finalTaxFactor = (taxFactor == null || taxFactor.compareTo(BigDecimal.ZERO) == 0) ? BigDecimal.ONE : taxFactor;
			if (order.getCashOnDeliveryFee() != null && order.getCashOnDeliveryFee().compareTo(BigDecimal.ZERO) > 0) {
				codFee = order.getCashOnDeliveryFee();
				totalPrPrice = totalPrPrice.subtract(order.getCashOnDeliveryFee().divide(finalTaxFactor, 2, RoundingMode.HALF_UP));
			}
			Iterator<ShukranTenders> iterator = shukranTenders.iterator();
			while (iterator.hasNext()) {
				ShukranTenders e = iterator.next();
				BigDecimal tenderAmount = e.getTenderCode().equals(Constants.orderCredentials.getShukranTenderMappings().getCashOnDelivery()) && codFee.compareTo(BigDecimal.ZERO) > 0
						? e.getTenderAmount().subtract(codFee)
						: e.getTenderAmount();
				if (tenderAmount.compareTo(BigDecimal.ZERO) > 0) {
					e.setTenderAmount(tenderAmount.divide(totalPrPrice, 6, RoundingMode.HALF_UP)
							.multiply(orderNetPrice)
							.setScale(2, RoundingMode.HALF_UP)
							.negate());
				} else {
					iterator.remove();
				}
			}
			LOGGER.info("new shukran tenders " + objectMapper.writeValueAsString(shukranTenders));
			body.setTenders(shukranTenders);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	private static ShukranJsonDetails buildClawbackJsonExternalData(SalesOrder order, Integer totalQty) {
		String jsonDetailsString = "N";
		ShukranJsonDetails shukranJsonDetails = new ShukranJsonDetails();
		shukranJsonDetails.setVirtualCardIdentifier("");
		shukranJsonDetails.setVendorInvoiceNumber("");
		shukranJsonDetails.setInvoiceTotalQty(totalQty);
		shukranJsonDetails.setIsCoD(jsonDetailsString);
		shukranJsonDetails.setProcessReturnFlag("");
		shukranJsonDetails.setIsCancel("");
		shukranJsonDetails.setIsOfflineTransaction(jsonDetailsString);
		shukranJsonDetails.setIsRetroTransaction(jsonDetailsString);
		shukranJsonDetails.setIsExternalPartnerTransaction("");
		shukranJsonDetails.setCrossBorderFlag(order.getSubSalesOrder().getCrossBorder() != null && order.getSubSalesOrder().getCrossBorder() ? "Y" : jsonDetailsString);
		shukranJsonDetails.setPhoneNumber("");
		shukranJsonDetails.setLMSCartId(order.getSubSalesOrder().getQuoteId());
		return shukranJsonDetails;
	}

	public ShukranClawbackRequestBody createShukranClawbackBody(SalesOrder order, AmastyRmaRequest rmaRequest, List<ShukranEarnItem> transactionDetails, BigDecimal orderNetPrice, Integer totalQty, BigDecimal taxFactor) {
		ShukranClawbackRequestBody shukranClawbackRequestBody = new ShukranClawbackRequestBody();
		shukranClawbackRequestBody.setProfileId(order.getSubSalesOrder().getCustomerProfileId());
		shukranClawbackRequestBody.setTransactionTypeCode("RT");
		shukranClawbackRequestBody.setGrossAmount(null);
		shukranClawbackRequestBody.setTransactionNetTotal(orderNetPrice.setScale(2, RoundingMode.HALF_UP).negate());
		shukranClawbackRequestBody.setTransactionTotalTax(null);
		shukranClawbackRequestBody.setDiscountAmount(null);
		shukranClawbackRequestBody.setCardNumber(order.getSubSalesOrder().getShukranCardNumber());
		shukranClawbackRequestBody.setCurrencyCode(order.getOrderCurrencyCode());
		shukranClawbackRequestBody.setTransactionDateTime(timeStampData());
		shukranClawbackRequestBody.setStoreCode(order.getSubSalesOrder().getShukranStoreCode());
		shukranClawbackRequestBody.setTransactionNumber(Constants.getShukranEnrollmentCommonCode() + rmaRequest.getRmaIncId());
		shukranClawbackRequestBody.setProgramCode(Constants.getShukranProgramCode());
		shukranClawbackRequestBody.setDeviceId("");
		shukranClawbackRequestBody.setDeviceUserid("");
		ClawbackOriginalTransactionInfo orig = resolveClawbackOriginalTransaction(order, rmaRequest);
		shukranClawbackRequestBody.setOriginalTransactionNumber(Constants.getShukranEnrollmentCommonCode() + orig.incrementId);
		shukranClawbackRequestBody.setOriginalTransactionDateTime(orig.dateTime != null ? formatTimestampToIsoUtc(orig.dateTime) : null);
		shukranClawbackRequestBody.setOriginalStoreCode(order.getSubSalesOrder().getShukranStoreCode());
		shukranClawbackRequestBody.setShippingAndHandling(null);
		applyClawbackTendersFromOrder(shukranClawbackRequestBody, order, orderNetPrice, taxFactor);
		shukranClawbackRequestBody.setJsonExternalData(buildClawbackJsonExternalData(order, totalQty));
		shukranClawbackRequestBody.setTransactionDetails(transactionDetails);
		return shukranClawbackRequestBody;
	}

	public RefundShukranBurnedBody createRefundShukranBody(SalesOrder order, AmastyRmaRequest rmaRequest, Integer points, Stores store) {
		RefundShukranBurnedBody refundShukranBurnedBody = new RefundShukranBurnedBody();
		refundShukranBurnedBody.setProfileId(order.getSubSalesOrder().getCustomerProfileId());
		refundShukranBurnedBody.setAdjustmentReasonCode(Constants.orderCredentials.getShukranReturnAdjustmentReasonCode());
		refundShukranBurnedBody.setAdjustmentComment(Constants.orderCredentials.getShukranReturnAdjustmentComment().replace("{{transactionNumber}}", Constants.getShukranEnrollmentCommonCode() + order.getIncrementId()));
		refundShukranBurnedBody.setNumPoints(points);
		refundShukranBurnedBody.setActivityDate(timeStampData());
		RefundShukranBurnedBodyData refundShukranBurnedBodyData = new RefundShukranBurnedBodyData();
		refundShukranBurnedBodyData.setTerritory(store.getShukranCurrencyCode());
		refundShukranBurnedBodyData.setConcept(Constants.getShukranEnrollmentConceptCode());
		refundShukranBurnedBodyData.setStoreCode(order.getSubSalesOrder().getShukranStoreCode());
		refundShukranBurnedBodyData.setOriginalPRTxnNumber(Constants.getShukranEnrollmentCommonCode() + order.getIncrementId());
		refundShukranBurnedBodyData.setUniqueReferenceNumber(rmaRequest.getRmaIncId() + Constants.getShukranEnrollmentCommonCode());
		refundShukranBurnedBody.setJsonExternalData(refundShukranBurnedBodyData);
		return refundShukranBurnedBody;

	}


	public String timeStampData() {
		return formatTimestampToIsoUtc(new Timestamp(new Date().getTime()));
	}

	/** Formats a Timestamp to ISO-8601 UTC (yyyy-MM-dd'T'HH:mm:ss.SSS'Z') - same as TransactionDateTime. */
	private String formatTimestampToIsoUtc(Timestamp timestamp) {
		if (timestamp == null) return null;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		return sdf.format(timestamp);
	}

	public RefundPaymentInfo findPaymentMethodAndAmount(RefundPaymentInfo refundPaymentInfo) {
		refundPaymentInfo.setStatusCode("208"); //Default status code
		refundPaymentInfo.setStatusMsg("Payment method not found"); // default status message
		BigDecimal totalAmountToShowInSMS = BigDecimal.ZERO;
		String paymentMethod = null;
		AmastyRmaRequest rmaRequest = amastyRmaRequestRepository.findByRmaIncIdAndOrderId(refundPaymentInfo.getReturnIncrementId(), refundPaymentInfo.getOrderId());
		if (null != rmaRequest.getRequestId()) {
			SalesOrder order = salesOrderRepository.findByEntityId(rmaRequest.getOrderId());
			BigDecimal taxFactor = BigDecimal.valueOf(1);
			Stores store = null;
			RefundAmountObject refundAmountDetails = null;
			BigDecimal totalRefundOnlineAmount;
			BigDecimal totalRefundGiftVoucherAmount = BigDecimal.ZERO;
			String msgString = null;
			String fortId = null;
			refundAmountDetails = new RefundAmountObject();
List<SalesCreditmemo> creditMemoList = salesCreditmemoRepository.findByRmaNumber(rmaRequest.getRequestId().toString());
			if (CollectionUtils.isNotEmpty(creditMemoList)) {
				refundPaymentInfo.setStatusCode("201");
				refundPaymentInfo.setStatusMsg("Refunded already done !!");
				return refundPaymentInfo;
			}
			if (null != order) {
				if (order.getStoreId() != null) {
					store = Constants.getStoresList().stream().filter(e -> e != null &&
							org.apache.commons.lang.StringUtils.isNotEmpty(e.getStoreId()) &&
							e.getStoreId().equalsIgnoreCase(order.getStoreId().toString())).findFirst().orElse(null);
					if (store != null && store.getTaxPercentage() != null && store.getTaxPercentage().compareTo(BigDecimal.ZERO) > 0) {
						BigDecimal decimalTaxValue = store.getTaxPercentage().divide(new BigDecimal(100), 4, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
						taxFactor = taxFactor.add(decimalTaxValue);
					}
				}
				SalesOrderPayment orderPayment = order.getSalesOrderPayment().stream().findFirst().orElse(null);
				Map<String, BigDecimal> mapSkuList = new HashMap<>();
				if (null != orderPayment) {

					paymentMethod = orderPayment.getMethod();
					fortId = orderPayment.getCcTransId();
				}

				CalculateRefundAmountResponse calculateRefundAmountResponse = paymentDtfRefundHelper.calculaterefundamount(order,
						paymentMethod, rmaRequest, refundAmountDetails, mapSkuList, taxFactor, store, "device-id");

				LOGGER.info("calculateRefundAmountResponse" + calculateRefundAmountResponse);
				String calcutedRefundAmount = calculateRefundAmountResponse.getAfterCalculatedRefundAmount();

				boolean checkQty = checkreturnQtyCheck(mapSkuList);

				LOGGER.info("mapList:" + mapSkuList);

				if (!checkQty) {
					refundPaymentInfo.setStatusCode("209");
					refundPaymentInfo.setStatusMsg("invalid refund qty !!");
					return refundPaymentInfo;
				} else if ((StringUtils.isBlank(rmaRequest.getReturnIncPayfortId()) || StringUtils.isEmpty(rmaRequest.getReturnIncPayfortId())) && null != paymentMethod && new BigDecimal(calcutedRefundAmount).compareTo(BigDecimal.ZERO) == 0 && (null == refundAmountDetails.getRefundStorecreditAmount() || refundAmountDetails.getRefundStorecreditAmount().compareTo(BigDecimal.ZERO) == 0)) {
					refundPaymentInfo.setStatusCode("200");
					refundPaymentInfo.setStatusMsg("Refundable amount is zero");
					return refundPaymentInfo;
				}

				if (null != order.getGiftVoucherDiscount()
						&& order.getGiftVoucherDiscount().compareTo(BigDecimal.ZERO) != 0) {
					refundAmountDetails.setGiftVoucher(true);
					totalRefundGiftVoucherAmount = paymentDtfRefundHelper.getGiftVoucherRefundAmount(order, rmaRequest, refundAmountDetails);
					paymentDtfRefundHelper.setReturnVoucherValueInDB(order, totalRefundGiftVoucherAmount);

				}
				if (StringUtils.isNotEmpty(calculateRefundAmountResponse.getAfterRefundOnlineAmount()) && StringUtils.isNotBlank(calculateRefundAmountResponse.getAfterRefundOnlineAmount())) {
					totalAmountToShowInSMS = totalAmountToShowInSMS.add(new BigDecimal(calculateRefundAmountResponse.getAfterRefundOnlineAmount()));
				}
				if (StringUtils.isNotEmpty(calculateRefundAmountResponse.getAfterCreditAmount()) && StringUtils.isNotBlank(calculateRefundAmountResponse.getAfterCreditAmount())) {
					totalAmountToShowInSMS = totalAmountToShowInSMS.add(new BigDecimal(calculateRefundAmountResponse.getAfterCreditAmount()));
				}

				if (null != paymentMethod && totalAmountToShowInSMS.doubleValue() > 0) {
					refundPaymentInfo.setStatusCode("200");
					refundPaymentInfo.setStatusMsg("Payment method found");
					refundPaymentInfo.setPaymentMethod(paymentMethod);
					refundPaymentInfo.setTotalAmountToShowInSMS(totalAmountToShowInSMS);
					refundPaymentInfo.setCalculateRefundAmountResponse(calculateRefundAmountResponse);
					refundPaymentInfo.setOrder(order);
					refundPaymentInfo.setMapSkuList(mapSkuList);
					refundPaymentInfo.setRefundAmountDetails(refundAmountDetails);
					refundPaymentInfo.setRmaRequest(rmaRequest);
					refundPaymentInfo.setTotalRefundGiftVoucherAmount(totalRefundGiftVoucherAmount);
					refundPaymentInfo.setStore(store);
					refundPaymentInfo.setTaxFactor(taxFactor);
					refundPaymentInfo.setFortId(fortId);
				}
			}
		}

		return refundPaymentInfo;
	}

	public void payTamaraRefund(RefundPaymentInfo refundPaymentInfo) {
		//Check shukran enable or not, and do return refund
		checkShukranAndReturnRefund(refundPaymentInfo);
		CalculateRefundAmountResponse calculateRefundAmountResponse = refundPaymentInfo.getCalculateRefundAmountResponse();
		String paymentMethod = refundPaymentInfo.getPaymentMethod();
		AmastyRmaRequest rmaRequest = refundPaymentInfo.getRmaRequest();
		RefundAmountObject refundAmountDetails = refundPaymentInfo.getRefundAmountDetails();
		String msgString = null;
		RefundPaymentRespone response = new RefundPaymentRespone();
		BigDecimal totalRefundGiftVoucherAmount = refundPaymentInfo.getTotalRefundGiftVoucherAmount();
		BigDecimal totalAmountToShowInSMS = refundPaymentInfo.getTotalAmountToShowInSMS();
		Map<String, BigDecimal> mapSkuList = refundPaymentInfo.getMapSkuList();
		SalesOrder order = refundPaymentInfo.getOrder();
		LOGGER.info(" calculateRefundAmountResponse.getBeforeCalculatedRefundAmount()2" + calculateRefundAmountResponse.getBeforeCalculatedRefundAmount());
		PaymentReturnAdditioanls additionals = new PaymentReturnAdditioanls();
		additionals.setPaymentMethod(paymentMethod);
		additionals.setRmaRequest(rmaRequest);
		additionals.setReturnAmount(calculateRefundAmountResponse.getAfterRefundOnlineAmount());
		if (StringUtils.isBlank(rmaRequest.getReturnIncPayfortId()) || StringUtils.isEmpty(rmaRequest.getReturnIncPayfortId())) {
			response = paymentUtility.initiateRefund(order, additionals);
			msgString = response.getStatusMsg();
			if (StringUtils.isNotBlank(calculateRefundAmountResponse.getAfterAmastyCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getAfterAmastyCreditAmount()) && StringUtils.isNotBlank(calculateRefundAmountResponse.getAfterCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getAfterCreditAmount())) {
				refundAmountDetails.setRefundStorecreditAmount(new BigDecimal(calculateRefundAmountResponse.getAfterCreditAmount()));
				refundAmountDetails.setBaseAmastyStoreCreditAmount(new BigDecimal(calculateRefundAmountResponse.getAfterAmastyCreditAmount()));
				refundToStoreCredit(order, refundAmountDetails);
			}
		}
		refundAmountDetails.setRefundOnlineAmount(new BigDecimal(calculateRefundAmountResponse.getBeforeRefundOnlineAmount()));
		if (StringUtils.isNotBlank(calculateRefundAmountResponse.getBeforeAmastyCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getBeforeAmastyCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getBeforeCreditAmount()) && StringUtils.isNotBlank(calculateRefundAmountResponse.getBeforeCreditAmount())) {
			refundAmountDetails.setBaseAmastyStoreCreditAmount(new BigDecimal(calculateRefundAmountResponse.getBeforeAmastyCreditAmount()));
			refundAmountDetails.setRefundStorecreditAmount(new BigDecimal(calculateRefundAmountResponse.getBeforeCreditAmount()));
		}
		if (StringUtils.isNotBlank(calculateRefundAmountResponse.getBeforeRefundOnlineAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getBeforeRefundOnlineAmount())) {
			refundAmountDetails.setRefundOnlineAmount(new BigDecimal(calculateRefundAmountResponse.getBeforeRefundOnlineAmount()));
		}
		LOGGER.info(" calculateRefundAmountResponse.getBeforeCalculatedRefundAmount()2" + calculateRefundAmountResponse.getBeforeCalculatedRefundAmount());
		paymentDtfRefundHelper.createReturnRma(order, paymentMethod, calculateRefundAmountResponse.getBeforeRefundOnlineAmount(), rmaRequest
				, refundAmountDetails, mapSkuList, msgString, response, calculateRefundAmountResponse.getAfterRefundOnlineAmount(), calculateRefundAmountResponse.getAfterAmastyCreditAmount(), totalAmountToShowInSMS);
		if (null != totalRefundGiftVoucherAmount && totalRefundGiftVoucherAmount.compareTo(BigDecimal.ZERO) != 0) {
			paymentDtfRefundHelper.setReturnVoucherValueInDB(order, totalRefundGiftVoucherAmount);
		}
	}

	public void payPayfortRefund(RefundPaymentInfo refundPaymentInfo) {
		//Check shukran enable or not, and do return refund
		checkShukranAndReturnRefund(refundPaymentInfo);
		CalculateRefundAmountResponse calculateRefundAmountResponse = refundPaymentInfo.getCalculateRefundAmountResponse();
		String paymentMethod = refundPaymentInfo.getPaymentMethod();
		AmastyRmaRequest rmaRequest = refundPaymentInfo.getRmaRequest();
		RefundAmountObject refundAmountDetails = refundPaymentInfo.getRefundAmountDetails();
		String msgString = null;
		RefundPaymentRespone response = new RefundPaymentRespone();
		BigDecimal totalAmountToShowInSMS = refundPaymentInfo.getTotalAmountToShowInSMS();
		Map<String, BigDecimal> mapSkuList = refundPaymentInfo.getMapSkuList();
		SalesOrder order = refundPaymentInfo.getOrder();
		String calcutedRefundAmount = calculateRefundAmountResponse.getAfterCalculatedRefundAmount();
		LOGGER.info("In payPayfortRefund refunabale payfort amount:" + calcutedRefundAmount);
		if ((new BigDecimal(calcutedRefundAmount).compareTo(BigDecimal.ZERO) == 0)) {
			response.setStatus(true);

		} else if (StringUtils.isBlank(rmaRequest.getReturnIncPayfortId()) || StringUtils.isEmpty(rmaRequest.getReturnIncPayfortId())) {
			if (StringUtils.isNotEmpty(calculateRefundAmountResponse.getAfterRefundOnlineAmount()) && StringUtils.isNotBlank(calculateRefundAmountResponse.getAfterRefundOnlineAmount()) && new BigDecimal(calculateRefundAmountResponse.getAfterRefundOnlineAmount()).compareTo(BigDecimal.ZERO) > 0) {
				response = paymentDtfRefundHelper.payfortRefundcall(order, new BigDecimal(calculateRefundAmountResponse.getAfterRefundOnlineAmount()),
						refundPaymentInfo.getFortId(), paymentMethod);
			}
		}
		try {
			LOGGER.info("payfort response:" + mapper.writeValueAsString(response));
		} catch (JsonProcessingException e) {
			LOGGER.error("payfort refund response parse error:" + e.getMessage());
		}
		if (!response.isStatus()) {

			response.setSendSms(false);
			msgString = response.getStatusMsg();

		}
		LOGGER.info("Store Credit Amount To Be Credited1: " + calculateRefundAmountResponse.getAfterCreditAmount());
		if (StringUtils.isNotBlank(calculateRefundAmountResponse.getAfterCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getAfterCreditAmount())
				&& new BigDecimal(calculateRefundAmountResponse.getAfterCreditAmount()).compareTo(BigDecimal.ZERO) > 0 && (StringUtils.isBlank(rmaRequest.getReturnIncPayfortId()) || StringUtils.isEmpty(rmaRequest.getReturnIncPayfortId()))) {
			LOGGER.info("Store Credit Amount To Be Credited2: " + calculateRefundAmountResponse.getAfterCreditAmount());
			paymentDtfRefundHelper.addStoreCredit(order, new BigDecimal(calculateRefundAmountResponse.getAfterCreditAmount()),
					refundAmountDetails.isGiftVoucher());

		}


		if (StringUtils.isNotBlank(calculateRefundAmountResponse.getBeforeAmastyCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getBeforeAmastyCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getBeforeCreditAmount()) && StringUtils.isNotBlank(calculateRefundAmountResponse.getBeforeCreditAmount())) {
			refundAmountDetails.setBaseAmastyStoreCreditAmount(new BigDecimal(calculateRefundAmountResponse.getBeforeAmastyCreditAmount()));
			refundAmountDetails.setRefundStorecreditAmount(new BigDecimal(calculateRefundAmountResponse.getBeforeCreditAmount()));
		}
		if (StringUtils.isNotBlank(calculateRefundAmountResponse.getBeforeRefundOnlineAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getBeforeRefundOnlineAmount())) {
			refundAmountDetails.setRefundOnlineAmount(new BigDecimal(calculateRefundAmountResponse.getBeforeRefundOnlineAmount()));
		}
		LOGGER.info(" calculateRefundAmountResponse.getBeforeCalculatedRefundAmount()1" + calculateRefundAmountResponse.getBeforeCalculatedRefundAmount());
		paymentDtfRefundHelper.createReturnRma(order, paymentMethod, calculateRefundAmountResponse.getBeforeRefundOnlineAmount(), rmaRequest,
				refundAmountDetails, mapSkuList, msgString, response, calculateRefundAmountResponse.getAfterRefundOnlineAmount(), calculateRefundAmountResponse.getAfterAmastyCreditAmount(), totalAmountToShowInSMS);

	}

	public void payShukranRefund(RefundPaymentInfo refundPaymentInfo) {
		//Check shukran enable or not, and do return refund
		checkShukranAndReturnRefund(refundPaymentInfo);
		String paymentMethod = refundPaymentInfo.getPaymentMethod();
		AmastyRmaRequest rmaRequest = refundPaymentInfo.getRmaRequest();
		RefundAmountObject refundAmountDetails = refundPaymentInfo.getRefundAmountDetails();
		String msgString = null;
		RefundPaymentRespone response = new RefundPaymentRespone();
		Map<String, BigDecimal> mapSkuList = refundPaymentInfo.getMapSkuList();
		SalesOrder order = refundPaymentInfo.getOrder();
		paymentDtfRefundHelper.createReturnRma(order, paymentMethod, BigDecimal.ZERO.toString(), rmaRequest
				, refundAmountDetails, mapSkuList, msgString, response, BigDecimal.ZERO.toString(), BigDecimal.ZERO.toString(), BigDecimal.ZERO);
	}

	@Transactional
	public Boolean refundData(RefundPaymentInfo refundPaymentInfo) {
		//Check shukran enable or not, and do return refund

		AmastyRmaRequest rmaRequest = amastyRmaRequestRepository.findByRmaIncIdAndOrderId(refundPaymentInfo.getReturnIncrementId(), refundPaymentInfo.getOrderId());
		List<SalesCreditmemo> creditMemoList = salesCreditmemoRepository.findByRmaNumber(rmaRequest.getRequestId().toString());

		if (CollectionUtils.isNotEmpty(creditMemoList)) {
            return true;
		}
		SalesOrder order = salesOrderRepository.findByEntityId(refundPaymentInfo.getOrderId());
		refundPaymentInfo.setOrder(order);
		checkShukranAndReturnRefund(refundPaymentInfo);
		StoreDataAndTaxFaxtor storeDataAndTaxFaxtor = getStoreDataAndTaxFaxtor(refundPaymentInfo.getOrder());
		RefundAmountObject refundAmountDetails = new RefundAmountObject();
		Map<String, BigDecimal> mapSkuList = new HashMap<>();
		CalculateRefundAmountResponse calculateRefundAmountResponse = calculateRefundAmountResponse(refundPaymentInfo, storeDataAndTaxFaxtor.getTaxFactor(), refundAmountDetails, mapSkuList, rmaRequest, storeDataAndTaxFaxtor.getStore());
		return useCalculatedDataToRefund(calculateRefundAmountResponse, refundPaymentInfo.getOrder(), storeDataAndTaxFaxtor.getStore(), rmaRequest, refundPaymentInfo.getPaymentMethod(), mapSkuList, refundAmountDetails, refundPaymentInfo, storeDataAndTaxFaxtor.getTaxFactor());

	}

	public void findPaymentMethodAndPushToQueue(RefundPaymentInfo paymentInfo) {
		try {
			ObjectMapper objectMapper= new ObjectMapper();
			LOGGER.info("In findPaymentMethodAndPushToQueue: received paymentInfo: " + paymentInfo);
			SalesOrderPayment orderPayment = salesOrderPaymentRepository.findByParentId(paymentInfo.getOrderId());
			String paymentMethod = null;
			String fortId= null;
			Map<String, BigDecimal> mapSkuList = new HashMap<>();
			if (null != orderPayment) {

				paymentMethod = orderPayment.getMethod();
				fortId = orderPayment.getCcTransId();
			}
			paymentInfo.setPaymentMethod(paymentMethod);
			paymentInfo.setFortId(fortId);
			paymentInfo.setStatusCode("200");
			LOGGER.info("In findPaymentMethodAndPushToQueue: received paymentInfo1: " + objectMapper.writeValueAsString(paymentInfo));
			if (StringUtils.isNotEmpty(paymentMethod) && StringUtils.isNotBlank(paymentMethod)) {
				switch (paymentMethod) {
					case PaymentConstants.CASHFREE, PaymentConstants.TABBY_INSTALMENTS, PaymentConstants.TABBY_PAYLATER,
						 PaymentConstants.TAMARA_INSTALMENTS_3, PaymentConstants.TAMARA_INSTALMENTS_6:
						pubSubServiceImpl.publishReturnRefundToPubSub(omsTamaraRefundTopic, paymentInfo);
						break;
					case OrderConstants.PAYFORT_FORT_CC, OrderConstants.MD_PAYFORT,
						 OrderConstants.MD_PAYFORT_CC_VAULT, OrderConstants.APPLE_PAY:
						pubSubServiceImpl.publishReturnRefundToPubSub(omsPayfortRefundTopic, paymentInfo);
						break;
					case PaymentConstants.SHUKRAN_PAYMENT:
						pubSubServiceImpl.publishReturnRefundToPubSub(omsShukranRefundTopic, paymentInfo);
						break;
					default:
						pubSubServiceImpl.publishReturnRefundToPubSub(omsWalletRefundTopic, paymentInfo);
				}
			} else {
				LOGGER.error("In findPaymentMethodAndPushToQueue: message: " + paymentInfo.getStatusMsg());
			}
		} catch (Exception e) {
			LOGGER.error("In findPaymentMethodAndPushToQueue: message: " + e.getMessage());
		}
	}

	public CalculateRefundAmountResponse calculateRefundAmountResponse(RefundPaymentInfo refundPaymentInfo, BigDecimal taxFactor, RefundAmountObject refundAmountDetails, Map<String, BigDecimal> mapSkuList, AmastyRmaRequest rmaRequest, Stores store) {

		return paymentDtfRefundHelper.calculaterefundamount(refundPaymentInfo.getOrder(),
				refundPaymentInfo.getPaymentMethod(), rmaRequest, refundAmountDetails, mapSkuList, taxFactor, store, "");
	}

	public StoreDataAndTaxFaxtor getStoreDataAndTaxFaxtor(SalesOrder order) {
		StoreDataAndTaxFaxtor storeDataAndTaxFaxtor = new StoreDataAndTaxFaxtor();
		Stores store = Constants.getStoresList().stream().filter(e -> e != null &&
				org.apache.commons.lang.StringUtils.isNotEmpty(e.getStoreId()) &&
				e.getStoreId().equalsIgnoreCase(order.getStoreId().toString())).findFirst().orElse(null);

		BigDecimal taxFactor = new BigDecimal(1);
		if (store != null && store.getTaxPercentage() != null && store.getTaxPercentage().compareTo(BigDecimal.ZERO) > 0) {
			BigDecimal decimalTaxValue = store.getTaxPercentage().divide(new BigDecimal(100), 4, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
			taxFactor = taxFactor.add(decimalTaxValue);
		}
		storeDataAndTaxFaxtor.setStore(store);
		storeDataAndTaxFaxtor.setTaxFactor(taxFactor);
		return storeDataAndTaxFaxtor;
	}

	public void checkShukranAndReturnRefund(RefundPaymentInfo refundPaymentInfo) {
		CalculateRefundAmountResponse calculateRefundAmountResponse = refundPaymentInfo.getCalculateRefundAmountResponse();
		String paymentMethod = refundPaymentInfo.getPaymentMethod();
		AmastyRmaRequest rmaRequest = refundPaymentInfo.getRmaRequest();
		SalesOrder order = refundPaymentInfo.getOrder();
		Stores store = refundPaymentInfo.getStore();

		if (store != null && store.getIsShukranEnable() && order.getSubSalesOrder() != null && order.getSubSalesOrder().getShukranLinked() && StringUtils.isNotEmpty(order.getSubSalesOrder().getCustomerProfileId()) && StringUtils.isNotBlank(order.getSubSalesOrder().getCustomerProfileId()) && StringUtils.isNotBlank(order.getSubSalesOrder().getShukranCardNumber()) && StringUtils.isNotEmpty(order.getSubSalesOrder().getShukranCardNumber())) {
			try {
				String earnResponse = "Clawback Api Failed";
				if (calculateRefundAmountResponse.getOrderNetPrice() != null && calculateRefundAmountResponse.getOrderNetPrice().compareTo(BigDecimal.ZERO) > 0 && !rmaRequest.getShukranRtSuccessful()) {
					if (paymentMethod != null) {
						ShukranClawbackRequestBody shukranClawbackRequestBody = createShukranClawbackBody(order, rmaRequest, calculateRefundAmountResponse.getTransactionDetails(), calculateRefundAmountResponse.getOrderNetPrice(), calculateRefundAmountResponse.getTotalQty(), refundPaymentInfo.getTaxFactor());
						earnResponse = commonService.clawbackShukranEarned(shukranClawbackRequestBody);
						LOGGER.info("earn response" + earnResponse);
					} else {
						earnResponse = "clawback api response passed";
					}
				}
				rmaRequest.setShukranRtSuccessful(StringUtils.isNotEmpty(earnResponse) && StringUtils.isNotBlank(earnResponse) && earnResponse.equals("clawback api response passed"));
			} catch (Exception e) {
				LOGGER.info("Error While Clawback Data " + e.getMessage());
			}
			try {
				if (order.getSubSalesOrder().getTotalShukranCoinsBurned() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0 && calculateRefundAmountResponse.getAfterCalculatedShukranPoints() > 0 && !rmaRequest.getShukranRefundSuccessful()) {
					RefundShukranBurnedBody refundShukranBurnedBody = createRefundShukranBody(order, rmaRequest, calculateRefundAmountResponse.getAfterCalculatedShukranPoints(), store);
					String burnResponse = commonService.refundShukranBurned(refundShukranBurnedBody);
					LOGGER.info("Burn response" + burnResponse);
					if (StringUtils.isNotBlank(burnResponse) && StringUtils.isNotEmpty(burnResponse) && burnResponse.equalsIgnoreCase("refund burned point api passed")) {
						rmaRequest.setShukranRefundSuccessful(true);
						BigDecimal points = BigDecimal.valueOf(calculateRefundAmountResponse.getAfterCalculatedShukranPoints());
						BigDecimal pointsValueInCurrency = points.multiply(store.getShukranPointConversion()).setScale(2, RoundingMode.HALF_UP);
						BigDecimal pointsValueInBaseCurreny = points.multiply(BigDecimal.valueOf(0.05)).setScale(2, RoundingMode.HALF_UP);
						ShukranLedgerData shukranLedgerData = orderHelperService.createShukranLedgerData(order, points, pointsValueInCurrency, pointsValueInBaseCurreny, store, true, "Refund Burned Shukran Points");
						eASServiceImpl.updateShukranLedger(shukranLedgerData);
					}
				}
			} catch (Exception e) {
				LOGGER.info("Error While Refund Shukran Data " + e.getMessage());
			}
			amastyRmaRequestRepository.saveAndFlush(rmaRequest);
		}
	}

	public Boolean useCalculatedDataToRefund(CalculateRefundAmountResponse calculateRefundAmountResponse, SalesOrder order, Stores store, AmastyRmaRequest rmaRequest, String paymentMethod, Map<String, BigDecimal> mapSkuList, RefundAmountObject refundAmountDetails, RefundPaymentInfo refundPaymentInfo, BigDecimal taxFactor) {
		String calcutedRefundAmount = calculateRefundAmountResponse.getAfterCalculatedRefundAmount();

		BigDecimal totalRefundGiftVoucherAmount = BigDecimal.ZERO;
		String msgString = null;

		boolean checkQty = checkreturnQtyCheck(mapSkuList);

		RefundPaymentRespone response = new RefundPaymentRespone();
		LOGGER.info("mapList:" + mapSkuList);

		if (!checkQty) {
			response.setStatus(false);
			response.setStatusCode("209");
			response.setRequestId(rmaRequest.getRmaIncId());
			response.setStatusMsg("invalid refund qty !!");
			response.setSendSms(false);
			return true;

		} else if ((StringUtils.isBlank(rmaRequest.getReturnIncPayfortId()) || StringUtils.isEmpty(rmaRequest.getReturnIncPayfortId())) && null != paymentMethod && new BigDecimal(calcutedRefundAmount).compareTo(BigDecimal.ZERO) == 0 && (null == refundAmountDetails.getRefundStorecreditAmount() || refundAmountDetails.getRefundStorecreditAmount().compareTo(BigDecimal.ZERO) == 0)) {

			response.setStatus(true);
			response.setStatusCode("200");
			response.setStatusMsg("Refundable amount is zero");
			response.setSendSms(false);
			return true;
		}

		if (null != order.getGiftVoucherDiscount()
				&& order.getGiftVoucherDiscount().compareTo(BigDecimal.ZERO) != 0) {
			refundAmountDetails.setGiftVoucher(true);
			totalRefundGiftVoucherAmount = paymentDtfRefundHelper.getGiftVoucherRefundAmount(order, rmaRequest, refundAmountDetails);
			paymentDtfRefundHelper.setReturnVoucherValueInDB(order, totalRefundGiftVoucherAmount);
		}
		BigDecimal totalAmountToShowInSMS = BigDecimal.ZERO;
		if (StringUtils.isNotEmpty(calculateRefundAmountResponse.getAfterRefundOnlineAmount()) && StringUtils.isNotBlank(calculateRefundAmountResponse.getAfterRefundOnlineAmount())) {
			totalAmountToShowInSMS = totalAmountToShowInSMS.add(new BigDecimal(calculateRefundAmountResponse.getAfterRefundOnlineAmount()));
		}
		if (StringUtils.isNotEmpty(calculateRefundAmountResponse.getAfterCreditAmount()) && StringUtils.isNotBlank(calculateRefundAmountResponse.getAfterCreditAmount())) {
			totalAmountToShowInSMS = totalAmountToShowInSMS.add(new BigDecimal(calculateRefundAmountResponse.getAfterCreditAmount()));
		}

		if (store != null && store.getIsShukranEnable() && order.getSubSalesOrder() != null && order.getSubSalesOrder().getShukranLinked() && StringUtils.isNotEmpty(order.getSubSalesOrder().getCustomerProfileId()) && StringUtils.isNotBlank(order.getSubSalesOrder().getCustomerProfileId()) && StringUtils.isNotBlank(order.getSubSalesOrder().getShukranCardNumber()) && StringUtils.isNotEmpty(order.getSubSalesOrder().getShukranCardNumber())) {
			try {
				String earnResponse = "Clawback Api Failed";

				if (calculateRefundAmountResponse.getOrderNetPrice() != null && calculateRefundAmountResponse.getOrderNetPrice().compareTo(BigDecimal.ZERO) > 0 && !rmaRequest.getShukranRtSuccessful() && order.getSubSalesOrder().getShukranPrSuccessful() != null && order.getSubSalesOrder().getShukranPrSuccessful().equals(1) && order.getSubSalesOrder().getShukranPrTransactionNetTotal() != null && order.getSubSalesOrder().getShukranPrTransactionNetTotal().compareTo(BigDecimal.ZERO) > 0) {
					if (paymentMethod != null) {
						ShukranClawbackRequestBody shukranClawbackRequestBody = createShukranClawbackBody(order, rmaRequest, calculateRefundAmountResponse.getTransactionDetails(), calculateRefundAmountResponse.getOrderNetPrice(), calculateRefundAmountResponse.getTotalQty(), taxFactor);
						earnResponse = commonService.clawbackShukranEarned(shukranClawbackRequestBody);
						LOGGER.info("earn response" + earnResponse);
					} else {
						earnResponse = "clawback api response passed";
					}
				}
				rmaRequest.setShukranRtSuccessful(StringUtils.isNotEmpty(earnResponse) && StringUtils.isNotBlank(earnResponse) && earnResponse.equals("clawback api response passed"));
			} catch (Exception e) {
				LOGGER.info("Error While Clawback Data " + e.getMessage());
			}
			try {
				if (order.getSubSalesOrder().getTotalShukranCoinsBurned() != null && order.getSubSalesOrder().getTotalShukranCoinsBurned().compareTo(BigDecimal.ZERO) > 0 && calculateRefundAmountResponse.getAfterCalculatedShukranPoints() > 0 && !rmaRequest.getShukranRefundSuccessful() && order.getSubSalesOrder().getShukranLocked() != null && order.getSubSalesOrder().getShukranLocked().equals(0)) {
					RefundShukranBurnedBody refundShukranBurnedBody = createRefundShukranBody(order, rmaRequest, calculateRefundAmountResponse.getAfterCalculatedShukranPoints(), store);
					String burnResponse = commonService.refundShukranBurned(refundShukranBurnedBody);
					LOGGER.info("Burn response" + burnResponse);
					if (StringUtils.isNotBlank(burnResponse) && StringUtils.isNotEmpty(burnResponse) && burnResponse.equalsIgnoreCase("refund burned point api passed")) {
						rmaRequest.setShukranRefundSuccessful(true);
						BigDecimal points = BigDecimal.valueOf(calculateRefundAmountResponse.getAfterCalculatedShukranPoints());
						BigDecimal pointsValueInCurrency = points.multiply(store.getShukranPointConversion()).setScale(2, RoundingMode.HALF_UP);
						BigDecimal pointsValueInBaseCurreny = points.multiply(BigDecimal.valueOf(0.05)).setScale(2, RoundingMode.HALF_UP);
						ShukranLedgerData shukranLedgerData = orderHelperService.createShukranLedgerData(order, points, pointsValueInCurrency, pointsValueInBaseCurreny, store, true, "Refund Burned Shukran Points");
						eASServiceImpl.updateShukranLedger(shukranLedgerData);
					}
				}
			} catch (Exception e) {
				LOGGER.info("Error While Refund Shukran Data " + e.getMessage());
			}
			amastyRmaRequestRepository.saveAndFlush(rmaRequest);
		}


		if (null != paymentMethod && OrderConstants.checkPaymentMethod(paymentMethod)) {
			LOGGER.info("refunabale payfort amount:" + calcutedRefundAmount);
			if ((new BigDecimal(calcutedRefundAmount).compareTo(BigDecimal.ZERO) == 0)) {
				response.setStatus(true);

			} else if (StringUtils.isBlank(rmaRequest.getReturnIncPayfortId()) || StringUtils.isEmpty(rmaRequest.getReturnIncPayfortId())) {
				if (StringUtils.isNotEmpty(calculateRefundAmountResponse.getAfterRefundOnlineAmount()) && StringUtils.isNotBlank(calculateRefundAmountResponse.getAfterRefundOnlineAmount()) && new BigDecimal(calculateRefundAmountResponse.getAfterRefundOnlineAmount()).compareTo(BigDecimal.ZERO) > 0) {

					response = paymentDtfRefundHelper.payfortRefundcall(order, new BigDecimal(calculateRefundAmountResponse.getAfterRefundOnlineAmount()),
							refundPaymentInfo.getFortId(), paymentMethod);
				}


			}


			if (!response.isStatus()) {

				response.setSendSms(false);
				msgString = response.getStatusMsg();

			}
			LOGGER.info("Store Credit Amount To Be Credited1: " + calculateRefundAmountResponse.getAfterCreditAmount());
			if (StringUtils.isNotBlank(calculateRefundAmountResponse.getAfterCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getAfterCreditAmount())
					&& new BigDecimal(calculateRefundAmountResponse.getAfterCreditAmount()).compareTo(BigDecimal.ZERO) > 0 && (StringUtils.isBlank(rmaRequest.getReturnIncPayfortId()) || StringUtils.isEmpty(rmaRequest.getReturnIncPayfortId()))) {
				LOGGER.info("Store Credit Amount To Be Credited2: " + calculateRefundAmountResponse.getAfterCreditAmount());
				paymentDtfRefundHelper.addStoreCredit(order, new BigDecimal(calculateRefundAmountResponse.getAfterCreditAmount()),
						refundAmountDetails.isGiftVoucher());

			}


			if (StringUtils.isNotBlank(calculateRefundAmountResponse.getBeforeAmastyCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getBeforeAmastyCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getBeforeCreditAmount()) && StringUtils.isNotBlank(calculateRefundAmountResponse.getBeforeCreditAmount())) {
				refundAmountDetails.setBaseAmastyStoreCreditAmount(new BigDecimal(calculateRefundAmountResponse.getBeforeAmastyCreditAmount()));
				refundAmountDetails.setRefundStorecreditAmount(new BigDecimal(calculateRefundAmountResponse.getBeforeCreditAmount()));
			}
			if (StringUtils.isNotBlank(calculateRefundAmountResponse.getBeforeRefundOnlineAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getBeforeRefundOnlineAmount())) {
				refundAmountDetails.setRefundOnlineAmount(new BigDecimal(calculateRefundAmountResponse.getBeforeRefundOnlineAmount()));
			}
			LOGGER.info(" calculateRefundAmountResponse.getBeforeCalculatedRefundAmount()1" + calculateRefundAmountResponse.getBeforeCalculatedRefundAmount());
			paymentDtfRefundHelper.createReturnRma(order, paymentMethod, calculateRefundAmountResponse.getBeforeRefundOnlineAmount(), rmaRequest,
					refundAmountDetails, mapSkuList, msgString, response, calculateRefundAmountResponse.getAfterRefundOnlineAmount(), calculateRefundAmountResponse.getAfterAmastyCreditAmount(), totalAmountToShowInSMS);

			return true;


		} else if (null != paymentMethod && (OrderConstants.checkBNPLPaymentMethods(paymentMethod) || PaymentConstants.CASHFREE.equalsIgnoreCase(paymentMethod))) {
			LOGGER.info(" calculateRefundAmountResponse.getBeforeCalculatedRefundAmount()2" + calculateRefundAmountResponse.getBeforeCalculatedRefundAmount());
			PaymentReturnAdditioanls additionals = new PaymentReturnAdditioanls();
			additionals.setPaymentMethod(paymentMethod);
			additionals.setRmaRequest(rmaRequest);
			additionals.setReturnAmount(calculateRefundAmountResponse.getAfterRefundOnlineAmount());
			if (StringUtils.isBlank(rmaRequest.getReturnIncPayfortId()) || StringUtils.isEmpty(rmaRequest.getReturnIncPayfortId())) {
				response = paymentUtility.initiateRefund(order, additionals);
				msgString = response.getStatusMsg();
				if (StringUtils.isNotBlank(calculateRefundAmountResponse.getAfterAmastyCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getAfterAmastyCreditAmount()) && StringUtils.isNotBlank(calculateRefundAmountResponse.getAfterCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getAfterCreditAmount())) {
					refundAmountDetails.setRefundStorecreditAmount(new BigDecimal(calculateRefundAmountResponse.getAfterCreditAmount()));
					refundAmountDetails.setBaseAmastyStoreCreditAmount(new BigDecimal(calculateRefundAmountResponse.getAfterAmastyCreditAmount()));
					refundToStoreCredit(order, refundAmountDetails);
				}
			}
			refundAmountDetails.setRefundOnlineAmount(new BigDecimal(calculateRefundAmountResponse.getBeforeRefundOnlineAmount()));
			if (StringUtils.isNotBlank(calculateRefundAmountResponse.getBeforeAmastyCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getBeforeAmastyCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getBeforeCreditAmount()) && StringUtils.isNotBlank(calculateRefundAmountResponse.getBeforeCreditAmount())) {
				refundAmountDetails.setBaseAmastyStoreCreditAmount(new BigDecimal(calculateRefundAmountResponse.getBeforeAmastyCreditAmount()));
				refundAmountDetails.setRefundStorecreditAmount(new BigDecimal(calculateRefundAmountResponse.getBeforeCreditAmount()));
			}
			if (StringUtils.isNotBlank(calculateRefundAmountResponse.getBeforeRefundOnlineAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getBeforeRefundOnlineAmount())) {
				refundAmountDetails.setRefundOnlineAmount(new BigDecimal(calculateRefundAmountResponse.getBeforeRefundOnlineAmount()));
			}
			LOGGER.info(" calculateRefundAmountResponse.getBeforeCalculatedRefundAmount()2" + calculateRefundAmountResponse.getBeforeCalculatedRefundAmount());
			paymentDtfRefundHelper.createReturnRma(order, paymentMethod, calculateRefundAmountResponse.getBeforeRefundOnlineAmount(), rmaRequest
					, refundAmountDetails, mapSkuList, msgString, response, calculateRefundAmountResponse.getAfterRefundOnlineAmount(), calculateRefundAmountResponse.getAfterAmastyCreditAmount(), totalAmountToShowInSMS);
			if (null != totalRefundGiftVoucherAmount && totalRefundGiftVoucherAmount.compareTo(BigDecimal.ZERO) != 0) {
				paymentDtfRefundHelper.setReturnVoucherValueInDB(order, totalRefundGiftVoucherAmount);
			}

			return true;
		} else if (paymentMethod != null && paymentMethod.equalsIgnoreCase(PaymentCodeENUM.SHUKRAN_PAYMENT.getValue())) {
			paymentDtfRefundHelper.createReturnRma(order, paymentMethod, BigDecimal.ZERO.toString(), rmaRequest
					, refundAmountDetails, mapSkuList, msgString, response, BigDecimal.ZERO.toString(), BigDecimal.ZERO.toString(), BigDecimal.ZERO);
			return true;
		} else {
			if (null != totalRefundGiftVoucherAmount && totalRefundGiftVoucherAmount.compareTo(BigDecimal.ZERO) != 0) {
				paymentDtfRefundHelper.setReturnVoucherValueInDB(order, totalRefundGiftVoucherAmount);
				refundAmountDetails.setRefundGiftVoucherAmount(totalRefundGiftVoucherAmount);

			}


			if (Regions.INDIA.equals(regionValue)) {
				if (PaymentConstants.CASHFREE.equalsIgnoreCase(rmaRequest.getRmaPaymentMethod())) {
					PaymentReturnAdditioanls additionals = new PaymentReturnAdditioanls();
					additionals.setPaymentMethod(paymentMethod);
					additionals.setRmaRequest(rmaRequest);
					additionals.setReturnAmount(calcutedRefundAmount);
					response = paymentUtility.initiateRefund(order, additionals);
					msgString = response.getStatusMsg();
				} else if (PaymentConstants.FREE.equalsIgnoreCase(rmaRequest.getRmaPaymentMethod()) && (StringUtils.isBlank(rmaRequest.getReturnIncPayfortId()) || StringUtils.isEmpty(rmaRequest.getReturnIncPayfortId()))) {
					refundToStoreCredit(order, refundAmountDetails);
				}
			} else {
				if (StringUtils.isNotBlank(calculateRefundAmountResponse.getAfterAmastyCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getAfterAmastyCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getAfterCreditAmount()) && StringUtils.isNotBlank(calculateRefundAmountResponse.getAfterCreditAmount())) {
					refundAmountDetails.setBaseAmastyStoreCreditAmount(new BigDecimal(calculateRefundAmountResponse.getAfterAmastyCreditAmount()));
					refundAmountDetails.setRefundStorecreditAmount(new BigDecimal(calculateRefundAmountResponse.getAfterCreditAmount()));
				}
				LOGGER.info(refundAmountDetails);

				if (StringUtils.isBlank(rmaRequest.getReturnIncPayfortId()) || StringUtils.isEmpty(rmaRequest.getReturnIncPayfortId())) {
					refundToStoreCredit(order, refundAmountDetails);
				}
			}
			if (StringUtils.isNotBlank(calculateRefundAmountResponse.getBeforeAmastyCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getBeforeAmastyCreditAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getBeforeCreditAmount()) && StringUtils.isNotBlank(calculateRefundAmountResponse.getBeforeCreditAmount())) {
				refundAmountDetails.setBaseAmastyStoreCreditAmount(new BigDecimal(calculateRefundAmountResponse.getBeforeAmastyCreditAmount()).setScale(2, RoundingMode.HALF_UP));
				refundAmountDetails.setRefundStorecreditAmount(new BigDecimal(calculateRefundAmountResponse.getBeforeCreditAmount()).setScale(2, RoundingMode.HALF_UP));
			}
			if (StringUtils.isNotBlank(calculateRefundAmountResponse.getBeforeRefundOnlineAmount()) && StringUtils.isNotEmpty(calculateRefundAmountResponse.getBeforeRefundOnlineAmount())) {
				refundAmountDetails.setRefundOnlineAmount(new BigDecimal(calculateRefundAmountResponse.getBeforeRefundOnlineAmount()).setScale(2, RoundingMode.HALF_UP));
			}
			LOGGER.info(" calculateRefundAmountResponse.getBeforeCalculatedRefundAmount()3" + calculateRefundAmountResponse.getBeforeCalculatedRefundAmount());
			paymentDtfRefundHelper.createReturnRma(order, paymentMethod, calculateRefundAmountResponse.getBeforeCalculatedRefundAmount(), rmaRequest
					, refundAmountDetails, mapSkuList, msgString, response, calculateRefundAmountResponse.getAfterRefundOnlineAmount(), calculateRefundAmountResponse.getAfterAmastyCreditAmount(), totalAmountToShowInSMS);
			return true;
		}

	}

	private boolean isPayfortIdBlank(String payfortId) {
		return StringUtils.isBlank(payfortId);
	}

	private boolean isZero(String amount) {
		if (StringUtils.isBlank(amount)) {
		}
		try {
			return new BigDecimal(amount).compareTo(BigDecimal.ZERO) == 0;
		} catch (NumberFormatException e) {
			return true;
		}
	}

	private boolean isZero(BigDecimal amount) {
		return amount == null || amount.compareTo(BigDecimal.ZERO) == 0;
	}

	private boolean isZeroOrBlank(String value) {
		if (StringUtils.isBlank(value)) {
			return true;
		}
		try {
			return new BigDecimal(value).compareTo(BigDecimal.ZERO) <= 0;
		} catch (NumberFormatException e) {
			return true;
		}
	}

	@Override
	@Transactional
	public GetShipmentV3Response getSellerShipmentV3(String orderCode, String shipmentCode) {
		GetShipmentV3Response response = new GetShipmentV3Response();
		GetShipmentV3ResponseBody responseBody = new GetShipmentV3ResponseBody();

		// This method is specifically for seller orders
		SplitSellerOrder splitSellerOrder = splitSellerOrderRepository.findByIncrementId(orderCode);
		SplitSellerShipment splitSellerShipment = splitSellerShipmentRepository.findByIncrementId(shipmentCode);
		
		SalesOrder order = null;
		if (splitSellerOrder != null) {
			order = splitSellerOrder.getSalesOrder();
		}

		GetShipmentV3Request navikRequest = new GetShipmentV3Request();

		if (null == order) {
			response.setHasError(true);
			response.setErrorMessage("Invalid seller order request");
			return response;
		} else if (null == splitSellerShipment) {
			response.setHasError(true);
			response.setErrorMessage("Seller shipment has not generated");
			return response;
		}

		try {
			ResponseEntity<NavikResponse> navikResponse;
			MpsOrderCreateRequest mpsRequest = buildFirstMileOrderCreateRequest(order, splitSellerShipment, Constants.orderCredentials.getNavik(), null, splitSellerOrder);
			ResponseEntity<MpsOrderCreateResponse> mpsResponse = createFirstMileShipmentWithBeta(mpsRequest);
			LOGGER.info("Alpha first mile response body:" + mapper.writeValueAsString(mpsResponse.getBody()));
			// Store first mile response data to pack details
			storeFirstMileResponseToPackDetailsForSeller(splitSellerShipment, mpsResponse);
			navikResponse = transformMpsResponseToNavikResponse(mpsResponse);
			return processForwardShipmentResponseForSeller(responseBody, order, splitSellerShipment, navikResponse, splitSellerOrder);

		} catch (RestClientException | JsonProcessingException e) {
			LOGGER.error("exception occurred during navik call for first mile:" + e.getMessage());
			response.setHasError(true);
			if(splitSellerOrder != null) {
				splitSellerOrder.setAwbFailed(1);
				splitSellerOrderRepository.save(splitSellerOrder);
			}
			response.setErrorMessage(e.getMessage());
		}

		return response;
	}


	// Seller-specific helper methods
	private void setShipmentDetailsForSeller(SalesOrder order, GetShipmentV3Request navikRequest,
									Navikinfos navikInfos, SplitSellerOrder splitSellerOrder) {
		ShipmentDetails shipmentDetails = new ShipmentDetails();
		shipmentDetails.setWeight("0.1");
		shipmentDetails.setBreadth("10");
		shipmentDetails.setHeight("10");
		shipmentDetails.setLength("10");
		BigDecimal grandToalInvoiced = new BigDecimal(0);

		// For seller orders, get invoice from split sales order
		SalesInvoice salesInvoice = splitSellerOrder.getSplitOrder().getSplitSalesInvoices().stream().findFirst().orElse(null);
		if (null != salesInvoice) {
			if (null != order.getGrandTotal()) {
				grandToalInvoiced = salesInvoice.getGrandTotal();
			}
			if (null != order.getAmstorecreditAmount()) {
				grandToalInvoiced = grandToalInvoiced.add(salesInvoice.getAmstorecreditAmount());
			}
			shipmentDetails.setInvoice_value(grandToalInvoiced.toString());
		}
        
        SalesOrderPayment salesOrderPayment = order.getSalesOrderPayment().stream().findFirst().orElse(null);
        if (salesOrderPayment != null) {
            if (salesOrderPayment.getMethod().equalsIgnoreCase(OrderConstants.PAYMENT_METHOD_COD)) {
                shipmentDetails.setCod_value(grandToalInvoiced.toString());
            } else {
                shipmentDetails.setCod_value("0");
            }
        } else {
            shipmentDetails.setCod_value("0");
        }

		navikRequest.setShipmentDetails(shipmentDetails);
	}

	/**
	 * Build first mile order create request
	 * @param order
	 * @param splitSellerShipment
	 * @param navikDetails
	 * @param infoName
	 * @param splitSellerOrder
	 * @return
	 */
	private MpsOrderCreateRequest buildFirstMileOrderCreateRequest(SalesOrder order, SplitSellerShipment splitSellerShipment, 
			NavikDetails navikDetails, String infoName, SplitSellerOrder splitSellerOrder) {
		MpsOrderCreateRequest mpsRequest = new MpsOrderCreateRequest();
		
		// Set basic order information
		mpsRequest.setOrderRefNumber(splitSellerOrder.getIncrementId());
		
		// Use apparel_deliver_info values if available, otherwise fallback to order details
		if (navikDetails != null && navikDetails.getDeliverInfo() != null) {
			DeliverInfo deliverInfo = navikDetails.getDeliverInfo();
			mpsRequest.setCustomerName(deliverInfo.getCustomerName() != null ? deliverInfo.getCustomerName() : "Styli_Riyadh");
			mpsRequest.setCustomerPhone(deliverInfo.getCustomerPhone() != null ? deliverInfo.getCustomerPhone() : 	"+966555555555");
			mpsRequest.setCustomerEmail("styli@styli.com");
			mpsRequest.setCustomerAddress(deliverInfo.getCustomerAddress() != null ? deliverInfo.getCustomerAddress() : "Styli_Riyadh");
			mpsRequest.setCustomerCity(deliverInfo.getCustomerCity() != null ? deliverInfo.getCustomerCity() : "Riyadh");
			mpsRequest.setCustomerPostalCode(deliverInfo.getCustomerPostalCode() != null ? deliverInfo.getCustomerPostalCode() : "12345");
		}
		
		mpsRequest.setOrderType("PREPAID");
		mpsRequest.setInvoiceValue(BigDecimal.ZERO.doubleValue());
		mpsRequest.setCodValue(BigDecimal.ZERO.doubleValue());
		mpsRequest.setDeliveryType("FORWARD");
		mpsRequest.setCodCurrencyCode(order.getOrderCurrencyCode() != null ? order.getOrderCurrencyCode() : "SAR");
		// Set currency and invoice information
		mpsRequest.setInvoiceCurrencyCode(order.getOrderCurrencyCode() != null ? order.getOrderCurrencyCode() : "SAR");
		
		// Set invoice date from actual sales invoice creation date
		SalesInvoice salesInvoice = null;
		salesInvoice = order.getSalesInvoices().stream().findFirst().orElse(null);
		
		if (salesInvoice != null && salesInvoice.getCreatedAt() != null) {
			mpsRequest.setInvoiceDate(new SimpleDateFormat("dd/MM/yyyy").format(salesInvoice.getCreatedAt()));
		} else {
			mpsRequest.setInvoiceDate(new SimpleDateFormat("dd/MM/yyyy").format(new Date()));
		}
		
		// Set invoice number
		if (salesInvoice != null && salesInvoice.getIncrementId() != null) {
			mpsRequest.setInvoiceNumber(salesInvoice.getIncrementId());
		} else {
			mpsRequest.setInvoiceNumber(order.getIncrementId());
		}
		// Set box count for forward shipments
		List<SellerShipmentPackDetails> packDetails = sellerShipmentPackDetailsRepository.findByShipmentId(splitSellerShipment.getEntityId());
		mpsRequest.setBoxCount(packDetails != null ? packDetails.size() : 1);
		mpsRequest.setIsMps(mpsRequest.getBoxCount() > 1 ? true : false);
		// Get pickup info name from seller inventory mapping based on sellerId and warehouseId
		String pickupInfoName = getPickupInfoNameBySellerAndWarehouse(splitSellerOrder.getSellerId(), splitSellerOrder.getWarehouseId());
		if (pickupInfoName != null) {
			mpsRequest.setPickupInfoName(pickupInfoName);
		}
		mpsRequest.setReturnInfoName(navikDetails.getStyliReturnInfoName());
		// Set forward shipment details
		mpsRequest.setShipmentDetails(buildFirstMileShipmentDetailsForSeller(order, splitSellerShipment, packDetails, navikDetails.getDropOffDetails().stream().findFirst().orElse(null)));
		
		return mpsRequest;
	}

	private List<MpsShipmentDetail> buildFirstMileShipmentDetailsForSeller(SalesOrder order, SplitSellerShipment splitSellerShipment, List<SellerShipmentPackDetails> packDetails, Navikinfos navikInfos) {
		List<MpsShipmentDetail> shipmentDetails = new ArrayList<>();
		
		// Get all shipment items once to avoid multiple repository calls
		List<SplitSellerShipmentItem> shipmentItems = splitSellerShipmentItemRepository.findByParentId(splitSellerShipment.getEntityId());
		
		if (packDetails != null && !packDetails.isEmpty()) {
			// Use pack details for dimensions
			for (SellerShipmentPackDetails packDetail : packDetails) {
				MpsShipmentDetail detail = new MpsShipmentDetail();
				detail.setId(packDetail.getBoxCode());
				detail.setWeight(packDetail.getWeight() != null ? packDetail.getWeight().intValue() : 1.00);
				detail.setBreadth(packDetail.getBreadth() != null ? packDetail.getBreadth().intValue() : 10.00);
				detail.setHeight(packDetail.getHeight() != null ? packDetail.getHeight().intValue() : 10.00);
				detail.setLength(packDetail.getLength() != null ? packDetail.getLength().intValue() : 10.00);
				
				// Create item details from pack details items
				List<SellerShipmentPackDetailsItem> packDetailsItems = sellerShipmentPackDetailsItemRepository.findByPackDetailsId(packDetail.getEntityId());
				List<MpsShipmentItem> mpsItems = new ArrayList<>();
				
				if (packDetailsItems != null && !packDetailsItems.isEmpty()) {
					// Use pack details items for SKU and quantity
					for (SellerShipmentPackDetailsItem packItem : packDetailsItems) {
						MpsShipmentItem mpsItem = new MpsShipmentItem();
						
						// Find SplitSellerShipmentItem data for this SKU from already loaded data
						SplitSellerShipmentItem shipmentItem = shipmentItems.stream()
								.filter(item -> item.getSku() != null && item.getSku().equals(packItem.getClientSkuId()))
								.findFirst()
								.orElse(null);
						
						// Set SKU description from SplitSellerShipmentItem
						if (shipmentItem != null) {
							mpsItem.setSkuDescription(navikInfos.getDescriptionEn() != null ? navikInfos.getDescriptionEn() :shipmentItem.getName());
							mpsItem.setPrice(shipmentItem.getPrice() != null ? shipmentItem.getPrice().doubleValue() : 0.0);
						} else {
							mpsItem.setSkuDescription(navikInfos.getDescriptionEn() != null ? navikInfos.getDescriptionEn() : "NA");
							mpsItem.setPrice(0.0);
						}
						
						mpsItem.setQuantity(packItem.getCount() != null ? packItem.getCount() : 1);
						mpsItem.setSku(packItem.getClientSkuId());
						mpsItems.add(mpsItem);
					}
				} 
				detail.setItems(mpsItems);
				shipmentDetails.add(detail);
			}
		} else {
			// Fallback to shipment items if no pack details found
			if (splitSellerShipment.getSplitSellerShipmentItems() != null && !splitSellerShipment.getSplitSellerShipmentItems().isEmpty()) {
				MpsShipmentDetail detail = new MpsShipmentDetail();
				detail.setId("BOX1");
				detail.setWeight(1.00);
				detail.setBreadth(20.00);
				detail.setHeight(10.00);
				detail.setLength(30.00);
				
				List<MpsShipmentItem> mpsItems = new ArrayList<>();
				for (SplitSellerShipmentItem item : splitSellerShipment.getSplitSellerShipmentItems()) {
					MpsShipmentItem mpsItem = new MpsShipmentItem();
					mpsItem.setSkuDescription(item.getName() != null ? item.getName() : "NA");
					mpsItem.setQuantity(item.getQuantity() != null ? item.getQuantity().intValue() : 1);
					mpsItem.setPrice(item.getPrice() != null ? item.getPrice().doubleValue() : 0.0);
					mpsItem.setSku(item.getSku());
					mpsItems.add(mpsItem);
					break;
				}
				detail.setItems(mpsItems);
				shipmentDetails.add(detail);
			}
		}
		
		return shipmentDetails;
	}

	private void storeFirstMileResponseToPackDetailsForSeller(SplitSellerShipment splitSellerShipment, ResponseEntity<MpsOrderCreateResponse> mpsResponse) {
		try {
			if (mpsResponse.getBody() != null && mpsResponse.getBody().getData() != null && !mpsResponse.getBody().getData().isEmpty()) {
				MpsOrderCreateResponse.MpsOrderData firstData = mpsResponse.getBody().getData().get(0);
				String alphaAwb = firstData.getAlphaAwb();
				List<String> childAwbs = new ArrayList<>();
				
				if (firstData.getLabelData() != null && firstData.getLabelData().getChildAwb() != null) {
					childAwbs = firstData.getLabelData().getChildAwb();
				}
					
				// Get existing pack details for this shipment
				List<SellerShipmentPackDetails> packDetails = sellerShipmentPackDetailsRepository.findByShipmentId(splitSellerShipment.getEntityId());
				if (packDetails != null && !packDetails.isEmpty()) {						
					List<SellerShipmentPackDetails> shuffledPackDetails = new ArrayList<>(packDetails);
					Collections.shuffle(shuffledPackDetails);
					
					// Update or create pack details
					for (int i = 0; i < shuffledPackDetails.size(); i++) {
						SellerShipmentPackDetails packDetail = shuffledPackDetails.get(i);
						
						// Assign child AWB if available, otherwise fall back to parent AWB
						if (i < childAwbs.size()) {
							String childAwb = childAwbs.get(i);
							packDetail.setWayBill(childAwb != null ? childAwb : alphaAwb);
						} else {
							packDetail.setWayBill(alphaAwb);
						}
						
						// Integrate GCS for shipping label
						if (org.apache.commons.lang.StringUtils.isNotBlank(firstData.getUrl())) {
							try {
								String signedUrl = shippingLabelUrlService.processAndSaveShippingLabel(
									firstData.getUrl(),
									packDetail,
									SellerShipmentPackDetails.class,
									null  // Use default bucket
								);
								LOGGER.info("Successfully processed shipping label to GCS for pack detail: " + packDetail.getEntityId());
							} catch (Exception e) {
								LOGGER.error("Failed to process shipping label to GCS, using carrier URL: " + e.getMessage(), e);
								// Fallback: use original carrier URL
								packDetail.setShippingLabel(firstData.getUrl());
								packDetail.setUpdatedAt(new Timestamp(new Date().getTime()));							
								sellerShipmentPackDetailsRepository.save(packDetail);
							}
						} else {
							LOGGER.warn("First mile MPS response has empty label URL; skipping GCS processing for pack detail.");
						}
					}
				}
			}
		} catch (Exception e) {
			LOGGER.error("Error storing MPS response data to pack details for first mile: " + e.getMessage(), e);
		}
	}

	private GetShipmentV3Response processForwardShipmentResponseForSeller(GetShipmentV3ResponseBody responseBody, SalesOrder order,
														 SplitSellerShipment splitSellerShipment, ResponseEntity<NavikResponse> navikResponse, SplitSellerOrder splitSellerOrder) throws JsonProcessingException {

		GetShipmentV3Response response = new GetShipmentV3Response();

		NavikResponse body = navikResponse.getBody();
		if (body != null && body.getResult() != null 
			&& body.getResult().getAlphaAwb() != null && !body.getResult().getAlphaAwb().isEmpty()
			&& body.getResult().getLabel() != null && !body.getResult().getLabel().isEmpty()) {
				// Set default courier partner as LOGISTIQ if courier name is not available from v1/create API
				String courierName = body.getResult().getCourier_name();
				if (courierName == null || courierName.trim().isEmpty()) {
					courierName = "LOGISTIQ";
				}
				responseBody.setTransporter(courierName);
				responseBody.setAwbNumber(body.getResult().getWaybill());
				// Note: shippingLabelUrl will be set after processing pack details to use GCS signed URL
				String encodeValue = null;
				if (null != Constants.orderCredentials && Constants.orderCredentials.getWms().isNewInVoiceEncode()) {
					encodeValue = order.getEntityId().toString().concat("#").concat(order.getCustomerEmail());
				} else {
					encodeValue = order.getEntityId().toString();
				}
				String encoded = Base64.getEncoder().encodeToString(encodeValue.getBytes());

				responseBody.setInvoiceUrl(Constants.orderCredentials.getOrderDetails().getOmsServicePublicBaseUrl()
						+ GENERATE_PDF_URI + encoded);

				SplitSellerShipmentTrack splitSellerShipmentTrack = new SplitSellerShipmentTrack();
				splitSellerShipmentTrack.setCreatedAt(new Timestamp(new Date().getTime()));
				splitSellerShipmentTrack.setUpdatedAt(new Timestamp(new Date().getTime()));
				splitSellerShipmentTrack.setOrderId(splitSellerShipment.getSalesOrder().getEntityId());
				splitSellerShipmentTrack.setTrackNumber(body.getResult().getWaybill());
				splitSellerShipmentTrack.setTitle(courierName);
				splitSellerShipmentTrack.setParentId(splitSellerShipment.getEntityId());
				splitSellerShipmentTrack.setCarrierCode(Constants.NAVIC_CARRIOR_CODE);
				splitSellerShipmentTrack.setAlphaAwb(body.getResult().getAlphaAwb());
				splitSellerShipmentTrack.setSellerId(splitSellerOrder.getEntityId().toString());
				
				// Set split order ID if this is a split order
				if (splitSellerOrder.getSplitOrder() != null) {
					splitSellerShipmentTrack.setSplitSalesOrderId(splitSellerOrder.getSplitOrder().getEntityId());
				}
				
				// Save the shipment track with proper error handling
				try {
					splitSellerShipmentTrackRepository.saveAndFlush(splitSellerShipmentTrack);
				} catch (Exception e) {
					LOGGER.error("Error saving SplitSellerShipmentTrack for shipment: " + splitSellerShipment.getIncrementId() + 
								", parentId: " + splitSellerShipment.getEntityId(), e);
					throw e;
				}

				response.setAwbNumber(responseBody.getAwbNumber());
				response.setTransporter(responseBody.getTransporter());
				
				// Populate box label details from pack details (with GCS signed URLs)
				List<BoxLabelDetails> boxLabelDetails = buildBoxLabelDetailsForSeller(splitSellerShipment);
				response.setBoxLabelDetails(boxLabelDetails);
				
				// Set shippingLabelUrl from first box's GCS signed URL (if available)
				if (boxLabelDetails != null && !boxLabelDetails.isEmpty() && 
				    boxLabelDetails.get(0).getBoxLabelUrl() != null && 
				    !boxLabelDetails.get(0).getBoxLabelUrl().isEmpty()) {
					String gcsSignedUrl = boxLabelDetails.get(0).getBoxLabelUrl();
					responseBody.setShippingLabelUrl(gcsSignedUrl);
					response.setShippingLabelUrl(gcsSignedUrl);
					LOGGER.info("Using GCS signed URL in response: " + gcsSignedUrl.substring(0, Math.min(100, gcsSignedUrl.length())) + "...");
				} else {
					// Fallback to carrier URL if GCS processing failed
					String carrierUrl = body.getResult().getLabel();
					responseBody.setShippingLabelUrl(carrierUrl);
					response.setShippingLabelUrl(carrierUrl);
					LOGGER.warn("GCS signed URL not available, falling back to carrier URL");
				}

				// updateOrderStatusHistory(order, OrderConstants.FORWARD_AWB_CREATED_MESSAGE + response.getAwbNumber(),
				// 		OrderConstants.ORDER_STATUS_HISTORY_ENTITY, order.getStatus());
			} else {
				response.setHasError(true);
				processShipmentError(response, body);
				if(splitSellerOrder != null) {
					splitSellerOrder.setAwbFailed(1);
					splitSellerOrderRepository.save(splitSellerOrder);
				}
			}
			orderHelper.buildOTSPayloadForSellerOrderAndPublishToPubSub(splitSellerOrder, OrderConstants.FORWARD_AWB_CREATED_MESSAGE + response.getAwbNumber(), "4.1");	
		return response;
	}

	private List<BoxLabelDetails> buildBoxLabelDetailsForSeller(SplitSellerShipment splitSellerShipment) {
		List<BoxLabelDetails> boxLabelDetails = new ArrayList<>();
		
		try {
			// Get pack details for this shipment
			List<SellerShipmentPackDetails> packDetails = sellerShipmentPackDetailsRepository.findByShipmentId(splitSellerShipment.getEntityId());
			
			if (packDetails != null && !packDetails.isEmpty()) {
				for (SellerShipmentPackDetails packDetail : packDetails) {
					BoxLabelDetails boxLabel = new BoxLabelDetails();
					boxLabel.setBoxId(packDetail.getBoxCode() != null ? packDetail.getBoxCode() : "BOX" + packDetail.getEntityId());
					
				// CRITICAL: Use getOrRefreshShippingLabelUrl to auto-refresh expired URLs
				// This ensures boxLabelUrl respects the gcs_shipping_label_enabled flag and refreshes expired URLs
				String boxLabelUrl = "";
				if (packDetail.getShippingLabel() != null && !packDetail.getShippingLabel().isEmpty()) {
					// Check if this is a GCS URL by checking if it has GCS object path
					if (packDetail.getGcsObjectPath() != null && !packDetail.getGcsObjectPath().isEmpty()) {
						// Has GCS data, use getOrRefresh to auto-refresh if expired
						try {
							String refreshedUrl = shippingLabelUrlService.getOrRefreshShippingLabelUrl(
								packDetail,
								SellerShipmentPackDetails.class
							);
							boxLabelUrl = refreshedUrl != null ? refreshedUrl : "";
						} catch (Exception e) {
							LOGGER.error("Failed to get/refresh box label URL for seller: " + e.getMessage(), e);
							boxLabelUrl = "";
						}
					} else {
						// Not a GCS URL (original carrier URL or pre-GCS data), return as-is
						boxLabelUrl = packDetail.getShippingLabel();
					}
				}
					
					boxLabel.setBoxLabelUrl(boxLabelUrl);
					boxLabel.setBoxAwb(packDetail.getWayBill() != null ? packDetail.getWayBill() : "");
					boxLabelDetails.add(boxLabel);
				}
			}
		} catch (Exception e) {
			LOGGER.error("Error building box label details for seller: " + e.getMessage(), e);
		}
		
		return boxLabelDetails;
	}

	@Override
	@Transactional
	public void processDummyReturnShipment(String returnIncrementId) {
		String csvFileName = "dummy_return_shipments_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv";
		processDummyReturnShipment(returnIncrementId, csvFileName);
	}
	
	@Override
	@Transactional
	public void processDummyReturnShipment(String returnIncrementId, String csvFileName) {
		LOGGER.info("[SalesOrderServiceV3Impl] Processing return shipment for ID: " + returnIncrementId);
		
		try {
			// Fetch actual return data from database like getReturnShipment does
			AmastyRmaRequest amastyRmaRequest = amastyRmaRequestRepository.findByRmaIncId(returnIncrementId);
			
			if (amastyRmaRequest == null) {
				LOGGER.warn("[SalesOrderServiceV3Impl] No RMA request found for ID: " + returnIncrementId);
				storeErrorToCsv(returnIncrementId, "RMA request not found in database", csvFileName);
				return;
			}
			
			// Get the associated sales order
			SalesOrder order = salesOrderRepository.findByEntityId(amastyRmaRequest.getOrderId());
			
			if (order == null) {
				LOGGER.warn("[SalesOrderServiceV3Impl] No sales order found for RMA ID: " + returnIncrementId);
				storeErrorToCsv(returnIncrementId, "Sales order not found for RMA request", csvFileName);
				return;
			}
			
			LOGGER.info("[SalesOrderServiceV3Impl] Found RMA request and order for ID: " + returnIncrementId + ", Order: " + order.getIncrementId());
			
			// Create MPS request for return shipment using dedicated return function
			MpsOrderCreateRequest mpsRequest = buildMpsReturnOrderCreateRequest(order, amastyRmaRequest, Constants.orderCredentials.getNavik());
			
			// Call createsMpsShipmentWithAlpha
			ResponseEntity<MpsOrderCreateResponse> mpsResponse = createsMpsShipmentWithAlpha(mpsRequest);
			
			LOGGER.info("[SalesOrderServiceV3Impl] Alpha MPS response for " + returnIncrementId + ": " + mapper.writeValueAsString(mpsResponse.getBody()));
			
			// Store the response in CSV file
			storeMpsResponseToCsv(returnIncrementId, mpsResponse, csvFileName);
			
		} catch (Exception e) {
			LOGGER.error("[SalesOrderServiceV3Impl] Exception processing return shipment for ID: " + returnIncrementId, e);
			// Store error in CSV as well
			storeErrorToCsv(returnIncrementId, e.getMessage(), csvFileName);
		}
	}
	
	/**
	 * Dedicated function to build MPS order create request specifically for return shipments
	 * Includes all return logic line by line without any missing parts
	 */
	private MpsOrderCreateRequest buildMpsReturnOrderCreateRequest(SalesOrder order, AmastyRmaRequest amastyRmaRequest, NavikDetails navik) {
		MpsOrderCreateRequest mpsRequest = new MpsOrderCreateRequest();
		
		// Set customer details from salesOrderAddress
		SalesOrderAddress shippingAddress = order.getSalesOrderAddress().stream()
				.filter(e -> e.getAddressType().equalsIgnoreCase(SHIPPING)).findAny().orElse(null);
		
		// Set customer address fields using the same logic as setDropInfo
		if (shippingAddress != null) {
			mpsRequest.setCustomerEmail(shippingAddress.getEmail() != null ? shippingAddress.getEmail() : "");
			String customerName = (shippingAddress.getFirstname() != null ? shippingAddress.getFirstname() : "") + 
				" " + (shippingAddress.getLastname() != null ? shippingAddress.getLastname() : "");
			mpsRequest.setCustomerName(customerName);
			// Set basic address fields
			mpsRequest.setCustomerAddress(escapeAmpersand(shippingAddress.getStreet() != null ? shippingAddress.getStreet() : ""));
			mpsRequest.setCustomerAddressLandmark(shippingAddress.getNearestLandmark() != null ? shippingAddress.getNearestLandmark() : "");
            if(Constants.orderCredentials.getOrderDetails().getEnableKSAAddressSupport()){
                mpsRequest.setShortAddress(shippingAddress.getShortAddress() != null ? shippingAddress.getShortAddress() : "");
                mpsRequest.setCustomerUnitNumber(shippingAddress.getUnitNumber() != null ? shippingAddress.getUnitNumber() : "");
                String streetNameTrimed = shippingAddress.getStreet() != null ? shippingAddress.getStreet() : "";
                if (streetNameTrimed.length() > 100) {
                	streetNameTrimed = streetNameTrimed.substring(0, 100);
                }
                mpsRequest.setCustomerStreetName(streetNameTrimed);
                String buildingNumberTrimed = shippingAddress.getBuildingNumber() != null ? shippingAddress.getBuildingNumber() : "";
                if (buildingNumberTrimed.length() > 100) {
                	buildingNumberTrimed = buildingNumberTrimed.substring(0, 100);
                }
                LOGGER.info("");
                mpsRequest.setCustomerBuildingNumber(buildingNumberTrimed);
                mpsRequest.setCustomerZipCode(shippingAddress.getPostalCode() != null ? shippingAddress.getPostalCode() : "");
            }
			// Apply the same address logic as setDropInfo
			boolean arabicTextCheck = checkArabicTextCheck(shippingAddress.getCity());

			if (Boolean.TRUE.equals(OrderConstants.CHECKENGLISHSTORE.get(order.getStoreId()))
					&& !arabicTextCheck) {
				// English store logic
				mpsRequest.setCustomerCity(shippingAddress.getCity() != null ? shippingAddress.getCity() : "");
				mpsRequest.setCustomerCountryCode(shippingAddress.getCountryId() != null ? shippingAddress.getCountryId() : "");
				mpsRequest.setCustomerPostalCode(shippingAddress.getArea() != null ? shippingAddress.getArea() : "");
			} else {
				// Arabic store logic
				try {
					AddressDetails addressDetails = navikHelper.getArabicAddressDetails(shippingAddress);
					// Use Navik city if non-blank, else fallback to shipping address city (fixes Logistiq "Customer City can not be empty" for e.g. Oman)
					String customerCity = addressDetails.getCityName();
					if (StringUtils.isBlank(customerCity)) {
						customerCity = StringUtils.defaultString(shippingAddress.getCity());
					}
					mpsRequest.setCustomerCity(customerCity);

					if (null != shippingAddress.getLatitude() && null != shippingAddress.getLongitude()) {
						mpsRequest.setCustomerPostalCode(shippingAddress.getArea() != null ? shippingAddress.getArea() : "");
					} else {
						mpsRequest.setCustomerPostalCode(addressDetails.getAreaName() != null ? addressDetails.getAreaName() : "");
					}

				} catch (JSONException e1) {
					LOGGER.error("error in json parsing during fetch arabic address");
					// Fallback to basic values
					mpsRequest.setCustomerCity(shippingAddress.getCity() != null ? shippingAddress.getCity() : "");
					mpsRequest.setCustomerPostalCode(shippingAddress.getArea() != null ? shippingAddress.getArea() : "");
				}
			}

			// Handle phone number parsing (same logic as setDropInfo)
			if (null != shippingAddress.getTelephone() && shippingAddress.getTelephone().contains(" ")
					&& ArrayUtils.isNotEmpty(shippingAddress.getTelephone().split(" "))
					&& shippingAddress.getTelephone().split(" ").length > 0) {
				mpsRequest.setCustomerPhone(shippingAddress.getTelephone().split(" ")[1]);
				mpsRequest.setCustomerPhoneCode(shippingAddress.getTelephone().split(" ")[0]);
			} else {
				mpsRequest.setCustomerPhone(shippingAddress.getTelephone() != null ? shippingAddress.getTelephone() : "");
				mpsRequest.setCustomerPhoneCode("");
			}

			// Handle formatted address with latitude/longitude (same logic as setDropInfo)
			if (Objects.nonNull(shippingAddress) && null != shippingAddress.getLatitude()) {
				String formattedAddress = shippingAddress.getFormattedAddress();
				String postalCode = mpsRequest.getCustomerPostalCode();
				mpsRequest.setCustomerPostalCode(mpsRequest.getCustomerCity());
				if (StringUtils.isNotBlank(postalCode)) {
					mpsRequest.setCustomerAddress(escapeAmpersand(
							shippingAddress.getStreet().concat(" ").concat(postalCode) + " " + formattedAddress));
				}
			}

		} else {
			// Set default empty values when shippingAddress is null
			mpsRequest.setCustomerAddress("");
			mpsRequest.setCustomerCity("");
			mpsRequest.setCustomerCountryCode("");
			mpsRequest.setCustomerPostalCode("");
			mpsRequest.setCustomerPhone("");
			mpsRequest.setCustomerAddressLandmark("");
		}
		
		// Set international order fields only for international stores (non-KSA) fulfilled from warehouse 110
		boolean isInternationalOrder = !order.getStoreId().equals(1) && !order.getStoreId().equals(3) 
				&& Integer.valueOf(110).equals(order.getSubSalesOrder().getWarehouseLocationId());
		
		if (isInternationalOrder) {
			// Override customer country code for international orders
			if (shippingAddress != null) {
				mpsRequest.setCustomerCountryCode(shippingAddress.getCountryId() != null ? shippingAddress.getCountryId() : "");
			}
			
			// Common international order fields
			mpsRequest.setCustomValue(1);
			mpsRequest.setCustomCurrencyCode(order.getOrderCurrencyCode() != null ? order.getOrderCurrencyCode() : "SAR");
			mpsRequest.setModeOfTransport("Road");
			mpsRequest.setCustomClearanceMode("Standard");
			mpsRequest.setHsCode("NA");	
		}
		
		// RETURN-SPECIFIC ORDER DETAILS
		// Set invoice value for return orders using refund amount calculation
		if (amastyRmaRequest != null) {
			BigDecimal totalRefundAmount = getTotalRefundAmount(amastyRmaRequest, order);
			mpsRequest.setInvoiceValue(totalRefundAmount.doubleValue());
		}
		mpsRequest.setOrderRefNumber(amastyRmaRequest.getRmaIncId() != null ? amastyRmaRequest.getRmaIncId()+"_500" : "");
		mpsRequest.setOrderType("PREPAID");
		mpsRequest.setDeliveryType("RETURN");
		
		// Set MPS flag for return shipments
		mpsRequest.setIsMps(false); // Return shipments typically don't use MPS
		
		mpsRequest.setReturnInfoName(navik.getStyliReturnInfoName());
	
		// RETURN-SPECIFIC SHIPMENT DETAILS
		// Get navikInfos for the current store
		Navikinfos navikInfos = null;
		if (navik != null && navik.getDropOffDetails() != null) {
			List<Stores> stores = Constants.getStoresList();
			Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(order.getStoreId())).findAny()
					.orElse(null);
			if (store != null) {
				navikInfos = navik.getDropOffDetails().stream()
						.filter(e -> null != e.getWebSiteId() && e.getWebSiteId().intValue() == store.getWebsiteId())
						.findFirst().orElse(null);
			}
		}
		
		// Get shipment details to extract SKU and quantity information
		List<MpsShipmentDetail> shipmentDetails = buildMpsReturnShipmentDetails(order, amastyRmaRequest, navikInfos);
		if (shipmentDetails != null && !shipmentDetails.isEmpty()) {
			// Get the first shipment detail (box)
			MpsShipmentDetail firstDetail = shipmentDetails.get(0);
			if (firstDetail.getItems() != null && !firstDetail.getItems().isEmpty()) {
				// Get the first item from the first box
				MpsShipmentItem firstItem = firstDetail.getItems().get(0);
				
				mpsRequest.setSkuDescription(firstItem.getSkuDescription() != null ? firstItem.getSkuDescription() : "Return Item");
				mpsRequest.setSku(firstItem.getSku() != null ? firstItem.getSku() : "SKU");
				mpsRequest.setQty(String.valueOf(firstItem.getQuantity() != null ? firstItem.getQuantity() : 1));
			}
		} else {
			// Fallback values if no shipment details found
			mpsRequest.setSkuDescription("Return Item");
			mpsRequest.setSku("SKU");
			mpsRequest.setQty("1");
		}
		
		mpsRequest.setDeliveryInstructions("Leave package with security if not available.");
		mpsRequest.setShipmentDetails(shipmentDetails);
		// Set box count from shipmentDetails size
		mpsRequest.setBoxCount(shipmentDetails != null ? shipmentDetails.size() : 1);
		
		// Set invoice and COD fields for return shipments
		mpsRequest.setInvoiceCurrencyCode(order.getOrderCurrencyCode() != null ? order.getOrderCurrencyCode() : "SAR");
		mpsRequest.setCodValue(0.0);
		mpsRequest.setCodCurrencyCode(order.getOrderCurrencyCode() != null ? order.getOrderCurrencyCode() : "SAR");
		
		// Set invoice date and number for return shipments
		SalesInvoice salesInvoice = order.getSalesInvoices().stream().findFirst().orElse(null);
		if (salesInvoice != null && salesInvoice.getCreatedAt() != null) {
			DateFormat dateFormat = new SimpleDateFormat(OrderConstants.ORDER_PUSH_OMS_DATE_FORMAT);
			Date createDate = new Date(salesInvoice.getCreatedAt().getTime());
			String invoiceDate = dateFormat.format(createDate);
			invoiceDate = invoiceDate.concat(OrderConstants.WMS_DEFAULT_TIME_ZONE_STRING);
			mpsRequest.setInvoiceDate(invoiceDate);
		} else {
			mpsRequest.setInvoiceDate(new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
		}
		
		if (salesInvoice != null) {
			mpsRequest.setInvoiceNumber(salesInvoice.getIncrementId() != null ? salesInvoice.getIncrementId() : "");
		} else {
			mpsRequest.setInvoiceNumber(order.getIncrementId() != null ? order.getIncrementId() : "");
		}
		
		return mpsRequest;
	}
	
	private void storeMpsResponseToCsv(String returnIncrementId, ResponseEntity<MpsOrderCreateResponse> mpsResponse, String csvFileName) {
		try {
			File csvFile = new File(csvFileName);
			
			// Check if file exists, if not create with headers
			boolean fileExists = csvFile.exists();
			
			try (FileWriter writer = new FileWriter(csvFile, true);
				 CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
				
				// Add headers if file is new
				if (!fileExists) {
					csvPrinter.printRecord("Return Increment ID", "Status", "AWB Number", "Courier Name", "Label URL", "Error Message", "Timestamp");
				}
				
				// Extract data from response
				String status = "SUCCESS";
				String awbNumber = "";
				String courierName = "";
				String labelUrl = "";
				String errorMessage = "";
				
				if (mpsResponse.getBody() != null) {
					if (mpsResponse.getBody().getData() != null && !mpsResponse.getBody().getData().isEmpty()) {
						MpsOrderCreateResponse.MpsOrderData firstData = mpsResponse.getBody().getData().get(0);
						awbNumber = firstData.getCpAwb() != null ? firstData.getCpAwb() : "";
						courierName = firstData.getCourierName() != null ? firstData.getCourierName() : "";
						labelUrl = firstData.getUrl() != null ? firstData.getUrl() : "";
					}
					if (mpsResponse.getBody().getMessage() != null && !mpsResponse.getBody().getMessage().isEmpty()) {
						errorMessage = mpsResponse.getBody().getMessage();
						status = "ERROR";
					}
				} else {
					status = "ERROR";
					errorMessage = "No response body";
				}
				
				// Write data to CSV
				csvPrinter.printRecord(
					returnIncrementId,
					status,
					awbNumber,
					courierName,
					labelUrl,
					errorMessage,
					new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
				);
				
				csvPrinter.flush();
			}
			
			LOGGER.info("[SalesOrderServiceV3Impl] Successfully stored response for " + returnIncrementId + " in CSV file: " + csvFileName);
			
		} catch (Exception e) {
			LOGGER.error("[SalesOrderServiceV3Impl] Error storing MPS response to CSV for " + returnIncrementId, e);
		}
	}
	
	private void storeErrorToCsv(String returnIncrementId, String errorMessage) {
		storeErrorToCsv(returnIncrementId, errorMessage, "dummy_return_shipments_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv");
	}
	
	private void storeErrorToCsv(String returnIncrementId, String errorMessage, String csvFileName) {
		try {
			File csvFile = new File(csvFileName);
			
			// Check if file exists, if not create with headers
			boolean fileExists = csvFile.exists();
			
			try (FileWriter writer = new FileWriter(csvFile, true);
				 CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
				
				// Add headers if file is new
				if (!fileExists) {
					csvPrinter.printRecord("Return Increment ID", "Status", "AWB Number", "Courier Name", "Label URL", "Error Message", "Timestamp");
				}
				
				// Write error data to CSV
				csvPrinter.printRecord(
					returnIncrementId,
					"ERROR",
					"", // AWB Number
					"", // Courier Name
					"", // Label URL
					errorMessage,
					new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
				);
				
				csvPrinter.flush();
			}
			
			LOGGER.info("[SalesOrderServiceV3Impl] Successfully stored error for " + returnIncrementId + " in CSV file: " + csvFileName);
			
		} catch (Exception e) {
			LOGGER.error("[SalesOrderServiceV3Impl] Error storing error to CSV for " + returnIncrementId, e);
		}
	}
	
	@Override
	@Transactional
	public String processDummyReturnShipmentWithCsvContent(List<String> returnIncrementIds) {
		// Generate CSV filename with date only (no time) so same file is used for the entire day
		String csvFileName = "dummy_return_shipments_" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")
				.format(java.time.LocalDateTime.now()) + ".csv";
		
		// Process each return increment ID using the same CSV file
		for (String returnIncrementId : returnIncrementIds) {
			LOGGER.info("[SalesOrderServiceV3Impl] Processing return increment ID: " + returnIncrementId);
			try {
				// Call the existing method to process return shipment and store in same CSV
				processDummyReturnShipment(returnIncrementId, csvFileName);
			} catch (Exception e) {
				LOGGER.error("[SalesOrderServiceV3Impl] Exception processing return ID: " + returnIncrementId, e);
				storeErrorToCsv(returnIncrementId, e.getMessage(), csvFileName);
			}
		}
		
		// Read the CSV file content and return it
		try {
			File csvFile = new File(csvFileName);
			if (csvFile.exists()) {
				return new String(Files.readAllBytes(csvFile.toPath()));
			} else {
				LOGGER.warn("[SalesOrderServiceV3Impl] CSV file not found: " + csvFileName);
				return "CSV file not found";
			}
		} catch (Exception e) {
			LOGGER.error("[SalesOrderServiceV3Impl] Error reading CSV file: " + csvFileName, e);
			return "Error reading CSV file: " + e.getMessage();
		}
	}

	/**
	 * Get pickup info name from seller inventory mapping based on sellerId and warehouseId
	 * @param sellerId The seller ID to lookup
	 * @param warehouseId The warehouse ID to lookup
	 * @return The pickup info name if found, null otherwise
	 */
	private String getPickupInfoNameBySellerAndWarehouse(String sellerId, String warehouseId) {
		if (sellerId == null && warehouseId == null) {
			return null;
		}
		
		try {
			// Use service to get seller config by sellerId and warehouseId
			SellerConfig matchingMapping = sellerConfigService.getSellerConfigBySellerIdAndWarehouse(sellerId, warehouseId);
			
			if (matchingMapping != null && matchingMapping.getConfiguration() != null && matchingMapping.getConfiguration().getPickupInfoName() != null) {
				return matchingMapping.getConfiguration().getPickupInfoName();
			}

            List<UnicommereceInventoryMapping> unicommereceInventoryMappings = Constants.orderCredentials.getUnicommerceInventoryMapping();
			
            if (unicommereceInventoryMappings == null || unicommereceInventoryMappings.isEmpty()) {
				return null;
			}
			
            UnicommereceInventoryMapping matchingMappingUnicommerce = unicommereceInventoryMappings.stream()
				.filter(mapping -> {
					boolean sellerMatch = sellerId == null || sellerId.equals(mapping.getSellerId());
					boolean warehouseMatch = warehouseId == null || warehouseId.equals(mapping.getWareHouseId());
					return sellerMatch && warehouseMatch;
				})
				.findFirst()
				.orElse(null);
			
            if(matchingMappingUnicommerce != null && matchingMappingUnicommerce.getPickupInfoName() != null ){
                return matchingMappingUnicommerce.getPickupInfoName();
            }
				return null;
		} catch (Exception e) {
			return null;
		}
	}

	private String getSkuDescriptionFromProductAttributes(SalesOrder order, String clientSkuId, NavikDetails navikDetails) {
		if (order == null || order.getSalesOrderItem() == null || order.getSalesOrderItem().isEmpty()) {
			return null;
		}

		try {
			ObjectMapper mapper = new ObjectMapper();

			// Select the item: match by SKU if available, else take the first item
			SalesOrderItem targetItem = Optional.ofNullable(clientSkuId)
					.flatMap(skuId -> order.getSalesOrderItem().stream()
							.filter(item -> skuId.equals(item.getSku()))
							.findFirst())
					.orElse(order.getSalesOrderItem().iterator().next()); // fallback to first item

			String productAttributesJson = targetItem.getProductAttributes();

			if (productAttributesJson == null || productAttributesJson.isEmpty()) {
				return null;
			}

			Map<String, String> productAttributes = mapper.readValue(productAttributesJson, new TypeReference<Map<String, String>>() {});

			// Extract short_description
			String shortDescription = productAttributes.get("short_description");

			if (Boolean.TRUE.equals(OrderConstants.CHECKENGLISHSTORE.get(order.getStoreId())) && StringUtils.isNotBlank(shortDescription) &&
					navikDetails.getLogisticProductDescriptionEn() != null &&
					navikDetails.getLogisticProductDescriptionEn().containsKey(shortDescription)) {

				return navikDetails.getLogisticProductDescriptionEn().get(shortDescription);
			} else if (StringUtils.isNotBlank(shortDescription) &&
					navikDetails.getLogisticProductDescriptionAr() != null &&
					navikDetails.getLogisticProductDescriptionAr().containsKey(shortDescription)) {

				return navikDetails.getLogisticProductDescriptionAr().get(shortDescription);
			} else if (StringUtils.isNotBlank(shortDescription)) {
				return shortDescription;
			}

		} catch (Exception e) {
			LOGGER.error("Error fetching sku_description for SKU: "+clientSkuId, e);
		}

		return null;
	}

	private String getShippingCategory(SalesOrder order, NavikDetails navik) {
		try {
			List<Integer> excludedStoreIds = navik.getExcludedDggStoreIds();
			if (null != excludedStoreIds && excludedStoreIds.contains(order.getStoreId())) {
				return "";
			}
			boolean hasDangerousProduct = Optional.ofNullable(order)
					.map(SalesOrder::getSalesOrderItem)
					.orElse(Collections.emptySet()) // or emptyList() depending on your model
					.stream()
					.filter(Objects::nonNull)
					.map(SalesOrderItem::getProductAttributes)
					.filter(attrJson -> attrJson != null && !attrJson.isEmpty())
					.map(attrJson -> {
						try {
							Map<String, String> attrs =
									mapper.readValue(attrJson, new TypeReference<Map<String, String>>() {});
							return Boolean.parseBoolean(attrs.getOrDefault("is_dangerous_product", "false"));
						} catch (Exception e) {
							LOGGER.warn("Error parsing product attributes JSON:", e);
							return false;
						}
					})
					.anyMatch(Boolean::booleanValue); // true if any item is dangerous

			return hasDangerousProduct
					? Optional.ofNullable(navik)
					.map(NavikDetails::getLogisticProductCategory)
					.orElse("")
					: "";

		} catch (Exception e) {
			LOGGER.error("Error fetching shipping category for order: ", e);
			return "";
		}
	}
}
