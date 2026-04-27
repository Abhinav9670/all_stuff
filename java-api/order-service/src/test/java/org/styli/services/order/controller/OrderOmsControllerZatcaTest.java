package org.styli.services.order.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.OrderObjects;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.model.SalesOrder.SalesCreditmemo;
import org.styli.services.order.model.sales.SalesInvoice;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.pojo.GetOrderConsulValues;
import org.styli.services.order.pojo.zatca.ZatcaConfig;
import org.styli.services.order.pojo.zatca.bulk.BulkInvoiceResponse;
import org.styli.services.order.pojo.zatca.bulk.EInvoicesListRes;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoItemRepository;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoRepository;
import org.styli.services.order.repository.SalesOrder.SalesInvoiceRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderItemRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.service.impl.ConfigServiceImpl;
import org.styli.services.order.service.impl.ZatcaServiceImpl;
import org.styli.services.order.utility.OrderConstants;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@SpringBootTest(classes = { OrderOmsControllerZatcaTest.class })
public class OrderOmsControllerZatcaTest extends AbstractTestNGSpringContextTests {
	@InjectMocks
	OrderOmsController orderOmsController;
	@InjectMocks
	ConfigServiceImpl configService;
	@InjectMocks
	ZatcaServiceImpl zatcaServiceImpl;
	@Mock
	SalesOrderRepository salesOrderRepository;
	@Mock
	SalesCreditmemoRepository salesCreditmemoRepository;
	@Mock
	SalesOrderItemRepository salesOrderItemRepository;
	@Mock
	SalesCreditmemoItemRepository salesCreditmemoItemRepository;
	@Mock
	SalesInvoiceRepository salesInvoiceRepository;
	@Mock
	RestTemplate restTemplate;
	@Mock
	SalesOrder salesOrder;
	List<Stores> storeList;
	ZatcaConfig zatcaConfig;
	@InjectMocks
	private org.styli.services.order.utility.Constants constants;
	OrderConstants orderConstants;

	Map<String, String> requestHeader;

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
			String storeData = new String(
					Files.readAllBytes(Paths.get("src/test/resources/constants/store_consul.json")));
			Type listType = new TypeToken<ArrayList<Stores>>() {
			}.getType();
			storeList = g.fromJson(storeData, listType);
			loadZatcaConfig();
		} catch (Exception e) {
		}
	}

	@BeforeTest
	public void beforeTest() {
		MockitoAnnotations.initMocks(this);
		setStaticData();
		salesOrder = OrderObjects.getSalesOrder();
	}

	@Test(testName = "zatca_create_invoice_auth")
	public void createShipmentZatcaWithAuth() throws Exception {
		GetOrderConsulValues values = new GetOrderConsulValues();
		values.setInternalAuthEnable(true);
		values.setZatcaConfig(zatcaConfig);
		ReflectionTestUtils.setField(constants, "orderCredentials", values);
		ReflectionTestUtils.setField(constants, "storesList", storeList);
		when(salesOrderRepository.findByIncrementId(anyString())).thenReturn(salesOrder);

		String authorizationToken = "token";
		List<String> invoiceList = Arrays.asList("121312412");
		orderOmsController.createShipmentZatca(requestHeader, invoiceList, authorizationToken);
	}

	@Test(testName = "zatca_create_invoice")
	public void createShipmentZatcaWithoutAuth() throws Exception {
		GetOrderConsulValues values = new GetOrderConsulValues();
		values.setInternalAuthEnable(false);
		values.setZatcaConfig(zatcaConfig);
		ReflectionTestUtils.setField(constants, "orderCredentials", values);
		String authorizationToken = "token";
		ReflectionTestUtils.setField(constants, "storesList", storeList);
		List<String> invoiceList = Arrays.asList("121312412");
		orderOmsController.createShipmentZatca(requestHeader, invoiceList, authorizationToken);
	}

	@Test
	public void zatcaCreateCreditNoteSuccess() throws Exception {
		creditmemoMocking();
		String authorizationToken = "token";
		List<String> invoiceList = Arrays.asList("121312412");
		orderOmsController.create(requestHeader, invoiceList, authorizationToken);
	}

	@Test
	public void zatcaCreateCreditNoteFailure() throws Exception {
		GetOrderConsulValues values = new GetOrderConsulValues();
		values.setInternalAuthEnable(true);
		values.setZatcaConfig(zatcaConfig);
		ReflectionTestUtils.setField(constants, "orderCredentials", values);
		ReflectionTestUtils.setField(constants, "storesList", storeList);
		when(salesOrderRepository.findByIncrementId(anyString())).thenReturn(salesOrder);

		String authorizationToken = "token";
		List<String> invoiceList = Arrays.asList("121312412");
		orderOmsController.create(requestHeader, invoiceList, authorizationToken);
	}

	@Test
	public void syncZatcaInvoiceStatusSuccess() throws Exception {
		creditmemoMocking();
		String authorizationToken = "token";
		orderOmsController.syncZatcaInvoiceStatus(requestHeader, null, authorizationToken);
	}

	private void creditmemoMocking() {
		GetOrderConsulValues values = new GetOrderConsulValues();
		values.setInternalAuthEnable(true);
		values.setZatcaConfig(zatcaConfig);
		ReflectionTestUtils.setField(constants, "orderCredentials", values);
		ReflectionTestUtils.setField(constants, "storesList", storeList);
		when(salesOrderRepository.findByIncrementId(anyString())).thenReturn(salesOrder);
		List<SalesInvoice> invoices = salesOrder.getSalesInvoices().stream().collect(Collectors.toList());
		when(salesInvoiceRepository.findByZatcaNotGenerated(anyInt(), anyInt(),anyInt())).thenReturn(invoices);
		when(salesInvoiceRepository.getInvoiceEntityId(anyString())).thenReturn(invoices.get(0));
		when(salesCreditmemoRepository.findByIncrementId(anyString())).thenReturn(OrderObjects.getSalesCreditmemo());

		List<SalesCreditmemo> salesCreditMemos = Arrays.asList(OrderObjects.getSalesCreditmemo());
		when(salesCreditmemoRepository.findByZatcaNotGenerated(anyInt(), anyInt(), anyInt())).thenReturn(salesCreditMemos);

		when(salesOrderRepository.findByEntityId(anyInt())).thenReturn(salesOrder);

		List<SalesOrderItem> salesorderItems = salesOrder.getSalesOrderItem().stream().collect(Collectors.toList());

		when(salesOrderItemRepository.findSalesOrderItemConfigurableByOrderId(anyInt())).thenReturn(salesorderItems);

		when(salesCreditmemoItemRepository.findByParentId(anyInt()))
				.thenReturn(Arrays.asList(OrderObjects.getSalesCreditmemoItem()));

		BulkInvoiceResponse bulkinvoice = new BulkInvoiceResponse();

		EInvoicesListRes eInvoicesListRes = new EInvoicesListRes();
		eInvoicesListRes.setEinvoiceStatus("REPORTED");
		eInvoicesListRes.setInvoiceNumber("234");

		EInvoicesListRes eInvoicesListRes1 = new EInvoicesListRes();
		eInvoicesListRes1.setEinvoiceStatus("FAILED");
		eInvoicesListRes1.setInvoiceNumber("123");
		bulkinvoice.setEinvoicesList(Arrays.asList(eInvoicesListRes, eInvoicesListRes1));

//		ResponseEntity<BulkInvoiceResponse> response = ResponseEntity.ok(bulkinvoice);
//		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
//				Mockito.any(HttpEntity.class), Mockito.eq(BulkInvoiceResponse.class))).thenReturn(response);
//
//		ZatcaInvoiceResponse invoiceResponse = new ZatcaInvoiceResponse();
//		ResponseEntity<ZatcaInvoiceResponse> response1 = ResponseEntity.ok(invoiceResponse);
//		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
//				Mockito.any(HttpEntity.class), Mockito.eq(ZatcaInvoiceResponse.class))).thenReturn(response1);

	}

	@Test
	public void syncZatcaInvoiceStatusFailure() throws Exception {
		GetOrderConsulValues values = new GetOrderConsulValues();
		values.setInternalAuthEnable(true);
		values.setZatcaConfig(zatcaConfig);
		ReflectionTestUtils.setField(constants, "orderCredentials", values);
		ReflectionTestUtils.setField(constants, "storesList", storeList);
		when(salesOrderRepository.findByIncrementId(anyString())).thenReturn(salesOrder);

		String authorizationToken = "token";
		orderOmsController.syncZatcaInvoiceStatus(requestHeader, null, authorizationToken);
	}

	@Test
	public void syncZatcaCreditMemoStatus() throws Exception {
		creditmemoMocking();

		String authorizationToken = "token";
		orderOmsController.syncZatcaCreditMemoStatus(requestHeader, null, authorizationToken);
	}

	public void setStaticData() {
		ReflectionTestUtils.setField(configService, "internalAuthBearerToken", "token,dwd,1,2");
		ReflectionTestUtils.setField(configService, "externalAuthBearerToken", "token,ds,1,1");
		ReflectionTestUtils.setField(orderOmsController, "configService", configService);
		ReflectionTestUtils.setField(orderOmsController, "zatcaServiceImpl", zatcaServiceImpl);
		ReflectionTestUtils.setField(orderOmsController, "salesOrderRepository", salesOrderRepository);
	}

	private void loadZatcaConfig() {
		try {
			// Prepare mock Data
			Gson g = new Gson();
			String storeData = new String(
					Files.readAllBytes(Paths.get("src/test/resources/constants/zatca_config.json")));
			Type listType = new TypeToken<ZatcaConfig>() {
			}.getType();
			zatcaConfig = g.fromJson(storeData, listType);
		} catch (Exception e) {
			System.out.println("error in loading zatca config. " + e);
		}
	}
}
