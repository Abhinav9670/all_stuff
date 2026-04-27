package org.styli.services.customer.pojo.registration.request;

import java.util.Date;
import lombok.Data;

@Data
public class CustomerUpdateProfileRequest {

	private Integer customerId;

	private String lastName;

	private String firstName;

	private String mobileNumber;

	private String email;

	private Integer gender;

	private Integer ageGroupId;
	
	private Boolean isReferral = false;

	private Boolean omsRequest = false;

	private Boolean whatsAppoptn = false;

	private Boolean customerBlocked = false;
	
	private Date dob;
	
	private Boolean isMobileVerified;

	private Boolean isEmailVerified;
	
	private String isSignUpOtpEnabled ;
	
	private String clientVersion;
	
	private String source;
	
	private Integer storeId;

	private Boolean isMobileNumberChanged;
}
