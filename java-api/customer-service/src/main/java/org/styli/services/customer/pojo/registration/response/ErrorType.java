package org.styli.services.customer.pojo.registration.response;

import lombok.Data;

import java.io.Serializable;

@Data
public class ErrorType implements Serializable {

	private static final long serialVersionUID = 8434919087089061467L;
	private String errorCode;
	private String errorMessage;

}
