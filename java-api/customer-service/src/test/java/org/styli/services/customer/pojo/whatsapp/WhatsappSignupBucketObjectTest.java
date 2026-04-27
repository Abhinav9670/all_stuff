package org.styli.services.customer.pojo.whatsapp;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import org.testng.annotations.Test;

public class WhatsappSignupBucketObjectTest {
	@Test
	public void testWhatsappSignupBucketObject() {
		WhatsappSignupBucketObject object = new WhatsappSignupBucketObject();
		object.setMobileNo("+919876543210");
		object.setFirstName("John");
		object.setLastName("Doe");
		object.setCode("123456");
		object.setOriginAt(System.currentTimeMillis());
		object.setUpdatedAt(System.currentTimeMillis());
		object.setExpiresAt(System.currentTimeMillis() + 1000 * 60 * 60 * 24);
		object.setRequestCount(0);

		assertEquals(object.getMobileNo(), "+919876543210");
		assertEquals(object.getFirstName(), "John");
		assertEquals(object.getLastName(), "Doe");
		assertEquals(object.getCode(), "123456");
		assertNotNull(object.getOriginAt());
		assertNotNull(object.getUpdatedAt());
		assertNotNull(object.getExpiresAt());
		assertEquals(object.getRequestCount(), 0);
	}

	@Test
	public void testWhatsappSignupRequest() {
		WhatsappSignupRequest request = new WhatsappSignupRequest();
		request.setMobileNumber("+919876543210");
		request.setName("John Doe");
		request.setUserContext("app");

		assertEquals(request.getMobileNumber(), "+919876543210");
		assertEquals(request.getName(), "John Doe");
		assertEquals(request.getUserContext(), "app");
	}

	@Test
	public void testWhatsappSignupResponse() {
		WhatsappSignupResponse response = new WhatsappSignupResponse();
		response.setToken("1234567890");
		response.setUrl("https://www.example.com");
		response.setMessage("Your account has been created successfully.");

		assertEquals(response.getToken(), "1234567890");
		assertEquals(response.getUrl(), "https://www.example.com");
		assertEquals(response.getMessage(), "Your account has been created successfully.");
	}
}
