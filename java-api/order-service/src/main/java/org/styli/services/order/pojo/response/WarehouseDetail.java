package org.styli.services.order.pojo.response;

import lombok.Data;

@Data
public class WarehouseDetail {
	private String warehouseId;
	private Integer min_sla;
	private Integer max_sla;
	private String estimated_date;
	private String min_estimated_date;
	private String max_estimated_date;
}
