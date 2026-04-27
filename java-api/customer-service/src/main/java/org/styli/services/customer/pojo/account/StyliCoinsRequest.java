package org.styli.services.customer.pojo.account;

import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
public class StyliCoinsRequest {
	private Integer customerId;

	@NotNull
	public Integer storeId;
}
