package org.styli.services.order.pojo.braze;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class BrazePendingPaymentEvent {

	@JsonProperty("external_id")
	private String externalId;

	private String name;
	private String time;
	private BrazePendingPaymentPush properties;
}
