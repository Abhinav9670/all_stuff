package org.styli.services.order.pojo;

import java.util.List;

import lombok.Data;

@Data
public class CreateRetryPaymentReplicaDTO {

	private Boolean status;
	private String statusCode;
	private String statusMsg;
	private String quoteId;
	private Integer customerId;
	private List<String> triedPaymentMethods;
	private Integer triedPaymentCount;
	private Integer retryPaymentThreshold;
	private ErrorType error;

}
