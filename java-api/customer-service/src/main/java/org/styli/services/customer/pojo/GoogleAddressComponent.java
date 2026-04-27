package org.styli.services.customer.pojo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleAddressComponent {

	@JsonProperty("long_name")
	private String longName;
	
	@JsonProperty("short_name")
	private String shortName;
	
	@JsonProperty("types")
	private List<String> types;
}
