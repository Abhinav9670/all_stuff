package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class PayfortVoidAuthorizationRequest {
	
	@JsonProperty("access_code")
	private String accessCode;
	
	@JsonProperty("command")
	private String command;
	
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
	
	@JsonProperty("order_description")
	private String orderDescription;

}
