package org.styli.services.customer.utility.exception;

import lombok.Data;

@Data
public class CustomerException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	String errorMessage;
	String errorCode;

	public CustomerException(String errorCode, String errorMessage) {
		// super(String.format("%s not found with %s : '%s'", errorMessage, errorCode));
		super(String.format("message %s : errorcode '%s'", errorCode, errorMessage));
		this.errorMessage = errorMessage;
		this.errorCode = errorCode;
	}

	// super(String.format("%s not found with %s : '%s'", resourceName, fieldName,
	// fieldValue));

}
