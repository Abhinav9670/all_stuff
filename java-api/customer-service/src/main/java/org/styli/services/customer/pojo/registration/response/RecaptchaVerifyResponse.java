package org.styli.services.customer.pojo.registration.response;

import lombok.Data;

@Data
public class RecaptchaVerifyResponse {
	
	private boolean status;

	private String statusCode;

	private String statusMsg;

	private ErrorType error;

}
