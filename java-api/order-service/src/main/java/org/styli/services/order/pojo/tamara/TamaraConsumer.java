package org.styli.services.order.pojo.tamara;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class TamaraConsumer {

	@JsonProperty("first_name")
	private String firstName;

	@JsonProperty("last_name")
	private String lastName;

	@JsonProperty("phone_number")
	private String phoneNumber;
	
	private String email;
}
