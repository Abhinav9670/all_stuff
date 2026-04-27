package org.styli.services.customer.pojo;

import lombok.Data;

@Data
public class CustomerOmslistrequest {

	
	
	private CustomerOmsfilterRequest filters;
	private String query;
	private int offset;
	private int pageSize;
}
