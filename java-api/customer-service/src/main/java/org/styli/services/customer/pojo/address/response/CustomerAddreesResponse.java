package org.styli.services.customer.pojo.address.response;

import org.styli.services.customer.pojo.registration.response.ErrorType;

import lombok.Data;

import java.io.Serializable;

@Data
public class CustomerAddreesResponse implements Serializable {

	private boolean status;

	private String statusCode;

	private String statusMsg;

	private CustomerAddressBody response;

	private ErrorType error;
		
	private String isSignUpOtpEnabled;
}

