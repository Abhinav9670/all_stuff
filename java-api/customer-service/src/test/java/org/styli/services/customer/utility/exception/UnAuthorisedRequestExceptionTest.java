package org.styli.services.customer.utility.exception;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class UnAuthorisedRequestExceptionTest {
	@Test
	public void testUnAuthorisedRequestException() {
		String resourceName = "Resource";
		String fieldName = "Field";
		String fieldValue = "Value";

		UnAuthorisedRequestException exception = new UnAuthorisedRequestException(resourceName, fieldName, fieldValue);

		// Assert the values are set correctly
		assertEquals(exception.getResourceName(), resourceName);
		assertEquals(exception.getFieldName(), fieldName);
		assertEquals(exception.getFieldValue(), fieldValue);

		// Assert the exception message
		String expectedMessage = String.format("%s not found with %s : '%s'", resourceName, fieldName, fieldValue);
		assertEquals(exception.getMessage(), expectedMessage);
	}

}
