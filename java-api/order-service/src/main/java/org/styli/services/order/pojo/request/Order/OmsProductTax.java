package org.styli.services.order.pojo.request.Order;

import lombok.Data;

@Data
public class OmsProductTax {

	private String taxType;

	private String taxPercentage;

	private String taxAmount;

}
