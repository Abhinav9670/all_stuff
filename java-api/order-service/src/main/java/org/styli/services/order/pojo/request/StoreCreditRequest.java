package org.styli.services.order.pojo.request;

import com.sun.istack.NotNull;

import lombok.Data;

@Data
public class StoreCreditRequest {

	@NotNull
	private Integer customerId = 0;
	
	@NotNull
	private Integer storeId = 0;
}
