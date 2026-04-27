package org.styli.services.order.pojo.response.Order;

import lombok.Data;

@Data
public class OrderDetails {

	private String status;
	
	private String incrementId;
	
	private String createdAt;
}
