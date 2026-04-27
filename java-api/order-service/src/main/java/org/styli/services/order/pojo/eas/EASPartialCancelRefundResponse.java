package org.styli.services.order.pojo.eas;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class EASPartialCancelRefundResponse {

	private BigDecimal coinAmountRefunded;
    private BigDecimal easValueInBaseCurrency;
}
