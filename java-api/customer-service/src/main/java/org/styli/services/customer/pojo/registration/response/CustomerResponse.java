package org.styli.services.customer.pojo.registration.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.styli.services.customer.pojo.QuoteDTO;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CustomerResponse {

	private Customer customer;

	private QuoteDTO quote;

	private Integer quoteId;

	// used in cookie login response
	private Integer storeId;

}
