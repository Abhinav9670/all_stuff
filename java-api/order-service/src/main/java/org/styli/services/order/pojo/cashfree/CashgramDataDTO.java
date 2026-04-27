package org.styli.services.order.pojo.cashfree;

import lombok.Data;

/**
 * Cashgram Data Response 
 * @author chandanbehera
 *
 */
@Data
public class CashgramDataDTO {

	private String token;
	
	private String cashgramLink;
	
	private String expiry;
	
	private String referenceId;
}
