package org.styli.services.order.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
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
import java.util.Set;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.db.product.exception.NotFoundException;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.helper.OrderHelperV2;
import org.styli.services.order.helper.PaymentDtfHelper;
import org.styli.services.order.helper.TabbyHelper;
import org.styli.services.order.model.rma.AmastyStoreCreditHistory;
import org.styli.services.order.model.sales.ProxyOrder;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderAddress;
import org.styli.services.order.model.sales.SalesOrderGrid;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SalesOrderPayment;
import org.styli.services.order.model.sales.SalesShipmentTrack;
import org.styli.services.order.model.sales.StatusChangeHistory;
import org.styli.services.order.model.sales.SubSalesOrder;
import org.styli.services.order.pojo.GetOrderConsulValues;
import org.styli.services.order.pojo.PaymentDTO;
import org.styli.services.order.pojo.TabbyDetails;
import org.styli.services.order.pojo.order.BNPLOrderUpdateResponse;
import org.styli.services.order.pojo.request.Order.CreateOrderRequestV2;
import org.styli.services.order.pojo.request.Order.OmsOrderListRequest;
import org.styli.services.order.pojo.request.Order.OrderStoreCreditRequest;
import org.styli.services.order.pojo.response.AddressMapperCity;
import org.styli.services.order.pojo.response.AddressMapperResponse;
import org.styli.services.order.pojo.response.InventoryBlockResponse;
import org.styli.services.order.pojo.response.InventoryMetaData;
import org.styli.services.order.pojo.response.Order.CreateOrderResponseDTO;
import org.styli.services.order.pojo.tabby.TabbyPayment;
import org.styli.services.order.pojo.tamara.TamaraCaptures;
import org.styli.services.order.repository.Rma.AmastyRmaReasonRepository;
import org.styli.services.order.repository.Rma.AmastyRmaRequestRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditHistoryRepository;
import org.styli.services.order.repository.SalesOrder.ProxyOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderPaymentRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusHistoryRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusLabelRepository;
import org.styli.services.order.repository.SalesOrder.StatusChaneHistoryRepository;
import org.styli.services.order.service.SalesOrderService;
import org.styli.services.order.service.SalesOrderServiceV2;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.PaymentUtility;
import org.styli.services.order.utility.consulValues.ConsulValues;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class TabbyPaymentServiceImplTest {

	@Mock
	AmastyStoreCreditHistoryRepository amastyStoreCreditHistoryRepository;
	@Mock
	SalesCreditmemoRepository salesCreditmemoRepository;
	@Mock
	SalesOrderStatusLabelRepository salesOrderStatusLabelRepository;
	@Mock
	AmastyRmaReasonRepository amastyRmaReasonRepository;
	@Mock
	AmastyRmaRequestRepository amastyRmaRequestRepository;
	@InjectMocks
	TabbyPaymentServiceImpl tabbyPaymentService;
	@InjectMocks
	TabbyHelper tabbyHelper;
	@InjectMocks
	PaymentDtfHelper paymentDtfHelper;

	@InjectMocks
	PaymentUtility paymentUtility;
	@InjectMocks
	OrderHelper orderHelper;
	@InjectMocks
	OrderHelperV2 orderHelperV2;
	@InjectMocks
	TabbyDetails tabbyDetails;
	@Mock
	ProxyOrderRepository proxyOrderRepository;
	@Mock
	SalesOrderService salesOrderService;
	@Mock
	SalesOrderRepository salesOrderRepository;
	@Mock
	SalesOrderStatusHistoryRepository salesOrderStatusHistoryRepository;
	@Mock
	SalesOrderGridRepository salesOrderGridRepository;
	@Mock
	StatusChaneHistoryRepository statusChaneHistoryRepository;
	@Mock
	private RestTemplate restTemplate;
	@Mock
	SalesOrderServiceV2 salesOrderServiceV2;
	@Mock
	SalesOrderPaymentRepository paymentRepository;

	org.styli.services.order.model.Customer.CustomerEntity customerEntity;
	Map<String, String> requestHeader;
	private List<Stores> storeList;
	SalesOrder salesOrder;
	OmsOrderListRequest request;
	@InjectMocks
	Constants constants;
	private ProxyOrder order;

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
	public void initiateReplicaTest() throws NotFoundException {
		setStaticData();
		setSalseOrderData();
		setMockData();
		when(salesOrderRepository.findByIncrementId(any())).thenReturn(salesOrder);
		tabbyPaymentService.updatePaymentOnReplica("1", "");

	}

	@Test
	public void processPaymentSuccessproxyTest() throws NotFoundException {
		setStaticData();
		setSalseOrderData();
		setMockData();
		salesOrder.setStatus("pending_payment");
		SubSalesOrder sso = new SubSalesOrder();
		sso.setRetryPayment(1);
		salesOrder.setSubSalesOrder(sso);
		SalesOrderAddress set = new SalesOrderAddress();
		set.setTelephone("+918794378763");
		set.setAddressType(Constants.QUOTE_ADDRESS_TYPE_SHIPPING);
		Set<SalesOrderAddress> salesOrderAddress = new HashSet<>();
		salesOrderAddress.add(set);

		salesOrder.setSalesOrderAddress(salesOrderAddress);
		PaymentDTO dto = new PaymentDTO();
		dto.setId("1");
		AddressMapperResponse amr = new AddressMapperResponse();
		amr.setStatusCode("200");
		AddressMapperCity city = new AddressMapperCity();

		city.setEstimatedDate("2023-07-04 00:00:00");
		amr.setResponse(city);
		ResponseEntity<AddressMapperResponse> response = new ResponseEntity<>(HttpStatus.OK).ok(amr);
		Mockito.when(restTemplate.exchange(Mockito.any(URI.class), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(AddressMapperResponse.class))).thenReturn(response);

		BNPLOrderUpdateResponse respo = tabbyPaymentService.processPaymentSuccess(dto, salesOrder, true, false);
		assertNotNull(respo);
		assertEquals(respo.isPaymentSuccess(), true);

	}

	@Test
	public void getPaymentUpdatesTest() throws NotFoundException {
		setStaticData();
		setSalseOrderData();
		setMockData();

		when(salesOrderService.findSalesOrdeForTabbyPayment()).thenReturn(Arrays.asList(salesOrder));
		List<BNPLOrderUpdateResponse> lst = tabbyPaymentService.getPaymentUpdates(Constants.paymentMethodDeviceId);
		assertNotNull(lst);
	}

	@Test
	public void onPaymentSuccessButStausFailedTest() throws NotFoundException {
		setStaticData();
		setSalseOrderData();
		setMockData();
		PaymentDTO dto = new PaymentDTO();
		dto.setId("1");
		when(salesOrderService.findSalesOrdeForTabbyPayment()).thenReturn(Arrays.asList(salesOrder));
		tabbyPaymentService.onPaymentSuccessButStausFailed(dto, order);
	}

	@Test
	public void processPaymentFailureTest() throws NotFoundException {
		setStaticData();
		setSalseOrderData();
		setMockData();
		salesOrder.setStatus("pending_payment");

		ConsulValues orderConsulValues = new ConsulValues();
		orderConsulValues.setPaymentFailedThresholdVersion("3.7.0");
		Constants.setOrderConsulValues(new Gson().toJson(orderConsulValues).toString());
		PaymentDTO dto = new PaymentDTO();
		dto.setId("1");
		when(salesOrderService.findSalesOrdeForTabbyPayment()).thenReturn(Arrays.asList(salesOrder));
		tabbyPaymentService.processPaymentFailure(dto, salesOrder, false, "");
	}

	@Test
	public void capturePaymentTest() throws NotFoundException {
		setStaticData();
		setSalseOrderData();
		setMockData();

		OrderStoreCreditRequest req = new OrderStoreCreditRequest();
		req.setStoreId(1);
		req.setCustomerId(1);
		AmastyStoreCreditHistory ash = new AmastyStoreCreditHistory();
		LocalDateTime currentTime = LocalDateTime.now();
		ash.setCreatedAt(Timestamp.valueOf(currentTime));
		ash.setAction(1);
		ash.setActionData("1");
		ash.setMessage("msg");
		List<AmastyStoreCreditHistory> creditHistories = Arrays.asList(ash);
		when(amastyStoreCreditHistoryRepository.findByCustomerIdOrderByHistoryIdDesc(anyInt()))
				.thenReturn(creditHistories);
		SalesOrderPayment sop = new SalesOrderPayment();
		when(paymentRepository.getOne(any())).thenReturn(sop);
		Boolean value = tabbyPaymentService.capturePayment(salesOrder);
		assertEquals(value, true);
	}

	@Test
	public void updatePaymentStatusTest() throws NotFoundException {
		setStaticData();
		setSalseOrderData();
		setMockData();
		BNPLOrderUpdateResponse respo = tabbyPaymentService.updatePaymentStatus(order, "");
		assertNotNull(respo);
		assertEquals(respo.isPaymentSuccess(), true);
	}

	@Test
	public void updatePaymentStatusfailTest() throws NotFoundException {
		setStaticData();
		setSalseOrderData();
		setMockData();
		salesOrder.setStatus("payment_failed");
		when(proxyOrderRepository.findByPaymentId(anyString())).thenReturn(null);
		BNPLOrderUpdateResponse respo = tabbyPaymentService.updatePaymentStatus(order, "");
		assertEquals(respo, null);
	}

	@Test
	public void updatePaymentStatus2Test() throws NotFoundException {
		setStaticData();
		setSalseOrderData();
		setMockData();
		salesOrder.setStatus("payment_failed");
		when(proxyOrderRepository.findByPaymentId(anyString())).thenReturn(null);
		ResponseEntity<String> iresponse = new ResponseEntity<>(HttpStatus.OK).ok("{id=1,status=REJECTED}");
		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.GET),
				Mockito.any(HttpEntity.class), Mockito.eq(String.class))).thenReturn(iresponse);
		BNPLOrderUpdateResponse respo = tabbyPaymentService.updatePaymentStatus(order, "");
		assertEquals(respo, null);
	}

	@Test
	public void processPaymentSuccessTest() throws NotFoundException {
		setStaticData();
		setSalseOrderData();
		setMockData();
		PaymentDTO dto = new PaymentDTO();
		dto.setId("1");
		BNPLOrderUpdateResponse respo = tabbyPaymentService.processPaymentSuccess(dto, true, order);
		assertNotNull(respo);
		assertEquals(respo.isPaymentSuccess(), true);
	}

	@Test
	public void processPaymentSuccessFailTest() throws NotFoundException {
		setStaticData();
		setSalseOrderData();
		setMockData();
		salesOrder.setStatus("pending_pafdvyment");
		PaymentDTO dto = new PaymentDTO();
		dto.setId("1");
		BNPLOrderUpdateResponse respo = tabbyPaymentService.processPaymentSuccess(dto, true, order);
		assertNotNull(respo);
		assertEquals(respo.isSuccess(), false);
	}

	@Test
	public void processPaymentSuccessTest2() throws NotFoundException {
		setStaticData();
		setSalseOrderData();
		setMockData();
		GetOrderConsulValues getOrderConsulValues = new GetOrderConsulValues();

		TabbyDetails tabbyDetails = new TabbyDetails();
		tabbyDetails.setBackendCreateOrderDurationMins(1);
		tabbyDetails.setEnableBackendCreateOrder(true);
		getOrderConsulValues.setTabby(tabbyDetails);
		ReflectionTestUtils.setField(constants, "orderCredentials", getOrderConsulValues);

		when(salesOrderService.findSalesOrderByPaymentId(anyString())).thenReturn(null);
		when(salesOrderService.findSalesOrderByIncrementId(any())).thenReturn(null);
		PaymentDTO dto = new PaymentDTO();
		dto.setId("1");
		BNPLOrderUpdateResponse respo = tabbyPaymentService.processPaymentSuccess(dto, false, order);
		assertEquals(respo, null);
	}

	private void setStaticData() {
		// TODO Auto-generated method stub
		ReflectionTestUtils.setField(tabbyPaymentService, "tabbyHelper", tabbyHelper);
		ReflectionTestUtils.setField(tabbyHelper, "tabbySecretKey", "secretkey");
		ReflectionTestUtils.setField(tabbyPaymentService, "paymentUtility", paymentUtility);
		ReflectionTestUtils.setField(tabbyHelper, "paymentUtility", paymentUtility);
		ReflectionTestUtils.setField(tabbyPaymentService, "paymentDtfHelper", paymentDtfHelper);

		ReflectionTestUtils.setField(tabbyPaymentService, "orderHelper", orderHelper);
		ReflectionTestUtils.setField(tabbyPaymentService, "orderHelperV2", orderHelperV2);
		ReflectionTestUtils.setField(tabbyPaymentService, "tabbyHelper", tabbyHelper);
	}

	void setMockData() throws NotFoundException {
		order = new ProxyOrder();
		order.setPaymentMethod("tamara_installments_3");
		LocalDateTime currentTime = LocalDateTime.now();
		LocalDateTime previousDay = currentTime.minus(10, ChronoUnit.MINUTES);
		Timestamp timestamp = Timestamp.valueOf(previousDay);
		order.setCreatedAt(new Date(timestamp.getTime()));
		order.setStatus("approved");

		Gson g = new Gson();
		order.setSalesOrder(g.toJson(salesOrder));

		CreateOrderRequestV2 v2 = new CreateOrderRequestV2();
		order.setOrderRequest(g.toJson(v2));
		ResponseEntity<String> iresponse = new ResponseEntity<>(HttpStatus.OK).ok("{id=1,status=AUTHORIZED}");
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

		InventoryMetaData meta = new InventoryMetaData();
		meta.setStatus(true);
		InventoryBlockResponse body = new InventoryBlockResponse();
		body.setMeta(meta);
		body.setResponse("response");
		ResponseEntity<InventoryBlockResponse> inresponse = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(InventoryBlockResponse.class))).thenReturn(inresponse);

		when(salesOrderGridRepository.findByEntityId(anyInt())).thenReturn(new SalesOrderGrid());
		when(salesOrderRepository.findByEntityId(anyInt())).thenReturn(salesOrder);
		when(proxyOrderRepository.findByPaymentId(anyString())).thenReturn(order);
		when(salesOrderService.findSalesOrderByPaymentId(anyString())).thenReturn(salesOrder);
		when(salesOrderService.findSalesOrderByIncrementId(anyString())).thenReturn(salesOrder);
		when(statusChaneHistoryRepository.findByOrderId(anyString())).thenReturn(new StatusChangeHistory());
		CreateOrderResponseDTO dto = new CreateOrderResponseDTO();
		dto.setStatusMsg("msg");
		dto.setStatusCode("200");
		when(salesOrderServiceV2.convertQuoteToOrderV2(any(), any(), any(), any(), any(), any(), any(), any(), any()))
				.thenReturn(dto);
	}

	private void setSalseOrderData() {
		salesOrder = new SalesOrder();
		salesOrder.setCustomerId(1);
		salesOrder.setAppVersion("2.0.1");
		salesOrder.setIncrementId("1");
		salesOrder.setCustomerIsGuest(2);
		salesOrder.setEntityId(1);
		salesOrder.setStoreId(1);
		salesOrder.setStatus("pending_payment");
		salesOrder.setGrandTotal(new BigDecimal(10));
		salesOrder.setWmsStatus(1);
		salesOrder.setAmstorecreditAmount(new BigDecimal(10));
		salesOrder.setStoreToBaseRate(new BigDecimal(1));
		salesOrder.setAmstorecreditAmount(new BigDecimal(1));
//		LocalDateTime currentTime = LocalDateTime.now();
//		salesOrder.setCreatedAt(Timestamp.valueOf(currentTime));
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
