package org.styli.services.order.pojo.customeAddressResponse;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CustomerAddrees {

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

    private String unitNumber;

    private String shortAddress;

    private String postalCode;

    private Boolean ksaAddressComplaint = null;

	private String buildingNumber; // merge this value with streetAddress
	
	private Integer storeId;

	private String email;

	private String jwtToken;
	
	private BigDecimal latitude;
	
	private BigDecimal longitude;
	
	private String formattedAddress;

	private Boolean isMobileVerified;

	private Boolean isSignUpOtpEnabled;

	private String clientVersion;

	private String source;
}

