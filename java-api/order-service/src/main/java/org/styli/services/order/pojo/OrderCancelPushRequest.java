package org.styli.services.order.pojo;

import java.util.List;

import lombok.Data;

@Data
public class OrderCancelPushRequest {

	
	private String locationCode;
	
	private List<OrderPushItem> orderItems;
	
	private String backOrderId;
		
	
	
	
		
	

}
