package org.styli.services.order.pojo.request.Order;

import lombok.Data;

@Data
public class CustomerOmslistrequest {

	
	private String email;
	
	private String phoneNumber;
	
	private String fromDate;
	
	private String toDate;
	
	private String gender;
	
}
