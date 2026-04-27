package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class PayfortPaymentCaptureRequest {
	
	@JsonProperty("command")
	private String command;
	
	@JsonProperty("access_code")
	private String accessCode;
	
	@JsonProperty("merchant_identifier")
	private String merchantIdentifier;
	
	@JsonProperty("merchant_reference")
	private String merchantReference;
	
	@JsonProperty("amount")
	private String amount;
	
	@JsonProperty("currency")
	private String currency;
	
	@JsonProperty("language")
	private String language;
	
	@JsonProperty("signature")
	private String signature;
	
	@JsonProperty("fort_id")
	private String fortId;
	
	@JsonProperty("order_description")
	private String orderDescription;

}
