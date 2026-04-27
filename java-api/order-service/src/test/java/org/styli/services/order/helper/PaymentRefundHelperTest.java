package org.styli.services.order.helper;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
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

import javax.servlet.http.HttpServletRequest;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.controller.AutoRefundController;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.jwt.security.jwtsecurity.security.JwtValidator;
import org.styli.services.order.model.Customer.CustomerEntity;
import org.styli.services.order.model.Order.cancel.PayfortRefundResponse;
import org.styli.services.order.model.SalesOrder.SalesCreditmemo;
import org.styli.services.order.model.rma.AmastyRmaRequest;
import org.styli.services.order.model.rma.AmastyRmaRequestItem;
import org.styli.services.order.model.rma.sequence.SequenceCreditmemoOne;
import org.styli.services.order.model.sales.ProxyOrder;
import org.styli.services.order.model.sales.SalesInvoice;
import org.styli.services.order.model.sales.SalesInvoiceItem;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderGrid;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SalesOrderPayment;
import org.styli.services.order.model.sales.SalesShipmentTrack;
import org.styli.services.order.model.sales.StatusChangeHistory;
import org.styli.services.order.model.sales.SubSalesOrder;
import org.styli.services.order.pojo.*;
import org.styli.services.order.pojo.cancel.MagentoAPIResponse;
import org.styli.services.order.pojo.eas.EASCoinUpdateResponse;
import org.styli.services.order.pojo.eas.StyliCoinUpdate;
import org.styli.services.order.pojo.oms.BankSwiftCode;
import org.styli.services.order.pojo.request.Order.CreateOrderRequestV2;
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
import org.styli.services.order.service.SalesOrderService;
import org.styli.services.order.service.SalesOrderServiceV2;
import org.styli.services.order.service.impl.CalculateRefundAmountResponse;
import org.styli.services.order.service.impl.CashfreePaymentServiceImpl;
import org.styli.services.order.service.impl.ConfigServiceImpl;
import org.styli.services.order.service.impl.EASServiceImpl;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@SpringBootTest(classes = { PaymentRefundHelperTest.class })
public class PaymentRefundHelperTest extends AbstractTestNGSpringContextTests {
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
	PaymentRefundHelper paymentRefundHelper;

	@InjectMocks
	private ConfigServiceImpl configService;

	@InjectMocks
	private AutoRefundController autoRefundController;
	@InjectMocks
	EASServiceImpl eASServiceImpl;

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
	@Mock
	private ZatcaServiceImpl zatcaServiceImpl;

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
	public void addStoreCreditTest() {
		paymentRefundHelper.addStoreCredit(salesOrder, new BigDecimal(1), true);
		verify(kafkaService, atLeast(1)).publishSCToKafka(any());
	}

	@Test
	public void createReturnRmaTest() {
		ReflectionTestUtils.setField(constants, "zatcaFlag", Arrays.asList(1,3));
		RefundAmountObject rao = new RefundAmountObject();
		Map<String, BigDecimal> mapList = new HashMap<>();
		mapList.put("01", new BigDecimal(1));
		paymentRefundHelper.createReturnRma(salesOrder, "payfort_fort_cc", "10", amastyRmaRequest, rao, mapList, "", null, "", "", BigDecimal.ZERO);
	}

	@Test
	public void getGiftVoucherRefundAmountTest() {
		RefundAmountObject rao = new RefundAmountObject();
		rao.setRefundStorecreditAmount(new BigDecimal(1));
		Map<String, BigDecimal> mapList = new HashMap<>();
		mapList.put("01", new BigDecimal(1));
		when(subSalesOrderItemRepository.findBySalesOrderItem(any()))
				.thenReturn(new ArrayList<>(salesOrder.getSubSalesOrderItem()));
		BigDecimal respo = paymentRefundHelper.getGiftVoucherRefundAmount(salesOrder, amastyRmaRequest, rao);
		assertNotNull(respo);
	}

	@Test
	public void setReturnVoucherValueInDBTest() {
		RefundAmountObject rao = new RefundAmountObject();
		rao.setRefundStorecreditAmount(new BigDecimal(1));
		Map<String, BigDecimal> mapList = new HashMap<>();
		mapList.put("01", new BigDecimal(1));
		when(subSalesOrderItemRepository.findBySalesOrderItem(any()))
				.thenReturn(new ArrayList<>(salesOrder.getSubSalesOrderItem()));
		paymentRefundHelper.setReturnVoucherValueInDB(salesOrder, new BigDecimal(1));
		verify(salesOrderRepository, atLeast(1)).saveAndFlush(any());
	}

	@Test
	public void payfortVoidAuthorizationcallTest() {
		Map<String, BigDecimal> mapList = new HashMap<>();
		mapList.put("01", new BigDecimal(1));
		RefundPaymentRespone respo = paymentRefundHelper.payfortVoidAuthorizationcall(salesOrder, "1",
				"payfort_fort_cc");
		assertNotNull(respo);
	}

	@Test
	public void getCancelAmountTest() {
		Map<String, BigDecimal> mapList = new HashMap<>();
		mapList.put("01", new BigDecimal(1));
		List<SalesOrderItem> canCelitemList = new ArrayList<>();
		BigDecimal respo = paymentRefundHelper.getCancelAmount(salesOrder, mapList, canCelitemList);
		assertEquals(respo.intValue(), 15);
	}

	@Test
	public void getCanceledAmountTest() {
		Stores stores = new Stores();
		stores.setCurrencyConversionRate(new BigDecimal(1));
		stores.setStoreId("1");
		Map<String, BigDecimal> mapList = new HashMap<>();
		mapList.put("01", new BigDecimal(1));
		paymentRefundHelper.getTotalCanceledAmount(salesOrder);
		paymentRefundHelper.getCanceledItemQty(salesOrder);
		paymentRefundHelper.getStoreCredit(salesOrder, new BigDecimal(1), stores);
		paymentRefundHelper.getCancelledStoreCredit(salesOrder, stores, new BigDecimal(10), new BigDecimal(10), true,
				"payfort_fort_cc");
		BigDecimal currentOrderValueForStoreCredit = paymentRefundHelper.findCurrentOrderValue(salesOrder);
		paymentRefundHelper.getCancelledStoreCreditWithCurrentOrderValue(salesOrder, stores, new BigDecimal(10), new BigDecimal(10), true,
				"payfort_fort_cc", currentOrderValueForStoreCredit);
		paymentRefundHelper.getCancelledStoreCredit(salesOrder, stores, new BigDecimal(1), new BigDecimal(10), true,
				"payfort_fort_cc");
		paymentRefundHelper.getCancelledStoreCredit(salesOrder, stores, new BigDecimal(10), new BigDecimal(1), true,
				"payfort_fort_cc");
		BigDecimal respo = paymentRefundHelper.getCanceledAmount(salesOrder);
		assertEquals(respo.intValue(), 14);
	}

	@Test
	public void saveOrderGridTest() {
		paymentRefundHelper.saveOrderGrid(salesOrder, "");
		verify(salesOrderGridRepository, atLeast(1)).saveAndFlush(any());
	}

	@Test
	public void cancelPercentageCalculationTest() {
//	  RefundAmountObject rao=new  RefundAmountObject();
		Map<String, BigDecimal> mapList = new HashMap<>();
		mapList.put("01", new BigDecimal(1));
		salesOrder.setStoreToOrderRate(new BigDecimal(1));
		salesOrder.setShippingAmount(new BigDecimal(1));
		salesOrder.setBaseShippingAmount(new BigDecimal(1));
		salesOrder.setBaseImportFee(new BigDecimal(1));
		salesOrder.setImportFee(new BigDecimal(1));
		salesOrder.setStoreId(7);
		CancelDetails details = new CancelDetails();
		Stores store = buildStore();
		store.setStoreId("7");
		BigDecimal currentOrderValue = paymentRefundHelper.findCurrentOrderValue(salesOrder);
		details.setCurrentOrderValue(currentOrderValue);
		paymentRefundHelper.cancelPercentageCalculation(salesOrder, new BigDecimal(10), new BigDecimal(10), details,
				true, "payfort_fort_cc", new BigDecimal(10));
		for (SalesOrderItem it : salesOrder.getSalesOrderItem()) {
			it.setQtyCanceled(new BigDecimal(0));
		}
		addInvoiceData();

//		List<SalesCreditmemo> lstmemo = Arrays.asList(memo);
//		when(amastyRmaRequestRepository.findByRequestId(any())).thenReturn(amastyRmaRequest);
//		when(salesCreditmemoRepository.findByRmaNumber(anyString())).thenReturn(lstmemo);
//		when(salesCreditmemoRepository.findByOrderId(anyInt())).thenReturn(lstmemo);

		BigDecimal respo = paymentRefundHelper.cancelPercentageCalculation(salesOrder, new BigDecimal(10),
				new BigDecimal(10), details, true, "payfort_fort_cc", new BigDecimal(10));
		assertNotNull(respo);
	}

	private  Stores buildStore() {
		Stores store = new Stores();
		store.setImportFeePercentage(new BigDecimal(2.5));
		store.setImportMaxFeePercentage(new BigDecimal(7.5));
		store.setMinimumDutiesAmount(new BigDecimal(1000));
		store.setCustomDutiesPercentage(new BigDecimal(10));
		return store;
	}

	@Test
	public void calculaterefundamountTest() {
		RefundAmountObject rao = new RefundAmountObject();
		Map<String, BigDecimal> mapList = new HashMap<>();
		mapList.put("01", new BigDecimal(1));
		addInvoiceData();

		List<SalesCreditmemo> lstmemo = Arrays.asList(memo);
		when(amastyRmaRequestRepository.findByRequestId(any())).thenReturn(amastyRmaRequest);
		when(salesCreditmemoRepository.findByRmaNumber(anyString())).thenReturn(lstmemo);
		when(salesCreditmemoRepository.findByOrderId(anyInt())).thenReturn(lstmemo);
		CalculateRefundAmountResponse calculateRefundAmountResponse= paymentRefundHelper.calculaterefundamount(salesOrder, "payfort_fort_cc", amastyRmaRequest, rao,
				mapList, BigDecimal.valueOf(1.0), null, "");
		assertNotNull(calculateRefundAmountResponse);
	}

	@Test
	public void testCalculateImportFee_UAE() throws Exception {
		Stores uaeStore =  buildStore();
		uaeStore.setStoreId("7"); // UAE
		// Use reflection to access private method
		java.lang.reflect.Method method = PaymentRefundHelper.class.getDeclaredMethod("calculateImportFee", BigDecimal.class, Stores.class);
		method.setAccessible(true);
		// Order value <= 1000 AED, should be 0%
		BigDecimal fee1 = (BigDecimal) method.invoke(paymentRefundHelper, new BigDecimal("900"), uaeStore);
		assertEquals(fee1, new BigDecimal("22.50"));
		// Order value > 1000 AED, should be 5%
		BigDecimal fee2 = (BigDecimal) method.invoke(paymentRefundHelper, new BigDecimal("1100"), uaeStore);
		assertEquals(fee2, new BigDecimal("90.75"));
	}

	@Test
	public void testCalculateImportFee_Kuwait() throws Exception {
		Stores kwtStore =  buildStore();
		kwtStore.setStoreId("12"); // Kuwait
		java.lang.reflect.Method method = PaymentRefundHelper.class.getDeclaredMethod("calculateImportFee", BigDecimal.class, Stores.class);
		method.setAccessible(true);
		// Order value <= 100 KWD, should be 2.5%
		BigDecimal fee1 = (BigDecimal) method.invoke(paymentRefundHelper, new BigDecimal("90"), kwtStore);
		assertEquals(fee1, new BigDecimal("2.25"));
		// Order value > 100 KWD, should be 7.5%
		BigDecimal fee2 = (BigDecimal) method.invoke(paymentRefundHelper, new BigDecimal("120"), kwtStore);
		assertEquals(fee2, new BigDecimal("3.00"));
	}

	@Test
	public void testCalculateImportFee_Bahrain() throws Exception {
		Stores bhStore = buildStore();
		bhStore.setStoreId("19"); // Bahrain
		java.lang.reflect.Method method = PaymentRefundHelper.class.getDeclaredMethod("calculateImportFee", BigDecimal.class, Stores.class);
		method.setAccessible(true);
		// Flat 2.5% for any value
		BigDecimal fee1 = (BigDecimal) method.invoke(paymentRefundHelper, new BigDecimal("200"), bhStore);
		assertEquals(fee1, new BigDecimal("5.00"));
		BigDecimal fee2 = (BigDecimal) method.invoke(paymentRefundHelper, new BigDecimal("100"), bhStore);
		assertEquals(fee2, new BigDecimal("2.50"));
	}

	@Test
	public void testFindImportFeeBasedOnStore_PartialCancel_UAE() throws Exception {
		Stores uaeStore = buildStore();
		uaeStore.setStoreId("7"); // UAE
		salesOrder.setStoreId(7);
		salesOrder.setGrandTotal(new BigDecimal("1100")); // Original order value
		// Use reflection to access private method
		java.lang.reflect.Method method = PaymentRefundHelper.class.getDeclaredMethod("findNewImportFeeOfOrder", BigDecimal.class, Stores.class, BigDecimal.class);
		method.setAccessible(true);
		// Partial cancel: refund 200 AED
		BigDecimal currentOrderValue = paymentRefundHelper.findCurrentOrderValue(salesOrder);
		BigDecimal newImportFee = (BigDecimal) method.invoke(paymentRefundHelper, currentOrderValue, uaeStore, new BigDecimal("200"));
		BigDecimal originalImportFee = paymentRefundHelper.calculateImportFee(salesOrder.getGrandTotal(), uaeStore);
		BigDecimal refund = new BigDecimal("200").add(originalImportFee.subtract(newImportFee));
		// Original import fee: 55 (5% of 1100), new import fee: 0 (0% of 900), refund import fee: 55
		// Total refund should be 200 + 55 = 255
		assertEquals(refund, new BigDecimal("295.35"));
	}

	private void addInvoiceData() {
		SalesInvoice salesInvoice = new SalesInvoice();
		salesInvoice.setEntityId(1);

		salesInvoice.setGrandTotal(new BigDecimal(1));
		salesInvoice.setAmstorecreditAmount(new BigDecimal(1));
		LocalDateTime currentTime = LocalDateTime.now();
		salesInvoice.setCreatedAt(Timestamp.valueOf(currentTime));
		SalesInvoiceItem salesInvoiceItem = new SalesInvoiceItem();
		salesInvoiceItem.setSku("1");
		salesInvoiceItem.setQuantity(new BigDecimal(10));
//		salesInvoiceItem.set
		Set<SalesInvoiceItem> salesInvoiceItemset = new HashSet<>();
		salesInvoiceItemset.add(salesInvoiceItem);
		salesInvoice.setSalesInvoiceItem(salesInvoiceItemset);
		Set<SalesInvoice> salesInvoices = new HashSet<>();
		salesInvoices.add(salesInvoice);
		salesOrder.setSalesInvoices(salesInvoices);
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
		UaeCredentials uaeCredentials = new UaeCredentials();
		uaeCredentials.setPayfortuaeAmountMultiplier("1");
		uaeCredentials.setPayfortUaeCardAccessCode("1");
		payFort.setUaeCredentials(uaeCredentials);
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
		ReflectionTestUtils.setField(paymentRefundHelper, "eASServiceImpl", eASServiceImpl);
//      ReflectionTestUtils.setField(tabbyPaymentService, "refundHelper", refundHelper);
		ReflectionTestUtils.setField(configService, "internalAuthBearerToken", "ffyg,token");
		ReflectionTestUtils.setField(cfPaymentService, "paymentUtility", paymentUtility);
		ReflectionTestUtils.setField(paymentRefundHelper, "paymentDtfHelper", paymentDtfHelper);
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

		ResponseEntity<String> iresponse = ResponseEntity.ok("{order_id=1,order_status=approved,cf_order_id=1,payload=AUTHORIZED}");
		ResponseEntity<String> response = ResponseEntity.ok(g.toJson(salesOrder));
		TabbyPayment tb = new TabbyPayment();
		tb.setId("1");
		ResponseEntity<TabbyPayment> tresponse = ResponseEntity.ok(tb);
		ResponseEntity<String> stresponse = ResponseEntity.ok("");
		TamaraCaptures tc = new TamaraCaptures();
		tc.setCaptureId("1");
		ResponseEntity<TamaraCaptures> tcresponse = ResponseEntity.ok(tc);

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
		ResponseEntity<PayfortRefundResponse> fortresponse = ResponseEntity.ok(fortbody);
		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(PayfortRefundResponse.class))).thenReturn(fortresponse);

		MagentoAPIResponse magentobody = new MagentoAPIResponse();
		magentobody.setStatusCode(200);
		MagentoAPIResponse[] ary = { magentobody };
		ResponseEntity<MagentoAPIResponse[]> magentoresponse = ResponseEntity.ok(ary);
		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(MagentoAPIResponse[].class))).thenReturn(magentoresponse);

		PayfortVoidAuthorizationResponse authrespobody = new PayfortVoidAuthorizationResponse();
		authrespobody.setStatus("08");

		ResponseEntity<PayfortVoidAuthorizationResponse> authresponse = ResponseEntity.ok(authrespobody);
		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
						Mockito.any(HttpEntity.class), Mockito.eq(PayfortVoidAuthorizationResponse.class)))
				.thenReturn(authresponse);

		InventoryMetaData meta = new InventoryMetaData();
		meta.setStatus(true);
		InventoryBlockResponse body = new InventoryBlockResponse();
		body.setMeta(meta);
		body.setResponse("response");
		ResponseEntity<InventoryBlockResponse> inresponse = ResponseEntity.ok(body);;

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(InventoryBlockResponse.class))).thenReturn(inresponse);
		EASCoinUpdateResponse coinbody = new EASCoinUpdateResponse();
		StyliCoinUpdate styliCoinUpdate = new StyliCoinUpdate();
		styliCoinUpdate.setCoin(1);
		coinbody.setResponse(styliCoinUpdate);
		coinbody.setStatus(200);
		ResponseEntity<EASCoinUpdateResponse> coinresponse = ResponseEntity.ok(coinbody);;

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(EASCoinUpdateResponse.class))).thenReturn(coinresponse);

		when(salesOrderGridRepository.findByEntityId(anyInt())).thenReturn(new SalesOrderGrid());
		when(salesOrderRepository.findByEntityId(anyInt())).thenReturn(salesOrder);
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
		salesOrder.setSubtotal(new BigDecimal(10));
		salesOrder.setShippingAmount(new BigDecimal(10));
		salesOrder.setCashOnDeliveryFee(new BigDecimal(10));
		salesOrder.setDiscountAmount(new BigDecimal(-5));
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
		items2.setProductType("simple");
		items2.setPriceInclTax(new BigDecimal(10));
		items2.setQtyOrdered(new BigDecimal(2));
		items2.setQtyCanceled(new BigDecimal(1));
		items2.setDiscountAmount(new BigDecimal(5));
		items2.setRowTotalInclTax(new BigDecimal(10));
		items2.setParentOrderItem(items);
		items2.setQtyCanceled(new BigDecimal(1));

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
