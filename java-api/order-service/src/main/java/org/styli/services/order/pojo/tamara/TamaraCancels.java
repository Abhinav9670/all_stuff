package org.styli.services.order.pojo.tamara;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class TamaraCancels {
	
	@JsonProperty("cancel_id")
	private String cancelId;
	
	@JsonProperty("order_id")
	private String orderId;
	
	private Amount amount;
	
	@JsonProperty("created_at")
    private String createdAt;

}
