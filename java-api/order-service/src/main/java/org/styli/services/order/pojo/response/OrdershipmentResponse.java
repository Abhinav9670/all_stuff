package org.styli.services.order.pojo.response;

import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
public class OrdershipmentResponse {

	
	private Integer shipmentId;
	
	private String shipmentIncid;
	
	private Integer orderId;
}
