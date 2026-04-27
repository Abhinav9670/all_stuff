package org.styli.services.order.pojo.zatca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZatcaErrorList {

	@JsonProperty("ErrorCode")
	private String errorCode;
	
	@JsonProperty("ErrorMessage")
	private String errorMessage;
	
	@JsonProperty("ErrorSource")
	private String errorSource;
	
	@JsonProperty("Path")
	private String path;
}
