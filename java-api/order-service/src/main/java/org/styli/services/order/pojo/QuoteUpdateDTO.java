package org.styli.services.order.pojo;

import lombok.Data;

@Data
public class QuoteUpdateDTO {

	private Boolean status;
	private String statusCode;
	private String statusMsg;
	private String quoteId;
	private Integer customerId;
	private ErrorType error;

}
