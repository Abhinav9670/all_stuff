package org.styli.services.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.db.product.exception.NotFoundException;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.helper.MulinHelper;
import org.styli.services.order.jwt.security.jwtsecurity.security.JwtValidator;
import org.styli.services.order.model.CoreConfigData;
import org.styli.services.order.model.Store;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.pojo.AttributeValue;
import org.styli.services.order.repository.CoreConfigDataRepository;
import org.styli.services.order.repository.StoreRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderGridRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.service.impl.CommonServiceImpl;
import org.styli.services.order.service.impl.CoreConfigDataServiceImpl;
import org.styli.services.order.service.impl.SalesOrderServiceV2Impl;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class CommonCoreControllerTest {

	@InjectMocks
	private CommonCoreController commonCoreController;

	@InjectMocks
	private MulinHelper mulinHelper;

	@InjectMocks
	private SalesOrderServiceV2Impl salesOrderServiceV2;

	@InjectMocks
	org.styli.services.order.utility.Constants constants;

	@InjectMocks
	CommonServiceImpl commonService;
	@InjectMocks
	CoreConfigDataServiceImpl coreConfigDataService;
	@Mock
	CoreConfigDataRepository coreConfigDataRepository;
	@Mock
	private RestTemplate restTemplate;
	@Mock
	JwtValidator validator;
	@Mock
	SalesOrderRepository salesOrderRepository;
	@Mock
	org.styli.services.order.repository.StaticComponents staticComponents;
	@Mock
	SalesOrderGridRepository salesOrderGridRepository;
	@Mock
	StoreRepository storeRepository;
	Map<String, String> requestHeader;
	private List<Stores> storeList;
	Map<String, AttributeValue> attrStatusMap;
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
//		ReflectionTestUtils.setField(configService, "internalAuthBearerToken", "token,dwd,1,2");
//		ReflectionTestUtils.setField(configService, "externalAuthBearerToken", "token,ds,1,1");
	}

	@Test
	public void getAttributeStatusTest() {
		setStaticData();
		when(staticComponents.getAttrStatusMap()).thenReturn(attrStatusMap);
		commonCoreController.getAttributeStatus();
		verify(staticComponents, atLeast(1)).getAttrStatusMap();
	}

	@Test
	public void getCodChargesTest() throws NotFoundException {
		setStaticData();
		CoreConfigData data = new CoreConfigData();
		data.setConfigId(1);
		data.setScopeId(1);
		data.setValue("1");
		when(coreConfigDataRepository.findByPathAndScopeId(anyString(), anyInt())).thenReturn(data);
		commonCoreController.getCodCharges(1);
		verify(coreConfigDataRepository, atLeast(1)).findByPathAndScopeId(anyString(), anyInt());
	}

	@Test
	public void getCurrencyConversionRateTest() throws NotFoundException {
		setStaticData();
		CoreConfigData data = new CoreConfigData();
		data.setConfigId(1);
		data.setScopeId(1);
		data.setValue("1");
		when(coreConfigDataRepository.findByPathAndScopeId(anyString(), anyInt())).thenReturn(data);
		commonCoreController.getCurrencyConversionRate(1);
		verify(coreConfigDataRepository, atLeast(1)).findByPathAndScopeId(anyString(), anyInt());
	}

	@Test
	public void getStoreCurrencyTest() {
		setStaticData();
		CoreConfigData data = new CoreConfigData();
		data.setConfigId(1);
		data.setScopeId(1);
		data.setValue("1");
		Store store = new Store();
		store.setWebSiteId(1);
		when(storeRepository.findByStoreId(any())).thenReturn(store);
		when(coreConfigDataRepository.findByPathAndScopeId(anyString(), anyInt())).thenReturn(data);
		commonCoreController.getStoreCurrency(1);
		verify(coreConfigDataRepository, atLeast(1)).findByPathAndScopeId(anyString(), anyInt());
	}

	@Test
	public void getStoreLanguageTest() throws NotFoundException {
		setStaticData();
		CoreConfigData data = new CoreConfigData();
		data.setConfigId(1);
		data.setScopeId(1);
		data.setValue("1");
		when(coreConfigDataRepository.findByPathAndScopeId(anyString(), anyInt())).thenReturn(data);
		commonCoreController.getStoreLanguage(1);
		verify(coreConfigDataRepository, atLeast(1)).findByPathAndScopeId(anyString(), anyInt());
	}

	@Test
	public void getStoreShipmentChargesTest() throws NotFoundException {
		setStaticData();
		CoreConfigData data = new CoreConfigData();
		data.setConfigId(1);
		data.setScopeId(1);
		data.setValue("1");
		when(coreConfigDataRepository.findByPathAndScopeId(anyString(), anyInt())).thenReturn(data);
		commonCoreController.getStoreShipmentCharges(1);
		verify(coreConfigDataRepository, atLeast(1)).findByPathAndScopeId(anyString(), anyInt());
	}

	@Test
	public void getStoreShipmentChargesThresholdTest() throws NotFoundException {
		setStaticData();
		CoreConfigData data = new CoreConfigData();
		data.setConfigId(1);
		data.setScopeId(1);
		data.setValue("1");
		when(coreConfigDataRepository.findByPathAndScopeId(anyString(), anyInt())).thenReturn(data);
		commonCoreController.getStoreShipmentChargesThreshold(1);
		verify(coreConfigDataRepository, atLeast(1)).findByPathAndScopeId(anyString(), anyInt());
	}

	@Test
	public void getTaxPercentageTest() throws NotFoundException {
		setStaticData();
		CoreConfigData data = new CoreConfigData();
		data.setConfigId(1);
		data.setScopeId(1);
		data.setValue("1");
		when(coreConfigDataRepository.findByPathAndScopeId(anyString(), anyInt())).thenReturn(data);
		commonCoreController.getTaxPercentage(1);
		commonCoreController.getCustomDutiesPercentage(1);
		commonCoreController.getImportFeePercentage(1);
		commonCoreController.getMinimumDutyFee(1);
		commonCoreController.getQuoteProductMaxQty(1);
		commonCoreController.getImportMaxFeePercentage(1);
		commonCoreController.getCatalogCurrencyConversionRate(1);

		verify(coreConfigDataRepository, atLeast(1)).findByPathAndScopeId(anyString(), anyInt());
	}

	@Test
	public void saveAddressDatabaseVersionTest() throws NotFoundException {
		setStaticData();
		CoreConfigData data = new CoreConfigData();
		data.setConfigId(1);
		data.setScopeId(1);
		data.setValue("1");
		when(coreConfigDataRepository.findByPathAndScopeId(anyString(), anyInt())).thenReturn(data);

		commonCoreController.saveAddressDatabaseVersion("1");
		commonCoreController.geAddrerssDatabaseVersion();
		verify(coreConfigDataRepository, atLeast(1)).findByPathAndScopeId(anyString(), anyInt());
	}

	private void setStaticData() {
		attrStatusMap = new HashMap<>();
		AttributeValue v = new AttributeValue();
		attrStatusMap.put("key", v);
		// TODO Auto-generated method stub
		ReflectionTestUtils.setField(constants, "storesList", storeList);
		ReflectionTestUtils.setField(commonCoreController, "commonService", commonService);
		ReflectionTestUtils.setField(commonCoreController, "coreConfigDataService", coreConfigDataService);
	}
}
