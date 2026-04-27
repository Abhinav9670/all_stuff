package org.styli.services.customer.pojo.registration.response;

import lombok.Data;

@Data
public class CustomerExistResponse {

	private boolean status;

	private String statusCode;

	private String statusMsg;

	private CustomerIsExistsBody response;

	private ErrorType error;
	
	private Boolean isVerifyMobileNumber = false;
	
	private Boolean isOtpSent;
	
	private String isSignUpOtpEnabled;

	private String isEmailOTPEnabled = "false";

	private Boolean isEmailVerificationEnabled;

	private Boolean isMagicLinkEnabled;

	private Boolean isEmailOtpEnabledV1;
	
	private Boolean isMagicLinkUpdateUserFeature;
	
	private Boolean isEmailOtpUpdateUserFeature;
	
}
