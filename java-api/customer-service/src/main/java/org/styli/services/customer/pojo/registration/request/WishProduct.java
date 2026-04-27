package org.styli.services.customer.pojo.registration.request;

import lombok.Data;

@Data
public class WishProduct {

	private String prodcutId;
	
	private String sku;
	
	private String price;
	
	private String specialPrice;

	private String parentProductId;

	private String comments;

	private Integer quantity;

	private String wishListItemId;
	
	private String source;
	
	private String utmCampaign;
}
