package org.styli.services.order.pojo.response;

import java.io.Serializable;

import lombok.Data;

@Data
public class CustomCouponCancelRedemptionResponse implements Serializable {

	
	private static final long serialVersionUID = 1L;

	
	private Integer code;
	
	private String status;
	
	private String data;
	
}
