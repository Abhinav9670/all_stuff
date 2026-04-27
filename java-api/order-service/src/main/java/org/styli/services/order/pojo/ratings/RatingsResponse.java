package org.styli.services.order.pojo.ratings;

import org.styli.services.order.pojo.ErrorType;

import lombok.Data;

@Data
public class RatingsResponse {
	
	private boolean status;

	private String statusCode;

	private String statusMsg;

	private CustomerRatingsResponse response;

	private ErrorType error;

}
