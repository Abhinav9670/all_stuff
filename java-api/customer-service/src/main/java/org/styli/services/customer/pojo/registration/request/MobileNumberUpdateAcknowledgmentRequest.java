package org.styli.services.customer.pojo.registration.request;

import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class MobileNumberUpdateAcknowledgmentRequest {

	@NotNull(message = "Customer ID is required")
	private Integer customerId;

	@NotNull(message = "Acknowledged flag is required")
	private Boolean acknowledged;
}