package org.styli.services.customer.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetOrderConsulValues {
	
	@JsonProperty("is_firebase_auth_enable")
	private boolean isFirebaseAuthEnable;

	@JsonProperty("is_internal_auth_enable")
	private boolean isInternalAuthEnable;

	@JsonProperty("is_external_auth_enable")
	private boolean isExternalAuthEnable;

}