package org.styli.services.order.pojo;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class OrderPushItem {

	
	private String channelSkuCode;
	
	private String orderItemCode;
	
	private Integer quantity;
	
	private Integer mrpPerUnit;
	
	private BigDecimal sellerDiscountPerUnit;
	
	private BigDecimal channelDiscountPerUnit;
	
	private BigDecimal sellingPricePerUnit;
	
	private BigDecimal shippingChargePerUnit;
	
	private GiftOption giftOptions;
	
	private BigDecimal cancelledQuantity;
}
