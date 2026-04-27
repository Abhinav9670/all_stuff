package org.styli.services.customer.utility.controller;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.styli.services.customer.repository.SequenceCustomerEntityRepository;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.helper.AddressMapperHelper;
import org.styli.services.customer.utility.pojo.config.AppEnvironments;
import org.styli.services.customer.utility.pojo.config.Store;
import org.styli.services.customer.utility.service.impl.ConfigServiceV2Impl;
import org.styli.services.customer.utility.service.impl.ConfigUtilityServiceImpl;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@SpringBootTest(classes = { UtilityControllerMissTest.class })
public class UtilityControllerMissTest extends AbstractTestNGSpringContextTests {

	@InjectMocks
	private UtilityController utilityController;

	private List<Store> orderStoreList;

	@Mock
	RestTemplate restTemplate;
	@InjectMocks
	private Constants constants;

	@InjectMocks
	private ConfigUtilityServiceImpl configService;

	@InjectMocks
	private ConfigServiceV2Impl configServiceV2;

	@InjectMocks
	private AddressMapperHelper addressMapperHelper;
	@Mock
	AppEnvironments appEnvironments;
	@Mock
	private SequenceCustomerEntityRepository sequenceCustomerEntityRepository;

	Map<String, String> requestHeader;

	@Spy
	private HttpServletRequest httpServletRequest = Mockito.mock(HttpServletRequest.class);

	@BeforeClass
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		requestHeader = new HashMap<String, String>();
		requestHeader.put("Authorization", "Bearer testToken");

	}

	@Test
	public void testGetAddressMapper() throws Exception {
		setStaticfields();
		ReflectionTestUtils.setField(addressMapperHelper, "addressMapperFlag", "1");
		// Prepare test data
		String countryCode = "US";

		// Prepare mock response
		String mockResponse = "testResponse";

		Map<String, Map<String, String>> map = new HashMap<>();
		Map<String, String> body = new HashMap<>();
		body.put("key", "value");
		map.put("response", body);
		ResponseEntity<Map> response = new ResponseEntity<>(HttpStatus.OK).ok(map);
		Mockito.when(restTemplate.exchange(Mockito.any(URI.class), Mockito.eq(HttpMethod.POST),
				Mockito.any(HttpEntity.class), Mockito.eq(Map.class))).thenReturn(response);

		utilityController.getAddressMapper(countryCode, httpServletRequest);

	}

	private void setStaticfields() {
		ReflectionTestUtils.setField(utilityController, "configService", configService);
		ReflectionTestUtils.setField(utilityController, "addressMapperHelper", addressMapperHelper);
		ReflectionTestUtils.setField(utilityController, "configServiceV2", configServiceV2);
		ReflectionTestUtils.setField(configServiceV2, "configService", configService);

	}

}
