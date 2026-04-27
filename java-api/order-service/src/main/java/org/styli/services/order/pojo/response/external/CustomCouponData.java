package org.styli.services.order.pojo.response.external;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class CustomCouponData implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7938165565605394688L;

	@JsonProperty("_id")
	private String id;

	@JsonProperty("coupon_name")
	private String couponName;

	@JsonProperty("coupon_code")
	private String couponCode;

	@JsonProperty("description")
	private String description;

	@JsonProperty("terms")
	private String terms;

	@JsonProperty("terms_cond")
	private List<String> termsCond;

	@JsonProperty("couponType")
	private Integer couponType;

}
