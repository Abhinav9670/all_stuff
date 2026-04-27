package org.styli.services.order.pojo.request;

import lombok.Data;

@Data
public class CustomerRequestBody {

	private Integer CustomerId;

	private String customerEmail;

	private String customerPhoneNo;

}
