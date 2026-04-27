package org.styli.services.order.pojo.ratings;

import java.util.List;

import lombok.Data;

@Data
public class RetrieveOrderRatings {
	
	private List<RetrieveProductRatings> ratings;
	
	private RetrieveDeliveryRatings deliveryRatings;

}
