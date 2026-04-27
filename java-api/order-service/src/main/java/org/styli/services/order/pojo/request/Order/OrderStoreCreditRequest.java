package org.styli.services.order.pojo.request.Order;

import lombok.Data;

@Data
public class OrderStoreCreditRequest {

	private Integer customerId;

	private Integer storeId;
}
