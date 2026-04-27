package org.styli.services.order.pojo.response;

import lombok.Data;
import org.styli.services.order.pojo.ErrorType;

import java.util.List;

@Data
public class ProductInventoryResV2 {

	private Meta meta;
	private List<ResponseItem> response;
	
	
}
