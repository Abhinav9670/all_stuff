package org.styli.services.order.pojo.cashfree;

import lombok.Data;

/**
 * Cashgram Base Response 
 * @author chandanbehera
 *
 */
@Data
public class CashgramResponseDTO {

	private String status;
	
	private String subCode;
	
	private String message;
	
	private CashgramDataDTO data;
}
