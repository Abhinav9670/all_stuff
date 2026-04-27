package org.styli.services.customer.pojo.registration.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerV4Registration {

	private CustomerInfoRequest customerInfo;
	
	private String isSignUpOtpEnabled;
	
	private String clientVersion;
	
	private String source;
}
