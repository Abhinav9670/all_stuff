package org.styli.services.order.pojo.ratings;

import java.util.List;

import lombok.Data;

@Data
public class RetrieveRatingsRequest {
	
	private String orderId;
	
	private String customerId;
	
	private List<RetrieveCustomerRatings> products;

}
