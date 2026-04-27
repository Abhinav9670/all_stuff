package org.styli.services.customer.pojo.registration.response;

import lombok.Data;

@Data
public class MobileNumberUpdateAcknowledgmentResponse {

	private Boolean status;
	private String statusCode;
	private String statusMsg;
	private Boolean acknowledged;

	public MobileNumberUpdateAcknowledgmentResponse() {
		this.status = false;
		this.statusCode = "500";
		this.statusMsg = "Internal Server Error";
	}

	public MobileNumberUpdateAcknowledgmentResponse(Boolean status, String statusCode, String statusMsg, Boolean acknowledged) {
		this.status = status;
		this.statusCode = statusCode;
		this.statusMsg = statusMsg;
		this.acknowledged = acknowledged;
	}
}