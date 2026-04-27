package org.styli.services.order.pojo;

import lombok.Data;

@Data
public class DiscountData {

	private String couponCode;
	private String discountType;
	private String redeemType;
	private String value;
	private String label;
	private Boolean isGiftVoucher;
}
