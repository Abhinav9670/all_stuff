package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Navikinfos {

	@JsonProperty("DROP_OFF_ADDRESS_SMSA")
	private String smsaDropOffAddress;
	
	@JsonProperty("WEBSITE_ID")
	private Integer webSiteId;
	
	@JsonProperty("DUTY_FEE_PAID")
	private String dutyFeePaid;
	
	@JsonProperty("DESCRIPTION_EN")
	private String descriptionEn;
	
	@JsonProperty("DESCRIPTION_AR")
	private String descriptionAr;
	
	@JsonProperty("RVP_REASON")
	private String rvpReason;
	
	@JsonProperty("VENDOR_CODE")
	private String vendorCode;
	
	@JsonProperty("PICK_DROPOFF_ADDRESS")
	private NavikAddress addressDetails;
	
	@JsonProperty("DROP_OFF_ADDRESS_ARMX")
	private String armxDropOffAddress;
}
