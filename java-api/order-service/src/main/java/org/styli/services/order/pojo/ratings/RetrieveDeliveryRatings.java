package org.styli.services.order.pojo.ratings;

import java.util.List;

import lombok.Data;

@Data
public class RetrieveDeliveryRatings {
	
	private String orderId;
	
	private String customerEmail;
	
	private String rate;
	
	private String comments;
	
	private List<String> options;
	
	private String questionId;
	
	private DeliveryQues questions;

}
