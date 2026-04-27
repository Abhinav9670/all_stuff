package org.styli.services.order.pojo.response;

import lombok.Data;

@Data
public class CustomerProfileResponse {

	private String userMessage;

	private boolean status;

	private Customer customer;

	private CustomerAddrees defaultAddress;
}
