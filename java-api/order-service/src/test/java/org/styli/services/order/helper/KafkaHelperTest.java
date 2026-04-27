package org.styli.services.order.helper;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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
import org.styli.services.order.controller.AutoRefundController;
import org.styli.services.order.controller.PaymentController;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.jwt.security.jwtsecurity.security.JwtValidator;
import org.styli.services.order.model.SalesOrder.SalesCreditmemoGrid;
import org.styli.services.order.model.sales.ProxyOrder;
import org.styli.services.order.model.sales.SalesInvoiceGrid;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderAddress;
import org.styli.services.order.model.sales.SalesOrderGrid;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SalesOrderPayment;
import org.styli.services.order.model.sales.SalesShipmentTrack;
import org.styli.services.order.model.sales.StatusChangeHistory;
import org.styli.services.order.pojo.CashfreeDetails;
import org.styli.services.order.pojo.GetOrderConsulValues;
import org.styli.services.order.pojo.OrderKeyDetails;
import org.styli.services.order.pojo.PendingOrderNotfcnDetails;
import org.styli.services.order.pojo.TabbyDetails;
import org.styli.services.order.pojo.kafka.DeleteCustomerKafka;
import org.styli.services.order.pojo.request.Order.CreateOrderRequestV2;
import org.styli.services.order.pojo.response.InventoryBlockResponse;
import org.styli.services.order.pojo.response.InventoryMetaData;
import org.styli.services.order.pojo.response.Order.CreateOrderResponseDTO;
import org.styli.services.order.pojo.tabby.TabbyPayment;
import org.styli.services.order.pojo.tamara.TamaraCaptures;
import org.styli.services.order.repository.Rma.AmastyRmaReasonRepository;
import org.styli.services.order.repository.Rma.AmastyRmaRequestRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditHistoryRepository;
import org.styli.services.order.repository.SalesOrder.ProxyOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoRepository;
import org.styli.services.order.repository.SalesOrder.SalesInvoiceGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderAddressRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderPaymentRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusHistoryRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusLabelRepository;
import org.styli.services.order.repository.SalesOrder.StatusChaneHistoryRepository;
import org.styli.services.order.repository.SalesOrder.VaultPaymentTokenRepository;
import org.styli.services.order.service.SalesOrderService;
import org.styli.services.order.service.SalesOrderServiceV2;
import org.styli.services.order.service.impl.CashfreePaymentServiceImpl;
import org.styli.services.order.service.impl.ConfigServiceImpl;
import org.styli.services.order.service.impl.TabbyPaymentServiceImpl;
import org.styli.services.order.service.impl.TamaraPaymentServiceImpl;
import org.styli.services.order.utility.PaymentUtility;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@SpringBootTest(classes = { KafkaHelperTest.class })
public class KafkaHelperTest extends AbstractTestNGSpringContextTests {

	@InjectMocks
	PaymentUtility paymentUtility;
	@InjectMocks
	TabbyPaymentServiceImpl tabbyPaymentService;
	@InjectMocks
	TamaraPaymentServiceImpl tamaraPaymentService;
	@InjectMocks
	TamaraHelper tamaraHelper;

	@Mock
	private RestTemplate restTemplate;

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
	SalesInvoiceGridRepository salesInvoiceGridRepository;
	@Mock
	SalesOrderAddressRepository salesOrderAddressRepository;
	@Mock
	VaultPaymentTokenRepository vaultPaymentTokenRepository;
	@Mock
	SalesCreditmemoGridRepository salesCreditmemoGridRepository;
	@Mock
	StatusChaneHistoryRepository statusChaneHistoryRepository;
	@Mock
	SalesOrderServiceV2 salesOrderServiceV2;
	@Mock
	SalesOrderPaymentRepository paymentRepository;
	@InjectMocks
	org.styli.services.order.utility.Constants constants;
	@InjectMocks
	PaymentController paymentController;

	@InjectMocks
	private ConfigServiceImpl configService;

	@InjectMocks
	private AutoRefundController autoRefundController;

	@Mock
	JwtValidator validator;
	private ProxyOrder order;
	Map<String, String> requestHeader;
	private List<Stores> storeList;
	SalesOrder salesOrder;
	@InjectMocks
	private CashfreePaymentServiceImpl cfPaymentService;
	@InjectMocks
	private KafkaHelper kafkaHelper;

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

	@Test
	public void processKafkaDeleteCustomerTest() {
		setStaticfields();
		setSalseOrderData();
		setconsul();
		setMockData();
		DeleteCustomerKafka req = new DeleteCustomerKafka();
		req.setCustomerId(1);
		kafkaHelper.processKafkaDeleteCustomer(req);
	}

	void setconsul() {
		GetOrderConsulValues getOrderConsulValues = new GetOrderConsulValues();
		OrderKeyDetails keydetail = new OrderKeyDetails();
		keydetail.setMaximumOrderPedningOrderThreshold(10);
		keydetail.setReferralOrderLastHours(1);
		keydetail.setPendingOrderNotfcnDetails(new PendingOrderNotfcnDetails());
		LocalDateTime currentTime = LocalDateTime.now();
		LocalDateTime previousDay = currentTime.minus(2, ChronoUnit.DAYS);
		Timestamp timestamp = Timestamp.valueOf(previousDay);
		keydetail.setPendingOrderNotificationInMins(timestamp.getNanos());
		keydetail.setCustomerServiceBaseUrl("url");
		CashfreeDetails details = new CashfreeDetails();
		details.setCashgramBaseUrl("url");
		details.setCashGramAppId("1");
		details.setCashFreeBaseUrl("url");
		details.setCashGramSecret("secrete");
		getOrderConsulValues.setOrderDetails(keydetail);
		getOrderConsulValues.setCashfree(details);
		ReflectionTestUtils.setField(constants, "orderCredentials", getOrderConsulValues);

	}

	private void setStaticfields() {
		// TODO Auto-generated method stub
//		ReflectionTestUtils.setField(KafkaHelper, "configService", configService);
//		ReflectionTestUtils.setField(KafkaHelper, "paymentUtility", paymentUtility);
//		ReflectionTestUtils.setField(KafkaHelper, "cfPaymentService", cfPaymentService);
//		ReflectionTestUtils.setField(cfPaymentService,"cashfreeHelper",cashfreeHelper);
//		ReflectionTestUtils.setField(configService, "internalAuthBearerToken", "ffyg,token");
//		ReflectionTestUtils.setField(cfPaymentService, "paymentUtility", paymentUtility);
	}

	void setMockData() {
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

		ResponseEntity<String> iresponse = new ResponseEntity<>(HttpStatus.OK)
				.ok("{order_id=1,order_status=approved,cf_order_id=1,payload=AUTHORIZED}");
		ResponseEntity<String> response = new ResponseEntity<>(HttpStatus.OK).ok(g.toJson(salesOrder));
		ResponseEntity<Object> oresponse = new ResponseEntity<>(HttpStatus.OK).ok(g.toJson(""));
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

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(Object.class))).thenReturn(oresponse);

		InventoryMetaData meta = new InventoryMetaData();
		meta.setStatus(true);
		InventoryBlockResponse body = new InventoryBlockResponse();
		body.setMeta(meta);
		body.setResponse("response");
		ResponseEntity<InventoryBlockResponse> inresponse = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(InventoryBlockResponse.class))).thenReturn(inresponse);
		SalesInvoiceGrid grid = new SalesInvoiceGrid();
		SalesOrderAddress add = new SalesOrderAddress();
		when(salesOrderAddressRepository.findByCustomerId(anyInt())).thenReturn(Arrays.asList(add));
		when(salesInvoiceGridRepository.findByOrderId(anyInt())).thenReturn(grid);
		SalesCreditmemoGrid memogrid = new SalesCreditmemoGrid();
		memogrid.setEntityId(1);
		when(salesCreditmemoGridRepository.findByOrderId(anyInt())).thenReturn(Arrays.asList(memogrid));
		when(salesOrderGridRepository.findByEntityId(anyInt())).thenReturn(new SalesOrderGrid());
		when(salesOrderGridRepository.findByCustomerId(anyInt())).thenReturn(Arrays.asList(new SalesOrderGrid()));
		when(salesOrderRepository.findByCustomerId(anyInt())).thenReturn(Arrays.asList(salesOrder));
		when(proxyOrderRepository.findByPaymentId(anyString())).thenReturn(order);
		when(salesOrderService.findSalesOrderByPaymentId(anyString())).thenReturn(salesOrder);
		when(salesOrderService.findSalesOrderByIncrementId(anyString())).thenReturn(salesOrder);
		when(statusChaneHistoryRepository.findByOrderId(anyString())).thenReturn(new StatusChangeHistory());
		CreateOrderResponseDTO dto = new CreateOrderResponseDTO();
		dto.setStatusMsg("msg");
		dto.setStatusCode("200");

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
