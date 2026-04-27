package org.styli.services.customer.pojo.eas;


import lombok.Data;

@Data
public class EarnCustomerProfileResponse {

	private boolean status;

	private String statusCode;

	private String statusMsg;

	private EarnResponse earnResponse;

}
