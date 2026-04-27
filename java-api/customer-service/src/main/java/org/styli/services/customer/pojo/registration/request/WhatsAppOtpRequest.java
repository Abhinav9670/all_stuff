package org.styli.services.customer.pojo.registration.request;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class WhatsAppOtpRequest {

	@NotNull
	@Min(1)
	private Integer customerId;

	@NotNull
	private Boolean requestFlag;
}
