package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class CityMapper {

	@JsonProperty("id")
	private String id;
	
	@JsonProperty("name_en")
	private String nameEn;
	
	@JsonProperty("name_ar")
	private String nameAr;
	
	@JsonProperty("enabled")
	private Boolean enaanled;
	
	@JsonProperty("min_sla")
	private String minSla;
	
	@JsonProperty("max_sla")
	private String maxSla;
	
	@JsonProperty("cod_verification")
	private Boolean codVerification;
	
	@JsonProperty("threshold")
	private Integer threshold;
	
	@JsonProperty("type")
	private String type;
	
	@JsonProperty("area")
	private String area;
	
	@JsonProperty("type_1")
	private String cityType;
	
	@JsonProperty("threshold_time")
	private String fstdlvrythrsTime;
	
	
	@JsonProperty("fast_delivery")
	private Boolean fastDeliver;

	@JsonProperty("cut_off_time")
	private String cutOffTime;
	
	@JsonProperty("estimated_date")
	private String esimatedDate;
	
	
}
