package org.styli.services.customer.utility.pojo.request;

import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Setter
public class SearchCityResponse {

	private String id;

	@JsonProperty("name_en")
	private String nameEn;

	@JsonProperty("name_ar")
	private String nameAr;

	@JsonProperty("enabled")
	private boolean enabled;

	@JsonProperty("country")
	private String country;

	@JsonProperty("province")
	private String province;

	@JsonProperty("city")
	private String city;

}
