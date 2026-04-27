package org.styli.services.order.pojo.eas;

import lombok.Data;

@Data
public class EASKafkaCancelOrder {
	
	private Integer orderId;
	private Integer customerId;
	private Integer storeId;
	private Integer spendCoin;
	private String status;
	private String state;
}
