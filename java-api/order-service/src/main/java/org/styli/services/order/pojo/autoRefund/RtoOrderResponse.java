package org.styli.services.order.pojo.autoRefund;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class RtoOrderResponse {

	private Integer entityId;
	private String incrementId;
	private String customerEmail;
	private String method;
	private BigDecimal grandTotal;
	private String status;

}
