package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class PayfortOrderRefundPayLoad {

	
	
	@JsonProperty("access_code")
	private String accessCode;
	
	@JsonProperty("amount")
	private String amount;
	
	@JsonProperty("command")
	private String command;
	
	@JsonProperty("currency")
	private String currency;
	
	@JsonProperty("fort_id")
	private String fortId;
	
	@JsonProperty("language")
	private String language;
	
	@JsonProperty("merchant_identifier")
	private String merchantIdentifier;
	
	@JsonProperty("merchant_reference")
	private String merchantReference;
		
	@JsonProperty("signature")
	private String signature;
	
	@JsonProperty("order_description")
	private String orderDescription;
	
}
