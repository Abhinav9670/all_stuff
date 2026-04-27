package org.styli.services.customer.service.impl;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.styli.services.customer.pojo.registration.response.Customer;
import org.styli.services.customer.repository.CustomerEntityMysqlRepository;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;
import org.styli.services.customer.utility.Constants;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class AsyncServiceTest {

	@Mock
	RestTemplate restTemplate;

	@Mock
	CustomerEntityMysqlRepository customerEntityMysqlRepository;

	@Mock
	CustomerEntityRepository customerEntityRepository;

	@InjectMocks
	AsyncService asyncService;

	@BeforeClass
	public void setUp() {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	public void testAsyncSalesOrdersUpdateCustId() throws Exception {
		Customer customer = new Customer();
		customer.setCustomerId(1);

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
		HttpEntity<Customer> requestBody = new HttpEntity<>(customer, requestHeaders);
//        Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
//				Mockito.any(HttpEntity.class), Mockito.eq(AccountDeletionEligibleResponse.class))).thenReturn(response);
		ResponseEntity<?> response = new ResponseEntity<>(HttpStatus.OK);
		Mockito.when(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(Void.class))).thenReturn((ResponseEntity<Void>) response);

		CompletableFuture<String> future = asyncService.asyncSalesOrdersUpdateCustId(customer);
		Assert.assertNull(future);
	}
}
