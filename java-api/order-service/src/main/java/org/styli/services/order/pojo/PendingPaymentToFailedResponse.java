package org.styli.services.order.pojo;

import lombok.Data;

@Data
public class PendingPaymentToFailedResponse {

	private boolean status;

	private String statusCode;

	private String statusMsg;

	private ErrorType error;

}
