package org.styli.services.customer.pojo.registration.response;

import lombok.Data;

@Data
public class CustomerLoginResponse {

	private boolean status;

	private String statusCode;

	private String statusMsg;

	private CustomerResponse response;

	private ErrorType error;
}
