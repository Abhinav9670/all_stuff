package org.styli.services.order.pojo.ratings;

import java.util.List;

import lombok.Data;

@Data
public class RetrieveCustomerRatings {
	
	private String parentSku;
	
	private String childSku;
	
	private Integer rate;
	
	private String comments;
	
	private String ratingText;
	
	private List<Integer> options;
	
	private String questionId;
	
}
