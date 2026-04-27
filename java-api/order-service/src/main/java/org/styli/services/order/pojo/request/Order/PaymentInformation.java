package org.styli.services.order.pojo.request.Order;

import lombok.Data;

@Data
public class PaymentInformation {

	private String paymentMethod;
	
	private String customerIp;
	
	private String merchantReference;
	
	private String authorizationCode;
	
	private String ccType;
	
	private String ccNumber;
	
	private String amount;
	
	private String paymentResponseMessage;
	
	private String commandType;
	

}
