package org.styli.services.customer.service.impl;

import org.apache.commons.logging.Log;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ConfigServiceImplTest {

	@InjectMocks
	private ConfigServiceImpl configService;

//    @Spy
//    @Value("${auth.internal.header.bearer.token}")
//    private String internalAuthBearerToken;

	@Mock
	private Log LOGGER;

	@BeforeMethod
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		ReflectionTestUtils.setField(configService, "internalAuthBearerToken", "token1,dwd,1,2");
		ReflectionTestUtils.setField(configService, "externalAuthBearerToken", "token2,ds,1,1");

	}

	@Test
	public void testCheckAuthorization() {
		String intenalAuthorizationToken = "token1";
		String externalAuthorizationToken = "token2";

		boolean statusFlag = configService.checkAuthorization(intenalAuthorizationToken, externalAuthorizationToken);

		Assert.assertTrue(statusFlag);
	}

	@Test
	public void testCheckAuthorizationInternal() {
		String authorizationToken = "token1";

		boolean statusFlag = configService.checkAuthorizationInternal(authorizationToken);

		Assert.assertTrue(statusFlag);
	}

	@Test
	public void testCheckAuthorizationExternal() {
		String authorizationToken = "token2";

		boolean statusFlag = configService.checkAuthorizationExternal(authorizationToken);

		Assert.assertTrue(statusFlag);
	}

	@Test
	public void testGetFirstInternalAuthBearerToken() {

		String result = configService.getFirstInternalAuthBearerToken();

		Assert.assertEquals(result, "token1");
	}

	@Test
	public void testGetFirstExternalAuthBearerToken() {

		String result = configService.getFirstExternalAuthBearerToken();

		Assert.assertEquals(result, "token2");
	}
}
