package org.styli.services.order.pojo.kafka;

import lombok.Data;

@Data
public class DeleteCustomerStatusUpdateRequest {

	private Integer customerId;
	private String task;
	private boolean status;
}
