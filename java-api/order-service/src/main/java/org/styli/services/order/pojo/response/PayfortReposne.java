package org.styli.services.order.pojo.response;

import lombok.Data;

@Data
public class PayfortReposne {

	private boolean status;
	
	private String message;
	
	private String paymentRRN;
}
