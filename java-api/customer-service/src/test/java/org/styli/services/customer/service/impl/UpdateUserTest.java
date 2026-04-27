package org.styli.services.customer.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Date;
import java.util.Map;

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
import org.styli.services.customer.controller.CustomerControllerTest;
import org.styli.services.customer.pojo.DisabledServices;
import org.styli.services.customer.pojo.eas.EarnOnProfileUpdateCompleteRequest;
import org.styli.services.customer.pojo.registration.request.CustomerUpdateProfileRequest;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.pojo.registration.response.CustomerUpdateProfileResponse;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.pojo.config.StoreConfigResponse;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

@SpringBootTest(classes = { CustomerControllerTest.class })
public class UpdateUserTest extends AbstractTestNGSpringContextTests {

	@InjectMocks
	UpdateUser updateUser;
	@Mock
	ClientImpl client;

	@InjectMocks
	EasCustomerService easCustomerService;

	@Autowired
	private WebApplicationContext webApplicationContext;

	private MockMvc mockMvc;
	private CustomerEntity customerEntity;
	@InjectMocks
	private Constants constants;
	@Mock
	private RestTemplate restTemplate;

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

	@BeforeTest
	public void beforeTest() {
		System.out.println("Initialise @BeforeTest ");
		MockitoAnnotations.initMocks(this);

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

	@Test
	public void f() throws IOException {
		DisabledServices ser = new DisabledServices(true, false, true);
		StoreConfigResponse serRespo = new StoreConfigResponse();
		serRespo.setDisabledServices(ser);
		ReflectionTestUtils.setField(constants, "StoreConfigResponse", serRespo);

		String requestData = new String(
				Files.readAllBytes(Paths.get("src/test/resources/customer_update_request.json")));
		String responseData = new String(
				Files.readAllBytes(Paths.get("src/test/resources/customer_update_response.json")));

		CustomerUpdateProfileRequest customerUpdateProfileRequest = g.fromJson(requestData,
				CustomerUpdateProfileRequest.class);
		CustomerUpdateProfileResponse customerUpdateProfileResponse = g.fromJson(responseData,
				CustomerUpdateProfileResponse.class);
		Map<String, String> requestHeader = null;
		ReflectionTestUtils.setField(updateUser, "easCustomerService", easCustomerService);
		EarnOnProfileUpdateCompleteRequest respo = new EarnOnProfileUpdateCompleteRequest();
		respo.setCustomerId(1);

		ResponseEntity<EarnOnProfileUpdateCompleteRequest> res = new ResponseEntity<>(HttpStatus.OK).ok(respo);
		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(EarnOnProfileUpdateCompleteRequest.class))).thenReturn(res);
		when(client.findByEntityId(anyInt())).thenReturn(customerEntity);
		when(client.saveAndFlushCustomerEntity(any())).thenReturn(customerEntity);
		CustomerUpdateProfileResponse responseEntity = updateUser.update(customerUpdateProfileRequest, client,"");
		assertEquals(responseEntity.getStatusCode(), "200");
		assertNotNull(responseEntity.getResponse().getCustomer());
	}
}
