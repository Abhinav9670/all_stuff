package org.styli.services.customer.pojo.address.response;

import java.io.Serializable;
import java.math.BigDecimal;

import javax.validation.constraints.Pattern;

import lombok.Data;

@Data
public class CustomerAddrees implements Serializable {

	//@NotNull
	private Integer customerId;

	private Integer addressId;

	private String firstName;

	private String lastName;
	
	//@NotNull
	private String mobileNumber;

	private String city;

	private String fax;

	private String streetAddress;

	private String telephone;

	private String country; // two character = SA

	private String region; // Madinah

	private boolean defaultAddress;

	private String postCode;

	private String regionId; // ID of region

	private String area;

	private String landMark;

	private String buildingNumber; // merge this value with streetAddress
	
	private Integer storeId;

	private String email;

	private String jwtToken;
	
	private BigDecimal latitude;
	
	private BigDecimal longitude;
	
	private String formattedAddress;
	
	private Boolean isMobileVerified;
	
	private String isSignUpOtpEnabled;
	
	private String clientVersion;
	
	private String source;

	// New fields for KSA national address format
	private String unitNumber; // Non-mandatory - apartment number

//	@Pattern(regexp = "^[0-9]{5}$", message = "Postal code must be exactly 5 digits")
	private String postalCode; // Mandatory for KSA - 5 digit number (validated in business logic)

//	@Pattern(regexp = "^[A-Za-z]{4}[0-9]{4}$", message = "Short address must have first 4 alphabets followed by 4 digits")
	private String shortAddress; // Non-mandatory - first 4 alphabets, last 4 digits

	private Boolean ksaAddressCompliant; // true if this address has shortAddress data, false otherwise

	/** National ID or Passport document details */
	private NationalId nationalId;

}