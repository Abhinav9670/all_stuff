package org.styli.services.customer.pojo.registration.response;

import lombok.Data;

@Data
public class CustomerRestPassResponse {

	private boolean status;

	private String statusCode;

	private String statusMsg;

	private PasswordResetResponse response;

	private ErrorType error;

}
