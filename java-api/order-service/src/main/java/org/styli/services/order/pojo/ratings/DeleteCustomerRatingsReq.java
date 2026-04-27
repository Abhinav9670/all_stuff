package org.styli.services.order.pojo.ratings;

import lombok.Data;

@Data
public class DeleteCustomerRatingsReq {
	
	private String orderId;
	
	private String customerId;
	
	private String childSku;

}
