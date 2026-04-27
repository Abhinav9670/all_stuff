package org.styli.services.customer.pojo.response;

import java.util.Date;

import lombok.Data;

@Data
public class CustomerOmsResponse {

	private Integer customerId;
	
	private String customerName;
	
	private String customerEmail;
	
	private String group;
	
	private String mobileNumber;
	
	private String countryName;
	
	private String provinceName;
	
	private String customerSince;
	
	private String website;	
	
	private Integer gender;
	
	private String optedForwhatsapp;
	
	private String isReferral;
	
	private String signUpBy;
	
	private String currentSignInBy;
	
	private Date lastSignedInTimestamp;
	
}
