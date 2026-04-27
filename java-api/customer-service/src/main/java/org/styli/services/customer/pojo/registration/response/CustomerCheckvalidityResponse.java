package org.styli.services.customer.pojo.registration.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerCheckvalidityResponse {

	private boolean status;

	private String statusCode;

	private String statusMsg;

	private CustomerValidityResponseBody response;

	private ErrorType error;
}
