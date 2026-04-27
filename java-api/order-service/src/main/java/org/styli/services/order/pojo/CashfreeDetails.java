package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CashfreeDetails {
	
	@JsonProperty("CF_BASE_URL")
	private String cashFreeBaseUrl;
	
	@JsonProperty("CF_APP_ID")
	private String cashFreeAppId;
	
	@JsonProperty("CF_SECRET")
	private String cashFreeSecret;
	
	@JsonProperty("CF_VERSION")
	private String cashFreeVersion;
	
	@JsonProperty("orders_minutes_ago")
	private String ordersMinutesAgo;
	
	@JsonProperty("orders_hours_ago")
	private String ordersHoursAgo;
	
	@JsonProperty("CASHGRAM_BASE_URL")
	private String cashgramBaseUrl;
	
	@JsonProperty("CASHGRAM_APP_ID")
	private String cashGramAppId;
	
	@JsonProperty("CASHGRAM_SECRET")
	private String cashGramSecret;
	
	@JsonProperty("CASHGRAM_EXPIRE_IN_DAYS")
	private int cashGramExpireInDays;

}
