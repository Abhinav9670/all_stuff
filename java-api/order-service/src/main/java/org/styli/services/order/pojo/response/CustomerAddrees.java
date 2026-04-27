package org.styli.services.order.pojo.response;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@ToString
@Data
public class CustomerAddrees implements Serializable {

	private Integer customerId;

	private Integer addressId;

	private String firstName;

	private String lastName;

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
	
	private String customerAccountFirstName;
	
	private String customerAccountLastName;
	
	private Integer isMobileVerified;

	private String isSignUpOtpEnabled;

	private String clientVersion;

	private String source;

    private String unitNumber;

    private String shortAddress;

    private String postalCode;

    private Boolean ksaAddressComplaint = null;

}
