package org.styli.services.order.pojo.order;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BNPLOrderUpdateResponse {
	
	
	private boolean paymentSuccess;
	
	private String incrementId;
	
	private Integer orderEntityId;
	
	private boolean success;
	
	private String errorMessage;

}
