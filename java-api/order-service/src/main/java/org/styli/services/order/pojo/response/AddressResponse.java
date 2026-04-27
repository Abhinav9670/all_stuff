package org.styli.services.order.pojo.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddressResponse {

	private int id;

	private int customer_id;

	private Region region;

	private String country_id;

	private List<String> street;

	private String telephone;

	private String postcode;

	private String city;

	private String firstname;

	private String lastname;

	private boolean default_shipping;

	private boolean default_billing;

}
