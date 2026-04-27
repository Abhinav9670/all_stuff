package org.styli.services.order.pojo.response;

import org.styli.services.order.pojo.ErrorType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerUpdateProfileResponse {

	private boolean status;

	private String statusCode;

	private String statusMsg;

	private CustomerProfileResponse response;

	private ErrorType error;
}
