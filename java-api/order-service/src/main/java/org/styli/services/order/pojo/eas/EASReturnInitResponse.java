package org.styli.services.order.pojo.eas;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class EASReturnInitResponse {

	private Integer returnCoin;
	private BigDecimal coinValueInCurrency;
	private String coinValueInCurrencyLabel;
}
