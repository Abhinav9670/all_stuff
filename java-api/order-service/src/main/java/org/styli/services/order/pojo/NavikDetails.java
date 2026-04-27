package org.styli.services.order.pojo;

import java.util.List;
import java.util.Map;

import org.styli.services.order.pojo.request.GetShipmentV3.QcChecks;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NavikDetails {

	@JsonProperty("RETURN_AWB_CREATE_CLUBING_HOURS")
	private Integer returnAwbCreateClubbingHrs;

	@JsonProperty("RETURN_AWB_CREATE_LIMIT")
	private Integer returnAwbCreateLimit;

	@JsonProperty("RETURN_AWB_CREATE_QUERY_CLUBING_HOURS")
	private Integer returnAwbCreateQueryClubinghrs;

	@JsonProperty("RETURN_AWB_CREATE_CLUBING_START_DATE")
	private String returnAwbCreateClubingStartDate;

	@JsonProperty("LOGISTIC_PRODUCT_DESCRIPTION_EN")
	private Map<String,String> logisticProductDescriptionEn;

	@JsonProperty("LOGISTIC_PRODUCT_DESCRIPTION_AR")
	private Map<String,String> logisticProductDescriptionAr;

	@JsonProperty("LOGISTIC_PRODUCT_CATEGORY")
	private String logisticProductCategory;

	@JsonProperty("IS_DGG_FEATURE_ENABLED")
	private Boolean isDggFeatureEnabled;

	@JsonProperty("EXCLUDED_DGG_STORE_IDS")
	private List<Integer> excludedDggStoreIds;

	@JsonProperty("dropoff_details")
	private List<Navikinfos> dropOffDetails;
	
	@JsonProperty("USE_ALPHA")
	private boolean alphaEnabled;
	
	@JsonProperty("ALPHA_USERNAME")
	private String alphaUsername;
	
	@JsonProperty("ALPHA_PASSWORD")
	private String alphaPassword;
	
	@JsonProperty("BETA_USERNAME")
	private String betaUsername;
	
	@JsonProperty("BETA_PASSWORD")
	private String betaPassword;
	
	@JsonProperty("APPAREL_PICKUP_INFO_NAME")
	private String apparelPickupInfoName;

	@JsonProperty("STYLI_PICKUP_INFO_NAME")
	private String styliPickupInfoName;

	@JsonProperty("STYLI_RETURN_INFO_NAME")
	private String styliReturnInfoName;
	
	@JsonProperty("SMSA_DROP_OFF")
	private String smsaDropOff;
	
	@JsonProperty("ARAMEX_DROP_OFF")
	private String aramexDropOff;
	
	@JsonProperty("qc_enabled")
	private boolean qcEnabled;
	
	@JsonProperty("qc_checks")
	private List<QcChecks> qcChecks;
	
	@JsonProperty("deliver_info")
	private DeliverInfo deliverInfo;
	
	

}
