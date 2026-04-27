package org.styli.services.customer.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true) 
public class LoginCredentials {

	@JsonProperty("ios_cleint_id")
	private String iosCleintId;

	@JsonProperty("ios_web_cleint_id")
	private String iosWebCleintId;

	@JsonProperty("apple_auth_url")
	private String appleAuthUrl;
	
	@JsonProperty("apple_base_url")
	private String appleBaseUrl;

	@JsonProperty("whatsapp_signup_url")
	private String whatsappSignupUrl;
}
