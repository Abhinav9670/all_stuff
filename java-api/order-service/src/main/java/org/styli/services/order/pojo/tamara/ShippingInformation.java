package org.styli.services.order.pojo.tamara;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class ShippingInformation {
	
	@JsonProperty("shipped_at")
	private String shippedAt;
	
	@JsonProperty("shipping_company")
	private String shippingCompany;

}
