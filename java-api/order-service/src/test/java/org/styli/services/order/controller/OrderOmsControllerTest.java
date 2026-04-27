package org.styli.services.order.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;


import java.io.IOException;
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

import javax.servlet.http.HttpServletRequest;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.converter.OmsorderentityConverter;
import org.styli.services.order.converter.OrderEntityConverter;
import org.styli.services.order.db.product.config.firebase.FirebaseAuthentication;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.helper.MulinHelper;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.helper.OrderpushHelper;
import org.styli.services.order.helper.PaymentDtfHelper;
import org.styli.services.order.helper.PaymentRefundHelper;
import org.styli.services.order.helper.RefundHelper;
import org.styli.services.order.jwt.security.jwtsecurity.model.JwtUser;
import org.styli.services.order.jwt.security.jwtsecurity.security.JwtValidator;
import org.styli.services.order.model.SalesOrder.SalesCreditmemo;
import org.styli.services.order.model.rma.AmastyRmaReason;
import org.styli.services.order.model.rma.AmastyRmaReasonStore;
import org.styli.services.order.model.rma.AmastyRmaRequest;
import org.styli.services.order.model.rma.AmastyRmaRequestItem;
import org.styli.services.order.model.rma.AmastyRmaStatus;
import org.styli.services.order.model.rma.AmastyRmaStatusStore;
import org.styli.services.order.model.rma.AmastyRmaTracking;
import org.styli.services.order.model.rma.AmastyStoreCredit;
import org.styli.services.order.model.rma.AmastyStoreCreditHistory;
import org.styli.services.order.model.sales.SalesInvoice;
import org.styli.services.order.model.sales.SalesInvoiceItem;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderAddress;
import org.styli.services.order.model.sales.SalesOrderGrid;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SalesOrderPayment;
import org.styli.services.order.model.sales.SalesShipment;
import org.styli.services.order.model.sales.SalesShipmentItem;
import org.styli.services.order.model.sales.SalesShipmentTrack;
import org.styli.services.order.model.sales.StatusChangeHistory;
import org.styli.services.order.pojo.*;
import org.styli.services.order.pojo.mulin.Attribute;
import org.styli.services.order.pojo.mulin.GetProductsBySkuResponse;
import org.styli.services.order.pojo.mulin.ProductName;
import org.styli.services.order.pojo.mulin.ProductResponseBody;
import org.styli.services.order.pojo.mulin.Variant;
import org.styli.services.order.pojo.oms.BankSubmitFormRequest;
import org.styli.services.order.pojo.oms.BankSwiftCode;
import org.styli.services.order.pojo.oms.BankSwiftCodeMapperResponse;
import org.styli.services.order.pojo.order.AddStoreCreditRequest;
import org.styli.services.order.pojo.order.RMAOrderInitV2ResponseDTO;
import org.styli.services.order.pojo.order.RMAOrderItemV2Request;
import org.styli.services.order.pojo.order.RMAOrderV2Request;
import org.styli.services.order.pojo.order.RMAUpdateItemV2Request;
import org.styli.services.order.pojo.order.RMAUpdateV2Request;
import org.styli.services.order.pojo.order.StoreCredit;
import org.styli.services.order.pojo.recreate.RecreateOrder;
import org.styli.services.order.pojo.recreate.RecreateOrderResponseDTO;
import org.styli.services.order.pojo.request.PaymentCodeENUM;
import org.styli.services.order.pojo.request.Order.OmsOrderListRequest;
import org.styli.services.order.pojo.request.Order.OrderStoreCreditRequest;
import org.styli.services.order.pojo.request.Order.OrderViewRequest;
import org.styli.services.order.pojo.request.Order.OrderupdateRequest;
import org.styli.services.order.pojo.response.*;
import org.styli.services.order.pojo.response.Order.CreditHistoryResponse;
import org.styli.services.order.pojo.response.Order.CustomerOrdersResponseDTO;
import org.styli.services.order.pojo.response.Order.OmsOrderresponsedto;
import org.styli.services.order.pojo.response.Order.OrderResponseDTO;
import org.styli.services.order.pojo.response.V3.GetInvoiceV3Response;
import org.styli.services.order.pojo.response.V3.GetShipmentV3Response;
import org.styli.services.order.pojo.response.V3.Meta;
import org.styli.services.order.pojo.response.V3.NavikResponse;
import org.styli.services.order.pojo.response.V3.Result;
import org.styli.services.order.repository.SalesOrder.*;
import org.styli.services.order.repository.SalesShipmentRepository;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.repository.Rma.AmastyRmaReasonRepository;
import org.styli.services.order.repository.Rma.AmastyRmaRequestItemRepository;
import org.styli.services.order.repository.Rma.AmastyRmaRequestRepository;
import org.styli.services.order.repository.Rma.AmastyRmaStatusRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditHistoryRepository;
import org.styli.services.order.repository.Rma.AmastyStoreCreditRepository;
import org.styli.services.order.service.ConfigService;
import org.styli.services.order.service.RedisService;
import org.styli.services.order.service.impl.*;
import org.styli.services.order.service.impl.child.GetOrderById;
import org.styli.services.order.service.impl.child.GetOrderList;
import org.styli.services.order.service.impl.child.OrderShipmentHelper;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.consulValues.ConsulValues;
import org.styli.services.order.utility.consulValues.DeleteCustomer;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@SpringBootTest(classes = { EasControllerTest.class })
public class OrderOmsControllerTest extends AbstractTestNGSpringContextTests {
	private org.styli.services.order.model.Customer.CustomerEntity customerEntity;
	@InjectMocks
	OrderOmsController orderOmsController;
	@InjectMocks
	OmsOrderupdateresponse omsOrderupdateresponse;
	@InjectMocks
	RefundHelper refundHelper;
	@InjectMocks
	OrderShipmentHelper orderShipmentHelper;
	@InjectMocks
	private org.styli.services.order.utility.Constants constants;
	@Mock
	JwtValidator validator;
	@Mock
	ShipmentTrackerRepository shipmentTrackerRepository;
	@Mock
	SubSalesOrderItemRepository subSalesOrderItemRepository;
	@InjectMocks
	ConfigServiceImpl configService;

	@InjectMocks
	OrderHelper orderHelper;
	@Mock
	AmastyRmaStatusRepository amastyRmaStatusRepository;
	@InjectMocks
	CommonServiceImpl commonService;
	@Mock
	AmastyRmaRequestItemRepository amastyRmaRequestItemRepository;
	@Mock
	OrderHelper orderHelpermock;

	@InjectMocks
	PaymentDtfHelper paymentDtfHelper;
	@InjectMocks
	SalesOrderServiceImpl salesOrderService;
	@InjectMocks
	GetOrderList getOrderList;
	@InjectMocks
	GetOrderById getOrderById;
	@InjectMocks
	OrderEntityConverter orderEntityConverter;
	@Mock
	SalesOrderStatusHistoryRepository salesOrderStatusHistoryRepository;

	@Mock
	AmastyStoreCreditHistoryRepository amastyStoreCreditHistoryRepository;
	@Mock
	SalesCreditmemoRepository salesCreditmemoRepository;
	@Mock
	SalesOrderStatusLabelRepository salesOrderStatusLabelRepository;
	@Mock
	ProxyOrderRepository proxyOrderRepository;
	@Mock
	AmastyRmaReasonRepository amastyRmaReasonRepository;
	@Mock
	SalesOrderGridRepository salesOrderGridRepository;
	@Mock
	SalesOrderRepository salesOrderRepository;
	@Mock
	AmastyRmaRequestRepository amastyRmaRequestRepository;
	@Mock
	ReturnShipmentTrackerRepository returnShipmentTrackerRepository;
	@InjectMocks
	OmsorderentityConverter omsorderentityConverter;
	@Mock
	PaymentRefundHelper paymentDtfRefundHelper;
	@InjectMocks
	StaticComponents staticComponents;
	@InjectMocks
	OmsOrderresponsedto omsOrderresponsedto;
	@Mock
	StatusChaneHistoryRepository statusChaneHistoryRepository;
	@Mock
	KafkaServiceImpl kafkaService;
	@Mock
	SalesInvoiceRepository salesInvoiceRepository;
	@Mock
	KafkaTemplate<String, Object> kafkaTemplate;
	@InjectMocks
	SalesOrderServiceV2Impl salesOrderServiceV2;

	@InjectMocks
	SalesOrderServiceV3Impl salesOrderServiceV3;
	@InjectMocks
	SalesOrderRMAServiceImpl salesOrderRMAService;

	Map<String, String> requestHeader;
	private List<Stores> storeList;
	SalesOrder salesOrder;
	OmsOrderListRequest request;
	@Mock
	FirebaseAuthentication FirebaseAuthentication;
	@InjectMocks
	private MulinHelper mulinHelper;
	@Mock
	private AmastyStoreCreditRepository amastyStoreCreditRepository;
	@Mock
	private RestTemplate restTemplate;
	@Mock
	private RedisService redisService;

	@Mock
	private EASServiceImpl eASServiceImpl;

	@Mock
	SalesShipmentRepository salesshipmentRepository;
	@Mock
	private SalesShipmentRepository salesShipmentRepository;
	@InjectMocks
	private OrderpushHelper orderpushHelper;
	@InjectMocks
	private SalesOrderCustomerServiceImpl salesOrderCustomerService;
	private SalesCreditmemo memo;
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
	public void orderListAllTest() throws IOException {
		setStaticData();
		setorderCred();
		String requestData = new String(Files.readAllBytes(Paths.get("src/test/resources/orderlistreq.json")));
		String reponseData = new String(Files.readAllBytes(Paths.get("src/test/resources/orderlistrespo.json")));
		Gson g = new Gson();
		request = g.fromJson(requestData, OmsOrderListRequest.class);
		request.setQuery("select all from table");
		SalesOrderGrid customerOrders = g.fromJson(reponseData, SalesOrderGrid.class);

		Page<SalesOrderGrid> page = new PageImpl<>(Arrays.asList(customerOrders));
		when(salesOrderGridRepository.findOmsOrderQueryDetails(any(), anyString(), any(), any(), any(), any())).thenReturn(page);
		AuthenticationtestImpl auth = new AuthenticationtestImpl();
		SecurityContextHolder.getContext().setAuthentication(auth);
		CustomerOrdersResponseDTO respo = orderOmsController.orderListAll(requestHeader, request, null);
		assertTrue(respo.getStatus());
		assertEquals(respo.getStatusCode(), "200");

	}

	@Test
	public void orderListAllfilterTest() throws IOException {
		setStaticData();
		setorderCred();
		String requestData = new String(
				Files.readAllBytes(Paths.get("src/test/resources/orderlistreqwithfilter.json")));
		String reponseData = new String(Files.readAllBytes(Paths.get("src/test/resources/orderlistrespo.json")));
		Gson g = new Gson();
		request = g.fromJson(requestData, OmsOrderListRequest.class);
		SalesOrderGrid customerOrders = g.fromJson(reponseData, SalesOrderGrid.class);

		Page<SalesOrderGrid> page = new PageImpl<>(Arrays.asList(customerOrders));
		when(salesOrderGridRepository.findOmsOrderQueryDetails(any(), anyString(), any(), any(), any(), any())).thenReturn(page);
		AuthenticationtestImpl auth = new AuthenticationtestImpl();
		SecurityContextHolder.getContext().setAuthentication(auth);
		CustomerOrdersResponseDTO respo = orderOmsController.orderListAll(requestHeader, request, null);
		assertFalse(respo.getStatus());

	}

	@Test
	public void orderDetailsTest() throws IOException {
		setStaticData();
		setorderCred();
		setSalseOrderData();
		String reponseData = new String(Files.readAllBytes(Paths.get("src/test/resources/orderlistrespo.json")));
		Gson g = new Gson();
		OrderViewRequest req = new OrderViewRequest();
		req.setOrderId(1);
		SalesOrderGrid customerOrders = g.fromJson(reponseData, SalesOrderGrid.class);
		when(salesOrderRepository.findByEntityId(anyInt())).thenReturn(salesOrder);
		Page<SalesOrderGrid> page = new PageImpl<>(Arrays.asList(customerOrders));
		when(salesOrderGridRepository.findOmsOrderQueryDetails(any(), anyString(), any(), any(), any(), any())).thenReturn(page);
		AuthenticationtestImpl auth = new AuthenticationtestImpl();
		SecurityContextHolder.getContext().setAuthentication(auth);
		GetProductsBySkuResponse getProductsBySkuResponse = getMulinData();

		ResponseEntity<GetProductsBySkuResponse> response = new ResponseEntity<>(HttpStatus.OK)
				.ok(getProductsBySkuResponse);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(GetProductsBySkuResponse.class))).thenReturn(response);

		CustomerUpdateProfileResponse body = new CustomerUpdateProfileResponse();
		CustomerProfileResponse cus = new CustomerProfileResponse();
		Customer customer = new Customer();
		customer.setCustomerId(1);
		cus.setCustomer(customer);
		body.setResponse(cus);
		ResponseEntity<CustomerUpdateProfileResponse> eresponse = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(CustomerUpdateProfileResponse.class))).thenReturn(eresponse);

		OmsOrderresponsedto respo = orderOmsController.orderDetails(requestHeader, req, "token");
		assertTrue(respo.getStatus());
		assertEquals(respo.getStatusCode(), "200");

	}

	@Test
	public void orderInvoiceDetailsTest() throws IOException {
		setStaticData();
		setorderCred();
		setSalseOrderData();
		addInvoiceData();
		// prepareOrderConstant();
		String reponseData = new String(Files.readAllBytes(Paths.get("src/test/resources/orderlistrespo.json")));
		Gson g = new Gson();
		OrderViewRequest req = new OrderViewRequest();
		req.setOrderId(1);
		SalesOrderGrid customerOrders = g.fromJson(reponseData, SalesOrderGrid.class);
		when(salesOrderRepository.findByEntityId(anyInt())).thenReturn(salesOrder);
		Page<SalesOrderGrid> page = new PageImpl<>(Arrays.asList(customerOrders));
		when(salesOrderGridRepository.findOmsOrderQueryDetails(any(), anyString(), any(), any(), any(), any())).thenReturn(page);
		AuthenticationtestImpl auth = new AuthenticationtestImpl();
		SecurityContextHolder.getContext().setAuthentication(auth);
		GetProductsBySkuResponse getProductsBySkuResponse = getMulinData();

		ResponseEntity<GetProductsBySkuResponse> response = new ResponseEntity<>(HttpStatus.OK)
				.ok(getProductsBySkuResponse);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(GetProductsBySkuResponse.class))).thenReturn(response);

		CustomerUpdateProfileResponse body = new CustomerUpdateProfileResponse();
		CustomerProfileResponse cus = new CustomerProfileResponse();
		Customer customer = new Customer();
		customer.setCustomerId(1);
		cus.setCustomer(customer);
		body.setResponse(cus);
		ResponseEntity<CustomerUpdateProfileResponse> eresponse = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(CustomerUpdateProfileResponse.class))).thenReturn(eresponse);

		OmsOrderresponsedto respo = orderOmsController.orderInvoiceDetails(requestHeader, req, null, "token");
		assertTrue(respo.getStatus());
		assertEquals(respo.getStatusCode(), "200");
	}

	@Test
	public void updateOrderStatusTest() throws IOException {
		setStaticData();
		setorderCred();
		setSalseOrderData();
		addInvoiceData();
		String reponseData = new String(Files.readAllBytes(Paths.get("src/test/resources/orderlistrespo.json")));
		Gson g = new Gson();
		OrderupdateRequest req = new OrderupdateRequest();
		req.setOrderId(1);
		SalesOrderGrid customerOrders = g.fromJson(reponseData, SalesOrderGrid.class);
		when(salesOrderRepository.findByEntityId(anyInt())).thenReturn(salesOrder);
		when(paymentDtfRefundHelper.getCancelAmount(any(), any(), any())).thenReturn(new BigDecimal(1));
		when(paymentDtfRefundHelper.cancelPercentageCalculation(any(), any(), any(), any(), anyBoolean(), anyString(),
				any())).thenReturn(new BigDecimal(1));
		when(paymentDtfRefundHelper.getCancelledStoreCredit(any(), any(), any(), any(), anyBoolean(), any()))
				.thenReturn(new BigDecimal(1));

		Page<SalesOrderGrid> page = new PageImpl<>(Arrays.asList(customerOrders));
		when(salesOrderGridRepository.findOmsOrderQueryDetails(any(), anyString(), any(), any(), any(), any())).thenReturn(page);
		AuthenticationtestImpl auth = new AuthenticationtestImpl();
		SecurityContextHolder.getContext().setAuthentication(auth);
		GetProductsBySkuResponse getProductsBySkuResponse = getMulinData();

		ResponseEntity<GetProductsBySkuResponse> response = new ResponseEntity<>(HttpStatus.OK)
				.ok(getProductsBySkuResponse);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(GetProductsBySkuResponse.class))).thenReturn(response);

		InventoryMetaData meta = new InventoryMetaData();
		meta.setStatus(true);
		InventoryBlockResponse body = new InventoryBlockResponse();
		body.setMeta(meta);
		body.setResponse("response");
		ResponseEntity<InventoryBlockResponse> iresponse = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(InventoryBlockResponse.class))).thenReturn(iresponse);

		OmsOrderupdateresponse respo = orderOmsController.updateOrderStatus(requestHeader, req, null);
		assertTrue(respo.getStatus());
		assertEquals(respo.getStatusCode(), "200");
	}

	@Test
	public void orderShippingDetailsTest() throws IOException {
		setStaticData();
		setorderCred();
		setSalseOrderData();
		// addInvoiceData();
		addShipmentData();
//		prepareOrderConstant();
		String reponseData = new String(Files.readAllBytes(Paths.get("src/test/resources/orderlistrespo.json")));
		Gson g = new Gson();
		OrderViewRequest req = new OrderViewRequest();
		req.setOrderId(1);
		SalesOrderGrid customerOrders = g.fromJson(reponseData, SalesOrderGrid.class);
		when(salesOrderRepository.findByEntityId(anyInt())).thenReturn(salesOrder);
		Page<SalesOrderGrid> page = new PageImpl<>(Arrays.asList(customerOrders));
		when(salesOrderGridRepository.findOmsOrderQueryDetails(any(), anyString(), any(), any(), any(), any())).thenReturn(page);
		AuthenticationtestImpl auth = new AuthenticationtestImpl();
		SecurityContextHolder.getContext().setAuthentication(auth);
		GetProductsBySkuResponse getProductsBySkuResponse = getMulinData();

		ResponseEntity<GetProductsBySkuResponse> response = new ResponseEntity<>(HttpStatus.OK)
				.ok(getProductsBySkuResponse);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(GetProductsBySkuResponse.class))).thenReturn(response);

		CustomerUpdateProfileResponse body = new CustomerUpdateProfileResponse();
		CustomerProfileResponse cus = new CustomerProfileResponse();
		Customer customer = new Customer();
		customer.setCustomerId(1);
		cus.setCustomer(customer);
		body.setResponse(cus);
		ResponseEntity<CustomerUpdateProfileResponse> eresponse = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(CustomerUpdateProfileResponse.class))).thenReturn(eresponse);

		OmsOrderresponsedto respo = orderOmsController.orderShippingDetails(requestHeader, req, null);
		assertTrue(respo.getStatus());
		assertEquals(respo.getStatusCode(), "200");
	}

//	@Test
//	public void updateOrderAddressTest() throws IOException {
//		setStaticData();
//		setorderCred();
//		setSalseOrderData();
//		addAddressData();
//		String reponseData = new String(Files.readAllBytes(Paths.get("src/test/resources/orderlistrespo.json")));
//		Gson g = new Gson();
//		OrderupdateRequest req = new OrderupdateRequest();
//		req.setOrderId(1);
//		req.setOrderAddressId(1);
//		SalesOrderGrid customerOrders = g.fromJson(reponseData, SalesOrderGrid.class);
//		when(salesOrderRepository.findByEntityId(anyInt())).thenReturn(salesOrder);
//		when(paymentDtfRefundHelper.getCancelAmount(any(), any(), any())).thenReturn(new BigDecimal(1));
//		when(paymentDtfRefundHelper.cancelPercentageCalculation(any(), any(), any(), any(), anyBoolean(), anyString(),
//				any())).thenReturn(new BigDecimal(1));
//		when(paymentDtfRefundHelper.getCancelledStoreCredit(any(), any(), any(), any(), anyBoolean(), any()))
//				.thenReturn(new BigDecimal(1));
//
//		Page<SalesOrderGrid> page = new PageImpl<>(Arrays.asList(customerOrders));
//		when(salesOrderGridRepository.findOmsOrderQueryDetails(any(), anyString(), any(), any(), any())).thenReturn(page);
//		AuthenticationtestImpl auth = new AuthenticationtestImpl();
//		SecurityContextHolder.getContext().setAuthentication(auth);
//		GetProductsBySkuResponse getProductsBySkuResponse = getMulinData();
//
//		ResponseEntity<GetProductsBySkuResponse> response = new ResponseEntity<>(HttpStatus.OK)
//				.ok(getProductsBySkuResponse);
//
//		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
//				Mockito.any(HttpEntity.class), Mockito.eq(GetProductsBySkuResponse.class))).thenReturn(response);
//
//		InventoryMetaData meta = new InventoryMetaData();
//		meta.setStatus(true);
//		InventoryBlockResponse body = new InventoryBlockResponse();
//		body.setMeta(meta);
//		body.setResponse("response");
//		ResponseEntity<InventoryBlockResponse> iresponse = new ResponseEntity<>(HttpStatus.OK).ok(body);
//
//		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
//				Mockito.any(HttpEntity.class), Mockito.eq(InventoryBlockResponse.class))).thenReturn(iresponse);
//
//		OmsOrderupdateresponse respo = orderOmsController.updateOrderAddress(requestHeader, req, null);
//		assertTrue(respo.getStatus());
//		assertEquals(respo.getStatusCode(), "200");
//	}

	@Test
	public void createShipmentTest() throws IOException {
		setStaticData();
		setorderCred();
		setSalseOrderData();
		addAddressData();
		// addInvoiceData();
		String reponseData = new String(Files.readAllBytes(Paths.get("src/test/resources/orderlistrespo.json")));
		Gson g = new Gson();
		OrderViewRequest req = new OrderViewRequest();
		req.setOrderId(1);
		req.setOrderCode("1");
		salesOrder.setStatus("processing");
		salesOrder.setSubtotal(new BigDecimal(10));
		salesOrder.setBaseSubtotal(new BigDecimal(10));
		salesOrder.setGrandTotal(new BigDecimal(10));
		salesOrder.setBaseGrandTotal(new BigDecimal(10));
		salesOrder.setAmstorecreditAmount(new BigDecimal(10));
		salesOrder.setSubtotalInclTax(new BigDecimal(10));
		salesOrder.setBaseSubtotalInclTax(new BigDecimal(10));
		salesOrder.setTaxAmount(new BigDecimal(10));
		salesOrder.setBaseTaxAmount(new BigDecimal(10));
		salesOrder.setDiscountAmount(new BigDecimal(10));
		salesOrder.setBaseDiscountAmount(new BigDecimal(10));

		SalesOrderGrid customerOrders = g.fromJson(reponseData, SalesOrderGrid.class);

		AuthenticationtestImpl auth = new AuthenticationtestImpl();
		SecurityContextHolder.getContext().setAuthentication(auth);

		StatusChangeHistory statusChangeHistory = new StatusChangeHistory();
		SalesShipment saleship = new SalesShipment();
		saleship.setEntityId(1);

		SalesCreditmemo memo = new SalesCreditmemo();
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
		List<SalesCreditmemo> lstmemo = Arrays.asList(memo);
		when(salesOrderRepository.findByIncrementId(anyString())).thenReturn(salesOrder);
		when(statusChaneHistoryRepository.findByOrderId(anyString())).thenReturn(statusChangeHistory);
		when(salesshipmentRepository.saveAndFlush(any())).thenReturn(saleship);
		when(salesOrderGridRepository.findByEntityId(anyInt())).thenReturn(customerOrders);
		when(salesOrderRepository.saveAndFlush(any())).thenReturn(new SalesOrder());
		when(salesCreditmemoRepository.findByOrderId(anyInt())).thenReturn(lstmemo);
		InventoryMetaData meta = new InventoryMetaData();
		meta.setStatus(true);
		InventoryBlockResponse body = new InventoryBlockResponse();
		body.setMeta(meta);
		body.setResponse("response");
		ResponseEntity<InventoryBlockResponse> iresponse = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(InventoryBlockResponse.class))).thenReturn(iresponse);

		OmsOrderoutboundresponse respo = orderOmsController.createShipment(requestHeader, req, "token");
		assertTrue(respo.getStatus());
		assertEquals(respo.getStatusCode(), "200");
	}

	@Test
	public void createShipment2Test() throws IOException {
		setStaticData();
		setorderCred();
		setSalseOrderData();
		addAddressData();
		addInvoiceData();
		String reponseData = new String(Files.readAllBytes(Paths.get("src/test/resources/orderlistrespo.json")));
		Gson g = new Gson();
		OrderViewRequest req = new OrderViewRequest();
		req.setOrderId(1);
		req.setOrderCode("1");
		salesOrder.setStatus("processing");
		List<Integer> zatcaConst = Arrays.asList(1,3);
		ReflectionTestUtils.setField(constants, "zatcaFlag", zatcaConst);
		SalesOrderGrid customerOrders = g.fromJson(reponseData, SalesOrderGrid.class);

		AuthenticationtestImpl auth = new AuthenticationtestImpl();
		SecurityContextHolder.getContext().setAuthentication(auth);

		StatusChangeHistory statusChangeHistory = new StatusChangeHistory();
		SalesShipment saleship = new SalesShipment();
		saleship.setEntityId(1);

		SalesCreditmemo memo = new SalesCreditmemo();
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
		List<SalesCreditmemo> lstmemo = Arrays.asList(memo);
		when(salesOrderRepository.findByIncrementId(anyString())).thenReturn(salesOrder);
		when(statusChaneHistoryRepository.findByOrderId(anyString())).thenReturn(statusChangeHistory);
		when(salesshipmentRepository.saveAndFlush(any())).thenReturn(saleship);
		when(salesOrderGridRepository.findByEntityId(anyInt())).thenReturn(customerOrders);
		when(salesOrderRepository.saveAndFlush(any())).thenReturn(salesOrder);
		when(salesCreditmemoRepository.findByOrderId(anyInt())).thenReturn(lstmemo);
		InventoryMetaData meta = new InventoryMetaData();
		meta.setStatus(true);
		InventoryBlockResponse body = new InventoryBlockResponse();
		body.setMeta(meta);
		body.setResponse("response");
		ResponseEntity<InventoryBlockResponse> iresponse = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(InventoryBlockResponse.class))).thenReturn(iresponse);

		OmsOrderoutboundresponse respo = orderOmsController.createShipment(requestHeader, req, "token");
		assertTrue(respo.getStatus());
		assertEquals(respo.getStatusCode(), "200");
	}

	@Test
	public void storeCreditHistoryTest() {
		setStaticData();
		setorderCred();
		setSalseOrderData();

		AuthenticationtestImpl auth = new AuthenticationtestImpl();
		SecurityContextHolder.getContext().setAuthentication(auth);
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
		CreditHistoryResponse respo = orderOmsController.storeCreditHistory(requestHeader, req, null);
		assertTrue(respo.getStatus());
		assertEquals(respo.getStatusCode(), "200");
	}

	@Test
	public void addStoreCreditTest() {
		setStaticData();
		setorderCred();
		setSalseOrderData();

		AuthenticationtestImpl auth = new AuthenticationtestImpl();
		SecurityContextHolder.getContext().setAuthentication(auth);
		AddStoreCreditRequest req = new AddStoreCreditRequest();
		StoreCredit strc = new StoreCredit();
		strc.setStore("1");
		strc.setStoreId(1);
		strc.setEmailId("test@mailinator.com");
		strc.setStoreCredit(new BigDecimal(10));

		req.setStoreCredits(Arrays.asList(strc));

		OrderKeyDetails keydetail = new OrderKeyDetails();
		keydetail.setMaximumOrderPedningOrderThreshold(10);
		keydetail.setStyliCreditBulkUpdateAmount(null);
		GetOrderConsulValues val = new GetOrderConsulValues();
		val.setOrderDetails(keydetail);
		// val.setExternalAuthEnable(false);
		// val.setInternalAuthEnable(false);
//		org.styli.services.order.utility.Constants.setOrderCredentials(new Gson().toJson(val).toString());
		ReflectionTestUtils.setField(constants, "orderCredentials", val);
		when(orderHelpermock.getCustomerDetails(any(), anyString())).thenReturn(customerEntity);
		AmastyStoreCreditHistory ash = new AmastyStoreCreditHistory();
		LocalDateTime currentTime = LocalDateTime.now();
		ash.setCreatedAt(Timestamp.valueOf(currentTime));
		ash.setAction(1);
		ash.setActionData("1");
		ash.setMessage("msg");

		AmastyStoreCredit asc = new AmastyStoreCredit();
		asc.setCustomerId(1);
		when(amastyStoreCreditRepository.findByCustomerId(anyInt())).thenReturn(Arrays.asList(asc));
		List<AmastyStoreCreditHistory> creditHistories = Arrays.asList(ash);
		when(amastyStoreCreditHistoryRepository.findByCustomerIdOrderByHistoryIdDesc(anyInt()))
				.thenReturn(creditHistories);
		AddStoreCreditResponse respo = orderOmsController.addStoreCredit(requestHeader, req, null);
		assertTrue(respo.getStatus());
		assertEquals(respo.getStatusCode(), "200");
	}

	@Test
	public void getShipmentV3Test() {
		setStaticData();
		setorderCred();
		setSalseOrderData();
		addInvoiceData();
		AuthenticationtestImpl auth = new AuthenticationtestImpl();
		SecurityContextHolder.getContext().setAuthentication(auth);

		SalesShipment saleship = new SalesShipment();
		saleship.setEntityId(1);
		when(salesOrderRepository.findByIncrementId(anyString())).thenReturn(salesOrder);
		when(salesShipmentRepository.findByIncrementId(anyString())).thenReturn(saleship);

		Meta meta = new Meta();
		meta.setStatus(200);
		meta.setSuccess(true);
		NavikResponse body = new NavikResponse();
		body.setMeta(meta);
		Result res = new Result();
		res.setCourier_name("alpha");
		body.setResult(res);
		ResponseEntity<NavikResponse> iresponse = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(NavikResponse.class))).thenReturn(iresponse);

		GetShipmentV3Response respo = orderOmsController.getShipmentV3(requestHeader, "1", "1", "token");
		assertNotNull(respo);
	}

	@Test
	public void getInvoiceV3Test() throws IOException {
		setStaticData();
		setorderCred();
		setSalseOrderData();
		addAddressData();
		addInvoiceData();
		String reponseData = new String(Files.readAllBytes(Paths.get("src/test/resources/orderlistrespo.json")));
		Gson g = new Gson();
		OrderViewRequest req = new OrderViewRequest();
		req.setOrderId(1);
		req.setOrderCode("1");
		salesOrder.setStatus("processing");

		SalesOrderGrid customerOrders = g.fromJson(reponseData, SalesOrderGrid.class);

		AuthenticationtestImpl auth = new AuthenticationtestImpl();
		SecurityContextHolder.getContext().setAuthentication(auth);

		when(salesOrderRepository.findByIncrementId(anyString())).thenReturn(salesOrder);
		when(salesOrderGridRepository.findByEntityId(anyInt())).thenReturn(customerOrders);
		when(salesOrderRepository.saveAndFlush(any())).thenReturn(salesOrder);
		InventoryMetaData meta = new InventoryMetaData();
		meta.setStatus(true);
		InventoryBlockResponse body = new InventoryBlockResponse();
		body.setMeta(meta);
		body.setResponse("response");
		ResponseEntity<InventoryBlockResponse> iresponse = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(InventoryBlockResponse.class))).thenReturn(iresponse);

		GetInvoiceV3Response respo = orderOmsController.getInvoiceV3(requestHeader, "1", "1", "token");
		assertNotNull(respo);
	}

	@Test
	public void rmaUpdateV2Test() throws IOException {
		setStaticData();
		setorderCred();
		setSalseOrderData();
		addAddressData();
		addInvoiceData();

		salesOrder.setStatus("processing");

		AuthenticationtestImpl auth = new AuthenticationtestImpl();
		SecurityContextHolder.getContext().setAuthentication(auth);

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
		RMAUpdateItemV2Request rmsItem = new RMAUpdateItemV2Request();
		rmsItem.setReasonId(1);
		rmsItem.setReturnQuantity(1);
		RMAUpdateV2Request req = new RMAUpdateV2Request();
		req.setCustomerId(1);
		req.setRequestId(1);
		req.setStoreId(1);
		req.setItems(Arrays.asList(rmsItem));
		OrderResponseDTO respo = orderOmsController.rmaUpdateV2(requestHeader, req, null);
		assertNotNull(respo);
	}

	@Test
	public void orderCancelTest() throws IOException {
		setStaticData();
		setorderCred();
		setSalseOrderData();

		when(salesOrderRepository.cancelledOrderforwmspush(any(), any())).thenReturn(Arrays.asList(salesOrder));

		ResponseEntity<Object> response = new ResponseEntity<>(HttpStatus.OK).ok("");
		Mockito.when(restTemplate.exchange(Mockito.any(URI.class), Mockito.eq(HttpMethod.PUT),
				Mockito.any(HttpEntity.class), Mockito.eq(Object.class))).thenReturn(response);
		OmsOrderresponsedto respo = orderOmsController.orderCancel(requestHeader, null, "token");
		assertNotNull(respo);
	}

	@Test
	public void orderPushTest() throws IOException {
		setStaticData();
		setorderCred();
		setSalseOrderData();

//		addAddressData();
//		addInvoiceData();
		when(salesOrderRepository.ordersforwmspush(any(), any())).thenReturn(Arrays.asList(salesOrder));
		OmsOrderresponsedto respo = orderOmsController.orderPush(requestHeader, null, "token");
		assertNotNull(respo);
	}

	ConfigService configService1 = Mockito.mock(ConfigService.class);
	@Test
	public void rmaOrderInitV2Test() throws IOException {
		// Now you can continue with your test case
		setStaticData();
		setorderCred();
		setSalseOrderData();


		when(configService1.getWebsiteRefundByStoreId(anyInt())).thenReturn(10.0);
		RMAOrderItemV2Request v2req = new RMAOrderItemV2Request();
		v2req.setParentOrderItemId(1);
		v2req.setReasonId(1);
		v2req.setReturnQuantity(1);
		List<RMAOrderItemV2Request> items = new ArrayList<>();
		items.add(v2req);
		AuthenticationtestImpl auth = new AuthenticationtestImpl();
		SecurityContextHolder.getContext().setAuthentication(auth);
		RMAOrderV2Request req = new RMAOrderV2Request();
		req.setItems(items);
		req.setCustomerId(1);
		req.setStoreId(1);
		req.setOrderId(1);

		when(salesOrderRepository.findByEntityIdAndCustomerId(anyInt(), anyInt())).thenReturn(salesOrder);

		GetProductsBySkuResponse body = new GetProductsBySkuResponse();
		body.setStatus(true);
		Map<String, ProductResponseBody> productsFromMulin = new HashMap<>();
		productsFromMulin.put("key", new ProductResponseBody());
		body.setResponse(productsFromMulin);
		ResponseEntity<GetProductsBySkuResponse> iresponse = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(GetProductsBySkuResponse.class))).thenReturn(iresponse);

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
		when(subSalesOrderItemRepository.findDiscountByParentOrderIdAndMainItemId(anyInt(), anyInt())).thenReturn(BigDecimal.valueOf(0.0));

		AmastyStoreCredit amastyStoreCredit = new AmastyStoreCredit();
		when(amastyStoreCreditRepository.findByCustomerId(anyInt())).thenReturn(Arrays.asList(amastyStoreCredit));

		AmastyRmaTracking amastyRmaTracking = new AmastyRmaTracking();
		amastyRmaTracking.setRequestId(1);
		amastyRmaTracking.setTrackingId(1);
//		when(amastyRmaTrackingRepository.findByRequestId(anyInt())).thenReturn(Arrays.asList(amastyRmaTracking));
		AmastyRmaStatusStore amastyRmaStatusStore = new AmastyRmaStatusStore();
		amastyRmaStatusStore.setStoreId(1);
		amastyRmaStatusStore.setLabel("label");
		Set<AmastyRmaStatusStore> set = new HashSet<>();
		set.add(amastyRmaStatusStore);
		AmastyRmaStatus amastyRmaStatus = new AmastyRmaStatus();
		amastyRmaStatus.setAmastyRmaStatusStores(set);
//		when(amastyRmaStatusRepository.findByStatusId(anyInt())).thenReturn(amastyRmaStatus);
		when(salesCreditmemoRepository.findByRmaNumber(anyString())).thenReturn(null);

//
		ConsulValues orderConsulValues = new ConsulValues();
		orderConsulValues.setReturnShortSinglepickSms("true");
		when(amastyRmaRequestItemRepository.findByRequestItemId(anyInt()))
				.thenReturn(Arrays.asList(amastyRmaRequestItem));
		when(amastyRmaRequestItemRepository.findByOrderItemId(anyInt()))
				.thenReturn(Arrays.asList(amastyRmaRequestItem));
		AmastyRmaReason amastyRmaReasons = new AmastyRmaReason();
		amastyRmaReasons.setTitle("return");
		AmastyRmaReasonStore s = new AmastyRmaReasonStore();
		s.setLabel("label");
		s.setStoreId(1);
		Set<AmastyRmaReasonStore> amastyRmaReasonStores = new HashSet<>();
		amastyRmaReasonStores.add(s);
		amastyRmaReasons.setAmastyRmaReasonStores(amastyRmaReasonStores);
		when(amastyRmaReasonRepository.findByStatusAndIsDeletedOrderByPositionAsc(1, 0))
				.thenReturn(Arrays.asList(amastyRmaReasons));
		;
		RMAOrderInitV2ResponseDTO respo = orderOmsController.rmaOrderInitV2(requestHeader, req, null);
		assertNotNull(respo);
	}

	@Test
	public void payfortRefundTest() throws IOException {
		setStaticData();
		setorderCred();
		setSalseOrderData();

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


		memo = new SalesCreditmemo();
		memo.setSubtotal(new BigDecimal(10));
		when(salesCreditmemoRepository.findByRmaNumber(anyString())).thenReturn(Arrays.asList(memo));



		when(amastyRmaRequestRepository.findByRmaIncIdAndOrderId(anyString(), anyInt()))
				.thenReturn(amastyRmaRequest);
		payFortRefund payFortRefund = new payFortRefund();
		payFortRefund.setReturnIncrementId("1");
		payFortRefund.setOrderId(1);
		when(salesOrderRepository.findByEntityId(anyInt())).thenReturn(salesOrder);
		CalculateRefundAmountResponse calculateRefundAmountResponse= new CalculateRefundAmountResponse();
		calculateRefundAmountResponse.setAfterRefundOnlineAmount("10");
		calculateRefundAmountResponse.setBeforeRefundOnlineAmount("10");
		calculateRefundAmountResponse.setAfterCreditAmount("10");
		calculateRefundAmountResponse.setBeforeCreditAmount("10");
		calculateRefundAmountResponse.setAfterAmastyCreditAmount("10");
		calculateRefundAmountResponse.setBeforeAmastyCreditAmount("10");
		calculateRefundAmountResponse.setAfterReturnEasCoinValue("5");
		calculateRefundAmountResponse.setBeforeReturnEasCoinValue("5");

		when(paymentDtfRefundHelper.calculaterefundamount(any(), anyString(), any(), any(), any(), any(), anyObject(), anyString())).thenReturn(calculateRefundAmountResponse);
		RefundPaymentRespone rpresponse = new RefundPaymentRespone();
		rpresponse.setStatus(true);

		when(paymentDtfRefundHelper.payfortRefundcall(any(), any(), any(), any())).thenReturn(rpresponse);
		for(SalesOrderPayment ss:salesOrder.getSalesOrderPayment()) {
			ss.setMethod(null);
		}
		RefundPaymentRespone respo = orderOmsController.payfortRefund(requestHeader,payFortRefund, null, "token");
		assertNotNull(respo);
	}

	@Test
	public void recreateInitTest() throws IOException {
		setStaticData();
		setorderCred();
		setSalseOrderDatarecreate();
		RMAOrderItemV2Request v2req = new RMAOrderItemV2Request();
		v2req.setParentOrderItemId(1);
		v2req.setReasonId(1);
		v2req.setReturnQuantity(1);
		List<RMAOrderItemV2Request> items = new ArrayList<>();
		items.add(v2req);
		AuthenticationtestImpl auth = new AuthenticationtestImpl();
		SecurityContextHolder.getContext().setAuthentication(auth);
		RecreateOrder req = new RecreateOrder();
		req.setOrderId(1);
		req.setPaymentMethod(PaymentCodeENUM.MD_PAYFORT);
		List<Integer> lst = new ArrayList<>();
		lst.add(1);
		req.setRequestedItems(lst);
		req.setStoreCreditApplied(new BigDecimal(10));
		Map<Integer, Integer> requestedItemsQty = new HashMap();
		requestedItemsQty.put(new Integer(1), new Integer(10));
		req.setRequestedItemsQty(requestedItemsQty);
		salesOrder.setStatus("closed");
		salesOrder.setShippingAmount(new BigDecimal(10));
		salesOrder.setCashOnDeliveryFee(new BigDecimal(10));
		when(salesOrderRepository.findByEntityIdAndCustomerId(anyInt(), anyInt())).thenReturn(salesOrder);

		GetProductsBySkuResponse body = new GetProductsBySkuResponse();
		body.setStatus(true);
		Map<String, ProductResponseBody> productsFromMulin = new HashMap<>();
		productsFromMulin.put("key", new ProductResponseBody());
		body.setResponse(productsFromMulin);
		ResponseEntity<GetProductsBySkuResponse> iresponse = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(GetProductsBySkuResponse.class))).thenReturn(iresponse);

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
		when(salesOrderRepository.findByEntityId(anyInt())).thenReturn(salesOrder);
		when(salesOrderRepository.findByEntityIdAndCustomerId(anyInt(), anyInt())).thenReturn(salesOrder);

		AmastyStoreCredit amastyStoreCredit = new AmastyStoreCredit();
		when(amastyStoreCreditRepository.findByCustomerId(anyInt())).thenReturn(Arrays.asList(amastyStoreCredit));

		AmastyRmaTracking amastyRmaTracking = new AmastyRmaTracking();
		amastyRmaTracking.setRequestId(1);
		amastyRmaTracking.setTrackingId(1);
//		when(amastyRmaTrackingRepository.findByRequestId(anyInt())).thenReturn(Arrays.asList(amastyRmaTracking));
		AmastyRmaStatusStore amastyRmaStatusStore = new AmastyRmaStatusStore();
		amastyRmaStatusStore.setStoreId(1);
		amastyRmaStatusStore.setLabel("label");
		Set<AmastyRmaStatusStore> set = new HashSet<>();
		set.add(amastyRmaStatusStore);
		AmastyRmaStatus amastyRmaStatus = new AmastyRmaStatus();
		amastyRmaStatus.setAmastyRmaStatusStores(set);
//		when(amastyRmaStatusRepository.findByStatusId(anyInt())).thenReturn(amastyRmaStatus);
		when(salesCreditmemoRepository.findByRmaNumber(anyString())).thenReturn(null);

//
		ConsulValues orderConsulValues = new ConsulValues();
		orderConsulValues.setReturnShortSinglepickSms("true");
		when(amastyRmaRequestItemRepository.findByRequestItemId(anyInt()))
				.thenReturn(Arrays.asList(amastyRmaRequestItem));
		when(amastyRmaRequestItemRepository.findByOrderItemId(anyInt()))
				.thenReturn(Arrays.asList(amastyRmaRequestItem));
		AmastyRmaReason amastyRmaReasons = new AmastyRmaReason();
		amastyRmaReasons.setTitle("return");
		AmastyRmaReasonStore s = new AmastyRmaReasonStore();
		s.setLabel("label");
		s.setStoreId(1);
		Set<AmastyRmaReasonStore> amastyRmaReasonStores = new HashSet<>();
		amastyRmaReasonStores.add(s);
		amastyRmaReasons.setAmastyRmaReasonStores(amastyRmaReasonStores);
		when(amastyRmaReasonRepository.findByStatusAndIsDeletedOrderByPositionAsc(1, 0))
				.thenReturn(Arrays.asList(amastyRmaReasons));
		;
		RecreateOrderResponseDTO respo = orderOmsController.recreateInit(requestHeader, req, null);
		assertNotNull(respo);
	}

	@Test
	public void rmaAwbCreationTest() throws Exception {
		setStaticData();
		setorderCred();
		setSalseOrderData();
		AmastyRmaRequest amastyRmaRequest = new AmastyRmaRequest();
		amastyRmaRequest.setCustomerId(1);
		amastyRmaRequest.setStoreId(1);
		amastyRmaRequest.setOrderId(1);
		amastyRmaRequest.setRequestId(1);
		amastyRmaRequest.setStatus(1);
		amastyRmaRequest.setReturnType(2);
		AmastyRmaRequestItem amastyRmaRequestItem = new AmastyRmaRequestItem();
		amastyRmaRequestItem.setRequestId(1);
		amastyRmaRequestItem.setRequestItemId(1);
		amastyRmaRequestItem.setOrderItemId(1);
		Set<AmastyRmaRequestItem> amastyRmaRequestItems = new HashSet<>();
		amastyRmaRequestItems.add(amastyRmaRequestItem);
		amastyRmaRequest.setAmastyRmaRequestItems(amastyRmaRequestItems);

		when(amastyRmaRequestRepository.createReturnAwb(anyString(), anyString(), anyInt()))
				.thenReturn(Arrays.asList(amastyRmaRequest));
		when(amastyRmaRequestRepository.findByRmaIncId(any())).thenReturn(amastyRmaRequest);
		when(salesOrderRepository.findByEntityId(anyInt())).thenReturn(salesOrder);
		Meta meta = new Meta();
		meta.setStatus(200);
		meta.setSuccess(true);
		NavikResponse body = new NavikResponse();
		body.setMeta(meta);
		Result res = new Result();
		res.setCourier_name("alpha");
		res.setCourier_partner_id(1);
		res.setAlphaAwb("2324");
		res.setLabel("label");
		body.setResult(res);
		ResponseEntity<NavikResponse> iresponse = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(NavikResponse.class))).thenReturn(iresponse);

		GetShipmentV3Response respo = orderOmsController.rmaAwbCreation(requestHeader, "token");
		assertNotNull(respo);
	}

	@Test
	public void updateOrderCancelTest() throws Exception {
		setStaticData();
		setorderCred();
		setSalseOrderData();
		when(salesOrderRepository.updateOrdercancelforwmspush(any(), any())).thenReturn(Arrays.asList(salesOrder));
		OmsOrderresponsedto respo = orderOmsController.updateOrderCancel(requestHeader, null, "token");
		assertNotNull(respo);

	}

	@Test
	public void orderunholdTest() throws Exception {
		setStaticData();
		setorderCred();
		setSalseOrderData();
		ResponseEntity<Object> response = new ResponseEntity<>(HttpStatus.OK).ok("");
		Mockito.when(restTemplate.exchange(Mockito.any(URI.class), Mockito.eq(HttpMethod.PUT),
				Mockito.any(HttpEntity.class), Mockito.eq(Object.class))).thenReturn(response);
		when(salesOrderRepository.ordersHoldwmspush(any())).thenReturn(Arrays.asList(salesOrder));
		OmsOrderresponsedto respo = orderOmsController.orderunhold(requestHeader, null, "token");
		assertNotNull(respo);

	}

	@Test
	public void queryDtfTest() throws Exception {
		setStaticData();
		setorderCred();
		setSalseOrderData();
		ResponseEntity<Object> response = ResponseEntity.ok("");
		Mockito.when(restTemplate.exchange(Mockito.any(URI.class), Mockito.eq(HttpMethod.PUT),
				Mockito.any(HttpEntity.class), Mockito.eq(Object.class))).thenReturn(response);
		when(salesOrderRepository.findPaymentFailedOrders(anyInt(), anyInt())).thenReturn(Arrays.asList(salesOrder));
		RefundPaymentRespone respo = orderOmsController.queryDtf(requestHeader, "token");
		assertNotNull(respo);

	}

	@Test
	public void updateSalesOrdersWithCustomerIdTest() throws Exception {
		setStaticData();
		setorderCred();
		setSalseOrderData();
		String reponseData = new String(Files.readAllBytes(Paths.get("src/test/resources/orderlistrespo.json")));
		Gson g = new Gson();
		SalesOrderGrid customerOrders = g.fromJson(reponseData, SalesOrderGrid.class);

		when(salesOrderRepository.getAllOrdersForCustIdUpdate(anyInt())).thenReturn(Arrays.asList(salesOrder));
		when(salesOrderGridRepository.getAllOrdersForCustIdUpdate(anyInt())).thenReturn(null);
		orderOmsController.updateSalesOrdersWithCustomerId();
	}

	@Test
	void rmaAwbDropoffCreationTest() {
		setStaticData();
		setorderCred();
		setSalseOrderData();
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

		when(amastyRmaRequestRepository.createReturnDropOffAwb()).thenReturn(Arrays.asList(amastyRmaRequest));
		when(salesOrderRepository.findByEntityId(anyInt())).thenReturn(salesOrder);

		when(amastyRmaRequestRepository.createReturnAwb(anyString(), anyString(), anyInt()))
				.thenReturn(Arrays.asList(amastyRmaRequest));
		when(amastyRmaRequestRepository.findByRmaIncId(any())).thenReturn(amastyRmaRequest);
		when(salesOrderRepository.findByEntityId(anyInt())).thenReturn(salesOrder);
		Meta meta = new Meta();
		meta.setStatus(200);
		meta.setSuccess(true);
		NavikResponse body = new NavikResponse();
		body.setMeta(meta);
		Result res = new Result();
		res.setCourier_name("alpha");
		res.setCourier_partner_id(1);
		res.setAlphaAwb("2324");
		res.setLabel("label");
		body.setResult(res);
		ResponseEntity<NavikResponse> iresponse = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(NavikResponse.class))).thenReturn(iresponse);

		GetShipmentV3Response respo = orderOmsController.rmaAwbDropoffCreation(requestHeader);
		assertNotNull(respo);
	}

	@Test
	public void getBankSwiftCodesTest() throws Exception {
		setStaticData();
		setorderCred();
		setSalseOrderData();
		BankSwiftCodeMapperResponse respo = orderOmsController.bankSwiftCodeMapper(requestHeader);
		assertNotNull(respo);

	}

	@Test
	public void bankReturnFormSubmitTest() throws Exception {
		setAuthenticateData();
		setStaticData();
		setorderCred();
		setSalseOrderData();
		BankSubmitFormRequest req = new BankSubmitFormRequest();
		req.setStoreId(1);
		req.setAmount(new BigDecimal(10));
		req.setIban("SAertyuiopasdfghjklz(.*)");
		AmastyStoreCredit asc = new AmastyStoreCredit();
		asc.setCustomerId(1);
		asc.setReturnableAmount(new BigDecimal(10));
		asc.setStoreCredit(new BigDecimal(10));
		when(amastyStoreCreditRepository.findByCustomerId(anyInt())).thenReturn(Arrays.asList(asc));
		AmastyStoreCreditHistory ash = new AmastyStoreCreditHistory();
		LocalDateTime currentTime = LocalDateTime.now();
		ash.setCreatedAt(Timestamp.valueOf(currentTime));
		ash.setAction(1);
		ash.setActionData("1");
		ash.setMessage("msg");

		List<AmastyStoreCreditHistory> creditHistories = Arrays.asList(ash);
		when(amastyStoreCreditHistoryRepository.findByCustomerIdOrderByHistoryIdDesc(anyInt()))
				.thenReturn(creditHistories);
		when(orderHelpermock.getCustomerDetails(any(), any())).thenReturn(customerEntity);
		BankSwiftCodeMapperResponse body = new BankSwiftCodeMapperResponse();
		body.setStatus(true);
		ResponseEntity<BankSwiftCodeMapperResponse> iresponse = new ResponseEntity<>(HttpStatus.OK).ok(body);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(BankSwiftCodeMapperResponse.class))).thenReturn(iresponse);

		BankSwiftCodeMapperResponse respo = orderOmsController.bankReturnFormSubmit(requestHeader, req);
		assertNotNull(respo);
		assertEquals(respo.getStatus(), true);
	}

	@Test
	public void checkAccountDeletionEligibility() {

		setStaticData();
		setorderCred();
		setSalseOrderData();
		AuthenticationtestImpl auth = new AuthenticationtestImpl();
		SecurityContextHolder.getContext().setAuthentication(auth);
		ConsulValues orderConsulValues = new ConsulValues();
		DeleteCustomer deletedcus = new DeleteCustomer();
		orderConsulValues.setDeleteCustomer(deletedcus);

		Constants.setOrderConsulValues(new Gson().toJson(orderConsulValues).toString());

		AccountDeletionEligibleRequest req = new AccountDeletionEligibleRequest();
		req.setCustomerId(1);
		AccountDeletionEligibleResponse respo = orderOmsController.checkAccountDeletionEligibility(req, "token");
		assertNotNull(respo);
	}


	@Test
	public void brazeWebHookForWalletUpdate() {

		setStaticData();
		setorderCred();
		setSalseOrderData();
		AuthenticationtestImpl auth = new AuthenticationtestImpl();
		SecurityContextHolder.getContext().setAuthentication(auth);
		ConsulValues orderConsulValues = new ConsulValues();
		DeleteCustomer deletedcus = new DeleteCustomer();
		orderConsulValues.setDeleteCustomer(deletedcus);

		Constants.setOrderConsulValues(new Gson().toJson(orderConsulValues).toString());

		AddStoreCreditRequest req = new AddStoreCreditRequest();
		StoreCredit strc = new StoreCredit();
		strc.setStore("1");
		strc.setStoreId(1);
		strc.setEmailId("test@mailinator.com");
		strc.setStoreCredit(new BigDecimal(10));

		req.setStoreCredits(Arrays.asList(strc));
		AddStoreCreditResponse respo = orderOmsController.brazeWebHookForWalletUpdate(null,req, "token");
		assertNotNull(respo);
	}
	@Test
	public void brazeCustomAttributePushTest() {
		setStaticData();
		setorderCred();
		setSalseOrderData();
		AuthenticationtestImpl auth = new AuthenticationtestImpl();
		SecurityContextHolder.getContext().setAuthentication(auth);
		ConsulValues orderConsulValues = new ConsulValues();
		DeleteCustomer deletedcus = new DeleteCustomer();
		orderConsulValues.setDeleteCustomer(deletedcus);

		Constants.setOrderConsulValues(new Gson().toJson(orderConsulValues).toString());

		AddStoreCreditRequest req = new AddStoreCreditRequest();
		StoreCredit strc = new StoreCredit();
		strc.setStore("1");
		strc.setStoreId(1);
		strc.setEmailId("test@mailinator.com");
		strc.setStoreCredit(new BigDecimal(10));

		req.setStoreCredits(Arrays.asList(strc));

		AmastyStoreCredit credit=new AmastyStoreCredit();
		credit.setCustomerId(1);
		credit.setStoreCredit(new BigDecimal(1));

		when(amastyStoreCreditRepository.findByCustomerIdIn(any())).thenReturn( Arrays.asList(credit));
		AddStoreCreditResponse respo = orderOmsController.brazeCustomAttributePush(null, "token");
		assertNotNull(respo);
	}

	@Test
	public void testLockUnlockShukranData_Lock_SuccessfulResponse() {
		// Arrange
		String profileId = "testProfile";
		String points = "100";
		String cartId = "testCart";
		Boolean isLock = true;
		String expectedResponse = "api failed";

		// Mock RedisObject response
		RedisObject redisObject = new RedisObject();
		redisObject.setAccessToken("dummyToken"); // Ensure token is not null
		when(redisService.getData(anyString(), eq(RedisObject.class))).thenReturn(redisObject);

		// Mock API response
		LockUnlockHttpResponseBody responseBody = new LockUnlockHttpResponseBody();
		ResponseEntity<LockUnlockHttpResponseBody> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);
		when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(LockUnlockHttpResponseBody.class)))
				.thenReturn(responseEntity);

		// Act
		String actualResponse = commonService.lockUnlockShukranData(profileId, points, cartId, isLock, null, null, "", "");

		// Assert
		assertNotNull(actualResponse, "Actual response should not be null.");
		assertEquals(expectedResponse, actualResponse);

	}

	@Test
	public void testLockUnlockShukranData_UnLock_SuccessfulResponse() {
		// Arrange
		String profileId = "testProfile";
		String points = "100";
		String cartId = "testCart";
		Boolean isLock = false;
		String expectedResponse = "api failed";

		// Mock RedisObject response
		RedisObject redisObject = new RedisObject();
		redisObject.setAccessToken("dummyToken"); // Ensure token is not null
		when(redisService.getData(anyString(), eq(RedisObject.class))).thenReturn(redisObject);

		// Mock API response
		LockUnlockHttpResponseBody responseBody = new LockUnlockHttpResponseBody();
		ResponseEntity<LockUnlockHttpResponseBody> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);
		when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(LockUnlockHttpResponseBody.class)))
				.thenReturn(responseEntity);

		// Act
		String actualResponse = commonService.lockUnlockShukranData(profileId, points, cartId, isLock, null, null, "", "");

		// Assert
		assertNotNull(actualResponse, "Actual response should not be null.");
		assertEquals(expectedResponse, actualResponse);

	}


	@Test
	public void testLockUnlockShukranData_UnLock_FailureResponse() {
		// Arrange
		String profileId = "testProfile";
		String points = "100";
		String cartId = "testCart";
		Boolean isLock = false;
		String expectedResponse = "api failed";

		// Mock RedisObject response
		RedisObject redisObject = new RedisObject();
		redisObject.setAccessToken("dummyToken"); // Ensure token is not null
		when(redisService.getData(anyString(), eq(RedisObject.class))).thenReturn(redisObject);

		// Mock API response
		LockUnlockHttpResponseBody responseBody = new LockUnlockHttpResponseBody();
		when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(LockUnlockHttpResponseBody.class)))
				.thenThrow(new RestClientException("Simulated exception"));

		// Act
		String actualResponse = commonService.lockUnlockShukranData(profileId, points, cartId, isLock, null, null, "", "");

		// Assert
		assertNotNull(actualResponse, "Actual response should not be null.");
		assertEquals(expectedResponse, actualResponse);

	}

	@Test
	public void testLockUnlockShukranData_Lock_FailureResponse() {
		// Arrange
		String profileId = "testProfile";
		String points = "100";
		String cartId = "testCart";
		Boolean isLock = true;
		String expectedResponse = "api failed";

		// Mock RedisObject response
		RedisObject redisObject = new RedisObject();
		redisObject.setAccessToken("dummyToken"); // Ensure token is not null
		when(redisService.getData(anyString(), eq(RedisObject.class))).thenReturn(redisObject);

		when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(LockUnlockHttpResponseBody.class)))
				.thenThrow(new RestClientException("Simulated exception"));

		// Act
		String actualResponse = commonService.lockUnlockShukranData(profileId, points, cartId, isLock, null, null, "", "");

		// Assert
		assertNotNull(actualResponse, "Actual response should not be null.");
		assertEquals(expectedResponse, actualResponse);

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
		salesInvoiceItem.setPriceInclTax(new BigDecimal(10));
		salesInvoiceItem.setOrderItemId(1);
//		salesInvoiceItem.set
		Set<SalesInvoiceItem> salesInvoiceItemset = new HashSet<>();
		salesInvoiceItemset.add(salesInvoiceItem);
		salesInvoice.setSalesInvoiceItem(salesInvoiceItemset);
		Set<SalesInvoice> salesInvoices = new HashSet<>();
		salesInvoices.add(salesInvoice);
		salesOrder.setSalesInvoices(salesInvoices);
	}

	private void addAddressData() {
		SalesOrderAddress add = new SalesOrderAddress();
		add.setAddressType("shipping");
		add.setEntityId(1);
		Set<SalesOrderAddress> salesOrderAddress = new HashSet<>();
		salesOrderAddress.add(add);
		salesOrder.setSalesOrderAddress(salesOrderAddress);
	}

	private void addShipmentData() {
		SalesShipmentItem item = new SalesShipmentItem();
		item.setItemId(1);
		item.setSku("1");
		SalesShipment salesShipment = new SalesShipment();
		salesShipment.addSalesShipmentItem(item);
		Set<SalesShipment> salesShipments = new HashSet<>();
		salesShipments.add(salesShipment);
		salesOrder.setSalesShipments(salesShipments);
	}

	//set test data
	private GetProductsBySkuResponse getMulinData() {
		OrderHelper orderHelper;
		ProductResponseBody prod = new ProductResponseBody();
		ProductName productName = new ProductName();
		productName.setArabic("Product name");
		productName.setEnglish("Product name");
		Attribute attr = new Attribute();
		attr.setName(productName);
		prod.set_id("1");
		prod.setSku("01");
		prod.setAttributes(attr);
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

	private void setStaticData() {
		// TODO Auto-generated method stub
		ReflectionTestUtils.setField(configService, "internalAuthBearerToken", "token,dwd,1,2");
		ReflectionTestUtils.setField(configService, "externalAuthBearerToken", "token,ds,1,1");
		ReflectionTestUtils.setField(constants, "storesList", storeList);
		ReflectionTestUtils.setField(orderOmsController, "configService", configService);
		ReflectionTestUtils.setField(orderOmsController, "salesOrderCustomerService", salesOrderCustomerService);
		ReflectionTestUtils.setField(orderOmsController, "salesOrderService", salesOrderService);
		ReflectionTestUtils.setField(salesOrderService, "getOrderList", getOrderList);
		ReflectionTestUtils.setField(salesOrderService, "getOrderById", getOrderById);
		ReflectionTestUtils.setField(salesOrderService, "orderEntityConverter", orderEntityConverter);
		ReflectionTestUtils.setField(salesOrderService, "mulinHelper", mulinHelper);
		ReflectionTestUtils.setField(salesOrderService, "staticComponents", staticComponents);
		ReflectionTestUtils.setField(salesOrderService, "restTemplate", restTemplate);
		ReflectionTestUtils.setField(getOrderById, "omsOrderresponsedto", omsOrderresponsedto);
		ReflectionTestUtils.setField(orderOmsController, "salesOrderServiceV2", salesOrderServiceV2);
		ReflectionTestUtils.setField(salesOrderServiceV2,"paymentDtfHelper",paymentDtfHelper);
		ReflectionTestUtils.setField(orderOmsController, "salesOrderServiceV3", salesOrderServiceV3);
		ReflectionTestUtils.setField(orderOmsController, "salesOrderRMAService", salesOrderRMAService);
		ReflectionTestUtils.setField(salesOrderRMAService, "omsorderentityConverter", omsorderentityConverter);
		ReflectionTestUtils.setField(salesOrderRMAService, "mulinHelper", mulinHelper);
		ReflectionTestUtils.setField(getOrderById, "omsorderentityConverter", omsorderentityConverter);
		ReflectionTestUtils.setField(getOrderById, "orderHelper", orderHelper);
		ReflectionTestUtils.setField(getOrderById, "refundHelper", refundHelper);
		ReflectionTestUtils.setField(getOrderById, "eASServiceImpl", eASServiceImpl);
		ReflectionTestUtils.setField(getOrderById, "omsOrderupdateresponse", omsOrderupdateresponse);
		ReflectionTestUtils.setField(omsorderentityConverter, "staticComponents", staticComponents);
		ReflectionTestUtils.setField(omsorderentityConverter, "orderHelper", orderHelper);
		ReflectionTestUtils.setField(omsorderentityConverter, "orderEntityConverter", orderEntityConverter);

		ReflectionTestUtils.setField(salesOrderService, "omsorderentityConverter", omsorderentityConverter);
		ReflectionTestUtils.setField(salesOrderService, "orderShipmentHelper", orderShipmentHelper);

		ReflectionTestUtils.setField(orderShipmentHelper, "paymentDtfHelper", paymentDtfHelper);
		ReflectionTestUtils.setField(orderShipmentHelper, "orderHelper", orderHelper);
		ReflectionTestUtils.setField(salesOrderServiceV3, "orderpushHelper", orderpushHelper);
		Map<String, Integer> m = new HashMap<>();
		m.put("key", 1);
		ReflectionTestUtils.setField(staticComponents, "statusStepMap", m);
	}

	void setdisabledSer(boolean flag) {
		DisabledServices val = new DisabledServices(flag, flag, flag);
		ReflectionTestUtils.setField(constants, "disabledServices", val);

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
		keydetail.setBrazeAttributePushLimit(1);
		keydetail.setEmail("abc@mail.com");
		GetOrderConsulValues val = new GetOrderConsulValues();
		val.setOrderDetails(keydetail);
		val.setExternalAuthEnable(false);
		val.setInternalAuthEnable(true);
		val.setFirebaseAuthEnable(true);

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
		salesOrder.setIncrementId("1");
		salesOrder.setCustomerIsGuest(2);
		salesOrder.setEntityId(1);
		salesOrder.setStoreId(1);
		salesOrder.setStatus("packed");
		salesOrder.setGrandTotal(new BigDecimal(10));
		salesOrder.setWmsStatus(new Integer(1));
		salesOrder.setAmstorecreditAmount(new BigDecimal(10));
		salesOrder.setStoreToBaseRate(new BigDecimal(1));
		salesOrder.setAmstorecreditAmount(new BigDecimal(1));
		LocalDateTime currentTime = LocalDateTime.now();
		salesOrder.setCreatedAt(Timestamp.valueOf(currentTime));
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
		items.setBaseRowTotalInclTax(new BigDecimal(10));
		items.setParentOrderItem(items);
		items.setTaxPercent(new BigDecimal(15));
		items.setProductType("configurable");
		items.setOriginalPrice(new BigDecimal(15));

		Set<SalesOrderItem> setitem = new HashSet<>();
		setitem.add(items);

		salesOrder.setSalesOrderPayment(payitem);
		SalesOrderItem item1 = new SalesOrderItem();
		item1.setSku("01");
		item1.setItemId(2);
		item1.setProductType("prepaid");
		item1.setPriceInclTax(new BigDecimal(10));
		item1.setQtyOrdered(new BigDecimal(2));
		item1.setQtyCanceled(new BigDecimal(1));
		item1.setDiscountAmount(new BigDecimal(5));
		item1.setRowTotalInclTax(new BigDecimal(10));
		item1.setBaseRowTotalInclTax(new BigDecimal(10));
		item1.setTaxPercent(new BigDecimal(15));
		item1.setProductType("simple");
		item1.setOriginalPrice(new BigDecimal(15));
		setitem.add(item1);
		salesOrder.setSalesOrderItem(setitem);
		Set<SalesShipmentTrack> set = new HashSet<>();
		SalesShipmentTrack track = new SalesShipmentTrack();
		track.setTrackNumber("1");
		salesOrder.setSalesShipmentTrack(set);
		set.add(track);
	}

	private void setSalseOrderDatarecreate() {
		salesOrder = new SalesOrder();
		salesOrder.setCustomerId(1);
		salesOrder.setIncrementId("1");
		salesOrder.setCustomerIsGuest(2);
		salesOrder.setEntityId(1);
		salesOrder.setStoreId(1);
		salesOrder.setStatus("packed");
		salesOrder.setGrandTotal(new BigDecimal(10));
		salesOrder.setWmsStatus(new Integer(1));
		salesOrder.setAmstorecreditAmount(new BigDecimal(10));
		salesOrder.setStoreToBaseRate(new BigDecimal(1));
		salesOrder.setAmstorecreditAmount(new BigDecimal(1));
		LocalDateTime currentTime = LocalDateTime.now();
		salesOrder.setCreatedAt(Timestamp.valueOf(currentTime));
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
		items.setBaseRowTotalInclTax(new BigDecimal(10));
		items.setParentOrderItem(items);
		items.setTaxPercent(new BigDecimal(15));
		items.setProductType("configurable");
		items.setOriginalPrice(new BigDecimal(15));

		Set<SalesOrderItem> setitem = new HashSet<>();
		setitem.add(items);

		salesOrder.setSalesOrderPayment(payitem);

		salesOrder.setSalesOrderItem(setitem);
		Set<SalesShipmentTrack> set = new HashSet<>();
		SalesShipmentTrack track = new SalesShipmentTrack();
		track.setTrackNumber("1");
		salesOrder.setSalesShipmentTrack(set);
		set.add(track);
	}
}