package org.styli.services.customer.pojo.registration.response;

import lombok.Data;

@Data
public class CustomerLoginV4Response {

	private boolean status;

	private String statusCode;

	private String statusMsg;

	private CustomerV4Response response;
	
	private String responseToken;

	private ErrorType error;

	private String encryptedRsaToken;

	private String encryptedRsaTokenExpiry;
}
