package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class PayfortDtfQueryRequest {

	
	
	@JsonProperty("access_code")
	private String accessCode;
	
	@JsonProperty("language")
	private String language;
	
	@JsonProperty("merchant_identifier")
	private String merchantIdentifier;
	
	@JsonProperty("merchant_reference")
	private String merchantReference;
	
	@JsonProperty("query_command")
	private String command;
				
	@JsonProperty("signature")
	private String signature;	
	
}
