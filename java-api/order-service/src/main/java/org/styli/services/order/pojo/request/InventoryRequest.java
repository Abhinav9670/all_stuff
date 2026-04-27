package org.styli.services.order.pojo.request;

import java.util.List;

import lombok.Data;

@Data
public class InventoryRequest {

	private Integer storeId;
	
	private String incrementId;
	
	private List<BlockInventory> inventories;
	
	private boolean updateQty;
	
	private String releaseType;
	
	private String warehouseId;
}
