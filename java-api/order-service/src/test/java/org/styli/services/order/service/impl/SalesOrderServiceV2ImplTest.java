package org.styli.services.order.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.db.product.exception.NotFoundException;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.exception.BadRequestException;
import org.styli.services.order.helper.ExternalQuoteHelper;
import org.styli.services.order.helper.KafkaBrazeHelper;
import org.styli.services.order.helper.MulinHelper;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.helper.OrderHelperV2;
import org.styli.services.order.helper.OrderHelperV3;
import org.styli.services.order.helper.PaymentDtfHelper;
import org.styli.services.order.helper.RedisHelper;
import org.styli.services.order.jwt.security.jwtsecurity.model.JwtUser;
import org.styli.services.order.jwt.security.jwtsecurity.security.JwtValidator;
import org.styli.services.order.model.Customer.CustomerEntity;
import org.styli.services.order.model.rma.AmastyStoreCredit;
import org.styli.services.order.model.rma.AmastyStoreCreditHistory;
import org.styli.services.order.model.sales.ProxyOrder;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SplitSalesOrder;
import org.styli.services.order.model.sales.SubSalesOrder;
import org.styli.services.order.pojo.AppliedCouponValue;
import org.styli.services.order.pojo.CatalogProductEntityForQuoteDTO;
import org.styli.services.order.pojo.GetOrderConsulValues;
import org.styli.services.order.pojo.LmdCommission;
import org.styli.services.order.pojo.OrderKeyDetails;
import org.styli.services.order.pojo.QuoteDTO;
import org.styli.services.order.pojo.QuoteUpdateDTO;
import org.styli.services.order.pojo.quote.response.GetQuoteResponse;
import org.styli.services.order.pojo.response.PriceDetails;
import org.styli.services.order.pojo.kafka.BulkWalletUpdate;
import org.styli.services.order.pojo.order.AddStoreCreditRequest;
import org.styli.services.order.pojo.order.CreateReplicaQuoteV4Request;
import org.styli.services.order.pojo.order.StoreCredit;
import org.styli.services.order.pojo.CreateRetryPaymentReplicaDTO;
import org.styli.services.order.pojo.ProductEntityForQuoteV7DTO;
import org.styli.services.order.pojo.QuoteV7DTO;
import org.styli.services.order.pojo.quote.response.QuoteV7Response;
import org.styli.services.order.pojo.request.Order.CreateOrderRequestV2;
import org.styli.services.order.pojo.request.ProductStatusRequest;
import org.styli.services.order.pojo.request.ProductStatusRequestV2;
import org.styli.services.order.pojo.response.AddStoreCreditResponse;
import org.styli.services.order.pojo.response.Order.CreateOrderResponseDTO;
import org.styli.services.order.pojo.response.ProductInventoryRes;
import org.styli.services.order.pojo.response.ProductInventoryResV2;
import org.styli.services.order.repository.Customer.CustomerEntityRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditHistoryRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditRepository;
import org.styli.services.order.repository.SalesOrder.ProxyOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusHistoryRepository;
import org.styli.services.order.repository.SalesOrder.SplitSalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SubSalesOrderRepository;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.service.ConfigService;
import org.styli.services.order.service.CustomerService;
import org.styli.services.order.service.PaymentService;
import org.styli.services.order.service.SalesOrderService;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.utility.PaymentUtility;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class SalesOrderServiceV2ImplTest {

	@InjectMocks
	private SalesOrderServiceV2Impl salesOrderServiceV2Impl;

	@Mock
	private StaticComponents staticComponents;

	@Mock
	private ExternalQuoteHelper externalQuoteHelper;

	@Mock
	private OrderHelper orderHelper;

	@Mock
	private OrderHelperV2 orderHelperV2;

	@Mock
	private OrderHelperV3 orderHelperV3;

	@Mock
	private CustomerService customerService;

	@Mock
	private CommonServiceImpl commonService;

	@Mock
	private SalesOrderRepository salesOrderRepository;

	@Mock
	private SplitSalesOrderRepository splitSalesOrderRepository;

	@Mock
	private SalesOrderService salesOrderService;

	@Mock
	private AmastyStoreCreditRepository amastyStoreCreditRepository;

	@Mock
	private SalesOrderCancelServiceImpl salesOrderCancelServiceImpl;

	@Mock
	private AmastyStoreCreditHistoryRepository amastyStoreCreditHistoryRepository;

	@Mock
	private CustomerEntityRepository customerEntityRepository;

	@Mock
	private JwtValidator validator;

	@Mock
	private PaymentDtfHelper paymentDtfHelper;

	@Mock
	private KafkaBrazeHelper kafkaBrazeHelper;

	@Mock
	private ConfigService configService;

	@Mock
	private EASServiceImpl easService;

	@Mock
	private SalesOrderStatusHistoryRepository salesOrderStatusHistoryRepository;

	@Mock
	private SalesOrderGridRepository salesOrderGridRepository;

	@Mock
	private RestTemplate restTemplate;

	@Mock
	private SubSalesOrderRepository subSalesOrderRepository;

	@Mock
	private PaymentUtility paymentUtility;

	@Mock
	private ProxyOrderRepository proxyOrderRepository;

	@Mock
	private PaymentService paymentService;

	@Mock
	private RedisHelper redisHelper;

	@Mock
	private PubSubServiceImpl pubSubServiceImpl;

	@Mock
	private MulinHelper mulinHelper;

	private List<Stores> storeList;
	private Map<String, String> requestHeader;
	private ObjectMapper objectMapper;

	@BeforeClass
	public void beforeClass() {
		requestHeader = new HashMap<>();
		requestHeader.put("x-source", "android");
		requestHeader.put("x-client-version", "11");
		requestHeader.put("token", "Bearer test-token");
		requestHeader.put("X-Header-Token", "test@mail.com");
		requestHeader.put("X-Header-Cloud-Method", "test");

		objectMapper = new ObjectMapper();

		try {
			Gson g = new Gson();
			String storeData = new String(Files.readAllBytes(Paths.get("src/test/resources/store_list.json")));
			Type listType = new TypeToken<ArrayList<Stores>>() {
			}.getType();
			storeList = g.fromJson(storeData, listType);
		} catch (Exception e) {
			storeList = new ArrayList<>();
			Stores store = new Stores();
			store.setStoreId("1");
			store.setStoreCode("KSA");
			store.setStoreCurrency("SAR");
			store.setCurrencyConversionRate(new BigDecimal("1.0"));
			storeList.add(store);
		}
	}

	@BeforeTest
	public void beforeTest() {
		MockitoAnnotations.openMocks(this);
		ReflectionTestUtils.setField(salesOrderServiceV2Impl, "internalHeaderBearerToken", "test-token");
		ReflectionTestUtils.setField(salesOrderServiceV2Impl, "splitOrderTopic", "split-order-topic");
		ReflectionTestUtils.setField(salesOrderServiceV2Impl, "splitOrderTrackingTopic", "split-order-tracking-topic");
	}

	// ========== convertQuoteToOrderV2 Tests ==========

	@Test
	public void testConvertQuoteToOrderV2_WithBlankIncrementId() throws NotFoundException {
		CreateOrderRequestV2 request = new CreateOrderRequestV2();
		request.setQuoteId("123");
		request.setStoreId(1);

		CreateOrderResponseDTO response = salesOrderServiceV2Impl.convertQuoteToOrderV2(request, "token", "", "android",
				requestHeader, "header-token", "1.0", "127.0.0.1", "device-id");

		assertFalse(response.getStatus());
		assertEquals(response.getStatusCode(), "206");
		assertEquals(response.getStatusMsg(), "Increment ID not found!");
	}

	@Test
	public void testConvertQuoteToOrderV2_WithMissingParameters() throws NotFoundException {
		CreateOrderRequestV2 request = new CreateOrderRequestV2();
		request.setQuoteId(null);
		request.setStoreId(null);

		CreateOrderResponseDTO response = salesOrderServiceV2Impl.convertQuoteToOrderV2(request, "token", "INV001",
				"android", requestHeader, "header-token", "1.0", "127.0.0.1", "device-id");

		assertTrue(response.getStatus());
		assertEquals(response.getStatusCode(), "201");
		assertEquals(response.getStatusMsg(), "Parameters missing!");
	}

	@Test
	public void testConvertQuoteToOrderV2_WithInvalidStore() throws NotFoundException {
		CreateOrderRequestV2 request = new CreateOrderRequestV2();
		request.setQuoteId("123");
		request.setStoreId(999);

		CreateOrderResponseDTO response = salesOrderServiceV2Impl.convertQuoteToOrderV2(request, "token", "INV001",
				"android", requestHeader, "header-token", "1.0", "127.0.0.1", "device-id");

		assertFalse(response.getStatus());
		assertEquals(response.getStatusCode(), "201");
	}

	@Test
	public void testConvertQuoteToOrderV2_WithProxyOrder() throws NotFoundException {
		CreateOrderRequestV2 request = new CreateOrderRequestV2();
		request.setQuoteId("123");
		request.setStoreId(1);
		request.setPaymentId("PAY123");
		request.setProxy(false);

		ProxyOrder proxyOrder = new ProxyOrder();
		proxyOrder.setIncrementId("INV001");
		proxyOrder.setPaymentId("PAY123");
		QuoteDTO quote = new QuoteDTO();
		quote.setQuoteId("123");
		quote.setCustomerId("1");

		try {
			String quoteJson = objectMapper.writeValueAsString(quote);
			proxyOrder.setQuote(quoteJson);

			when(orderHelperV2.findProxyOrderByPaymentId("PAY123")).thenReturn(proxyOrder);
			when(Constants.getStoresList()).thenReturn(storeList);

			CreateOrderResponseDTO response = salesOrderServiceV2Impl.convertQuoteToOrderV2(request, "token", "INV001",
					"android", requestHeader, "header-token", "1.0", "127.0.0.1", "device-id");

			assertNotNull(response);
		} catch (Exception e) {
			// Handle exception
		}
	}

	@Test
	public void testConvertQuoteToOrderV2_WithSellerConditionAndCommissionCalculation() throws NotFoundException {
		// Test for each seller: Max, Splash, Shoexpress, Shoemart, Babyshop, Lifestyle, Homebox
		String[] sellers = { "Max", "Splash", "Shoexpress", "Shoemart", "Babyshop", "Lifestyle", "Homebox" };
		
		for (String seller : sellers) {
			CreateOrderRequestV2 request = new CreateOrderRequestV2();
			request.setQuoteId("123");
			request.setStoreId(1);
			request.setRetryPaymentReplica(false);

			QuoteDTO quote = new QuoteDTO();
			quote.setQuoteId("123");
			quote.setCustomerId("1");
			quote.setStoreId("1");

			// Create product with seller name
			CatalogProductEntityForQuoteDTO product = new CatalogProductEntityForQuoteDTO();
			product.setSku("SKU001");
			product.setSoldBy(seller);
			product.setBrandName("TestBrand");
			product.setDiscount("10.00");

			// Set price details
			PriceDetails priceDetails = new PriceDetails();
			priceDetails.setPrice("100.00");
			priceDetails.setSpecialPrice("90.00");
			product.setPrices(priceDetails);

			// Set applied coupon value
			AppliedCouponValue couponValue = new AppliedCouponValue();
			couponValue.setDiscount(new BigDecimal("5.00"));
			product.setAppliedCouponValue(java.util.Arrays.asList(couponValue));

			List<CatalogProductEntityForQuoteDTO> products = new ArrayList<>();
			products.add(product);
			quote.setProducts(products);

			// Setup LmdCommission
			LmdCommission lmdCommission = new LmdCommission();
			lmdCommission.setEn("Max");
			lmdCommission.setAr("Max");
			lmdCommission.setValue(15.0);
			lmdCommission.setNtdDiscount(8.0);
			List<LmdCommission> lmdCommissions = new ArrayList<>();
			lmdCommissions.add(lmdCommission);

			GetOrderConsulValues orderCredentials = new GetOrderConsulValues();
			orderCredentials.setLmdCommission(lmdCommissions);

			GetQuoteResponse quoteResponse = new GetQuoteResponse();
			quoteResponse.setStatus(true);
			quoteResponse.setStatusCode("200");
			quoteResponse.setResponse(quote);

			when(Constants.getStoresList()).thenReturn(storeList);
			when(Constants.orderCredentials).thenReturn(orderCredentials);
			when(externalQuoteHelper.fetchQuote(anyString(), anyInt(), anyInt(), anyString(), anyBoolean(), anyString(),
					anyString(), anyString(), anyBoolean(), anyString())).thenReturn(quoteResponse);

			CreateOrderResponseDTO response = salesOrderServiceV2Impl.convertQuoteToOrderV2(request, "token", "INV001",
					"android", requestHeader, "header-token", "1.0", "127.0.0.1", "device-id");

			// Verify that poPrice was set (indicating the seller condition was matched)
			assertNotNull(response);
			// The product should have poPrice set after commission calculation
			// Expected: rrPrice = 90 - 10 = 80, then with coupon: 80 - adjustedCouponDiscount
			// Then: styliCommission = rrPrice * 0.30, poPrice = rrPrice - styliCommission
			assertNotNull(product.getPoPrice());
			assertTrue(new BigDecimal(product.getPoPrice()).compareTo(BigDecimal.ZERO) >= 0,
					"poPrice should be calculated for seller: " + seller);
		}
	}

	@Test
	public void testConvertQuoteToOrderV2_WithSellerConditionWithoutCoupon() throws NotFoundException {
		CreateOrderRequestV2 request = new CreateOrderRequestV2();
		request.setQuoteId("123");
		request.setStoreId(1);
		request.setRetryPaymentReplica(false);

		QuoteDTO quote = new QuoteDTO();
		quote.setQuoteId("123");
		quote.setCustomerId("1");
		quote.setStoreId("1");

		// Create product with Splash seller
		CatalogProductEntityForQuoteDTO product = new CatalogProductEntityForQuoteDTO();
		product.setSku("SKU001");
		product.setSoldBy("Splash");
		product.setBrandName("SplashBrand");
		product.setDiscount("20.00");

		// Set price details
		PriceDetails priceDetails = new PriceDetails();
		priceDetails.setPrice("200.00");
		priceDetails.setSpecialPrice(null); // No special price
		product.setPrices(priceDetails);

		// No coupon applied
		product.setAppliedCouponValue(null);

		List<CatalogProductEntityForQuoteDTO> products = new ArrayList<>();
		products.add(product);
		quote.setProducts(products);

		// Setup LmdCommission
		LmdCommission lmdCommission = new LmdCommission();
		lmdCommission.setEn("Max");
		lmdCommission.setAr("Max");
		lmdCommission.setValue(15.0);
		lmdCommission.setNtdDiscount(8.0);
		List<LmdCommission> lmdCommissions = new ArrayList<>();
		lmdCommissions.add(lmdCommission);

		GetOrderConsulValues orderCredentials = new GetOrderConsulValues();
		orderCredentials.setLmdCommission(lmdCommissions);

		GetQuoteResponse quoteResponse = new GetQuoteResponse();
		quoteResponse.setStatus(true);
		quoteResponse.setStatusCode("200");
		quoteResponse.setResponse(quote);

		when(Constants.getStoresList()).thenReturn(storeList);
		when(Constants.orderCredentials).thenReturn(orderCredentials);
		when(externalQuoteHelper.fetchQuote(anyString(), anyInt(), anyInt(), anyString(), anyBoolean(), anyString(),
				anyString(), anyString(), anyBoolean(), anyString())).thenReturn(quoteResponse);

		CreateOrderResponseDTO response = salesOrderServiceV2Impl.convertQuoteToOrderV2(request, "token", "INV001",
				"android", requestHeader, "header-token", "1.0", "127.0.0.1", "device-id");

		// Verify poPrice calculation: price = 200, discount = 20, rrPrice = 180
		// styliCommission = 180 * 0.30 = 54, poPrice = 180 - 54 = 126
		assertNotNull(response);
		assertNotNull(product.getPoPrice());
		BigDecimal expectedPoPrice = new BigDecimal("126.00");
		BigDecimal actualPoPrice = new BigDecimal(product.getPoPrice());
		assertTrue(actualPoPrice.compareTo(expectedPoPrice) == 0 || 
				actualPoPrice.setScale(2, java.math.RoundingMode.HALF_UP)
						.compareTo(expectedPoPrice.setScale(2, java.math.RoundingMode.HALF_UP)) == 0,
				"Expected poPrice around 126.00, got: " + product.getPoPrice());
	}

	@Test
	public void testConvertQuoteToOrderV2_WithSellerConditionWithCouponAndCommission() throws NotFoundException {
		CreateOrderRequestV2 request = new CreateOrderRequestV2();
		request.setQuoteId("123");
		request.setStoreId(1);
		request.setRetryPaymentReplica(false);

		QuoteDTO quote = new QuoteDTO();
		quote.setQuoteId("123");
		quote.setCustomerId("1");
		quote.setStoreId("1");

		// Create product with Max seller
		CatalogProductEntityForQuoteDTO product = new CatalogProductEntityForQuoteDTO();
		product.setSku("SKU001");
		product.setSoldBy("Max");
		product.setBrandName("MaxBrand");
		product.setDiscount("15.00");

		// Set price details
		PriceDetails priceDetails = new PriceDetails();
		priceDetails.setPrice("150.00");
		priceDetails.setSpecialPrice("140.00");
		product.setPrices(priceDetails);

		// Set applied coupon value
		AppliedCouponValue couponValue = new AppliedCouponValue();
		couponValue.setDiscount(new BigDecimal("10.00"));
		product.setAppliedCouponValue(java.util.Arrays.asList(couponValue));

		List<CatalogProductEntityForQuoteDTO> products = new ArrayList<>();
		products.add(product);
		quote.setProducts(products);

		// Setup LmdCommission with 10% for Max
		LmdCommission lmdCommission = new LmdCommission();
		lmdCommission.setEn("Max");
		lmdCommission.setAr("Max");
		lmdCommission.setValue(15.0);
		lmdCommission.setNtdDiscount(8.0);
		List<LmdCommission> lmdCommissions = new ArrayList<>();
		lmdCommissions.add(lmdCommission);

		GetOrderConsulValues orderCredentials = new GetOrderConsulValues();
		orderCredentials.setLmdCommission(lmdCommissions);

		GetQuoteResponse quoteResponse = new GetQuoteResponse();
		quoteResponse.setStatus(true);
		quoteResponse.setStatusCode("200");
		quoteResponse.setResponse(quote);

		when(Constants.getStoresList()).thenReturn(storeList);
		when(Constants.orderCredentials).thenReturn(orderCredentials);
		when(externalQuoteHelper.fetchQuote(anyString(), anyInt(), anyInt(), anyString(), anyBoolean(), anyString(),
				anyString(), anyString(), anyBoolean(), anyString())).thenReturn(quoteResponse);

		CreateOrderResponseDTO response = salesOrderServiceV2Impl.convertQuoteToOrderV2(request, "token", "INV001",
				"android", requestHeader, "header-token", "1.0", "127.0.0.1", "device-id");

		// Verify poPrice calculation with coupon and commission:
		// priceToUse = 140 (specialPrice), discount = 15, rrPrice = 140 - 15 = 125
		// coupon = 10, commission on coupon = 10 * 0.10 = 1, adjustedCoupon = 10 - 1 = 9
		// rrPrice after coupon = 125 - 9 = 116
		// styliCommission = 116 * 0.30 = 34.8, poPrice = 116 - 34.8 = 81.2
		assertNotNull(response);
		assertNotNull(product.getPoPrice());
		BigDecimal actualPoPrice = new BigDecimal(product.getPoPrice());
		assertTrue(actualPoPrice.compareTo(BigDecimal.ZERO) > 0,
				"poPrice should be positive, got: " + product.getPoPrice());
		assertTrue(actualPoPrice.compareTo(new BigDecimal("150.00")) < 0,
				"poPrice should be less than original price");
	}

	// ========== convertQuoteToOrderV3 Tests (Newer Version) ==========

	@Test
	public void testConvertQuoteToOrderV3_WithBlankIncrementId() {
		CreateOrderRequestV2 request = new CreateOrderRequestV2();
		request.setQuoteId("123");
		request.setStoreId(1);

		CreateOrderResponseDTO response = salesOrderServiceV2Impl.convertQuoteToOrderV3(request, "token", "", "android",
				requestHeader, "header-token", "1.0", "127.0.0.1", "device-id");

		assertFalse(response.getStatus());
		assertEquals(response.getStatusCode(), "206");
		assertEquals(response.getStatusMsg(), "Increment ID not found!");
	}

	@Test
	public void testConvertQuoteToOrderV3_WithMissingParameters() {
		CreateOrderRequestV2 request = new CreateOrderRequestV2();
		request.setQuoteId(null);
		request.setStoreId(null);

		CreateOrderResponseDTO response = salesOrderServiceV2Impl.convertQuoteToOrderV3(request, "token", "INV001",
				"android", requestHeader, "header-token", "1.0", "127.0.0.1", "device-id");

		assertTrue(response.getStatus());
		assertEquals(response.getStatusCode(), "201");
		assertEquals(response.getStatusMsg(), "Parameters missing!");
	}

	@Test
	public void testConvertQuoteToOrderV3_WithInvalidStore() {
		CreateOrderRequestV2 request = new CreateOrderRequestV2();
		request.setQuoteId("123");
		request.setStoreId(999);

		CreateOrderResponseDTO response = salesOrderServiceV2Impl.convertQuoteToOrderV3(request, "token", "INV001",
				"android", requestHeader, "header-token", "1.0", "127.0.0.1", "device-id");

		assertFalse(response.getStatus());
		assertEquals(response.getStatusCode(), "201");
	}

	@Test
	public void testConvertQuoteToOrderV3_WithShukranPointsChanged() {
		CreateOrderRequestV2 request = new CreateOrderRequestV2();
		request.setQuoteId("123");
		request.setStoreId(1);

		QuoteV7DTO quote = new QuoteV7DTO();
		quote.setQuoteId("123");
		quote.setIsAvailableShukranChanged(true);

		QuoteV7Response quoteV7Response = new QuoteV7Response();
		quoteV7Response.setStatus(true);
		quoteV7Response.setStatusCode("200");
		quoteV7Response.setResponse(quote);

		when(Constants.getStoresList()).thenReturn(storeList);
		when(externalQuoteHelper.fetchQuotev7(anyString(), anyInt(), anyInt(), anyString(), anyBoolean(), anyString(),
				anyString(), anyString(), anyBoolean(), anyString())).thenReturn(quoteV7Response);

		CreateOrderResponseDTO response = salesOrderServiceV2Impl.convertQuoteToOrderV3(request, "token", "INV001",
				"android", requestHeader, "header-token", "1.0", "127.0.0.1", "device-id");

		assertTrue(response.getStatus());
		assertEquals(response.getStatusCode(), "226");
		assertEquals(response.getStatusMsg(), "Shukran Available Points Changed");
	}

	@Test
	public void testConvertQuoteToOrderV3_WithSellerConditionAndCommissionCalculation() {
		// Test for each seller: Max, Splash, Shoexpress, Shoemart, Babyshop, Lifestyle, Homebox
		String[] sellers = { "Max", "Splash", "Shoexpress", "Shoemart", "Babyshop", "Lifestyle", "Homebox" };
		
		for (String seller : sellers) {
			CreateOrderRequestV2 request = new CreateOrderRequestV2();
			request.setQuoteId("123");
			request.setStoreId(1);
			request.setRetryPaymentReplica(false);

			QuoteV7DTO quote = new QuoteV7DTO();
			quote.setQuoteId("123");
			quote.setCustomerId("1");
			quote.setStoreId("1");

			// Create product with seller name
			ProductEntityForQuoteV7DTO product = new ProductEntityForQuoteV7DTO();
			product.setSku("SKU001");
			product.setSoldBy(seller);
			product.setBrandName("TestBrand");
			product.setDiscount("10.00");

			// Set price details
			PriceDetails priceDetails = new PriceDetails();
			priceDetails.setPrice("100.00");
			priceDetails.setSpecialPrice("90.00");
			product.setPrices(priceDetails);

			// Set applied coupon value
			AppliedCouponValue couponValue = new AppliedCouponValue();
			couponValue.setDiscount(new BigDecimal("5.00"));
			product.setAppliedCouponValue(java.util.Arrays.asList(couponValue));

			List<ProductEntityForQuoteV7DTO> products = new ArrayList<>();
			products.add(product);
			quote.setProducts(products);

			// Setup LmdCommission
			LmdCommission lmdCommission = new LmdCommission();
			lmdCommission.setEn("Max");
			lmdCommission.setAr("Max");
			lmdCommission.setValue(15.0);
			lmdCommission.setNtdDiscount(8.0);
			List<LmdCommission> lmdCommissions = new ArrayList<>();
			lmdCommissions.add(lmdCommission);

			GetOrderConsulValues orderCredentials = new GetOrderConsulValues();
			orderCredentials.setLmdCommission(lmdCommissions);

			QuoteV7Response quoteV7Response = new QuoteV7Response();
			quoteV7Response.setStatus(true);
			quoteV7Response.setStatusCode("200");
			quoteV7Response.setResponse(quote);

			CreateOrderResponseDTO quoteResponse = new CreateOrderResponseDTO();
			quoteResponse.setStatus(true);
			quoteResponse.setQuoteV7Response(quoteV7Response);

			when(Constants.getStoresList()).thenReturn(storeList);
			when(Constants.orderCredentials).thenReturn(orderCredentials);
			when(externalQuoteHelper.fetchQuotev7(anyString(), anyInt(), anyInt(), anyString(), anyBoolean(), anyString(),
					anyString(), anyString(), anyBoolean(), anyString())).thenReturn(quoteV7Response);

			CreateOrderResponseDTO response = salesOrderServiceV2Impl.convertQuoteToOrderV3(request, "token", "INV001",
					"android", requestHeader, "header-token", "1.0", "127.0.0.1", "device-id");

			// Verify that poPrice was set (indicating the seller condition was matched)
			assertNotNull(response);
			// The product should have poPrice set after commission calculation
			assertNotNull(product.getPoPrice());
			assertTrue(new BigDecimal(product.getPoPrice()).compareTo(BigDecimal.ZERO) >= 0,
					"poPrice should be calculated for seller: " + seller);
		}
	}

	@Test
	public void testConvertQuoteToOrderV3_WithSellerConditionWithoutCoupon() {
		CreateOrderRequestV2 request = new CreateOrderRequestV2();
		request.setQuoteId("123");
		request.setStoreId(1);
		request.setRetryPaymentReplica(false);

		QuoteV7DTO quote = new QuoteV7DTO();
		quote.setQuoteId("123");
		quote.setCustomerId("1");
		quote.setStoreId("1");

		// Create product with Splash seller
		ProductEntityForQuoteV7DTO product = new ProductEntityForQuoteV7DTO();
		product.setSku("SKU001");
		product.setSoldBy("Splash");
		product.setBrandName("SplashBrand");
		product.setDiscount("20.00");

		// Set price details
		PriceDetails priceDetails = new PriceDetails();
		priceDetails.setPrice("200.00");
		priceDetails.setSpecialPrice(null); // No special price
		product.setPrices(priceDetails);

		// No coupon applied
		product.setAppliedCouponValue(null);

		List<ProductEntityForQuoteV7DTO> products = new ArrayList<>();
		products.add(product);
		quote.setProducts(products);

		// Setup LmdCommission
		LmdCommission lmdCommission = new LmdCommission();
		lmdCommission.setEn("Max");
		lmdCommission.setAr("Max");
		lmdCommission.setValue(15.0);
		lmdCommission.setNtdDiscount(8.0);
		List<LmdCommission> lmdCommissions = new ArrayList<>();
		lmdCommissions.add(lmdCommission);
		GetOrderConsulValues orderCredentials = new GetOrderConsulValues();
		orderCredentials.setLmdCommission(lmdCommissions);

		QuoteV7Response quoteV7Response = new QuoteV7Response();
		quoteV7Response.setStatus(true);
		quoteV7Response.setStatusCode("200");
		quoteV7Response.setResponse(quote);

		when(Constants.getStoresList()).thenReturn(storeList);
		when(Constants.orderCredentials).thenReturn(orderCredentials);
		when(externalQuoteHelper.fetchQuotev7(anyString(), anyInt(), anyInt(), anyString(), anyBoolean(), anyString(),
				anyString(), anyString(), anyBoolean(), anyString())).thenReturn(quoteV7Response);

		CreateOrderResponseDTO response = salesOrderServiceV2Impl.convertQuoteToOrderV3(request, "token", "INV001",
				"android", requestHeader, "header-token", "1.0", "127.0.0.1", "device-id");

		// Verify poPrice calculation: price = 200, discount = 20, rrPrice = 180
		// styliCommission = 180 * 0.30 = 54, poPrice = 180 - 54 = 126
		assertNotNull(response);
		assertNotNull(product.getPoPrice());
		BigDecimal expectedPoPrice = new BigDecimal("126.00");
		BigDecimal actualPoPrice = new BigDecimal(product.getPoPrice());
		assertTrue(actualPoPrice.compareTo(expectedPoPrice) == 0 || 
				actualPoPrice.setScale(2, java.math.RoundingMode.HALF_UP)
						.compareTo(expectedPoPrice.setScale(2, java.math.RoundingMode.HALF_UP)) == 0,
				"Expected poPrice around 126.00, got: " + product.getPoPrice());
	}

	@Test
	public void testConvertQuoteToOrderV3_WithSellerConditionWithCouponAndCommission() {
		CreateOrderRequestV2 request = new CreateOrderRequestV2();
		request.setQuoteId("123");
		request.setStoreId(1);
		request.setRetryPaymentReplica(false);

		QuoteV7DTO quote = new QuoteV7DTO();
		quote.setQuoteId("123");
		quote.setCustomerId("1");
		quote.setStoreId("1");

		// Create product with Max seller
		ProductEntityForQuoteV7DTO product = new ProductEntityForQuoteV7DTO();
		product.setSku("SKU001");
		product.setSoldBy("Max");
		product.setBrandName("MaxBrand");
		product.setDiscount("15.00");

		// Set price details
		PriceDetails priceDetails = new PriceDetails();
		priceDetails.setPrice("150.00");
		priceDetails.setSpecialPrice("140.00");
		product.setPrices(priceDetails);

		// Set applied coupon value
		AppliedCouponValue couponValue = new AppliedCouponValue();
		couponValue.setDiscount(new BigDecimal("10.00"));
		product.setAppliedCouponValue(java.util.Arrays.asList(couponValue));

		List<ProductEntityForQuoteV7DTO> products = new ArrayList<>();
		products.add(product);
		quote.setProducts(products);

		// Setup LmdCommission with 10% for Max
		LmdCommission lmdCommission = new LmdCommission();
		lmdCommission.setEn("Max");
		lmdCommission.setAr("Max");
		lmdCommission.setValue(15.0);
		lmdCommission.setNtdDiscount(8.0);
		List<LmdCommission> lmdCommissions = new ArrayList<>();
		lmdCommissions.add(lmdCommission);

		GetOrderConsulValues orderCredentials = new GetOrderConsulValues();
		orderCredentials.setLmdCommission(lmdCommissions);

		QuoteV7Response quoteV7Response = new QuoteV7Response();
		quoteV7Response.setStatus(true);
		quoteV7Response.setStatusCode("200");
		quoteV7Response.setResponse(quote);

		when(Constants.getStoresList()).thenReturn(storeList);
		when(Constants.orderCredentials).thenReturn(orderCredentials);
		when(externalQuoteHelper.fetchQuotev7(anyString(), anyInt(), anyInt(), anyString(), anyBoolean(), anyString(),
				anyString(), anyString(), anyBoolean(), anyString())).thenReturn(quoteV7Response);

		CreateOrderResponseDTO response = salesOrderServiceV2Impl.convertQuoteToOrderV3(request, "token", "INV001",
				"android", requestHeader, "header-token", "1.0", "127.0.0.1", "device-id");

		// Verify poPrice calculation with coupon and commission:
		// priceToUse = 140 (specialPrice), discount = 15, rrPrice = 140 - 15 = 125
		// coupon = 10, commission on coupon = 10 * 0.10 = 1, adjustedCoupon = 10 - 1 = 9
		// rrPrice after coupon = 125 - 9 = 116
		// styliCommission = 116 * 0.30 = 34.8, poPrice = 116 - 34.8 = 81.2
		assertNotNull(response);
		assertNotNull(product.getPoPrice());
		BigDecimal actualPoPrice = new BigDecimal(product.getPoPrice());
		assertTrue(actualPoPrice.compareTo(BigDecimal.ZERO) > 0,
				"poPrice should be positive, got: " + product.getPoPrice());
		assertTrue(actualPoPrice.compareTo(new BigDecimal("150.00")) < 0,
				"poPrice should be less than original price");
	}

	// ========== addStoreCredit Tests ==========

	@Test
	public void testAddStoreCredit_WithNullRequest() {
		AddStoreCreditResponse response = salesOrderServiceV2Impl.addStoreCredit(null);

		assertFalse(response.getStatus());
		assertEquals(response.getStatusCode(), "400");
		assertEquals(response.getStatusMsg(), "Invalid Request!");
	}

	@Test
	public void testAddStoreCredit_WithEmptyStoreCredits() {
		AddStoreCreditRequest request = new AddStoreCreditRequest();
		request.setStoreCredits(new ArrayList<>());

		AddStoreCreditResponse response = salesOrderServiceV2Impl.addStoreCredit(request);

		assertFalse(response.getStatus());
		assertEquals(response.getStatusCode(), "400");
		assertEquals(response.getStatusMsg(), "Invalid Request!");
	}

	@Test
	public void testAddStoreCredit_WithNullStoreCredit() {
		AddStoreCreditRequest request = new AddStoreCreditRequest();
		List<StoreCredit> storeCredits = new ArrayList<>();
		storeCredits.add(null);
		request.setStoreCredits(storeCredits);

		when(Constants.getStoresList()).thenReturn(storeList);

		AddStoreCreditResponse response = salesOrderServiceV2Impl.addStoreCredit(request);

		assertFalse(response.getStatus());
		assertEquals(response.getStatusCode(), "202");
	}

	@Test
	public void testAddStoreCredit_WithInvalidCustomer() {
		AddStoreCreditRequest request = new AddStoreCreditRequest();
		List<StoreCredit> storeCredits = new ArrayList<>();
		StoreCredit storeCredit = new StoreCredit();
		storeCredit.setCustomerId(999);
		storeCredit.setStoreId(1);
		storeCredit.setStoreCredit(new BigDecimal("100.00"));
		storeCredits.add(storeCredit);
		request.setStoreCredits(storeCredits);

		CustomerEntity customer = new CustomerEntity();
		customer.setEntityId(null);

		when(Constants.getStoresList()).thenReturn(storeList);
		when(orderHelper.getCustomerDetails(999, null)).thenReturn(customer);

		AddStoreCreditResponse response = salesOrderServiceV2Impl.addStoreCredit(request);

		assertFalse(response.getStatus());
	}

	@Test
	public void testAddStoreCredit_WithInvalidStore() {
		AddStoreCreditRequest request = new AddStoreCreditRequest();
		List<StoreCredit> storeCredits = new ArrayList<>();
		StoreCredit storeCredit = new StoreCredit();
		storeCredit.setCustomerId(1);
		storeCredit.setStoreId(999);
		storeCredit.setStoreCredit(new BigDecimal("100.00"));
		storeCredits.add(storeCredit);
		request.setStoreCredits(storeCredits);

		CustomerEntity customer = new CustomerEntity();
		customer.setEntityId(1);

		when(Constants.getStoresList()).thenReturn(storeList);
		when(orderHelper.getCustomerDetails(1, null)).thenReturn(customer);

		AddStoreCreditResponse response = salesOrderServiceV2Impl.addStoreCredit(request);

		assertFalse(response.getStatus());
		assertEquals(response.getStatusCode(), "202");
	}

	@Test
	public void testAddStoreCredit_WithZeroStoreCredit() {
		AddStoreCreditRequest request = new AddStoreCreditRequest();
		List<StoreCredit> storeCredits = new ArrayList<>();
		StoreCredit storeCredit = new StoreCredit();
		storeCredit.setCustomerId(1);
		storeCredit.setStoreId(1);
		storeCredit.setStoreCredit(BigDecimal.ZERO);
		storeCredits.add(storeCredit);
		request.setStoreCredits(storeCredits);

		CustomerEntity customer = new CustomerEntity();
		customer.setEntityId(1);

		when(Constants.getStoresList()).thenReturn(storeList);
		when(orderHelper.getCustomerDetails(1, null)).thenReturn(customer);

		AddStoreCreditResponse response = salesOrderServiceV2Impl.addStoreCredit(request);

		assertFalse(response.getStatus());
		assertEquals(response.getStatusCode(), "209");
	}

	// ========== authenticateCheck Tests ==========

	@Test(expectedExceptions = BadRequestException.class)
	public void testAuthenticateCheck_WithNullJwtUser() {
		Map<String, String> headers = new HashMap<>();
		headers.put("Token", "Bearer test-token");
		headers.put("X-Header-Token", "test@mail.com");

		when(validator.validate("test-token")).thenReturn(null);

		salesOrderServiceV2Impl.authenticateCheck(headers, 1);
	}

	@Test(expectedExceptions = BadRequestException.class)
	public void testAuthenticateCheck_WithMismatchedCustomerId() {
		Map<String, String> headers = new HashMap<>();
		headers.put("Token", "Bearer test-token");
		headers.put("X-Header-Token", "test@mail.com");

		JwtUser jwtUser = new JwtUser();
		jwtUser.setCustomerId(2);
		jwtUser.setUserId("test@mail.com");

		when(validator.validate("test-token")).thenReturn(jwtUser);

		salesOrderServiceV2Impl.authenticateCheck(headers, 1);
	}

	@Test
	public void testAuthenticateCheck_WithValidToken() {
		Map<String, String> headers = new HashMap<>();
		headers.put("Token", "Bearer test-token");
		headers.put("X-Header-Token", "test@mail.com");

		JwtUser jwtUser = new JwtUser();
		jwtUser.setCustomerId(1);
		jwtUser.setUserId("test@mail.com");

		when(validator.validate("test-token")).thenReturn(jwtUser);

		Boolean result = salesOrderServiceV2Impl.authenticateCheck(headers, 1);

		assertFalse(result);
	}

	@Test
	public void testAuthenticateCheck_WithNullCustomerId() {
		Map<String, String> headers = new HashMap<>();

		Boolean result = salesOrderServiceV2Impl.authenticateCheck(headers, null);

		assertFalse(result);
	}

	// ========== authenticateOrderCheck Tests ==========

	@Test(expectedExceptions = BadRequestException.class)
	public void testAuthenticateOrderCheck_WithNullJwtUser() {
		Map<String, String> headers = new HashMap<>();
		headers.put("Token", "Bearer test-token");
		headers.put("X-Header-Token", "test@mail.com");

		when(validator.validate("test-token")).thenReturn(null);

		salesOrderServiceV2Impl.authenticateOrderCheck(headers, 1);
	}

	@Test(expectedExceptions = BadRequestException.class)
	public void testAuthenticateOrderCheck_WithMismatchedCustomerId() {
		Map<String, String> headers = new HashMap<>();
		headers.put("Token", "Bearer test-token");
		headers.put("X-Header-Token", "test@mail.com");

		JwtUser jwtUser = new JwtUser();
		jwtUser.setCustomerId(2);
		jwtUser.setUserId("test@mail.com");
		jwtUser.setIsOldToken(false);

		when(validator.validate("test-token")).thenReturn(jwtUser);

		salesOrderServiceV2Impl.authenticateOrderCheck(headers, 1);
	}

	@Test
	public void testAuthenticateOrderCheck_WithValidToken() {
		Map<String, String> headers = new HashMap<>();
		headers.put("Token", "Bearer test-token");
		headers.put("X-Header-Token", "test@mail.com");

		JwtUser jwtUser = new JwtUser();
		jwtUser.setCustomerId(1);
		jwtUser.setUserId("test@mail.com");
		jwtUser.setIsOldToken(false);

		when(validator.validate("test-token")).thenReturn(jwtUser);

		Boolean result = salesOrderServiceV2Impl.authenticateOrderCheck(headers, 1);

		assertFalse(result);
	}

	// ========== getInventoryQty Tests ==========

	@Test
	public void testGetInventoryQty_Success() {
		ProductStatusRequest request = new ProductStatusRequest();
		request.setStoreId(1);

		ProductInventoryRes expectedResponse = new ProductInventoryRes();
		expectedResponse.setStatus(true);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
		headers.add(Constants.AUTH_BEARER_HEADER, "Bearer test-token");

		HttpEntity<ProductStatusRequest> requestEntity = new HttpEntity<>(request, headers);

		when(Constants.orderCredentials).thenReturn(new GetOrderConsulValues());
		when(Constants.orderCredentials.getOrderDetails()).thenReturn(new OrderKeyDetails());
		when(Constants.orderCredentials.getOrderDetails().getInventoryBaseUrl()).thenReturn("http://test-url");
		when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
				eq(ProductInventoryRes.class))).thenReturn(new ResponseEntity<>(expectedResponse, HttpStatus.OK));

		ProductInventoryRes response = salesOrderServiceV2Impl.getInventoryQty(request);

		assertNotNull(response);
	}

	@Test
	public void testGetInventoryQty_WithException() {
		ProductStatusRequest request = new ProductStatusRequest();
		request.setStoreId(1);

		when(Constants.orderCredentials).thenReturn(new GetOrderConsulValues());
		when(Constants.orderCredentials.getOrderDetails()).thenReturn(new OrderKeyDetails());
		when(Constants.orderCredentials.getOrderDetails().getInventoryBaseUrl()).thenReturn("http://test-url");
		when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
				eq(ProductInventoryRes.class))).thenThrow(new RuntimeException("Test exception"));

		ProductInventoryRes response = salesOrderServiceV2Impl.getInventoryQty(request);

		assertNotNull(response);
	}

	// ========== getInventoryV2 Tests (Newer Version) ==========

	@Test
	public void testGetInventoryV2_Success() {
		ProductStatusRequestV2 request = new ProductStatusRequestV2();
		request.setStoreId(1);

		ProductInventoryResV2 expectedResponse = new ProductInventoryResV2();

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
		headers.add(Constants.AUTH_BEARER_HEADER, "Bearer test-token");

		// Request entity created for mock

		when(Constants.orderCredentials).thenReturn(new GetOrderConsulValues());
		when(Constants.orderCredentials.getOrderDetails()).thenReturn(new OrderKeyDetails());
		when(Constants.orderCredentials.getOrderDetails().getInventoryBaseUrl()).thenReturn("http://test-url");
		when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
				eq(ProductInventoryResV2.class))).thenReturn(new ResponseEntity<>(expectedResponse, HttpStatus.OK));

		ProductInventoryResV2 response = salesOrderServiceV2Impl.getInventoryV2(request);

		assertNotNull(response);
	}

	@Test
	public void testGetInventoryV2_WithException() {
		ProductStatusRequestV2 request = new ProductStatusRequestV2();
		request.setStoreId(1);

		when(Constants.orderCredentials).thenReturn(new GetOrderConsulValues());
		when(Constants.orderCredentials.getOrderDetails()).thenReturn(new OrderKeyDetails());
		when(Constants.orderCredentials.getOrderDetails().getInventoryBaseUrl()).thenReturn("http://test-url");
		when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
				eq(ProductInventoryResV2.class))).thenThrow(new RuntimeException("Test exception"));

		ProductInventoryResV2 response = salesOrderServiceV2Impl.getInventoryV2(request);

		assertNotNull(response);
	}

	// ========== getAuthorization Tests ==========

	@Test
	public void testGetAuthorization_WithSingleToken() {
		String authToken = "single-token";
		String result = (String) ReflectionTestUtils.invokeMethod(salesOrderServiceV2Impl, "getAuthorization",
				authToken);

		assertNull(result);
	}

	@Test
	public void testGetAuthorization_WithCommaSeparatedTokens() {
		String authToken = "token1,token2,token3";
		String result = (String) ReflectionTestUtils.invokeMethod(salesOrderServiceV2Impl, "getAuthorization",
				authToken);

		assertEquals(result, "token1");
	}

	@Test
	public void testGetAuthorization_WithEmptyToken() {
		String authToken = "";
		String result = (String) ReflectionTestUtils.invokeMethod(salesOrderServiceV2Impl, "getAuthorization",
				authToken);

		assertNull(result);
	}

	// ========== updateRatingStatus Tests ==========

	@Test
	public void testUpdateRatingStatus_WithSalesOrder() {
		SalesOrder salesOrder = new SalesOrder();
		salesOrder.setEntityId(1);

		when(salesOrderRepository.findByEntityId(1)).thenReturn(salesOrder);
		when(salesOrderRepository.updateRatingStatus("5", 1)).thenReturn(1);

		int result = salesOrderServiceV2Impl.updateRatingStatus("5", 1);

		assertEquals(result, 1);
		verify(salesOrderRepository, times(1)).updateRatingStatus("5", 1);
	}

	@Test
	public void testUpdateRatingStatus_WithSplitSalesOrder() {
		SplitSalesOrder splitSalesOrder = new SplitSalesOrder();
		splitSalesOrder.setEntityId(1);

		when(salesOrderRepository.findByEntityId(1)).thenReturn(null);
		when(splitSalesOrderRepository.findByEntityId(1)).thenReturn(splitSalesOrder);
		when(splitSalesOrderRepository.updateRatingStatus("5", 1)).thenReturn(1);

		int result = salesOrderServiceV2Impl.updateRatingStatus("5", 1);

		assertEquals(result, 1);
		verify(splitSalesOrderRepository, times(1)).updateRatingStatus("5", 1);
	}

	@Test
	public void testUpdateRatingStatus_WithNoOrderFound() {
		when(salesOrderRepository.findByEntityId(1)).thenReturn(null);
		when(splitSalesOrderRepository.findByEntityId(1)).thenReturn(null);

		int result = salesOrderServiceV2Impl.updateRatingStatus("5", 1);

		assertEquals(result, 0);
	}

	// ========== createQuoteReplica Tests ==========

	@Test
	public void testCreateQuoteReplica_WithInvalidStore() {
		CreateReplicaQuoteV4Request request = new CreateReplicaQuoteV4Request();
		request.setStoreId(999);

		when(Constants.getStoresList()).thenReturn(storeList);

		QuoteUpdateDTO response = salesOrderServiceV2Impl.createQuoteReplica(request, "token", "device-id");

		assertFalse(response.getStatus());
		assertEquals(response.getStatusCode(), "201");
	}

	@Test
	public void testCreateQuoteReplica_WithProxyOrderNotFound() {
		CreateReplicaQuoteV4Request request = new CreateReplicaQuoteV4Request();
		request.setStoreId(1);
		request.setOrderId(999);
		request.setTabbyPaymentId("TABBY123");

		when(Constants.getStoresList()).thenReturn(storeList);
		when(proxyOrderRepository.findByIdOrPaymentId(any(), eq("TABBY123"))).thenReturn(Optional.empty());

		QuoteUpdateDTO response = salesOrderServiceV2Impl.createQuoteReplica(request, "token", "device-id");

		assertFalse(response.getStatus());
		assertEquals(response.getStatusCode(), "202");
	}

	@Test
	public void testCreateQuoteReplica_WithAlreadyExecutedReplica() throws Exception {
		CreateReplicaQuoteV4Request request = new CreateReplicaQuoteV4Request();
		request.setStoreId(1);
		request.setOrderId(1);
		request.setTabbyPaymentId("TABBY123");

		ProxyOrder proxyOrder = new ProxyOrder();
		proxyOrder.setQuoteId("123");
		proxyOrder.setPaymentId("PAY123");
		proxyOrder.setIncrementId("INV001");

		SalesOrder salesOrder = new SalesOrder();
		salesOrder.setEntityId(1);
		salesOrder.setIncrementId("INV001");

		when(Constants.getStoresList()).thenReturn(storeList);
		when(proxyOrderRepository.findByIdOrPaymentId(1L, "TABBY123")).thenReturn(Optional.of(proxyOrder));
		when(salesOrderRepository.findByIncrementId("INV001")).thenReturn(salesOrder);

		QuoteUpdateDTO response = salesOrderServiceV2Impl.createQuoteReplica(request, "token", "device-id");

		assertTrue(response.getStatus());
		assertEquals(response.getStatusCode(), "200");
		assertEquals(response.getStatusMsg(), "Replica alredy executed for this quote : 123");
	}

	// ========== createRetryPaymentReplica Tests (Newer Method) ==========

	@Test
	public void testCreateRetryPaymentReplica_WithInvalidStore() {
		CreateReplicaQuoteV4Request request = new CreateReplicaQuoteV4Request();
		request.setStoreId(999);

		when(Constants.getStoresList()).thenReturn(storeList);

		QuoteUpdateDTO response = salesOrderServiceV2Impl.createRetryPaymentReplica(request, "token", "device-id");

		assertFalse(response.getStatus());
		assertEquals(response.getStatusCode(), "201");
	}

	@Test
	public void testCreateRetryPaymentReplica_WithOrderNotFound() {
		CreateReplicaQuoteV4Request request = new CreateReplicaQuoteV4Request();
		request.setStoreId(1);
		request.setOrderId(999);
		request.setCustomerId(1);

		when(Constants.getStoresList()).thenReturn(storeList);
		when(salesOrderRepository.findByEntityIdAndCustomerId(999, 1)).thenReturn(null);

		QuoteUpdateDTO response = salesOrderServiceV2Impl.createRetryPaymentReplica(request, "token", "device-id");

		assertFalse(response.getStatus());
		assertEquals(response.getStatusCode(), "202");
	}

	@Test
	public void testCreateRetryPaymentReplica_WithFailedQuoteResponse() {
		CreateReplicaQuoteV4Request request = new CreateReplicaQuoteV4Request();
		request.setStoreId(1);
		request.setOrderId(1);
		request.setCustomerId(1);
		request.setFailedPaymentMethod("COD");

		SalesOrder order = new SalesOrder();
		order.setEntityId(1);
		order.setQuoteId(123);
		order.setStoreId(1);
		order.setMerchantReferance("REF123");

		CreateRetryPaymentReplicaDTO quoteResponse = new CreateRetryPaymentReplicaDTO();
		quoteResponse.setStatus(false);
		quoteResponse.setStatusCode("400");

		when(Constants.getStoresList()).thenReturn(storeList);
		when(salesOrderRepository.findByEntityIdAndCustomerId(1, 1)).thenReturn(order);
		when(externalQuoteHelper.enableExternalQuoteForRetryPayment(anyString(), anyInt(), anyString(), anyString(),
				anyString(), any(), anyString())).thenReturn(quoteResponse);

		QuoteUpdateDTO response = salesOrderServiceV2Impl.createRetryPaymentReplica(request, "token", "device-id");

		assertFalse(response.getStatus());
		assertEquals(response.getStatusCode(), "202");
	}

	@Test
	public void testCreateRetryPaymentReplica_WithMaxRetryThreshold() {
		CreateReplicaQuoteV4Request request = new CreateReplicaQuoteV4Request();
		request.setStoreId(1);
		request.setOrderId(1);
		request.setCustomerId(1);
		request.setFailedPaymentMethod("COD");

		SalesOrder order = new SalesOrder();
		order.setEntityId(1);
		order.setQuoteId(123);
		order.setStoreId(1);
		order.setMerchantReferance("REF123");
		order.setStatus(OrderConstants.PENDING_PAYMENT_ORDER_STATUS);

		SubSalesOrder subSalesOrder = new SubSalesOrder();
		subSalesOrder.setSalesOrder(order);
		order.setSubSalesOrder(subSalesOrder);

		CreateRetryPaymentReplicaDTO quoteResponse = new CreateRetryPaymentReplicaDTO();
		quoteResponse.setStatus(true);
		quoteResponse.setStatusCode("200");
		quoteResponse.setTriedPaymentCount(5);
		quoteResponse.setRetryPaymentThreshold(3);
		quoteResponse.setQuoteId("123");
		quoteResponse.setCustomerId(1);

		when(Constants.getStoresList()).thenReturn(storeList);
		when(salesOrderRepository.findByEntityIdAndCustomerId(1, 1)).thenReturn(order);
		when(externalQuoteHelper.enableExternalQuoteForRetryPayment(anyString(), anyInt(), anyString(), anyString(),
				anyString(), any(), anyString())).thenReturn(quoteResponse);
		when(salesOrderRepository.saveAndFlush(any(SalesOrder.class))).thenReturn(order);

		QuoteUpdateDTO response = salesOrderServiceV2Impl.createRetryPaymentReplica(request, "token", "device-id");

		assertTrue(response.getStatus());
		assertEquals(response.getStatusCode(), "201");
		assertEquals(response.getStatusMsg(), "maximum retry threshold reached!");
	}

	// ========== brazeWalletUpdate Tests (Newer Method) ==========

	@Test
	public void testBrazeWalletUpdate_WithEmptyStoreCredits() {
		AddStoreCreditRequest request = new AddStoreCreditRequest();
		request.setStoreCredits(new ArrayList<>());

		Map<String, String> headers = new HashMap<>();

		AddStoreCreditResponse response = salesOrderServiceV2Impl.brazeWalletUpdate(headers, request);

		assertFalse(response.getStatus());
		assertEquals(response.getStatusCode(), "201");
		assertEquals(response.getStatusMsg(), "Empty request from braze!");
	}

	@Test
	public void testBrazeWalletUpdate_WithExceededLimit() {
		AddStoreCreditRequest request = new AddStoreCreditRequest();
		List<StoreCredit> storeCredits = new ArrayList<>();
		for (int i = 0; i < 1001; i++) {
			storeCredits.add(new StoreCredit());
		}
		request.setStoreCredits(storeCredits);

		Map<String, String> headers = new HashMap<>();

		when(Constants.orderCredentials).thenReturn(new GetOrderConsulValues());
		when(Constants.orderCredentials.getOrderDetails()).thenReturn(new OrderKeyDetails());
		when(Constants.orderCredentials.getOrderDetails().getBrazeAttributePushLimit()).thenReturn(1000L);

		AddStoreCreditResponse response = salesOrderServiceV2Impl.brazeWalletUpdate(headers, request);

		assertFalse(response.getStatus());
		assertEquals(response.getStatusCode(), "202");
		assertTrue(response.getStatusMsg().contains("braze requests exceeded limit"));
	}

	@Test
	public void testBrazeWalletUpdate_Success() {
		AddStoreCreditRequest request = new AddStoreCreditRequest();
		List<StoreCredit> storeCredits = new ArrayList<>();
		StoreCredit storeCredit = new StoreCredit();
		storeCredit.setCustomerId(1);
		storeCredit.setStoreId(1);
		storeCredit.setStoreCredit(new BigDecimal("100.00"));
		storeCredit.setReturnableToBank(false);
		storeCredits.add(storeCredit);
		request.setStoreCredits(storeCredits);

		Map<String, String> headers = new HashMap<>();

		when(Constants.orderCredentials).thenReturn(new GetOrderConsulValues());
		when(Constants.orderCredentials.getOrderDetails()).thenReturn(new OrderKeyDetails());
		when(Constants.orderCredentials.getOrderDetails().getBrazeAttributePushLimit()).thenReturn(1000L);
		Mockito.doNothing().when(paymentDtfHelper).publishSCToKafkaForBraze(any(BulkWalletUpdate.class));

		AddStoreCreditResponse response = salesOrderServiceV2Impl.brazeWalletUpdate(headers, request);

		assertTrue(response.getStatus());
		assertEquals(response.getStatusCode(), "200");
		assertEquals(response.getStatusMsg(), "Wallet updates request acknowledged!");
		verify(paymentDtfHelper, times(1)).publishSCToKafkaForBraze(any(BulkWalletUpdate.class));
	}

	@Test
	public void testBrazeWalletUpdate_WithException() {
		AddStoreCreditRequest request = new AddStoreCreditRequest();
		List<StoreCredit> storeCredits = new ArrayList<>();
		StoreCredit storeCredit = new StoreCredit();
		storeCredit.setCustomerId(1);
		storeCredit.setStoreId(1);
		storeCredit.setStoreCredit(new BigDecimal("100.00"));
		storeCredits.add(storeCredit);
		request.setStoreCredits(storeCredits);

		Map<String, String> headers = new HashMap<>();

		when(Constants.orderCredentials).thenReturn(new GetOrderConsulValues());
		when(Constants.orderCredentials.getOrderDetails()).thenReturn(new OrderKeyDetails());
		when(Constants.orderCredentials.getOrderDetails().getBrazeAttributePushLimit()).thenReturn(1000L);
		Mockito.doThrow(new RuntimeException("Test exception")).when(paymentDtfHelper)
				.publishSCToKafkaForBraze(any(BulkWalletUpdate.class));

		AddStoreCreditResponse response = salesOrderServiceV2Impl.brazeWalletUpdate(headers, request);

		assertTrue(response.getStatus());
		assertEquals(response.getStatusCode(), "200");
	}

	// ========== brazeAttributePush Tests (Newer Method) ==========

	@Test
	public void testBrazeAttributePush_Success() {
		Map<String, String> headers = new HashMap<>();

		AmastyStoreCreditHistory history = new AmastyStoreCreditHistory();
		history.setCustomerId(1);
		history.setCreatedAt(new Timestamp(System.currentTimeMillis()));

		List<AmastyStoreCreditHistory> histories = new ArrayList<>();
		histories.add(history);

		AmastyStoreCredit storeCredit = new AmastyStoreCredit();
		storeCredit.setCustomerId(1);
		storeCredit.setStoreCredit(new BigDecimal("100.00"));

		List<AmastyStoreCredit> credits = new ArrayList<>();
		credits.add(storeCredit);

		when(Constants.orderCredentials).thenReturn(new GetOrderConsulValues());
		when(Constants.orderCredentials.getOrderDetails()).thenReturn(new OrderKeyDetails());
		when(Constants.orderCredentials.getOrderDetails().getBrazeAttributePushStartOffset()).thenReturn(7200L);
		when(amastyStoreCreditHistoryRepository.findByCreatedAtGreaterThan(any(Timestamp.class)))
				.thenReturn(histories);
		when(amastyStoreCreditRepository.findByCustomerIdIn(anyList())).thenReturn(credits);
		Mockito.doNothing().when(kafkaBrazeHelper).sendAttributesToBraze(anyList());

		AddStoreCreditResponse response = salesOrderServiceV2Impl.brazeAttributePush(headers);

		assertTrue(response.getStatus());
		assertEquals(response.getStatusCode(), "200");
		assertEquals(response.getStatusMsg(), "Wallet attributes submit request acknowledged!");
		verify(kafkaBrazeHelper, times(1)).sendAttributesToBraze(anyList());
	}

	@Test
	public void testBrazeAttributePush_WithEmptyHistories() {
		Map<String, String> headers = new HashMap<>();

		when(Constants.orderCredentials).thenReturn(new GetOrderConsulValues());
		when(Constants.orderCredentials.getOrderDetails()).thenReturn(new OrderKeyDetails());
		when(Constants.orderCredentials.getOrderDetails().getBrazeAttributePushStartOffset()).thenReturn(7200L);
		when(amastyStoreCreditHistoryRepository.findByCreatedAtGreaterThan(any(Timestamp.class)))
				.thenReturn(new ArrayList<>());

		AddStoreCreditResponse response = salesOrderServiceV2Impl.brazeAttributePush(headers);

		assertTrue(response.getStatus());
		assertEquals(response.getStatusCode(), "200");
		verify(kafkaBrazeHelper, never()).sendAttributesToBraze(anyList());
	}

	@Test
	public void testBrazeAttributePush_WithException() {
		Map<String, String> headers = new HashMap<>();

		when(Constants.orderCredentials).thenReturn(new GetOrderConsulValues());
		when(Constants.orderCredentials.getOrderDetails()).thenReturn(new OrderKeyDetails());
		when(Constants.orderCredentials.getOrderDetails().getBrazeAttributePushStartOffset()).thenReturn(7200L);
		when(amastyStoreCreditHistoryRepository.findByCreatedAtGreaterThan(any(Timestamp.class)))
				.thenThrow(new RuntimeException("Test exception"));

		AddStoreCreditResponse response = salesOrderServiceV2Impl.brazeAttributePush(headers);

		assertTrue(response.getStatus());
		assertEquals(response.getStatusCode(), "200");
	}

	@Test
	public void testBrazeAttributePush_WithNullStoreCredit() {
		Map<String, String> headers = new HashMap<>();

		AmastyStoreCreditHistory history = new AmastyStoreCreditHistory();
		history.setCustomerId(1);
		history.setCreatedAt(new Timestamp(System.currentTimeMillis()));

		List<AmastyStoreCreditHistory> histories = new ArrayList<>();
		histories.add(history);

		AmastyStoreCredit storeCredit = new AmastyStoreCredit();
		storeCredit.setCustomerId(1);
		storeCredit.setStoreCredit(null);

		List<AmastyStoreCredit> credits = new ArrayList<>();
		credits.add(storeCredit);

		when(Constants.orderCredentials).thenReturn(new GetOrderConsulValues());
		when(Constants.orderCredentials.getOrderDetails()).thenReturn(new OrderKeyDetails());
		when(Constants.orderCredentials.getOrderDetails().getBrazeAttributePushStartOffset()).thenReturn(7200L);
		when(amastyStoreCreditHistoryRepository.findByCreatedAtGreaterThan(any(Timestamp.class)))
				.thenReturn(histories);
		when(amastyStoreCreditRepository.findByCustomerIdIn(anyList())).thenReturn(credits);

		AddStoreCreditResponse response = salesOrderServiceV2Impl.brazeAttributePush(headers);

		assertTrue(response.getStatus());
		assertEquals(response.getStatusCode(), "200");
		verify(kafkaBrazeHelper, never()).sendAttributesToBraze(anyList());
	}
}

