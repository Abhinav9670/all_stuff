package org.styli.services.order.pojo.customeAddressResponse;

import lombok.Data;
import org.styli.services.order.pojo.ErrorType;

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
