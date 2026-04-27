package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class PayfortQueryResponse {

	@JsonProperty("transaction_code")
	private String transactionCode;
	
	@JsonProperty("transaction_status")
	private String transactionStatus;
	
	@JsonProperty("query_command")
	private String queryCommand;	
	
	@JsonProperty("response_code")
	private String responseCode;	
	
	@JsonProperty("signature")
	private String signature;
	
	@JsonProperty("merchant_identifier")
	private String merchantIdentifier;
	
	@JsonProperty("access_code")
	private String accessCode;
	
	@JsonProperty("transaction_message")
	private String transactionMessage;
	
	@JsonProperty("merchant_reference")
	private String merchantReference;
	
	@JsonProperty("captured_amount")
	private String capturedAmount;
	
	@JsonProperty("authorized_amount")
	private String authorizedAmount;
	
	@JsonProperty("refunded_amount")
	private String refundedAmount;
	
	@JsonProperty("currency")
	private String currency;
	
	@JsonProperty("language")
	private String language;

	@JsonProperty("fort_id")
	private String fortId;	
	
	@JsonProperty("response_message")
	private String responseMessage;
	
	@JsonProperty("status")
	private String status;
	
	@JsonProperty("reconciliation_reference")
	private String reconciliationReference;	
	
	
}
