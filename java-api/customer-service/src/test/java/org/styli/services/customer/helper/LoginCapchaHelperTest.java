package org.styli.services.customer.helper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.ReflectionTestUtils;
import org.styli.services.customer.pojo.consul.Message;
import org.styli.services.customer.pojo.limiter.TokenBucketObject;
import org.styli.services.customer.pojo.registration.request.CustomerLoginV4Request;
import org.styli.services.customer.redis.RedisHelper;
import org.styli.services.customer.utility.consul.FromEmail;
import org.styli.services.customer.utility.consul.ServiceConfigs;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.gson.Gson;

public class LoginCapchaHelperTest extends AbstractTestNGSpringContextTests {

	@InjectMocks
	private ServiceConfigs serviceConfigs;

	@InjectMocks
	private LoginCapchaHelper loginCapchaHelper;

	@Mock
	RedisHelper redisHelper;

	private CustomerLoginV4Request customerLoginV4Request;

	private Map<String, Object> newConfigs;

	private TokenBucketObject obj;

	@BeforeMethod
	public void setup() {
		MockitoAnnotations.initMocks(this);

		Message msg = new Message();
		msg.setEn("default message");
		FromEmail dm = new FromEmail();
		dm.setMail("test mail");

		LinkedHashMap<String, String> liststr = new LinkedHashMap();
		liststr.put("en", "en name");
		liststr.put("ar", "ar name");
		dm.setName(liststr);

		Map<String, Object> mp = new HashMap<>();
		Map<String, Object> currentMap = new HashMap<>();
		currentMap.put("enabled", true);
		currentMap.put("hit", 10);
		currentMap.put("refreshPeriodsMilli", 10l);
		currentMap.put("tokenMode", "nvsv");
		mp.put("login-v4-capcha", currentMap);

		newConfigs = new LinkedHashMap<>();
		newConfigs.put("fromEmail", dm);
		newConfigs.put("capcha", mp);

		obj = new TokenBucketObject();
		obj.setToken("token");
		obj.setUpdatedAt(10l);
		obj.setCount(10);

		ReflectionTestUtils.setField(serviceConfigs, "consulServiceMap", newConfigs);
	}

	@Test
	public void getCapchaBucketObjectTest() throws Exception {
		loginCapchaHelper.init();
		loginCapchaHelper.onConfigsUpdated(newConfigs);
		String requestData = new String(
				Files.readAllBytes(Paths.get("src/test/resources/customer_login_request.json")));
		Gson g = new Gson();
		Map<String, String> requestHeader = new HashMap<>();
		customerLoginV4Request = g.fromJson(requestData, CustomerLoginV4Request.class);
		customerLoginV4Request.setUseridentifier("test identifier");
		when(redisHelper.get(any(), any(), any())).thenReturn(obj);
		TokenBucketObject tokenBucketObject = loginCapchaHelper.getCapchaBucketObject(customerLoginV4Request,
				requestHeader);
		loginCapchaHelper.destroy();
		Assert.assertNotNull(tokenBucketObject);
	}

	@Test
	public void needsReCapchaTest() throws Exception {
		ReflectionTestUtils.setField(loginCapchaHelper, "enabled", true);
		when(redisHelper.put(any(), any(), any(), any())).thenReturn(true);
		boolean value = loginCapchaHelper.needsReCapcha(obj);
		Assert.assertFalse(value);
	}

	@Test
	public void needsReCapchaPositiveTest() throws Exception {
		ReflectionTestUtils.setField(loginCapchaHelper, "enabled", true);
		when(redisHelper.put(any(), any(), any(), any())).thenReturn(true);
		obj.setUpdatedAt(null);
		boolean value = loginCapchaHelper.needsReCapcha(obj);
		Assert.assertTrue(value);
	}
}
