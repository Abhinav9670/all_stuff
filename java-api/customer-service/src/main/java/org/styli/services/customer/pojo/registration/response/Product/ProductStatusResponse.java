package org.styli.services.customer.pojo.registration.response.Product;

import java.io.Serializable;

import org.styli.services.customer.pojo.registration.response.ErrorType;

import lombok.Data;

@Data
public class ProductStatusResponse implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5673511080494469556L;

	private boolean status;

	private String statusCode;

	private String statusMsg;

	private ProductStatusResBody response;

	private ErrorType error;

}
