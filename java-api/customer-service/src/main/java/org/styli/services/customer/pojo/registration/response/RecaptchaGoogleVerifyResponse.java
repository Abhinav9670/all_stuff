package org.styli.services.customer.pojo.registration.response;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class RecaptchaGoogleVerifyResponse {
	
	private boolean success;
	
	@JsonProperty("challenge_ts")
	private Date timestamp;
	
	private String hostname;
	
	@JsonProperty("error-codes")
	private List<String> errorCodes;

}
