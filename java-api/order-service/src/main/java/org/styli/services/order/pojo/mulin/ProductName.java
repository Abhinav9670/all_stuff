package org.styli.services.order.pojo.mulin;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class ProductName {

	@JsonProperty("en")
	private String english;
	 
	@JsonProperty("ar")
	private String arabic;
}
