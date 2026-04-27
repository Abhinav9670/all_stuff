package org.styli.services.customer.utility.exception;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class NotFoundExceptionTest {
	@Test
	public void testNotFoundException() {
		String errorMessage = "Resource not found";
		int errorCode = 404;

		NotFoundException notFoundException1 = new NotFoundException(new Throwable("exp"));
		notFoundException1.setErrorCode(1);
		notFoundException1.setErrorMessage("msg");
		NotFoundException notFoundException2 = new NotFoundException("exp");
		NotFoundException notFoundException3 = new NotFoundException(errorMessage, new Throwable("exp"));
		NotFoundException notFoundException = new NotFoundException(errorMessage, errorCode);

		// Assert the values are set correctly
		assertEquals(notFoundException.getErrorMessage(), errorMessage);
		assertEquals(notFoundException.getErrorCode(), errorCode);

		// Assert the exception message
		String expectedMessage = errorMessage;
		assertEquals(notFoundException.getMessage(), expectedMessage);

		// Assert the exception toString() representation
		String expectedToString = errorCode + " : " + errorMessage;
		assertEquals(notFoundException.toString(), expectedToString);
	}

}
