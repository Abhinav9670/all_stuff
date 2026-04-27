package org.styli.services.order.pojo.eas;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class StyliCoinUpdate {

	private Integer orderId;

	private Integer requestId;

	private Integer coin;

	private BigDecimal coinToCurrency;

	private BigDecimal baseCurrencyValue;
}
