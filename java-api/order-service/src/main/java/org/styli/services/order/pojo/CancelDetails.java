package org.styli.services.order.pojo;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class CancelDetails {

	private BigDecimal amasyStoreCredit;
	
	private BigDecimal totalOnliineCancelAMount ;
	
	private BigDecimal amastyBaseStoreCredit;
	
	private boolean isGiftVoucher;

	private BigDecimal currentOrderValue;
}
