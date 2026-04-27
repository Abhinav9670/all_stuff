package org.styli.services.order.pojo;

import lombok.Data;

@Data
public class RefundPaymentRespone {

	private boolean status;

	private String statusCode;

	private String statusMsg;

	private ErrorType error;

	private String requestId;

	private boolean sendSms = true;
	
	private String refundAmount;

	private String refundUrl;
	
	private boolean refund = false;
	
	private String paymentRRN;

	@Override
	public String toString() {
		return "RefundPaymentRespone [status=" + status + ", statusCode=" + statusCode + ", statusMsg=" + statusMsg
				+ ", error=" + error + ", requestId=" + requestId + ", sendSms=" + sendSms + ", refundUrl=" + refundUrl + "]";
	}
	
	
}
