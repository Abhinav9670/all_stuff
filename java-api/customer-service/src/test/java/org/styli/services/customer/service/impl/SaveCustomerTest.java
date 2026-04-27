package org.styli.services.customer.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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
import org.styli.services.customer.exception.CustomerException;
import org.styli.services.customer.helper.PasswordHelper;
import org.styli.services.customer.model.SequenceCustomerEntity;
import org.styli.services.customer.pojo.registration.request.CustomerUpdateProfileRequest;
import org.styli.services.customer.pojo.registration.request.CustomerV4Registration;
import org.styli.services.customer.pojo.registration.response.Customer;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.pojo.registration.response.CustomerV4RegistrationResponse;
import org.styli.services.customer.repository.SequenceCustomerEntityRepository;
import org.styli.services.customer.service.Client;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.google.gson.Gson;

@SpringBootTest(classes = { SaveCustomerTest.class })

public class SaveCustomerTest extends AbstractTestNGSpringContextTests {

	@Autowired
	private WebApplicationContext webApplicationContext;

	private MockMvc mockMvc;

	private CustomerV4Registration customerRegistration;

	private CustomerV4RegistrationResponse customerV4RegistrationResponse;

	private static Customer cusresponse;

	private static org.styli.services.customer.pojo.registration.request.Customer cusRequest;

	private static CustomerUpdateProfileRequest req;

	@InjectMocks
	private SaveCustomer saveCustomer;

	@Mock
	PasswordHelper passwordHelper;

	@Mock
	RestTemplate restTemplate;

	@Mock
	Client client;

	@Mock
	OtpServiceImpl otpService;

	@Mock
	SequenceCustomerEntityRepository sequenceCustomerEntityRepository;

	private SequenceCustomerEntity entity;

	private CustomerEntity customerEntity;

	@BeforeMethod
	public void beforeMethod() {
		System.out.println("Initialise 	BeforeMethod ");
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

		entity = new SequenceCustomerEntity();
		entity.setSequenceValue(1l);
		entity.setVersion(10);
		when(sequenceCustomerEntityRepository.saveAndFlush(new SequenceCustomerEntity())).thenReturn(entity);

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
		customerEntity.setPasswordHash("samehash");
		when(client.saveAndFlushMongoCustomerDocument(any(CustomerEntity.class))).thenReturn(customerEntity);
	}

	@BeforeClass
	public void beforeClass() {
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

	@BeforeTest
	public void beforeTest() {
		System.out.println("Initialise @BeforeTest ");
		MockitoAnnotations.initMocks(this);
	}

	@Test
	public void saveCustomerEntityGCCTest() throws CustomerException {
		ReflectionTestUtils.setField(saveCustomer, "region", "GCC");

		Map<String, String> requestHeader = new HashMap<String, String>();

		when(otpService.getVerificationStatusFromRedis(anyString())).thenReturn(true);
		CustomerV4RegistrationResponse responseentity = saveCustomer.saveCustomer(customerRegistration, requestHeader,
				passwordHelper, client, "1");

		assertEquals(responseentity.getStatusMsg(), "SUCCESS");
		assertNotNull(responseentity.getResponse().getCustomer());
	}

	@Test
	public void saveCustomerEntityINTest() throws Exception {
		ReflectionTestUtils.setField(saveCustomer, "region", "IN");
		ResponseEntity<String> response = new ResponseEntity<>("123", HttpStatus.OK);

		Mockito.when(restTemplate.exchange(anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(String.class))).thenReturn(response);

		Map<String, String> requestHeader = new HashMap<String, String>();

		when(otpService.getVerificationStatusFromRedis(anyString())).thenReturn(true);
		CustomerV4RegistrationResponse responseentity = saveCustomer.saveCustomer(customerRegistration, requestHeader,
				passwordHelper, client, "1");
		assertEquals(responseentity.getStatusMsg(), "SUCCESS");
		assertNotNull(responseentity.getResponse().getCustomer());
	}

}
