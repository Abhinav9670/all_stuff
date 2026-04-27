package org.styli.services.customer.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AddressValidatetionMessage {

	@JsonProperty("area_validation_en")
	private String areaValidateMsgEn;

	@JsonProperty("area_validation_ar")
	private String areaValidateMsgAr;
	
	@JsonProperty("city-validation_en")
	private String cityValidateMsgEn;

	@JsonProperty("city-validation_er")
	private String cityValidateMsgAr;
	
	@JsonProperty("region-validation_en")
	private String regionValidateMsgEn;

	@JsonProperty("region-validation_er")
	private String regionValidateMsgAr;
	
	
}
