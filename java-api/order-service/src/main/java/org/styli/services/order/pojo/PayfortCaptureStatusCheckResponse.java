
package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class PayfortCaptureStatusCheckResponse {
	
	@JsonProperty("command")
	private String command;
	
	@JsonProperty("access_code")
	private String accessCode;
	
	@JsonProperty("merchant_identifier")
	private String merchantIdentifier;
	
	@JsonProperty("merchant_reference")
	private String merchantReference;
	
	@JsonProperty("language")
	private String language;
	
	@JsonProperty("signature")
	private String signature;
	
	@JsonProperty("fort_id")
	private String fortId;	
	
	@JsonProperty("response_message")
	private String responseMessage;

	@JsonProperty("response_code")
	private String responseCode;

	@JsonProperty("status")
	private String status;
	
	@JsonProperty("refunded_amount")
	private String refundedAmount;
	
	@JsonProperty("captured_amount")
	private String capturedAmount;
	
	@JsonProperty("authorized_amount")
	private String authorizedAmount;
	
	@JsonProperty("authorized_code")
	private String authorizedCode;
	
	@JsonProperty("transaction_status")
	private String transactionStatus;
	
	@JsonProperty("transaction_code")
	private String transactionCode;
	
	@JsonProperty("transaction_message")
	private String transactionMessage;
	
	@JsonProperty("processor_response_code")
	private String processorResponseCode;
	
	@JsonProperty("acquirer_response_code")
	private String acquirerResponseCode;
}

