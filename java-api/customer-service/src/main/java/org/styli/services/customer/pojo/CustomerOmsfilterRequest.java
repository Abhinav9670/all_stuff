package org.styli.services.customer.pojo;

import java.util.List;

import lombok.Data;

@Data
public class CustomerOmsfilterRequest {

	
	private String customerName;
	
	private String customerEmail;
	
	private String mobileNumber;
	
	private String gender;
	
	private String customerId;
	
	private List<String> webSite;
	
	private String source;
	
	private List<String> storeId;
	
	private String customerSince;
}
