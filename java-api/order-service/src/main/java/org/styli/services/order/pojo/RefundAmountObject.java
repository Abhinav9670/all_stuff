package org.styli.services.order.pojo;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class RefundAmountObject {

	private BigDecimal refundOnlineAmount;
	
	private BigDecimal refundStorecreditAmount;
	
	private BigDecimal discountAmount ;
	
	private BigDecimal shippingAmount;
	
	private BigDecimal donationAmount;
	
	private BigDecimal baseAmastyStoreCreditAmount;
	
	private BigDecimal refundGiftVoucherAmount = BigDecimal.ZERO;
	
	private boolean isGiftVoucher;
}
