package org.styli.services.order.pojo.request.Order;

import lombok.Data;

@Data
public class OrderStoreCredit {

	private String orderId;

	private String date;

	private String amount;

	private String balance;

	private Boolean isCredit;
	
	private String actionData;
	
	private String comment;
	
}
