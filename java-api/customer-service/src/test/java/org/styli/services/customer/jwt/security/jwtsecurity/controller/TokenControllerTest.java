package org.styli.services.customer.jwt.security.jwtsecurity.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import java.util.LinkedHashMap;
import java.util.Map;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.styli.services.customer.jwt.security.jwtsecurity.model.JwtTokenResponse;
import org.styli.services.customer.jwt.security.jwtsecurity.model.JwtUserInfo;
import org.styli.services.customer.jwt.security.jwtsecurity.security.JwtGenerator;
import org.styli.services.customer.model.GuestSessions;
import org.styli.services.customer.repository.Customer.GuestSessionsRepository;
import org.styli.services.customer.service.CustomerV4Service;
import org.styli.services.customer.service.impl.ConfigServiceImpl;
import org.styli.services.customer.service.impl.GuestServiceImpl;
import org.styli.services.customer.utility.consul.ServiceConfigs;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.gson.Gson;

@WebAppConfiguration
public class TokenControllerTest extends AbstractTestNGSpringContextTests {

	@InjectMocks
	TokenController tokenController;
	@InjectMocks
	GuestServiceImpl guestService;

	@InjectMocks
	ConfigServiceImpl configService;

	@Mock
	JwtGenerator jwtGenerator;

	@Mock
	CustomerV4Service customerV4Service;

	@InjectMocks
	ServiceConfigs config;

	private MockMvc mockMvc;
	@Mock
	GuestSessionsRepository guestSessionsRepository;

	@BeforeClass
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		mockMvc = MockMvcBuilders.standaloneSetup(tokenController).build();
	}

	@Test
	public void generateGuestTokenTest() throws Exception {
		JwtTokenResponse jwtTokenResponse = new JwtTokenResponse();
		jwtTokenResponse.setStatus(true);
		jwtTokenResponse.setMessage("SUCCESS");
		jwtTokenResponse.setJwtToken("dummy_jwt_token");
		jwtTokenResponse.setGuestId(null);

		Map<String, Object> newConfigs = new LinkedHashMap<>();
		Map<String, Object> map = new LinkedHashMap<>();
		Map<String, String> lang = new LinkedHashMap<>();
		lang.put("en", "default message");
		map.put("DEFAULTMSG", lang);
		newConfigs.put("guest_sessions_logging", "true");
		ReflectionTestUtils.setField(config, "consulServiceMap", newConfigs);
		ReflectionTestUtils.setField(tokenController, "secretCode", "abc");
		ReflectionTestUtils.setField(tokenController, "guestService", guestService);

		JwtUserInfo jwtUserInfo = new JwtUserInfo();
		jwtUserInfo.setEmail("test@example.com");
		jwtUserInfo.setStoreId(1);
		GuestSessions guestSessions = new GuestSessions();
		// (configService.checkAuthorizationInternal(null)).thenReturn(true);
		when(guestSessionsRepository.findByDeviceId(any())).thenReturn(guestSessions);
		when(guestSessionsRepository.saveAndFlush(any())).thenReturn(guestSessions);
		when(jwtGenerator.generate(any())).thenReturn("dummy_jwt_token");

		mockMvc.perform(MockMvcRequestBuilders.post("/rest/customer/token/create")
				.content(convertObjectToJson(jwtUserInfo)).header("device-id", "1321312").header("x-source", "nvknsd")
				.header("x-client-version", "njkdfv").contentType(MediaType.APPLICATION_JSON_VALUE))
				.andExpect(MockMvcResultMatchers.status().isOk())
				.andExpect(MockMvcResultMatchers.content().json(convertObjectToJson(jwtTokenResponse))).andDo(print());
	}

	private String convertObjectToJson(Object obj) {
		String result = new Gson().toJson(obj);
		return result;
	}

	@Test
	public void generateCustomerTokenUnauthorizedTest() throws Exception {

		Map<String, Object> newConfigs = new LinkedHashMap<>();
		Map<String, Object> map = new LinkedHashMap<>();
		Map<String, String> lang = new LinkedHashMap<>();
		lang.put("en", "default message");
		map.put("DEFAULTMSG", lang);
		newConfigs.put("guest_sessions_logging", "true");
		ReflectionTestUtils.setField(config, "consulServiceMap", newConfigs);
		ReflectionTestUtils.setField(tokenController, "secretCode", "abc");
		ReflectionTestUtils.setField(configService, "internalAuthBearerToken", "csdf,jkdfv,1,1");
		ReflectionTestUtils.setField(tokenController, "guestService", guestService);
		ReflectionTestUtils.setField(tokenController, "configService", configService);
		JwtUserInfo jwtUserInfo = new JwtUserInfo();
		jwtUserInfo.setEmail("test@example.com");
		jwtUserInfo.setStoreId(1);
		GuestSessions guestSessions = new GuestSessions();
		when(guestSessionsRepository.findByDeviceId(any())).thenReturn(guestSessions);
		when(guestSessionsRepository.saveAndFlush(any())).thenReturn(guestSessions);
		when(jwtGenerator.generate(any())).thenReturn("dummy_jwt_token");

//        when(configService.checkAuthorizationInternal(any())).thenReturn(true);

		mockMvc.perform(
				MockMvcRequestBuilders.post("/rest/customer/v2/token/create").content(convertObjectToJson(jwtUserInfo))
						.header("authorization-token", "csdf").header("device-id", "1321312")
						.header("device-id", "1321312").contentType(MediaType.APPLICATION_JSON_VALUE))
				.andExpect(MockMvcResultMatchers.status().isOk()).andDo(print());
	}

}
