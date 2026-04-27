package org.styli.services.order.pojo;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class GiftOption {

	private Boolean giftwrapRequired;
	
	private Boolean giftMessage;
	
	private BigDecimal giftChargePerUnit;
}
