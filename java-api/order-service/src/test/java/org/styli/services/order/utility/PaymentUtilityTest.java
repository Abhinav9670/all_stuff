package org.styli.services.order.utility;

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
import org.styli.services.order.helper.CashfreeHelper;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.helper.OrderHelperV2;
import org.styli.services.order.helper.PaymentDtfHelper;
import org.styli.services.order.helper.TabbyHelper;
import org.styli.services.order.helper.TamaraHelper;
import org.styli.services.order.model.rma.AmastyRmaRequest;
import org.styli.services.order.model.rma.AmastyRmaRequestItem;
import org.styli.services.order.model.sales.ProxyOrder;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderGrid;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SalesOrderPayment;
import org.styli.services.order.model.sales.SalesShipmentTrack;
import org.styli.services.order.model.sales.StatusChangeHistory;
import org.styli.services.order.pojo.CashfreeDetails;
import org.styli.services.order.pojo.GetOrderConsulValues;
import org.styli.services.order.pojo.RefundPaymentRespone;
import org.styli.services.order.pojo.TabbyDetails;
import org.styli.services.order.pojo.order.PaymentReturnAdditioanls;
import org.styli.services.order.pojo.request.Order.CreateOrderRequestV2;
import org.styli.services.order.pojo.request.Order.OmsOrderListRequest;
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
import org.styli.services.order.service.impl.TabbyPaymentServiceImpl;
import org.styli.services.order.service.impl.TamaraPaymentServiceImpl;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@SpringBootTest(classes = { PaymentUtilityTest.class })
public class PaymentUtilityTest extends AbstractTestNGSpringContextTests {
	@InjectMocks
	PaymentUtility paymentUtility;
	@InjectMocks
	TabbyPaymentServiceImpl tabbyPaymentService;
	@InjectMocks
	TamaraPaymentServiceImpl tamaraPaymentService;
	@InjectMocks
	TamaraHelper tamaraHelper;

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
	TabbyHelper tabbyHelper;
	@InjectMocks
	PaymentDtfHelper paymentDtfHelper;

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
	SalesOrderServiceV2 salesOrderServiceV2;
	@Mock
	SalesOrderPaymentRepository paymentRepository;

	@InjectMocks
	Constants constants;
	private ProxyOrder order;

	@Mock
	private RestTemplate restTemplate;

	org.styli.services.order.model.Customer.CustomerEntity customerEntity;
	Map<String, String> requestHeader;
	private List<Stores> storeList;
	SalesOrder salesOrder;
	OmsOrderListRequest request;
	@InjectMocks
	private CashfreeHelper cashfreeHelper;

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
	public void initiateRefundTest() throws NotFoundException {
		setStaticData();
		setSalseOrderData();
		setMockData();
		setconsul();
		PaymentReturnAdditioanls add = new PaymentReturnAdditioanls();
		add.setReturnAmount("100");
		add.setPaymentMethod("cashfree");
		AmastyRmaRequest amastyRmaRequest = new AmastyRmaRequest();
		amastyRmaRequest.setCustomerId(1);
		amastyRmaRequest.setStoreId(1);
		amastyRmaRequest.setOrderId(1);
		amastyRmaRequest.setRequestId(1);
		amastyRmaRequest.setStatus(1);
		amastyRmaRequest.setRmaIncId("1");
		AmastyRmaRequestItem amastyRmaRequestItem = new AmastyRmaRequestItem();
		amastyRmaRequestItem.setRequestId(1);
		amastyRmaRequestItem.setRequestItemId(1);
		amastyRmaRequestItem.setOrderItemId(1);
		Set<AmastyRmaRequestItem> amastyRmaRequestItems = new HashSet<>();
		amastyRmaRequestItems.add(amastyRmaRequestItem);
		amastyRmaRequest.setAmastyRmaRequestItems(amastyRmaRequestItems);
		add.setRmaRequest(amastyRmaRequest);
		ResponseEntity<String> iresponse = new ResponseEntity<>(HttpStatus.OK).ok("sucess");

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.GET),
				Mockito.any(HttpEntity.class), Mockito.eq(String.class))).thenReturn(iresponse);

		RefundPaymentRespone respo = paymentUtility.initiateRefund(salesOrder, add);
		assertNotNull(respo);
		assertEquals(respo.isStatus(), true);
	}

	private void setStaticData() {
		// TODO Auto-generated method stub
		ReflectionTestUtils.setField(paymentUtility, "tabbyPaymentService", tabbyPaymentService);
		ReflectionTestUtils.setField(paymentUtility, "cashfreeHelper", cashfreeHelper);

		ReflectionTestUtils.setField(cashfreeHelper, "cashgramPublicKey", ".\\resources\\cashfree.txt");
		ReflectionTestUtils.setField(tamaraPaymentService, "tamaraHelper", tamaraHelper);
		ReflectionTestUtils.setField(tamaraHelper, "tamaraSecretKey", "secretkey");
		ReflectionTestUtils.setField(tabbyPaymentService, "tabbyHelper", tabbyHelper);
		ReflectionTestUtils.setField(tabbyHelper, "tabbySecretKey", "secretkey");
		ReflectionTestUtils.setField(tabbyPaymentService, "paymentUtility", paymentUtility);
		ReflectionTestUtils.setField(tabbyHelper, "paymentUtility", paymentUtility);
		ReflectionTestUtils.setField(tabbyPaymentService, "paymentDtfHelper", paymentDtfHelper);

		ReflectionTestUtils.setField(tabbyPaymentService, "orderHelper", orderHelper);
		ReflectionTestUtils.setField(tabbyPaymentService, "orderHelperV2", orderHelperV2);
		ReflectionTestUtils.setField(tabbyPaymentService, "tabbyHelper", tabbyHelper);
	}

	void setconsul() {
		GetOrderConsulValues getOrderConsulValues = new GetOrderConsulValues();

		CashfreeDetails details = new CashfreeDetails();
		details.setCashgramBaseUrl("url");
		details.setCashGramAppId("1");
		getOrderConsulValues.setCashfree(details);
		ReflectionTestUtils.setField(constants, "orderCredentials", getOrderConsulValues);

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
