package org.styli.services.order.pojo.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

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
	
	@JsonProperty("line1")
	private String line1;
	
	private String name;
	
	private String state;
	
	private String country;
	
	private String email;
	
	private String phone;
	
	private String zip;

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

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

	public String getLine1() {
		return line1;
	}

	public void setLine1(String line1) {
		this.line1 = line1;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getZip() {
		return zip;
	}

	public void setZip(String zip) {
		this.zip = zip;
	}

	
}
