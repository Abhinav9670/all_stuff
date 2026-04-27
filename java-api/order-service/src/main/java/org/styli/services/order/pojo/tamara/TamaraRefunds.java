package org.styli.services.order.pojo.tamara;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class TamaraRefunds {
	
	@JsonProperty("refund_id")
	private String refundId;
	
	@JsonProperty("capture_id")
	private String captureId;
	
	@JsonProperty("total_amount")
	private Amount amount;
	
	@JsonProperty("created_at")
    private String createdAt;
	
	private String comment;
	

}
