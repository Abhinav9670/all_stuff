package org.styli.services.order.pojo.request.Order;

import lombok.Data;

@Data
public class OrderHistory {

	private String date;
	
	private String status;
	
	private Boolean customerNotified;
	
	private String message;
	
	
}
