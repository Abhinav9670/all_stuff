package org.styli.services.order.pojo.response;

import lombok.Data;

@Data
public class shipmentItem {

	private String channelSkuCode;
	
	private Integer quantity;
	
	private String orderItemCode;
}
