package org.styli.services.order.exception;

import lombok.ToString;

@ToString
public class CustomerException extends Exception {

	private static final long serialVersionUID = 1L;

	private final String errorMessage;
	private final String errorCode;

	public CustomerException(String errorCode, String errorMessage) {
		super(String.format("message %s : errorcode '%s'", errorCode, errorMessage));
		this.errorMessage = errorMessage;
		this.errorCode = errorCode;
	}
}
