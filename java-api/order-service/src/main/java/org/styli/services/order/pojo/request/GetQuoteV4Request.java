package org.styli.services.order.pojo.request;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class GetQuoteV4Request {

	private Integer quoteId;

	private Integer customerId;

	@NotNull
	@Min(1)
	private Integer storeId;

	@NotNull
	private Integer bagView;

}
