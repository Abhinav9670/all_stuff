package org.styli.services.customer.service.impl;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.styli.services.customer.exception.CustomerException;
import org.styli.services.customer.helper.ElasticProductHelperV5;
import org.styli.services.customer.helper.EmailHelper;
import org.styli.services.customer.helper.PasswordHelper;
import org.styli.services.customer.jwt.security.jwtsecurity.model.JwtUser;
import org.styli.services.customer.jwt.security.jwtsecurity.security.JwtGenerator;
import org.styli.services.customer.jwt.security.jwtsecurity.security.JwtValidator;
import org.styli.services.customer.pojo.Stores;
import org.styli.services.customer.pojo.registration.request.CustomerLoginV4Request;
import org.styli.services.customer.pojo.registration.request.CustomerQueryReq;
import org.styli.services.customer.pojo.registration.request.TokenPasswordRequest;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.pojo.registration.response.CustomerLoginV4Response;
import org.styli.services.customer.pojo.registration.response.CustomerRestPassResponse;
import org.styli.services.customer.repository.StaticComponents;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;
import org.styli.services.customer.repository.Customer.WishlistRepository;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.consul.ServiceConfigs;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class PasswordV2ServiceImplTest {

	Map<String, String> requestHeader;

	@Mock
	RestTemplate restTemplate;

	private List<Stores> storeList;
	@Mock
	StaticComponents staticComponents;
	@Mock
	JwtValidator jwtValidator;

	@InjectMocks
	ServiceConfigs config;

	@InjectMocks
	PasswordV2ServiceImpl passwordV2ServiceImpl;
	@InjectMocks
	GetWishlist getWishlist;
	@Mock
	private MongoTemplate mongoGccTemplate;
	@Mock
	JwtGenerator jwtGenerator;

	@Autowired
	private Map<String, Object> newConfigs;

	List<CustomerEntity> cl;
	@Mock
	PasswordHelper passwordHelper;
	@Mock
	EmailHelper emailHelper;
	@Mock
	IosSigninHelper iosSigninHelper;

	private CustomerEntity customerEntity;
	private
	@Mock
	ClientImpl client;
	@InjectMocks
	ElasticProductHelperV5 elasticProductHelperV5;
	@Mock
	private CustomerEntityRepository customerEntityRepository;

	@Mock
	private WishlistRepository wishlistRepository;

	@BeforeMethod
	public void beforeMethod() throws Exception {
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
		customerEntity.setRefreshToken("token");
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
		ReflectionTestUtils.setField(passwordV2ServiceImpl, "resetPasswordUrl", "url");
		Map<String, String> forgotPasswordMailPatterns = new LinkedHashMap<>();
		Map<String, String> forgotPasswordSubject = new LinkedHashMap<>();
		forgotPasswordMailPatterns.put("en", "test");
		forgotPasswordSubject.put("en", "test");
		ReflectionTestUtils.setField(passwordV2ServiceImpl, "forgotPasswordMailPatterns", forgotPasswordMailPatterns);
		ReflectionTestUtils.setField(passwordV2ServiceImpl, "forgotPasswordSubject", forgotPasswordSubject);
		customerEntity.setPasswordHash("samehash:saltval:1:1");
		when(jwtGenerator.generate(any())).thenReturn("token");
		when(passwordHelper.getSha256Hash(anyString(), anyString())).thenReturn("samehash:saltval:1:1");
		when(emailHelper.sendEmail(anyString(), anyString(), anyString(), any(), anyString(), anyString()))
				.thenReturn(true);
		when(staticComponents.getStoresArray()).thenReturn(storeList);
		JwtUser jwtUser = new JwtUser();
		LocalDateTime currentTime = LocalDateTime.now();
		LocalDateTime previousDay = currentTime.plus(2, ChronoUnit.DAYS);
		Timestamp timestamp = Timestamp.valueOf(previousDay);
		jwtUser.setExpiry(new Date(timestamp.getTime()));
		jwtUser.setUserId("1");
		jwtUser.setCode("5c8b098b97b7829b10f8f68cf49274481ac553371c6132179c6f63af06a94b5a");
		when(jwtValidator.validate(anyString())).thenReturn(jwtUser);
		when(iosSigninHelper.appleAuth(any(), any(), any(), any(), anyBoolean(), anyBoolean())).thenReturn(true);

	}

	@BeforeClass
	public void beforeClass() {
		MockitoAnnotations.initMocks(this);
		requestHeader = new HashMap<>();
		requestHeader.put("x-source", "msite");
		requestHeader.put("x-client-version", "11");
		requestHeader.put("token", "token");
		requestHeader.put("X-Header-Token", "test@mail.com");
		try {
			String storeData = new String(Files.readAllBytes(Paths.get("src/test/resources/stores_list.json")));
			Type listType = new TypeToken<ArrayList<Stores>>() {
			}.getType();
			storeList = new Gson().fromJson(storeData, listType);

		} catch (Exception e) {
		}
	}

	@Test
	public void forgotPasswordTest() throws CustomerException, IOException {
		// Setup request
		CustomerQueryReq customerExitsReq = new CustomerQueryReq();
		customerExitsReq.setUseridentifier("testuser.100@mailinator.com");
		customerExitsReq.setStoreId(1);

		// Mock store list
		String storeData = new String(Files.readAllBytes(Paths.get("src/test/resources/store_list.json")));
		Type listType = new TypeToken<ArrayList<org.styli.services.customer.utility.pojo.config.Stores>>() {}.getType();
		List<org.styli.services.customer.utility.pojo.config.Stores> stores = new Gson().fromJson(storeData, listType);

		// Mock customer entity
		CustomerEntity customerEntity = new CustomerEntity();
		customerEntity.setFirstName("John");
		customerEntity.setLastName("Doe");
		customerEntity.setEmail("testuser.100@mailinator.com");
		when(customerEntityRepository.findByEmail(anyString())).thenReturn(customerEntity);

		// Mock email sending
		when(emailHelper.sendEmail(anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);

		// Test method
		CustomerRestPassResponse respo = passwordV2ServiceImpl.forgotPassword(new HashMap<>(), customerExitsReq);

		// Assertions
		assertEquals(false, respo.isStatus());
		assertEquals("201", respo.getStatusCode());
	}


	@Test
	public void resetTokenPasswordTest() throws CustomerException {
		TokenPasswordRequest tokenPasswordRequest = new TokenPasswordRequest();
		tokenPasswordRequest.setNewPassword("password");
		tokenPasswordRequest.setToken("token");
		when(customerEntityRepository.findByEmail(anyString())).thenReturn(customerEntity);
		when(client.findByEmail(anyString())).thenReturn(customerEntity);
		CustomerRestPassResponse respo = passwordV2ServiceImpl.resetTokenPassword(requestHeader, tokenPasswordRequest);
		assertEquals(respo.isStatus(), true);
		assertEquals(respo.getStatusCode(), "200");
	}

	@Test
	public void refreshTokenTest() throws CustomerException, IOException {
		String requestData = new String(
				Files.readAllBytes(Paths.get("src/test/resources/customer_login_request.json")));
		Gson g = new Gson();
		Map<String, String> requestHeader = null;
		CustomerLoginV4Request CustomerLoginV4Request = g.fromJson(requestData, CustomerLoginV4Request.class);

		when(customerEntityRepository.findByEmail(anyString())).thenReturn(customerEntity);
		CustomerLoginV4Response respo = passwordV2ServiceImpl.refreshToken(requestHeader, CustomerLoginV4Request);
		assertEquals(respo.isStatus(), true);
		assertEquals(respo.getStatusCode(), "200");
	}

}
