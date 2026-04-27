package org.styli.services.order.pojo.ratings;

import java.util.List;

import lombok.Data;

@Data
public class CustomerRatings {
	
	private String orderId;
	
	private String customerId;
	
	private String customerEmail;
	
	private String customerName;
	
	private String storeId;
	
	private boolean coinEnabled = false;
	
	private List<ProductRatings> ratings;
	
	private DeliveryRatings deliveryRatings;
	
}
