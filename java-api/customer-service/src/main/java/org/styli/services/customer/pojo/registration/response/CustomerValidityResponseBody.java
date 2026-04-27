package org.styli.services.customer.pojo.registration.response;

import lombok.Data;

@Data
public class CustomerValidityResponseBody {

	
	private String jwtToken;
	
	private Integer customerId;
	
	private String customerEmail;
}
