package org.styli.services.customer.pojo.eas;

import org.styli.services.customer.pojo.registration.response.ErrorType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EarnUpdateProfileResponse {

	private boolean status;

	private String statusCode;

	private String statusMsg;

	private EarnCustomerProfileResponse response;

	private ErrorType error;
}


