package org.styli.services.customer.pojo.registration.request;

import java.util.List;

//@Getter
//@Setter
public class Address {

	private boolean defaultBilling;

	private boolean defaultShipping;

	private String firstname;

	private String lastname;

	private String countryId;

	private String postcode;

	private String city;

	private List<String> street;

	private String telephone;

	public boolean isDefaultBilling() {
		return defaultBilling;
	}

	public void setDefaultBilling(boolean defaultBilling) {
		this.defaultBilling = defaultBilling;
	}

	public boolean isDefaultShipping() {
		return defaultShipping;
	}

	public void setDefaultShipping(boolean defaultShipping) {
		this.defaultShipping = defaultShipping;
	}

	public String getFirstname() {
		return firstname;
	}

	public void setFirstname(String firstname) {
		this.firstname = firstname;
	}

	public String getLastname() {
		return lastname;
	}

	public void setLastname(String lastname) {
		this.lastname = lastname;
	}

	public String getCountryId() {
		return countryId;
	}

	public void setCountryId(String countryId) {
		this.countryId = countryId;
	}

	public String getPostcode() {
		return postcode;
	}

	public void setPostcode(String postcode) {
		this.postcode = postcode;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public List<String> getStreet() {
		return street;
	}

	public void setStreet(List<String> street) {
		this.street = street;
	}

	public String getTelephone() {
		return telephone;
	}

	public void setTelephone(String telephone) {
		this.telephone = telephone;
	}

}
