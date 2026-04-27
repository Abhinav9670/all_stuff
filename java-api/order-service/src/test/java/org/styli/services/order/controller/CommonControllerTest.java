package org.styli.services.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertNotNull;

import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDateTime;
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
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.ReflectionTestUtils;
import org.styli.services.order.db.product.exception.NotFoundException;
import org.styli.services.order.db.product.pojo.CoreConfigDataServicePojo;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.helper.OrderHelper;
import org.styli.services.order.model.CoreConfigData;
import org.styli.services.order.model.Store;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderGrid;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SalesOrderPayment;
import org.styli.services.order.model.sales.SalesShipmentTrack;
import org.styli.services.order.pojo.request.Order.OmsOrderListRequest;
import org.styli.services.order.pojo.response.Customer;
import org.styli.services.order.repository.CoreConfigDataRepository;
import org.styli.services.order.repository.StaticComponents;
import org.styli.services.order.repository.StoreRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.service.impl.CommonServiceImpl;
import org.styli.services.order.service.impl.CoreConfigDataServiceImpl;
import org.styli.services.order.service.impl.SalesOrderServiceV3Impl;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@SpringBootTest(classes = { CommonControllerTest.class })
public class CommonControllerTest extends AbstractTestNGSpringContextTests {
	Map<String, String> requestHeader;
	private List<Stores> storeList;
	SalesOrder salesOrder;
	OmsOrderListRequest request;
	private org.styli.services.order.model.Customer.CustomerEntity customerEntity;
	@InjectMocks
	CommonController CommonController;
	@InjectMocks
	CommonServiceImpl commonService;
	@Mock
	StoreRepository storeRepository;
	@Mock
	StaticComponents staticComponents;

	@Mock
	CoreConfigDataRepository coreConfigDataRepository;
	@Mock
	SalesOrderGridRepository salesOrderGridRepository;
	@Mock
	SalesOrderRepository salesOrderRepository;
	@Mock
	OrderHelper orderHelper;
	@InjectMocks
	CoreConfigDataServiceImpl coreConfigDataService;
	@InjectMocks
	SalesOrderServiceV3Impl salesOrderV3Service;

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
		when(orderHelper.getCustomerDetails(anyInt(), anyString())).thenReturn(customerEntity);
		// ReflectionTestUtils.setField(configService, "internalAuthBearerToken",
		// "token,dwd,1,2");
//		ReflectionTestUtils.setField(configService, "externalAuthBearerToken", "token,ds,1,1");
	}

	@Test
	public void findAllStoresTest() {
		setStaticData();
		CommonController.findAllStores();
	}

	@Test
	public void findByCustomerEmailSalesOrderTest() {
		setStaticData();
		CommonController.findByCustomerEmailSalesOrder("mail");
	}

	@Test
	public void findByCustomerEmailSalesOrderGridTest() {
		setStaticData();
		CommonController.findByCustomerEmailSalesOrderGrid("mail");
	}

	@Test
	public void findByEmailTest() {
		setStaticData();
		CommonController.findByEmail("mail");
	}

	@Test
	public void findByEntityIdTest() {
		setStaticData();
		CommonController.findByEntityId(1);
	}

	@Test
	public void findByWebsiteIdTest() {
		setStaticData();
		CommonController.findByWebsiteId(1);
	}

	@Test
	public void findSalesOrdersTest() throws IOException {
		setStaticData();
		setSalseOrderData();
		Customer cust = new Customer();
		cust.setEmail("mail");
		String requestData = new String(Files.readAllBytes(Paths.get("src/test/resources/orderlistreq.json")));
		String reponseData = new String(Files.readAllBytes(Paths.get("src/test/resources/orderlistrespo.json")));
		Gson g = new Gson();
		request = g.fromJson(requestData, OmsOrderListRequest.class);
		request.setQuery("select all from table");
		SalesOrderGrid customerOrders = g.fromJson(reponseData, SalesOrderGrid.class);
		when(salesOrderRepository.findByCustomerEmailAndCustomerId(anyString(), any()))
				.thenReturn(Arrays.asList(salesOrder));
		when(salesOrderGridRepository.findByCustomerEmail(anyString())).thenReturn(Arrays.asList(customerOrders));
		CommonController.findSalesOrders(cust);
	}

	@Test
	public void findStoreByStoreIdTest() {
		setStaticData();
		CommonController.findStoreByStoreId(1);
	}

	@Test
	public void getAttributeLabelsTest() {
		setStaticData();
		CommonController.getAttributeLabels();
	}

	@Test
	public void getCoreConfigDataServiceTest() throws NotFoundException {
		setStaticData();
		Store store = new Store();
		store.setStoreId(1);
		CoreConfigData config = new CoreConfigData();
		config.setValue("1");
		when(coreConfigDataRepository.findByPathAndScopeId(anyString(), anyInt())).thenReturn(config);
		when(storeRepository.findByStoreId(anyInt())).thenReturn(store);
		CoreConfigDataServicePojo respo = CommonController.getCoreConfigDataService(1, 1, "");
		assertNotNull(respo);
	}

	@Test
	public void getStoresArrayTest() throws NotFoundException {
		setStaticData();
		CommonController.getStoresArray();
	}

	private void setStaticData() {
		// TODO Auto-generated method stub
		ReflectionTestUtils.setField(CommonController, "commonService", commonService);
		ReflectionTestUtils.setField(commonService, "coreConfigDataService", coreConfigDataService);
		ReflectionTestUtils.setField(CommonController, "salesOrderV3Service", salesOrderV3Service);
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