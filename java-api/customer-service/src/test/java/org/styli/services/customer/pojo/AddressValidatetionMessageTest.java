package org.styli.services.customer.pojo;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class AddressValidatetionMessageTest {
	@Test
	public void testAddressValidatetionMessage() {
		AddressValidatetionMessage message = new AddressValidatetionMessage();
		message.setAreaValidateMsgEn("This is an English message");
		message.setAreaValidateMsgAr("This is an Arabic message");
		message.setCityValidateMsgEn("This is an English message");
		message.setCityValidateMsgAr("This is an Arabic message");
		message.setRegionValidateMsgEn("This is an English message");
		message.setRegionValidateMsgAr("This is an Arabic message");

		assertEquals(message.getAreaValidateMsgEn(), "This is an English message");
		assertEquals(message.getAreaValidateMsgAr(), "This is an Arabic message");
		assertEquals(message.getCityValidateMsgEn(), "This is an English message");
		assertEquals(message.getCityValidateMsgAr(), "This is an Arabic message");
		assertEquals(message.getRegionValidateMsgEn(), "This is an English message");
		assertEquals(message.getRegionValidateMsgAr(), "This is an Arabic message");
	}
}
