package org.styli.services.order.pojo;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.validation.constraints.NotNull;

import org.styli.services.order.model.sales.SplitSellerOrder;

import lombok.Data;

@Data
public class OrderunfulfilmentRequest {

	@NotNull
	private String orderCode;
	
	private String locationCode;
	
	private List<OrderPushItem> orderItems;

	private Integer cancelledBy = 1;

	private SplitSellerOrder splitSellerOrder = null;

	private Map<String, BigDecimal> cancelledSellerSkuQtyMap = new HashMap<>();
}
