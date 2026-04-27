package org.styli.services.order.pojo.request;

import lombok.Data;

@Data
public class BlockInventoryV3 {

	private String quantity;
	
	private String channelSkuCode;

	private String warehouseId;
}
