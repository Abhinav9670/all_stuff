package org.styli.services.order.pojo.tabby;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class Order {
	
	private String tax_amount;
	
	private String shipping_amount;
	
	private String discount_amount;
	
	@JsonProperty("updated_at")
	private String updatedAt;
	
	@JsonProperty("reference_id")
	private String referenceId;
	
	private List<TabbyItems> items = new ArrayList<>();
}
