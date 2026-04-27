package org.styli.services.customer.service.impl;

import static org.mockito.Mockito.when;

import java.security.NoSuchAlgorithmException;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataAccessException;
import org.styli.services.customer.helper.PasswordHelper;
import org.styli.services.customer.pojo.registration.request.CustomerPasswordRequest;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.pojo.registration.response.CustomerRestPassResponse;
import org.styli.services.customer.pojo.registration.response.PasswordResetResponse;
import org.styli.services.customer.service.Client;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ChangePasswordTest {

	@Mock
	private Client client;

	@Mock
	private PasswordHelper passwordHelper;

	@InjectMocks
	private ChangePassword changePassword;

	@BeforeMethod
	public void initMocks() {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	public void testReset() throws NoSuchAlgorithmException, DataAccessException {
		// Arrange
		CustomerPasswordRequest request = new CustomerPasswordRequest();
		request.setCurrentPassword("old_password");
		request.setNewPassword("new_password");
		request.setCustomerId(1);

		CustomerEntity customerEntity = new CustomerEntity();
		customerEntity.setPasswordHash("hash:salt:1");

		when(client.findByEntityId(request.getCustomerId())).thenReturn(customerEntity);
		when(passwordHelper.getSha256Hash(request.getCurrentPassword(), "salt")).thenReturn("hash:salt:1");
		when(passwordHelper.getSha256Hash(request.getNewPassword(), null)).thenReturn("hash:salt:1");

		CustomerRestPassResponse expectedResponse = new CustomerRestPassResponse();
		PasswordResetResponse passwordRes = new PasswordResetResponse();
		passwordRes.setValue(true);
		expectedResponse.setStatus(true);
		expectedResponse.setStatusCode("200");
		expectedResponse.setStatusMsg("Password Changhed Successfully!!");
		expectedResponse.setResponse(passwordRes);

		// Act
		CustomerRestPassResponse response = changePassword.reset(request, client, passwordHelper);

		// Assert
		Assert.assertEquals(response.isStatus(), expectedResponse.isStatus());
		Assert.assertEquals(response.getStatusCode(), expectedResponse.getStatusCode());
		Assert.assertEquals(response.getStatusMsg(), expectedResponse.getStatusMsg());
		Assert.assertEquals(response.getResponse().isValue(), expectedResponse.getResponse().isValue());
	}

}
