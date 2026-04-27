package org.styli.services.order.pojo.request;

import lombok.Data;

import java.util.List;

@Data
public class InventoryRequestV3 {

	private Integer storeId;
	
	private String incrementId;
	
	private List<BlockInventoryV3> inventories;
	
	private boolean updateQty;
	
	private String releaseType;
	
	private String warehouseId;
}
