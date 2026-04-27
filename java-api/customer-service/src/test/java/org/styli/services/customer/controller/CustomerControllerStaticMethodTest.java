package org.styli.services.customer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.styli.services.customer.config.KafkaAsyncService;
import org.styli.services.customer.helper.AccountHelper;
import org.styli.services.customer.helper.EmailHelper;
import org.styli.services.customer.helper.SmsHelper;
import org.styli.services.customer.limiter.SendOtpLimiterWorker;
import org.styli.services.customer.pojo.DeleteCustomerEntity;
import org.styli.services.customer.pojo.Stores;
import org.styli.services.customer.pojo.account.AccountDeleteResponse;
import org.styli.services.customer.pojo.account.AccountDeletionOTPRequest;
import org.styli.services.customer.pojo.otp.OtpBucketObject;
import org.styli.services.customer.pojo.otp.OtpResponseBody;
import org.styli.services.customer.pojo.otp.OtpValidationType;
import org.styli.services.customer.pojo.otp.SendOtpRequest;
import org.styli.services.customer.pojo.otp.SendOtpResponse;
import org.styli.services.customer.pojo.otp.ValidateOtpRequest;
import org.styli.services.customer.pojo.otp.ValidateOtpResponse;
import org.styli.services.customer.pojo.registration.request.CustomerUpdateProfileRequest;
import org.styli.services.customer.pojo.registration.response.Customer;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.redis.RedisHelper;
import org.styli.services.customer.repository.StaticComponents;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;
import org.styli.services.customer.repository.Customer.DeleteCustomerEntityRepository;
import org.styli.services.customer.service.CustomerV4Service;
import org.styli.services.customer.service.CustomerV5Service;
import org.styli.services.customer.service.PasswordV2Service;
import org.styli.services.customer.service.SalesOrderService;
import org.styli.services.customer.service.impl.AccountDeleteServiceImpl;
import org.styli.services.customer.service.impl.AsyncService;
import org.styli.services.customer.service.impl.ClientImpl;
import org.styli.services.customer.service.impl.ConfigServiceImpl;
import org.styli.services.customer.service.impl.OtpServiceImpl;
import org.styli.services.customer.service.impl.SaveCustomer;
import org.styli.services.customer.utility.consul.ServiceConfigs;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@SpringBootTest(classes = { CustomerControllerStaticMethodTest.class })
public class CustomerControllerStaticMethodTest extends AbstractTestNGSpringContextTests {

	private static Customer cusresponse;
	private static org.styli.services.customer.pojo.registration.request.Customer cusRequest;
	private static CustomerUpdateProfileRequest req;

	@Autowired
	private WebApplicationContext webApplicationContext;

	private MockMvc mockMvc;

	private CustomerEntity customerEntity;

	@Mock
	EmailHelper emailHelper;

	@Mock
	SmsHelper smsHelper;

	@InjectMocks
	ConfigServiceImpl configServiceImpl;

	@Mock
	StaticComponents staticComponents;

	@Mock
	CustomerV4Service customerV4Service;

	@Mock
	CustomerV5Service customerV5Service;

	@InjectMocks
	ServiceConfigs config;

	@Mock
	CustomerEntityRepository customerEntityRepository;

	@Mock
	DeleteCustomerEntityRepository deleteCustomerEntityRepository;

	@Mock
	PasswordV2Service passwordV2Service;

	@Mock
	RedisHelper redisHelper;

	@Mock
	AsyncService asyncService;

	@Mock
	KafkaAsyncService kafkaAsyncService;

	@Mock
	SalesOrderService salesOrderService;

	@Mock
	AccountHelper accountHelper;

	@Mock
	SendOtpLimiterWorker sendOtpWorker;

	@Mock
	ClientImpl client;

	@Mock
	private SaveCustomer saveCustomer;

	@InjectMocks
	CustomerController customerController;

	@InjectMocks
	AccountDeleteServiceImpl accountDeleteService;

	@InjectMocks
	OtpServiceImpl otpService;

	private AccountDeletionOTPRequest accountDeletionOTPRequest;
	private List<Stores> storeList;

	@BeforeMethod
	public void setUp() {
		System.out.println("Initialise 	BeforeMethod ");
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
		customerEntity.setPhoneNumber("8989891111");

		// prepare Otp bucket object.
		OtpBucketObject obj = new OtpBucketObject();
		obj.setExpiresAt(Instant.now().toEpochMilli() + 120000);
		obj.setOtp("1234");

		when(redisHelper.get(any(), any(), any())).thenReturn(obj);

		when(customerV4Service.getPhoneNumberByEmailId(anyString())).thenReturn("9877865632");
		when(customerEntityRepository.findByEntityId(anyInt())).thenReturn(customerEntity);
		when(client.getStoresArray()).thenReturn(storeList);
		when(client.saveAndFlushCustomerEntity(any())).thenReturn(customerEntity);
		when(deleteCustomerEntityRepository.findByCustomerId(anyInt())).thenReturn(new DeleteCustomerEntity());
		when(redisHelper.put(any(), any(), any(), any())).thenReturn(true);
		when(redisHelper.remove(any(), any())).thenReturn(true);
		when(client.findByEntityId(anyInt())).thenReturn(customerEntity);
		when(accountHelper.getOtpMessage(any())).thenReturn("message test");
		when(accountHelper.getEmailMessage(anyString())).thenReturn("emai message test");
		when(accountHelper.generateSafeOtp(any(), anyLong())).thenReturn("1234");
		when(emailHelper.sendEmail(anyString(), anyString(), anyString(), any(), any(), anyString())).thenReturn(true);
		when(smsHelper.sendSMS(anyString(), anyString(), anyInt(), false)).thenReturn(true);
		when(smsHelper.sendSMSIN(anyString(), anyString(), 1, false)).thenReturn(true);

	}

	@AfterMethod
	public void afterMethod() {
		System.out.println("Initialise 	AfterMethod ");
	}

	@BeforeClass
	public void beforeClass() {
		System.out.println("Initialise 	BeforeClass ");
		try {
			String otpData = new String(
					Files.readAllBytes(Paths.get("src/test/resources/account_deletion_otp_request.json")));
			String storeData = new String(Files.readAllBytes(Paths.get("src/test/resources/store_list.json")));
			Gson g = new Gson();
			accountDeletionOTPRequest = g.fromJson(otpData, AccountDeletionOTPRequest.class);
			Type listType = new TypeToken<ArrayList<Stores>>() {
			}.getType();
			storeList = new Gson().fromJson(storeData, listType);

		} catch (Exception e) {
		}

	}

	@AfterClass
	public void afterClass() {
		System.out.println("Initialise 	@AfterClass ");
	}

	@BeforeTest
	public void beforeTest() {
		System.out.println("Initialise @BeforeTest ");
		MockitoAnnotations.initMocks(this);
		when(asyncService.asyncSalesOrdersUpdateCustId(cusresponse)).thenReturn(null);
		when(kafkaAsyncService.publishCustomerEntityToKafka(cusresponse)).thenReturn(null);

	}

	@AfterTest
	public void afterTest() {
		System.out.println("Initialise @AfterTest ");
	}

	@Test
	void sendOTPForAccountDeletionTest() throws IOException {

		Map<String, String> requestHeader = null;
		ReflectionTestUtils.setField(accountDeleteService, "client", client);
		ReflectionTestUtils.setField(customerController, "accountDeleteService", accountDeleteService);
		ReflectionTestUtils.setField(customerController, "jwtFlag", "1");

		AccountDeleteResponse responseEntity = customerController.sendOTPForAccountDeletion(accountDeletionOTPRequest,
				"", "", requestHeader);
		assertEquals(responseEntity.getStatusCode(), "200");
		assertEquals(responseEntity.getStatusMsg(), "SUCCESS");
	}

	@Test
	void sendOtpTest() throws Exception {
		ReflectionTestUtils.setField(customerController, "otpService", otpService);
		ReflectionTestUtils.setField(otpService, "region", "IN");
		ReflectionTestUtils.setField(otpService, "env", "dev");
		String responseData = new String(Files.readAllBytes(Paths.get("src/test/resources/sendotprequest.json")));
		String storeData = new String(Files.readAllBytes(Paths.get("src/test/resources/store_list.json")));
		Gson g = new Gson();
		Type listType = new TypeToken<ArrayList<Stores>>() {
		}.getType();
		storeList = new Gson().fromJson(storeData, listType);
		Map<String, Object> newConfigs = new LinkedHashMap<>();
		Map<String, Object> map = new LinkedHashMap<>();
		Map<String, String> lang = new LinkedHashMap<>();
		lang.put("en", "default message");
		map.put("DEFAULTMSG", lang);
		newConfigs.put("otpMessages", map);
		ReflectionTestUtils.setField(config, "consulServiceMap", newConfigs);

		when(staticComponents.getStoresArray()).thenReturn(storeList);
		Map<String, String> requestHeader = null;
		SendOtpRequest sendOtpRequest = g.fromJson(responseData, SendOtpRequest.class);

		OtpResponseBody<SendOtpResponse> responseEntity = customerController.sendOtp(requestHeader, sendOtpRequest);
		assertNotNull(responseEntity);
		assertEquals(responseEntity.getStatus(), true);
		assertNotNull(responseEntity.getResponse().getOtpData());
	}

	@Test
	void sendOtpemailTest() throws Exception {
		ReflectionTestUtils.setField(customerController, "otpService", otpService);
		ReflectionTestUtils.setField(otpService, "region", "IN");
		ReflectionTestUtils.setField(otpService, "env", "dev");
		String responseData = new String(Files.readAllBytes(Paths.get("src/test/resources/sendotprequest.json")));
		String storeData = new String(Files.readAllBytes(Paths.get("src/test/resources/store_list.json")));
		Gson g = new Gson();
		Type listType = new TypeToken<ArrayList<Stores>>() {
		}.getType();
		storeList = new Gson().fromJson(storeData, listType);
		Map<String, Object> newConfigs = new LinkedHashMap<>();
		Map<String, Object> map = new LinkedHashMap<>();
		Map<String, String> lang = new LinkedHashMap<>();
		lang.put("en", "default message");
		map.put("DEFAULTMSG", lang);
		newConfigs.put("otpMessages", map);
		ReflectionTestUtils.setField(config, "consulServiceMap", newConfigs);

		when(staticComponents.getStoresArray()).thenReturn(storeList);
		Map<String, String> requestHeader = null;
		SendOtpRequest sendOtpRequest = g.fromJson(responseData, SendOtpRequest.class);
		sendOtpRequest.setEmail("");
		OtpResponseBody<SendOtpResponse> responseEntity = customerController.sendOtp(requestHeader, sendOtpRequest);
		assertNotNull(responseEntity);
		assertEquals(responseEntity.getStatus(), true);
		assertNotNull(responseEntity.getResponse().getOtpData());
	}

	@Test
	void validateOtpTest() throws Exception {
		ReflectionTestUtils.setField(customerController, "otpService", otpService);
		ReflectionTestUtils.setField(otpService, "region", "IN");
		ReflectionTestUtils.setField(otpService, "env", "dev");
		String responseData = new String(Files.readAllBytes(Paths.get("src/test/resources/validateotpreq.json")));
		String storeData = new String(Files.readAllBytes(Paths.get("src/test/resources/store_list.json")));
		Gson g = new Gson();
		Type listType = new TypeToken<ArrayList<Stores>>() {
		}.getType();
		storeList = new Gson().fromJson(storeData, listType);
		Map<String, Object> newConfigs = new LinkedHashMap<>();
		Map<String, Object> map = new LinkedHashMap<>();
		Map<String, String> lang = new LinkedHashMap<>();
		lang.put("en", "default message");
		map.put("DEFAULTMSG", lang);
		newConfigs.put("otpMessages", map);
		ReflectionTestUtils.setField(config, "consulServiceMap", newConfigs);

		when(staticComponents.getStoresArray()).thenReturn(storeList);
		Map<String, String> requestHeader = null;
		ValidateOtpRequest sendOtpRequest = g.fromJson(responseData, ValidateOtpRequest.class);

		OtpResponseBody<ValidateOtpResponse> responseEntity = customerController.validateOtp(requestHeader,
				sendOtpRequest);
		assertNotNull(responseEntity);
		assertEquals(responseEntity.getStatus(), true);
		assertNotNull(responseEntity.getResponse());
	}

	@Test
	void validateOtploginTest() throws Exception {
		ReflectionTestUtils.setField(customerController, "otpService", otpService);
		ReflectionTestUtils.setField(otpService, "region", "IN");
		ReflectionTestUtils.setField(otpService, "env", "dev");
		String responseData = new String(Files.readAllBytes(Paths.get("src/test/resources/validateotpreq.json")));
		String storeData = new String(Files.readAllBytes(Paths.get("src/test/resources/store_list.json")));
		Gson g = new Gson();
		Type listType = new TypeToken<ArrayList<Stores>>() {
		}.getType();
		storeList = new Gson().fromJson(storeData, listType);
		Map<String, Object> newConfigs = new LinkedHashMap<>();
		Map<String, Object> map = new LinkedHashMap<>();
		Map<String, String> lang = new LinkedHashMap<>();
		lang.put("en", "default message");
		map.put("DEFAULTMSG", lang);
		newConfigs.put("otpMessages", map);
		ReflectionTestUtils.setField(config, "consulServiceMap", newConfigs);

		when(staticComponents.getStoresArray()).thenReturn(storeList);
		Map<String, String> requestHeader = null;
		ValidateOtpRequest sendOtpRequest = g.fromJson(responseData, ValidateOtpRequest.class);
		sendOtpRequest.setType(OtpValidationType.LOGIN);

		OtpResponseBody<ValidateOtpResponse> responseEntity = customerController.validateOtp(requestHeader,
				sendOtpRequest);
		assertNotNull(responseEntity);
	}

}

