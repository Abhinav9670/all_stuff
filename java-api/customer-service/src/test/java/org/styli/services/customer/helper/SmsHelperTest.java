package org.styli.services.customer.helper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.styli.services.customer.utility.consul.ServiceConfigs;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import okhttp3.OkHttpClient;

public class SmsHelperTest extends AbstractTestNGSpringContextTests {

	@InjectMocks
	SmsHelper smsHelper;

	@Mock
	UriComponentsBuilder uriComponentsBuilder;
	@Mock
	UriComponents uriComponents;
	@Mock
	OkHttpClient client;

	@InjectMocks
	ServiceConfigs config;

	@BeforeMethod
	public void setUp() {
		MockitoAnnotations.initMocks(this);

		Map<String, Object> newConfigs = new LinkedHashMap<>();

		newConfigs.put("kaleyraSenderId", "123");
		newConfigs.put("kaleyraOtpTemplateId", "123");
		ReflectionTestUtils.setField(config, "consulServiceMap", newConfigs);
	}

	@Test
	public void initTest() {
		smsHelper.init();
	}

	@Test
	public void sendSMSTest() {
		ReflectionTestUtils.setField(smsHelper, "env", "live");
		ReflectionTestUtils.setField(smsHelper, "kaleyraUrl", "/rest/cust/vsvs");
		when(uriComponentsBuilder.path(any())).thenReturn(uriComponentsBuilder);
		UriComponents result = UriComponentsBuilder.fromPath("foo").queryParam("bar").fragment("baz").build();
		when(uriComponentsBuilder.buildAndExpand(anyLong())).thenReturn(result);

		smsHelper.sendSMS("9898732132", "jfgbndfnb", 1, false);
	}

	@Test
	public void sendSMSINTest() {
		ReflectionTestUtils.setField(smsHelper, "env", "live");
		ReflectionTestUtils.setField(smsHelper, "kaleyraUrl", "/rest/cust/vsvs");
		when(uriComponentsBuilder.path(any())).thenReturn(uriComponentsBuilder);
		UriComponents result = UriComponentsBuilder.fromPath("foo").queryParam("bar").fragment("baz").build();
		when(uriComponentsBuilder.buildAndExpand(anyLong())).thenReturn(result);

		smsHelper.sendSMSIN("9898732132", "jfgbndfnb", 1, false);
		// verify(uriComponentsBuilder,times(1)).buildAndExpand(anyLong());
	}

}
