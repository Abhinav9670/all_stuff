package org.styli.services.customer.pojo.response;

import java.util.List;

import org.styli.services.customer.pojo.registration.response.ErrorType;
import org.styli.services.customer.pojo.registration.response.Product.ProductValue;

import lombok.Data;
@Data
public class ProductInventoryRes {
	
	private InventoryMetaData meta;

	private String statusCode;
	
	private Boolean status;
	
	private String statusMsg;
	
	private List<ProductValue> response;
	
	private ErrorType error;
	
	
}
