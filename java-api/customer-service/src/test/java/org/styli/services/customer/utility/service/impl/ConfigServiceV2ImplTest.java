package org.styli.services.customer.utility.service.impl;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.client.OrderClient;
import org.styli.services.customer.utility.pojo.config.Store;
import org.styli.services.customer.utility.pojo.config.StoreConfigResponse;
import org.styli.services.customer.utility.pojo.config.StoreConfigResponseDTO;
import org.styli.services.customer.utility.pojo.config.Stores;
import org.styli.services.customer.utility.service.ConfigService;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ConfigServiceV2ImplTest {

	@Mock
	private HttpServletRequest request;

	@Mock
	private ConfigService configService;

	@Mock
	private Constants constants;

	@Mock
	private OrderClient orderClient;

	@InjectMocks
	private ConfigServiceV2Impl configServiceV2Impl;

	@BeforeClass
	public void setUp() {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	public void testGetStoreV2Configs() throws Exception {
		ReflectionTestUtils.setField(configServiceV2Impl, "dbVersion", "testVersion");
		Stores s1 = new Stores();
		s1.setStoreId("1");
		List<Stores> lst = new ArrayList<>();
		lst.add(s1);
		StoreConfigResponse storeConfigResponse = new StoreConfigResponse();
		storeConfigResponse.setDbVersion("testVersion");
		ReflectionTestUtils.setField(constants, "StoreConfigResponse", null);

		ReflectionTestUtils.setField(constants, "storesList", lst);

		Store ss = new Store();
		ss.setStoreId(1);
		List<Store> slst = new ArrayList<>();
		slst.add(ss);
		when(orderClient.findAllStores()).thenReturn(slst);
		StoreConfigResponseDTO resp = configServiceV2Impl.getStoreV2Configs(request, true, false);
		assertEquals(resp.getStatus(), false);
		assertEquals(resp.getStatusCode(), "201");

	}
}
