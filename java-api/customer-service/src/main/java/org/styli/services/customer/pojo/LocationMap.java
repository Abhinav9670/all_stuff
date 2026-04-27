package org.styli.services.customer.pojo;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LocationMap {

	@JsonProperty("lat")
	private BigDecimal latitude;
	
	@JsonProperty("lng")
	private BigDecimal longitude;
}

