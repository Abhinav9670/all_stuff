package org.styli.services.order.pojo.tamara;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class Amount {

	@JsonProperty("amount")
	private String totalAmount;
	
	@JsonProperty("currency")
	private String currency;

}
