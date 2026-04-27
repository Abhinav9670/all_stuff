package org.styli.services.order.pojo.cashfree;

import lombok.Data;
import lombok.ToString;

/**
 * Cashgram Webhook Request.
 * DO NOT ADD EXTRA ATTRIBUTES. AS IT'LL IMPACT THE SIGNATURE VALIDATION.
 * CHECK CASHGRAM DOCUMENTATION BEFORE ADDING 
 * @author chandanbehera
 *
 */
@Data
@ToString
public class CashgramWebhookDTO {

	private String cashgramId;
	
	private String referenceId;
	
	private String eventTime;
	
	private String utr;
	
	private String event;
	
	private String signature;
	
	private String reason;
}
