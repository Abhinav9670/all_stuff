package org.styli.services.customer.pojo;

import lombok.Data;

@Data
public class CustomerDetailsRequest {

	private Integer customerId;
	
	private Integer addressId;
	
	private String customerEmail;

	private String customerPhoneNo;
}
