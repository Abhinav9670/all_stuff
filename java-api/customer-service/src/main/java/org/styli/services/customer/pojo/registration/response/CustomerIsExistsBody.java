package org.styli.services.customer.pojo.registration.response;

import lombok.Data;

@Data
public class CustomerIsExistsBody {

	private boolean isExists;

	private String email;

	private String mobileNumber;

	private String mesage;

	private Integer signedInNowUsing;

	private boolean isPasswordAvailable;
	
	private Boolean isVerifyMobileNumber;

	private Boolean isUserConsentProvided;
	
	private Boolean isMobileVerified;
		
	private Boolean isEmailVerified;
}
