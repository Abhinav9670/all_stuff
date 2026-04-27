package org.styli.services.customer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import com.google.gson.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
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
import org.styli.services.customer.config.KafkaAsyncService;
import org.styli.services.customer.exception.CustomerException;
import org.styli.services.customer.helper.AccountHelper;
import org.styli.services.customer.helper.CardHelper;
import org.styli.services.customer.jwt.security.jwtsecurity.security.JwtValidator;
import org.styli.services.customer.pojo.CustomerRequestBody;
import org.styli.services.customer.pojo.DeleteCustomerEntity;
import org.styli.services.customer.pojo.GetLocationGoogleMapsRequest;
import org.styli.services.customer.pojo.PrintLogInfoRequest;
import org.styli.services.customer.pojo.ProductStatusRequest;
import org.styli.services.customer.pojo.Stores;
import org.styli.services.customer.pojo.account.AccountDeleteResponse;
import org.styli.services.customer.pojo.account.AccountDeletionEligibleRequest;
import org.styli.services.customer.pojo.account.AccountDeletionEligibleResponse;
import org.styli.services.customer.pojo.account.AccountDeletionRequest;
import org.styli.services.customer.pojo.account.StyliCoinsCustomerInfo;
import org.styli.services.customer.pojo.account.StyliCoinsData;
import org.styli.services.customer.pojo.account.StyliCoinsResponse;
import org.styli.services.customer.pojo.address.response.CustomerAddrees;
import org.styli.services.customer.pojo.address.response.CustomerAddreesResponse;
import org.styli.services.customer.pojo.card.request.CreateCardRequest;
import org.styli.services.customer.pojo.card.request.DeleteCardRequest;
import org.styli.services.customer.pojo.card.response.CustomerCard;
import org.styli.services.customer.pojo.card.response.CustomerCardsResponseDTO;
import org.styli.services.customer.pojo.epsilon.request.LinkShukranRequest;
import org.styli.services.customer.pojo.epsilon.request.ShukranEnrollmentRequest;
import org.styli.services.customer.pojo.epsilon.response.EnrollmentResponse;
import org.styli.services.customer.pojo.otp.OtpBucketObject;
import org.styli.services.customer.pojo.registration.request.CustomerLoginV4Request;
import org.styli.services.customer.pojo.registration.request.CustomerPasswordRequest;
import org.styli.services.customer.pojo.registration.request.CustomerQueryReq;
import org.styli.services.customer.pojo.registration.request.CustomerUpdateProfileRequest;
import org.styli.services.customer.pojo.registration.request.CustomerV4Registration;
import org.styli.services.customer.pojo.registration.request.CustomerValidityCheckRequest;
import org.styli.services.customer.pojo.registration.request.CustomerWishListRequest;
import org.styli.services.customer.pojo.registration.request.CustomerWishlistV5Request;
import org.styli.services.customer.pojo.registration.request.GetProductV4Request;
import org.styli.services.customer.pojo.registration.request.LoginType;
import org.styli.services.customer.pojo.registration.request.WhatsAppOtpRequest;
import org.styli.services.customer.pojo.registration.response.Customer;
import org.styli.services.customer.pojo.registration.response.CustomerCheckvalidityResponse;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.pojo.registration.response.CustomerExistResponse;
import org.styli.services.customer.pojo.registration.response.CustomerLoginV4Response;
import org.styli.services.customer.pojo.registration.response.CustomerRestPassResponse;
import org.styli.services.customer.pojo.registration.response.CustomerUpdateProfileResponse;
import org.styli.services.customer.pojo.registration.response.CustomerV4RegistrationResponse;
import org.styli.services.customer.pojo.registration.response.CustomerWishlistResponse;
import org.styli.services.customer.pojo.registration.response.WhatsAppOptResponse;
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

@SpringBootTest(classes = { CustomerControllerTest.class })
public class CustomerControllerTest extends AbstractTestNGSpringContextTests {

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

	Gson g = new GsonBuilder()
			.registerTypeAdapter(LocalDate.class, new JsonDeserializer<LocalDate>() {
				@Override
				public LocalDate deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
					return LocalDate.parse(json.getAsString());
				}
			})
			.registerTypeAdapter(LocalDate.class, new JsonSerializer<LocalDate>() {
				@Override
				public JsonElement serialize(LocalDate src, Type typeOfSrc, JsonSerializationContext context) {
					return new JsonPrimitive(src.toString()); // ISO format
				}
			})
			.create();

	@BeforeClass
	public void beforeClass() {
		System.out.println("Initialise 	BeforeClass ");
		try {
			String requestData = new String(
					Files.readAllBytes(Paths.get("src/test/resources/customer_registation_request.json")));
			String responseData = new String(
					Files.readAllBytes(Paths.get("src/test/resources/customer_registration_response.json")));
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
	public void isExistsCustomerTest() throws IOException {
		System.out.println("Inside 	isExistsCustomerTest  ");

		CustomerQueryReq customerExitsReq = new CustomerQueryReq();
		customerExitsReq.setUseridentifier("testuser.100@mailinator.com");
		customerExitsReq.setStoreId(1);
		customerExitsReq.setLoginType(LoginType.EMAIL_LOGIN);
		Map<String, String> requestHeader = new HashMap<>();

		// Prepare mock response
		String jsonData = new String(
				Files.readAllBytes(Paths.get("src/test/resources/customer_validation_response.json")));
		CustomerExistResponse response = g.fromJson(jsonData, CustomerExistResponse.class);

		when(customerV4Service.validateUser(customerExitsReq, requestHeader)).thenReturn(response);

		CustomerExistResponse responseEntity = customerController.isExistsCustomer(requestHeader, customerExitsReq);

		assertEquals(responseEntity.getStatusMsg(), "SUCCESS");
	}

	@Test
	void SaveV3CustomerFailTest() throws CustomerException, IOException {

		Map<String, String> requestHeader = new HashMap<>();
		when(customerV4Service.saveV4Customer(customerRegistration, requestHeader))
				.thenReturn(customerV4RegistrationResponse);

		CustomerV4RegistrationResponse responseEntity = customerController.saveV3Customer(requestHeader,
				customerRegistration);

		assertEquals(responseEntity.getStatusMsg(), "Something Went Wrong");


	}

	@Test
	void saveV3CustomerblockedTest() throws CustomerException {
		Map<String, String> requestHeader = new HashMap<>();
		when(customerV4Service.saveV4Customer(customerRegistration, requestHeader))
				.thenReturn(customerV4RegistrationResponse);
		Map<String, Object> newConfigs = new LinkedHashMap<>();
		newConfigs.put("blockRegistration", true);
		ReflectionTestUtils.setField(config, "consulServiceMap", newConfigs);

		CustomerV4RegistrationResponse resentity = customerController.saveV3Customer(requestHeader,
				customerRegistration);

		assertEquals(resentity.getStatusMsg(), "ERROR");
		assertNotNull(resentity.getError());
		assertEquals(resentity.getError().getErrorMessage(), "Registration is blocked for customer migration activity");

	}

	@Test
	void customerV31LoginTest() throws CustomerException, IOException {
		// Prepare mock Data
		String reponseData = new String(
				Files.readAllBytes(Paths.get("src/test/resources/customer_login_reponse.json")));
		String requestData = new String(
				Files.readAllBytes(Paths.get("src/test/resources/customer_login_request.json")));

		 String deviceId = null;
		Map<String, String> requestHeader = null;
		CustomerLoginV4Response customerLoginV4Response = g.fromJson(reponseData, CustomerLoginV4Response.class);
		CustomerLoginV4Request CustomerLoginV4Request = g.fromJson(requestData, CustomerLoginV4Request.class);
		when(customerV4Service.getCustomerLoginV4Details(any(), any())).thenReturn(customerLoginV4Response);
		CustomerLoginV4Response responseEntity = customerController.customerV31Login(requestHeader,
				CustomerLoginV4Request,deviceId);

		assertEquals(responseEntity.getStatusMsg(), "Logged In Successfully!!");

	}

	@Test
	void customerUpdateProfileRequestTest() throws CustomerException, IOException {
		String requestData = new String(
				Files.readAllBytes(Paths.get("src/test/resources/customer_update_request.json")));
		String responseData = new String(
				Files.readAllBytes(Paths.get("src/test/resources/customer_update_response.json")));


		CustomerUpdateProfileRequest customerUpdateProfileRequest = g.fromJson(requestData,
				CustomerUpdateProfileRequest.class);

		CustomerUpdateProfileResponse customerUpdateProfileResponse = g.fromJson(responseData,
				CustomerUpdateProfileResponse.class);
		Map<String, String> requestHeader = null;
		ReflectionTestUtils.setField(customerController, "jwtFlag", "1");
		when(customerV4Service.updateCustomer(any(), any())).thenReturn(customerUpdateProfileResponse);
		CustomerUpdateProfileResponse responseEntity = customerController.updateProfile(requestHeader,
				customerUpdateProfileRequest);
		assertEquals(responseEntity.getStatusCode(), "200");
		assertNotNull(responseEntity.getResponse().getCustomer());
	}

	@Test
	void getProfileTest() throws CustomerException, IOException {
		String responseData = new String(
				Files.readAllBytes(Paths.get("src/test/resources/customer_update_response.json")));

		CustomerUpdateProfileResponse customerUpdateProfileResponse = g.fromJson(responseData,
				CustomerUpdateProfileResponse.class);
		Map<String, String> requestHeader = null;
		ReflectionTestUtils.setField(customerController, "jwtFlag", "1");
		when(customerV4Service.getCustomerDetails(any(), any())).thenReturn(customerUpdateProfileResponse);
		CustomerUpdateProfileResponse responseEntity = customerController.getProfile(requestHeader, 1);
		assertEquals(responseEntity.getStatusCode(), "200");
		assertNotNull(responseEntity.getResponse().getCustomer());
	}

	@Test
	void getPOstProfileTest() throws IOException {
		String responseData = new String(
				Files.readAllBytes(Paths.get("src/test/resources/customer_update_response.json")));
		CustomerRequestBody customerRequestBody = new CustomerRequestBody();
		customerRequestBody.setCustomerEmail("test123@mailinator.com");
		customerRequestBody.setStoreId(1);
		customerRequestBody.setCustomerId(1);
		CustomerUpdateProfileResponse customerUpdateProfileResponse = g.fromJson(responseData,
				CustomerUpdateProfileResponse.class);
		Map<String, String> requestHeader = null;
		ReflectionTestUtils.setField(customerController, "jwtFlag", "1");
		when(customerV4Service.getCustomerDetails(any(), any())).thenReturn(customerUpdateProfileResponse);
		CustomerUpdateProfileResponse responseEntity = customerController.getPOstProfile(requestHeader,
				customerRequestBody);
		assertEquals(responseEntity.getStatusCode(), "200");
		assertNotNull(responseEntity.getResponse().getCustomer());
	}

	@Test
	void changePasswordTest() throws IOException {
		CustomerPasswordRequest request = new CustomerPasswordRequest();
		request.setCustomerId(1);
		request.setCurrentPassword("");
		request.setNewPassword("");
		ReflectionTestUtils.setField(customerController, "jwtFlag", "1");
		Map<String, String> requestHeader = null;
		String responseData = new String(
				Files.readAllBytes(Paths.get("src/test/resources/password_update_response.json")));
		CustomerRestPassResponse customerRestPassResponse = g.fromJson(responseData, CustomerRestPassResponse.class);
		when(customerV4Service.changePassword(any())).thenReturn(customerRestPassResponse);
		CustomerRestPassResponse responseEntity = customerController.changePassword(requestHeader, request);
		assertEquals(responseEntity.getStatusCode(), "200");
	}

	@Test
	void resetCustomerPasswordTest() throws IOException, CustomerException {
		CustomerQueryReq passResetReq = new CustomerQueryReq();
		passResetReq.setLoginType(LoginType.EMAIL_LOGIN);
		passResetReq.setStoreId(1);
		passResetReq.setUseridentifier("test");
		ReflectionTestUtils.setField(customerController, "jwtFlag", "1");
		Map<String, String> requestHeader = null;
		String responseData = new String(
				Files.readAllBytes(Paths.get("src/test/resources/password_update_response.json")));
		CustomerRestPassResponse customerRestPassResponse = g.fromJson(responseData, CustomerRestPassResponse.class);
		when(passwordV2Service.forgotPassword(any(), any())).thenReturn(customerRestPassResponse);
		ResponseEntity<CustomerRestPassResponse> responseEntity = customerController
				.resetCustomerPassword(requestHeader, passResetReq);
		assertEquals(responseEntity.getStatusCode(), HttpStatus.OK);
	}

	@Test
	void getWhatsAppOtpTest() throws IOException {
		WhatsAppOtpRequest whatsAppOtpRequest = new WhatsAppOtpRequest();
		whatsAppOtpRequest.setCustomerId(1);
		ReflectionTestUtils.setField(customerController, "jwtFlag", "1");
		String responseData = new String(Files.readAllBytes(Paths.get("src/test/resources/whatsappotpresponse.json")));
		WhatsAppOptResponse customerRestPassResponse = g.fromJson(responseData, WhatsAppOptResponse.class);
		when(customerV4Service.getWhatsAppOtp(any(), any())).thenReturn(customerRestPassResponse);
		WhatsAppOptResponse responseEntity = customerController.getWhatsAppOtp(null, whatsAppOtpRequest);
		assertEquals(responseEntity.getStatusCode(), "200");
		assertNotNull(responseEntity.getResponse());
	}

	@Test
	void updateV4OneWishListTest() throws IOException {
		ReflectionTestUtils.setField(customerController, "jwtFlag", "1");
		String requestData = new String(Files.readAllBytes(Paths.get("src/test/resources/wishlist_add_request.json")));
		String responseData = new String(
				Files.readAllBytes(Paths.get("src/test/resources/wishlist_add_response.json")));
		CustomerWishListRequest customerWishListRequest = g.fromJson(requestData, CustomerWishListRequest.class);
		CustomerWishlistResponse customerWishlistResponse = g.fromJson(responseData, CustomerWishlistResponse.class);
		when(customerV4Service.saveUpdateV4OneWishList(customerWishListRequest, null, true))
				.thenReturn(customerWishlistResponse);
		when(customerV4Service.saveUpdateV4OneWishList(customerWishListRequest, null, false))
				.thenReturn(customerWishlistResponse);
		when(customerV4Service.removeWishList(customerWishListRequest)).thenReturn(customerWishlistResponse);
		CustomerWishlistResponse updateResponseEntity = customerController.updateV4OneWishList(null,
				customerWishListRequest);
		CustomerWishlistResponse saveResponseEntity = customerController.saveV4oneWishList(null,
				customerWishListRequest);
		CustomerWishlistResponse deleteResponseEntity = customerController.removefourOneWishList(null,
				customerWishListRequest);
		assertEquals(updateResponseEntity.getStatusCode(), "200");
		assertEquals(saveResponseEntity.getStatusCode(), "200");
		assertEquals(deleteResponseEntity.getStatusCode(), "200");

	}

	@Test
	void getWishForOneListIds() throws IOException {
		CustomerWishlistV5Request customerWishlistV5Request = new CustomerWishlistV5Request();
		customerWishlistV5Request.setCustomerId(6559);
		customerWishlistV5Request.setStoreId(51);
		ReflectionTestUtils.setField(customerController, "jwtFlag", "1");
		String responseData = new String(Files.readAllBytes(Paths.get("src/test/resources/wishlist_response.json")));
		CustomerWishlistResponse customerWishListResponse = g.fromJson(responseData, CustomerWishlistResponse.class);
		when(customerV4Service.getWishList(6559, 51, false)).thenReturn(customerWishListResponse);
		CustomerWishlistResponse responseEntity = customerController.getWishForOneListIds(null,
				customerWishlistV5Request.getCustomerId(), customerWishlistV5Request.getStoreId());
		assertEquals(responseEntity.getStatusCode(), "200");
		assertNotNull(responseEntity.getResponse());
	}

	@Test
	void getWishlistForCustomerTest() throws IOException {
		CustomerWishlistV5Request customerWishlistV5Request = new CustomerWishlistV5Request();
		customerWishlistV5Request.setCustomerId(6559);
		customerWishlistV5Request.setStoreId(51);
		ReflectionTestUtils.setField(customerController, "jwtFlag", "1");
		String responseData = new String(Files.readAllBytes(Paths.get("src/test/resources/wishlist_response.json")));
		CustomerWishlistResponse customerWishListResponse = g.fromJson(responseData, CustomerWishlistResponse.class);
		when(customerV5Service.getWishList(customerWishlistV5Request)).thenReturn(customerWishListResponse);
		CustomerWishlistResponse responseEntity = customerController.getWishlistForCustomer(null,
				customerWishlistV5Request);
		assertEquals(responseEntity.getStatusCode(), "200");
		assertNotNull(responseEntity.getResponse());
	}

	@Test
	void saveAddressTest() throws Exception {
		Map<String, String> requestHeader = new HashMap<>();
		ReflectionTestUtils.setField(customerController, "jwtFlag", "1");
		String responseData = new String(Files.readAllBytes(Paths.get("src/test/resources/address_response.json")));
		String requestData = new String(Files.readAllBytes(Paths.get("src/test/resources/save_address_request.json")));
		CustomerAddreesResponse customerAddreesResponse = g.fromJson(responseData, CustomerAddreesResponse.class);
		CustomerAddrees customerAddRequest = g.fromJson(requestData, CustomerAddrees.class);

		when(customerV4Service.saveAddress(any(), anyBoolean(), any())).thenReturn(customerAddreesResponse);
		CustomerAddreesResponse responseEntity = customerController.saveAddress(requestHeader, customerAddRequest);
		CustomerAddreesResponse updateresponseEntity = customerController.updateAddress(requestHeader, customerAddRequest);
		assertEquals(responseEntity.getStatusCode(), "200");
		assertNotNull(responseEntity.getResponse());
		assertEquals(updateresponseEntity.getStatusCode(), "200");
		assertNotNull(updateresponseEntity.getResponse());
		assertNotNull(responseEntity.getResponse().getAddress());
	}

	@Test
	void deleteAddressTest() throws Exception {
		ReflectionTestUtils.setField(customerController, "jwtFlag", "1");
		String responseData = new String(Files.readAllBytes(Paths.get("src/test/resources/address_response.json")));
		String requestData = new String(Files.readAllBytes(Paths.get("src/test/resources/save_address_request.json")));
		CustomerAddreesResponse customerAddreesResponse = g.fromJson(responseData, CustomerAddreesResponse.class);
		CustomerAddrees customerAddRequest = g.fromJson(requestData, CustomerAddrees.class);

		when(customerV4Service.deleteAddress(any())).thenReturn(customerAddreesResponse);
		CustomerAddreesResponse responseEntity = customerController.deleteAddress(null, customerAddRequest);
		assertEquals(responseEntity.getStatusCode(), "200");
		assertNotNull(responseEntity.getResponse());
	}

	@Test
	void getAddressTest() throws Exception {
		ReflectionTestUtils.setField(customerController, "jwtFlag", "1");
		String responseData = new String(Files.readAllBytes(Paths.get("src/test/resources/address_response.json")));
		CustomerAddreesResponse customerAddreesResponse = g.fromJson(responseData, CustomerAddreesResponse.class);
		CustomerRequestBody customerRequestBody = new CustomerRequestBody();
		customerRequestBody.setCustomerEmail("test123@mailinator.com");
		customerRequestBody.setCustomerId(232);
		customerRequestBody.setStoreId(51);
		when(customerV4Service.getAddress(anyInt(), any())).thenReturn(customerAddreesResponse);
		CustomerAddreesResponse responseEntity = customerController.getAddress(null, 56743);
		CustomerAddreesResponse postresponseEntity = customerController.getPostAddress(null, customerRequestBody);
		assertEquals(responseEntity.getStatusCode(), "200");
		assertEquals(postresponseEntity.getStatusCode(), "200");
		assertNotNull(responseEntity.getResponse());
		assertFalse(responseEntity.getResponse().getAddresses().isEmpty());
	}

	@Test
	void deleteOrWithdrawCustomerAccountTest() throws IOException {
		String responseData = new String(
				Files.readAllBytes(Paths.get("src/test/resources/account_deletion_request.json")));
		Map<String, String> requestHeader = null;
		ReflectionTestUtils.setField(accountDeleteService, "accountHelper", accountHelper);
		ReflectionTestUtils.setField(customerController, "accountDeleteService", accountDeleteService);
		AccountDeletionRequest accountDeletionRequest = g.fromJson(responseData, AccountDeletionRequest.class);
		// prepare Otp bucket object.
		OtpBucketObject obj = new OtpBucketObject();
		obj.setExpiresAt(Instant.now().toEpochMilli() + 120000);
		obj.setOtp(accountDeletionRequest.getOtp());
		when(customerEntityRepository.findByEntityId(anyInt())).thenReturn(customerEntity);
		when(deleteCustomerEntityRepository.findByCustomerId(anyInt())).thenReturn(new DeleteCustomerEntity());
		when(redisHelper.get(any(), any(), any())).thenReturn(obj);
		AccountDeleteResponse responseEntity = customerController
				.deleteOrWithdrawCustomerAccount(accountDeletionRequest, requestHeader);
		assertEquals(responseEntity.getStatusCode(), "200");

	}

	@Test
	void checkAccountDeletionEligiblityTest() throws Exception {
		String responseData = new String(
				Files.readAllBytes(Paths.get("src/test/resources/acountdeletioneligibityreq.json")));
		Map<String, String> requestHeader = null;
		AccountDeletionEligibleRequest accountDeletionEligibleRequest = g.fromJson(responseData,
				AccountDeletionEligibleRequest.class);

		setStaticfield();
		AccountDeletionEligibleResponse accountDeletionEligibleResponse = new AccountDeletionEligibleResponse();

		ResponseEntity<AccountDeletionEligibleResponse> response = new ResponseEntity<>(HttpStatus.OK)
				.ok(accountDeletionEligibleResponse);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(AccountDeletionEligibleResponse.class))).thenReturn(response);
		StyliCoinsCustomerInfo styliCoinsCustomerInfo = new StyliCoinsCustomerInfo();
		styliCoinsCustomerInfo.setCoinAvailable(100);
		StyliCoinsData styliCoinsData = new StyliCoinsData();
		styliCoinsData.setCustomerInfo(styliCoinsCustomerInfo);
		StyliCoinsResponse styliCoinsResponse = new StyliCoinsResponse();
		styliCoinsResponse.setData(styliCoinsData);
		ResponseEntity<StyliCoinsResponse> coinresponse = new ResponseEntity<>(HttpStatus.OK).ok(styliCoinsResponse);
		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(StyliCoinsResponse.class))).thenReturn(coinresponse);

		AccountDeletionEligibleResponse responseEntity = customerController
				.checkAccountDeletionEligiblity(accountDeletionEligibleRequest, "", "", requestHeader);
		assertNotNull(responseEntity);
		assertEquals(responseEntity.isEligible(), true);
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

	@Test
	void miscellaneousTest() {
		CustomerValidityCheckRequest customerValidityCheckRequest = new CustomerValidityCheckRequest();
		CustomerCheckvalidityResponse customerCheckvalidityResponse = new CustomerCheckvalidityResponse();
		customerValidityCheckRequest.setCustomerEmail("test@mailnator.com");
		customerValidityCheckRequest.setCustomerId(122);
		Map<String, String> requestHeader = null;
		when(customerV4Service.customerValidityCheck(customerValidityCheckRequest,requestHeader))
				.thenReturn(customerCheckvalidityResponse);
		customerController.GetCustomer(null, customerValidityCheckRequest);
		customerController.getLocationGoogleMaps(new GetLocationGoogleMapsRequest());
		customerController.getGooglePlacesForAutocompleteText("", 123);
		customerController.getProductQty(null, new ProductStatusRequest());
		customerController.getProductDesInfo(new GetProductV4Request(), "");
	}

	@Test
	void getCustomerCardsTest() throws Exception {
		setStaticfield();
		Map<String, String> requestHeader = null;
		CustomerCardsResponseDTO respo = new CustomerCardsResponseDTO();
		respo.setStatus(true);
		CustomerCard card1 = new CustomerCard();
		CustomerCard card2 = new CustomerCard();
		List<CustomerCard> cards = Arrays.asList(card1, card2);
		respo.setResponse(cards);
		when(cardHelper.getCustomerCards(anyInt())).thenReturn(respo);
		CustomerCardsResponseDTO responseEntity = customerController.getCustomerCards(requestHeader, 1);
		assertEquals(responseEntity.getResponse().isEmpty(), false);
	}

	@Test
	void getCustomerPostCardsTest() throws Exception {
		setStaticfield();
		Map<String, String> requestHeader = null;
		CustomerRequestBody req = new CustomerRequestBody();
		req.setCustomerId(1);
		CustomerCardsResponseDTO respo = new CustomerCardsResponseDTO();
		respo.setStatus(true);
		CustomerCard card1 = new CustomerCard();
		CustomerCard card2 = new CustomerCard();
		List<CustomerCard> cards = Arrays.asList(card1, card2);
		respo.setResponse(cards);
		when(cardHelper.getCustomerCards(anyInt())).thenReturn(respo);
		CustomerCardsResponseDTO responseEntity = customerController.getCustomerPostCards(requestHeader, req);
		assertEquals(responseEntity.getResponse().isEmpty(), false);
	}

	@Test
	void createCustomerCardTest() throws Exception {
		setStaticfield();
		Map<String, String> requestHeader = null;
		CreateCardRequest req = new CreateCardRequest();
		req.setCustomerId(1);
		CustomerCardsResponseDTO respo = new CustomerCardsResponseDTO();
		respo.setStatus(true);
		CustomerCard card1 = new CustomerCard();
		CustomerCard card2 = new CustomerCard();
		List<CustomerCard> cards = Arrays.asList(card1, card2);
		respo.setResponse(cards);
		when(cardHelper.createCard(any())).thenReturn(respo);
		CustomerCardsResponseDTO responseEntity = customerController.createCustomerCard(requestHeader, req);
		assertEquals(responseEntity.getResponse().isEmpty(), false);
	}

	@Test
	void deleteCustomerCardTest() throws Exception {
		setStaticfield();
		Map<String, String> requestHeader = null;
		DeleteCardRequest req = new DeleteCardRequest();
		req.setCustomerId(1);
		CustomerCardsResponseDTO respo = new CustomerCardsResponseDTO();
		respo.setStatus(true);
		CustomerCard card1 = new CustomerCard();
		CustomerCard card2 = new CustomerCard();
		List<CustomerCard> cards = Arrays.asList(card1, card2);
		respo.setResponse(cards);
		when(cardHelper.deleteCard(any())).thenReturn(respo);
		CustomerCardsResponseDTO responseEntity = customerController.deleteCustomerCard(requestHeader, req);
		assertEquals(responseEntity.getResponse().isEmpty(), false);
	}

	@Test
	void printLogInfosTest() throws Exception {
		setStaticfield();
		PrintLogInfoRequest req = new PrintLogInfoRequest();
		req.setRawData("kgf");
		req.setData("nc");
		customerController.printLogInfos(req);

	}

	@Test
	public void enrollShukranAccountTest() throws IOException {
		ReflectionTestUtils.setField(customerController, "jwtFlag", "1");
		ShukranEnrollmentRequest mockShukranEnrollmentRequest = ShukranEnrollmentRequest.builder()
															.customerId(6559).storeId(51)
															.customerEmail("test.100@mailinator.com").build();
		Map<String, String> requestHeader = null;
		String mockStatusMessage ="Successfully enrolled shukran account for customer";
		when(customerV4Service.enrollShukranAccount(mockShukranEnrollmentRequest, requestHeader))
				.thenReturn(enrollmentResponseMock(mockStatusMessage));
		EnrollmentResponse enrollmentResponse = customerController.enrollShukranAccount(requestHeader,mockShukranEnrollmentRequest);
		assertNotNull(enrollmentResponse);
		assertEquals(enrollmentResponse.getStatusCode(), "200");

	}

	@Test
	public void linkShukranAccountTest() throws IOException {
		ReflectionTestUtils.setField(customerController, "jwtFlag", "1");
		LinkShukranRequest mockLinkShukranRequest = LinkShukranRequest.builder().customerId(6559)
													.profileId("4b7e6244-e78f-49a7-a113-0ef8bc0f31af").shukranLinkFlag(true).build();
		Map<String, String> requestHeader = null;
		String mockStatusMessage ="Successfully linked Shukran profile for customer";
		when(customerV4Service.linkShukranAccount(mockLinkShukranRequest, requestHeader))
				.thenReturn(enrollmentResponseMock(mockStatusMessage));
		EnrollmentResponse enrollmentResponse = customerController.linkShukranAccount(null,mockLinkShukranRequest);
		assertNotNull(enrollmentResponse);
		assertEquals(enrollmentResponse.getStatusCode(), "200");
	}

	private EnrollmentResponse enrollmentResponseMock(String mockStatusMessage) {
		return EnrollmentResponse.builder().status(true).statusCode("200").statusMsg(mockStatusMessage)
				.error(null).build();
	}

}
