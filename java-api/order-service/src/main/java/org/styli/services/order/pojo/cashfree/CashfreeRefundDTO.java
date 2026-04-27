package org.styli.services.order.pojo.cashfree;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class CashfreeRefundDTO {

	@JsonProperty("refund_amount")
	private String refundAmount;
	@JsonProperty("refund_id")
	private String refundId;
	@JsonProperty("refund_note")
	private String refundNote;
}
