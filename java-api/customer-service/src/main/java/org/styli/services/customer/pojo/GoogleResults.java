package org.styli.services.customer.pojo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleResults {

	@JsonProperty("address_components")
	private List<GoogleAddressComponent> addressComponents;
	
	@JsonProperty("place_id")
	private String placeId;
	
	private List<String> types;
	
	@JsonProperty("formatted_address")
	private String formattedAddress;
	
	private GeometryMap geometry;
	
}

