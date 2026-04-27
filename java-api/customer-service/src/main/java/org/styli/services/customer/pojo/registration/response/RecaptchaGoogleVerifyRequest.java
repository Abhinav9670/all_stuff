package org.styli.services.customer.pojo.registration.response;

import lombok.Data;

@Data
public class RecaptchaGoogleVerifyRequest {
	
	private String secret;

	private String response;

	private String remoteip;

}
