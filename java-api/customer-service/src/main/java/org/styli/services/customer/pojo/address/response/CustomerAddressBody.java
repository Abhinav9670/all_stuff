package org.styli.services.customer.pojo.address.response;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

@Data
public class CustomerAddressBody implements Serializable {

	private CustomerAddrees address;

	private String message;
	
	private boolean status;
	
	private String userMessage;

	private List<CustomerAddrees> addresses;

	private Boolean ksaAddressCompliant; // true if all addresses have shortAddress data, false otherwise

}
