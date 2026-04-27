package org.styli.services.customer.pojo.registration.request;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class CustomerValidityCheckRequest {

	
	@NotNull
	@Min(1)
	private Integer customerId;
	@NotEmpty
	private String customerEmail;
	
}
