package org.styli.services.customer.pojo.registration.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerRegistrationResponse {

	private boolean status;

	private String statusCode;

	private String statusMsg;

	private CustomerResponse response;

	private ErrorType error;
}
