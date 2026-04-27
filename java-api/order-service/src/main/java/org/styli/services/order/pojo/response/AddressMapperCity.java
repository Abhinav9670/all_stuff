package org.styli.services.order.pojo.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class AddressMapperCity {

	private String id;
	
	@JsonProperty("name_en")
	private String nameEn;
	
	@JsonProperty("name_ar")
	private String nameAr;
	
	private Boolean enabaled;
	
	@JsonProperty("min_sla")
	private String minSla;
	
	@JsonProperty("max_sla")
	private String maxSla;
	
	@JsonProperty("cod_verification")
	private Boolean codeVerification;
	
	private Integer threshold;
	
	private String type;
	
	@JsonProperty("estimated_date")
	private String estimatedDate;
	
	@JsonProperty("threshold_time")
	private String fstdlvrythrsTime;
	
	@JsonProperty("fast_delivery")
	private Boolean fastDeliver;
	
	@JsonProperty("fast_delivery_eligible")
	private boolean fastDeliveryEligible;

	@JsonProperty("cut_off_time")
	private String cutOffTime;
	
	@JsonProperty("express_cut_off_time")
	private String expressCutOffTime;
	
}
