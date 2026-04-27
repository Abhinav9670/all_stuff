package org.styli.services.customer.pojo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleMapsGeocodingResponse {

	private List<GoogleResults> results;
	
}
