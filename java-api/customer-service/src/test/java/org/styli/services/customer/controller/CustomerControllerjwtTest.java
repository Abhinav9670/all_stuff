package org.styli.services.customer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.styli.services.customer.config.KafkaAsyncService;
import org.styli.services.customer.helper.AccountHelper;
import org.styli.services.customer.helper.CardHelper;
import org.styli.services.customer.jwt.security.jwtsecurity.model.JwtUser;
import org.styli.services.customer.jwt.security.jwtsecurity.security.JwtValidator;
import org.styli.services.customer.model.DeleteCustomersEventsEntity;
import org.styli.services.customer.pojo.DeleteCustomerEntity;
import org.styli.services.customer.pojo.GenericApiResponse;
import org.styli.services.customer.pojo.Stores;
import org.styli.services.customer.pojo.account.AccountDeleteResponse;
import org.styli.services.customer.pojo.account.AccountDeleteTaskUpdateRequest;
import org.styli.services.customer.pojo.address.response.NonServiceableAddressDTO;
import org.styli.services.customer.pojo.registration.request.CustomerUpdateProfileRequest;
import org.styli.services.customer.pojo.registration.request.CustomerV4Registration;
import org.styli.services.customer.pojo.registration.response.Customer;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.pojo.registration.response.CustomerV4RegistrationResponse;
import org.styli.services.customer.redis.RedisHelper;
import org.styli.services.customer.repository.StaticComponents;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;
import org.styli.services.customer.repository.Customer.DeleteCustomerEntityRepository;
import org.styli.services.customer.repository.Customer.DeleteCustomersEventsRepository;
import org.styli.services.customer.repository.Customer.NonServiceableAddressRepository;
import org.styli.services.customer.service.ConfigService;
import org.styli.services.customer.service.CustomerV4Service;
import org.styli.services.customer.service.CustomerV5Service;
import org.styli.services.customer.service.PasswordV2Service;
import org.styli.services.customer.service.impl.AccountDeleteServiceImpl;
import org.styli.services.customer.service.impl.AddressService;
import org.styli.services.customer.service.impl.AsyncService;
import org.styli.services.customer.service.impl.ClientImpl;
import org.styli.services.customer.service.impl.CustomerV4ServiceImpl;
import org.styli.services.customer.service.impl.SalesOrderServiceImpl;
import org.styli.services.customer.service.impl.SaveCustomer;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.consul.ServiceConfigs;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.google.gson.Gson;

@SpringBootTest(classes = { CustomerControllerjwtTest.class })
public class CustomerControllerjwtTest extends AbstractTestNGSpringContextTests {

	private static Customer cusresponse;
	private static org.styli.services.customer.pojo.registration.request.Customer cusRequest;
	private static CustomerUpdateProfileRequest req;

	@Autowired
	private WebApplicationContext webApplicationContext;

	private MockMvc mockMvc;

	private CustomerEntity customerEntity;
	@Mock
	private JwtValidator validator;

	@Mock
	StaticComponents staticComponents;

	@Mock
	CustomerV4Service customerV4Service;

	@InjectMocks
	CustomerV4ServiceImpl customerV4Servicemock;
	@Mock
	CustomerV5Service customerV5Service;
	@Mock
	CustomerEntityRepository customerEntityRepository;
	@Mock
	DeleteCustomerEntityRepository deleteCustomerEntityRepository;

	@Mock
	PasswordV2Service passwordV2Service;

	@Mock
	ServiceConfigs serviceConfigs;

	@Mock
	RedisHelper redisHelper;

	@Mock
	AsyncService asyncService;

	@Mock
	KafkaAsyncService kafkaAsyncService;

	@Mock
	RestTemplate restTemplate;

	@InjectMocks
	ServiceConfigs config;
	@InjectMocks
	SalesOrderServiceImpl SalesOrderService;
	@InjectMocks
	AccountHelper accountHelper;

	@Mock
	AccountHelper accountHelpermock;
	@Mock
	CardHelper cardHelper;

	@InjectMocks
	ClientImpl client;

	@Mock
	private SaveCustomer saveCustomer;

	@InjectMocks
	CustomerController customerController;

	@InjectMocks
	Constants constants;

	@InjectMocks
	AccountDeleteServiceImpl accountDeleteService;

	private CustomerV4Registration customerRegistration;

	private CustomerV4RegistrationResponse customerV4RegistrationResponse;

	private List<Stores> storeList;
	@InjectMocks
	private AddressService addressService;

	@Mock
	NonServiceableAddressRepository addressRepository;
	@Mock
	private ConfigService configService;
	@Mock
	private DeleteCustomersEventsRepository deleteCustomersEventsRepository;

	@InjectMocks
	AccountDeleteServiceImpl accountDeleteServicemock;

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
	}

	@AfterMethod
	public void afterMethod() {
		System.out.println("Initialise 	AfterMethod ");
	}

	@BeforeClass
	public void beforeClass() {
		System.out.println("Initialise 	BeforeClass ");
		try {
			String requestData = new String(
					Files.readAllBytes(Paths.get("src/test/resources/customer_registation_request.json")));
			String responseData = new String(
					Files.readAllBytes(Paths.get("src/test/resources/customer_registration_response.json")));
			Gson g = new Gson();
			customerRegistration = g.fromJson(requestData, CustomerV4Registration.class);
			customerV4RegistrationResponse = g.fromJson(responseData, CustomerV4RegistrationResponse.class);
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
	void processDeleteRequestsMissingTokenTest() throws Exception {
//		ConfigService configService = mock(ConfigServiceImpl.class);
		when(configService.checkAuthorizationInternal(anyString())).thenReturn(false);
		DeleteCustomerEntity entity = new DeleteCustomerEntity();
		entity.setCustomerId(1);
		DeleteCustomerEntity entity1 = new DeleteCustomerEntity();
		entity1.setCustomerId(1);
		List<DeleteCustomerEntity> list = new ArrayList<>();
		list.add(entity);
		list.add(entity1);
		when(accountHelpermock.getDeleteRequests()).thenReturn(list);
		AccountDeleteResponse response = customerController.processDeleteRequests("token");
		AccountDeleteResponse response1 = customerController.processDeleteRequestsCleanup("token");
		assertNotNull(response);
		assertEquals(response.getStatusCode(), "401");
		assertNotNull(response1);
		assertEquals(response1.getStatusCode(), "401");
	}

	@Test
	void processDeleteRequestsTest() throws Exception {
		setStaticfield();
//		ConfigService configService = mock(ConfigServiceImpl.class);
		when(configService.checkAuthorizationInternal(anyString())).thenReturn(true);
		DeleteCustomerEntity entity = new DeleteCustomerEntity();
		entity.setCustomerId(1);
		DeleteCustomerEntity entity1 = new DeleteCustomerEntity();
		entity1.setCustomerId(1);
		List<DeleteCustomerEntity> list = new ArrayList<>();

		list.add(entity);
		list.add(entity1);
		when(accountHelpermock.getDeleteRequests()).thenReturn(list);
		AccountDeleteResponse response = customerController.processDeleteRequests("token");
		AccountDeleteResponse response1 = customerController.processDeleteRequestsCleanup("token");

		assertNotNull(response);
		assertEquals(response.getStatusCode(), "200");
		assertNotNull(response1);
		assertEquals(response1.getStatusCode(), "200");
	}

	@Test
	void processStatusUpdateTest() throws Exception {
		setStaticfield();
		ReflectionTestUtils.setField(accountDeleteService, "accountHelper", accountHelper);
//		ConfigService configService = mock(ConfigServiceImpl.class);
		AccountDeleteTaskUpdateRequest req = new AccountDeleteTaskUpdateRequest();
		req.setCustomerId(1);
		req.setTask("task");
		req.setStatus(true);
		when(configService.checkAuthorizationInternal(anyString())).thenReturn(true);
		when(configService.checkAuthorizationExternal(anyString())).thenReturn(true);
		DeleteCustomersEventsEntity entity = new DeleteCustomersEventsEntity();
		entity.setCustomerId(1);
		entity.setTask("task");
		entity.setStatus(1);
		DeleteCustomersEventsEntity entity1 = new DeleteCustomersEventsEntity();
		entity1.setCustomerId(1);
		entity1.setTask("task");
		entity1.setStatus(1);
		List<DeleteCustomersEventsEntity> list = new ArrayList<>();

		list.add(entity);
		list.add(entity1);

		DeleteCustomerEntity deleteCustomerEntity = new DeleteCustomerEntity();
		when(deleteCustomersEventsRepository.findByCustomerId(anyInt())).thenReturn(list);
		when(deleteCustomersEventsRepository.saveAndFlush(any())).thenReturn(null);
		when(deleteCustomerEntityRepository.findByCustomerId(anyInt())).thenReturn(deleteCustomerEntity);
		when(customerEntityRepository.findByEntityId(any())).thenReturn(customerEntity);

		AccountDeleteResponse response = customerController.processStatusUpdate(req, "token");

		assertNotNull(response);
		assertEquals(response.getStatusCode(), "200");
	}

	@Test
	void processStatusUpdatefailTest() throws Exception {
		setStaticfield();
		ReflectionTestUtils.setField(accountDeleteService, "accountHelper", accountHelper);
		AccountDeleteTaskUpdateRequest req = new AccountDeleteTaskUpdateRequest();
		req.setCustomerId(1);
		req.setTask("task");
		req.setStatus(true);
		when(configService.checkAuthorizationInternal(anyString())).thenReturn(false);
		when(configService.checkAuthorizationExternal(anyString())).thenReturn(false);

		AccountDeleteResponse response = customerController.processStatusUpdate(req, "token");

		assertNotNull(response);
		assertEquals(response.getStatusCode(), "401");
	}

	@Test
	void addressNonServiceableTest() throws Exception {
		setStaticfield();
		ReflectionTestUtils.setField(accountDeleteService, "accountHelper", accountHelper);
		ReflectionTestUtils.setField(customerController, "customerV4Service", customerV4Servicemock);
		ReflectionTestUtils.setField(customerController, "addressService", addressService);
		NonServiceableAddressDTO req = new NonServiceableAddressDTO();
		req.setAddressId("1");
		req.setCustomerId("1");
		req.setArea("stree");
		when(configService.checkAuthorizationExternal(anyString())).thenReturn(false);
		JwtUser user = new JwtUser();
		user.setCustomerId(1);
		user.setUserId("guest@stylishop.com");
		when(validator.validate(anyString())).thenReturn(user);
		when(addressRepository.save(any())).thenReturn(null);
		Map<String, String> requestHeader = new HashMap<>();
		requestHeader.put("X-Header-Token", "guest@stylishop.com");
		requestHeader.put("Token", "token,jnjds,salt,1");

		GenericApiResponse<String> response = customerController.addressNonServiceable(req, requestHeader);

		assertNotNull(response);
		assertEquals(response.getStatusCode(), "200");
	}

	private void setStaticfield() {
		ReflectionTestUtils.setField(accountDeleteService, "accountHelper", accountHelper);
		ReflectionTestUtils.setField(accountDeleteService, "internalAuthBearerToken", "tokentotest,jjd,nds");
		ReflectionTestUtils.setField(customerController, "accountDeleteService", accountDeleteService);
		ReflectionTestUtils.setField(accountDeleteService, "client", client);
		ReflectionTestUtils.setField(customerController, "accountDeleteService", accountDeleteService);
		ReflectionTestUtils.setField(customerController, "salesOrderService", SalesOrderService);
		ReflectionTestUtils.setField(customerController, "jwtFlag", "1");
	}

}
