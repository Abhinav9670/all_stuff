package org.styli.services.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.powermock.core.classloader.annotations.PrepareForTest;
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
import org.styli.services.order.db.product.exception.NotFoundException;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.helper.ExternalQuoteHelper;
import org.styli.services.order.helper.KafkaBrazeHelper;
import org.styli.services.order.helper.MulinHelper;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.helper.OrderHelperV2;
import org.styli.services.order.helper.PaymentRefundHelper;
import org.styli.services.order.helper.RMAHelper;
import org.styli.services.order.helper.RefundHelper;
import org.styli.services.order.jwt.security.jwtsecurity.model.JwtUser;
import org.styli.services.order.jwt.security.jwtsecurity.security.JwtValidator;
import org.styli.services.order.model.rma.AmastyRmaRequest;
import org.styli.services.order.model.rma.AmastyRmaTracking;
import org.styli.services.order.model.rma.sequence.SequenceCreditmemoOne;
import org.styli.services.order.model.sales.ProxyOrder;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderGrid;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SalesOrderPayment;
import org.styli.services.order.model.sales.SalesShipmentTrack;
import org.styli.services.order.model.sales.StatusChangeHistory;
import org.styli.services.order.pojo.AddressObject;
import org.styli.services.order.pojo.CatalogProductEntityForQuoteDTO;
import org.styli.services.order.pojo.GetOrderConsulValues;
import org.styli.services.order.pojo.KsaCredentials;
import org.styli.services.order.pojo.NavikDetails;
import org.styli.services.order.pojo.OrderKeyDetails;
import org.styli.services.order.pojo.OrderRestrictionDetails;
import org.styli.services.order.pojo.PayfortDetails;
import org.styli.services.order.pojo.PayfortOrderRefundResponse;
import org.styli.services.order.pojo.QuoteDTO;
import org.styli.services.order.pojo.WmsDetails;
import org.styli.services.order.pojo.cancel.CancelOrderInitResponseDTO;
import org.styli.services.order.pojo.cancel.CancelOrderRequest;
import org.styli.services.order.pojo.cancel.SalesOrderCancelReason;
import org.styli.services.order.pojo.cancel.SalesOrderCancelReasonStore;
import org.styli.services.order.pojo.order.RMAOrderInitV2ResponseDTO;
import org.styli.services.order.pojo.order.RMAOrderItemV2Request;
import org.styli.services.order.pojo.order.RMAOrderV2Request;
import org.styli.services.order.pojo.quote.response.GetQuoteResponse;
import org.styli.services.order.pojo.request.Order.CreateOrderRequestV2;
import org.styli.services.order.pojo.response.InventoryBlockResponse;
import org.styli.services.order.pojo.response.ProductInventoryRes;
import org.styli.services.order.pojo.response.ProductValue;
import org.styli.services.order.pojo.response.Order.CreateOrderResponseDTO;
import org.styli.services.order.pojo.response.Order.OrderResponseDTO;
import org.styli.services.order.repository.Rma.AmastyRmaReasonRepository;
import org.styli.services.order.repository.Rma.AmastyRmaRequestItemRepository;
import org.styli.services.order.repository.Rma.AmastyRmaRequestRepository;
import org.styli.services.order.repository.Rma.AmastyRmaTrackingRepository;
import org.styli.services.order.repository.Rma.sequence.SequenceCreditmemoOneRepository;
import org.styli.services.order.repository.Rma.sequence.SequenceCreditmemoThreeRepository;
import org.styli.services.order.repository.SalesOrder.ProxyOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderAddressRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderCancelReasonRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderStatusLabelRepository;
import org.styli.services.order.repository.SalesOrder.StatusChaneHistoryRepository;
import org.styli.services.order.repository.SalesOrder.SubSalesOrderRepository;
import org.styli.services.order.service.ConfigService;
import org.styli.services.order.service.CustomerService;
import org.styli.services.order.service.impl.ConfigServiceImpl;
import org.styli.services.order.service.impl.EASServiceImpl;
import org.styli.services.order.service.impl.SalesOrderCancelServiceImpl;
import org.styli.services.order.service.impl.SalesOrderRMAServiceImpl;
import org.styli.services.order.service.impl.SalesOrderServiceImpl;
import org.styli.services.order.service.impl.SalesOrderServiceV2Impl;
import org.styli.services.order.service.impl.SalesOrderServiceV3Impl;
import org.styli.services.order.service.impl.WhatsappBotServiceImpl;
import org.styli.services.order.service.impl.child.GetFailedOrderList;
import org.styli.services.order.service.impl.child.GetOrderCount;
import org.styli.services.order.service.impl.child.GetOrderList;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@SpringBootTest(classes = { OrderWriteControllerTest.class })
@PrepareForTest({ OrderConstants.class })
public class OrderWriteControllerTest extends AbstractTestNGSpringContextTests {

	@InjectMocks
	OrderWriteController orderWriteController;

	@InjectMocks
	GetFailedOrderList getFailedOrderList;
	@InjectMocks
	GetOrderCount getOrderCount;
	@InjectMocks
	RMAHelper rmaHelper;

	@InjectMocks
	WhatsappBotServiceImpl whatsappBotService;
	@InjectMocks
	GetOrderList getOrderList;

	@InjectMocks
	OrderOutboundController orderOutboundController;
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
	ExternalQuoteHelper externalQuoteHelper;
	@InjectMocks
	OmsorderentityConverter omsorderentityConverter;

	@InjectMocks
	org.styli.services.order.utility.Constants constants;

	@InjectMocks
	ConfigServiceImpl configService;
	@InjectMocks
	SalesOrderServiceV2Impl salesOrderServiceV2;
	@InjectMocks
	SalesOrderCancelServiceImpl salesOrderCancelService;
	@InjectMocks
	SalesOrderRMAServiceImpl salesOrderRMAService;

	@InjectMocks
	PaymentRefundHelper paymentDtfRefundHelper;
	@InjectMocks
	OrderHelper orderHelper;
	@Mock
	OrderHelper orderHelperMock;
	@Mock
	OrderHelperV2 orderHelperv2;
	@InjectMocks
	SalesOrderServiceV3Impl salesOrderServiceV3;

	@Mock
	AmastyRmaReasonRepository amastyRmaReasonRepository;
	@Mock
	SalesOrderServiceV3Impl salesOrderServiceV3Impl;
	@Mock
	ProxyOrderRepository proxyOrderRepository;
	@Mock
	RefundHelper refundHelpermock;
	@Mock
	EASServiceImpl eASServiceImpl;
	@Mock
	StatusChaneHistoryRepository statusChaneHistoryRepository;
	@Mock
	RestTemplate restTemplate;
	@Mock
	SalesOrderRepository salesOrderRepository;
	@Mock
	CustomerService customerService;
	@Mock
	SalesOrderGridRepository salesOrderGridRepository;
	@Mock
	SequenceCreditmemoThreeRepository sequenceCreditmemoThreeRepository;
	@Mock
	JwtValidator validator;
	@InjectMocks
	SequenceCreditmemoOne sequenceCreditmemoOne;
	@Mock
	SequenceCreditmemoOneRepository sequenceCreditmemoOneRepository;
	@Mock
	SalesOrderCancelReasonRepository salesOrderCancelReasonRepository;

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
		SalesOrderCancelReason reason = new SalesOrderCancelReason();
		reason.setReasonId(1);
		SalesOrderCancelReasonStore reasonStore = new SalesOrderCancelReasonStore();
		reasonStore.setStoreId(1);
		reasonStore.setReasonId(1);
		reasonStore.setLabel("incorect size");
		Set<SalesOrderCancelReasonStore> salesOrderCancelReasonStores = new HashSet<>();
		salesOrderCancelReasonStores.add(reasonStore);
		reason.setSalesOrderCancelReasonStores(salesOrderCancelReasonStores);
		reason.setTitle("incorrect size");
		List<SalesOrderCancelReason> cancelReasons = Arrays.asList(reason);
		when(salesOrderCancelReasonRepository.findByStatusOrderBySortOrderAsc(anyInt())).thenReturn(cancelReasons);
//		when(OrderConstants.checkPaymentMethod(any())).thenReturn(true);
		ReflectionTestUtils.setField(configService, "internalAuthBearerToken", "token,dwd,1,2");
		ReflectionTestUtils.setField(configService, "externalAuthBearerToken", "token,ds,1,1");
	}

	@Test
	public void testcancelOrderInit() {
		setStaticData();
		setAuthenticateData();
		CancelOrderRequest req = new CancelOrderRequest();
		req.setCustomerId(1);
		req.setStoreId(1);
		req.setOrderId(1);
		CancelOrderInitResponseDTO respo = orderWriteController.cancelOrderInit(requestHeader, req);
		assertTrue(respo.getStatus());
		assertNotNull(respo.getResponse());
	}

	@Test
	public void testcancelOrder() {
		setStaticData();
		setAuthenticateData();
		setSalseOrderData();
		prepareOrderConstant();
		CancelOrderRequest req = new CancelOrderRequest();
		req.setCustomerId(1);
		req.setStoreId(1);
		req.setOrderId(1);
		when(salesOrderRepository.findByEntityIdAndCustomerId(any(), any())).thenReturn(salesOrder);
		when(salesOrderRepository.findByEntityId(anyInt())).thenReturn(salesOrder);
		SalesOrderGrid grid = new SalesOrderGrid();
		grid.setTotalPaid(new BigDecimal(1));
		when(refundHelpermock.cancelOrderObject(any(), any(), anyBoolean(), any())).thenReturn(salesOrder);
		when(refundHelpermock.cancelStatusHistory(any(), anyBoolean(), any(), anyString())).thenReturn(salesOrder);
		when(refundHelpermock.cancelOrderItems(any(), anyBoolean())).thenReturn(salesOrder);
		when(refundHelpermock.cancelOrderGrid(any(), anyBoolean(), anyString())).thenReturn(grid);
		when(refundHelpermock.getIncrementId(anyInt())).thenReturn("1");
		StatusChangeHistory statusChangeHistory = new StatusChangeHistory();
		when(proxyOrderRepository.findByPaymentId(anyString())).thenReturn(new ProxyOrder());
		when(statusChaneHistoryRepository.findByOrderId(anyString())).thenReturn(statusChangeHistory);

		ReflectionTestUtils.setField(sequenceCreditmemoOne, "sequenceValue", new Integer(1));
		PayfortOrderRefundResponse body = new PayfortOrderRefundResponse();
		body.setStatus(OrderConstants.PAYFORT_REFUND_CONSTANT_SUCCESS_STATUS);
		ResponseEntity<PayfortOrderRefundResponse> response = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(PayfortOrderRefundResponse.class))).thenReturn(response);

		InventoryBlockResponse ibody = new InventoryBlockResponse();
		body.setResponseCode("200");
		ResponseEntity<InventoryBlockResponse> iResponse = new ResponseEntity<>(HttpStatus.OK).ok(ibody);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(InventoryBlockResponse.class))).thenReturn(iResponse);

		ResponseEntity<Object> oResponse = new ResponseEntity<>(HttpStatus.OK).ok("");

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(Object.class))).thenReturn(oResponse);

		OrderResponseDTO respo = orderWriteController.cancelOrder(requestHeader, req);
		assertTrue(respo.getStatus());
		assertEquals(respo.getStatusCode(), "200");
	}

	ConfigService configService1 = Mockito.mock(ConfigService.class);
	@Test
	public void testRmaOrderV2() {
		setStaticData();
		setAuthenticateData();
		setSalseOrderData();
		prepareOrderConstant();

		RMAOrderItemV2Request v2req = new RMAOrderItemV2Request();
		v2req.setParentOrderItemId(1);
		v2req.setReasonId(1);
		v2req.setReturnQuantity(1);
		List<RMAOrderItemV2Request> items = new ArrayList<>();
		items.add(v2req);

		RMAOrderV2Request req = new RMAOrderV2Request();
		req.setItems(items);
		req.setCustomerId(1);
		req.setStoreId(1);
		req.setOrderId(1);
		when(salesOrderRepository.findByEntityIdAndCustomerId(any(), any())).thenReturn(salesOrder);
		when(configService1.getWebsiteRefundByStoreId(anyInt())).thenReturn(10.0);
		when(salesOrderRepository.findByEntityId(anyInt())).thenReturn(salesOrder);
		AmastyRmaTracking rmaTrack = new AmastyRmaTracking();
		rmaTrack.setRequestId(1);
		List<AmastyRmaTracking> amastyTrackingList = new ArrayList<>();
		amastyTrackingList.add(rmaTrack);
		when(amastyRmaTrackingRepository.findByRequestId(any())).thenReturn(amastyTrackingList);
		LocalDateTime currentTime = LocalDateTime.now();
		Timestamp timestamp = Timestamp.valueOf(currentTime);
		AmastyRmaRequest amastyRmaRequest = new AmastyRmaRequest();
		amastyRmaRequest.setCreatedAt(timestamp);
		amastyRmaRequest.setStatus(1);
		amastyRmaRequest.setOrderId(1);
		when(amastyRmaRequestRepository.findFirstByCustomerIdAndOrderIdOrderByCreatedAtDesc(anyInt(), anyInt()))
				.thenReturn(amastyRmaRequest);
		StatusChangeHistory statusChangeHistory = new StatusChangeHistory();
		when(statusChaneHistoryRepository.findByOrderId(any())).thenReturn(statusChangeHistory);
		when(orderHelperMock.getIncrementId(anyInt())).thenReturn("1");

		SalesOrderGrid grid = new SalesOrderGrid();
		grid.setTotalPaid(new BigDecimal(1));
		when(refundHelpermock.cancelOrderObject(any(), any(), anyBoolean(), any())).thenReturn(salesOrder);
		when(refundHelpermock.cancelStatusHistory(any(), anyBoolean(), any(), anyString())).thenReturn(salesOrder);
		when(refundHelpermock.cancelOrderItems(any(), anyBoolean())).thenReturn(salesOrder);
		when(refundHelpermock.cancelOrderGrid(any(), anyBoolean(), anyString())).thenReturn(grid);

		when(proxyOrderRepository.findByPaymentId(anyString())).thenReturn(new ProxyOrder());

		ReflectionTestUtils.setField(sequenceCreditmemoOne, "sequenceValue", new Integer(1));
		PayfortOrderRefundResponse body = new PayfortOrderRefundResponse();
		body.setStatus(OrderConstants.PAYFORT_REFUND_CONSTANT_SUCCESS_STATUS);
		ResponseEntity<PayfortOrderRefundResponse> response = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(PayfortOrderRefundResponse.class))).thenReturn(response);

		InventoryBlockResponse ibody = new InventoryBlockResponse();
		body.setResponseCode("200");
		ResponseEntity<InventoryBlockResponse> iResponse = new ResponseEntity<>(HttpStatus.OK).ok(ibody);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(InventoryBlockResponse.class))).thenReturn(iResponse);

		ResponseEntity<Object> oResponse = new ResponseEntity<>(HttpStatus.OK).ok("");

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(Object.class))).thenReturn(oResponse);

		OrderResponseDTO respo = orderWriteController.rmaOrderV2(requestHeader, req, anyString());
		assertTrue(respo.getStatus());
		assertEquals(respo.getStatusCode(), "200");
	}

	@Test
	public void testrmaOrderInitV2() {
		setStaticData();
		setAuthenticateData();
		setSalseOrderData();
		prepareOrderConstant();

		RMAOrderItemV2Request v2req = new RMAOrderItemV2Request();
		v2req.setParentOrderItemId(1);
		v2req.setReasonId(1);
		v2req.setReturnQuantity(1);
		List<RMAOrderItemV2Request> items = new ArrayList<>();
		items.add(v2req);

		RMAOrderV2Request req = new RMAOrderV2Request();
		req.setItems(items);
		req.setCustomerId(1);
		req.setStoreId(1);
		req.setOrderId(1);
		when(salesOrderRepository.findByEntityIdAndCustomerId(any(), any())).thenReturn(salesOrder);
		when(salesOrderRepository.findByEntityId(anyInt())).thenReturn(salesOrder);
		when(configService1.getWebsiteRefundByStoreId(anyInt())).thenReturn(10.0);
		RMAOrderInitV2ResponseDTO respo = orderWriteController.rmaOrderInitV2(requestHeader, req, anyString());
		assertTrue(respo.getStatus());
		assertNotNull(respo.getResponse());
	}

	@Test
	void testCreateOdrderFromQuote() throws NotFoundException {
		setStaticData();
		setAuthenticateData();
		setSalseOrderData();
		prepareOrderConstant();
		CreateOrderRequestV2 req = new CreateOrderRequestV2();
		req.setOrderIncrementId("1");
		req.setRetryPaymentReplica(false);
		req.setQuoteId("1");
		req.setStoreId(1);
		when(orderHelperMock.getIncrementId(anyInt())).thenReturn("1");
		when(orderHelperv2.createOrderObjectToPersist(any(), any(), any(), any(), any(), anyInt(), any(), any(), any(),
				any(), any(), anyBoolean(), anyBoolean())).thenReturn(salesOrder);

		GetQuoteResponse body = new GetQuoteResponse();
		body.setStatus(true);
		body.setStatusCode("200");
		QuoteDTO res = new QuoteDTO();
		res.setCustomerId("1");
		res.setSelectedPaymentMethod("md_payfort");
		res.setGrandTotal("10");
		res.setStoreId("1");
		AddressObject add = new AddressObject();
		add.setArea("blr");
		add.setCity("bbsr");
		res.setShippingAddress(add);
		CatalogProductEntityForQuoteDTO dto = new CatalogProductEntityForQuoteDTO();
		dto.setQuantity("1");
		dto.setSku("1");
		List<CatalogProductEntityForQuoteDTO> products = new ArrayList<>();
		products.add(dto);
		res.setProducts(products);
		body.setResponse(res);
		ResponseEntity<GetQuoteResponse> oResponse = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(GetQuoteResponse.class))).thenReturn(oResponse);
		ProductValue pval = new ProductValue();
		pval.setProcuctId(1);
		pval.setSku("1");
		pval.setValue("1");
		List<ProductValue> response = new ArrayList<>();
		response.add(pval);
		ProductInventoryRes prod = new ProductInventoryRes();
		prod.setResponse(response);
		ResponseEntity<ProductInventoryRes> presponse = new ResponseEntity<>(HttpStatus.OK).ok(prod);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(ProductInventoryRes.class))).thenReturn(presponse);

		CreateOrderResponseDTO respo = orderWriteController.createOrderFromQuote(requestHeader, req, null, null, null,
				null, null);
		assertTrue(respo.getStatus());
		assertNotNull(respo.getResponse());
	}

	public void prepareOrderConstant() {
		OrderKeyDetails keydetail = new OrderKeyDetails();
		keydetail.setMaximumOrderPedningOrderThreshold(10);
		OrderRestrictionDetails dtl = new OrderRestrictionDetails();
		dtl.setMdpayfoirt(new BigDecimal(10));
		keydetail.setOrderRestrictionDetails(dtl);
		GetOrderConsulValues val = new GetOrderConsulValues();
		val.setOrderDetails(keydetail);
		val.setExternalAuthEnable(false);
		val.setInternalAuthEnable(false);
		val.setInternalAuthEnable(true);
		NavikDetails nav = new NavikDetails();
		nav.setReturnAwbCreateClubbingHrs(1);
		val.setNavik(nav);
		WmsDetails wmsd = new WmsDetails();
		wmsd.setWmsOrderPushMinutes(new Integer(10));
		val.setWms(wmsd);
		PayfortDetails payfort = new PayfortDetails();
		KsaCredentials cred = new KsaCredentials();
		cred.setPayfortKsaCardAccessCode(null);
		cred.setPayfortksaAmountMultiplier("1");
		payfort.setKsaCredentials(cred);
		val.setPayfort(payfort);
		ReflectionTestUtils.setField(constants, "orderCredentials", val);
		System.out.println("String: " + Constants.getPlainPhoneNo("+918783267890"));
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
		salesOrder.setGrandTotal(new BigDecimal(10));
		salesOrder.setWmsStatus(new Integer(1));
		salesOrder.setAmstorecreditAmount(new BigDecimal(10));
		salesOrder.setStoreToBaseRate(new BigDecimal(1));
		salesOrder.setAmstorecreditAmount(new BigDecimal(1));
		SalesOrderPayment pay = new SalesOrderPayment();
		pay.setMethod("payfort_fort_cc");
		Set<SalesOrderPayment> payitem = new HashSet<>();
		payitem.add(pay);
		salesOrder.setSalesOrderPayment(payitem);
		SalesOrderItem items = new SalesOrderItem();
		items.setSku("01");
		items.setItemId(1);
		items.setProductType("jcsdc");
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

	private void setStaticData() {
		// TODO Auto-generated method stub
		ReflectionTestUtils.setField(constants, "IS_JWT_TOKEN_ENABLE", true);
		ReflectionTestUtils.setField(orderWriteController, "jwtFlag", "1");
		ReflectionTestUtils.setField(orderWriteController, "salesOrderCancelService", salesOrderCancelService);
		ReflectionTestUtils.setField(orderWriteController, "salesOrderRMAService", salesOrderRMAService);
		ReflectionTestUtils.setField(salesOrderCancelService, "paymentDtfRefundHelper", paymentDtfRefundHelper);
		ReflectionTestUtils.setField(salesOrderCancelService, "refundHelper", refundHelpermock);
//		ReflectionTestUtils.setField(salesOrderCancelService, "orderHelperV2", orderHelperv2);
		ReflectionTestUtils.setField(salesOrderCancelService, "orderHelper", orderHelper);
		ReflectionTestUtils.setField(orderWriteController, "salesOrderServiceV2", salesOrderServiceV2);
		ReflectionTestUtils.setField(orderWriteController, "salesOrderServiceV3", salesOrderServiceV3);
		ReflectionTestUtils.setField(salesOrderServiceV3, "orderHelper", orderHelper);

		ReflectionTestUtils.setField(salesOrderRMAService, "rmaHelper", rmaHelper);
		ReflectionTestUtils.setField(rmaHelper, "orderHelper", orderHelperMock);
		ReflectionTestUtils.setField(rmaHelper, "env", "dev");
		ReflectionTestUtils.setField(salesOrderRMAService, "configService", configService);
		ReflectionTestUtils.setField(salesOrderRMAService, "mulinHelper", mulinHelper);
		ReflectionTestUtils.setField(salesOrderRMAService, "omsorderentityConverter", omsorderentityConverter);
		ReflectionTestUtils.setField(salesOrderServiceV2, "externalQuoteHelper", externalQuoteHelper);

		ReflectionTestUtils.setField(salesOrderService, "orderHelper", orderHelperMock);
		ReflectionTestUtils.setField(salesOrderService, "omsorderentityConverter", omsorderentityConverter);
		ReflectionTestUtils.setField(salesOrderService, "getOrderCount", getOrderCount);
		ReflectionTestUtils.setField(getOrderList, "orderHelper", orderHelper);
		ReflectionTestUtils.setField(getFailedOrderList, "orderHelper", orderHelper);
		ReflectionTestUtils.setField(getOrderCount, "orderHelper", orderHelper);
		ReflectionTestUtils.setField(constants, "storesList", storeList);
	}
}