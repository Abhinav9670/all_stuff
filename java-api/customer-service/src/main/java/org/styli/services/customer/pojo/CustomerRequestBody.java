package org.styli.services.customer.pojo;

import lombok.Data;

@Data
public class CustomerRequestBody {

	private Integer customerId;
	
	private Integer storeId;
	
	private String customerEmail;
}
