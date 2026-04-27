package org.styli.services.customer.pojo.registration.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerUpdateProfileResponse {

	private boolean status;

	private String statusCode;

	private String statusMsg;

	private CustomerProfileResponse response;

	private ErrorType error;
	
	private String isSignUpOtpEnabled;

	private String isEmailOTPEnabled = "false";
}
