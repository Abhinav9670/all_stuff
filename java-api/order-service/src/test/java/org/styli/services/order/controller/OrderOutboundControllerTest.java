package org.styli.services.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.styli.services.order.converter.OmsorderentityConverter;
import org.styli.services.order.converter.OrderEntityConverter;
import org.styli.services.order.converter.OrderEntityConverterV1;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.helper.ExternalQuoteHelper;
import org.styli.services.order.helper.KafkaBrazeHelper;
import org.styli.services.order.helper.MulinHelper;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.jwt.security.jwtsecurity.model.JwtUser;
import org.styli.services.order.jwt.security.jwtsecurity.security.JwtValidator;
import org.styli.services.order.model.rma.AmastyRmaRequest;
import org.styli.services.order.model.rma.AmastyStoreCredit;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderAddress;
import org.styli.services.order.model.sales.SalesOrderGrid;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SalesShipmentTrack;
import org.styli.services.order.model.sales.SubSalesOrder;
import org.styli.services.order.pojo.DisabledServices;
import org.styli.services.order.pojo.GenericApiResponse;
import org.styli.services.order.pojo.GetOrderConsulValues;
import org.styli.services.order.pojo.OrderKeyDetails;
import org.styli.services.order.pojo.PendingOrderNotfcnDetails;
import org.styli.services.order.pojo.consul.oms.base.OmsBaseConfigs;
import org.styli.services.order.pojo.consul.oms.base.WhatsappOrderConfig;
import org.styli.services.order.pojo.response.Customer;
import org.styli.services.order.pojo.response.CustomerProfileResponse;
import org.styli.services.order.pojo.response.CustomerUpdateProfileResponse;
import org.styli.services.order.pojo.response.InventoryBlockResponse;
import org.styli.services.order.pojo.response.InventoryMetaData;
import org.styli.services.order.pojo.response.Order.CustomerOrdersResponseDTO;
import org.styli.services.order.pojo.projection.ReferralOrderProjection;
import org.styli.services.order.pojo.response.external.ReferralOrderListResponse;
import org.styli.services.order.pojo.whatsapp.bot.MobileOrderDetailRequest;
import org.styli.services.order.pojo.whatsapp.bot.MobileOrderDetailResponse;
import org.styli.services.order.pojo.whatsapp.bot.MobileOrderListRequest;
import org.styli.services.order.pojo.whatsapp.bot.MobileOrderListResponse;
import org.styli.services.order.pojo.whatsapp.bot.MobileReturnDetailResponse;
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
import org.styli.services.order.repository.SalesOrder.SalesOrderAddressRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusHistoryRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusLabelRepository;
import org.styli.services.order.repository.SalesOrder.SubSalesOrderRepository;
import org.styli.services.order.service.PaymentService;
import org.styli.services.order.service.impl.ConfigServiceImpl;
import org.styli.services.order.service.impl.EASServiceImpl;
import org.styli.services.order.service.impl.KafkaServiceImpl;
import org.styli.services.order.service.impl.SalesOrderRMAServiceImpl;
import org.styli.services.order.service.impl.SalesOrderServiceImpl;
import org.styli.services.order.service.impl.SalesOrderServiceV2Impl;
import org.styli.services.order.service.impl.WhatsappBotServiceImpl;
import org.styli.services.order.service.impl.child.GetFailedOrderList;
import org.styli.services.order.service.impl.child.GetOrderById;
import org.styli.services.order.service.impl.child.GetOrderCount;
import org.styli.services.order.service.impl.child.GetOrderList;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.utility.PaymentUtility;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@SpringBootTest(classes = { OrderOutboundControllerTest.class })
public class OrderOutboundControllerTest extends AbstractTestNGSpringContextTests {
	@InjectMocks
	OrderOutboundController orderOutboundController;
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
	KafkaTemplate<String, Object> kafkaTemplate;

	@Mock
	KafkaBrazeHelper kafkaBrazeHelper;

	@Mock
	AmastyRmaRequestRepository amastyRmaRequestRepository;
	@Mock
	SalesCreditmemoRepository salesCreditmemoRepository;
	@Mock
	SalesOrderAddressRepository salesOrderAddressRepository;

	@Mock
	SalesOrderStatusLabelRepository salesOrderStatusLabelRepository;

	@InjectMocks
	MulinHelper mulinHelper;

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

	@InjectMocks
	EASServiceImpl eASServiceImpl;
	@Mock
	PaymentService paymentService;

	@Mock
	StaticComponents staticComponents;

	@Mock
	SalesOrderStatusHistoryRepository salesOrderStatusHistoryRepository;
	@Mock
	SalesOrderGridRepository salesOrderGridRepository;

	@Mock
	AmastyRmaStatusRepository amastyRmaStatusRepository;
	@Mock
	AmastyRmaReasonRepository amastyRmaReasonRepository;

	@Mock
	AmastyStoreCreditHistoryRepository amastyStoreCreditHistoryRepository;
	@Mock
	AmastyStoreCreditRepository amastyStoreCreditRepository;
	@Mock
	KafkaServiceImpl kafkaService;
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

	@Mock
	private PaymentUtility paymentUtility;

	@InjectMocks
	GetFailedOrderList getFailedOrderList;
	@InjectMocks
	GetOrderCount getOrderCount;
	@InjectMocks
	WhatsappBotServiceImpl whatsappBotService;

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
		ReflectionTestUtils.setField(configService, "internalAuthBearerToken", "token,dwd,1,2");
		ReflectionTestUtils.setField(configService, "externalAuthBearerToken", "token,ds,1,1");
	}

	@Test
	public void testReferalOfforderderListAll() {
		setStaticData();
		setAuthenticateData();
		setSalseOrderData();
		setCustomerOrderresponseData();
		setdisabledSer(true);
		CustomerOrdersResponseDTO respo = orderOutboundController.orderderListAll(requestHeader);
		assertTrue(!respo.getStatus());
		assertEquals(respo.getStatusCode(), "202");

	}

	@Test
	public void testReferalOnorderderListAll() {
		setStaticData();
		setAuthenticateData();
		setSalseOrderData();
		setCustomerOrderresponseData();
		setdisabledSer(false);
		setorderCred();
		ReferralOrderProjection referralProjection = mock(ReferralOrderProjection.class);
		when(referralProjection.getCustomerId()).thenReturn(1);
		when(salesOrderRepository.getReferralDeliveredOrders(anyInt())).thenReturn(Arrays.asList(referralProjection));

		ReferralOrderListResponse body = new ReferralOrderListResponse();
		body.setCode(200);
		body.setStatus(true);
		ResponseEntity<ReferralOrderListResponse> response = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(ReferralOrderListResponse.class))).thenReturn(response);

		CustomerOrdersResponseDTO respo = orderOutboundController.orderderListAll(requestHeader);
		assertTrue(!respo.getStatus());
		

	}

	@Test
	public void testmobileOrderList() {
		setStaticData();
		setAuthenticateData();
		setSalseOrderData();
		setCustomerOrderresponseData();
		setdisabledSer(false);
		setorderCred();
		setwhatapporderConfi();

		MobileOrderListRequest req = new MobileOrderListRequest();
		req.setPhoneNo("+918762156784");
		ReferralOrderProjection referralProjection = mock(ReferralOrderProjection.class);
		when(referralProjection.getCustomerId()).thenReturn(1);
		when(salesOrderRepository.getReferralDeliveredOrders(anyInt())).thenReturn(Arrays.asList(referralProjection));
		when(salesOrderAddressRepository.findCurrentOrdersByTelephone(anyString(), anyLong()))
				.thenReturn(Arrays.asList("1", "2", "3"));

		ReferralOrderListResponse body = new ReferralOrderListResponse();
		body.setCode(200);
		body.setStatus(true);
		ResponseEntity<ReferralOrderListResponse> response = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(ReferralOrderListResponse.class))).thenReturn(response);

		GenericApiResponse<MobileOrderListResponse> respo = orderOutboundController.mobileOrderList(req, requestHeader,
				"token");
		assertTrue(respo.getStatus());
		assertEquals(respo.getStatusCode(), "200");
	}

	@Test
	public void testmobileOrderDetails() {
		setStaticData();
		setAuthenticateData();
		setSalseOrderData();
		setCustomerOrderresponseData();
		setdisabledSer(false);
		setorderCred();
		setwhatapporderConfi();

		MobileOrderDetailRequest req = new MobileOrderDetailRequest();
		req.setId("1");
		when(salesOrderRepository.findByIncrementId(any())).thenReturn(salesOrder);
		when(salesOrderAddressRepository.findCurrentOrdersByTelephone(anyString(), anyLong()))
				.thenReturn(Arrays.asList("1", "2", "3"));

		ReferralOrderListResponse body = new ReferralOrderListResponse();
		body.setCode(200);
		body.setStatus(true);
		ResponseEntity<ReferralOrderListResponse> response = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(ReferralOrderListResponse.class))).thenReturn(response);

		GenericApiResponse<MobileOrderDetailResponse> respo = orderOutboundController.mobileOrderDetails(req,
				requestHeader, "token");
		assertTrue(respo.getStatus());
		assertEquals(respo.getStatusCode(), "200");
	}

	@Test
	void testmakePaymentPendingOrdersToPaymentFailed() {
		setStaticData();
		setAuthenticateData();
		setSalseOrderData();
		setorderCred();
		SubSalesOrder subSaleOrder = new SubSalesOrder();
		salesOrder.setCustomerId(1);
		salesOrder.setCustomerIsGuest(0);
		salesOrder.setSubSalesOrder(subSaleOrder);
		salesOrder.setCreatedAt(new Timestamp(10));
		salesOrder.setStatus(OrderConstants.PENDING_PAYMENT_ORDER_STATUS);
		when(salesOrderGridRepository.findByEntityId(any())).thenReturn(new SalesOrderGrid());
		when(salesOrderRepository.findPendingPaymentOrderWithinMinutes()).thenReturn(Arrays.asList(salesOrder));
		when(salesOrderAddressRepository.findCurrentOrdersByTelephone(anyString(), anyLong()))
				.thenReturn(Arrays.asList("1", "2", "3"));

		AmastyStoreCredit amastyStoreCredit = new AmastyStoreCredit();
		when(amastyStoreCreditRepository.findByCustomerId(anyInt())).thenReturn(Arrays.asList(amastyStoreCredit));
		InventoryMetaData meta = new InventoryMetaData();
		meta.setStatus(true);
		InventoryBlockResponse body = new InventoryBlockResponse();
		body.setMeta(meta);
		body.setResponse("response");
		ResponseEntity<InventoryBlockResponse> response = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(InventoryBlockResponse.class))).thenReturn(response);

		orderOutboundController.makePaymentPendingOrdersToPaymentFailed("token");
		verify(salesOrderGridRepository, atLeast(1)).findByEntityId(any());
//		verify(amastyStoreCreditRepository,atLeast(1)).findByCustomerId(anyInt());
	}

	@Test
	void testsendBrazeNotificatonForPendingOrder() {
		setStaticData();
		setAuthenticateData();
		setSalseOrderData();
		setorderCred();
		SubSalesOrder subSaleOrder = new SubSalesOrder();

		LocalDateTime currentTime = LocalDateTime.now();

		LocalDateTime previousDay = currentTime.minus(1, ChronoUnit.DAYS);
		Timestamp timestamp = Timestamp.valueOf(previousDay);
		subSaleOrder.setFirstNotificationAt(timestamp);
		subSaleOrder.setSecondNotificationAt(timestamp);
		salesOrder.setCustomerId(1);
		salesOrder.setCustomerIsGuest(0);
		salesOrder.setSubSalesOrder(subSaleOrder);
		salesOrder.setCreatedAt(new Timestamp(10));
		salesOrder.setStatus(OrderConstants.PENDING_PAYMENT_ORDER_STATUS);
		SalesOrderAddress set = new SalesOrderAddress();
		set.setTelephone("+918794378763");
		set.setAddressType(Constants.QUOTE_ADDRESS_TYPE_SHIPPING);
		Set<SalesOrderAddress> salesOrderAddress = new HashSet<>();
		salesOrderAddress.add(set);

		salesOrder.setSalesOrderAddress(salesOrderAddress);
		subSaleOrder.setSalesOrder(salesOrder);

		when(subsalesOrderRepository.findExpiredOrder(any())).thenReturn(Arrays.asList(subSaleOrder));

		orderOutboundController.sendBrazeNotificatonForPendingOrder("token");
		verify(subsalesOrderRepository, atLeast(1)).findExpiredOrder(any());
	}

	@Test
	void testmakeFaildPendingOrder() {
		setStaticData();
		setAuthenticateData();
		setSalseOrderData();
		setorderCred();
		SubSalesOrder subSaleOrder = new SubSalesOrder();

		LocalDateTime currentTime = LocalDateTime.now();

		LocalDateTime previousDay = currentTime.minus(1, ChronoUnit.DAYS);
		Timestamp timestamp = Timestamp.valueOf(previousDay);
		subSaleOrder.setFirstNotificationAt(timestamp);
		subSaleOrder.setSecondNotificationAt(timestamp);
		subSaleOrder.setPaymentId("1");
		salesOrder.setCustomerId(1);
		salesOrder.setCustomerIsGuest(0);
		salesOrder.setSubSalesOrder(subSaleOrder);
		salesOrder.setCreatedAt(new Timestamp(10));
		salesOrder.setStatus(OrderConstants.PENDING_PAYMENT_ORDER_STATUS);
		SalesOrderAddress set = new SalesOrderAddress();
		set.setTelephone("+918794378763");
		set.setAddressType(Constants.QUOTE_ADDRESS_TYPE_SHIPPING);
		Set<SalesOrderAddress> salesOrderAddress = new HashSet<>();
		salesOrderAddress.add(set);

		salesOrder.setSalesOrderAddress(salesOrderAddress);
		subSaleOrder.setSalesOrder(salesOrder);
		when(salesOrderGridRepository.findByEntityId(any())).thenReturn(new SalesOrderGrid());
		when(salesOrderRepository.findPendingPaymentOrdersTomakeFailed(anyInt())).thenReturn(Arrays.asList(salesOrder));
		InventoryMetaData meta = new InventoryMetaData();
		meta.setStatus(true);
		InventoryBlockResponse body = new InventoryBlockResponse();
		body.setMeta(meta);
		body.setResponse("response");
		ResponseEntity<InventoryBlockResponse> response = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(InventoryBlockResponse.class))).thenReturn(response);

		orderOutboundController.makeFaildPendingOrder("token");
		verify(salesOrderRepository, atLeast(1)).findPendingPaymentOrdersTomakeFailed(anyInt());
	}

	@Test
	void testGetMobileReturnList() {
		setStaticData();
		setAuthenticateData();
		setSalseOrderData();
		setCustomerOrderresponseData();
		setdisabledSer(false);
		setorderCred();
		setwhatapporderConfi();

		MobileOrderListRequest req = new MobileOrderListRequest();
		req.setPhoneNo("+918762156784");
		when(salesOrderAddressRepository.findOrderReturnsByCustomer(anyInt())).thenReturn(Arrays.asList("1"));
		when(salesOrderAddressRepository.findOrderReturnsByCustomerAndStatus(anyInt(), any()))
				.thenReturn(Arrays.asList("1"));

		ReferralOrderListResponse body = new ReferralOrderListResponse();
		body.setCode(200);
		body.setStatus(true);
		ResponseEntity<ReferralOrderListResponse> response = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(ReferralOrderListResponse.class))).thenReturn(response);

		GenericApiResponse<MobileOrderListResponse> respo = orderOutboundController.mobileReturnList(req, "token");
		assertTrue(respo.getStatus());
		assertEquals(respo.getStatusCode(), "200");
	}

	@Test
	void testunPickedReturns() {
		setStaticData();
		setAuthenticateData();
		setSalseOrderData();
		setCustomerOrderresponseData();
		setdisabledSer(false);
		setorderCred();
		setwhatapporderConfi();

		MobileOrderListRequest req = new MobileOrderListRequest();
		req.setPhoneNo("+918762156784");
		when(salesOrderAddressRepository.findOrderReturnsByCustomer(anyInt())).thenReturn(Arrays.asList("1"));
		when(salesOrderAddressRepository.findOrderReturnsByCustomerAndStatus(anyInt(), any()))
				.thenReturn(Arrays.asList("1"));

		ReferralOrderListResponse body = new ReferralOrderListResponse();
		body.setCode(200);
		body.setStatus(true);
		ResponseEntity<ReferralOrderListResponse> response = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(ReferralOrderListResponse.class))).thenReturn(response);

		GenericApiResponse<MobileOrderListResponse> respo = orderOutboundController.unPickedReturns(req, "token");
		assertTrue(respo.getStatus());
		assertEquals(respo.getStatusCode(), "200");
	}

	@Test
	void testmobileReturnDetails() {
		setStaticData();
		setAuthenticateData();
		setSalseOrderData();
		setCustomerOrderresponseData();
		setdisabledSer(false);
		setorderCred();
		setwhatapporderConfi();

		MobileOrderDetailRequest req = new MobileOrderDetailRequest();
		req.setId("1");

		AmastyRmaRequest amy = new AmastyRmaRequest();
		when(amastyRmaRequestRepository.findByOrderOrRmaOrAwb(any())).thenReturn(Arrays.asList(amy));
		when(salesOrderAddressRepository.findOrderReturnsByCustomerAndStatus(anyInt(), any()))
				.thenReturn(Arrays.asList("1"));

		ReferralOrderListResponse body = new ReferralOrderListResponse();
		body.setCode(200);
		body.setStatus(true);
		ResponseEntity<ReferralOrderListResponse> response = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(ReferralOrderListResponse.class))).thenReturn(response);

		GenericApiResponse<MobileReturnDetailResponse> respo = orderOutboundController.mobileReturnDetails(req,
				"token");
		assertTrue(respo.getStatus());
		assertEquals(respo.getStatusCode(), "200");
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
		GetOrderConsulValues val = new GetOrderConsulValues();
		val.setOrderDetails(keydetail);
		val.setExternalAuthEnable(false);
		val.setInternalAuthEnable(true);
//		org.styli.services.order.utility.Constants.setOrderCredentials(new Gson().toJson(val).toString());
		ReflectionTestUtils.setField(constants, "orderCredentials", val);

	}

	void setwhatapporderConfi() {
		WhatsappOrderConfig keydetail = new WhatsappOrderConfig();
		OmsBaseConfigs val = new OmsBaseConfigs();
		val.setWhatsappOrderConfig(keydetail);

//		org.styli.services.order.utility.Constants.setOrderCredentials(new Gson().toJson(val).toString());
		ReflectionTestUtils.setField(constants, "omsBaseConfigs", val);

	}

	void setdisabledSer(boolean flag) {
		DisabledServices val = new DisabledServices(flag, flag, flag);
		ReflectionTestUtils.setField(constants, "disabledServices", val);

	}

	void setCustomerOrderresponseData() {
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
		salesOrder.setStoreId(1);
		salesOrder.setStatus("packed");
		SalesOrderItem items = new SalesOrderItem();
		items.setSku("01");
		items.setItemId(1);
		items.setProductType("jcsdc");
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
		ReflectionTestUtils.setField(constants, "IS_JWT_TOKEN_ENABLE", true);
		ReflectionTestUtils.setField(orderOutboundController, "configService", configService);
		ReflectionTestUtils.setField(orderOutboundController, "salesOrderService", salesOrderService);
		ReflectionTestUtils.setField(orderOutboundController, "salesOrderServiceV2", salesOrderServiceV2);
		ReflectionTestUtils.setField(orderOutboundController, "whatsappBotService", whatsappBotService);
		ReflectionTestUtils.setField(whatsappBotService, "configService", configService);
		// ReflectionTestUtils.setField(orderOutboundController,
		// "salesOrderAddressRepository", salesOrderAddressRepository);
		ReflectionTestUtils.setField(salesOrderService, "mulinHelper", mulinHelper);
		ReflectionTestUtils.setField(salesOrderService, "orderHelper", orderHelper);
		ReflectionTestUtils.setField(salesOrderService, "orderEntityConverter", orderEntityConverter);
		ReflectionTestUtils.setField(salesOrderService, "getOrderById", getOrderById);
		ReflectionTestUtils.setField(salesOrderService, "getOrderList", getOrderList);
		ReflectionTestUtils.setField(salesOrderService, "configService", configService);
		ReflectionTestUtils.setField(salesOrderService, "getFailedOrderList", getFailedOrderList);
		ReflectionTestUtils.setField(salesOrderService, "eASServiceImpl", eASServiceImpl);

		ReflectionTestUtils.setField(whatsappBotService, "orderHelper", orderHelper);
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
