package org.styli.services.order.pojo.cashfree;

import lombok.Data;

/**
 * Cashgram request payload 
 * @author chandanbehera
 *
 */
@Data
public class CashgramRequestDTO {

	private String amount;
	
	private String phone;
	
	private String email;
	
	private String notifyCustomer;
	
	private String linkExpiry;
	
	private String cashgramId;
	
	private String name;
	
	private String remarks;
}
