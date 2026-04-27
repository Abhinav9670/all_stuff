package org.styli.services.order.pojo.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class NavikErrormessage {

	@JsonProperty("error_message")
	private String errorMessage;
	
	@JsonProperty("account_name")
	private String accountName;
}
