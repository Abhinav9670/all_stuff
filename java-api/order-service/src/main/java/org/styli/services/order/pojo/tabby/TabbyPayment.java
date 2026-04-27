package org.styli.services.order.pojo.tabby;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class TabbyPayment {
	
	private String id;
	
	@JsonProperty("created_at")
    private String createdAt;
	
	@JsonProperty("expires_at")
    private String expiresAt;
	
	private String amount;
	
	private String currency;
	
	private String description;
	
	private String status;
	
	@JsonProperty("is_expired")
    private boolean isExpired;
	
	private boolean test;
	
	@JsonProperty("buyer")
	private TabbyBuyer tabbyBuyer;
	
	@JsonProperty("buyer_history")
	private TabbyBuyerHistory tabbyBuyerHistory;
	
	private Order order;

	@JsonProperty("order_history")
	private List<TabbyOrderHistory> orderHistory = new ArrayList<>();
	
	@JsonProperty("shipping_address")
	private TabbyShippingAddress tabbyShippingAddress;
	
	@JsonProperty("captures")
	private List<TabbypaymentCaptureDTO> tabbypaymentCaptureDTO = new ArrayList<>();
	
	@JsonProperty("refunds")
	private List<TabbyRefundDTO> tabbyRefundDTO = new ArrayList<>();
	
	private boolean success;
}
