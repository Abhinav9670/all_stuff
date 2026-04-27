package org.styli.services.customer.exception;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String errorMessage;
	private String errorCode;

	public CustomerException(String errorCode, String errorMessage) {
		super(String.format("message %s : errorcode '%s'", errorCode, errorMessage));
		this.errorMessage = errorMessage;
		this.errorCode = errorCode;
	}

	public CustomerException(String errorMessage) {
		super(String.format("message %s : ", errorMessage));
		this.errorMessage = errorMessage;
	}
}
