package org.styli.services.order.pojo.tabby;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class TabbyBuyerHistory {
	
	 @JsonProperty("registered_since")
	 private String registeredSince;
	 
	 @JsonProperty("loyalty_level")
	 private Integer loyaltyLevel;
	 
	 @JsonProperty("wishlist_count")
	 private Integer wishlistCount;
	 
	 private boolean is_social_networks_connected;
	 
	 private boolean is_phone_number_verified;
	 
	 private boolean is_email_verified;

}
