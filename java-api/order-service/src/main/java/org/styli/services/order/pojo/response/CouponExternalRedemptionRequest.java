package org.styli.services.order.pojo.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class CouponExternalRedemptionRequest {
	
	@JsonProperty("tracking_id")
	private String trackingId;
	
	@JsonProperty("redeem_status")
	private Integer redemptionStatus;

}
