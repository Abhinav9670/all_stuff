package org.styli.services.order.pojo.response;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@Data
public class ResponseItem {

	private String sku;
	private String value;
	private List<WarehouseDetail> warehouse_details;

}
