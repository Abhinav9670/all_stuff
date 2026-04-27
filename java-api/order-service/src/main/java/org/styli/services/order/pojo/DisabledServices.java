package org.styli.services.order.pojo;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
public class DisabledServices implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@JsonProperty("recommendationDisabled")
	private boolean recommendationDisabled;
	
	@JsonProperty("earnDisabled")
	private boolean earnDisabled;
	
	@JsonProperty("referralDisabled")
	private boolean referralDisabled;
	
}
