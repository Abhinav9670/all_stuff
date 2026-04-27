package org.styli.services.customer.helper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.security.NoSuchAlgorithmException;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.ReflectionTestUtils;
import org.styli.services.customer.jwt.security.jwtsecurity.model.JwtUser;
import org.styli.services.customer.jwt.security.jwtsecurity.security.JwtGenerator;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class PasswordHelperTest extends AbstractTestNGSpringContextTests {

	@Mock
	private JwtGenerator jwtGenerator;

	@Spy
	@InjectMocks
	private PasswordHelper passwordHelper;

	@BeforeMethod
	public void setUp() {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	public void testGetSha256Hash() throws NoSuchAlgorithmException {
		final String password = "password";
		final String salt = "salt";
		final String expectedResult = "ebe491b28964e73215b291c1c09a30e0d75a6c95a2b8e7748b8b8c2b3503ca5b:salt:1";
		// when(RandomStringUtils.randomAlphanumeric(32)).thenReturn(salt);

		String actualResult = passwordHelper.getSha256Hash(password, null);

		Assert.assertNotNull(actualResult);
	}

	@Test
	public void testGetArgon2Id13Hash() {
		final String password = "password";
		final String salt = "saltnjdfvjh";
		// final String expectedResult =
		// "5B4F9E8FBE6F9739C6D3AF817FEE659F9E6797ED8E65B82C03C2D47B6B27D7E6:salt:2";

		String actualResult = passwordHelper.getArgon2Id13Hash(password, salt);

		Assert.assertNotNull(actualResult);
	}

	@Test
	public void testGenerateToken() {
		final String userId = "user1";
		final String code = "code1";
		final Integer customerId = 1;
		final String jwtFlag = "1";
		final String expectedResult = "jwt-token";
		final boolean refreshToken = false;
		JwtUser expectedJwtUser = new JwtUser();
		expectedJwtUser.setUserId(userId);
		expectedJwtUser.setCode(code);
		expectedJwtUser.setRole("user");
		expectedJwtUser.setCustomerId(customerId);
		expectedJwtUser.setJwtFlag(jwtFlag);
		ReflectionTestUtils.setField(passwordHelper, "jwtFlag", "1");
		when(jwtGenerator.generate(any())).thenReturn(expectedResult);

		String actualResult = passwordHelper.generateToken(userId, code, customerId,refreshToken);

		Assert.assertEquals(actualResult, expectedResult);
	}

}
