package org.styli.services.order.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.lang.reflect.Type;
import java.math.BigDecimal;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.db.product.exception.NotFoundException;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.helper.TamaraHelper;
import org.styli.services.order.model.rma.AmastyStoreCreditHistory;
import org.styli.services.order.model.sales.ProxyOrder;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderGrid;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SalesOrderPayment;
import org.styli.services.order.model.sales.SalesShipmentTrack;
import org.styli.services.order.model.sales.StatusChangeHistory;
import org.styli.services.order.pojo.GetOrderConsulValues;
import org.styli.services.order.pojo.PaymentDTO;
import org.styli.services.order.pojo.TabbyDetails;
import org.styli.services.order.pojo.order.BNPLOrderUpdateResponse;
import org.styli.services.order.pojo.request.Order.CreateOrderRequestV2;
import org.styli.services.order.pojo.request.Order.OmsOrderListRequest;
import org.styli.services.order.pojo.request.Order.OrderStoreCreditRequest;
import org.styli.services.order.pojo.response.Order.CreateOrderResponseDTO;
import org.styli.services.order.pojo.tamara.TamaraCaptures;
import org.styli.services.order.pojo.tamara.TamaraPayment;
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
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@SpringBootTest(classes = { TamaraPaymentServiceImplTest.class })
public class TamaraPaymentServiceImplTest extends AbstractTestNGSpringContextTests {
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
	TamaraPaymentServiceImpl tamaraPaymentService;
	@InjectMocks
	TamaraHelper tamaraHelper;
	@InjectMocks
	PaymentUtility paymentUtility;
	@InjectMocks
	OrderHelper orderHelper;
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
		tamaraPaymentService.updatePaymentOnReplica("1", "");
	}

	@Test
	public void processPaymentSuccessTest() throws NotFoundException {
		setStaticData();
		setSalseOrderData();
		setMockData();
		PaymentDTO dto = new PaymentDTO();
		dto.setId("1");
		BNPLOrderUpdateResponse respo = tamaraPaymentService.processPaymentSuccess(dto, true);
		assertNotNull(respo);
		assertEquals(respo.isPaymentSuccess(), true);
	}

	@Test
	public void processPaymentSuccessFailTest() throws NotFoundException {
		setStaticData();
		setSalseOrderData();
		setMockData();
		when(salesOrderService.findSalesOrderByPaymentId(anyString())).thenReturn(null);
		when(salesOrderService.findSalesOrderByIncrementId(any())).thenReturn(salesOrder);
		PaymentDTO dto = new PaymentDTO();
		dto.setId("1");
		BNPLOrderUpdateResponse respo = tamaraPaymentService.processPaymentSuccess(dto, true);
		assertNotNull(respo);
		assertEquals(respo.isPaymentSuccess(), false);
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
		BNPLOrderUpdateResponse respo = tamaraPaymentService.processPaymentSuccess(dto, false);
		assertNotNull(respo);
		assertEquals(respo.isPaymentSuccess(), false);
	}

	@Test
	public void updatePaymentStatusTest() throws NotFoundException {
		setStaticData();
		setSalseOrderData();
		setMockData();
		BNPLOrderUpdateResponse respo = tamaraPaymentService.updatePaymentStatus(order, "");
		assertNotNull(respo);
		assertEquals(respo.isPaymentSuccess(), true);
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
		Boolean value = tamaraPaymentService.capturePayment(salesOrder);
		assertEquals(value, true);
	}

	private void setStaticData() {
		// TODO Auto-generated method stub
		ReflectionTestUtils.setField(tamaraPaymentService, "tamaraHelper", tamaraHelper);
		ReflectionTestUtils.setField(tamaraHelper, "tamaraSecretKey", "secretkey");
		ReflectionTestUtils.setField(tamaraPaymentService, "paymentUtility", paymentUtility);
		ReflectionTestUtils.setField(tamaraHelper, "paymnetUtility", paymentUtility);

		ReflectionTestUtils.setField(tamaraPaymentService, "orderHelper", orderHelper);
		ReflectionTestUtils.setField(tamaraPaymentService, "tamaraHelper", tamaraHelper);
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
		ResponseEntity<String> iresponse = new ResponseEntity<>(HttpStatus.OK).ok("{order_id=1,status=approved}");
		ResponseEntity<String> response = new ResponseEntity<>(HttpStatus.OK).ok(g.toJson(salesOrder));
		TamaraPayment tb = new TamaraPayment();
		tb.setCaptureId("1");
		ResponseEntity<TamaraPayment> tresponse = new ResponseEntity<>(HttpStatus.OK).ok(tb);
		TamaraCaptures tc = new TamaraCaptures();
		tc.setCaptureId("1");
		ResponseEntity<TamaraCaptures> tcresponse = new ResponseEntity<>(HttpStatus.OK).ok(tc);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.GET),
				Mockito.any(HttpEntity.class), Mockito.eq(String.class))).thenReturn(iresponse);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(String.class))).thenReturn(response);
		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(TamaraPayment.class))).thenReturn(tresponse);
		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.PUT),
				Mockito.any(HttpEntity.class), Mockito.eq(TamaraPayment.class))).thenReturn(tresponse);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(TamaraCaptures.class))).thenReturn(tcresponse);

		when(salesOrderGridRepository.findByEntityId(anyInt())).thenReturn(new SalesOrderGrid());
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
