package org.styli.services.customer.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.ObjectUtils;
import org.json.JSONObject;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.styli.services.customer.exception.CustomerException;
import org.styli.services.customer.helper.ExternalQuoteHelper;
import org.styli.services.customer.helper.LoginCapchaHelper;
import org.styli.services.customer.helper.PasswordHelper;
import org.styli.services.customer.model.SequenceCustomerEntity;
import org.styli.services.customer.pojo.CustomerRequestBody;
import org.styli.services.customer.pojo.DisabledServices;
import org.styli.services.customer.pojo.QuoteDTO;
import org.styli.services.customer.pojo.address.response.CustomerAddressEntity;
import org.styli.services.customer.pojo.address.response.CustomerAddressEntityVarchar;
import org.styli.services.customer.pojo.epsilon.request.LinkShukranRequest;
import org.styli.services.customer.pojo.webhook.ShukranWebhookRequest;
import org.styli.services.customer.pojo.webhook.ShukranWebhookResponse;
import org.styli.services.customer.pojo.epsilon.request.ShukranEnrollmentRequest;
import org.styli.services.customer.pojo.epsilon.request.UpgradeShukranTierActivityRequest;
import org.styli.services.customer.pojo.epsilon.response.BuildUpgradeShukranTierActivityResponse;
import org.styli.services.customer.pojo.epsilon.response.EnrollmentResponse;
import org.styli.services.customer.pojo.registration.request.CustomerLoginV4Request;
import org.styli.services.customer.pojo.registration.request.CustomerPasswordRequest;
import org.styli.services.customer.pojo.registration.request.CustomerQueryReq;
import org.styli.services.customer.pojo.registration.request.CustomerUpdateProfileRequest;
import org.styli.services.customer.pojo.registration.request.CustomerV4Registration;
import org.styli.services.customer.pojo.registration.request.LoginType;
import org.styli.services.customer.pojo.registration.response.*;
import org.styli.services.customer.pojo.whatsapp.WhatsappSignupBucketObject;
import org.styli.services.customer.redis.RedisHelper;
import org.styli.services.customer.repository.SequenceCustomerEntityRepository;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;
import org.styli.services.customer.repository.Customer.CustomerAddressEntityRepository;
import org.styli.services.customer.service.Client;
import org.styli.services.customer.service.ExternalServiceAdapter;
import org.styli.services.customer.service.WhatsappService;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.consul.ServiceConfigs;
import org.styli.services.customer.utility.pojo.config.StoreConfigResponse;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.google.gson.Gson;

@SpringBootTest(classes = { CustomerV4ServiceImplTest.class })
public class CustomerV4ServiceImplTest extends AbstractTestNGSpringContextTests {
	@Autowired
	private WebApplicationContext webApplicationContext;

	private MockMvc mockMvc;

	private CustomerV4Registration customerRegistration;

	private CustomerV4RegistrationResponse customerV4RegistrationResponse;

	private static Customer cusresponse;

	private static org.styli.services.customer.pojo.registration.request.Customer cusRequest;

	private static CustomerUpdateProfileRequest req;

	@InjectMocks
	private CustomerV4ServiceImpl customerV4ServiceImpl;

	@InjectMocks
	ValidateUser validateUser;

	@InjectMocks
	UpdateUser updateUser;
	@InjectMocks
	Constants constants;

	@Mock
	PasswordHelper passwordHelper;

	@Mock
	RedisHelper redisHelper;

	@Mock
	IosSigninHelper iosSigninHelper;

	@Mock
	GoogleSigninHelper googleSigninHelper;

	@Mock
	StoreConfigResponse storeConfigResponse;

	@Mock
	RestTemplate restTemplate;

	@Mock
	Client client;

	@Mock
	SequenceCustomerEntityRepository sequenceCustomerEntityRepository;

	@InjectMocks
	LoginUser loginUser;

	@InjectMocks
	ServiceConfigs config;

	@InjectMocks
	AddWishlist addWishlist;

	@InjectMocks
	LoginCapchaHelper loginCapchaHelper;

	@Mock
	WhatsappService whatsappService;

	@InjectMocks
	private SaveCustomer saveCustomer;

	private SequenceCustomerEntity entity;

	private CustomerEntity customerEntity;

	private CustomerLoginV4Request customerLoginV4Request;

	private CustomerLoginV4Request customerLoginV4AppleRequest;

	private CustomerLoginV4Request customerLoginV4GoogleRequest;

	private CustomerLoginV4Request customerLoginV4WhatsappRequest;

	private CustomerLoginV4Request customerLoginV4MobileRequest;

	private CustomerLoginV4Response customerLoginV4Response;
	@Mock
	private CustomerEntityRepository customerEntityRepository;
	@Mock
	private org.styli.services.customer.repository.Customer.CustomerAddressEntityRepository customerAddressEntityRepository;
	@Mock
	private ExternalServiceAdapter externalServiceAdapter;
	@Mock
	private OtpServiceImpl otpService;

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
	}

	@BeforeClass
	public void beforeClass() {
		try {
			// Prepare mock Data
			String reponseData = new String(
					Files.readAllBytes(Paths.get("src/test/resources/customer_login_reponse.json")));
			String requestData = new String(
					Files.readAllBytes(Paths.get("src/test/resources/customer_login_request.json")));
			String requestAppleData = new String(
					Files.readAllBytes(Paths.get("src/test/resources/customer_login_request_apple.json")));
			String requestGoogleData = new String(
					Files.readAllBytes(Paths.get("src/test/resources/customer_login_request_google.json")));
			String requestWhatsappData = new String(
					Files.readAllBytes(Paths.get("src/test/resources/customer_login_request_whatsapp.json")));
			String requestMobileData = new String(
					Files.readAllBytes(Paths.get("src/test/resources/customer_login_request_mobile.json")));

			Gson g = new Gson();
			customerLoginV4Response = g.fromJson(reponseData, CustomerLoginV4Response.class);
			customerLoginV4Request = g.fromJson(requestData, CustomerLoginV4Request.class);
			customerLoginV4AppleRequest = g.fromJson(requestAppleData, CustomerLoginV4Request.class);
			customerLoginV4GoogleRequest = g.fromJson(requestGoogleData, CustomerLoginV4Request.class);
			customerLoginV4WhatsappRequest = g.fromJson(requestWhatsappData, CustomerLoginV4Request.class);
			customerLoginV4MobileRequest = g.fromJson(requestMobileData, CustomerLoginV4Request.class);
		} catch (Exception e) {
		}
	}

	@BeforeTest
	public void beforeTest() {
		MockitoAnnotations.initMocks(this);
	}

	/**
	 * @throws CustomerException
	 * @throws NoSuchAlgorithmException
	 */
	@Test
	public void getCustomerLoginV4DetailsTest() throws CustomerException, NoSuchAlgorithmException {
		// set common static fields
		setStaticfields();
		customerEntity.setPasswordHash("samehash:saltval:1:1");
		when(passwordHelper.getSha256Hash(anyString(), anyString())).thenReturn("samehash:saltval:1:1");
		Map<String, String> requestHeader = new HashMap<>();
		CustomerLoginV4Response response = customerV4ServiceImpl.getCustomerLoginV4Details(customerLoginV4Request,
				requestHeader);
		assertEquals(response.getStatusCode(), "400");
		assertEquals(response.getResponse(), null);
	}

	@Test
	public void getCustomerLoginV4DetailsArgonhashTest() throws CustomerException, NoSuchAlgorithmException {
		setStaticfields();
		customerEntity.setPasswordHash("samehash:saltval:2:2");
		customerEntity.setAgeGroupId(1);
		customerEntity.setGender(1);
		when(client.findByEmail(any())).thenReturn(customerEntity);
		when(client.findByPhoneNumber(any())).thenReturn(customerEntity);
		when(passwordHelper.getArgon2Id13Hash(anyString(), anyString())).thenReturn("samehash:saltval:2:2");
		Map<String, String> requestHeader = new HashMap<>();
		CustomerLoginV4Response response = customerV4ServiceImpl.getCustomerLoginV4Details(customerLoginV4Request,
				requestHeader);
		assertEquals(response.getStatusCode(), "400");

	}

	@Test
	public void getCustomerLoginV4DetailsAppleLoginTest() throws Exception {
		setStaticfields();
		when(client.findByEmail(any())).thenReturn(customerEntity);
		when(client.findByPhoneNumber(any())).thenReturn(customerEntity);
		customerEntity.setPasswordHash("samehash:saltval:1:1");
		when(client.findByEmail(any())).thenReturn(customerEntity);
		when(client.findByPhoneNumber(any())).thenReturn(customerEntity);
		when(passwordHelper.getSha256Hash(anyString(), anyString())).thenReturn("samehash:saltval:1:1");
		when(iosSigninHelper.appleAuth(any(), any(), any(), any(), anyBoolean(), anyBoolean()))
				.thenReturn(true);
		Map<String, String> requestHeader = new HashMap<>();
		requestHeader.put("x-source", "msite");
		CustomerLoginV4Response response = customerV4ServiceImpl.getCustomerLoginV4Details(customerLoginV4AppleRequest,
				requestHeader);
		assertEquals(response.getStatusCode(), "400");
	}

	@Test
	public void getCustomerLoginV4DetailsGoogleLoginTest() throws Exception {
		setStaticfields();
		customerEntity.setPasswordHash("samehash:saltval:1:1");
		when(client.findByEmail(any())).thenReturn(customerEntity);
		when(client.findByPhoneNumber(any())).thenReturn(customerEntity);
		when(passwordHelper.getSha256Hash(anyString(), anyString())).thenReturn("samehash:saltval:1:1");
		when(googleSigninHelper.validateGoogleSignin(any(), any(), any())).thenReturn(true);
		Map<String, String> requestHeader = new HashMap<>();
		requestHeader.put("x-source", "msite");
		CustomerLoginV4Response response = customerV4ServiceImpl.getCustomerLoginV4Details(customerLoginV4GoogleRequest,
				requestHeader);
		assertEquals(response.getStatusCode(), "400");

	}

	@Test
	public void getCustomerLoginV4DetailsGoogleLoginregistationTest() throws Exception {
		setStaticfields();
		customerEntity.setPasswordHash("samehash:saltval:1:1");
		Map<String, Object> newConfigs = new LinkedHashMap<>();
		newConfigs.put("blockRegistration", false);
		ReflectionTestUtils.setField(config, "consulServiceMap", newConfigs);

		when(client.findByEmail(any())).thenReturn(null);
		when(client.findByPhoneNumber(any())).thenReturn(customerEntity);
		when(passwordHelper.getSha256Hash(anyString(), anyString())).thenReturn("samehash:saltval:1:1");
		when(googleSigninHelper.validateGoogleSignin(any(), any(), any())).thenReturn(true);
		Map<String, String> requestHeader = new HashMap<>();
		requestHeader.put("x-source", "msite");
		CustomerLoginV4Response response = customerV4ServiceImpl.getCustomerLoginV4Details(customerLoginV4GoogleRequest,
				requestHeader);
		assertEquals(response.getStatusCode(), "400");

	}

	@Test
	public void getCustomerLoginV4DetailsGoogleLoginRegistrationBlockTest() throws Exception {
		setStaticfields();
		Map<String, Object> newConfigs = new LinkedHashMap<>();
		newConfigs.put("blockRegistration", true);
		ReflectionTestUtils.setField(config, "consulServiceMap", newConfigs);

		when(client.findByEmail(any())).thenReturn(null);
		when(client.findByPhoneNumber(any())).thenReturn(customerEntity);
		customerEntity.setPasswordHash("samehash:saltval:1:1");
		when(client.findByPhoneNumber(any())).thenReturn(customerEntity);
		when(passwordHelper.getSha256Hash(anyString(), anyString())).thenReturn("samehash:saltval:1:1");
		when(googleSigninHelper.validateGoogleSignin(any(), any(), any())).thenReturn(true);
		Map<String, String> requestHeader = new HashMap<>();
		requestHeader.put("x-source", "msite");
		CustomerLoginV4Response response = customerV4ServiceImpl.getCustomerLoginV4Details(customerLoginV4GoogleRequest,
				requestHeader);
		assertEquals(response.getResponse(), null);
	}

	@Test
	public void getCustomerLoginV4DetailsWhatsappLoginTest() throws Exception {
		setStaticfields();
		customerEntity.setPasswordHash("samehash:saltval:1:1");
		when(client.findByEmail(any())).thenReturn(customerEntity);
		when(client.findByPhoneNumber(any())).thenReturn(customerEntity);
		when(passwordHelper.getSha256Hash(anyString(), anyString())).thenReturn("samehash:saltval:1:1");
		WhatsappSignupBucketObject bucket = new WhatsappSignupBucketObject();
		bucket.setMobileNo("8972167898");
		when(whatsappService.getValidPayloadFromToken(anyString())).thenReturn(bucket);
		Map<String, String> requestHeader = new HashMap<>();
		requestHeader.put("x-source", "msite");
		CustomerLoginV4Response response = customerV4ServiceImpl
				.getCustomerLoginV4Details(customerLoginV4WhatsappRequest, requestHeader);
		assertEquals(response.getStatusCode(), "400");
		assertEquals(response.getResponse(), null);

	}

	@Test
	public void getCustomerLoginV4DetailsWhatsappLoginRegistrationTest() throws Exception {
		setStaticfields();
		Map<String, Object> newConfigs = new LinkedHashMap<>();
		newConfigs.put("blockRegistration", false);
		ReflectionTestUtils.setField(config, "consulServiceMap", newConfigs);

		customerEntity.setPasswordHash("samehash:saltval:1:1");
		when(client.findByEmail(any())).thenReturn(null);
		when(client.findByPhoneNumber(any())).thenReturn(null);
		when(client.saveAndFlushCustomerEntity(any())).thenReturn(customerEntity);
		when(passwordHelper.getSha256Hash(anyString(), anyString())).thenReturn("samehash:saltval:1:1");
		WhatsappSignupBucketObject bucket = new WhatsappSignupBucketObject();
		bucket.setMobileNo("8972167898");
		when(whatsappService.getValidPayloadFromToken(anyString())).thenReturn(bucket);
		Map<String, String> requestHeader = new HashMap<>();
		requestHeader.put("x-source", "msite");
		CustomerLoginV4Response response = customerV4ServiceImpl
				.getCustomerLoginV4Details(customerLoginV4WhatsappRequest, requestHeader);
		assertEquals(response.getStatusCode(), "400");
		assertEquals(response.getResponse(), null);
	}

	@Test
	public void getCustomerLoginV4DetailsWhatsappLoginRegistrationFailTest() throws Exception {
		setStaticfields();
		Map<String, Object> newConfigs = new LinkedHashMap<>();
		newConfigs.put("blockRegistration", true);
		ReflectionTestUtils.setField(config, "consulServiceMap", newConfigs);

		customerEntity.setPasswordHash("samehash:saltval:1:1");
		when(client.findByEmail(any())).thenReturn(null);
		when(client.findByPhoneNumber(any())).thenReturn(null);
		when(client.saveAndFlushCustomerEntity(any())).thenReturn(customerEntity);
		when(passwordHelper.getSha256Hash(anyString(), anyString())).thenReturn("samehash:saltval:1:1");
		WhatsappSignupBucketObject bucket = new WhatsappSignupBucketObject();
		bucket.setMobileNo("8972167898");
		when(whatsappService.getValidPayloadFromToken(anyString())).thenReturn(bucket);
		Map<String, String> requestHeader = new HashMap<>();
		requestHeader.put("x-source", "msite");
		CustomerLoginV4Response response = customerV4ServiceImpl
				.getCustomerLoginV4Details(customerLoginV4WhatsappRequest, requestHeader);
		assertEquals(response.getStatusCode(), "400");
	}

	@Test
	public void getCustomerLoginV4DetailsMobileTest() throws Exception {
		setStaticfields();
		when(client.findByEmail(any())).thenReturn(customerEntity);
		when(client.findByPhoneNumber(any())).thenReturn(customerEntity);
		customerEntity.setPasswordHash("samehash:saltval:1:1");
		when(passwordHelper.getSha256Hash(anyString(), anyString())).thenReturn("samehash:saltval:1:1");
		WhatsappSignupBucketObject bucket = new WhatsappSignupBucketObject();
		bucket.setMobileNo("8972167898");
		when(whatsappService.getValidPayloadFromToken(anyString())).thenReturn(bucket);
		Map<String, String> requestHeader = new HashMap<>();
		requestHeader.put("x-source", "msite");
		CustomerLoginV4Response response = customerV4ServiceImpl.getCustomerLoginV4Details(customerLoginV4MobileRequest,
				requestHeader);
		assertEquals(response.getStatusCode(), "400");
		assertEquals(response.getResponse(), null);

	}

	@Test
	public void getCustomerDetailsTest() throws Exception {
		setStaticfields();
		when(client.findByEmail(any())).thenReturn(customerEntity);
		when(client.findByPhoneNumber(any())).thenReturn(customerEntity);
		when(client.findByEntityId(anyInt())).thenReturn(customerEntity);
		when(client.saveAndFlushCustomerEntity(any())).thenReturn(customerEntity);
		when(client.findAddressByEntityId(any())).thenReturn(mockCustomerAddEntity());
		when(customerAddressEntityRepository.findAllByCustomerId(anyInt())).thenReturn(new ArrayList<>());
		customerEntity.setJwtToken(1);
		customerEntity.setDefaultShipping(1);
		customerEntity.setPhoneNumber("+971 988786332");
		customerEntity.setShukranLinkFlag(true);
		customerEntity.setProfileId("abd29fb6-4ed7-4f18-814d-d6391435e79e");
		customerEntity.setCardNumber("1700000522640167");
		customerEntity.setDefaultShipping(1);
		when(restTemplate.exchange(any(),eq(String.class))).thenReturn(buildMockResponseEntity());
		when(externalServiceAdapter.getEpsilonProfile(any(),any())).thenReturn(buildMockResponseEntity());
		Map<String, String> requestHeader = new HashMap<>();
		CustomerRequestBody request = new CustomerRequestBody();
		request.setCustomerId(536373);
		request.setStoreId(1);
		request.setCustomerEmail("test@mailinator.com");
		CustomerUpdateProfileResponse response = customerV4ServiceImpl.getCustomerDetails(request,requestHeader);
		assertEquals(response.getStatusCode(), "200");
		assertNotNull(response.getResponse());
		assertEquals(response.getResponse().getCustomer().getEmail(), customerEntity.getEmail());
	}

	@Test
	public void recordNudgeSeenTest_Success() throws Exception {
		setStaticfields();
		when(client.findByEntityId(anyInt())).thenReturn(customerEntity);
		when(client.saveAndFlushCustomerEntity(any())).thenReturn(customerEntity);
		
		Map<String, String> requestHeader = new HashMap<>();
		CustomerRequestBody request = new CustomerRequestBody();
		request.setCustomerId(536373);
		
		org.styli.services.customer.pojo.GenericApiResponse<String> response = 
			customerV4ServiceImpl.recordNudgeSeen(request, requestHeader);
		
		assertNotNull(response);
		assertTrue(response.getStatus());
		assertEquals("200", response.getStatusCode());
		assertEquals("Nudge seen timestamp recorded successfully", response.getStatusMsg());
		assertNotNull(customerEntity.getNudgeSeenTime());
		verify(client).findByEntityId(536373);
		verify(client).saveAndFlushCustomerEntity(customerEntity);
	}

	@Test
	public void recordNudgeSeenTest_CustomerNotFound() throws Exception {
		setStaticfields();
		when(client.findByEntityId(anyInt())).thenReturn(null);
		
		Map<String, String> requestHeader = new HashMap<>();
		CustomerRequestBody request = new CustomerRequestBody();
		request.setCustomerId(536373);
		
		org.styli.services.customer.pojo.GenericApiResponse<String> response = 
			customerV4ServiceImpl.recordNudgeSeen(request, requestHeader);
		
		assertNotNull(response);
		assertFalse(response.getStatus());
		assertEquals("404", response.getStatusCode());
		assertEquals("Customer not found", response.getStatusMsg());
		verify(client).findByEntityId(536373);
		verify(client, never()).saveAndFlushCustomerEntity(any());
	}

	@Test
	public void recordNudgeSeenTest_NullCustomerId() throws Exception {
		setStaticfields();
		
		Map<String, String> requestHeader = new HashMap<>();
		CustomerRequestBody request = new CustomerRequestBody();
		request.setCustomerId(null);
		
		org.styli.services.customer.pojo.GenericApiResponse<String> response = 
			customerV4ServiceImpl.recordNudgeSeen(request, requestHeader);
		
		assertNotNull(response);
		assertFalse(response.getStatus());
		assertEquals("400", response.getStatusCode());
		assertEquals("Customer ID is required", response.getStatusMsg());
		verify(client, never()).findByEntityId(anyInt());
		verify(client, never()).saveAndFlushCustomerEntity(any());
	}

	@Test
	public void getCustomerDetailsTest_failure() throws Exception {
		setStaticfields();
		when(client.findByEmail(any())).thenReturn(customerEntity);
		when(client.findByPhoneNumber(any())).thenReturn(customerEntity);
		when(client.findByEntityId(anyInt())).thenReturn(customerEntity);
		when(client.saveAndFlushCustomerEntity(any())).thenReturn(customerEntity);
		when(client.findAddressByEntityId(any())).thenReturn(mockCustomerAddEntity());
		customerEntity.setJwtToken(1);
		customerEntity.setDefaultShipping(1);
		customerEntity.setPhoneNumber("+971 988786332");
		customerEntity.setShukranLinkFlag(true);
		customerEntity.setProfileId("abd29fb6-4ed7-4f18-814d-d6391435e79e");
		customerEntity.setCardNumber("1700000522640167");
		customerEntity.setDefaultShipping(1);
		when(restTemplate.exchange(any(),eq(String.class))).thenReturn(new ResponseEntity<>("{}",HttpStatus.BAD_REQUEST));
		when(externalServiceAdapter.getEpsilonProfile(any(),any())).thenReturn(new ResponseEntity<>("{}",HttpStatus.BAD_REQUEST));
		Map<String, String> requestHeader = new HashMap<>();
		// requestHeader.put("x-source", "msite");
		CustomerRequestBody request = new CustomerRequestBody();
		request.setCustomerId(536373);
		request.setStoreId(1);
		request.setCustomerEmail("test@mailinator.com");
		CustomerUpdateProfileResponse response = customerV4ServiceImpl.getCustomerDetails(request, requestHeader);
		assertEquals(response.getStatusCode(), "200");
		assertNotNull(response.getResponse());
		assertEquals(response.getResponse().getCustomer().getEmail(), customerEntity.getEmail());
	}



	private ResponseEntity<String> buildMockResponseEntity() throws IOException{
		String responseData = new String(
				Files.readAllBytes(Paths.get("src/test/resources/epsilon_get_response.json")));
		return new ResponseEntity<String>(responseData, HttpStatus.OK);
	}

	@Test
	public void updateCustomeromstrueTest() throws Exception {
		setStaticfields();
		String requestData = new String(
				Files.readAllBytes(Paths.get("src/test/resources/customer_update_request.json")));
		Gson g = new Gson();
		CustomerUpdateProfileRequest customerUpdateProfileRequest = g.fromJson(requestData,
				CustomerUpdateProfileRequest.class);
		customerUpdateProfileRequest.setOmsRequest(true);
		when(client.findByEntityId(any())).thenReturn(customerEntity);
		when(client.findByEmail(any())).thenReturn(customerEntity);
		when(client.findByPhoneNumber(any())).thenReturn(customerEntity);
		when(client.saveAndFlushCustomerEntity(any())).thenReturn(customerEntity);
		customerEntity.setJwtToken(1);
		customerEntity.setDefaultShipping(1);
		customerEntity.setPhoneNumber("+971 988786332");
		customerEntity.setShukranLinkFlag(true);
		customerEntity.setProfileId("abd29fb6-4ed7-4f18-814d-d6391435e79e");
		customerEntity.setCardNumber("1700000522640167");
		customerEntity.setDefaultShipping(1);
		when(restTemplate.exchange(any(),eq(String.class))).thenReturn(buildMockUpdateResponseEntity());
		when(externalServiceAdapter.updateEpsilonProfile(any(),any())).thenReturn(buildMockUpdateResponseEntity());
		CustomerQueryReq customerExitsReq = new CustomerQueryReq();
		Map<String, String> requestHeader = new HashMap<>();
		requestHeader.put("x-source", "msite");
		CustomerUpdateProfileResponse response = customerV4ServiceImpl.updateCustomer(customerUpdateProfileRequest,
				requestHeader);
		assertEquals(response.getStatusCode(), "200");
		assertNotNull(response.getResponse());
	}

	private ResponseEntity<String> buildMockUpdateResponseEntity() throws IOException{
		String updateResponseData = new String(
				Files.readAllBytes(Paths.get("src/test/resources/epsilon_update_response.json")));

		return new ResponseEntity<String>(updateResponseData,HttpStatus.OK);
	}

	@Test
	public void updateCustomerTest() throws Exception {
		setStaticfields();
		String requestData = new String(
				Files.readAllBytes(Paths.get("src/test/resources/customer_update_request.json")));
		Gson g = new Gson();
		CustomerUpdateProfileRequest customerUpdateProfileRequest = g.fromJson(requestData,
				CustomerUpdateProfileRequest.class);
		when(client.findByEntityId(any())).thenReturn(customerEntity);
		when(client.findByEmail(any())).thenReturn(customerEntity);
		when(client.findByPhoneNumber(any())).thenReturn(customerEntity);
		when(client.saveAndFlushCustomerEntity(any())).thenReturn(customerEntity);
		CustomerQueryReq customerExitsReq = new CustomerQueryReq();
		Map<String, String> requestHeader = new HashMap<>();
		requestHeader.put("x-source", "msite");
		CustomerUpdateProfileResponse response = customerV4ServiceImpl.updateCustomer(customerUpdateProfileRequest,
				requestHeader);
		assertEquals(response.getStatusCode(), "200");
		assertNotNull(response.getResponse());
	}

	@Test
	public void validateUserTest() {
		setStaticfields();
		when(client.findByEmail(any())).thenReturn(null);
		when(client.findByPhoneNumber(any())).thenReturn(null);
		CustomerQueryReq customerExitsReq = new CustomerQueryReq();
		customerExitsReq.setUseridentifier("testuser.100@mailinator.com");
		customerExitsReq.setStoreId(1);
		customerExitsReq.setLoginType(LoginType.EMAIL_LOGIN);
		Map<String, String> requestHeader = null;
		CustomerExistResponse response = customerV4ServiceImpl.validateUser(customerExitsReq, requestHeader);
		assertEquals(response.getStatusCode(), "201");
		assertNotNull(response.getResponse());
	}

	@Test
	public void validateUserEailTest() {
		setStaticfields();
		when(client.findByEmail(any())).thenReturn(customerEntity);
		when(client.findByPhoneNumber(any())).thenReturn(customerEntity);
		CustomerQueryReq customerExitsReq = new CustomerQueryReq();
		customerExitsReq.setUseridentifier("testuser.100@mailinator.com");
		customerExitsReq.setStoreId(1);
		customerExitsReq.setLoginType(LoginType.EMAIL_LOGIN);
		Map<String, String> requestHeader = null;
		CustomerExistResponse response = customerV4ServiceImpl.validateUser(customerExitsReq, requestHeader);
		assertEquals(response.getStatusCode(), "200");
		assertNotNull(response.getResponse());
	}

	@Test
	public void validateUserMobileTest() {
		setStaticfields();
		when(client.findByEmail(any())).thenReturn(customerEntity);
		when(client.findByPhoneNumber(any())).thenReturn(customerEntity);
		CustomerQueryReq customerExitsReq = new CustomerQueryReq();
		customerExitsReq.setUseridentifier("testuser.100@mailinator.com");
		customerExitsReq.setStoreId(1);
		customerExitsReq.setLoginType(LoginType.MOBILE_LOGIN);
		Map<String, String> requestHeader = new HashMap<>();
		requestHeader.put("x-source", "msite");
		CustomerExistResponse response = customerV4ServiceImpl.validateUser(customerExitsReq, requestHeader);
		assertEquals(response.getStatusCode(), "200");
		assertNotNull(response.getResponse());
	}

	@Test
	public void validateUserMobileEmailTest() {
		setStaticfields();
		when(client.findByEmail(any())).thenReturn(customerEntity);
		when(client.findByPhoneNumber(any())).thenReturn(customerEntity);
		CustomerQueryReq customerExitsReq = new CustomerQueryReq();
		customerExitsReq.setUseridentifier("testuser.100@mailinator.com");
		customerExitsReq.setStoreId(1);
		customerExitsReq.setLoginType(LoginType.MOBILE_LOGIN);
		Map<String, String> requestHeader = new HashMap<>();
		requestHeader.put("x-source", "msite");
		CustomerExistResponse response = customerV4ServiceImpl.validateUser(customerExitsReq, requestHeader);
		assertEquals(response.getStatusCode(), "200");
		assertNotNull(response.getResponse());
	}

	@Test
	public void getCustomerLoginV4DetailsAppleLoginRegistrationTest() throws Exception {
		CustomerLoginV4Request customerLoginV4Request1= new CustomerLoginV4Request();
		setStaticfields();
		Map<String, Object> newConfigs = new LinkedHashMap<>();
		newConfigs.put("blockRegistration", false);
		ReflectionTestUtils.setField(config, "consulServiceMap", newConfigs);

		when(client.findByEmail(any())).thenReturn(null);
		when(client.findByPhoneNumber(any())).thenReturn(customerEntity);
		customerEntity.setPasswordHash("samehash:saltval:1:1");
		when(client.findByPhoneNumber(any())).thenReturn(customerEntity);
		when(passwordHelper.getSha256Hash(anyString(), anyString())).thenReturn("samehash:saltval:1:1");
		when(iosSigninHelper.appleAuth(any(), any(), any(), any(), anyBoolean(), anyBoolean()))
				.thenReturn(true);
		Map<String, String> requestHeader = new HashMap<>();
		requestHeader.put("x-source", "msite");
		customerLoginV4Request1.setFullName("Test Name");
		CustomerLoginV4Response response = customerV4ServiceImpl.getCustomerLoginV4Details(customerLoginV4Request1,
				requestHeader);
		assertEquals(response.getStatusCode(), "204");
		assertEquals(response.getResponse(), null);
	}

	@Test
	public void getCustomerLoginV4DetailsAppleLoginRegistrationBlockTest() throws Exception {
		setStaticfields();
		Map<String, Object> newConfigs = new LinkedHashMap<>();
		newConfigs.put("blockRegistration", true);
		ReflectionTestUtils.setField(config, "consulServiceMap", newConfigs);

		when(client.findByEmail(any())).thenReturn(null);
		when(client.findByPhoneNumber(any())).thenReturn(customerEntity);
		customerEntity.setPasswordHash("samehash:saltval:1:1");
		when(client.findByPhoneNumber(any())).thenReturn(customerEntity);
		when(passwordHelper.getSha256Hash(anyString(), anyString())).thenReturn("samehash:saltval:1:1");
		when(iosSigninHelper.appleAuth(any(), any(), any(), any(), anyBoolean(), anyBoolean()))
				.thenReturn(true);
		Map<String, String> requestHeader = new HashMap<>();
		requestHeader.put("x-source", "msite");
		CustomerLoginV4Response response = customerV4ServiceImpl.getCustomerLoginV4Details(customerLoginV4AppleRequest,
				requestHeader);
		assertEquals(response.getStatusCode(), "400");
		assertEquals(response.getResponse(), null);
	}

	@Test
	public void changePasswordTest() throws Exception {
		CustomerPasswordRequest request = new CustomerPasswordRequest();
		request.setCustomerId(1);
		request.setCurrentPassword("");
		request.setNewPassword("");
		CustomerRestPassResponse response = customerV4ServiceImpl.changePassword(request);
		assertEquals(response.isStatus(), false);
		assertEquals(response.getStatusCode(), "204");
	}

	@Test
	public void zchangePasswordExcepTest() throws Exception {
		CustomerPasswordRequest request = new CustomerPasswordRequest();
		request.setCustomerId(1);
		request.setCurrentPassword("currpass");
		request.setNewPassword("newpass");
		when(client.findByEntityId(anyInt())).thenAnswer(invocation -> {
			throw new NoSuchAlgorithmException("Example checked exception");
		});
		CustomerRestPassResponse response = customerV4ServiceImpl.changePassword(request);
		assertEquals(response.isStatus(), false);
		assertEquals(response.getStatusCode(), "500");
	}

	private void setStaticfields() {
		CustomerLoginV4Request customerLoginV4Request1= new CustomerLoginV4Request();
		ReflectionTestUtils.setField(customerV4ServiceImpl, "jwtFlag", "1");
		ReflectionTestUtils.setField(customerV4ServiceImpl, "loginUser", loginUser);
		ReflectionTestUtils.setField(customerV4ServiceImpl, "saveCustomer", saveCustomer);
		ReflectionTestUtils.setField(customerV4ServiceImpl, "recaptchaSecretKey", "cccvevevee");
		ReflectionTestUtils.setField(customerV4ServiceImpl, "whatsappService", whatsappService);
		ReflectionTestUtils.setField(customerV4ServiceImpl, "secretReactJavaApi", customerLoginV4Request1.getPassword());
		ReflectionTestUtils.setField(customerV4ServiceImpl, "addWishlist", addWishlist);
		ReflectionTestUtils.setField(customerV4ServiceImpl, "validateUser", validateUser);
		ReflectionTestUtils.setField(customerV4ServiceImpl, "updateUser", updateUser);
		ReflectionTestUtils.setField(customerV4ServiceImpl, "externalServiceAdapter", externalServiceAdapter);
		ReflectionTestUtils.setField(customerV4ServiceImpl, "restTemplate", restTemplate);
		DisabledServices ser = new DisabledServices(true, true, true);
		StoreConfigResponse serRespo = new StoreConfigResponse();
		serRespo.setDisabledServices(ser);
		ReflectionTestUtils.setField(constants, "StoreConfigResponse", serRespo);
	}

	private CustomerAddressEntity mockCustomerAddEntity() {
		CustomerAddressEntity customerAddressEntity = new CustomerAddressEntity();
		customerAddressEntity.setCity("kjr");
		customerAddressEntity.setCountryId("IN");
		customerAddressEntity.setFirstname("first");
		customerAddressEntity.setLastName("last");
		customerAddressEntity.setLatitude(new BigDecimal(100));
		customerAddressEntity.setLongitude(new BigDecimal(200));
		customerAddressEntity.setTelephone("+919887863323");
		CustomerAddressEntityVarchar customerAddressEntityVarchar = new CustomerAddressEntityVarchar();
		customerAddressEntityVarchar.setCustomerAddressEntity(customerAddressEntity);
		Set<CustomerAddressEntityVarchar> set = new HashSet<>();
		set.add(customerAddressEntityVarchar);
		customerAddressEntity.setCustomerAddressEntityVarchar(null);
		customerAddressEntity.setStreet(null);
		return customerAddressEntity;
	}

	@Test
	public void testEnrollShukranAccount_EnrollmentApiFailure() throws Exception{
		setStaticfields();
		ShukranEnrollmentRequest request = ShukranEnrollmentRequest.builder()
				.customerId(6559).build();
		Map<String, String> requestHeader = new HashMap<>();
		when(client.findByEntityId(anyInt())).thenReturn(customerEntity);
		when(client.findByEmail(any())).thenReturn(customerEntity);
		when(client.findByPhoneNumber(any())).thenReturn(customerEntity);
		when(client.saveAndFlushCustomerEntity(any())).thenReturn(customerEntity);
		when(client.findAddressByEntityId(any())).thenReturn(mockCustomerAddEntity());
		customerEntity.setJwtToken(1);
		customerEntity.setDefaultShipping(1);
		customerEntity.setPhoneNumber("+971 988786332");
		customerEntity.setShukranLinkFlag(false);
		customerEntity.setDefaultShipping(1);
		ResponseEntity<String> responseEntity = new ResponseEntity<>("Error enrolling account", HttpStatus.BAD_REQUEST);
		when(restTemplate.exchange(any(), eq(String.class))).thenReturn(responseEntity);
		when(externalServiceAdapter.createShukranAccount(any(),any())).thenReturn(responseEntity);
		EnrollmentResponse response = customerV4ServiceImpl.enrollShukranAccount(request, requestHeader);
		assertNotNull(response);
		assertTrue(response.isStatus());
		assertEquals("201", response.getStatusCode());

	}

	@Test
	public void testEnrollShukranAccount_ExceptionDuringApiCall() throws Exception{
		setStaticfields();
		ShukranEnrollmentRequest request = ShukranEnrollmentRequest.builder()
				.customerId(6559).build();
		Map<String, String> requestHeader = new HashMap<>();
		when(client.findByEntityId(anyInt())).thenReturn(customerEntity);
		when(client.findByEmail(any())).thenReturn(customerEntity);
		when(client.findByPhoneNumber(any())).thenReturn(customerEntity);
		when(client.saveAndFlushCustomerEntity(any())).thenReturn(customerEntity);
		when(client.findAddressByEntityId(any())).thenReturn(mockCustomerAddEntity());
		customerEntity.setJwtToken(1);
		customerEntity.setDefaultShipping(1);
		customerEntity.setPhoneNumber("+971 988786332");
		customerEntity.setShukranLinkFlag(false);
		customerEntity.setDefaultShipping(1);
		when(restTemplate.exchange(any(), eq(String.class))).thenThrow(new RuntimeException("Service unavailable"));
		when(externalServiceAdapter.createShukranAccount(any(), any())).thenThrow(new RuntimeException("Service unavailable"));

		EnrollmentResponse response = customerV4ServiceImpl.enrollShukranAccount(request, requestHeader);

		// Verify response and assertions
		assertNotNull(response);
		assertEquals("500", response.getStatusCode());
	}

	@Test
	public void testExceptionHandlingDuringLinking() throws Exception{
		setStaticfields();
        LinkShukranRequest mockLinkShukranRequest = LinkShukranRequest.builder().customerId(6559)
				.profileId("e7114135-4c55-4af6-bd91-fddf768a4df8").build();
		Map<String, String> requestHeader = new HashMap<>();
		when(client.findByEntityId(anyInt())).thenReturn(customerEntity);
		when(client.findByEmail(any())).thenReturn(customerEntity);
		when(client.findByPhoneNumber(any())).thenReturn(customerEntity);
		when(client.saveAndFlushCustomerEntity(any())).thenReturn(customerEntity);
		when(client.findAddressByEntityId(any())).thenReturn(mockCustomerAddEntity());
		customerEntity.setJwtToken(1);
		customerEntity.setDefaultShipping(1);
		customerEntity.setPhoneNumber("+971 988786332");
		customerEntity.setShukranLinkFlag(false);
		customerEntity.setDefaultShipping(1);
        when(restTemplate.exchange(any(), eq(String.class)))
                .thenThrow(new RuntimeException("External service error"));
        EnrollmentResponse response = customerV4ServiceImpl.linkShukranAccount(mockLinkShukranRequest, requestHeader);
		assertNotNull(response);
		assertEquals("201", response.getStatusCode());
	}

	@Test
	public void testShukranAccountLinkingFailure() throws Exception {
		setStaticfields();
		// Mock request
		LinkShukranRequest mockLinkShukranRequest = LinkShukranRequest.builder()
				.customerId(6559)
				.profileId("e7114135-4c55-4af6-bd91-fddf768a4df8")
				.build();

		when(client.findByEntityId(anyInt())).thenReturn(customerEntity);
		when(client.findByEmail(any())).thenReturn(customerEntity);
		when(client.findByPhoneNumber(any())).thenReturn(customerEntity);
		when(client.saveAndFlushCustomerEntity(any())).thenReturn(customerEntity);
		when(client.findAddressByEntityId(any())).thenReturn(mockCustomerAddEntity());
		customerEntity.setJwtToken(1);
		customerEntity.setDefaultShipping(1);
		customerEntity.setPhoneNumber("+971 988786332");
		customerEntity.setDefaultShipping(1);
		// Mock external service behavior
		when(restTemplate.exchange(any(), eq(String.class)))
				.thenReturn(buildMockResponseEntity());
		when(externalServiceAdapter.getEpsilonProfile(any(),any())).thenReturn(buildMockResponseEntity());
		when(restTemplate.exchange(any(),eq(String.class))).thenReturn(new ResponseEntity<>("{}", HttpStatus.BAD_REQUEST));
		when(externalServiceAdapter.linkShukranAccount(any())).thenReturn(new ResponseEntity<>("{}", HttpStatus.BAD_REQUEST));
		// Execute method under test
		EnrollmentResponse response = customerV4ServiceImpl.linkShukranAccount(mockLinkShukranRequest, Map.of());

		// Assert response
		assertNotNull(response, "Response should not be null");
		assertTrue(response.isStatus(), "Status should be false for a failed linking");
		assertEquals("201", response.getStatusCode());
	}

	@Test
	public void testSuccessfulLinkingShukranAccount() throws Exception {
		setStaticfields();
		LinkShukranRequest mockLinkShukranRequest = LinkShukranRequest.builder().customerId(6559)
				.profileId("e7114135-4c55-4af6-bd91-fddf768a4df8").shukranLinkFlag(true).build();
		Map<String, String> requestHeader = new HashMap<>();
		when(client.findByEntityId(anyInt())).thenReturn(customerEntity);
		when(client.findByEmail(any())).thenReturn(customerEntity);
		when(client.findByPhoneNumber(any())).thenReturn(customerEntity);
		when(client.saveAndFlushCustomerEntity(any())).thenReturn(customerEntity);
		when(client.findAddressByEntityId(any())).thenReturn(mockCustomerAddEntity());
		customerEntity.setJwtToken(1);
		customerEntity.setDefaultShipping(1);
		customerEntity.setPhoneNumber("+971 988786332");
		customerEntity.setDefaultShipping(1);
		when(restTemplate.exchange(any(),eq(String.class))).thenReturn(buildMockResponseEntity());
		when(externalServiceAdapter.getEpsilonProfile(any(),any())).thenReturn(buildMockResponseEntity());
		when(restTemplate.exchange(any(),eq(String.class))).thenReturn(buildMockUpdateResponseEntity());
		when(externalServiceAdapter.linkShukranAccount(any())).thenReturn(buildMockUpdateResponseEntity());
		EnrollmentResponse mockEnrollmentResponse = customerV4ServiceImpl.linkShukranAccount(mockLinkShukranRequest,requestHeader);
		assertEquals("200", mockEnrollmentResponse.getStatusCode());
	}

	@Test
	public void testSuccessfulUpgradeShukranAccount() throws Exception {
		setStaticfields();
		UpgradeShukranTierActivityRequest mockRequest = new UpgradeShukranTierActivityRequest();
		mockRequest.setCustomerId(512343);
		mockRequest.setStoreId(1);
		mockRequest.setCustomerEmail("mock@gmail.com");
		mockRequest.setShukranTierUpdgradeFlag(true);
		Map<String, String> requestHeader = new HashMap<>();
		when(client.findByEntityId(anyInt())).thenReturn(customerEntity);
		when(client.findByEmail(any())).thenReturn(customerEntity);
		when(client.findByPhoneNumber(any())).thenReturn(customerEntity);
		when(client.saveAndFlushCustomerEntity(any())).thenReturn(customerEntity);
		when(client.findAddressByEntityId(any())).thenReturn(mockCustomerAddEntity());
		customerEntity.setJwtToken(1);
		customerEntity.setDefaultShipping(1);
		customerEntity.setPhoneNumber("+971 988786332");
		customerEntity.setShukranLinkFlag(true);
		customerEntity.setDefaultShipping(1);
		when(restTemplate.exchange(any(),eq(String.class))).thenReturn(buildMockResponseEntity());
		when(externalServiceAdapter.getEpsilonProfile(any(),any())).thenReturn(buildMockResponseEntity());
		BuildUpgradeShukranTierActivityResponse mockResponse = customerV4ServiceImpl.shukranUpgradeTierActivity(requestHeader,mockRequest);
		assertEquals("200", mockResponse.getStatusCode());
	}

	@Test
	public void testUpgradeShukranAccount_fail() throws Exception {
		setStaticfields();
		UpgradeShukranTierActivityRequest mockRequest = new UpgradeShukranTierActivityRequest();
		mockRequest.setCustomerId(512343);
		mockRequest.setStoreId(1);
		mockRequest.setCustomerEmail("mock@gmail.com");
		mockRequest.setShukranTierUpdgradeFlag(true);
		Map<String, String> requestHeader = new HashMap<>();
		when(client.findByEntityId(anyInt())).thenReturn(customerEntity);
		when(client.findByEmail(any())).thenReturn(customerEntity);
		when(client.findByPhoneNumber(any())).thenReturn(customerEntity);
		when(client.saveAndFlushCustomerEntity(any())).thenReturn(customerEntity);
		when(client.findAddressByEntityId(any())).thenReturn(mockCustomerAddEntity());
		customerEntity.setJwtToken(1);
		customerEntity.setDefaultShipping(1);
		customerEntity.setPhoneNumber("+971 988786332");
		customerEntity.setShukranLinkFlag(true);
		customerEntity.setDefaultShipping(1);
		when(restTemplate.exchange(any(),eq(String.class))).thenReturn(new ResponseEntity<>("{}",HttpStatus.BAD_REQUEST));
		when(externalServiceAdapter.getEpsilonProfile(any(),any())).thenReturn(new ResponseEntity<>("{}",HttpStatus.BAD_REQUEST));
		BuildUpgradeShukranTierActivityResponse mockResponse = customerV4ServiceImpl.shukranUpgradeTierActivity(requestHeader,mockRequest);
		assertEquals(false, mockResponse.isStatus());
	}

	@Test
	public void testHandleShukranPhoneUnlinkWebhook_Success() throws Exception {
		// Given
		ShukranWebhookRequest request = ShukranWebhookRequest.builder()
				.mobileNumber("+966411055731")
				.action("remove")
				.phone("+966411055731")
				.build();

		CustomerEntity customerEntity = new CustomerEntity();
		customerEntity.setEntityId(123);
		customerEntity.setPhoneNumber("+966411055731");
		customerEntity.setIsMobileNumberRemoved(false);

		when(client.findByPhoneNumber("+966411055731")).thenReturn(customerEntity);
		when(client.saveAndFlushCustomerEntity(any(CustomerEntity.class))).thenReturn(customerEntity);

		// When
		ShukranWebhookResponse response = customerV4ServiceImpl.handleShukranPhoneUnlinkWebhook(request);

		// Then
		assertTrue(response.isSuccess());
		assertEquals("Successfully removed phone number from Shukran account and set isMobileNumberRemoved flag", response.getMessage());
		assertEquals(Integer.valueOf(123), response.getCustomerId());
		assertEquals("+966411055731", response.getPhoneNumber());
		assertNotNull(response.getUnlinkedAt());

		// Verify phone number is cleared and flag is set
		assertNull(customerEntity.getPhoneNumber());
		assertTrue(customerEntity.getIsMobileNumberRemoved());
		assertNotNull(customerEntity.getUpdatedAt());
	}

	@Test
	public void testHandleShukranPhoneUnlinkWebhook_InvalidAction() throws Exception {
		// Given
		ShukranWebhookRequest request = ShukranWebhookRequest.builder()
				.mobileNumber("+966411055731")
				.action("invalid")
				.phone("+966411055731")
				.build();

		// When
		ShukranWebhookResponse response = customerV4ServiceImpl.handleShukranPhoneUnlinkWebhook(request);

		// Then
		assertFalse(response.isSuccess());
		assertEquals("Invalid action. Expected 'remove'", response.getMessage());
		assertEquals("400", response.getErrorCode());
		assertEquals("Invalid action", response.getErrorMessage());
		assertEquals("+966411055731", response.getPhoneNumber());
	}

	@Test
	public void testHandleShukranPhoneUnlinkWebhook_NoPhoneNumber() throws Exception {
		// Given
		ShukranWebhookRequest request = ShukranWebhookRequest.builder()
				.mobileNumber(null)
				.action("remove")
				.phone(null)
				.build();

		// When
		ShukranWebhookResponse response = customerV4ServiceImpl.handleShukranPhoneUnlinkWebhook(request);

		// Then
		assertFalse(response.isSuccess());
		assertEquals("No phone number provided", response.getMessage());
		assertEquals("400", response.getErrorCode());
		assertEquals("Phone number is required", response.getErrorMessage());
	}

	@Test
	public void testHandleShukranPhoneUnlinkWebhook_CustomerNotFound() throws Exception {
		// Given
		ShukranWebhookRequest request = ShukranWebhookRequest.builder()
				.mobileNumber("+966999999999")
				.action("remove")
				.phone("+966999999999")
				.build();

		when(client.findByPhoneNumber("+966999999999")).thenReturn(null);

		// When
		ShukranWebhookResponse response = customerV4ServiceImpl.handleShukranPhoneUnlinkWebhook(request);

		// Then
		assertFalse(response.isSuccess());
		assertEquals("No customer found for the provided phone number", response.getMessage());
		assertEquals("404", response.getErrorCode());
		assertEquals("Customer not found", response.getErrorMessage());
		assertEquals("+966999999999", response.getPhoneNumber());
	}

	@Test
	public void testHandleShukranPhoneUpdateWebhook_Success() throws Exception {
		// Given
		ShukranWebhookRequest request = ShukranWebhookRequest.builder()
				.mobileNumber("+96890901909")
				.action("update")
				.loyaltyCardNumber(1200000522414150L)
				.cardNo(1200000522414150L)
				.phone("+96890901909")
				.build();

		CustomerEntity customerEntity = new CustomerEntity();
		customerEntity.setEntityId(123);
		customerEntity.setPhoneNumber("+96890901908");
		customerEntity.setIsMobileNumberRemoved(true);

		when(client.findByCardNumber("1200000522414150L")).thenReturn(customerEntity);
		when(client.saveAndFlushCustomerEntity(any(CustomerEntity.class))).thenReturn(customerEntity);

		// When
		ShukranWebhookResponse response = customerV4ServiceImpl.handleShukranPhoneUpdateWebhook(request);

		// Then
		assertTrue(response.isSuccess());
		assertEquals("Successfully updated phone number", response.getMessage());
		assertEquals(Integer.valueOf(123), response.getCustomerId());
		assertEquals("1200000522414150L", response.getCardNumber());
		assertEquals("+96890901908", response.getOldPhoneNumber());
		assertEquals("+96890901909", response.getNewPhoneNumber());
		assertNotNull(response.getUpdatedAt());

		// Verify phone number is updated and flag is reset
		assertEquals("+96890901909", customerEntity.getPhoneNumber());
		assertFalse(customerEntity.getIsMobileNumberRemoved());
		assertNotNull(customerEntity.getUpdatedAt());
	}

	@Test
	public void testHandleShukranPhoneUpdateWebhook_InvalidAction() throws Exception {
		// Given
		ShukranWebhookRequest request = ShukranWebhookRequest.builder()
				.mobileNumber("+96890901909")
				.action("invalid")
				.loyaltyCardNumber(1200000522414150L)
				.cardNo(1200000522414150L)
				.phone("+96890901909")
				.build();

		// When
		ShukranWebhookResponse response = customerV4ServiceImpl.handleShukranPhoneUpdateWebhook(request);

		// Then
		assertFalse(response.isSuccess());
		assertEquals("Invalid action. Expected 'update'", response.getMessage());
		assertEquals("400", response.getErrorCode());
		assertEquals("Invalid action", response.getErrorMessage());
		assertEquals("1200000522414150L", response.getCardNumber());
	}

	@Test
	public void testHandleShukranPhoneUpdateWebhook_NoPhoneNumberOrCard() throws Exception {
		// Given
		ShukranWebhookRequest request = ShukranWebhookRequest.builder()
				.mobileNumber(null)
				.action("update")
				.loyaltyCardNumber(null)
				.cardNo(null)
				.phone(null)
				.build();

		// When
		ShukranWebhookResponse response = customerV4ServiceImpl.handleShukranPhoneUpdateWebhook(request);

		// Then
		assertFalse(response.isSuccess());
		assertEquals("No phone number provided", response.getMessage());
		assertEquals("400", response.getErrorCode());
		assertEquals("Phone number or card number is required", response.getErrorMessage());
	}

	@Test
	public void testHandleShukranPhoneUpdateWebhook_CustomerNotFound() throws Exception {
		// Given
		ShukranWebhookRequest request = ShukranWebhookRequest.builder()
				.mobileNumber("+96890901909")
				.action("update")
				.loyaltyCardNumber(1200000522414150L)
				.cardNo(1200000522414150L)
				.phone("+96890901909")
				.build();

		when(client.findByCardNumber("1200000522414150L")).thenReturn(null);

		// When
		ShukranWebhookResponse response = customerV4ServiceImpl.handleShukranPhoneUpdateWebhook(request);

		// Then
		assertFalse(response.isSuccess());
		assertEquals("No customer found for the provided card number", response.getMessage());
		assertEquals("404", response.getErrorCode());
		assertEquals("Customer not found", response.getErrorMessage());
		assertEquals("1200000522414150L", response.getCardNumber());
	}

}