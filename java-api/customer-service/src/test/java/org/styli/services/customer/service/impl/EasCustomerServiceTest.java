package org.styli.services.customer.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.web.client.RestTemplate;
import org.styli.services.customer.pojo.eas.EarnCustomerProfileResponse;
import org.styli.services.customer.pojo.eas.EarnCustomerUpdateProfileRequest;
import org.styli.services.customer.pojo.eas.EarnResBody;
import org.styli.services.customer.pojo.eas.EarnResponse;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.service.Client;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

@SpringBootTest(classes = { EasCustomerServiceTest.class })
public class EasCustomerServiceTest extends AbstractTestNGSpringContextTests {

	@InjectMocks
	EasCustomerService easCustomerService;

	Map<String, String> requestHeader;

	ResponseEntity<EarnResponse> response;
	@Mock
	RestTemplate restTemplate;

	EarnCustomerUpdateProfileRequest req;

	@Mock
	Client client;

	private CustomerEntity customerEntity;

	@BeforeTest
	public void beforeTest() {
		System.out.println("Initialise @BeforeTest ");
		MockitoAnnotations.initMocks(this);
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

	@Test
	public void updateTest() {
		req = new EarnCustomerUpdateProfileRequest();
		setData();
		easCustomerService.update(req, requestHeader);
		verify(client, atLeast(1)).findByEntityId(any());
	}

	@Test
	public void update2Test() {
		req = new EarnCustomerUpdateProfileRequest();
		req.setCustomerId(1);
		req.setStoreId(1);
		req.setStage(1);
		setData();
		easCustomerService.update(req, requestHeader);
		verify(client, atLeast(1)).findByEntityId(any());
	}

	@Test
	public void update3Test() {
		req = new EarnCustomerUpdateProfileRequest();
		req.setCustomerId(1);
		req.setStoreId(1);
		req.setStage(1);
		req.setGender(1);

		setData();

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(EarnResponse.class))).thenReturn(response);
		EarnCustomerProfileResponse respo = easCustomerService.update(req, requestHeader);
		assertEquals(respo.isStatus(), true);
		assertNotNull(respo.getEarnResponse());
	}

	@Test
	public void update4Test() {
		setData();
		req = new EarnCustomerUpdateProfileRequest();
		req.setCustomerId(1);
		req.setStoreId(1);
		req.setStage(2);
		req.setAgeGroupId(1);
		req.setDob(new Date());

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(EarnResponse.class))).thenReturn(response);
		EarnCustomerProfileResponse respo = easCustomerService.update(req, requestHeader);
		assertEquals(respo.isStatus(), true);
		assertNotNull(respo.getEarnResponse());
	}

	@Test
	public void update5Test() {
		req = new EarnCustomerUpdateProfileRequest();
		req.setCustomerId(1);
		req.setStoreId(1);
		req.setStage(2);
		req.setAgeGroupId(null);
		req.setDob(new Date());
		req.setIsVerifyMobileNumber(true);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(EarnResponse.class))).thenReturn(response);
		EarnCustomerProfileResponse respo = easCustomerService.update(req, requestHeader);
		assertEquals(respo.isStatus(), true);
		assertNotNull(respo.getEarnResponse());
	}

	@Test
	public void update6Test() {
		setData();
		req = new EarnCustomerUpdateProfileRequest();
		req.setCustomerId(1);
		req.setStoreId(1);
		req.setStage(3);
		req.setAgeGroupId(null);
		req.setDob(new Date());
		req.setMobileNumber("+918978345678");
		req.setIsVerifyMobileNumber(true);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(EarnResponse.class)))
				.thenThrow(new RuntimeException("Error saving address"));
		EarnCustomerProfileResponse respo = easCustomerService.update(req, requestHeader);
		assertEquals(respo.isStatus(), false);
	}

	@Test
	public void update8est() {
		setData();
		EarnCustomerUpdateProfileRequest req = new EarnCustomerUpdateProfileRequest();
		req.setCustomerId(1);
		req.setStoreId(1);
		req.setStage(2);
		req.setAgeGroupId(null);
		req.setDob(new Date());
		req.setMobileNumber("+918978345678");
		req.setIsVerifyMobileNumber(true);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(EarnResponse.class)))
				.thenThrow(new RuntimeException("Error saving address"));
		EarnCustomerProfileResponse respo = easCustomerService.update(req, requestHeader);
		assertEquals(respo.isStatus(), false);
	}

	@Test
	public void update7Test() {
		setData();
		EarnCustomerUpdateProfileRequest req = new EarnCustomerUpdateProfileRequest();
		req.setCustomerId(1);
		req.setStoreId(1);
		req.setStage(3);
		req.setAgeGroupId(null);
		req.setDob(new Date());
		req.setMobileNumber("+918978345678");
		req.setIsVerifyMobileNumber(true);

		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(EarnResponse.class))).thenReturn(response);
		EarnCustomerProfileResponse respo = easCustomerService.update(req, requestHeader);
		assertEquals(respo.isStatus(), true);
	}

	private void setData() {
		requestHeader = new HashMap<>();
		requestHeader.put("x-source", "android");
		requestHeader.put("x-client-version", "11");
		requestHeader.put("token", "token");
		requestHeader.put("X-Header-Token", "test@mail.com");

		when(client.findByEntityId(any())).thenReturn(customerEntity);

		EarnResponse earnResponse = new EarnResponse();
		earnResponse.setCode(1);
		earnResponse.setMessage(new EarnResBody());
		earnResponse.setCoins(100);
		response = new ResponseEntity<>(HttpStatus.OK).ok(earnResponse);
	}
}
