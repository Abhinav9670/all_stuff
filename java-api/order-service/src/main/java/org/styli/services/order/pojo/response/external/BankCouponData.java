package org.styli.services.order.pojo.response.external;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class BankCouponData implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2993687181070483020L;


	@JsonProperty("_id")
	private String id;

	@JsonProperty("coupon_name")
	private String couponName;

	@JsonProperty("description")
	private String description;

	@JsonProperty("terms")
	private String terms;

	@JsonProperty("terms_cond")
	private List<String> termsCond;

	@JsonProperty("coupon_type")
	private Integer couponType;

	@JsonProperty("card_bin")
	private List<Integer> cardbinList;
	
	@JsonProperty("coupon_image")
	private String ImageUrl;
	
	@JsonProperty("priority")
	private Integer priority;
	
	@JsonProperty("offer_message")
	private String offerMessage;

}
