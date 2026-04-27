package org.styli.services.customer.pojo.registration.response;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class CustomerV4RegistrationResponse {

	private boolean status;

	private String statusCode;

	private String statusMsg;

	private CustomerV4RegistrationResponseBody response;

	private ErrorType error;
	
	private String isSignUpOtpEnabled;
	private String isEmailOTPEnabled = "false";
	
	private String encryptedRsaToken;

	private String encryptedRsaTokenExpiry;
 
}
