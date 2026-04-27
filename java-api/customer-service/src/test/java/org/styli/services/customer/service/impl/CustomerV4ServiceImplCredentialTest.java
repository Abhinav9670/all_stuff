package org.styli.services.customer.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.styli.services.customer.exception.CustomerException;
import org.styli.services.customer.helper.LoginCapchaHelper;
import org.styli.services.customer.helper.PasswordHelper;
import org.styli.services.customer.jwt.security.jwtsecurity.security.JwtValidator;
import org.styli.services.customer.model.SequenceCustomerEntity;
import org.styli.services.customer.model.Store;
import org.styli.services.customer.pojo.DisabledServices;
import org.styli.services.customer.pojo.FirstFreeShipping;
import org.styli.services.customer.pojo.address.response.CustomerAddrees;
import org.styli.services.customer.pojo.address.response.CustomerAddreesResponse;
import org.styli.services.customer.pojo.address.response.CustomerAddressEntity;
import org.styli.services.customer.pojo.address.response.CustomerAddressEntityVarchar;
import org.styli.services.customer.pojo.consul.CountryEnabledDays;
import org.styli.services.customer.pojo.consul.FreeShipping;
import org.styli.services.customer.pojo.consul.QuoteFreeShippingConsul;
import org.styli.services.customer.pojo.registration.request.CustomerLoginV4Request;
import org.styli.services.customer.pojo.registration.request.CustomerQueryReq;
import org.styli.services.customer.pojo.registration.request.CustomerV4Registration;
import org.styli.services.customer.pojo.registration.request.CustomerValidityCheckRequest;
import org.styli.services.customer.pojo.registration.request.LoginType;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.pojo.registration.response.CustomerRestPassResponse;
import org.styli.services.customer.pojo.registration.response.CustomerV4RegistrationResponse;
import org.styli.services.customer.pojo.registration.response.RecaptchaGoogleVerifyResponse;
import org.styli.services.customer.pojo.registration.response.RecaptchaVerifyRequest;
import org.styli.services.customer.pojo.registration.response.RecaptchaVerifyResponse;
import org.styli.services.customer.redis.RedisHelper;
import org.styli.services.customer.repository.SequenceCustomerEntityRepository;
import org.styli.services.customer.repository.StaticComponents;
import org.styli.services.customer.repository.StoreRepository;
import org.styli.services.customer.repository.Address.CustomerAddressEntityVarcharRepository;
import org.styli.services.customer.repository.Customer.CustomerAddressEntityRepository;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;
import org.styli.services.customer.repository.Customer.CustomerGridFlatRepository;
import org.styli.services.customer.service.Client;
import org.styli.services.customer.service.WhatsappService;
import org.styli.services.customer.service.impl.Address.SaveAddress;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.helper.AddressMapperHelperV2;
import org.styli.services.customer.utility.pojo.config.StoreConfigResponse;
import org.styli.services.customer.utility.pojo.config.Stores;
import org.styli.services.customer.utility.pojo.request.AdrsmprResponse;
import org.styli.services.customer.utility.pojo.request.SearchCityResponse;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@SpringBootTest(classes = { CustomerV4ServiceImplCredentialTest.class })
public class CustomerV4ServiceImplCredentialTest extends AbstractTestNGSpringContextTests {

	CustomerAddressEntity newCustAddressObject;
	CustomerAddrees customerAddrees;
	CustomerAddressEntityVarchar customerAddressEntityVarchar;
	AdrsmprResponse ad;
	@Autowired
	private WebApplicationContext webApplicationContext;

	private CustomerV4Registration customerInfoRequest;

	private CustomerV4RegistrationResponse customerV4RegistrationResponse;

	private List<org.styli.services.customer.utility.pojo.config.Stores> storeList;

	@InjectMocks
	private CustomerV4ServiceImpl customerV4ServiceImpl;

	@Mock
	private JwtValidator validator;
	@Mock
	StaticComponents staticComponents;
	@Mock
	CustomerGridFlatRepository customerGridFlatRepository;
	@Mock
	CustomerAddressEntityVarcharRepository customerAddressEntityVarcharRepository;

	@InjectMocks
	ClientImpl clientImpl;
	@Mock
	AsyncService asyncService;
	@Mock
	CustomerEntityRepository customerEntityRepository;
	@Mock
	CustomerAddressEntityRepository customerAddressEntityRepository;
	@Mock
	StoreRepository storeRepository;

	@InjectMocks
	ValidateUser validateUser;

	@InjectMocks
	UpdateUser updateUser;
	@InjectMocks
	Constants constants;
	@InjectMocks
	SaveAddress saveAddress;

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
	AddWishlist addWishlist;

	@InjectMocks
	LoginCapchaHelper loginCapchaHelper;

	@Mock
	WhatsappService whatsappService;

	@InjectMocks
	private SaveCustomer saveCustomer;

	Map<String, String> requestHeader;

	private CustomerEntity customerEntity;

	private CustomerLoginV4Request customerLoginV4Request;

	@InjectMocks
	private CustomRestTemplate customRestTemplate;
	@Mock
	private RestTemplate withoutEurekarestTemplate;
	@InjectMocks
	private AddressMapperHelperV2 addressMapperHelperV2;

	@BeforeMethod
	public void beforeMethod() {
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
		customerEntity.setJwtToken(1);
	}

	@BeforeClass
	public void beforeClass() {

		requestHeader = new HashMap<>();
		requestHeader.put("x-source", "android");
		requestHeader.put("x-client-version", "11");
		requestHeader.put("token", "token");
		requestHeader.put("X-Header-Token", "test@mail.com");
		try {
			// Prepare mock Data
			String requestData = new String(
					Files.readAllBytes(Paths.get("src/test/resources/customer_login_request.json")));

			Gson g = new Gson();
			customerLoginV4Request = g.fromJson(requestData, CustomerLoginV4Request.class);

			String requestcusData = new String(
					Files.readAllBytes(Paths.get("src/test/resources/customer_registation_request.json")));
			String responseData = new String(
					Files.readAllBytes(Paths.get("src/test/resources/customer_registration_response.json")));
			customerInfoRequest = g.fromJson(requestcusData, CustomerV4Registration.class);
			customerV4RegistrationResponse = g.fromJson(responseData, CustomerV4RegistrationResponse.class);
			String storeData = new String(Files.readAllBytes(Paths.get("src/test/resources/store_list.json")));
			Type listType = new TypeToken<ArrayList<Stores>>() {
			}.getType();
			storeList = g.fromJson(storeData, listType);
		} catch (Exception e) {
		}
	}

	@BeforeTest
	public void beforeTest() {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	public void resetCustomerPasswordTest() throws CustomerException {
		setStaticfields();
		CustomerQueryReq customerExitsReq = new CustomerQueryReq();
		customerExitsReq.setUseridentifier("testuser@mailinator.com");
		customerExitsReq.setStoreId(1);
		customerExitsReq.setLoginType(LoginType.MOBILE_LOGIN);
		Map<String, String> requestHeader = new HashMap<>();
		requestHeader.put("x-source", "msite");
		ResponseEntity<String> body = new ResponseEntity<>(HttpStatus.OK).ok("true");
		Mockito.when(withoutEurekarestTemplate.exchange(anyString(), Mockito.eq(HttpMethod.PUT),
				Mockito.any(HttpEntity.class), Mockito.eq(String.class))).thenReturn(body);
		Store store = new Store();
		store.setCode("001");
		when(storeRepository.findByStoreId(anyInt())).thenReturn(store);
		CustomerRestPassResponse response = customerV4ServiceImpl.resetCustomerPassword("testuser@mailinator.com", 1,
				requestHeader);

		assertEquals(response.getStatusCode(), "200");
		assertNotNull(response.getResponse());
	}

	@Test
	public void resetCustomerPasswordFalseTest() throws CustomerException {
		setStaticfields();
		CustomerQueryReq customerExitsReq = new CustomerQueryReq();
		customerExitsReq.setUseridentifier("testuser@mailinator.com");
		customerExitsReq.setStoreId(1);
		customerExitsReq.setLoginType(LoginType.MOBILE_LOGIN);
		Map<String, String> requestHeader = new HashMap<>();
		requestHeader.put("x-source", "msite");
		ResponseEntity<String> body = new ResponseEntity<>(HttpStatus.OK).ok("false");
		Mockito.when(withoutEurekarestTemplate.exchange(anyString(), Mockito.eq(HttpMethod.PUT),
				Mockito.any(HttpEntity.class), Mockito.eq(String.class))).thenReturn(body);
		Store store = new Store();
		store.setCode("001");
		when(storeRepository.findByStoreId(anyInt())).thenReturn(store);
		CustomerRestPassResponse response = customerV4ServiceImpl.resetCustomerPassword("testuser@mailinator.com", 1,
				requestHeader);

		assertEquals(response.getStatusCode(), "200");
		assertNotNull(response.getResponse());
	}

	@Test
	public void resetCustomerPasswordNullTest() throws CustomerException {
		setStaticfields();
		CustomerQueryReq customerExitsReq = new CustomerQueryReq();
		customerExitsReq.setUseridentifier("testuser@mailinator.com");
		customerExitsReq.setStoreId(1);
		customerExitsReq.setLoginType(LoginType.MOBILE_LOGIN);
		Map<String, String> requestHeader = new HashMap<>();
		requestHeader.put("x-source", "msite");
		ResponseEntity<String> body = new ResponseEntity<>(HttpStatus.OK).ok("false");
		Mockito.when(withoutEurekarestTemplate.exchange(anyString(), Mockito.eq(HttpMethod.PUT),
				Mockito.any(HttpEntity.class), Mockito.eq(String.class))).thenReturn(body);
		Store store = new Store();
		store.setCode("001");
		when(storeRepository.findByStoreId(anyInt())).thenReturn(store);
		CustomerRestPassResponse response = customerV4ServiceImpl.resetCustomerPassword("testuser@mailinator.com", 1,
				requestHeader);

		assertEquals(response.getStatusCode(), "200");
		assertNotNull(response.getResponse());
	}

//	@Test
//	public void getWhatsAppOtpTest() throws CustomerException {
//		customerV4ServiceImpl.getWhatsAppOtp(null, null);
//
//	}

	@Test
	void verifyRecaptchaTest() {
		RecaptchaVerifyRequest req = new RecaptchaVerifyRequest();
		req.setToken("token");
		Map<String, String> requestHeader = new HashMap<>();
		requestHeader.put("x-source", "msite");
		RecaptchaGoogleVerifyResponse body = new RecaptchaGoogleVerifyResponse();
		body.setSuccess(true);
		ResponseEntity<RecaptchaGoogleVerifyResponse> resp = new ResponseEntity<>(HttpStatus.OK).ok(body);
		Mockito.when(restTemplate.exchange(anyString(), Mockito.eq(HttpMethod.POST), Mockito.any(HttpEntity.class),
				Mockito.eq(RecaptchaGoogleVerifyResponse.class))).thenReturn(resp);

		RecaptchaVerifyResponse responseEntity = customerV4ServiceImpl.verifyRecaptcha(requestHeader, req, "1");
		assertEquals(responseEntity.isStatus(), true);
	}

	@Test
	void verifyRecaptchaTestNull() {
		RecaptchaVerifyRequest req = new RecaptchaVerifyRequest();
		req.setToken("token");
		Map<String, String> requestHeader = new HashMap<>();
		requestHeader.put("x-source", "msite");
		RecaptchaGoogleVerifyResponse body = new RecaptchaGoogleVerifyResponse();
		body.setSuccess(false);
		body.setErrorCodes(Arrays.asList("500", "501"));
		ResponseEntity<RecaptchaGoogleVerifyResponse> resp = new ResponseEntity<>(HttpStatus.OK).ok(body);
		Mockito.when(restTemplate.exchange(anyString(), Mockito.eq(HttpMethod.POST), Mockito.any(HttpEntity.class),
				Mockito.eq(RecaptchaGoogleVerifyResponse.class))).thenReturn(resp);

		RecaptchaVerifyResponse responseEntity = customerV4ServiceImpl.verifyRecaptcha(requestHeader, req, "1");
		assertEquals(responseEntity.isStatus(), false);
	}

	@Test
	void verifyRecaptchaTestExceptionNull() {
		RecaptchaVerifyRequest req = new RecaptchaVerifyRequest();
		req.setToken("token");
		Map<String, String> requestHeader = new HashMap<>();
		requestHeader.put("x-source", "msite");

		ResponseEntity<RecaptchaGoogleVerifyResponse> resp = new ResponseEntity<>(HttpStatus.OK).ok(null);
		Mockito.when(restTemplate.exchange(anyString(), Mockito.eq(HttpMethod.POST), Mockito.any(HttpEntity.class),
				Mockito.eq(RecaptchaGoogleVerifyResponse.class))).thenThrow(new RestClientException("json exception"));

		RecaptchaVerifyResponse responseEntity = customerV4ServiceImpl.verifyRecaptcha(requestHeader, req, "1");
		assertEquals(responseEntity.isStatus(), false);
	}

	@Test
	void saveV4CustomerTest() throws CustomerException {
		setStaticfields();
		Map<String, String> requestHeader = new HashMap<>();
		requestHeader.put("x-source", "msite");
		when(customerEntityRepository.findByPhoneNumber(anyString())).thenReturn(customerEntity);
		CustomerV4RegistrationResponse reponse = customerV4ServiceImpl.saveV4Customer(customerInfoRequest,
				requestHeader);
		assertEquals(reponse.isStatus(), false);
		assertEquals(reponse.getStatusCode(), "206");
	}

	@Test
	void saveAddressTest() throws CustomerException {
		setStaticfields();
		saveAddressSetUpData();

		when(customerEntityRepository.existsById(anyInt())).thenReturn(true);
		when(customerAddressEntityRepository.existsById(anyInt())).thenReturn(true);
		when(customerEntityRepository.findByEntityId(anyInt())).thenReturn(customerEntity);
		when(customerEntityRepository.findByEmail(anyString())).thenReturn(customerEntity);
		when(customerAddressEntityRepository.saveAndFlush(any())).thenReturn(newCustAddressObject);
		when(customerAddressEntityVarcharRepository.findByEntityIdAndAttributeId(anyInt(), anyInt()))
				.thenReturn(customerAddressEntityVarchar);
		when(customerAddressEntityVarcharRepository.saveAndFlush(any())).thenReturn(customerAddressEntityVarchar);

		ResponseEntity<AdrsmprResponse> resp = new ResponseEntity<>(HttpStatus.OK).ok(ad);
		Mockito.when(restTemplate.exchange(anyString(), Mockito.eq(HttpMethod.GET), Mockito.any(HttpEntity.class),
				Mockito.eq(AdrsmprResponse.class))).thenReturn(resp);

		CustomerAddreesResponse reponse = customerV4ServiceImpl.saveAddress(customerAddrees, true, requestHeader);
		assertEquals(reponse.isStatus(), true);
		assertNotNull(reponse.getResponse());
	}

	@Test
	void getAddressTest() throws CustomerException {
		setStaticfields();
		saveAddressSetUpData();
		customerAddrees.setEmail("testmail.com");
		when(customerEntityRepository.existsById(anyInt())).thenReturn(true);
		when(customerAddressEntityRepository.existsById(anyInt())).thenReturn(true);
		when(customerEntityRepository.findByEntityId(anyInt())).thenReturn(customerEntity);
		when(customerEntityRepository.findByEmail(anyString())).thenReturn(customerEntity);
		when(customerAddressEntityRepository.saveAndFlush(any())).thenReturn(newCustAddressObject);
		when(customerAddressEntityVarcharRepository.findByEntityIdAndAttributeId(anyInt(), anyInt()))
				.thenReturn(customerAddressEntityVarchar);
		when(customerAddressEntityVarcharRepository.saveAndFlush(any())).thenReturn(customerAddressEntityVarchar);
		when(customerAddressEntityRepository.findAllByCustomerId(anyInt()))
				.thenReturn(Arrays.asList(newCustAddressObject));

		ResponseEntity<AdrsmprResponse> resp = new ResponseEntity<>(HttpStatus.OK).ok(ad);
		Mockito.when(restTemplate.exchange(anyString(), Mockito.eq(HttpMethod.GET), Mockito.any(HttpEntity.class),
				Mockito.eq(AdrsmprResponse.class))).thenReturn(resp);

		CustomerAddreesResponse reponse = customerV4ServiceImpl.getAddress(1, requestHeader);
		assertEquals(reponse.isStatus(), true);
		assertNotNull(reponse.getResponse());
	}

	@Test
	void getAddressNotfoundTest() throws CustomerException {
		setStaticfields();
		saveAddressSetUpData();
		customerAddrees.setEmail("testmail.com");
		when(customerEntityRepository.existsById(anyInt())).thenReturn(true);
		when(customerAddressEntityRepository.existsById(anyInt())).thenReturn(true);
		when(customerEntityRepository.findByEntityId(anyInt())).thenReturn(customerEntity);
		when(customerEntityRepository.findByEmail(anyString())).thenReturn(customerEntity);
		when(customerAddressEntityRepository.saveAndFlush(any())).thenReturn(newCustAddressObject);
		when(customerAddressEntityVarcharRepository.findByEntityIdAndAttributeId(anyInt(), anyInt()))
				.thenReturn(customerAddressEntityVarchar);
		when(customerAddressEntityVarcharRepository.saveAndFlush(any())).thenReturn(customerAddressEntityVarchar);

		ResponseEntity<AdrsmprResponse> resp = new ResponseEntity<>(HttpStatus.OK).ok(ad);
		Mockito.when(restTemplate.exchange(anyString(), Mockito.eq(HttpMethod.GET), Mockito.any(HttpEntity.class),
				Mockito.eq(AdrsmprResponse.class))).thenReturn(resp);

		CustomerAddreesResponse reponse = customerV4ServiceImpl.getAddress(1, requestHeader);
		assertEquals(reponse.isStatus(), true);
		assertEquals(reponse.getStatusCode(), "201");
	}

	@Test
	void setFreeShippingTest() {
		QuoteFreeShippingConsul qoute = new QuoteFreeShippingConsul();
		FreeShipping freeship = new FreeShipping();
		CountryEnabledDays ced = new CountryEnabledDays();
		ced.setEnable(true);
		ced.setExpireInDays("5");
		freeship.setAe(ced);
		freeship.setSa(ced);
		qoute.setFreeShipping(freeship);
		ReflectionTestUtils.setField(constants, "freeShipping", qoute);
		FirstFreeShipping freeshipRepo1 = customerV4ServiceImpl.setFreeShipping(new Date().toString(), 1);
		FirstFreeShipping freeshipRepo2 = customerV4ServiceImpl.setFreeShipping(new Date().toString(), 7);
		assertEquals(freeshipRepo1.isActive(), true);
		assertEquals(freeshipRepo2.isActive(), true);

	}

	@Test
	void validateDeletedUserExceptionTest() {
		customerEntity.setIsActive(1);
		when(customerEntityRepository.findByEntityId(anyInt())).thenReturn(customerEntity);
		customerV4ServiceImpl.validateDeletedUser(7);
		verify(customerEntityRepository, atLeastOnce()).findByEntityId(anyInt());
	}

	@Test
	void getPhoneNumberByEmailIdTest() {
		customerEntity.setIsActive(1);
		when(customerEntityRepository.findByEmail(anyString())).thenReturn(customerEntity);
		String phone = customerV4ServiceImpl.getPhoneNumberByEmailId(anyString());
		verify(customerEntityRepository, atLeastOnce()).findByEmail(anyString());
		assertEquals(phone, customerEntity.getPhoneNumber());
	}

	@Test
	void customerValidityCheck() {
		CustomerValidityCheckRequest customerValidityCheckRequest = new CustomerValidityCheckRequest();
		customerValidityCheckRequest.setCustomerEmail("test@mail.com");
		customerValidityCheckRequest.setCustomerId(1);
		when(customerEntityRepository.findByEntityIdAndEmail(anyInt(), anyString())).thenReturn(customerEntity);
		when(passwordHelper.generateToken("", "", 1, false)).thenReturn("token");
		customerV4ServiceImpl.customerValidityCheck(customerValidityCheckRequest, null);
		verify(customerEntityRepository, times(1)).findByEntityIdAndEmail(anyInt(), anyString());
	}

	@Test
	void getRegistrationIncrementIdTest() throws CustomerException {
		SequenceCustomerEntity sequenceCustomerEntity = new SequenceCustomerEntity();
		sequenceCustomerEntity.setSequenceValue(1l);
		when(sequenceCustomerEntityRepository.saveAndFlush(any())).thenReturn(sequenceCustomerEntity);
		int i = customerV4ServiceImpl.getRegistrationIncrementId();
		assertEquals(i, 1);

	}

	@Test
	void getAddressByIdTest() {
		saveAddressSetUpData();
		CustomerAddressEntity customerAddressEntity = new CustomerAddressEntity();
		customerAddressEntity.setFirstname("first");
		customerAddressEntity.setLastName("last");
		when(customerEntityRepository.findByEntityId(anyInt())).thenReturn(customerEntity);
		when(customerAddressEntityRepository.findByEntityId(anyInt())).thenReturn(customerAddressEntity);
		CustomerAddreesResponse response = customerV4ServiceImpl.getAddressById(1, 1);
		assertEquals(response.isStatus(), true);
		assertEquals(response.getStatusCode(), "200");
	}

	@Test
	void getAddressByIdFailTest() {
		saveAddressSetUpData();
		CustomerAddressEntity customerAddressEntity = new CustomerAddressEntity();
		customerAddressEntity.setFirstname("first");
		customerAddressEntity.setLastName("last");
		when(customerEntityRepository.findByEntityId(anyInt())).thenReturn(customerEntity);
		when(customerAddressEntityRepository.findByEntityId(anyInt())).thenReturn(null);
		CustomerAddreesResponse response = customerV4ServiceImpl.getAddressById(1, 1);
		assertEquals(response.isStatus(), false);
		assertEquals(response.getStatusCode(), "201");
	}

	@Test
	void getzAddressExceptionTest() throws CustomerException {
		setStaticfields();
		saveAddressSetUpData();
		customerAddrees.setEmail("testmail.com");
		when(customerEntityRepository.existsById(anyInt())).thenReturn(true);
		when(customerAddressEntityRepository.findAllByCustomerId(anyInt()))
				.thenThrow(new DataAccessException("Error while deleting card") {
				});

		ResponseEntity<AdrsmprResponse> resp = new ResponseEntity<>(HttpStatus.OK).ok(ad);
		Mockito.when(restTemplate.exchange(anyString(), Mockito.eq(HttpMethod.GET), Mockito.any(HttpEntity.class),
				Mockito.eq(AdrsmprResponse.class))).thenReturn(resp);

		CustomerAddreesResponse reponse = customerV4ServiceImpl.getAddress(1, requestHeader);
		assertEquals(reponse.isStatus(), false);
		assertEquals(reponse.getStatusCode(), "204");
	}

	private void saveAddressSetUpData() {
		// TODO Auto-generated method stub
		Map<String, String> saveEmailTranslation = new LinkedHashMap();
		Map<Integer, String> attrMap = new HashMap();
		attrMap.put(1, "area");
		attrMap.put(1, "nearest_landmark");
		saveEmailTranslation.put("en", "en mail");
		ReflectionTestUtils.setField(constants, "storesList", storeList);
		ReflectionTestUtils.setField(saveAddress, "region", "IN");
		ReflectionTestUtils.setField(saveAddress, "attributeMap", attrMap);
		ReflectionTestUtils.setField(customerV4ServiceImpl, "saveEmailTranslation", saveEmailTranslation);
		customerAddrees = new CustomerAddrees();
		customerAddrees.setCustomerId(1);
		customerAddrees.setMobileNumber("+91 8989878900");
		customerAddrees.setStoreId(1);
		customerAddrees.setEmail("test@mail.com");
		customerAddrees.setCountry("IN");
		customerAddrees.setCity("bbsr");
		customerAddrees.setCountry("IN");
		customerAddrees.setRegion("bbsr");
		SearchCityResponse city = new SearchCityResponse();
		city.setCity("bbsr");
		city.setCountry("IN");
		city.setProvince("bbsr");
		newCustAddressObject = new CustomerAddressEntity();
		newCustAddressObject.setEntityId(1);
		newCustAddressObject.setIsActive(1);

		customerAddressEntityVarchar = new CustomerAddressEntityVarchar();
		customerAddressEntityVarchar.setValueId(1);

		ad = new AdrsmprResponse();
		ad.setStatus(true);
		ad.setResponse(city);
	}

	private void setStaticfields() {
		ReflectionTestUtils.setField(customerV4ServiceImpl, "jwtFlag", "1");
		ReflectionTestUtils.setField(customerV4ServiceImpl, "env", "dev");
		ReflectionTestUtils.setField(customerV4ServiceImpl, "consulIpAddress", "10.0.0.1");
		ReflectionTestUtils.setField(customerV4ServiceImpl, "saveCustomer", saveCustomer);
		ReflectionTestUtils.setField(customerV4ServiceImpl, "client", clientImpl);
		ReflectionTestUtils.setField(customerV4ServiceImpl, "saveAddress", saveAddress);
		ReflectionTestUtils.setField(saveAddress, "addressMapperHelperV2", addressMapperHelperV2);
		ReflectionTestUtils.setField(addressMapperHelperV2, "adrsmprBaseUrl", "someurl");
		ReflectionTestUtils.setField(clientImpl, "customRestTemplate", customRestTemplate);

		ReflectionTestUtils.setField(customerV4ServiceImpl, "magentoBaseUrl", "someurl");
		ReflectionTestUtils.setField(customerV4ServiceImpl, "loginUser", loginUser);
		ReflectionTestUtils.setField(customerV4ServiceImpl, "recaptchaSecretKey", "cccvevevee");
		ReflectionTestUtils.setField(customerV4ServiceImpl, "whatsappService", whatsappService);
		ReflectionTestUtils.setField(customerV4ServiceImpl, "secretReactJavaApi", customerLoginV4Request.getPassword());
		ReflectionTestUtils.setField(customerV4ServiceImpl, "addWishlist", addWishlist);
		ReflectionTestUtils.setField(customerV4ServiceImpl, "validateUser", validateUser);
		ReflectionTestUtils.setField(customerV4ServiceImpl, "updateUser", updateUser);
		DisabledServices ser = new DisabledServices(true, true, true);
		StoreConfigResponse serRespo = new StoreConfigResponse();
		serRespo.setDisabledServices(ser);
		ReflectionTestUtils.setField(constants, "StoreConfigResponse", serRespo);
	}
}
