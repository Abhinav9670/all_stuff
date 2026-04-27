package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TamaraDetails {

	@JsonProperty("base_url")
	private String baseUrl;
	
	@JsonProperty("orders_minutes_ago")
	private String ordersMinutesAgo;
	
	@JsonProperty("orders_hours_ago")
	private String ordersHoursAgo;
	
	@JsonProperty("webhook_notification_token")
	private String webhookNotificationToken;
	
}
