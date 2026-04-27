package org.styli.services.customer.utility.exception;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class BadRequestExceptionTest {
	@Test
	public void testBadRequestException() {
		String resourceName = "Resource";
		String fieldName = "Field";
		String fieldValue = "Value";

		BadRequestException badRequestException = new BadRequestException(resourceName, fieldName, fieldValue);

		// Assert the values are set correctly
		assertEquals(badRequestException.getResourceName(), resourceName);
		assertEquals(badRequestException.getFieldName(), fieldName);
		assertEquals(badRequestException.getFieldValue(), fieldValue);

		// Assert the exception message
		String expectedMessage = String.format("%s not found with %s : '%s'", resourceName, fieldName, fieldValue);
		assertEquals(badRequestException.getMessage(), expectedMessage);
	}
}
