package org.styli.services.customer.utility.exception;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class CustomerExceptionTest {
	@Test
	public void testCustomerException() {
		String errorCode = "123";
		String errorMessage = "Something went wrong";

		CustomerException customerException = new CustomerException(errorCode, errorMessage);

		// Assert the values are set correctly
		assertEquals(customerException.getErrorCode(), errorCode);
		assertEquals(customerException.getErrorMessage(), errorMessage);

		// Assert the exception message
		assertEquals(customerException.getMessage(), "message " + errorCode + " : errorcode '" + errorMessage + "'");
	}

}
