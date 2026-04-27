package org.styli.services.customer.pojo.registration.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CustomerV4RegistrationResponseBody {

	private Customer customer;

}
