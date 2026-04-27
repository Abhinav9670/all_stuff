package org.styli.services.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.converter.OmsorderentityConverter;
import org.styli.services.order.converter.OrderEntityConverter;
import org.styli.services.order.converter.OrderEntityConverterV1;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.helper.ExternalQuoteHelper;
import org.styli.services.order.helper.MulinHelper;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.jwt.security.jwtsecurity.model.JwtUser;
import org.styli.services.order.jwt.security.jwtsecurity.security.JwtValidator;
import org.styli.services.order.model.SalesOrder.SalesOrderStatusLabel;
import org.styli.services.order.model.SalesOrder.SalesOrderStatusLabelPK;
import org.styli.services.order.model.rma.AmastyRmaRequest;
import org.styli.services.order.model.rma.AmastyRmaRequestItem;
import org.styli.services.order.model.rma.AmastyRmaStatus;
import org.styli.services.order.model.rma.AmastyRmaStatusStore;
import org.styli.services.order.model.rma.AmastyRmaTracking;
import org.styli.services.order.model.rma.AmastyStoreCredit;
import org.styli.services.order.model.rma.AmastyStoreCreditHistory;
import org.styli.services.order.model.sales.ProxyOrder;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SalesShipmentTrack;
import org.styli.services.order.model.sales.SubSalesOrder;
import org.styli.services.order.pojo.CreateRetryPaymentReplicaDTO;
import org.styli.services.order.pojo.GetOrderConsulValues;
import org.styli.services.order.pojo.OrderKeyDetails;
import org.styli.services.order.pojo.QuoteUpdateDTO;
import org.styli.services.order.pojo.mulin.GetProductsBySkuResponse;
import org.styli.services.order.pojo.mulin.ProductResponseBody;
import org.styli.services.order.pojo.mulin.Variant;
import org.styli.services.order.pojo.order.CreateReplicaQuoteV4Request;
import org.styli.services.order.pojo.order.OrderEmailRequest;
import org.styli.services.order.pojo.order.UpdateOrderResponseDTO;
import org.styli.services.order.pojo.quote.response.QuoteUpdateDTOV2;
import org.styli.services.order.pojo.request.GetPromosRequest;
import org.styli.services.order.pojo.request.OrderStatusENUM;
import org.styli.services.order.pojo.request.StoreCreditListRequest;
import org.styli.services.order.pojo.request.StoreCreditRequest;
import org.styli.services.order.pojo.request.UpdateOrderRequest;
import org.styli.services.order.pojo.request.Order.OrderListRequest;
import org.styli.services.order.pojo.request.Order.OrderStoreCreditRequest;
import org.styli.services.order.pojo.request.Order.OrderViewRequest;
import org.styli.services.order.pojo.response.BankCouponsresponse;
import org.styli.services.order.pojo.response.Customer;
import org.styli.services.order.pojo.response.CustomerProfileResponse;
import org.styli.services.order.pojo.response.CustomerStoreCreditResponse;
import org.styli.services.order.pojo.response.CustomerStoreCreditResponseList;
import org.styli.services.order.pojo.response.CustomerUpdateProfileResponse;
import org.styli.services.order.pojo.response.Coupon.ProductPromotionsDTO;
import org.styli.services.order.pojo.response.Order.CreditHistoryResponse;
import org.styli.services.order.pojo.response.Order.CustomerOrdersCountResponseDTO;
import org.styli.services.order.pojo.response.Order.CustomerOrdersResponseDTO;
import org.styli.services.order.pojo.response.Order.OrderResponseDTO;
import org.styli.services.order.pojo.response.Order.RMAOrderResponseDTO;
import org.styli.services.order.pojo.response.external.BankCouponData;
import org.styli.services.order.pojo.response.external.CustomBankCouponListResponse;
import org.styli.services.order.pojo.response.external.CustomCouponData;
import org.styli.services.order.pojo.response.external.CustomCouponListResponse;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.repository.Customer.CustomerEntityRepository;
import org.styli.services.order.repository.Rma.AmastyRmaReasonRepository;
import org.styli.services.order.repository.Rma.AmastyRmaRequestItemRepository;
import org.styli.services.order.repository.Rma.AmastyRmaRequestRepository;
import org.styli.services.order.repository.Rma.AmastyRmaStatusRepository;
import org.styli.services.order.repository.Rma.AmastyRmaTrackingRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditHistoryRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditRepository;
import org.styli.services.order.repository.SalesOrder.ProxyOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusLabelRepository;
import org.styli.services.order.repository.SalesOrder.SubSalesOrderRepository;
import org.styli.services.order.service.ConfigService;
import org.styli.services.order.service.impl.ConfigServiceImpl;
import org.styli.services.order.service.impl.SalesOrderRMAServiceImpl;
import org.styli.services.order.service.impl.SalesOrderServiceImpl;
import org.styli.services.order.service.impl.SalesOrderServiceV2Impl;
import org.styli.services.order.service.impl.child.GetFailedOrderList;
import org.styli.services.order.service.impl.child.GetOrderById;
import org.styli.services.order.service.impl.child.GetOrderCount;
import org.styli.services.order.service.impl.child.GetOrderList;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.utility.PaymentConstants;
import org.styli.services.order.utility.PaymentUtility;
import org.styli.services.order.utility.consulValues.ConsulValues;
import org.styli.services.order.utility.consulValues.FeatureBasedFlag;
import org.styli.services.order.utility.consulValues.PromoRedemptionValues;
import org.styli.services.order.utility.consulValues.PromoValues;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@SpringBootTest(classes = { OrderControllerTest.class })
public class OrderControllerTest extends AbstractTestNGSpringContextTests {
	@InjectMocks
	SalesOrderServiceV2Impl salesOrderServiceV2;
	@InjectMocks
	SalesOrderRMAServiceImpl salesOrderRMAService;
	@InjectMocks
	SalesOrderServiceImpl salesOrderService;
	@InjectMocks
	OrderEntityConverter orderEntityConverter;
	@Mock
	AmastyRmaTrackingRepository amastyRmaTrackingRepository;
	@Mock
	SubSalesOrderRepository subsalesOrderRepository;

	@Mock
	AmastyRmaRequestItemRepository amastyRmaRequestItemRepository;

	@Mock
	RestTemplate restTemplate;

	@Mock
	AmastyRmaRequestRepository amastyRmaRequestRepository;
	@Mock
	SalesCreditmemoRepository salesCreditmemoRepository;

	@Mock
	SalesOrderStatusLabelRepository salesOrderStatusLabelRepository;

	@InjectMocks
	MulinHelper mulinHelper;

	@InjectMocks
	OrderController orderController;
	@InjectMocks
	ConfigServiceImpl configService;
	@Mock
	ProxyOrderRepository proxyOrderRepository;
	@InjectMocks
	ExternalQuoteHelper externalQuoteHelper;

	@InjectMocks
	org.styli.services.order.utility.Constants constants;
	@InjectMocks
	GetOrderById getOrderById;
	@InjectMocks
	OrderHelper orderHelper;
	@InjectMocks
	OmsorderentityConverter omsorderentityConverter;

	@Mock
	StaticComponents staticComponents;

	@Mock
	AmastyRmaStatusRepository amastyRmaStatusRepository;
	@Mock
	AmastyRmaReasonRepository amastyRmaReasonRepository;

	@Mock
	AmastyStoreCreditHistoryRepository amastyStoreCreditHistoryRepository;
	@Mock
	AmastyStoreCreditRepository amastyStoreCreditRepository;

	@Mock
	JwtValidator validator;
	@Mock
	SalesOrderRepository salesOrderRepository;
	@Mock
	CustomerEntityRepository customerEntityRepository;
	@InjectMocks
	GetOrderList getOrderList;
	@InjectMocks
	OrderEntityConverterV1 orderEntityConverterV1;
	@InjectMocks
	StoreCreditListRequest storeCreditListRequest;

	@Mock
	private PaymentUtility paymentUtility;

	@InjectMocks
	GetFailedOrderList getFailedOrderList;
	@InjectMocks
	GetOrderCount getOrderCount;
	@InjectMocks
	private StoreCreditRequest storeRequest;

	Map<String, String> requestHeader;
	private List<Stores> storeList;
	SalesOrder salesOrder;

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
	}
	ConfigService configService1 = Mockito.mock(ConfigService.class);
//	@Test
//	public void testGetOrders() {
//		setStaticData();
//		setAuthenticateData();
//		setSalseOrderData();
//
//		SalesOrderStatusLabel label = new SalesOrderStatusLabel();
//		label.setLabel("packed");
//
//
//
//		when(salesOrderStatusLabelRepository.findById(any(SalesOrderStatusLabelPK.class))).thenReturn(label);
//		when(salesOrderRepository.findByEntityId(anyInt())).thenReturn(salesOrder);
//
//		when(salesOrderRepository.findByEntityIdAndCustomerId(anyInt(), anyInt())).thenReturn(salesOrder);
//		when(amastyRmaRequestRepository.getRMACount(anyInt(), anyInt())).thenReturn(1);
//		when(configService1.getWebsiteRefundByStoreId(anyInt())).thenReturn(10.0);
//
//		GetProductsBySkuResponse getProductsBySkuResponse = getMulinData();
//
//		ResponseEntity<GetProductsBySkuResponse> response = new ResponseEntity<>(HttpStatus.OK)
//				.ok(getProductsBySkuResponse);
//
//		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
//				Mockito.any(HttpEntity.class), Mockito.eq(GetProductsBySkuResponse.class))).thenReturn(response);
//
//		OrderKeyDetails keydetail = new OrderKeyDetails();
//		keydetail.setMaximumOrderPedningOrderThreshold(10);
//		GetOrderConsulValues val = new GetOrderConsulValues();
//		val.setOrderDetails(keydetail);
//		val.setExternalAuthEnable(false);
//		val.setInternalAuthEnable(false);
////		org.styli.services.order.utility.Constants.setOrderCredentials(new Gson().toJson(val).toString());
//		ReflectionTestUtils.setField(constants, "orderCredentials", val);
//		OrderResponseDTO responsedto = orderController.getOrder(requestHeader, 1, 1);
//		assertTrue(responsedto.getStatus());
//		assertNotNull(responsedto.getResponse());
//	}
//
//	@Test
//	public void testOrders() {
//		setStaticData();
//		setAuthenticateData();
//		setSalseOrderData();
//
//		SalesOrderStatusLabel label = new SalesOrderStatusLabel();
//		label.setLabel("packed");
//
//		when(salesOrderStatusLabelRepository.findById(any(SalesOrderStatusLabelPK.class))).thenReturn(label);
//		when(salesOrderRepository.findByEntityId(anyInt())).thenReturn(salesOrder);
//		when(salesOrderRepository.findByEntityIdAndCustomerId(anyInt(), anyInt())).thenReturn(salesOrder);
//		when(amastyRmaRequestRepository.getRMACount(anyInt(), anyInt())).thenReturn(1);
//		when(configService1.getWebsiteRefundByStoreId(anyInt())).thenReturn(10.0);
//
//		GetProductsBySkuResponse getProductsBySkuResponse = getMulinData();
//
//		ResponseEntity<GetProductsBySkuResponse> response = new ResponseEntity<>(HttpStatus.OK)
//				.ok(getProductsBySkuResponse);
//
//		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
//				Mockito.any(HttpEntity.class), Mockito.eq(GetProductsBySkuResponse.class))).thenReturn(response);
//
//		OrderKeyDetails keydetail = new OrderKeyDetails();
//		keydetail.setMaximumOrderPedningOrderThreshold(10);
//		GetOrderConsulValues val = new GetOrderConsulValues();
//		val.setOrderDetails(keydetail);
//		val.setExternalAuthEnable(false);
//		val.setInternalAuthEnable(false);
////		org.styli.services.order.utility.Constants.setOrderCredentials(new Gson().toJson(val).toString());
//		ReflectionTestUtils.setField(constants, "orderCredentials", val);
//
//		OrderViewRequest req = new OrderViewRequest();
//		req.setStoreId(1);
//		req.setOrderId(1);
//		req.setCustomerId(1);
//
//		OrderResponseDTO responsedto = orderController.orders(requestHeader, req, anyString());
//		assertTrue(responsedto.getStatus());
//		assertNotNull(responsedto.getResponse());
//	}

	@Test
	void testSendEmail() {
		setStaticData();
		OrderEmailRequest req = new OrderEmailRequest();
		req.setOrderId(1);

		ResponseEntity<String> response = new ResponseEntity<>(HttpStatus.OK).ok("");

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(String.class))).thenReturn(response);

		UpdateOrderResponseDTO respo = orderController.sendEmail(req);
		assertTrue(respo.getStatus());
		assertEquals(respo.getStatusCode(), "200");
	}

	@Test
	void testSendEmailfail() {
		setStaticData();
		OrderEmailRequest req = new OrderEmailRequest();
		req.setOrderId(1);

		ResponseEntity<String> response = new ResponseEntity<>(HttpStatus.OK).ok("");

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
						Mockito.any(HttpEntity.class), Mockito.eq(String.class)))
				.thenThrow(new ResourceAccessException("excep"));

		UpdateOrderResponseDTO respo = orderController.sendEmail(req);
		assertTrue(!respo.getStatus());
		assertEquals(respo.getStatusCode(), "203");
	}

	@Test
	void testupdateOrderFail() {
		setStaticData();
		UpdateOrderRequest req = new UpdateOrderRequest();
		req.setOrderId(1);

		when(salesOrderRepository.findByEntityIdAndCustomerId(anyInt(), anyInt())).thenReturn(salesOrder);
		UpdateOrderResponseDTO respo = orderController.updateOrder(req);
		assertTrue(!respo.getStatus());
		assertEquals(respo.getStatusCode(), "202");
	}

	@Test
	void testupdateOrder() {
		setStaticData();
		setSalseOrderData();
		UpdateOrderRequest req = new UpdateOrderRequest();
		req.setOrderId(1);
		req.setStatus(OrderStatusENUM.SUCCESS);
		req.setFortId("1");
		ResponseEntity<String> response = new ResponseEntity<>(HttpStatus.OK).ok("");

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
						Mockito.any(HttpEntity.class), Mockito.eq(String.class)))
				.thenThrow(new ResourceAccessException("excep"));
		SalesOrder order = new SalesOrder();
		when(salesOrderRepository.findByEntityIdAndCustomerId(any(), any())).thenReturn(salesOrder);
		UpdateOrderResponseDTO respo = orderController.updateOrder(req);
		assertTrue(respo.getStatus());
		assertEquals(respo.getStatusCode(), "200");
	}

	@Test
	void testorderderListAll() {
		setStaticData();
		setSalseOrderData();
		setAuthenticateData();
//		OrderKeyDetails keydetail=new OrderKeyDetails();
//		keydetail.setMaximumOrderPedningOrderThreshold(10);
//		GetOrderConsulValues val = new GetOrderConsulValues();
//		val.setOrderDetails(keydetail);
//		val.setExternalAuthEnable(false);
//		val.setInternalAuthEnable(false);
////		org.styli.services.order.utility.Constants.setOrderCredentials(new Gson().toJson(val).toString());
//		ReflectionTestUtils.setField(constants, "orderCredentials", val);

		OrderListRequest req = new OrderListRequest();
		req.setCustomerId(1);
		req.setStoreId(1);
		req.setCustomerEmail("test@mail.com");
		CustomerUpdateProfileResponse body = new CustomerUpdateProfileResponse();
		CustomerProfileResponse cus = new CustomerProfileResponse();
		Customer customer = new Customer();
		customer.setCustomerId(1);
		cus.setCustomer(customer);
		body.setResponse(cus);
		ResponseEntity<CustomerUpdateProfileResponse> response = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(CustomerUpdateProfileResponse.class))).thenReturn(response);
		when(salesOrderRepository.findByEntityIdAndCustomerId(any(), any())).thenReturn(salesOrder);
		CustomerOrdersResponseDTO respo = orderController.orderderListAll(requestHeader, req);
		assertTrue(respo.getStatus());
		assertEquals(respo.getStatusCode(), "200");
	}

	@Test
	void testorderderLisTFail() {
		setStaticData();
		setSalseOrderData();
		setAuthenticateData();

		OrderListRequest req = new OrderListRequest();
		req.setCustomerId(1);
		req.setStoreId(1);
		req.setCustomerEmail("test@mail.com");
		CustomerUpdateProfileResponse body = new CustomerUpdateProfileResponse();
		CustomerProfileResponse cus = new CustomerProfileResponse();
		Customer customer = new Customer();
		customer.setCustomerId(1);
		cus.setCustomer(customer);
		body.setResponse(cus);
		ResponseEntity<CustomerUpdateProfileResponse> response = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(CustomerUpdateProfileResponse.class))).thenReturn(response);
		when(salesOrderRepository.findByEntityIdAndCustomerId(any(), any())).thenReturn(salesOrder);
		CustomerOrdersResponseDTO respo = orderController.orderListFailed(requestHeader, req);
		assertTrue(respo.getStatus());
		assertEquals(respo.getStatusCode(), "200");
	}

	@Test
	void testzgetOrderCountAll() {
		setStaticData();
		setSalseOrderData();
		setAuthenticateData();
		OrderListRequest request = new OrderListRequest();
		request.setCustomerId(1);
		request.setStoreId(1);

		OrderListRequest req = new OrderListRequest();
		req.setCustomerId(1);
		req.setStoreId(1);
		req.setCustomerEmail("test@mail.com");
		CustomerUpdateProfileResponse body = new CustomerUpdateProfileResponse();
		CustomerProfileResponse cus = new CustomerProfileResponse();
		Customer customer = new Customer();
		customer.setCustomerId(1);
		cus.setCustomer(customer);
		body.setResponse(cus);
		ResponseEntity<CustomerUpdateProfileResponse> response = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(CustomerUpdateProfileResponse.class))).thenReturn(response);
		CustomerOrdersCountResponseDTO respo = orderController.getOrderCountAll(requestHeader, request);
		assertTrue(respo.getStatus());
		assertEquals(respo.getStatusCode(), "200");
	}

	@Test
	void TestGetAllOrderCountFailEmail() {
		setStaticData();
		setSalseOrderData();
		setAuthenticateData();
		OrderListRequest request = new OrderListRequest();
		request.setCustomerId(1);
		request.setStoreId(1);

		CustomerOrdersCountResponseDTO respo = orderController.getAllOrderCount(requestHeader, request);
		assertEquals(respo.getStatus(), false);
		assertEquals(respo.getStatusCode(), "203");
	}

	@Test
	void TeststireCreditHistory() {
		setStaticData();
		setSalseOrderData();
		setAuthenticateData();
		OrderStoreCreditRequest request = new OrderStoreCreditRequest();
		request.setCustomerId(1);
		request.setStoreId(1);

		AmastyStoreCreditHistory amastyStoreCreditHistory = new AmastyStoreCreditHistory();
		when(amastyStoreCreditHistoryRepository.findByCustomerIdOrderByHistoryIdDesc(anyInt()))
				.thenReturn(Arrays.asList(amastyStoreCreditHistory));

		AmastyStoreCredit amastyStoreCredit = new AmastyStoreCredit();
		when(amastyStoreCreditRepository.findByCustomerId(anyInt())).thenReturn(Arrays.asList(amastyStoreCredit));
		CreditHistoryResponse respo = orderController.stireCreditHistory(requestHeader, request);
		assertEquals(respo.getStatus(), true);
		assertEquals(respo.getStatusCode(), "200");
	}

	@Test
	void testOrderListReturns() {
		setStaticData();
		setSalseOrderData();
		setAuthenticateData();
		OrderListRequest request = new OrderListRequest();
		request.setCustomerId(1);
		request.setStoreId(1);
		request.setOffSet(10);
		request.setPageSize(10);

		// when(configService.getWebsiteStoresByStoreId(any())).thenReturn(Arrays.asList(1,2));

		AmastyRmaRequest amastyRmaRequest = new AmastyRmaRequest();
		amastyRmaRequest.setCustomerId(1);
		amastyRmaRequest.setStoreId(1);
		amastyRmaRequest.setOrderId(1);
		amastyRmaRequest.setRequestId(1);
		amastyRmaRequest.setStatus(1);

		AmastyRmaRequestItem amastyRmaRequestItem = new AmastyRmaRequestItem();
		amastyRmaRequestItem.setRequestId(1);
		amastyRmaRequestItem.setRequestItemId(1);
		amastyRmaRequestItem.setOrderItemId(1);
		Set<AmastyRmaRequestItem> amastyRmaRequestItems = new HashSet<>();
		amastyRmaRequestItems.add(amastyRmaRequestItem);
		amastyRmaRequest.setAmastyRmaRequestItems(amastyRmaRequestItems);

		when(amastyRmaRequestRepository.findByCustomerIdAndStoreIdIn(anyInt(), any(), any()))
				.thenReturn(Arrays.asList(amastyRmaRequest));

		GetProductsBySkuResponse getProductsBySkuResponse = getMulinData();

		ResponseEntity<GetProductsBySkuResponse> response = new ResponseEntity<>(HttpStatus.OK)
				.ok(getProductsBySkuResponse);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(GetProductsBySkuResponse.class))).thenReturn(response);
		when(salesOrderRepository.findByEntityIdIn(any())).thenReturn(Arrays.asList(salesOrder));
		when(salesOrderRepository.findByEntityIdAndCustomerId(anyInt(), anyInt())).thenReturn(salesOrder);

		AmastyStoreCredit amastyStoreCredit = new AmastyStoreCredit();
		when(amastyStoreCreditRepository.findByCustomerId(anyInt())).thenReturn(Arrays.asList(amastyStoreCredit));

		AmastyRmaTracking amastyRmaTracking = new AmastyRmaTracking();
		amastyRmaTracking.setRequestId(1);
		amastyRmaTracking.setTrackingId(1);
		when(amastyRmaTrackingRepository.findByRequestId(anyInt())).thenReturn(Arrays.asList(amastyRmaTracking));
		AmastyRmaStatusStore amastyRmaStatusStore = new AmastyRmaStatusStore();
		amastyRmaStatusStore.setStoreId(1);
		amastyRmaStatusStore.setLabel("label");
		Set<AmastyRmaStatusStore> set = new HashSet<>();
		set.add(amastyRmaStatusStore);
		AmastyRmaStatus amastyRmaStatus = new AmastyRmaStatus();
		amastyRmaStatus.setAmastyRmaStatusStores(set);
		when(amastyRmaStatusRepository.findByStatusId(anyInt())).thenReturn(amastyRmaStatus);
		when(salesCreditmemoRepository.findByRmaNumber(anyString())).thenReturn(null);

//
		ConsulValues orderConsulValues = new ConsulValues();
		orderConsulValues.setReturnShortSinglepickSms("true");
		when(amastyRmaRequestItemRepository.findByRequestItemId(anyInt()))
				.thenReturn(Arrays.asList(amastyRmaRequestItem));

		Constants.setOrderConsulValues(new Gson().toJson(orderConsulValues).toString());

		RMAOrderResponseDTO respo = orderController.orderListReturns(requestHeader, request);
		assertEquals(respo.getStatus(), true);
		assertEquals(respo.getStatusCode(), "200");
	}

	@Test
	void tesorderCountReturns() {
		setStaticData();
		setSalseOrderData();
		setAuthenticateData();
		OrderListRequest req = new OrderListRequest();
		req.setCustomerId(1);
		req.setStoreId(1);
		req.setCustomerEmail("test@mail.com");
		when(amastyRmaRequestRepository.countByCustomerIdAndStoreIdIn(anyInt(), any())).thenReturn((long) 1);
		RMAOrderResponseDTO respo = orderController.orderCountReturns(requestHeader, req);
		assertEquals(respo.getStatus(), true);
		assertEquals(respo.getStatusCode(), "200");
	}

	@Test
	void tesGetAllCoupons() {
		setStaticData();
		setSalseOrderData();
		GetPromosRequest req = new GetPromosRequest();
		req.setStoreId(1);
		req.setCustomerId("1");
		req.setCustomerEmail("name");
		when(amastyRmaRequestRepository.countByCustomerIdAndStoreIdIn(anyInt(), any())).thenReturn((long) 1);
		PromoValues val = new PromoValues();

		PromoRedemptionValues pv = new PromoRedemptionValues();
		pv.setDefaultCouponListEndpoint("url");
		val.setPromoRedemptionUrl(pv);

		FeatureBasedFlag pfBValue = new FeatureBasedFlag();
		pfBValue.setCohortBasedCoupon(true);
		val.setFeatureBasedFlag(pfBValue);

		Constants.setPromoConsulValues(new Gson().toJson(val).toString());

		CustomCouponListResponse body = new CustomCouponListResponse();
		body.setData(Arrays.asList(new CustomCouponData()));
		body.setCode(200);
		ResponseEntity<CustomCouponListResponse> response = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.any(URI.class), Mockito.eq(HttpMethod.GET),
				Mockito.any(HttpEntity.class), Mockito.eq(CustomCouponListResponse.class))).thenReturn(response);
		Mockito.when(restTemplate.exchange(Mockito.any(URI.class), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(CustomCouponListResponse.class))).thenReturn(response);
		ProductPromotionsDTO respo = orderController.getAllCoupons(requestHeader, req);
		assertEquals(respo.getStatus(), true);
		assertEquals(respo.getStatusCode(), "200");
	}

	@Test
	void testzgetStoreCreditDetailsFailed() {
		setStaticData();
		setSalseOrderData();
		setAuthenticateData();

		CustomerUpdateProfileResponse body = new CustomerUpdateProfileResponse();
		CustomerProfileResponse cus = new CustomerProfileResponse();
		Customer customer = new Customer();
		customer.setCustomerId(1);
		cus.setCustomer(customer);
		body.setResponse(cus);
		ResponseEntity<CustomerUpdateProfileResponse> response = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(CustomerUpdateProfileResponse.class))).thenReturn(response);

		CustomerStoreCreditResponse reponse = orderController.getStoreCreditDetails(requestHeader, storeRequest);
		assertEquals(reponse.isStatus(), true);
		assertEquals(reponse.getStatusCode(), "200");
	}

	@Test
	void testgetStoreCreditDetails() {
		setStaticData();
		setSalseOrderData();
		setAuthenticateData();
		storeRequest.setStoreId(1);
		storeRequest.setCustomerId(1);
		CustomerUpdateProfileResponse body = new CustomerUpdateProfileResponse();
		CustomerProfileResponse cus = new CustomerProfileResponse();
		Customer customer = new Customer();
		customer.setCustomerId(1);
		cus.setCustomer(customer);
		body.setResponse(cus);
		ResponseEntity<CustomerUpdateProfileResponse> response = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(CustomerUpdateProfileResponse.class))).thenReturn(response);
		AmastyStoreCredit amastyStoreCredit = new AmastyStoreCredit();
		amastyStoreCredit.setStoreCredit(new BigDecimal(10));
		when(amastyStoreCreditRepository.findByCustomerId(anyInt())).thenReturn(Arrays.asList(amastyStoreCredit));
		CustomerStoreCreditResponse reponse = orderController.getStoreCreditDetails(requestHeader, storeRequest);
		assertEquals(reponse.isStatus(), true);
		assertEquals(reponse.getStatusCode(), "200");
	}

	@Test
	void testCreateQuoteReplica() {
		setStaticData();
		setSalseOrderData();
		setAuthenticateData();
		CreateReplicaQuoteV4Request req = new CreateReplicaQuoteV4Request();
		req.setStoreId(1);
		req.setOrderId(1);
		req.setTabbyPaymentId("1");
		// Optional<ProxyOrder> proxyOrder = new optiona
		when(salesOrderRepository.findByEntityId(any())).thenReturn(salesOrder);
		when(salesOrderRepository.findByIncrementId(any())).thenReturn(salesOrder);
		when(proxyOrderRepository.findByIdOrPaymentId(anyLong(), anyString()))
				.thenReturn(Optional.of(mockProxyOrder()));
		QuoteUpdateDTO respo = orderController.createQuoteReplica(req, "token", "");
		assertEquals(respo.getStatus(), true);
		assertEquals(respo.getStatusCode(), "200");
	}

	@Test
	void testCreateQuoteReplica2() {
		setStaticData();
		setSalseOrderData();
		setAuthenticateData();
		CreateReplicaQuoteV4Request req = new CreateReplicaQuoteV4Request();
		req.setStoreId(1);
		req.setOrderId(1);
		req.setTabbyPaymentId("1");
		// Optional<ProxyOrder> proxyOrder = new optiona
		when(salesOrderRepository.findByEntityId(any())).thenReturn(salesOrder);
		when(salesOrderRepository.findByIncrementId(any())).thenReturn(null);
		when(proxyOrderRepository.findByIdOrPaymentId(anyLong(), anyString()))
				.thenReturn(Optional.of(mockProxyOrder()));

		QuoteUpdateDTOV2 body = new QuoteUpdateDTOV2();
		body.setCustomerId(1);
		body.setStatus(true);
		body.setStatusCode("200");
		ResponseEntity<QuoteUpdateDTOV2> response = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(QuoteUpdateDTOV2.class))).thenReturn(response);

		QuoteUpdateDTO respo = orderController.createQuoteReplica(req, "token", "");
		assertEquals(respo.getStatus(), true);
		assertEquals(respo.getStatusCode(), "200");
	}

	@Test
	void testcreateRetryPaymentReplica() {
		setStaticData();
		setSalseOrderData();
		setAuthenticateData();
		CreateReplicaQuoteV4Request req = new CreateReplicaQuoteV4Request();
		req.setStoreId(1);
		req.setOrderId(1);
		req.setCustomerId(1);
		req.setTabbyPaymentId("1");
		// Optional<ProxyOrder> proxyOrder = new optiona
		SubSalesOrder subSaleOrder = new SubSalesOrder();
		salesOrder.setSubSalesOrder(subSaleOrder);
		salesOrder.setCreatedAt(new Timestamp(10));
		salesOrder.setStatus(OrderConstants.PENDING_PAYMENT_ORDER_STATUS);
		when(salesOrderRepository.findByEntityIdAndCustomerId(anyInt(), anyInt())).thenReturn(salesOrder);
		when(salesOrderRepository.findByIncrementId(any())).thenReturn(null);
		when(proxyOrderRepository.findByIdOrPaymentId(anyLong(), anyString()))
				.thenReturn(Optional.of(mockProxyOrder()));

		OrderKeyDetails keydetail = new OrderKeyDetails();
		keydetail.setMaximumOrderPedningOrderThreshold(10);
		GetOrderConsulValues val = new GetOrderConsulValues();
		val.setOrderDetails(keydetail);
		val.setExternalAuthEnable(false);
		val.setInternalAuthEnable(false);
		val.setWms(null);
		ReflectionTestUtils.setField(constants, "orderCredentials", val);

		CreateRetryPaymentReplicaDTO body = new CreateRetryPaymentReplicaDTO();
		body.setCustomerId(1);
		body.setStatus(true);
		body.setStatusCode("200");
		body.setTriedPaymentCount(1);
		body.setRetryPaymentThreshold(5);
		ResponseEntity<CreateRetryPaymentReplicaDTO> response = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(CreateRetryPaymentReplicaDTO.class))).thenReturn(response);

		QuoteUpdateDTO respo = orderController.createRetryPaymentReplica(req, "token", "");
		assertEquals(respo.getStatus(), true);
		assertEquals(respo.getStatusCode(), "200");
	}

	@Test
	void testgetBankCoupons() {
		setStaticData();
		GetPromosRequest req = new GetPromosRequest();
		BankCouponsresponse res = orderController.getBankCoupons(requestHeader, req);
		assertEquals(res.getStatus(), true);
		assertEquals(res.getStatusCode(), "200");
	}

	@Test
	void testgetBankCoupons2() {
		setStaticData();
		GetPromosRequest req = new GetPromosRequest();
		PromoValues val = new PromoValues();
		PromoRedemptionValues pv = new PromoRedemptionValues();
		pv.setDefaultCouponListEndpoint("url");
		pv.setDefaultBankOffersEndpoint("url");
		val.setPromoRedemptionUrl(pv);
		Constants.setPromoConsulValues(new Gson().toJson(val).toString());

		CustomBankCouponListResponse body = new CustomBankCouponListResponse();
		body.setCode(200);
		BankCouponData data = new BankCouponData();
		data.setImageUrl("//image");
		body.setData(Arrays.asList(data));

		ResponseEntity<CustomBankCouponListResponse> response = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.any(URI.class), Mockito.eq(HttpMethod.GET),
				Mockito.any(HttpEntity.class), Mockito.eq(CustomBankCouponListResponse.class))).thenReturn(response);

		BankCouponsresponse res = orderController.getBankCoupons(new HashMap<>(), req);
		assertEquals(res.getStatus(), true);
		assertEquals(res.getStatusCode(), "200");
	}

	@Test
	void testgetStoreCreditList() {
		setStaticData();
		storeCreditListRequest.setStoreId(1);
		storeCreditListRequest.setCustomerIds(Arrays.asList(1));
		ReflectionTestUtils.setField(configService, "internalAuthBearerToken", "token,jjd,nds");
		OrderKeyDetails keydetail = new OrderKeyDetails();
		keydetail.setMaximumOrderPedningOrderThreshold(10);
		GetOrderConsulValues val = new GetOrderConsulValues();
		val.setOrderDetails(keydetail);
		val.setExternalAuthEnable(false);
		val.setInternalAuthEnable(false);
		val.setInternalAuthEnable(true);
		val.setWms(null);
		ReflectionTestUtils.setField(constants, "orderCredentials", val);

		CustomerUpdateProfileResponse body = new CustomerUpdateProfileResponse();
		CustomerProfileResponse cus = new CustomerProfileResponse();
		Customer customer = new Customer();
		customer.setCustomerId(1);
		cus.setCustomer(customer);
		body.setResponse(cus);
		ResponseEntity<CustomerUpdateProfileResponse> response = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(CustomerUpdateProfileResponse.class))).thenReturn(response);

		CustomerStoreCreditResponseList res = orderController.getStoreCreditList(requestHeader, storeCreditListRequest,
				"token");
		assertEquals(res.isStatus(), true);
		assertEquals(res.getStatusCode(), "200");
	}

	@Test
	void getOrderTest() {
		setStaticData();
		setSalseOrderData();

		when(subsalesOrderRepository.findOrderId("1")).thenReturn(1);
		when(salesOrderRepository.findByEntityId(anyInt())).thenReturn(salesOrder);
		OrderResponseDTO respo = orderController.getOrder(requestHeader, "1");
		assertEquals(respo.getStatus(), true);
		assertEquals(respo.getStatusCode(), "200");
	}

//	@Test
//	void orderPaybleDetailTest() {
//		setStaticData();
//		setSalseOrderData();
//		OrderViewRequest req = new OrderViewRequest();
//		req.setOrderId(1);
//		req.setStoreId(1);
//		OrderKeyDetails keydetail = new OrderKeyDetails();
//		keydetail.setMaximumOrderPedningOrderThreshold(10);
//		GetOrderConsulValues val = new GetOrderConsulValues();
//		val.setOrderDetails(keydetail);
//		val.setExternalAuthEnable(false);
//		val.setInternalAuthEnable(false);
////		org.styli.services.order.utility.Constants.setOrderCredentials(new Gson().toJson(val).toString());
//		ReflectionTestUtils.setField(constants, "orderCredentials", val);
//		when(amastyRmaRequestRepository.getRMACount(anyInt(), anyInt())).thenReturn(1);
//		when(configService1.getWebsiteRefundByStoreId(anyInt())).thenReturn(10.0);
//		when(subsalesOrderRepository.findOrderId("1")).thenReturn(1);
//		when(salesOrderRepository.findPendingOrderList(any(), any())).thenReturn(salesOrder);
//		OrderResponseDTO respo = orderController.orderPaybleDetail(requestHeader, req);
//		assertEquals(respo.getStatus(), true);
//		assertEquals(respo.getStatusCode(), "200");
//	}

	private ProxyOrder mockProxyOrder() {
		// TODO Auto-generated method stub
		ProxyOrder order = new ProxyOrder();
		order.setId(1l);
		order.setSalesOrder(new Gson().toJson(salesOrder).toString());

		order.setPaymentMethod(PaymentConstants.TABBY_INSTALMENTS);
		return order;
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

	private void setAuthenticateData() {
		// TODO Auto-generated method stub
		JwtUser jwtUser = new JwtUser();
		jwtUser.setCustomerId(1);
		when(validator.validate(anyString())).thenReturn(jwtUser);
	}

	private void setSalseOrderData() {
		salesOrder = new SalesOrder();
		salesOrder.setCustomerId(1);
		salesOrder.setStatus("packed");
		SalesOrderItem items = new SalesOrderItem();
		items.setSku("01");
		items.setItemId(1);
		Set<SalesOrderItem> setitem = new HashSet<>();
		setitem.add(items);
		salesOrder.setSalesOrderItem(setitem);
		Set<SalesShipmentTrack> set = new HashSet<>();
		SalesShipmentTrack track = new SalesShipmentTrack();
		track.setTrackNumber("1");
		salesOrder.setSalesShipmentTrack(set);
		set.add(track);
	}

	private void setStaticData() {
		// TODO Auto-generated method stub
		ReflectionTestUtils.setField(orderController, "jwtFlag", "1");
		ReflectionTestUtils.setField(constants, "IS_JWT_TOKEN_ENABLE", true);
		ReflectionTestUtils.setField(orderController, "orderEntityConverterV1", orderEntityConverterV1);
		ReflectionTestUtils.setField(orderController, "configService", configService);
		ReflectionTestUtils.setField(orderController, "salesOrderService", salesOrderService);
		ReflectionTestUtils.setField(orderController, "salesOrderRMAService", salesOrderRMAService);
		ReflectionTestUtils.setField(orderController, "salesOrderServiceV2", salesOrderServiceV2);
		ReflectionTestUtils.setField(orderController, "orderEntityConverter", orderEntityConverter);
		ReflectionTestUtils.setField(orderController, "mulinHelper", mulinHelper);
		ReflectionTestUtils.setField(salesOrderService, "mulinHelper", mulinHelper);
		ReflectionTestUtils.setField(salesOrderService, "orderHelper", orderHelper);
		ReflectionTestUtils.setField(salesOrderService, "orderEntityConverter", orderEntityConverter);
		ReflectionTestUtils.setField(salesOrderService, "getOrderById", getOrderById);
		ReflectionTestUtils.setField(salesOrderService, "getOrderList", getOrderList);
		ReflectionTestUtils.setField(salesOrderService, "configService", configService);
		ReflectionTestUtils.setField(salesOrderService, "getFailedOrderList", getFailedOrderList);
		ReflectionTestUtils.setField(salesOrderRMAService, "configService", configService);
		ReflectionTestUtils.setField(salesOrderRMAService, "mulinHelper", mulinHelper);
		ReflectionTestUtils.setField(salesOrderRMAService, "omsorderentityConverter", omsorderentityConverter);
		ReflectionTestUtils.setField(salesOrderServiceV2, "externalQuoteHelper", externalQuoteHelper);

		ReflectionTestUtils.setField(salesOrderService, "omsorderentityConverter", omsorderentityConverter);
		ReflectionTestUtils.setField(salesOrderService, "getOrderCount", getOrderCount);
		ReflectionTestUtils.setField(getOrderList, "orderHelper", orderHelper);
		ReflectionTestUtils.setField(getFailedOrderList, "orderHelper", orderHelper);
		ReflectionTestUtils.setField(getOrderCount, "orderHelper", orderHelper);
		ReflectionTestUtils.setField(constants, "storesList", storeList);
	}
}