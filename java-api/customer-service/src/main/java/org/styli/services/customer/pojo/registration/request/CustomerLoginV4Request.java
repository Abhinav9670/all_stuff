package org.styli.services.customer.pojo.registration.request;

import lombok.Data;

@Data
public class CustomerLoginV4Request {

	private String useridentifier;

	private String password;

	private LoginType loginType;
	
	private String fullName;

	private String otpNumber;

	private Integer ageGroupId;

	private Integer gender;
	
	private Integer websiteId;
	
	private Integer storeId;
	
	private String phoneNumber;
	
	private SocialLoginDetails socialLoginDetails;
	
	private String deviceId;

	private Boolean isUserConsentProvided;
	
	private Boolean isOtpRequired;
	
	private Boolean isOtpVerified;

	
}
