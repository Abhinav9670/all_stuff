package org.styli.services.order.pojo.response;

import lombok.Data;

@Data
public class InventoryMetaData {

	private Boolean status;
	
	private Integer code;
	
	private String message;
	
	private String details;
}
