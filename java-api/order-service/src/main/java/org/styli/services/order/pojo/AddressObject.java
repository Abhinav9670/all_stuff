package org.styli.services.order.pojo;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class AddressObject {

	@JsonProperty("area")
	private String area;

	@JsonProperty("firstname")
	private String firstname;

	@JsonProperty("city")
	private String city;

	@JsonProperty("addressType")
	private String addressType;

	@JsonProperty("mobileNumber")
	private String mobileNumber;

	@JsonProperty("shippingMethod")
	private String shippingMethod;

	@JsonProperty("postcode")
	private String postcode;

	@JsonProperty("shippingDescription")
	private String shippingDescription;

	@JsonProperty("locationType")
	private String locationType;

	@JsonProperty("telephone")
	private String telephone;

	@JsonProperty("countryId")
	private String countryId;

	@JsonProperty("lastname")
	private String lastname;

	@JsonProperty("customerAddressId")
	private String customerAddressId;

	@JsonProperty("regionId")
	private String regionId;

	@JsonProperty("nearestLandmark")
	private String nearestLandmark;

	@JsonProperty("street")
	private String street;

	@JsonProperty("buildingNumber")
	private String buildingNumber;

	@JsonProperty("region")
	private String region;

	@JsonProperty("email")
	private String email;

	@JsonProperty("defaultAddress")
	private boolean defaultAddress;
	
	@JsonProperty("latitude")
	private BigDecimal latitude;
	
	@JsonProperty("longitude")
	private BigDecimal longitude;

    @JsonProperty("unitNumber")
    private String unitNumber;

    @JsonProperty("shortAddress")
    private String shortAddress;

    @JsonProperty("postalCode")
    private String postalCode;

    @JsonProperty("ksaAddressComplaint")
    private Boolean ksaAddressComplaint = null;
	
	@JsonProperty("formattedAddress")
	private String formattedAddress;
	
	private CityMapper cityMapper;
}