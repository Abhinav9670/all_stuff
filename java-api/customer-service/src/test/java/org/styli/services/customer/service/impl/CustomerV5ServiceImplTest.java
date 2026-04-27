package org.styli.services.customer.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.styli.services.customer.helper.ElasticProductHelperV5;
import org.styli.services.customer.model.Wishlist.WishlistEntity;
import org.styli.services.customer.model.Wishlist.WishlistItem;
import org.styli.services.customer.pojo.CustomerOmsfilterRequest;
import org.styli.services.customer.pojo.CustomerOmslistrequest;
import org.styli.services.customer.pojo.GeometryMap;
import org.styli.services.customer.pojo.GetLocationGoogleMapsRequest;
import org.styli.services.customer.pojo.GetLocationGoogleMapsResponse;
import org.styli.services.customer.pojo.GoogleAddressComponent;
import org.styli.services.customer.pojo.GoogleMapsGeocodingResponse;
import org.styli.services.customer.pojo.GoogleResults;
import org.styli.services.customer.pojo.LocationMap;
import org.styli.services.customer.pojo.ProductInfo;
import org.styli.services.customer.pojo.ProductStatusRequest;
import org.styli.services.customer.pojo.ProductStatusResponse;
import org.styli.services.customer.pojo.Stores;
import org.styli.services.customer.pojo.elastic.HitsDetailResponseElastic;
import org.styli.services.customer.pojo.elastic.ProductDetailsList;
import org.styli.services.customer.pojo.elastic.ResponseDetailElastic;
import org.styli.services.customer.pojo.elastic.VmDetailListResponse;
import org.styli.services.customer.pojo.registration.request.CustomerWishlistV5Request;
import org.styli.services.customer.pojo.registration.request.GetProductV4Request;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.pojo.registration.response.CustomerWishlistResponse;
import org.styli.services.customer.pojo.registration.response.Product.ProductDetailsResponseV4;
import org.styli.services.customer.pojo.registration.response.Product.ProductDetailsResponseV4DTO;
import org.styli.services.customer.pojo.registration.response.Product.ProductValue;
import org.styli.services.customer.pojo.response.MulinProductDescRes;
import org.styli.services.customer.pojo.response.MulinProductDetails;
import org.styli.services.customer.pojo.response.ProductInventoryRes;
import org.styli.services.customer.redis.RedisHelper;
import org.styli.services.customer.repository.StaticComponents;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;
import org.styli.services.customer.repository.Customer.WishlistRepository;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.consul.ServiceConfigs;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@SpringBootTest(classes = { CustomerV5ServiceImplTest.class })
public class CustomerV5ServiceImplTest extends AbstractTestNGSpringContextTests {

	private MockMvc mockMvc;
	@Mock
	RedisHelper redisHelper;
	@Mock
	RestTemplate restTemplate;

	private List<Stores> storeList;
	@Mock
	StaticComponents staticComponents;

	@InjectMocks
	ServiceConfigs config;

	@InjectMocks
	CustomerV5ServiceImpl customerV5ServiceImpl;
	@InjectMocks
	GetWishlist getWishlist;
	@Mock
	private MongoTemplate mongoGccTemplate;

	@Autowired
	private WebApplicationContext webApplicationContext;
	private Map<String, Object> newConfigs;

	List<CustomerEntity> cl;

	private CustomerEntity customerEntity;
	@InjectMocks
	private ClientImpl client;
	@InjectMocks
	ElasticProductHelperV5 elasticProductHelperV5;
	@Mock
	private CustomerEntityRepository customerEntityRepository;
	@Mock
	private WishlistRepository wishlistRepository;

	@BeforeMethod
	public void beforeMethod() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
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
		customerEntity.setPhoneNumber("889898797");
		cl = new ArrayList<>();
		cl.add(customerEntity);
		List<Object> list = new ArrayList<>();
		list.add("njdfk");
		list.add("dfjbds");

		newConfigs = new LinkedHashMap<>();
		Map<String, Object> map = new LinkedHashMap<>();
		Map<String, String> lang = new LinkedHashMap<>();
		lang.put("en", "default message");
		map.put("DEFAULTMSG", lang);
		newConfigs.put("useNewProductInfo", "true");
		newConfigs.put(Constants.BEAUTYLRGTXT_ATTR, list);
		newConfigs.put(Constants.LRGTXT_ATTR, list);
		newConfigs.put(Constants.GOOGLE_MAP_COUNTRY_CODE, list);
		ReflectionTestUtils.setField(customerV5ServiceImpl, "googleMapsCountryCodes", list);
		ReflectionTestUtils.setField(customerV5ServiceImpl, "region", "GCC");
		when(staticComponents.getStoresArray()).thenReturn(storeList);

	}

	@AfterMethod
	public void afterMethod() {
	}

	@BeforeClass
	public void beforeClass() {
		try {
			String storeData = new String(Files.readAllBytes(Paths.get("src/test/resources/store_list.json")));
			Gson g = new Gson();
			Type listType = new TypeToken<ArrayList<Stores>>() {
			}.getType();
			storeList = new Gson().fromJson(storeData, listType);
		} catch (Exception e) {
		}
	}

	@AfterClass
	public void afterClass() {
	}

	@BeforeTest
	public void beforeTest() {
		MockitoAnnotations.initMocks(this);
	}

	@AfterTest
	public void afterTest() {
	}

	@BeforeSuite
	public void beforeSuite() {
	}

	@AfterSuite
	public void afterSuite() {
	}

	@Test
	void customerOmslistTest() {
		customerV5ServiceImpl.onConfigsUpdated(newConfigs);
		CustomerOmsfilterRequest customerOmsfilterRequest = new CustomerOmsfilterRequest();
		customerOmsfilterRequest.setCustomerId("1");
		CustomerOmslistrequest customerOmslistrequest = new CustomerOmslistrequest();
		customerOmslistrequest.setFilters(customerOmsfilterRequest);
		customerOmslistrequest.setQuery("vdsvs");
		customerOmslistrequest.setOffset(-10);
		customerOmslistrequest.setPageSize(-1);

		MongoTemplate mongoOperations = mock(MongoTemplate.class);

		// Create a mock Query
		Query query = mock(Query.class);

		// Set up the mock behavior for the count method
		when(mongoOperations.count(query, CustomerEntity.class)).thenReturn(10L);

		// Call the method you want to test
		long count = mongoOperations.count(query, CustomerEntity.class);
		when(mongoOperations.find(any(Query.class), eq(CustomerEntity.class), eq("CUSTOMER_ENTITY"))).thenReturn(cl);

		Pageable pageable = Mockito.mock(Pageable.class);

		// create a mock page object to return
//		Page<Object> page = new PageImpl(cl);
//		Page<CustomerEntity> customerPage = new PageImpl<>(cl);

		Page<CustomerEntity> customerLists = mock(Page.class);

		customerV5ServiceImpl.customerOmslist(customerOmslistrequest);

	}

//	@Test
//	void getProductQtyErrorTest() {
//		CustomerV5ServiceImpl customerV5ServiceImpl = new CustomerV5ServiceImpl();
//		HashMap<String, String> requestHeader = new HashMap<>();
//
//		ProductStatusRequest productStatusReq = new ProductStatusRequest();
//		productStatusReq.setProducts(new ArrayList<>());
//		productStatusReq.setSkus(new ArrayList<>());
//		productStatusReq.setStoreId(1);
//		ProductStatusResponse actualProductQty = customerV5ServiceImpl.getProductQty(requestHeader, productStatusReq);
//		assertFalse(actualProductQty.isStatus());
//		assertEquals("ERROR!!", actualProductQty.getStatusMsg());
//		assertEquals("204", actualProductQty.getStatusCode());
//		org.styli.services.customer.utility.pojo.ErrorType error = actualProductQty.getError();
//		assertNull(error.getErrorMessage());
//		assertEquals("204", error.getErrorCode());
//
//	}

	@Test
	void getProductQtyTest() {

		// CustomerV5ServiceImpl customerV5ServiceImpl = new CustomerV5ServiceImpl();
		ReflectionTestUtils.setField(customerV5ServiceImpl, "getWishlist", getWishlist);
		HashMap<String, String> requestHeader = new HashMap<>();
		ProductInfo info = new ProductInfo();
		info.setProductId("1");
		info.setSku("001");
		ProductInfo info1 = new ProductInfo();
		info.setProductId("2");
		info.setSku("002");

		ProductStatusRequest productStatusReq = new ProductStatusRequest();
		productStatusReq.setProducts(Arrays.asList(info, info1));
		productStatusReq.setSkus(new ArrayList<>());
		productStatusReq.setStoreId(1);

		ProductValue body = new ProductValue();
		body.setProcuctId("1");
		ProductValue body1 = new ProductValue();
		body.setProcuctId("2");

		ProductInventoryRes res = new ProductInventoryRes();
		res.setResponse(Arrays.asList(body, body1));
		ResponseEntity<ProductInventoryRes> response = new ResponseEntity<>(HttpStatus.OK).ok(res);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(ProductInventoryRes.class))).thenReturn(response);

		ProductStatusResponse actualProductQty = customerV5ServiceImpl.getProductQty(requestHeader, productStatusReq);

		assertEquals(actualProductQty.isStatus(), true);
		assertEquals(actualProductQty.getResponse().getProductStatus().isEmpty(), false);
	}

	@Test
	void getProductInfoTest() {

		// CustomerV5ServiceImpl customerV5ServiceImpl = new CustomerV5ServiceImpl();
		ReflectionTestUtils.setField(customerV5ServiceImpl, "useNewProductInfo", false);
		HashMap<String, String> requestHeader = new HashMap<>();

		GetProductV4Request req = new GetProductV4Request("1", 1, "001");

		MulinProductDetails list = new MulinProductDetails();
		list.setBeautyCode("1");
		MulinProductDetails list1 = new MulinProductDetails();
		list1.setBeautyCode("2");

		MulinProductDescRes res = new MulinProductDescRes();
		res.setBeautyAttrs(Arrays.asList(list, list1));
		res.setStatusCode(200);
		ResponseEntity<MulinProductDescRes> response = new ResponseEntity<>(HttpStatus.OK).ok(res);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(MulinProductDescRes.class))).thenReturn(response);

		ProductDetailsResponseV4 responseEntity = customerV5ServiceImpl.getProductInfo(req, "token");

		assertEquals(responseEntity.getStatus(), true);
		assertNotNull(responseEntity.getResponse().getProductInfos());
	}

	@Test
	void getProductInfoErrorTest() {

		// CustomerV5ServiceImpl customerV5ServiceImpl = new CustomerV5ServiceImpl();
		ReflectionTestUtils.setField(customerV5ServiceImpl, "useNewProductInfo", false);
		HashMap<String, String> requestHeader = new HashMap<>();

		GetProductV4Request req = new GetProductV4Request("1", 1, "001");

		MulinProductDetails list = new MulinProductDetails();
		list.setBeautyCode("1");
		MulinProductDetails list1 = new MulinProductDetails();
		list1.setBeautyCode("2");

		MulinProductDescRes res = new MulinProductDescRes();
		res.setBeautyAttrs(Arrays.asList(list, list1));
		res.setStatusCode(204);
		ResponseEntity<MulinProductDescRes> response = new ResponseEntity<>(HttpStatus.OK).ok(res);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(MulinProductDescRes.class))).thenReturn(response);

		ProductDetailsResponseV4 responseEntity = customerV5ServiceImpl.getProductInfo(req, "token");

		assertEquals(responseEntity.getStatus(), false);
		assertEquals(responseEntity.getResponse(), null);
	}

	@Test
	void getProductInfoExceptionTest() {

		// CustomerV5ServiceImpl customerV5ServiceImpl = new CustomerV5ServiceImpl();
		ReflectionTestUtils.setField(customerV5ServiceImpl, "useNewProductInfo", false);
		HashMap<String, String> requestHeader = new HashMap<>();

		GetProductV4Request req = new GetProductV4Request("1", 1, "001");

		MulinProductDetails list = new MulinProductDetails();
		list.setBeautyCode("1");
		MulinProductDetails list1 = new MulinProductDetails();
		list1.setBeautyCode("2");

		MulinProductDescRes res = new MulinProductDescRes();
		res.setBeautyAttrs(Arrays.asList(list, list1));
		res.setStatusCode(204);
		ResponseEntity<MulinProductDescRes> response = new ResponseEntity<>(HttpStatus.OK).ok(res);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(MulinProductDescRes.class)))
				.thenThrow(new RestClientException("json exception"));

		ProductDetailsResponseV4 responseEntity = customerV5ServiceImpl.getProductInfo(req, "token");

		assertEquals(responseEntity.getStatus(), false);
		assertEquals(responseEntity.getResponse(), null);
	}

	@Test
	void getNewProductInfoTest() {
		ReflectionTestUtils.setField(customerV5ServiceImpl, "useNewProductInfo", true);
		ReflectionTestUtils.setField(customerV5ServiceImpl, "env", "dev");
		HashMap<String, String> requestHeader = new HashMap<>();

		GetProductV4Request req = new GetProductV4Request("1", 1, "001");

		MulinProductDetails list = new MulinProductDetails();
		list.setBeautyCode("1");
		MulinProductDetails list1 = new MulinProductDetails();
		list1.setBeautyCode("2");

		HitsDetailResponseElastic hits = new HitsDetailResponseElastic();
		hits.setCategoryIds(Arrays.asList(1, 2, 3));
		HitsDetailResponseElastic hits1 = new HitsDetailResponseElastic();
		hits.setCategoryIds(Arrays.asList(1, 2, 3));
		ResponseDetailElastic res = new ResponseDetailElastic();
		res.setHits(Arrays.asList(hits, hits1));
		ResponseEntity<ResponseDetailElastic> response = new ResponseEntity<>(HttpStatus.OK).ok(res);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(ResponseDetailElastic.class))).thenReturn(response);

		ProductDetailsResponseV4 responseEntity = customerV5ServiceImpl.getProductInfo(req, "token");
		assertNotNull(responseEntity.getResponse());
	}

	@Test
	void getLocationGoogleMapsTest() {

//		  PowerMockito.when(uriComponentsBuilder.path(any())).thenReturn(uriComponentsBuilder);
//		  UriComponents result = UriComponentsBuilder.fromPath("foo").queryParam("bar").fragment("baz").build();
//		  PowerMockito.when(uriComponentsBuilder.buildAndExpand(anyLong())).thenReturn(result);

		GetLocationGoogleMapsRequest req = new GetLocationGoogleMapsRequest();
		req.setPlaceId("0012");
		req.setLatitude(new BigDecimal(10));
		req.setLongitude(new BigDecimal(100));
		req.setStoreId(1);
//		when(staticComponents.getStoresArray()).thenReturn(storeList);
		GoogleAddressComponent gac = new GoogleAddressComponent();
		gac.setLongName("jvdfv");
		gac.setShortName("jvdfv");
		gac.setTypes(Arrays.asList("locality", "neighborhood"));

		GoogleResults gresult = new GoogleResults();
		gresult.setTypes(Arrays.asList("locality", "neighborhood"));
		gresult.setAddressComponents(Arrays.asList(gac));
		LocationMap lm = new LocationMap();
		lm.setLatitude(new BigDecimal(10));
		lm.setLongitude(new BigDecimal(100));
		GeometryMap m = new GeometryMap();
		m.getLocation();
		gresult.setGeometry(new GeometryMap());
		GoogleResults gresult1 = new GoogleResults();
		gresult1.setTypes(Arrays.asList("locality", "neighborhood"));
		gresult1.setGeometry(new GeometryMap());
		GoogleMapsGeocodingResponse body = new GoogleMapsGeocodingResponse();
		body.setResults(Arrays.asList(gresult, gresult1));
		ResponseEntity<GoogleMapsGeocodingResponse> response = new ResponseEntity<>(HttpStatus.OK).ok(body);
		Mockito.when(restTemplate.exchange(Mockito.any(URI.class), Mockito.eq(HttpMethod.GET),
				Mockito.any(HttpEntity.class), Mockito.eq(GoogleMapsGeocodingResponse.class))).thenReturn(response);
		GetLocationGoogleMapsResponse responseEntity = customerV5ServiceImpl.getLocationGoogleMaps(req);
		assertNotNull(responseEntity.getResponse());
		assertEquals(responseEntity.getStatus(), true);
	}

	@Test
	void getLocationGoogleMaps2Test() {

//		  PowerMockito.when(uriComponentsBuilder.path(any())).thenReturn(uriComponentsBuilder);
//		  UriComponents result = UriComponentsBuilder.fromPath("foo").queryParam("bar").fragment("baz").build();
//		  PowerMockito.when(uriComponentsBuilder.buildAndExpand(anyLong())).thenReturn(result);

		GetLocationGoogleMapsRequest req = new GetLocationGoogleMapsRequest();
		req.setPlaceId("0012");
		req.setLatitude(new BigDecimal(10));
		req.setLongitude(new BigDecimal(100));
		req.setStoreId(1);
//		when(staticComponents.getStoresArray()).thenReturn(storeList);
		GoogleAddressComponent gac = new GoogleAddressComponent();
		gac.setLongName("jvdfv");
		gac.setShortName("jvdfv");
		gac.setTypes(Arrays.asList("locality", "neighborhood"));

		GoogleResults gresult = new GoogleResults();
		gresult.setTypes(Arrays.asList("locality", "neighborhood"));
		gresult.setAddressComponents(Arrays.asList(gac));
		LocationMap lm = new LocationMap();
		lm.setLatitude(new BigDecimal(10));
		lm.setLongitude(new BigDecimal(100));
		GeometryMap m = new GeometryMap();
		m.getLocation();
		gresult.setGeometry(new GeometryMap());
		GoogleResults gresult1 = new GoogleResults();
		gresult1.setTypes(Arrays.asList("locality", "neighborhood"));
		gresult1.setGeometry(new GeometryMap());
		GoogleMapsGeocodingResponse body = new GoogleMapsGeocodingResponse();
		body.setResults(Arrays.asList(gresult, gresult1));
		ResponseEntity<GoogleMapsGeocodingResponse> response = new ResponseEntity<>(HttpStatus.OK).ok(body);
		Mockito.when(restTemplate.exchange(Mockito.any(URI.class), Mockito.eq(HttpMethod.GET),
				Mockito.any(HttpEntity.class), Mockito.eq(GoogleMapsGeocodingResponse.class))).thenReturn(response);
		Map mp = new HashMap<>();
		Map mpb = new HashMap<>();
		mp.put("response", mpb);
		mpb.put("serviceable", true);
		mpb.put("cityEn", "abu");
		mpb.put("region", "region");
		mpb.put("regionId", "1");
		ResponseEntity<Map> mresponse = new ResponseEntity<>(HttpStatus.OK).ok(mp);
		Mockito.when(restTemplate.exchange(Mockito.any(String.class), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(Map.class))).thenReturn(mresponse);
		GetLocationGoogleMapsResponse responseEntity = customerV5ServiceImpl.getLocationGoogleMaps(req);
		assertNotNull(responseEntity.getResponse());
		assertEquals(responseEntity.getStatus(), true);
	}

	@Test
	void getWishlistTest() throws Exception {

		CustomerWishlistV5Request customerWishlistV5Request = new CustomerWishlistV5Request();
		customerWishlistV5Request.setCustomerId(6559);
		customerWishlistV5Request.setStoreId(1);
		ReflectionTestUtils.setField(customerV5ServiceImpl, "jwtFlag", "1");
		ReflectionTestUtils.setField(customerV5ServiceImpl, "env", "dev");
		ReflectionTestUtils.setField(customerV5ServiceImpl, "client", client);
		ReflectionTestUtils.setField(customerV5ServiceImpl, "vmUrl", "/test/url");
		ReflectionTestUtils.setField(customerV5ServiceImpl, "getWishlist", getWishlist);
		ReflectionTestUtils.setField(client, "region", "GCC");
		ReflectionTestUtils.setField(customerV5ServiceImpl, "elasticProductHelperV5", elasticProductHelperV5);


		Map<String, Object> newConfigs = new LinkedHashMap<>();

		newConfigs.put("wishlist_minimum_count", 2);
		ReflectionTestUtils.setField(config, "consulServiceMap", newConfigs);

		WishlistItem item = new WishlistItem();
		WishlistItem item1 = new WishlistItem();
		item.setStoreId(1);
		item.setSku("001");
		item.setWishlistItemId("001");
		item1.setStoreId(2);
		item1.setSku("001");
		item1.setWishlistItemId("001");

		WishlistEntity wishList = new WishlistEntity();
		wishList.setId(1);
		wishList.setWishListItems(Arrays.asList(item, item1));
		when(customerEntityRepository.existsById(anyInt())).thenReturn(true);
		when(wishlistRepository.findById(anyInt())).thenReturn(Optional.of(wishList));
		HitsDetailResponseElastic hits = new HitsDetailResponseElastic();
		hits.setObjectID("001");
		hits.setCategoryIds(Arrays.asList(1, 2, 3));
		HitsDetailResponseElastic hits1 = new HitsDetailResponseElastic();
		hits1.setObjectID("001");
		hits.setCategoryIds(Arrays.asList(1, 2, 3));
		ResponseDetailElastic res = new ResponseDetailElastic();
		res.setHits(Arrays.asList(hits, hits1));
		ResponseEntity<ResponseDetailElastic> response = new ResponseEntity<>(HttpStatus.OK).ok(res);
		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(ResponseDetailElastic.class))).thenReturn(response);
		
		VmDetailListResponse res1=new VmDetailListResponse();
		res1.setStatus(true);
		res1.setStatusCode("200");
		ProductDetailsList list = new ProductDetailsList();
        List<ProductDetailsResponseV4DTO> lst=new ArrayList<>();		
        ProductDetailsResponseV4DTO productDetails = new ProductDetailsResponseV4DTO();
        productDetails.setId("123");
        productDetails.setSku("ABC");
        lst.add(productDetails);
		list.setProductDetails(lst);
		res1.setResponse(list);
		ResponseEntity<VmDetailListResponse> response1 = new ResponseEntity<>(HttpStatus.OK).ok(res1);
		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(VmDetailListResponse.class))).thenReturn(response1);

		
		CustomerWishlistResponse customerWishlistResponse = customerV5ServiceImpl
				.getWishList(customerWishlistV5Request);
		assertNotNull(customerWishlistResponse);
	}

}
