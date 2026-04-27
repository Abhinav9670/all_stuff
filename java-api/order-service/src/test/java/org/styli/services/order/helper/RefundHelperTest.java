package org.styli.services.order.helper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

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

import javax.servlet.http.HttpServletRequest;

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
import org.styli.services.order.model.CoreConfigData;
import org.styli.services.order.model.Customer.CustomerEntity;
import org.styli.services.order.model.Order.cancel.PayfortRefundResponse;
import org.styli.services.order.model.SalesOrder.SalesCreditmemo;
import org.styli.services.order.model.rma.AmastyRmaRequest;
import org.styli.services.order.model.rma.AmastyRmaRequestItem;
import org.styli.services.order.model.rma.AmastyStoreCredit;
import org.styli.services.order.model.rma.AmastyStoreCreditHistory;
import org.styli.services.order.model.rma.sequence.SequenceCreditmemoOne;
import org.styli.services.order.model.sales.ProxyOrder;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderGrid;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SalesOrderPayment;
import org.styli.services.order.model.sales.SalesShipmentTrack;
import org.styli.services.order.model.sales.StatusChangeHistory;
import org.styli.services.order.model.sales.SubSalesOrder;
import org.styli.services.order.pojo.CashfreeDetails;
import org.styli.services.order.pojo.GetOrderConsulValues;
import org.styli.services.order.pojo.InventoryMapping;
import org.styli.services.order.pojo.NavikAddress;
import org.styli.services.order.pojo.NavikDetails;
import org.styli.services.order.pojo.Navikinfos;
import org.styli.services.order.pojo.OrderKeyDetails;
import org.styli.services.order.pojo.PayfortDetails;
import org.styli.services.order.pojo.PendingOrderNotfcnDetails;
import org.styli.services.order.pojo.RefundAmountObject;
import org.styli.services.order.pojo.RefundPaymentRespone;
import org.styli.services.order.pojo.TabbyDetails;
import org.styli.services.order.pojo.TamaraDetails;
import org.styli.services.order.pojo.WmsDetails;
import org.styli.services.order.pojo.cancel.CancelOrderRequest;
import org.styli.services.order.pojo.cancel.MagentoAPIResponse;
import org.styli.services.order.pojo.oms.BankSwiftCode;
import org.styli.services.order.pojo.request.Order.CreateOrderRequestV2;
import org.styli.services.order.pojo.response.InventoryBlockResponse;
import org.styli.services.order.pojo.response.InventoryMetaData;
import org.styli.services.order.pojo.response.Order.CreateOrderResponseDTO;
import org.styli.services.order.pojo.tabby.TabbyPayment;
import org.styli.services.order.pojo.tamara.TamaraCaptures;
import org.styli.services.order.repository.CoreConfigDataRepository;
import org.styli.services.order.repository.Rma.AmastyRmaReasonRepository;
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
import org.styli.services.order.service.SalesOrderService;
import org.styli.services.order.service.SalesOrderServiceV2;
import org.styli.services.order.service.impl.CashfreePaymentServiceImpl;
import org.styli.services.order.service.impl.ConfigServiceImpl;
import org.styli.services.order.service.impl.KafkaServiceImpl;
import org.styli.services.order.service.impl.TabbyPaymentServiceImpl;
import org.styli.services.order.service.impl.TamaraPaymentServiceImpl;
import org.styli.services.order.service.impl.ZatcaServiceImpl;
import org.styli.services.order.utility.PaymentUtility;
import org.styli.services.order.utility.consulValues.PromoRedemptionValues;
import org.styli.services.order.utility.consulValues.PromoValues;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@SpringBootTest(classes = { RefundHelperTest.class })
public class RefundHelperTest extends AbstractTestNGSpringContextTests {
	@InjectMocks
	PaymentUtility paymentUtility;
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
	@InjectMocks
	TamaraHelper tamaraHelper;

	@Mock
	private RestTemplate restTemplate;
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
	@InjectMocks
	TabbyHelper tabbyHelper;

	@InjectMocks
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
	PaymentDtfHelper paymentDtfHelper;
	@InjectMocks
	private RefundHelper refundHelper;
	private CustomerEntity customerEntity;
	@Mock
	private SalesCreditmemoGridRepository salesCreditmemoGridRepository;
	@Mock
	CoreConfigDataRepository coreConfigDataRepository;
	@Mock
	private ZatcaServiceImpl zatcaServiceImpl;

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
//		ReflectionTestUtils.setField(configService, "internalAuthBearerToken", "token,dwd,1,2");
//		ReflectionTestUtils.setField(configService, "externalAuthBearerToken", "token,ds,1,1");
	}

	@Test
	public void cancelOrderObjectTest() {
		CancelOrderRequest req = new CancelOrderRequest();
		setStaticfields();
		setSalseOrderData();
		setMockData();
		setorderCred();
		SalesOrder order = refundHelper.cancelOrderObject(req, salesOrder, true, "payfort_fort_cc");
		assertNotNull(order);
	}

	@Test
	public void cancelOrderObject2Test() {
		CancelOrderRequest req = new CancelOrderRequest();
		req.setReason("cancel");
		setStaticfields();
		setSalseOrderData();
		setMockData();
		setorderCred();
		SalesOrder order = refundHelper.cancelOrderObject(req, salesOrder, false, "payfort_fort_cc");
		assertNotNull(order);
	}

	@Test
	public void cancelOrderItemsTest() {
		CancelOrderRequest req = new CancelOrderRequest();
		req.setReason("cancel");
		setStaticfields();
		setSalseOrderData();
		setMockData();
		setorderCred();
		SalesOrder order = refundHelper.cancelOrderItems(salesOrder, false);
		assertNotNull(order);
	}

	@Test
	public void cancelOrderItems2Test() {
		CancelOrderRequest req = new CancelOrderRequest();
		req.setReason("cancel");
		setStaticfields();
		setSalseOrderData();
		setMockData();
		setorderCred();
		SalesOrder order = refundHelper.cancelOrderItems(salesOrder, true);
		assertNotNull(order);
	}

	@Test
	public void cancelOrderPaymentTest() {
		CancelOrderRequest req = new CancelOrderRequest();
		req.setReason("cancel");
		setStaticfields();
		setSalseOrderData();
		setMockData();
		setorderCred();
		SalesOrder order = refundHelper.cancelOrderPayment(salesOrder);
		assertNotNull(order);
	}

	@Test
	public void cancelOrderGridTest() {
		CancelOrderRequest req = new CancelOrderRequest();
		req.setReason("cancel");
		setStaticfields();
		setSalseOrderData();
		setMockData();
		setorderCred();
		SalesOrderGrid order = refundHelper.cancelOrderGrid(salesOrder, true, "payfort_fort_cc");
		assertNotNull(order);
	}

	@Test
	public void cancelStatusHistoryTest() {
		CancelOrderRequest req = new CancelOrderRequest();
		req.setReason("cancel");
		setStaticfields();
		setSalseOrderData();
		setMockData();
		setorderCred();
		SalesOrder order = refundHelper.cancelStatusHistory(salesOrder, true, new BigDecimal(10), "");
		order = refundHelper.cancelStatusHistory(salesOrder, false, new BigDecimal(10), "");
		assertNotNull(order);
	}

	@Test
	public void createCreditMemoTest() {
		CancelOrderRequest req = new CancelOrderRequest();
		req.setReason("cancel");
		setStaticfields();
		setSalseOrderData();
		setMockData();
		setorderCred();
		Stores stores = new Stores();
		stores.setCurrencyConversionRate(new BigDecimal(1));
		stores.setStoreId("1");
		Map<String, BigDecimal> mapList = new HashMap<>();
		mapList.put("", new BigDecimal(1));
		SalesCreditmemo order = refundHelper.createCreditMemo(salesOrder, "1", new BigDecimal(10),
				new BigDecimal(10), "payfort_fort_cc", mapList, true, mapList, null, new BigDecimal(0), new BigDecimal(0), new BigDecimal(0));
		assertNotNull(order);
	}

	@Test
	public void createCreditmemoItemsTest() {
		CancelOrderRequest req = new CancelOrderRequest();
		req.setReason("cancel");
		setStaticfields();
		setSalseOrderData();
		setMockData();
		setorderCred();
		Stores stores = new Stores();
		stores.setCurrencyConversionRate(new BigDecimal(1));
		stores.setStoreId("1");
		Map<String, BigDecimal> mapList = new HashMap<>();
		mapList.put("", new BigDecimal(1));
		SalesCreditmemo memo = new SalesCreditmemo();
		memo.setEntityId(1);
		refundHelper.createCreditmemoItems(salesOrder, memo, mapList);
		verify(salesCreditmemoItemRepository, atLeast(1)).saveAndFlush(any());
	}

	@Test
	public void createCancelCreditmemoItemsTest() {
		CancelOrderRequest req = new CancelOrderRequest();
		req.setReason("cancel");
		setStaticfields();
		setSalseOrderData();
		setMockData();
		setorderCred();
		Stores stores = new Stores();
		stores.setCurrencyConversionRate(new BigDecimal(1));
		stores.setStoreId("1");
		Map<String, BigDecimal> mapList = new HashMap<>();
		mapList.put("01", new BigDecimal(1));
		SalesCreditmemo memo = new SalesCreditmemo();
		memo.setEntityId(1);
		salesOrder.setStoreToOrderRate(new BigDecimal(1));
		List<SalesOrderItem> items = new ArrayList<SalesOrderItem>();
		refundHelper.createCancelCreditmemoItems(salesOrder, memo, mapList, items);
		verify(salesCreditmemoRepository, atLeast(1)).saveAndFlush(any());
	}

	@Test
	public void createRefundCreditmemoItemsTest() {
		CancelOrderRequest req = new CancelOrderRequest();
		req.setReason("cancel");
		setStaticfields();
		setSalseOrderData();
		setMockData();
		setorderCred();
		Stores stores = new Stores();
		stores.setCurrencyConversionRate(new BigDecimal(1));
		stores.setStoreId("1");
		Map<String, BigDecimal> mapList = new HashMap<>();
		mapList.put("01", new BigDecimal(1));
		SalesCreditmemo memo = new SalesCreditmemo();
		memo.setEntityId(1);
		salesOrder.setStoreToOrderRate(new BigDecimal(1));
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

		refundHelper.createRefundCreditmemoItems(salesOrder, memo, amastyRmaRequest, mapList);
		verify(salesCreditmemoRepository, atLeast(1)).saveAndFlush(any());
	}

	@Test
	public void createReturnCreditMemoTest() {
		List<Integer> zatcaConst = Arrays.asList(1,3,7,9);
		ReflectionTestUtils.setField(constants, "zatcaFlag", zatcaConst);
		CancelOrderRequest req = new CancelOrderRequest();
		req.setReason("cancel");
		setStaticfields();
		setSalseOrderData();
		setMockData();
		setorderCred();
		Stores stores = new Stores();
		stores.setCurrencyConversionRate(new BigDecimal(1));
		stores.setStoreId("1");
		Map<String, BigDecimal> mapList = new HashMap<>();
		mapList.put("01", new BigDecimal(1));
		SalesCreditmemo memo = new SalesCreditmemo();
		memo.setEntityId(1);
		salesOrder.setStoreToOrderRate(new BigDecimal(1));
		salesOrder.setShippingAmount(new BigDecimal(1));
		salesOrder.setBaseShippingAmount(new BigDecimal(1));
		salesOrder.setBaseImportFee(new BigDecimal(1));

		salesOrder.setImportFee(new BigDecimal(1));
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

		when(amastyRmaRequestRepository.findByRequestId(any())).thenReturn(amastyRmaRequest);

		RefundAmountObject rao = new RefundAmountObject();
		RefundPaymentRespone refundRes = new RefundPaymentRespone();
		refundRes.setPaymentRRN("2342342334");
		refundHelper.createReturnCreditMemo(salesOrder, "1", "10", "payfort_fort_cc", "1", rao, mapList, "", refundRes, "", "", 0, 0, BigDecimal.ZERO);
		verify(salesCreditmemoRepository, atLeast(1)).saveAndFlush(any());
	}

	@Test
	public void createCreditmemoCommentTest() {
		setStaticfields();
		setSalseOrderData();
		setMockData();
		setorderCred();
		Stores stores = new Stores();
		stores.setCurrencyConversionRate(new BigDecimal(1));
		stores.setStoreId("1");
		Map<String, BigDecimal> mapList = new HashMap<>();
		mapList.put("01", new BigDecimal(1));
		SalesCreditmemo memo = new SalesCreditmemo();
		memo.setEntityId(1);
		memo.setGrandTotal(new BigDecimal(1));
		refundHelper.createCreditmemoComment(memo, new BigDecimal(1));
		verify(salesCreditmemoCommentRepository, atLeast(1)).saveAndFlush(any());
	}

	@Test
	public void releaseStoreCreditTest() {
		setStaticfields();
		setSalseOrderData();
		setMockData();
		setorderCred();
		Stores stores = new Stores();
		stores.setCurrencyConversionRate(new BigDecimal(1));
		stores.setStoreId("1");
		Map<String, BigDecimal> mapList = new HashMap<>();
		mapList.put("01", new BigDecimal(1));
		SalesCreditmemo memo = new SalesCreditmemo();
		memo.setEntityId(1);
		memo.setGrandTotal(new BigDecimal(1));

		AmastyStoreCredit amastyStoreCredit = new AmastyStoreCredit();
		amastyStoreCredit.setStoreCredit(new BigDecimal(1));
		amastyStoreCredit.setReturnableAmount(new BigDecimal(1));
		when(amastyStoreCreditRepository.findByCustomerId(anyInt())).thenReturn(Arrays.asList(amastyStoreCredit));

		AmastyStoreCreditHistory amastyStoreCreditHistory = new AmastyStoreCreditHistory();
		when(amastyStoreCreditHistoryRepository.findByCustomerIdOrderByHistoryIdDesc(anyInt()))
				.thenReturn(Arrays.asList(amastyStoreCreditHistory));

		refundHelper.releaseStoreCredit(salesOrder, new BigDecimal(1));
		verify(amastyStoreCreditHistoryRepository, atLeast(1)).saveAndFlush(any());
	}

	@Test
	public void createCreditmemoFailCommentTest() {
		setStaticfields();
		setSalseOrderData();
		setMockData();
		setorderCred();
		Stores stores = new Stores();
		stores.setCurrencyConversionRate(new BigDecimal(1));
		stores.setStoreId("1");
		Map<String, BigDecimal> mapList = new HashMap<>();
		mapList.put("01", new BigDecimal(1));
		SalesCreditmemo memo = new SalesCreditmemo();
		memo.setEntityId(1);
		memo.setGrandTotal(new BigDecimal(1));
		refundHelper.createCreditmemoFailComment(memo, new BigDecimal(1), "");
		verify(salesCreditmemoCommentRepository, atLeast(1)).saveAndFlush(any());
	}

	@Test
	public void createCreditmemoGridTest() {
		setStaticfields();
		setSalseOrderData();
		setMockData();
		setorderCred();
		Stores stores = new Stores();
		stores.setCurrencyConversionRate(new BigDecimal(1));
		stores.setStoreId("1");
		Map<String, BigDecimal> mapList = new HashMap<>();
		mapList.put("01", new BigDecimal(1));
		SalesCreditmemo memo = new SalesCreditmemo();
		memo.setEntityId(1);
		memo.setGrandTotal(new BigDecimal(1));
		refundHelper.createCreditmemoGrid(salesOrder, memo, "1", new SalesOrderGrid(), new BigDecimal(1));
		verify(salesCreditmemoGridRepository, atLeast(1)).saveAndFlush(any());
	}

	@Test
	public void payfortRefundInitiationTest() {
		setStaticfields();
		setSalseOrderData();
		setMockData();
		setorderCred();
		Stores stores = new Stores();
		stores.setCurrencyConversionRate(new BigDecimal(1));
		stores.setStoreId("1");
		stores.setWebsiteId(1);
		salesOrder.setBaseGrandTotal(new BigDecimal(1));
		CoreConfigData data = new CoreConfigData();
		data.setValue("10");
		when(coreConfigDataRepository.findByPathAndScopeId(anyString(), eq(0))).thenReturn(data);
		boolean respo = refundHelper.payfortRefundInitiation(salesOrder, "1", "payfort_fort_cc", stores);
		assertTrue(respo);
	}

	@Test
	public void payfortRefundInitiationFromMagentoTest() {
		setStaticfields();
		setSalseOrderData();
		setMockData();
		setorderCred();
		Stores stores = new Stores();
		stores.setCurrencyConversionRate(new BigDecimal(1));
		stores.setStoreId("1");
		stores.setWebsiteId(1);
		salesOrder.setBaseGrandTotal(new BigDecimal(1));
		MagentoAPIResponse respo = refundHelper.payfortRefundInitiationFromMagento(salesOrder);
		assertNotNull(respo);
	}

	@Test
	public void getGrandTotalAmountTest() {
		setStaticfields();
		setSalseOrderData();
		setMockData();
		setorderCred();
		Stores stores = new Stores();
		stores.setCurrencyConversionRate(new BigDecimal(1));
		stores.setStoreId("1");
		Map<String, BigDecimal> mapList = new HashMap<>();
		mapList.put("01", new BigDecimal(1));
		SalesCreditmemo memo = new SalesCreditmemo();
		memo.setEntityId(1);
		salesOrder.setStoreToOrderRate(new BigDecimal(1));
		refundHelper.updateOrderStatusHistory(salesOrder, "", "", "");
		BigDecimal respo = refundHelper.getGrandTotalAmount(salesOrder, mapList);
		assertEquals(respo.intValue(), 7);
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

	private void setStaticfields() {
		// TODO Auto-generated method stub
		ReflectionTestUtils.setField(configService, "internalAuthBearerToken", "token1,dwd,1,2");
		ReflectionTestUtils.setField(configService, "externalAuthBearerToken", "token2,ds,1,1");
//        ReflectionTestUtils.setField(paymentController, "tabbyPaymentService", tabbyPaymentService);
//        ReflectionTestUtils.setField(tabbyPaymentService, "refundHelper", refundHelper);
		ReflectionTestUtils.setField(paymentController, "configService", configService);
		ReflectionTestUtils.setField(paymentController, "paymentUtility", paymentUtility);
		ReflectionTestUtils.setField(paymentController, "cfPaymentService", cfPaymentService);
		ReflectionTestUtils.setField(configService, "internalAuthBearerToken", "ffyg,token");
		ReflectionTestUtils.setField(cfPaymentService, "paymentUtility", paymentUtility);
		ReflectionTestUtils.setField(refundHelper, "paymentDtfHelper", paymentDtfHelper);
	}

	void setMockData() {
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
		pay.setMethod("tabby");
		Set<SalesOrderPayment> payitem = new HashSet<>();
		payitem.add(pay);
		salesOrder.setSalesOrderPayment(payitem);
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
		items2.setProductType("packed");
		items2.setPriceInclTax(new BigDecimal(10));
		items2.setQtyOrdered(new BigDecimal(2));
		items2.setQtyCanceled(new BigDecimal(1));
		items2.setDiscountAmount(new BigDecimal(5));
		items2.setRowTotalInclTax(new BigDecimal(10));
		items2.setParentOrderItem(items);

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

		salesOrder.setSubSalesOrder(subord);
		salesOrder.setSalesShipmentTrack(set);
	}
}
