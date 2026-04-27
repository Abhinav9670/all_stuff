package org.styli.services.order.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Cashfree payment details 
 * @author chandanbehera
 *
 */


@NoArgsConstructor
@ToString
@Data
@EqualsAndHashCode(callSuper = true)
public class CashfreePaymentDTO extends PaymentDTO {

	private static final long serialVersionUID = 1L;

	private String cfOrderId;

	private String message;
	
	private String paymentType;
	
	public CashfreePaymentDTO(String id, String status, String payload) {
		super(id, status, payload);
	}

}
