package org.styli.services.customer.jwt.security.jwtsecurity.config;

import static org.testng.Assert.assertNotNull;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfigurationSource;
import org.styli.services.customer.jwt.security.jwtsecurity.security.JwtAuthenticationEntryPoint;
import org.styli.services.customer.jwt.security.jwtsecurity.security.JwtAuthenticationProvider;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class JwtSecurityConfigTest {

	@Mock
	private JwtAuthenticationProvider authenticationProvider;

	@Mock
	private JwtAuthenticationEntryPoint entryPoint;

	@Mock
	private CorsConfigurationSource corsConfigurationSource;

	@Mock
	private AuthenticationManager authenticationManager;

	@Mock
	private HttpServletRequest httpServletRequest;

	@Mock
	private HttpServletResponse httpServletResponse;

	@InjectMocks
	private JwtSecurityConfig jwtSecurityConfig;

	@BeforeMethod
	public void setup() {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	public void testCorsConfigurationSourceBean() {
		ReflectionTestUtils.setField(jwtSecurityConfig, "env", "dev");
		ReflectionTestUtils.setField(jwtSecurityConfig, "corsMethods", "all");
		ReflectionTestUtils.setField(jwtSecurityConfig, "corsAllowedHeader", "all");
		CorsConfigurationSource respo = jwtSecurityConfig.corsConfigurationSource();
		assertNotNull(respo);
	}
}
