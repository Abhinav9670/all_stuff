package org.styli.services.order.pojo.request;

import lombok.Data;

@Data
public class BlockInventory {

	private String quantity;
	
	private String channelSkuCode;

	private String warehouseId;
}
