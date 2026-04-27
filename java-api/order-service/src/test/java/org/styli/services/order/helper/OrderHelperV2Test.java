package org.styli.services.order.helper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertNotNull;

import java.lang.reflect.Type;
import java.math.BigDecimal;
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
import java.util.Optional;
import java.util.Set;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.service.impl.KafkaServiceImpl;
import org.styli.services.order.service.impl.PubSubServiceImpl;
import org.styli.services.order.controller.OrderOmsController;
import org.styli.services.order.converter.OmsorderentityConverter;
import org.styli.services.order.converter.OrderEntityConverter;
import org.styli.services.order.db.product.config.firebase.FirebaseAuthentication;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.jwt.security.jwtsecurity.model.JwtUser;
import org.styli.services.order.jwt.security.jwtsecurity.security.JwtValidator;
import org.styli.services.order.model.sales.ProxyOrder;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SalesOrderPayment;
import org.styli.services.order.model.sales.SalesShipmentTrack;
import org.styli.services.order.model.sales.SplitSalesOrder;
import org.styli.services.order.pojo.AddressObject;
import org.styli.services.order.pojo.AppliedCouponValue;
import org.styli.services.order.pojo.CatalogProductEntityForQuoteDTO;
import org.styli.services.order.pojo.CityMapper;
import org.styli.services.order.pojo.GetOrderConsulValues;
import org.styli.services.order.pojo.InventoryMapping;
import org.styli.services.order.pojo.NavikAddress;
import org.styli.services.order.pojo.NavikDetails;
import org.styli.services.order.pojo.Navikinfos;
import org.styli.services.order.pojo.OrderKeyDetails;
import org.styli.services.order.pojo.QuoteV7DTO;
import org.styli.services.order.pojo.PayfortDetails;
import org.styli.services.order.pojo.PendingOrderNotfcnDetails;
import org.styli.services.order.pojo.QuoteDTO;
import org.styli.services.order.pojo.WmsDetails;
import org.styli.services.order.pojo.mulin.ProductResponseBody;
import org.styli.services.order.pojo.eas.EASQuoteSpend;
import org.styli.services.order.pojo.oms.BankSwiftCode;
import org.styli.services.order.pojo.request.Order.CreateOrderRequestV2;
import org.styli.services.order.pojo.request.Order.OmsOrderListRequest;
import org.styli.services.order.pojo.request.ProductStatusRequest;
import org.styli.services.order.pojo.response.AddressMapperCity;
import org.styli.services.order.pojo.response.AddressMapperResponse;
import org.styli.services.order.pojo.response.CustomCouponRedemptionV5Response;
import org.styli.services.order.pojo.response.ProductInventoryRes;
import org.styli.services.order.pojo.response.OmsOrderupdateresponse;
import org.styli.services.order.pojo.response.PriceDetails;
import org.styli.services.order.pojo.response.Order.OmsOrderresponsedto;
import org.styli.services.order.pojo.tax.TaxObject;
import org.styli.services.order.repository.SalesShipmentRepository;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.repository.Rma.AmastyRmaReasonRepository;
import org.styli.services.order.repository.Rma.AmastyRmaRequestItemRepository;
import org.styli.services.order.repository.Rma.AmastyRmaRequestRepository;
import org.styli.services.order.repository.Rma.AmastyRmaStatusRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditHistoryRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditRepository;
import org.styli.services.order.repository.SalesOrder.ProxyOrderRepository;
import org.styli.services.order.repository.SalesOrder.ReturnShipmentTrackerRepository;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoRepository;
import org.styli.services.order.repository.SalesOrder.SalesInvoiceRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusHistoryRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusLabelRepository;
import org.styli.services.order.repository.SalesOrder.ShipmentTrackerRepository;
import org.styli.services.order.repository.SalesOrder.StatusChaneHistoryRepository;
import org.styli.services.order.service.impl.ConfigServiceImpl;
import org.styli.services.order.service.impl.EASServiceImpl;
import org.styli.services.order.service.impl.SalesOrderCustomerServiceImpl;
import org.styli.services.order.service.impl.SalesOrderRMAServiceImpl;
import org.styli.services.order.service.impl.SalesOrderServiceImpl;
import org.styli.services.order.service.impl.SalesOrderServiceV2Impl;
import org.styli.services.order.service.impl.SalesOrderServiceV3Impl;
import org.styli.services.order.service.impl.child.GetOrderById;
import org.styli.services.order.service.impl.child.GetOrderList;
import org.styli.services.order.service.impl.child.OrderShipmentHelper;
import org.styli.services.order.utility.consulValues.PromoRedemptionValues;
import org.styli.services.order.utility.consulValues.PromoValues;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@SpringBootTest(classes = { OrderHelperV2Test.class })
public class OrderHelperV2Test extends AbstractTestNGSpringContextTests {
	private org.styli.services.order.model.Customer.CustomerEntity customerEntity;
	@InjectMocks
	OrderHelperV2 orderHelperV2;
	@InjectMocks
	OrderOmsController orderOmsController;
	@InjectMocks
	OmsOrderupdateresponse omsOrderupdateresponse;
	@InjectMocks
	RefundHelper refundHelper;
	@InjectMocks
	OrderShipmentHelper orderShipmentHelper;
	@InjectMocks
	private org.styli.services.order.utility.Constants constants;
	@Mock
	JwtValidator validator;
	@Mock
	ShipmentTrackerRepository shipmentTrackerRepository;
	@InjectMocks
	ConfigServiceImpl configService;

	@Mock
	OrderHelper orderHelper;
	@Mock
	AmastyRmaStatusRepository amastyRmaStatusRepository;
	@Mock
	AmastyRmaRequestItemRepository amastyRmaRequestItemRepository;
	@Mock
	OrderHelper orderHelpermock;

	@InjectMocks
	PaymentDtfHelper paymentDtfHelper;
	@InjectMocks
	SalesOrderServiceImpl salesOrderService;
	@InjectMocks
	GetOrderList getOrderList;
	@InjectMocks
	GetOrderById getOrderById;
	@InjectMocks
	OrderEntityConverter orderEntityConverter;
	@Mock
	SalesOrderStatusHistoryRepository salesOrderStatusHistoryRepository;

	@Mock
	AmastyStoreCreditHistoryRepository amastyStoreCreditHistoryRepository;
	@Mock
	SalesCreditmemoRepository salesCreditmemoRepository;
	@Mock
	SalesOrderStatusLabelRepository salesOrderStatusLabelRepository;
	@Mock
	ProxyOrderRepository proxyOrderRepository;
	@Mock
	AmastyRmaReasonRepository amastyRmaReasonRepository;
	@Mock
	SalesOrderGridRepository salesOrderGridRepository;
	@Mock
	SalesOrderRepository salesOrderRepository;
	@Mock
	AmastyRmaRequestRepository amastyRmaRequestRepository;
	@Mock
	ReturnShipmentTrackerRepository returnShipmentTrackerRepository;
	@InjectMocks
	OmsorderentityConverter omsorderentityConverter;
	@Mock
	PaymentRefundHelper paymentDtfRefundHelper;
	@InjectMocks
	StaticComponents staticComponents;
	@InjectMocks
	OmsOrderresponsedto omsOrderresponsedto;
	@Mock
	StatusChaneHistoryRepository statusChaneHistoryRepository;
	@Mock
	SalesInvoiceRepository salesInvoiceRepository;
	@Mock
	KafkaTemplate<String, Object> kafkaTemplate;
	@InjectMocks
	SalesOrderServiceV2Impl salesOrderServiceV2;

	@InjectMocks
	SalesOrderServiceV3Impl salesOrderServiceV3;
	@InjectMocks
	SalesOrderRMAServiceImpl salesOrderRMAService;

	Map<String, String> requestHeader;
	private List<Stores> storeList;
	SalesOrder salesOrder;
	OmsOrderListRequest request;
	@Mock
	FirebaseAuthentication FirebaseAuthentication;
	@InjectMocks
	private MulinHelper mulinHelper;
	@Mock
	private AmastyStoreCreditRepository amastyStoreCreditRepository;
	@Mock
	private RestTemplate restTemplate;
	@Mock
	private KafkaServiceImpl kafkaService;
	@Mock
	private PubSubServiceImpl pubSubServiceImpl;
	@Mock
	private EASServiceImpl eASServiceImpl;

	@Mock
	SalesShipmentRepository salesshipmentRepository;
	@Mock
	private SalesShipmentRepository salesShipmentRepository;
	@InjectMocks
	private OrderpushHelper orderpushHelper;
	@InjectMocks
	private SalesOrderCustomerServiceImpl salesOrderCustomerService;

	@BeforeClass
	public void beforeClass() {

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
	}

	@BeforeTest
	public void beforeTest() {
		System.out.println("Initialise @BeforeTest ");
		MockitoAnnotations.initMocks(this);

		customerEntity = new org.styli.services.order.model.Customer.CustomerEntity();
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
//		ReflectionTestUtils.setField(configService, "internalAuthBearerToken", "token,dwd,1,2");
//		ReflectionTestUtils.setField(configService, "externalAuthBearerToken", "token,ds,1,1");
	}

	@Test
	public void publishPreferredPayment_usesKafka_whenFlagTrue() {
		setStaticData();
		setorderCred();
		// ensure kafka branch
		ReflectionTestUtils.setField(orderHelperV2, "preferredPaymentTopic", "pp-topic");
		SalesOrder order = new SalesOrder();
		order.setCustomerId(123);
		order.setStoreId(10);
		orderHelperV2.publishPreferredPaymentIfValid("payfort_fort_cc", order);
		Mockito.verify(kafkaService, atLeast(1)).publishPreferredPaymentToKafka(any());
	}

	@Test
	public void publishPreferredPayment_usesPubSub_whenFlagFalse() {
		setStaticData();
		setorderCred();
		// flip the flag to false
		PromoValues v = new PromoValues();
		PromoRedemptionValues prv = new PromoRedemptionValues();
		prv.setDefaultRedemptionEndpoint("");
		prv.setEnabled(true);
		v.setPromoRedemptionUrl(prv);
		GetOrderConsulValues val = (GetOrderConsulValues) ReflectionTestUtils.getField(constants, "orderCredentials");
		val.setKafkaForPreferredPaymentFeature(false);
		ReflectionTestUtils.setField(constants, "orderCredentials", val);
		ReflectionTestUtils.setField(orderHelperV2, "preferredPaymentTopic", "pp-topic");
		SalesOrder order = new SalesOrder();
		order.setCustomerId(123);
		order.setStoreId(10);
		orderHelperV2.publishPreferredPaymentIfValid("payfort_fort_cc", order);
		Mockito.verify(pubSubServiceImpl, atLeast(1)).publishPreferredPaymentMethodToPubSub(anyString(), anyString(), anyInt(), anyInt());
	}

	@Test
	public void publishPreferredPaymentSplit_usesKafka_whenFlagTrue() {
		setStaticData();
		setorderCred();
		ReflectionTestUtils.setField(orderHelperV2, "preferredPaymentTopic", "pp-topic");
		SplitSalesOrder split = new SplitSalesOrder();
		split.setCustomerId(555);
		split.setStoreId(22);
		orderHelperV2.publishPreferredPaymentIfValidSplit("apple_pay", split);
		Mockito.verify(kafkaService, atLeast(1)).publishPreferredPaymentToKafka(any());
	}
	public void checkApplePayRetryRequest_returnsTrue_whenFailedApplePayPresent() {
		QuoteDTO quote = new QuoteDTO();
		quote.setSelectedPaymentMethod("apple_pay");
		List<String> failed = new ArrayList<>();
		failed.add("applePay");
		quote.setFailedPaymentMethod(failed);
		boolean result = orderHelperV2.checkApplePayRetryRequest(quote);
		org.testng.Assert.assertTrue(result);
	}

	@Test
	public void getInternalAuthorizationOfRestAPI_returnsFirstToken() {
		String token = orderHelperV2.getInternalAuthorizationOfRestAPI("bearer-token,meta,more");
		org.testng.Assert.assertEquals(token, "bearer-token");
	}

	@Test
	public void createOrderObjectToPersistTest() {
		setStaticData();
		setorderCred();
		setSalseOrderData();
		QuoteDTO res = new QuoteDTO();
		res.setCustomerId("1");
		res.setSelectedPaymentMethod("payfort_fort_cc");
		res.setGrandTotal("10");
		res.setStoreId("1");
		AddressObject add = new AddressObject();
		add.setArea("blr");
		add.setCity("bbsr");
		CityMapper citymap = new CityMapper();
		citymap.setMaxSla("100");
		citymap.setFstdlvrythrsTime("10");
		citymap.setEsimatedDate(new Date().toString());
		add.setCityMapper(citymap);

		res.setShippingAddress(add);
		res.setStoreCreditApplied("10");
		CatalogProductEntityForQuoteDTO dto = new CatalogProductEntityForQuoteDTO();
		dto.setQuantity("1");
		dto.setSku("1");
		List<CatalogProductEntityForQuoteDTO> products = new ArrayList<>();
		products.add(dto);
		res.setProducts(products);
		EASQuoteSpend easspent = new EASQuoteSpend();
		easspent.setIsCoinApplied(1);
		easspent.setBaseCurrencyValue("1");
		easspent.setStoreCoinValue("1");
		res.setCoinDiscountData(easspent);
		when(salesOrderRepository.findByIncrementId(anyString())).thenReturn(salesOrder);
		Stores stores = new Stores();
		stores.setCurrencyConversionRate(new BigDecimal(1));
		SalesOrder order = orderHelperV2.createOrderObjectToPersist(res, "payfort", stores, "1", "10.2.1.0", 1, "", "",
				"", "", "", true, true);
		assertNotNull(order);

	}

	@Test
	public void extractOrderItemsFromQuote() {
		setStaticData();
		setorderCred();
		setSalseOrderData();
		QuoteDTO qoute = setQiuteData();

		when(salesOrderRepository.findByIncrementId(anyString())).thenReturn(salesOrder);

		Stores stores = new Stores();
		stores.setCurrencyConversionRate(new BigDecimal(1));
		stores.setStoreId("1");
		orderHelperV2.extractOrderItemsFromQuote(qoute, salesOrder, stores,null);
//		assertNotNull(order);

	}

	@Test
	public void createOrderGridest() {
		setStaticData();
		setorderCred();
		setSalseOrderData();
		QuoteDTO qoute = setQiuteData();
		AddressObject add = new AddressObject();
		add.setArea("blr");
		add.setCity("bbsr");
		CityMapper citymap = new CityMapper();
		citymap.setMaxSla("100");
		citymap.setFstdlvrythrsTime("10");
		citymap.setEsimatedDate(new Date().toString());
		add.setCityMapper(citymap);
		when(salesOrderRepository.findByIncrementId(anyString())).thenReturn(salesOrder);
		when(orderHelper.getCustomerDetails(anyInt(), any())).thenReturn(customerEntity);
		Stores stores = new Stores();
		stores.setCurrencyConversionRate(new BigDecimal(1));
		stores.setStoreId("1");
		orderHelperV2.createOrderGrid(qoute, salesOrder, "payfort", add, stores, 1, "3");
		verify(salesOrderGridRepository, atLeast(1)).saveAndFlush(any());
	}

	@Test
	public void reedmeExternalCouponTest() {
		setStaticData();
		setorderCred();
		setSalseOrderData();
		QuoteDTO qoute = setQiuteData();
		AddressObject add = new AddressObject();
		add.setArea("blr");
		add.setCity("bbsr");
		CityMapper citymap = new CityMapper();
		citymap.setMaxSla("100");
		citymap.setFstdlvrythrsTime("10");
		citymap.setEsimatedDate(new Date().toString());
		add.setCityMapper(citymap);
		when(salesOrderRepository.findByIncrementId(anyString())).thenReturn(salesOrder);
		when(orderHelper.getCustomerDetails(anyInt(), any())).thenReturn(customerEntity);
		Stores stores = new Stores();
		stores.setCurrencyConversionRate(new BigDecimal(1));
		stores.setStoreId("1");
		CustomCouponRedemptionV5Response body = new CustomCouponRedemptionV5Response();
		body.setCode(200);
		body.setTrackingId("1");

		ResponseEntity<CustomCouponRedemptionV5Response> response = new ResponseEntity<>(HttpStatus.OK).ok(body);
		Mockito.when(restTemplate.exchange(Mockito.any(URI.class), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(CustomCouponRedemptionV5Response.class)))
				.thenReturn(response);
		orderHelperV2.reedmeExternalCoupon(qoute, stores, salesOrder, false);
		verify(salesOrderRepository, atLeast(1)).saveAndFlush(any());
	}

	@Test
	public void setEstmateDateTest() {
		setStaticData();
		setorderCred();
		setSalseOrderData();
		QuoteDTO qoute = setQiuteData();

		AddressMapperResponse amr = new AddressMapperResponse();
		amr.setStatusCode("200");
		AddressMapperCity city = new AddressMapperCity();

		city.setEstimatedDate("2023-07-04 00:00:00");
		amr.setResponse(city);
		ResponseEntity<AddressMapperResponse> response = new ResponseEntity<>(HttpStatus.OK).ok(amr);
		Mockito.when(restTemplate.exchange(Mockito.any(URI.class), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(AddressMapperResponse.class))).thenReturn(response);

		orderHelperV2.setEstmateDate(qoute, salesOrder);
		verify(restTemplate, atLeast(1)).exchange(Mockito.any(URI.class), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(AddressMapperResponse.class));
	}

	@Test
	public void createOrderItemsProductDetailsTest() {
		setStaticData();
		setorderCred();
		setSalseOrderData();
		QuoteDTO qoute = setQiuteData();
		Stores stores = new Stores();
		stores.setCurrencyConversionRate(new BigDecimal(1));
		stores.setStoreId("1");
		orderHelperV2.createOrderItemsProductDetails(qoute, salesOrder, stores, false);
	}

	@Test
	public void createV3ProxyOrder_savesToRepository() {
		setStaticData();
		setorderCred();
		setSalseOrderData();
		QuoteV7DTO quote = new QuoteV7DTO();
		quote.setQuoteId("Q7");
		quote.setTabbyPaymentId("PID");
		SalesOrder order = new SalesOrder();
		order.setIncrementId("INC7");
		order.setStoreId(1);
		order.setCustomerId(2);
		order.setCustomerEmail("a@b.com");
		CreateOrderRequestV2 req = new CreateOrderRequestV2();
		ProxyOrder saved = new ProxyOrder();
		when(proxyOrderRepository.save(any())).thenReturn(saved);
		ProxyOrder res = orderHelperV2.createV3ProxyOrder(quote, "payfort_fort_cc", order, req);
		assertNotNull(res);
	}

	@Test
	public void createProxyOrderTest() {
		setStaticData();
		setorderCred();
		setSalseOrderData();
		QuoteDTO qoute = setQiuteData();
		Stores stores = new Stores();
		stores.setCurrencyConversionRate(new BigDecimal(1));
		stores.setStoreId("1");
		CreateOrderRequestV2 req = new CreateOrderRequestV2();
		req.setOrderIncrementId("1");
		req.setRetryPaymentReplica(false);
		req.setQuoteId("1");
		req.setStoreId(1);

		orderHelperV2.createProxyOrder(qoute, "payfort", salesOrder, req);
		orderHelperV2.findProxyOrderByPaymentId("1");
		verify(proxyOrderRepository, atLeast(1)).save(any());
	}

	@Test
	public void updateProxyOrderStatusByPaymentIdTest() {
		setStaticData();
		setorderCred();
		ProxyOrder order = new ProxyOrder();
		when(proxyOrderRepository.findByPaymentId(anyString())).thenReturn(order);
		when(proxyOrderRepository.findById(anyLong())).thenReturn(Optional.of(order));
		orderHelperV2.updateProxyOrderStatusByPaymentId("1", "true");
		orderHelperV2.updateProxyOrderInventoryRelease(1l, false);

		verify(proxyOrderRepository, atLeast(1)).saveAndFlush(any());

	}

	@Test
	public void getInventoryInfoOfQuoteProduct_returnsBody_whenOk() throws Exception {
		setStaticData();
		setorderCred();
		ProductStatusRequest req = new ProductStatusRequest();
		ReflectionTestUtils.setField(orderHelperV2, "internalHeaderBearerToken", "tok,rest");
		ProductInventoryRes body = new ProductInventoryRes();
		ResponseEntity<ProductInventoryRes> response = new ResponseEntity<>(HttpStatus.OK).ok(body);
		when(restTemplate.exchange(anyString(), Mockito.eq(HttpMethod.POST), Mockito.any(HttpEntity.class), Mockito.eq(ProductInventoryRes.class))).thenReturn(response);
		ProductInventoryRes res = orderHelperV2.getInventoryInfoOfQuoteProduct(req);
		assertNotNull(res);
	}

	@Test
	public void updateProductDetails_setsAttributesOnMatchingItem() {
		SalesOrder order = new SalesOrder();
		SalesOrderItem item = new SalesOrderItem();
		item.setSku("SKU1");
		java.util.Set<SalesOrderItem> set = new java.util.HashSet<>();
		set.add(item);
		order.setSalesOrderItem(set);
		ProductResponseBody resp = new ProductResponseBody();
		Map<String,Object> attrs = new HashMap<>();
		attrs.put("test","test");
		resp.setProductAttributes(attrs);
		orderHelperV2.updateProductDetails(order);
		org.testng.Assert.assertNotNull(order.getSalesOrderItem().iterator().next().getProductAttributes());
	}

	@Test
	public void updateSalesOrderIsPayfortAuthorizedTest() {
		setStaticData();
		setorderCred();
		setSalseOrderData();
		ProxyOrder order = new ProxyOrder();
		orderHelperV2.updateSalesOrderIsPayfortAuthorized(salesOrder, "payfort");
		orderHelperV2.updateSalesOrderIsPayfortAuthorized(salesOrder, "payfort_fort_cc");
		orderHelperV2.saveUuidOfUserInOrder(salesOrder, "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");

	}

	private QuoteDTO setQiuteData() {
		// TODO Auto-generated method stub
		QuoteDTO res = new QuoteDTO();
		res.setCustomerId("1");
		res.setSelectedPaymentMethod("payfort_fort_cc");
		res.setGrandTotal("10");
		res.setStoreId("1");
		AddressObject add = new AddressObject();
		add.setArea("blr");
		add.setCity("bbsr");
		CityMapper citymap = new CityMapper();
		citymap.setMaxSla("100");
		citymap.setFstdlvrythrsTime("10");
		citymap.setEsimatedDate(new Date().toString());
		add.setCityMapper(citymap);

		res.setShippingAddress(add);
		res.setStoreCreditApplied("10");
		CatalogProductEntityForQuoteDTO product = new CatalogProductEntityForQuoteDTO();
		product.setQuantity("1");
		product.setSku("1");
		PriceDetails pd = new PriceDetails();
		pd.setPrice("10");
		pd.setSpecialPrice("5");
		pd.setDroppedPrice("5");
		product.setPrices(pd);
		product.setPrice("10");
		product.setDiscountPercent("50");
		product.setTaxAmount("0");
		product.setTaxPercent("0");
		product.setDiscountTaxCompensationAmount("0");
		product.setParentProductId("1");
		product.setQuantity("1");
		product.setLandedCost("5");
		product.setGift(true);

		AppliedCouponValue acv = new AppliedCouponValue();
		acv.setIsGiftVoucher(true);
		product.setAppliedCouponValue(Arrays.asList(acv));

		TaxObject taxob = new TaxObject();
		taxob.setTaxIGST("0");
		taxob.setTaxIGSTAmount("0");
		taxob.setTaxCGST("0");
		taxob.setTaxCGSTAmount("0");
		taxob.setTaxSGST("0");
		taxob.setTaxSGSTAmount("0");
		product.setTaxObj(taxob);
		List<CatalogProductEntityForQuoteDTO> productslist = new ArrayList<>();
		productslist.add(product);
		res.setProducts(productslist);
		EASQuoteSpend easspent = new EASQuoteSpend();
		easspent.setIsCoinApplied(1);
		easspent.setBaseCurrencyValue("1");
		easspent.setStoreCoinValue("1");
		res.setCoinDiscountData(easspent);
		return res;
	}

	private void setStaticData() {
		// TODO Auto-generated method stub
		ReflectionTestUtils.setField(orderHelperV2, "addressMapperFlag", "1");
		ReflectionTestUtils.setField(orderHelperV2, "regionValue", "IN");
		Map<String, Integer> m = new HashMap<>();
		m.put("key", 1);
		ReflectionTestUtils.setField(staticComponents, "statusStepMap", m);
		ReflectionTestUtils.setField(orderHelperV2, "jwtsaltOldSecret", "secret");
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
		wmsd.setWmsOrderPushMinutes(1);
		val.setWms(wmsd);
		PayfortDetails payFort = new PayfortDetails();
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
		prv.setAllowInternalUsers(true);
		prv.setExcludeEmailId(new ArrayList<String>());
		prv.setRedemptionEndpoint("endpoint");
		v.setPromoRedemptionUrl(prv);

		Map<String, String> m = new HashMap<>();
		m.put("promo/promo", new Gson().toJson(v).toString());
		ReflectionTestUtils.setField(constants, "promoConsulValues", m);

	}

	private void setAuthenticateData() {
		// TODO Auto-generated method stub
		JwtUser jwtUser = new JwtUser();
		jwtUser.setCustomerId(1);
		when(validator.validate(anyString())).thenReturn(jwtUser);
	}

	private void setSalseOrderData() {
		salesOrder = new SalesOrder();
		salesOrder.setCustomerId(1);
		salesOrder.setCustomerEmail("test@stylishop.com");
		salesOrder.setIncrementId("1");
		salesOrder.setCustomerIsGuest(2);
		salesOrder.setEntityId(1);
		salesOrder.setStoreId(1);
		salesOrder.setStatus("packed");
		salesOrder.setGrandTotal(new BigDecimal(10));
		salesOrder.setWmsStatus(1);
		salesOrder.setAmstorecreditAmount(new BigDecimal(10));
		salesOrder.setStoreToBaseRate(new BigDecimal(1));
		salesOrder.setAmstorecreditAmount(new BigDecimal(1));
		LocalDateTime currentTime = LocalDateTime.now();
		salesOrder.setCreatedAt(Timestamp.valueOf(currentTime));
		salesOrder.setWmsStatus(1);
		salesOrder.setExtOrderId("0");

		SalesOrderPayment pay = new SalesOrderPayment();
		pay.setMethod("payfort_fort_cc");
		Set<SalesOrderPayment> payitem = new HashSet<>();
		payitem.add(pay);
		salesOrder.setSalesOrderPayment(payitem);
		SalesOrderItem items = new SalesOrderItem();
		items.setSku("01");
		items.setItemId(1);
		items.setProductType("prepaid");
		items.setPriceInclTax(new BigDecimal(10));
		items.setQtyOrdered(new BigDecimal(2));
		items.setQtyCanceled(new BigDecimal(1));
		items.setDiscountAmount(new BigDecimal(5));
		items.setRowTotalInclTax(new BigDecimal(10));
		items.setParentOrderItem(items);

		Set<SalesOrderItem> setitem = new HashSet<>();
		setitem.add(items);
		salesOrder.setSalesOrderItem(setitem);
		Set<SalesShipmentTrack> set = new HashSet<>();
		SalesShipmentTrack track = new SalesShipmentTrack();
		track.setTrackNumber("1");
		salesOrder.setSalesShipmentTrack(set);
		set.add(track);
	}

}
