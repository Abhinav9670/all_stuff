package org.styli.services.order.pojo;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class PaymentDTO implements Serializable{
	
	private static final long serialVersionUID = 1L;

	private String orderId;
	
	private String id;
	
	private String status;
	
	private String payload;
	
	public PaymentDTO(String id, String status, String payload) {
		this.id = id;
		this.status = status;
		this.payload = payload;
	}

}
