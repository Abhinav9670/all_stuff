package org.styli.services.order.pojo.eas;

import org.styli.services.order.pojo.response.Order.OrderResponse;

import lombok.Data;

@Data
public class EASKafkaSaleOrder {

	private Integer customerId;
	
	private Integer storeId;
	
	private Integer spendCoin;
	
	private Integer orderId;
	
	private EASKafkaCustomerDetailSaleOrder customerDetail;
	
	private OrderResponse otherDetail;
}
