package org.styli.services.customer.utility.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.lang.reflect.Type;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;
import org.styli.services.customer.repository.SequenceCustomerEntityRepository;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.client.OrderClient;
import org.styli.services.customer.utility.helper.AddressMapperHelper;
import org.styli.services.customer.utility.helper.AddressMapperHelperV2;
import org.styli.services.customer.utility.pojo.category.CategoryListResponse;
import org.styli.services.customer.utility.pojo.config.AppEnvironments;
import org.styli.services.customer.utility.pojo.config.BaseConfig;
import org.styli.services.customer.utility.pojo.config.Environments;
import org.styli.services.customer.utility.pojo.config.Store;
import org.styli.services.customer.utility.pojo.config.StoreConfigResponse;
import org.styli.services.customer.utility.pojo.config.StoreConfigResponseDTO;
import org.styli.services.customer.utility.pojo.config.Stores;
import org.styli.services.customer.utility.service.CatlogCategoryEnityService;
import org.styli.services.customer.utility.service.impl.ConfigServiceV2Impl;
import org.styli.services.customer.utility.service.impl.ConfigUtilityServiceImpl;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@SpringBootTest(classes = { UtilityControllerTest.class })
public class UtilityControllerTest extends AbstractTestNGSpringContextTests {

	@InjectMocks
	private UtilityController utilityController;

	@Mock
	RestTemplate restTemplate;
	@InjectMocks
	private Constants constants;

	@InjectMocks
	private ConfigUtilityServiceImpl configService;

	@InjectMocks
	private ConfigServiceV2Impl configServiceV2;

	@Mock
	OrderClient orderClient;

	@Mock
	private CatlogCategoryEnityService catlogCategoryEnityService;

	@Spy
	private HttpServletRequest httpServletRequest = Mockito.mock(HttpServletRequest.class);

	private MockMvc mockMvc;

	private List<org.styli.services.customer.utility.pojo.config.Stores> storeList;
	@InjectMocks
	private AddressMapperHelper addressMapperHelper;
	@Mock
	AppEnvironments appEnvironments;
	@Mock
	private SequenceCustomerEntityRepository sequenceCustomerEntityRepository;

	Map<String, String> requestHeader;
	@InjectMocks
	private AddressMapperHelperV2 addressMapperHelperV2;

	@BeforeClass
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		mockMvc = MockMvcBuilders.standaloneSetup(utilityController).build();
		requestHeader = new HashMap<String, String>();
		requestHeader.put("Authorization", "Bearer testToken");
		try {
			String storeData = new String(Files.readAllBytes(Paths.get("src/test/resources/store_list.json")));
			Type listType = new TypeToken<ArrayList<Stores>>() {
			}.getType();
			storeList = new Gson().fromJson(storeData, listType);
		} catch (Exception e) {
		}

		Store store1 = new Store();
		store1.setStoreId(1);
		store1.setWebSiteId(1);
		store1.setCode("1_");
		Store store2 = new Store();
		store2.setStoreId(3);
		store2.setWebSiteId(1);
		store2.setCode("1_");
		Store store11 = new Store();
		store11.setStoreId(11);
		store11.setWebSiteId(1);
		store11.setCode("1_");
		Store store13 = new Store();
		store13.setStoreId(13);
		store13.setWebSiteId(1);
		store13.setCode("1_");
		Store store17 = new Store();
		store17.setStoreId(17);
		store17.setWebSiteId(1);
		store17.setCode("1_");
		Store store21 = new Store();
		store21.setStoreId(21);
		store21.setWebSiteId(1);
		store21.setCode("1_");

		Map<String, Object> body = new HashMap<>();
		body.put("version", "1");
		body.put("status", true);
		ResponseEntity<Map> response = new ResponseEntity<>(HttpStatus.OK).ok(body);
		Mockito.when(restTemplate.exchange(Mockito.any(URI.class), Mockito.eq(HttpMethod.GET),
				Mockito.any(HttpEntity.class), Mockito.eq(Map.class))).thenReturn(response);
		when(orderClient.findAllStores()).thenReturn(Arrays.asList(store1, store21, store11, store13, store17, store2));

	}

	@Test
	public void testGetAllCategories() throws Exception {
		// Prepare test data

		setStaticfields();
		when(orderClient.getStoreLanguage(anyInt())).thenReturn("en");
		Map<String, String> requestHeader = new HashMap<String, String>();
		requestHeader.put("Authorization", "Bearer testToken");
		int storeId = 1;

		// Prepare mock response
		CategoryListResponse mockResponse = new CategoryListResponse();
		mockResponse.setStatus(true);

		Mockito.when(catlogCategoryEnityService.findAllCategories(requestHeader, storeId, false))
				.thenReturn(mockResponse);
		StoreConfigResponseDTO repo = utilityController.getStoreConfigsV1(httpServletRequest);
		assertEquals(repo.getStatus(), true);
		assertNotNull(repo.getResponse());
		assertEquals(repo.getResponse().getAppEnvironments().isEmpty(), false);
	}

	@Test
	public void testARGetAllCategories() throws Exception {
		// Prepare test data

		setStaticfields();
		when(orderClient.getStoreLanguage(anyInt())).thenReturn("ar");
		Map<String, String> requestHeader = new HashMap<String, String>();
		requestHeader.put("Authorization", "Bearer testToken");
		int storeId = 1;

		// Prepare mock response
		CategoryListResponse mockResponse = new CategoryListResponse();
		mockResponse.setStatus(true);

		Mockito.when(catlogCategoryEnityService.findAllCategories(requestHeader, storeId, false))
				.thenReturn(mockResponse);
		StoreConfigResponseDTO repo = utilityController.getStoreConfigsV1(httpServletRequest);
		assertEquals(repo.getStatus(), true);
		assertNotNull(repo.getResponse());
		assertEquals(repo.getResponse().getAppEnvironments().isEmpty(), false);
	}

	@Test
	public void testPushStoreConfigsV1() throws Exception {
		// Prepare test data

		setStaticfields();
		when(orderClient.getStoreLanguage(anyInt())).thenReturn("ar");
		Map<String, String> requestHeader = new HashMap<String, String>();
		requestHeader.put("Authorization", "Bearer testToken");
		int storeId = 1;

		// Prepare mock response
		CategoryListResponse mockResponse = new CategoryListResponse();
		mockResponse.setStatus(true);

		Mockito.when(catlogCategoryEnityService.findAllCategories(requestHeader, storeId, false))
				.thenReturn(mockResponse);
		StoreConfigResponseDTO repo = utilityController.pushStoreConfigsV1(httpServletRequest);
		assertEquals(repo.getStatus(), true);
		assertNotNull(repo.getResponse());
		assertEquals(repo.getResponse().getAppEnvironments().isEmpty(), false);
	}

	@Test
	public void testGetStoreConfigsV2() throws Exception {
		// Prepare test data

		setStaticfields();
		Environments env = new Environments("dfg", "fvs", "vsdvsnull", null);
		StoreConfigResponse storeConfigResponse = new StoreConfigResponse();
		storeConfigResponse.setEnvironments(Arrays.asList(env));
		ReflectionTestUtils.setField(constants, "storesList", storeList);
		ReflectionTestUtils.setField(constants, "StoreConfigResponse", storeConfigResponse);
		when(orderClient.getStoreLanguage(anyInt())).thenReturn("ar");
		Map<String, String> requestHeader = new HashMap<String, String>();
		requestHeader.put("Authorization", "Bearer testToken");
		int storeId = 1;

		// Prepare mock response
		CategoryListResponse mockResponse = new CategoryListResponse();
		mockResponse.setStatus(true);

		Mockito.when(catlogCategoryEnityService.findAllCategories(requestHeader, storeId, false))
				.thenReturn(mockResponse);
		StoreConfigResponseDTO repo = utilityController.getStoreConfigsV2(httpServletRequest);
		assertEquals(repo.getStatus(), true);
		assertNotNull(repo.getResponse());
	}

	@Test
	public void testPushStoreConfigsV2GCP() throws Exception {
		// Prepare test data

		setStaticfields();
		Environments env = new Environments("dfg", "fvs", "vsdvsnull", null);
		StoreConfigResponse storeConfigResponse = new StoreConfigResponse();
		storeConfigResponse.setEnvironments(Arrays.asList(env));
		ReflectionTestUtils.setField(constants, "storesList", storeList);
		ReflectionTestUtils.setField(constants, "StoreConfigResponse", storeConfigResponse);
		when(orderClient.getStoreLanguage(anyInt())).thenReturn("ar");

		// Prepare mock response
		CategoryListResponse mockResponse = new CategoryListResponse();
		mockResponse.setStatus(true);

		Mockito.when(catlogCategoryEnityService.findAllCategories(requestHeader, 1, false)).thenReturn(mockResponse);
		StoreConfigResponseDTO repo = utilityController.pushStoreConfigsV2GCP(httpServletRequest);
		assertEquals(repo.getStatus(), true);
		assertNotNull(repo.getResponse());
	}

	@Test
	public void testGetAddressMapper() throws Exception {
		setStaticfields();
		ReflectionTestUtils.setField(addressMapperHelper, "addressMapperFlag", "1");
		// Prepare test data
		String countryCode = "US";

		// Prepare mock response

		Map<String, Map<String, String>> map = new HashMap<>();
		Map<String, String> body = new HashMap<>();
		body.put("key", "value");
		map.put("response", body);
		ResponseEntity<Map> response = new ResponseEntity<>(HttpStatus.OK).ok(map);
		Mockito.when(restTemplate.exchange(Mockito.any(URI.class), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(Map.class))).thenReturn(response);

		String repo = utilityController.getAddressMapper(countryCode, httpServletRequest);
		assertNotNull(repo);
	}

	@Test
	public void testGetAddressMapperMapperOff() throws Exception {
		setStaticfields();
		ReflectionTestUtils.setField(addressMapperHelper, "addressMapperFlag", null);
		// Prepare test data
		String countryCode = "US";

		// Prepare mock response
		ResponseEntity<String> response = new ResponseEntity<>(HttpStatus.OK).ok("");
		Mockito.when(restTemplate.exchange(anyString(), Mockito.eq(HttpMethod.GET), any(), Mockito.eq(String.class)))
				.thenReturn(response);

		String repo = utilityController.getAddressMapper(countryCode, httpServletRequest);
		verify(restTemplate, times(1)).exchange(anyString(), Mockito.eq(HttpMethod.GET), any(),
				Mockito.eq(String.class));
		assertNotNull(repo);
	}

	@Test
	public void pushAddressMapperToConsul() throws Exception {
		setStaticfields();
		BaseConfig config = new BaseConfig();
		config.setAddressMapperPublishCronFlow(true);
		ReflectionTestUtils.setField(addressMapperHelper, "addressMapperFlag", "1");
		ReflectionTestUtils.setField(addressMapperHelperV2, "adrsmprBaseUrl", "customer");
		ReflectionTestUtils.setField(addressMapperHelperV2, "configService", configService);
		ReflectionTestUtils.setField(addressMapperHelper, "addressMapperFlag", "1");
		ReflectionTestUtils.setField(addressMapperHelperV2, "addressMapperHelper", addressMapperHelper);
		ReflectionTestUtils.setField(constants, "baseConfig", config);
		// Prepare test data
		String countryCode = "US";

		// Prepare mock response
		Environments env = new Environments("dfg", "fvs", "vsdvsnull", null);
		StoreConfigResponse storeConfigResponse = new StoreConfigResponse();
		storeConfigResponse.setEnvironments(Arrays.asList(env));
		ReflectionTestUtils.setField(constants, "storesList", storeList);
		ReflectionTestUtils.setField(constants, "StoreConfigResponse", storeConfigResponse);

		Map<String, Map<String, String>> map = new HashMap<>();
		Map<String, String> body = new HashMap<>();
		body.put("key", "value");
		map.put("response", body);
		ResponseEntity<Map> response = new ResponseEntity<>(HttpStatus.OK).ok(map);
		Mockito.when(restTemplate.exchange(Mockito.any(URI.class), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(Map.class))).thenReturn(response);
		String repo = utilityController.pushAddressMapperToConsul(countryCode, httpServletRequest);
		assertEquals(repo.isEmpty(), false);
	}

	@Test
	public void testCronOffPushAddressMapperToConsul() throws Exception {
		setStaticfields();
		BaseConfig config = new BaseConfig();
		config.setAddressMapperPublishCronFlow(false);
		ReflectionTestUtils.setField(addressMapperHelper, "addressMapperFlag", "1");
		ReflectionTestUtils.setField(addressMapperHelperV2, "adrsmprBaseUrl", "customer");
		ReflectionTestUtils.setField(addressMapperHelperV2, "configService", configService);
		ReflectionTestUtils.setField(addressMapperHelper, "addressMapperFlag", "1");
		ReflectionTestUtils.setField(addressMapperHelper, "configServiceV2", configServiceV2);
		ReflectionTestUtils.setField(addressMapperHelperV2, "addressMapperHelper", addressMapperHelper);
		ReflectionTestUtils.setField(constants, "baseConfig", config);
		// Prepare test data
		String countryCode = "US";

		// Prepare mock response
		Environments env = new Environments("dfg", "fvs", "vsdvsnull", null);
		StoreConfigResponse storeConfigResponse = new StoreConfigResponse();
		storeConfigResponse.setEnvironments(Arrays.asList(env));
		ReflectionTestUtils.setField(constants, "storesList", storeList);
		ReflectionTestUtils.setField(constants, "StoreConfigResponse", storeConfigResponse);

		Map<String, Map<String, String>> map = new HashMap<>();
		Map<String, String> body = new HashMap<>();
		body.put("key", "value");
		map.put("response", body);
		ResponseEntity<Map> response = new ResponseEntity<>(HttpStatus.OK).ok(map);
		Mockito.when(restTemplate.exchange(Mockito.any(URI.class), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(Map.class))).thenReturn(response);
		String repo = utilityController.pushAddressMapperToConsul(countryCode, httpServletRequest);
		assertEquals(repo.isEmpty(), false);
	}

	@Test
	public void testpushAddressMapperToGCP() throws Exception {
		setStaticfields();
		BaseConfig config = new BaseConfig();
		config.setAddressMapperPublishCronFlow(false);

		ReflectionTestUtils.setField(addressMapperHelper, "addressMapperFlag", "1");
		ReflectionTestUtils.setField(addressMapperHelperV2, "adrsmprBaseUrl", "customer");
		ReflectionTestUtils.setField(addressMapperHelperV2, "configService", configService);
		ReflectionTestUtils.setField(addressMapperHelper, "addressMapperFlag", "1");
		ReflectionTestUtils.setField(addressMapperHelper, "configServiceV2", configServiceV2);
		ReflectionTestUtils.setField(addressMapperHelperV2, "addressMapperHelper", addressMapperHelper);
		ReflectionTestUtils.setField(constants, "baseConfig", config);
		// Prepare test data
		String countryCode = "US";

		// Prepare mock response
		Environments env = new Environments("dfg", "fvs", "vsdvsnull", null);
		StoreConfigResponse storeConfigResponse = new StoreConfigResponse();
		storeConfigResponse.setEnvironments(Arrays.asList(env));
		ReflectionTestUtils.setField(constants, "storesList", storeList);
		ReflectionTestUtils.setField(constants, "StoreConfigResponse", storeConfigResponse);

		Map<String, Map<String, String>> map = new HashMap<>();
		Map<String, String> body = new HashMap<>();
		body.put("key", "value");
		map.put("response", body);
		ResponseEntity<Map> response = new ResponseEntity<>(HttpStatus.OK).ok(map);
		Mockito.when(restTemplate.exchange(Mockito.any(URI.class), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(Map.class))).thenReturn(response);
		StoreConfigResponseDTO repo = utilityController.pushAddressMapperToGCP();
		assertEquals(repo.getStatus(), false);
	}

	@Test
	public void testpushAddressMapperToGCP2() throws Exception {
		setStaticfields();
		BaseConfig config = new BaseConfig();
		config.setAddressMapperPublishCronFlow(true);

		ReflectionTestUtils.setField(addressMapperHelper, "addressMapperFlag", "1");
		ReflectionTestUtils.setField(addressMapperHelperV2, "adrsmprBaseUrl", "customer");
		ReflectionTestUtils.setField(addressMapperHelperV2, "configService", configService);
		ReflectionTestUtils.setField(addressMapperHelper, "addressMapperFlag", "1");
		ReflectionTestUtils.setField(addressMapperHelper, "configServiceV2", configServiceV2);
		ReflectionTestUtils.setField(addressMapperHelperV2, "addressMapperHelper", addressMapperHelper);
		ReflectionTestUtils.setField(constants, "baseConfig", config);
		// Prepare test data
		String countryCode = "US";

		// Prepare mock response
		Environments env = new Environments("dfg", "fvs", "vsdvsnull", null);
		StoreConfigResponse storeConfigResponse = new StoreConfigResponse();
		storeConfigResponse.setEnvironments(Arrays.asList(env));
		ReflectionTestUtils.setField(constants, "storesList", storeList);
		ReflectionTestUtils.setField(constants, "StoreConfigResponse", storeConfigResponse);

		Map<String, Map<String, String>> map = new HashMap<>();
		Map<String, String> body = new HashMap<>();
		body.put("key", "value");
		map.put("response", body);
		ResponseEntity<Map> response = new ResponseEntity<>(HttpStatus.OK).ok(map);
		Mockito.when(restTemplate.exchange(Mockito.any(URI.class), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(Map.class))).thenReturn(response);
		StoreConfigResponseDTO repo = utilityController.pushAddressMapperToGCP();
		assertEquals(repo.getStatus(), false);
	}

	private void setStaticfields() {
		ReflectionTestUtils.setField(utilityController, "configService", configService);
		ReflectionTestUtils.setField(utilityController, "addressMapperHelper", addressMapperHelper);
		ReflectionTestUtils.setField(utilityController, "addressMapperHelperV2", addressMapperHelperV2);
		ReflectionTestUtils.setField(utilityController, "configServiceV2", configServiceV2);
		ReflectionTestUtils.setField(configServiceV2, "configService", configService);
		ReflectionTestUtils.setField(configService, "addressMapperFlag", "1");
		ReflectionTestUtils.setField(configService, "androidUpdateRequired", false);
		ReflectionTestUtils.setField(configService, "androidVersion", "11");
		ReflectionTestUtils.setField(configService, "lastUpdatedVersion", "11");
		ReflectionTestUtils.setField(configService, "iosUpdateRequired", true);
		ReflectionTestUtils.setField(configService, "iosVersion", "15");
	}

	@Test
	public void getStoreConfigsV1Test() throws Exception {
		// Prepare test data
		Map<String, String> requestHeader = new HashMap<String, String>();
		requestHeader.put("Authorization", "Bearer testToken");
		int storeId = 1;

		// Prepare mock response
		CategoryListResponse mockResponse = new CategoryListResponse();
		mockResponse.setStatus(true);

		Mockito.when(catlogCategoryEnityService.findAllCategories(requestHeader, storeId, false))
				.thenReturn(mockResponse);

		// Perform the MVC request and assert the result
		mockMvc.perform(MockMvcRequestBuilders.get("/rest/utility/categories/store/{storeId}", storeId)
				.header("Authorization", "Bearer testToken")).andExpect(status().isOk());

		// Verify the mock was called
		Mockito.verify(catlogCategoryEnityService).findAllCategories(requestHeader, storeId, false);
	}

}
