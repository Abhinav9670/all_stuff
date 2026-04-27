package org.styli.services.customer.service.impl;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

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
import org.styli.services.customer.exception.CustomerException;
import org.styli.services.customer.model.Store;
import org.styli.services.customer.repository.StoreRepository;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class CustomRestTemplateTest {

	@Mock
	private RestTemplate restTemplate;

	@Mock
	private StoreRepository storeRepository;

	@InjectMocks
	private CustomRestTemplate customRestTemplate;

	@BeforeMethod
	public void setUp() {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	public void testResetPasswordWithStoreId() throws CustomerException {
		String email = "test@example.com";
		Integer storeId = 1;
		String magentoBaseUrl = "http://example.com";

		Store store = new Store();
		store.setCode("store1");

		when(storeRepository.findByStoreId(storeId)).thenReturn(store);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

		HttpEntity entity = new HttpEntity(headers);

		String url = magentoBaseUrl + "/rest/store1/V1/customers/password";
		String sbUrl = url + "?email=" + email + "&template=email_reset";

		ResponseEntity<String> responseEntity = new ResponseEntity<>("test response", HttpStatus.OK);

		when(restTemplate.exchange(Mockito.eq(sbUrl), Mockito.eq(HttpMethod.PUT), Mockito.eq(entity),
				Mockito.eq(String.class))).thenReturn(responseEntity);

		String result = customRestTemplate.resetPassword(email, storeId, magentoBaseUrl);

		Assert.assertEquals(result, "test response");

		verify(storeRepository).findByStoreId(storeId);
		verify(restTemplate).exchange(Mockito.eq(sbUrl), Mockito.eq(HttpMethod.PUT), Mockito.eq(entity),
				Mockito.eq(String.class));
	}

	@Test
	public void testResetPasswordWithoutStoreId() throws CustomerException {
		String email = "test@example.com";
		String magentoBaseUrl = "http://example.com";

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

		HttpEntity entity = new HttpEntity(headers);

		String url = magentoBaseUrl + "/rest/V1/customers/password";
		String sbUrl = url + "?email=" + email + "&template=email_reset";

		ResponseEntity<String> responseEntity = new ResponseEntity<>("test response", HttpStatus.OK);

		when(restTemplate.exchange(Mockito.eq(sbUrl), Mockito.eq(HttpMethod.PUT), Mockito.eq(entity),
				Mockito.eq(String.class))).thenReturn(responseEntity);

		String result = customRestTemplate.resetPassword(email, null, magentoBaseUrl);

		Assert.assertEquals(result, "test response");

		verify(storeRepository, never()).findByStoreId(Mockito.anyInt());
		verify(restTemplate).exchange(Mockito.eq(sbUrl), Mockito.eq(HttpMethod.PUT), Mockito.eq(entity),
				Mockito.eq(String.class));
	}

}