package org.styli.services.order.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.controller.AutoRefundController;
import org.styli.services.order.db.product.exception.NotFoundException;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.helper.ExternalQuoteHelper;
import org.styli.services.order.helper.MulinHelper;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.helper.OrderHelperV2;
import org.styli.services.order.helper.PaymentRefundHelper;
import org.styli.services.order.helper.PrepaidRefundHelper;
import org.styli.services.order.helper.RefundHelper;
import org.styli.services.order.helper.TabbyHelper;
import org.styli.services.order.helper.TamaraHelper;
import org.styli.services.order.jwt.security.jwtsecurity.security.JwtValidator;
import org.styli.services.order.model.Customer.CustomerEntity;
import org.styli.services.order.model.Order.cancel.PayfortRefundResponse;
import org.styli.services.order.model.SalesOrder.SalesCreditmemo;
import org.styli.services.order.model.rma.AmastyRmaRequest;
import org.styli.services.order.model.rma.AmastyRmaRequestItem;
import org.styli.services.order.model.rma.sequence.SequenceCreditmemoOne;
import org.styli.services.order.model.sales.ProxyOrder;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderGrid;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SalesOrderPayment;
import org.styli.services.order.model.sales.SalesShipmentTrack;
import org.styli.services.order.model.sales.StatusChangeHistory;
import org.styli.services.order.model.sales.SubSalesOrder;
import org.styli.services.order.pojo.AddressObject;
import org.styli.services.order.pojo.CashfreeDetails;
import org.styli.services.order.pojo.CatalogProductEntityForQuoteDTO;
import org.styli.services.order.pojo.GetOrderConsulValues;
import org.styli.services.order.pojo.InventoryMapping;
import org.styli.services.order.pojo.KsaCredentials;
import org.styli.services.order.pojo.NavikAddress;
import org.styli.services.order.pojo.NavikDetails;
import org.styli.services.order.pojo.Navikinfos;
import org.styli.services.order.pojo.OrderKeyDetails;
import org.styli.services.order.pojo.PayfortDetails;
import org.styli.services.order.pojo.PayfortVoidAuthorizationResponse;
import org.styli.services.order.pojo.PendingOrderNotfcnDetails;
import org.styli.services.order.pojo.QuoteDTO;
import org.styli.services.order.pojo.TabbyDetails;
import org.styli.services.order.pojo.TamaraDetails;
import org.styli.services.order.pojo.WmsDetails;
import org.styli.services.order.pojo.cancel.MagentoAPIResponse;
import org.styli.services.order.pojo.eas.EASCoinUpdateResponse;
import org.styli.services.order.pojo.eas.StyliCoinUpdate;
import org.styli.services.order.pojo.mulin.GetProductsBySkuResponse;
import org.styli.services.order.pojo.mulin.ProductResponseBody;
import org.styli.services.order.pojo.mulin.Variant;
import org.styli.services.order.pojo.oms.BankSwiftCode;
import org.styli.services.order.pojo.quote.response.GetQuoteResponse;
import org.styli.services.order.pojo.quote.response.QuoteUpdateDTOV2;
import org.styli.services.order.pojo.request.Order.CreateOrderRequestV2;
import org.styli.services.order.pojo.request.Order.ReOrderRequest;
import org.styli.services.order.pojo.response.AddressMapperCity;
import org.styli.services.order.pojo.response.AddressMapperResponse;
import org.styli.services.order.pojo.response.CustomCouponCancelRedemptionResponse;
import org.styli.services.order.pojo.response.InventoryBlockResponse;
import org.styli.services.order.pojo.response.InventoryMetaData;
import org.styli.services.order.pojo.response.Order.CreateOrderResponseDTO;
import org.styli.services.order.pojo.tabby.TabbyPayment;
import org.styli.services.order.pojo.tamara.TamaraCaptures;
import org.styli.services.order.repository.CoreConfigDataRepository;
import org.styli.services.order.repository.Rma.AmastyRmaReasonRepository;
import org.styli.services.order.repository.Rma.AmastyRmaRequestItemRepository;
import org.styli.services.order.repository.Rma.AmastyRmaRequestRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditHistoryRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditRepository;
import org.styli.services.order.repository.Rma.sequence.SequenceCreditmemoOneRepository;
import org.styli.services.order.repository.SalesOrder.ProxyOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoCommentRepository;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoItemRepository;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderPaymentRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusHistoryRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusLabelRepository;
import org.styli.services.order.repository.SalesOrder.StatusChaneHistoryRepository;
import org.styli.services.order.repository.SalesOrder.SubSalesOrderItemRepository;
import org.styli.services.order.repository.SalesOrder.VaultPaymentTokenRepository;
import org.styli.services.order.service.AutoRefundService;
import org.styli.services.order.service.SalesOrderService;
import org.styli.services.order.service.SalesOrderServiceV2;
import org.styli.services.order.utility.PaymentUtility;
import org.styli.services.order.utility.consulValues.PromoRedemptionValues;
import org.styli.services.order.utility.consulValues.PromoValues;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class SalesOrderRetryServiceImplTest {
	@InjectMocks
	PaymentUtility paymentUtility;
	@InjectMocks
	ExternalQuoteHelper externalQuoteHelper;
	@Mock
	TabbyPaymentServiceImpl tabbyPaymentService;
	@Mock
	SalesCreditmemoItemRepository salesCreditmemoItemRepository;
	@Mock
	HttpServletRequest httpServletRequest;
	@Mock
	KafkaServiceImpl kafkaService;
	@Mock
	TamaraPaymentServiceImpl tamaraPaymentService;
	@Mock
	TamaraHelper tamaraHelper;

	@Mock
	private RestTemplate restTemplate;
	@Mock
	SubSalesOrderItemRepository subSalesOrderItemRepository;
	@Mock
	AmastyRmaRequestItemRepository amastyRmaRequestItemRepository;
	@Mock
	AmastyStoreCreditRepository amastyStoreCreditRepository;
	@Mock
	AmastyStoreCreditHistoryRepository amastyStoreCreditHistoryRepository;
	@Mock
	SalesCreditmemoRepository salesCreditmemoRepository;
	@Mock
	SalesCreditmemoCommentRepository salesCreditmemoCommentRepository;
	@Mock
	SalesOrderStatusLabelRepository salesOrderStatusLabelRepository;
	@Mock
	AmastyRmaReasonRepository amastyRmaReasonRepository;
	@Mock
	AmastyRmaRequestRepository amastyRmaRequestRepository;
	@Mock
	TabbyHelper tabbyHelper;

	@Mock
	OrderHelper orderHelper;
	@InjectMocks
	OrderHelperV2 orderHelperV2;
	@InjectMocks
	TabbyDetails tabbyDetails;
	@Mock
	ProxyOrderRepository proxyOrderRepository;
	@Mock
	PaymentRefundHelper paymentDtfRefundHelper;
	@Mock
	SequenceCreditmemoOneRepository sequenceCreditmemoOneRepository;
	@Mock
	SalesOrderService salesOrderService;
	@Mock
	SequenceCreditmemoOne sequenceCreditmemoOne;
	@Mock
	SalesOrderRepository salesOrderRepository;
	@Mock
	SalesOrderStatusHistoryRepository salesOrderStatusHistoryRepository;
	@Mock
	SalesOrderGridRepository salesOrderGridRepository;
	@Mock
	StatusChaneHistoryRepository statusChaneHistoryRepository;
	@Mock
	SalesOrderServiceV2 salesOrderServiceV2;
	@Mock
	SalesOrderPaymentRepository paymentRepository;
	@Mock
	KafkaTemplate<String, Object> kafkaTemplate;

	@InjectMocks
	org.styli.services.order.utility.Constants constants;
	@Mock
	PrepaidRefundHelper prepaidRefundHelper;
	@Mock
	VaultPaymentTokenRepository vaultPaymentTokenRepository;

	@InjectMocks
	private ConfigServiceImpl configService;
	@Mock
	AutoRefundService autoRefundService;
	@InjectMocks
	private AutoRefundController autoRefundController;
	@InjectMocks
	EASServiceImpl eASServiceImpl;
	@InjectMocks
	MulinHelper mulinHelper;
	@Mock
	JwtValidator validator;
	private ProxyOrder order;
	Map<String, String> requestHeader;
	private List<Stores> storeList;
	SalesOrder salesOrder;

	@InjectMocks
	SalesOrderCancelServiceImpl salesOrderCancelService;
	@InjectMocks
	private CashfreePaymentServiceImpl cfPaymentService;
	@InjectMocks
	SalesOrderRetryServiceImpl salesOrderRetryServiceImpl;
	@Mock
	private RefundHelper refundHelper;
	private CustomerEntity customerEntity;
	@Mock
	private SalesCreditmemoGridRepository salesCreditmemoGridRepository;
	@Mock
	CoreConfigDataRepository coreConfigDataRepository;
	@Mock
	ObjectMapper mapper;
	private AmastyRmaRequest amastyRmaRequest;
	private AmastyRmaRequestItem amastyRmaRequestItem;
	private SalesCreditmemo memo;

	@BeforeClass
	public void beforeClass() {
		MockitoAnnotations.initMocks(this);
		requestHeader = new HashMap<>();
		requestHeader.put("x-source", "android");
		requestHeader.put("x-client-version", "11");
		requestHeader.put("token", "token");
		requestHeader.put("X-Header-Token", "test@mail.com");
		try {
			// Prepare mock Data
			Gson g = new Gson();
			String storeData = new String(Files.readAllBytes(Paths.get("src/test/resources/store_list.json")));
			Type listType = new TypeToken<ArrayList<Stores>>() {
			}.getType();
			storeList = g.fromJson(storeData, listType);
		} catch (Exception e) {
		}
		ReflectionTestUtils.setField(constants, "storesList", storeList);
		customerEntity = new CustomerEntity();
		customerEntity.setEntityId(1);
		customerEntity.setFirstName("First Name");
		customerEntity.setLastName("Last Name");
		customerEntity.setEmail("test.100@mailinator.com");
		customerEntity.setGroupId(1);
		customerEntity.setStoreId(1);
		customerEntity.setCreatedAt(new Date());
		customerEntity.setUpdatedAt(new Date());
		customerEntity.setCreatedIn("nowhere");
		customerEntity.setIsActive(1);
		customerEntity.setPhoneNumber("8989891111");
		setStaticfields();
		setSalseOrderData();
		setMockData();
		setorderCred();

	}

	@BeforeTest
	public void beforeTest() {
		System.out.println("Initialise @BeforeTest ");

//		ReflectionTestUtils.setField(configService, "internalAuthBearerToken", "token,dwd,1,2");
//		ReflectionTestUtils.setField(configService, "externalAuthBearerToken", "token,ds,1,1");
	}

	@Test
	public void reOrderV2Test() throws NotFoundException {
		ReOrderRequest req = new ReOrderRequest();
		req.setOrderId(1);
		req.setCustomerId(1);
		GetProductsBySkuResponse getProductsBySkuResponse = getMulinData();

		ResponseEntity<GetProductsBySkuResponse> response = new ResponseEntity<>(HttpStatus.OK)
				.ok(getProductsBySkuResponse);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(GetProductsBySkuResponse.class))).thenReturn(response);

		salesOrderRetryServiceImpl.reOrderV2(requestHeader, req, "retry_payment", "token", "", "", "", restTemplate);
	}

	private void setStaticfields() {
		// TODO Auto-generated method stub
		ReflectionTestUtils.setField(configService, "internalAuthBearerToken", "token1,dwd,1,2");
		ReflectionTestUtils.setField(configService, "externalAuthBearerToken", "token2,ds,1,1");
		ReflectionTestUtils.setField(salesOrderRetryServiceImpl, "externalQuoteHelper", externalQuoteHelper);
		ReflectionTestUtils.setField(configService, "internalAuthBearerToken", "ffyg,token");
		ReflectionTestUtils.setField(salesOrderRetryServiceImpl, "mulinHelper", mulinHelper);
//		ReflectionTestUtils.setField(salesOrderRetryServiceImpl, "eASServiceImpl", eASServiceImpl);
	}

	// set test data
	private GetProductsBySkuResponse getMulinData() {
		OrderHelper orderHelper;
		ProductResponseBody prod = new ProductResponseBody();
		prod.set_id("1");
		prod.setSku("01");
		prod.setIsReturnApplicable(false);
		Variant var = new Variant();
		var.setSku("01");
		prod.setVariants(Arrays.asList(var));
		Map<String, ProductResponseBody> body = new HashMap<>();
		body.put("key", prod);
		GetProductsBySkuResponse getProductsBySkuResponse = new GetProductsBySkuResponse();
		getProductsBySkuResponse.setStatus(true);
		getProductsBySkuResponse.setStatusCode("200");
		getProductsBySkuResponse.setResponse(body);
		return getProductsBySkuResponse;
	}

	void setorderCred() {
		OrderKeyDetails keydetail = new OrderKeyDetails();
		keydetail.setMaximumOrderPedningOrderThreshold(10);
		keydetail.setReferralOrderLastHours(1);
		keydetail.setPendingOrderNotfcnDetails(new PendingOrderNotfcnDetails());
		LocalDateTime currentTime = LocalDateTime.now();
		LocalDateTime previousDay = currentTime.minus(2, ChronoUnit.DAYS);
		Timestamp timestamp = Timestamp.valueOf(previousDay);
		keydetail.setPendingOrderNotificationInMins(timestamp.getNanos());
		keydetail.setOmsServiceBaseUrl("url");
		GetOrderConsulValues val = new GetOrderConsulValues();
		val.setOrderDetails(keydetail);
		val.setExternalAuthEnable(false);
		val.setInternalAuthEnable(true);
		val.setFirebaseAuthEnable(true);

		TabbyDetails tabbyDetails = new TabbyDetails();
		tabbyDetails.setBackendCreateOrderDurationMins(1);
		tabbyDetails.setEnableBackendCreateOrder(true);
		val.setTabby(tabbyDetails);
		TamaraDetails tamaraDetails = new TamaraDetails();
		tamaraDetails.setWebhookNotificationToken("auth");
		val.setTamara(tamaraDetails);
		CashfreeDetails details = new CashfreeDetails();
		details.setCashgramBaseUrl("url");
		details.setCashGramAppId("1");
		details.setCashFreeBaseUrl("url");
		details.setCashGramSecret("secrete");
		val.setCashfree(details);

		NavikDetails nav = new NavikDetails();
		nav.setReturnAwbCreateClubbingHrs(1);
		Navikinfos info = new Navikinfos();
		info.setWebSiteId(1);
		info.setAddressDetails(new NavikAddress());
		nav.setDropOffDetails(Arrays.asList(info));
		nav.setReturnAwbCreateQueryClubinghrs(1);
		nav.setReturnAwbCreateClubingStartDate("27-06-23");
		nav.setReturnAwbCreateLimit(1);
		val.setNavik(nav);
		WmsDetails wmsd = new WmsDetails();
		wmsd.setWmsOrderPushMinutes(new Integer(10));
		val.setWms(wmsd);
		PayfortDetails payFort = new PayfortDetails();

		KsaCredentials kd = new KsaCredentials();
		kd.setPayfortksaAmountMultiplier("1");
		kd.setPayfortKsaCardAccessCode("1");
		payFort.setKsaCredentials(kd);

		payFort.setPayfortQueryStatusCheckbfrhrsAgo("1");
		payFort.setPayfortQueryFetchInMinute(1);
		val.setPayfort(payFort);
		Map<String, List<BankSwiftCode>> bankSwiftCodes = new HashMap<>();
		val.setBankSwiftCodes(bankSwiftCodes);

		InventoryMapping inventoryMapping = new InventoryMapping();
		inventoryMapping.setWareHouseId("1");
		val.setInventoryMapping(Arrays.asList(inventoryMapping));

		org.styli.services.order.utility.Constants.setOrderCredentials(new Gson().toJson(val).toString());
		ReflectionTestUtils.setField(constants, "orderCredentials", val);
		PromoValues v = new PromoValues();
		PromoRedemptionValues prv = new PromoRedemptionValues();
		prv.setDefaultRedemptionEndpoint("");
		prv.setEnabled(true);
		prv.setAllowAllStores(false);
		prv.setAllowedStores(Arrays.asList(2));
		prv.setAllowInternalUsers(true);
		prv.setExcludeEmailId(new ArrayList<String>());
		prv.setRedemptionEndpoint("endpoint");
		prv.setRedemptionChangeStatusEndpoint("url");
		v.setPromoRedemptionUrl(prv);

		Map<String, String> m = new HashMap<>();
		m.put("promo/promo", new Gson().toJson(v).toString());
		ReflectionTestUtils.setField(constants, "promoConsulValues", m);

	}

	void setMockData() {

		amastyRmaRequest = new AmastyRmaRequest();
		amastyRmaRequest.setCustomerId(1);
		amastyRmaRequest.setStoreId(1);
		amastyRmaRequest.setOrderId(1);
		amastyRmaRequest.setRequestId(1);
		amastyRmaRequest.setStatus(1);

		amastyRmaRequestItem = new AmastyRmaRequestItem();
		amastyRmaRequestItem.setRequestId(1);
		amastyRmaRequestItem.setRequestItemId(1);
		amastyRmaRequestItem.setOrderItemId(1);
		Set<AmastyRmaRequestItem> amastyRmaRequestItems = new HashSet<>();
		amastyRmaRequestItems.add(amastyRmaRequestItem);
		amastyRmaRequest.setAmastyRmaRequestItems(amastyRmaRequestItems);

		memo = new SalesCreditmemo();
		memo.setSubtotal(new BigDecimal(10));
		memo.setBaseSubtotal(new BigDecimal(10));
		memo.setGrandTotal(new BigDecimal(10));
		memo.setBaseGrandTotal(new BigDecimal(10));
		memo.setAmstorecreditAmount(new BigDecimal(10));
		memo.setAmstorecreditBaseAmount(new BigDecimal(10));
		memo.setSubtotalInclTax(new BigDecimal(10));
		memo.setBaseSubtotalInclTax(new BigDecimal(10));
		memo.setTaxAmount(new BigDecimal(10));
		memo.setBaseTaxAmount(new BigDecimal(10));
		memo.setDiscountAmount(new BigDecimal(10));
		memo.setBaseDiscountAmount(new BigDecimal(1));

		order = new ProxyOrder();
		order.setPaymentMethod("tabby_installments");
		order.setPaymentId("1");
		LocalDateTime currentTime = LocalDateTime.now();
		LocalDateTime previousDay = currentTime.minus(10, ChronoUnit.MINUTES);
		Timestamp timestamp = Timestamp.valueOf(previousDay);
		order.setCreatedAt(new Date(timestamp.getTime()));
		order.setStatus("approved");

		Gson g = new Gson();
		order.setSalesOrder(g.toJson(salesOrder));

		CreateOrderRequestV2 v2 = new CreateOrderRequestV2();
		order.setOrderRequest(g.toJson(v2));

		ResponseEntity<String> iresponse = new ResponseEntity<>(HttpStatus.OK)
				.ok("{order_id=1,order_status=approved,cf_order_id=1,payload=AUTHORIZED}");
		ResponseEntity<String> response = new ResponseEntity<>(HttpStatus.OK).ok(g.toJson(salesOrder));
		TabbyPayment tb = new TabbyPayment();
		tb.setId("1");
		ResponseEntity<TabbyPayment> tresponse = new ResponseEntity<>(HttpStatus.OK).ok(tb);
		ResponseEntity<String> stresponse = new ResponseEntity<>(HttpStatus.OK).ok("");
		TamaraCaptures tc = new TamaraCaptures();
		tc.setCaptureId("1");
		ResponseEntity<TamaraCaptures> tcresponse = new ResponseEntity<>(HttpStatus.OK).ok(tc);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.GET),
				Mockito.any(HttpEntity.class), Mockito.eq(String.class))).thenReturn(iresponse);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(String.class))).thenReturn(response);
		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(TabbyPayment.class))).thenReturn(tresponse);
		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.PUT),
				Mockito.any(HttpEntity.class), Mockito.eq(TabbyPayment.class))).thenReturn(tresponse);
		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.PUT),
				Mockito.any(HttpEntity.class), Mockito.eq(String.class))).thenReturn(stresponse);
		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(TamaraCaptures.class))).thenReturn(tcresponse);
		PayfortRefundResponse fortbody = new PayfortRefundResponse();
		fortbody.setResponseCode("06000");
		ResponseEntity<PayfortRefundResponse> fortresponse = new ResponseEntity<>(HttpStatus.OK).ok(fortbody);
		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(PayfortRefundResponse.class))).thenReturn(fortresponse);

		MagentoAPIResponse magentobody = new MagentoAPIResponse();
		magentobody.setStatusCode(200);
		MagentoAPIResponse[] ary = { magentobody };
		ResponseEntity<MagentoAPIResponse[]> magentoresponse = new ResponseEntity<>(HttpStatus.OK).ok(ary);
		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(MagentoAPIResponse[].class))).thenReturn(magentoresponse);

		PayfortVoidAuthorizationResponse authrespobody = new PayfortVoidAuthorizationResponse();
		authrespobody.setStatus("08");
		ResponseEntity<PayfortVoidAuthorizationResponse> authresponse = new ResponseEntity<>(HttpStatus.OK)
				.ok(authrespobody);
		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(PayfortVoidAuthorizationResponse.class)))
				.thenReturn(authresponse);
		CustomCouponCancelRedemptionResponse bb = new CustomCouponCancelRedemptionResponse();
		bb.setCode(200);
		ResponseEntity<CustomCouponCancelRedemptionResponse> redresponse = new ResponseEntity<>(HttpStatus.OK).ok(bb);
		Mockito.when(restTemplate.exchange(Mockito.any(URI.class), Mockito.eq(HttpMethod.PUT),
				Mockito.any(HttpEntity.class), Mockito.eq(CustomCouponCancelRedemptionResponse.class)))
				.thenReturn(redresponse);

		QuoteUpdateDTOV2 qbody = new QuoteUpdateDTOV2();
		qbody.setCustomerId(1);
		qbody.setStatus(true);
		qbody.setStatusCode("200");
		ResponseEntity<QuoteUpdateDTOV2> qresponse = new ResponseEntity<>(HttpStatus.OK).ok(qbody);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(QuoteUpdateDTOV2.class))).thenReturn(qresponse);

		AddressMapperResponse amr = new AddressMapperResponse();
		amr.setStatusCode("200");
		AddressMapperCity city = new AddressMapperCity();

		city.setEstimatedDate("2023-07-04 00:00:00");
		amr.setResponse(city);
		ResponseEntity<AddressMapperResponse> aresponse = new ResponseEntity<>(HttpStatus.OK).ok(amr);
		Mockito.when(restTemplate.exchange(Mockito.any(URI.class), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(AddressMapperResponse.class))).thenReturn(aresponse);

		QuoteUpdateDTOV2 qubody = new QuoteUpdateDTOV2();
		qubody.setCustomerId(1);
		qubody.setStatus(true);
		qubody.setStatusCode("200");
		ResponseEntity<QuoteUpdateDTOV2> quresponse = new ResponseEntity<>(HttpStatus.OK).ok(qubody);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(QuoteUpdateDTOV2.class))).thenReturn(quresponse);

		InventoryMetaData meta = new InventoryMetaData();
		meta.setStatus(true);
		InventoryBlockResponse body = new InventoryBlockResponse();
		body.setMeta(meta);
		body.setResponse("response");
		ResponseEntity<InventoryBlockResponse> inresponse = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(InventoryBlockResponse.class))).thenReturn(inresponse);
		EASCoinUpdateResponse coinbody = new EASCoinUpdateResponse();
		StyliCoinUpdate styliCoinUpdate = new StyliCoinUpdate();
		styliCoinUpdate.setCoin(1);
		coinbody.setResponse(styliCoinUpdate);
		coinbody.setStatus(200);
		ResponseEntity<EASCoinUpdateResponse> coinresponse = new ResponseEntity<>(HttpStatus.OK).ok(coinbody);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(EASCoinUpdateResponse.class))).thenReturn(coinresponse);

		when(salesOrderGridRepository.findByEntityId(anyInt())).thenReturn(new SalesOrderGrid());
		when(salesOrderRepository.findByEntityId(anyInt())).thenReturn(salesOrder);
		when(salesOrderRepository.findByEntityIdAndCustomerId(anyInt(), anyInt())).thenReturn(salesOrder);
		when(proxyOrderRepository.findByPaymentId(anyString())).thenReturn(order);
		when(salesOrderService.findSalesOrderByPaymentId(anyString())).thenReturn(salesOrder);
		when(salesOrderService.findSalesOrderByIncrementId(anyString())).thenReturn(salesOrder);
		when(statusChaneHistoryRepository.findByOrderId(anyString())).thenReturn(new StatusChangeHistory());

		when(refundHelper.cancelOrderObject(any(), any(), anyBoolean(), any())).thenReturn(salesOrder);
		when(refundHelper.cancelStatusHistory(any(), anyBoolean(), any(), anyString())).thenReturn(salesOrder);
		when(refundHelper.cancelOrderItems(any(), anyBoolean())).thenReturn(salesOrder);
		SalesOrderGrid grid = new SalesOrderGrid();
		grid.setTotalPaid(new BigDecimal(1));
		when(refundHelper.cancelOrderGrid(any(), anyBoolean(), anyString())).thenReturn(grid);
		when(refundHelper.getIncrementId(anyInt())).thenReturn("1");

		GetQuoteResponse getbody = new GetQuoteResponse();
		getbody.setStatus(true);
		getbody.setStatusCode("200");
		QuoteDTO res = new QuoteDTO();
		res.setCustomerId("1");
		res.setSelectedPaymentMethod("md_payfort");
		res.setGrandTotal("10");
		res.setStoreId("1");
		AddressObject add = new AddressObject();
		add.setArea("blr");
		add.setCity("bbsr");
		res.setShippingAddress(add);
		CatalogProductEntityForQuoteDTO dto = new CatalogProductEntityForQuoteDTO();
		dto.setQuantity("1");
		dto.setSku("1");
		List<CatalogProductEntityForQuoteDTO> products = new ArrayList<>();
		products.add(dto);
		res.setProducts(products);
		getbody.setResponse(res);
		ResponseEntity<GetQuoteResponse> oResponse = new ResponseEntity<>(HttpStatus.OK).ok(getbody);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(GetQuoteResponse.class))).thenReturn(oResponse);

		CreateOrderResponseDTO resdto = new CreateOrderResponseDTO();
		resdto.setStatusMsg("msg");
		resdto.setStatusCode("200");

	}

	private void setSalseOrderData() {
		salesOrder = new SalesOrder();
		salesOrder.setCustomerId(1);
		salesOrder.setCustomerEmail("admin@stylishop.com");
		salesOrder.setAppVersion("2.0.1");
		salesOrder.setIncrementId("1");
		salesOrder.setCustomerIsGuest(2);
		salesOrder.setEntityId(1);
		salesOrder.setStoreId(1);
		salesOrder.setStatus("pending_payment");
		salesOrder.setGrandTotal(new BigDecimal(10));
		salesOrder.setWmsStatus(new Integer(1));
		salesOrder.setAmstorecreditAmount(new BigDecimal(10));
		salesOrder.setStoreToBaseRate(new BigDecimal(1));
		salesOrder.setAmstorecreditAmount(new BigDecimal(1));
//		LocalDateTime currentTime = LocalDateTime.now();
//		salesOrder.setCreatedAt(Timestamp.valueOf(currentTime));
		salesOrder.setWmsStatus(1);
		salesOrder.setExtOrderId("0");

		SalesOrderItem items = new SalesOrderItem();
		items.setSku("01");
		items.setItemId(1);
		items.setProductType("configurable");
		items.setPriceInclTax(new BigDecimal(10));
		items.setQtyOrdered(new BigDecimal(2));
		items.setQtyCanceled(new BigDecimal(1));
		items.setDiscountAmount(new BigDecimal(5));
		items.setRowTotalInclTax(new BigDecimal(10));
		items.setDiscountTaxCompensationAmount(new BigDecimal(0));
		items.setParentOrderItem(items);
		SalesOrderItem items2 = new SalesOrderItem();
		items2.setSku("01");
		items2.setItemId(1);
		items2.setProductType("simple");
		items2.setPriceInclTax(new BigDecimal(10));
		items2.setQtyOrdered(new BigDecimal(2));
		items2.setQtyCanceled(new BigDecimal(1));
		items2.setDiscountAmount(new BigDecimal(5));
		items2.setRowTotalInclTax(new BigDecimal(10));
		items2.setParentOrderItem(items);
		items2.setQtyCanceled(new BigDecimal(1));
		SalesOrderPayment pay = new SalesOrderPayment();
		pay.setMethod("payfort_fort_cc");
		SalesOrderPayment pay1 = new SalesOrderPayment();
		pay1.setMethod("tabby_installments");
		Set<SalesOrderPayment> payitem = new HashSet<>();
		payitem.add(pay);
		payitem.add(pay1);
		salesOrder.setSalesOrderPayment(payitem);
		Set<SalesOrderItem> setitem = new HashSet<>();
		setitem.add(items);
		setitem.add(items2);
		salesOrder.setSalesOrderItem(setitem);
		Set<SalesShipmentTrack> set = new HashSet<>();
		SalesShipmentTrack track = new SalesShipmentTrack();
		track.setTrackNumber("1");
		set.add(track);
		SubSalesOrder subord = new SubSalesOrder();
		subord.setBaseDonationAmount(new BigDecimal(1));
		subord.setDonationAmount(new BigDecimal(1));
		subord.setExternalCouponRedemptionTrackingId("1");
		subord.setExternalQuoteId(new BigInteger("1"));
		salesOrder.setSubSalesOrder(subord);
		salesOrder.setSalesShipmentTrack(set);
	}
}
