package org.styli.services.order.pojo.quote.response;

import lombok.Data;

import java.math.BigDecimal;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class GetQuoteV5Request {

	private String quoteId;

	private Integer customerId;

	@NotNull
	@Min(1)
	private Integer storeId;

	@NotNull
	private Integer bagView;

	private Boolean orderCreation;

	private BigDecimal storeCredit;
	
	private boolean retryPayment;

}
