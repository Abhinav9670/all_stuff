package org.styli.services.order.pojo.order;

import org.styli.services.order.model.rma.AmastyRmaRequest;

import lombok.Data;

/**
 * Any additional information to Initiate payment refund
 * @author chandanbehera
 *
 */
@Data
public class PaymentReturnAdditioanls {

	private AmastyRmaRequest rmaRequest;
	
	private String paymentMethod;
	
	private String returnAmount;
}
