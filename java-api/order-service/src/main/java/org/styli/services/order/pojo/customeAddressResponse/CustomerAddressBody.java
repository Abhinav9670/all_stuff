package org.styli.services.order.pojo.customeAddressResponse;

import lombok.Data;

import java.util.List;

/**
 * @author Biswabhusan Pradhan <biswabhusan.pradhan@landmarkgroup.com>
 */
@Data
public class CustomerAddressBody {

	private CustomerAddrees address;

	private String message;
	
	private boolean status;
	
	private String userMessage;

	private List<CustomerAddrees> addresses;

}
