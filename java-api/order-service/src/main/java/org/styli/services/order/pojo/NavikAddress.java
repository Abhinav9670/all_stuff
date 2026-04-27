package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NavikAddress {

	@JsonProperty("STATE")
	private String state;
	
	@JsonProperty("ADDRESS")
	private String address;
	
	@JsonProperty("COUNTRY_CODE")	
	private String countryCode;
	
	@JsonProperty("NAME")	
	private String name;
	
	@JsonProperty("PHONE")	
	private String phone;
	
	@JsonProperty("PHONE_CODE")	
	private String phoneCode;
	
	@JsonProperty("EMAIL")	
	private String email;
	
	@JsonProperty("CITY")	
	private String city;
	
	@JsonProperty("POSTAL_CODE")	
	private String posatalCode;
	
	
}
