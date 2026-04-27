package org.styli.services.order.pojo;

import lombok.Data;

@Data
public class CustomCouponRedemptionV5Request {
	
	
	private String quoteId;
	
	private String orderReferenceId;
	
	private String storeId;
	
	private String customerEmailId;

}
