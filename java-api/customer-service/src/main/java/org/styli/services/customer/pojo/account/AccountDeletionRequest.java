package org.styli.services.customer.pojo.account;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class AccountDeletionRequest {
	
	private String requestType;

	@Min(1) @NotNull
	private Integer customerId;

	private String reason;

	private String otp;

}
