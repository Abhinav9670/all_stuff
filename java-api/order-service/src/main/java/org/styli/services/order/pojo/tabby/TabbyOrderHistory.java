package org.styli.services.order.pojo.tabby;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class TabbyOrderHistory {
	
	private String purchased_at;
	
	private String amount;
	
	@JsonProperty("buyer")
	private TabbyBuyer tabbyBuyer;
	
	@JsonProperty("shipping_address")
	private List<TabbyShippingAddress> shippingAddress = new ArrayList<>();
	
	private List<TabbyItems> items = new ArrayList<>();

}
