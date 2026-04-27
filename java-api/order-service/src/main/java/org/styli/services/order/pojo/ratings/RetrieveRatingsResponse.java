package org.styli.services.order.pojo.ratings;

import org.styli.services.order.pojo.ErrorType;

import lombok.Data;

@Data
public class RetrieveRatingsResponse {
	
	private boolean status;

	private String statusCode;

	private String statusMsg;

	private RetrieveOrderRatings response;

	private ErrorType error;

}
