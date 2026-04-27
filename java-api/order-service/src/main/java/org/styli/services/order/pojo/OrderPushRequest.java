package org.styli.services.order.pojo;

import java.util.List;

import org.styli.services.order.pojo.request.Address;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class OrderPushRequest {

	
	private String parentOrderCode;
	
	private String locationCode;
	
	private String orderCode;
	
	private String orderTime;
	
	private String orderType;
	
	private Boolean onHold;
	
	private String qcStatus;
	
	@JsonProperty("isPriority")
	private boolean isPriority;
	
	private String dispatchByTime;
	
	private String startProcessingTime;
	
	private String paymentMethod;
	
	private Address shippingAddress;
	
	private Address billingAddress;
	
	private List<OrderPushItem> orderItems;

	private OrderCustomAttributes orderCustomAttributes;
	
		
	
	
}
