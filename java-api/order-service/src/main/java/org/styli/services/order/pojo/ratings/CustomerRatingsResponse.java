package org.styli.services.order.pojo.ratings;

import org.styli.services.order.pojo.eas.EarnResponse;

import lombok.Data;

@Data
public class CustomerRatingsResponse {
	
	private String orderId;
	
	private String storeId;
	
	private String parentSku;
	
	private String childSku;
	
	private String customerEmail;
	
	private ProductRatings ratings;
	
	private DeliveryRatings deliveryRatings;
	

	private EarnResponse eas;
}
