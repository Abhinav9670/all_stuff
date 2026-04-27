package org.styli.services.customer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.styli.services.customer.model.SequenceCustomerEntity;
import org.styli.services.customer.pojo.registration.request.CustomerWishlistV5Request;
import org.styli.services.customer.pojo.registration.response.CustomerWishlistResponse;
import org.styli.services.customer.repository.SequenceCustomerEntityRepository;
import org.styli.services.customer.service.CustomerV5Service;
import org.styli.services.customer.service.impl.ConfigServiceImpl;
import org.styli.services.customer.service.impl.CustomerV4ServiceImpl;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class CustomeromsControllerTest {
	@Mock
	private SequenceCustomerEntityRepository sequenceCustomerEntityRepository;
	@Mock
	private ConfigServiceImpl configServiceImpl;
	@InjectMocks
	private CustomerV4ServiceImpl customerV4Service;

	@InjectMocks
	private CustomeromsController customeromsController;

	@Mock
	CustomerV5Service customerV5Service;

	@BeforeClass
	public void setUp() {
		MockitoAnnotations.initMocks(this);

	}

	@Test
	public void testGetIncrementId() throws Exception {
		setStatiFields();
		SequenceCustomerEntity entity = new SequenceCustomerEntity();
		entity.setVersion(1);
		entity.setSequenceValue(1234l);
		Mockito.when(sequenceCustomerEntityRepository.saveAndFlush(any())).thenReturn(entity);
		Mockito.when(configServiceImpl.checkAuthorizationInternal(anyString())).thenReturn(true);
		Map<String, String> requestHeader = new HashMap<String, String>();
		requestHeader.put("Authorization", "Bearer testToken");
		// Perform the operation and assert the result
		String value = customeromsController.getIncrementId(requestHeader, "token");
		assertEquals(value, "1234");
	}

	@Test
	void getInternalWishlistForCustomerTest() throws IOException {
		CustomerWishlistV5Request customerWishlistV5Request = new CustomerWishlistV5Request();
		customerWishlistV5Request.setCustomerId(6559);
		customerWishlistV5Request.setStoreId(51);

		String responseData = new String(Files.readAllBytes(Paths.get("src/test/resources/wishlist_response.json")));
		Gson g = new Gson();
		CustomerWishlistResponse customerWishListResponse = g.fromJson(responseData, CustomerWishlistResponse.class);
		when(customerV5Service.getWishList(customerWishlistV5Request)).thenReturn(customerWishListResponse);
		Mockito.when(configServiceImpl.checkAuthorizationInternal(anyString())).thenReturn(true);
		CustomerWishlistResponse responseEntity = customeromsController.getWishlistForCustomer(customerWishlistV5Request, anyString());
		assertEquals(responseEntity.getStatusCode(), "200");
		assertNotNull(responseEntity.getResponse());
	}
	private void setStatiFields() {
		ReflectionTestUtils.setField(customeromsController, "customerV4Service", customerV4Service);
	}
}
