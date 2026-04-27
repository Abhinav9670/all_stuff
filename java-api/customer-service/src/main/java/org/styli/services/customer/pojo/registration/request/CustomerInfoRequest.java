package org.styli.services.customer.pojo.registration.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerInfoRequest {

	private Customer customer;

	private String password;

	private String socialToken;

	private String socialLoginType;

	private String isSocialLogin; /** 0 for social loginType **/
	
	private String deviceId;

}
