package org.styli.services.order.pojo.response;

import java.util.List;

import org.styli.services.order.pojo.ErrorType;

import lombok.Data;
@Data
public class ProductInventoryRes {

	private String statusCode;
	
	private Boolean status;
	
	private String statusMsg;
	
	private List<ProductValue> response;
	
	private ErrorType error;
	
	
}
