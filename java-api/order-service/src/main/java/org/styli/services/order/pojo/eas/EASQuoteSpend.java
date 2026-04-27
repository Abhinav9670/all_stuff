package org.styli.services.order.pojo.eas;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class EASQuoteSpend {
	
	@JsonProperty("coins")
	private Integer coins;
	
	@JsonProperty("storeCoinValue")
	private String storeCoinValue;
	
	@JsonProperty("baseCurrencyValue")
	private String baseCurrencyValue;
	
	@JsonProperty("isCoinApplied")
	private int isCoinApplied;

	@JsonProperty("statusMinValueSatisfy")
	private String statusMinValueSatisfy;

}
