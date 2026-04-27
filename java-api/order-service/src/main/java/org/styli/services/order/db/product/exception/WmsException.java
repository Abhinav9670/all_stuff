package org.styli.services.order.db.product.exception;

import lombok.ToString;

@ToString
public class WmsException extends RuntimeException {

	private static final long serialVersionUID = -7806029002430564887L;

	private final String errorMessage;

	public WmsException(String message) {
		this.errorMessage = message;
	}

}