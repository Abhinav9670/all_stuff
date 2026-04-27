package org.styli.services.customer.pojo.consul;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CountryEnabledDays {
	
	private boolean enable;
	private String expireInDays;

}
