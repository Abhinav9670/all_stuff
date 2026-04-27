package org.styli.services.customer.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.ReflectionTestUtils;
import org.styli.services.customer.pojo.GenericApiResponse;
import org.styli.services.customer.pojo.LoginCredentials;
import org.styli.services.customer.pojo.whatsapp.WhatsappSignupBucketObject;
import org.styli.services.customer.pojo.whatsapp.WhatsappSignupRequest;
import org.styli.services.customer.pojo.whatsapp.WhatsappSignupResponse;
import org.styli.services.customer.redis.RedisHelper;
import org.styli.services.customer.service.ConfigService;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.pojo.config.Stores;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class WhatsappServiceImplTest extends AbstractTestNGSpringContextTests {

	@Mock
	private ConfigService configService;
	@Mock
	Cipher cipher;

	@Mock
	private RedisHelper redisHelper;

	@InjectMocks
	private WhatsappServiceImpl whatsappService;

	Map<String, String> requestHeader;

	private List<Stores> storeList;
	@InjectMocks
	private Constants constants;

	@BeforeMethod
	public void setup() {
		MockitoAnnotations.initMocks(this);
		requestHeader = new HashMap<>();
		requestHeader.put("x-source", "android");
		requestHeader.put("x-client-version", "11");
		requestHeader.put("token", "token");
		requestHeader.put("X-Header-Token", "test@mail.com");
		try {
			String storeData = new String(Files.readAllBytes(Paths.get("src/test/resources/stores_list.json")));
			Type listType = new TypeToken<ArrayList<Stores>>() {
			}.getType();
			storeList = new Gson().fromJson(storeData, listType);

		} catch (Exception e) {
		}
	}

	@Test
	public void testCreateWhatsappSignupLink() {
		// Prepare test data
		LoginCredentials loginCredentials = new LoginCredentials();
		loginCredentials.setWhatsappSignupUrl("url");
		ReflectionTestUtils.setField(constants, "storesList", storeList);
		ReflectionTestUtils.setField(constants, "loginCredentials", loginCredentials);
		WhatsappSignupRequest requestBody = new WhatsappSignupRequest();
		requestBody.setMobileNumber("+918792189873");
		requestBody.setName("John Doe");

//        WhatsappSignupBucketObject obj= new WhatsappSignupBucketObject();
//        obj.setExpiresAt(Instant.now().toEpochMilli()+600000);

		// Mock dependencies
		when(configService.getFirstExternalAuthBearerToken()).thenReturn("token");
		when(redisHelper.put(anyString(), anyString(), any(), any())).thenReturn(true);
		when(redisHelper.get(anyString(), anyString(), Mockito.eq(WhatsappSignupBucketObject.class))).thenReturn(null);

		// Invoke the method
		GenericApiResponse<WhatsappSignupResponse> response = whatsappService.createWhatsappSignupLink(requestBody,
				new HashMap<>());

		assertEquals(response.getStatus(), true);
		assertEquals(response.getResponse().getToken().isEmpty(), false);

		// Verify interactions with dependencies
		verify(redisHelper, times(1)).put(anyString(), anyString(), any(), any());
		verify(redisHelper, times(1)).get(anyString(), anyString(), eq(WhatsappSignupBucketObject.class));
		verify(configService, times(1)).getFirstExternalAuthBearerToken();
	}

}
