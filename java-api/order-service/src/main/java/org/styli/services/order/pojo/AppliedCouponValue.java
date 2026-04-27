package org.styli.services.order.pojo;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class AppliedCouponValue {

	
	private String coupon;
	
	private String type;
	
	private BigDecimal discount;

	private Boolean isGiftVoucher;
}
