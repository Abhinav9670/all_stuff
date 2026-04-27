package org.styli.services.customer.pojo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerVerificationStatusResponse {
	
	private boolean status;
	private String statusCode;
    private Boolean isMobileVerified;
    private Boolean isEmailVerified;
}

