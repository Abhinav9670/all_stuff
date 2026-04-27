package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PendingOrderNotfcnDetails {

	@JsonProperty("ORDER_PENDING_FIRST_NOTIFICATION_IN_MINUTE")
	private Integer orderPendingFirstNototificationPending;
	
	@JsonProperty("ORDER_PENDING_SECOND_NOTIFICATION_IN_MINUTE")
	private Integer orderPendingSecondNototificationPending;
	
	@JsonProperty("ORDER_PENDING_FIRST_WHTSAPP_TEMPLATE")
	private String orderPendingFirstWhtsAppTemplate;
	
	@JsonProperty("ORDER_PENDING_SECOND_WHTSAPP_TEMPLATE")
	private String orderPendingSecopndWhtsAppTemplate;

}
